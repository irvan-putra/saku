/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 *
 * Test-only prototype. Not production vault code.
 *
 * Android emulator tests for the local KDBX4 vault store on pinned KeePassDX
 * database/crypto modules. These run REAL native Argon2id transforms with
 * synthetic data only. No real vault, no transport, no SAF, no autofill, no
 * process-kill or power-loss claim, no Samsung A24 measurement.
 */
package com.vanipy.saku.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.crypto.kdf.Argon2Kdf
import com.kunzisoft.keepass.database.crypto.kdf.KdfParameters
import com.kunzisoft.keepass.database.exception.KDFMemoryDatabaseException
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalVaultStoreAndroidTest {

    private lateinit var directory: File
    private val master = "SYNTHETIC-master".toCharArray()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.filesDir, "saku-vault-test")
        directory.deleteRecursively()
        check(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private inline fun <reified T : Throwable> expectFailure(action: () -> Unit) {
        val failure = try {
            action()
            null
        } catch (caught: Throwable) {
            caught
        }
        assertTrue("Expected ${T::class.java.simpleName}, got $failure", failure is T)
    }

    /** Rewrites only the Argon2 memory field inside the KDF parameters of a
     *  real written file, reusing upstream's own VariantDictionary codec.
     *  Same keys and value types, so the serialized length is unchanged and
     *  every other byte of the file survives. */
    private fun patchMemoryField(file: File, memory: ULong) {
        val bytes = file.readBytes()
        var offset = 12 // signatures + version
        while (true) {
            val fieldId = bytes[offset].toInt() and 0xFF
            val length = (bytes[offset + 1].toLong() and 0xFF) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 4].toLong() and 0xFF) shl 24)
            if (fieldId == 0) break
            if (fieldId == 11) {
                val start = offset + 5
                val end = start + length.toInt()
                val params = KdfParameters.deserialize(bytes.copyOfRange(start, end))
                    ?: error("fixture KDF parameters not deserializable")
                // VariantDictionary key "M" is the KDBX Argon2 memory parameter.
                params.setUInt64("M", memory)
                val patched = KdfParameters.serialize(params)
                check(patched.size == length.toInt()) { "patched length changed" }
                System.arraycopy(patched, 0, bytes, start, patched.size)
                file.writeBytes(bytes)
                return
            }
            offset += 5 + length.toInt()
        }
        error("no KDF parameters field in fixture")
    }

    @Test
    fun createUnlockAddReopenWithArgon2id() {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.addEntry("saku synthetic entry", "dummy@example.invalid", "example.test",
            "SYNTHETIC-spike".toCharArray())
        store.addEntry("second synthetic entry", "two@example.invalid", "two.example.test",
            "SYNTHETIC-two".toCharArray())
        assertEquals(2, store.entries().size)
        store.lock()

        val reopened = LocalVaultStore(directory)
        reopened.unlock(master)
        val entries = reopened.entries()
        assertEquals(2, entries.size)
        val spike = entries.single { it.url == "example.test" }
        assertEquals("saku synthetic entry", spike.title)
        assertEquals("dummy@example.invalid", spike.username)
        assertEquals("SYNTHETIC-spike", spike.password.concatToString())
        val two = entries.single { it.url == "two.example.test" }
        assertEquals("SYNTHETIC-two", two.password.concatToString())
        reopened.lock()

        // The file on disk must actually be Argon2id with the bounded memory,
        // not a silent AES-KDF fallback. See STORAGE-EVAL: check the KDF of
        // any KeePassDX-written fixture before claiming Argon2 coverage.
        val params = KdbxKdfBudgetGate.enforce(reopened.vaultFile(), LocalVaultStore.TEST_LIMITS)
        assertEquals(Argon2Kdf.Type.ARGON2_ID.CIPHER_UUID, params.uuid)
        assertEquals(16uL * 1024u * 1024u, params.getUInt64("M"))
    }

    @Test
    fun wrongMasterPasswordRejected() {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.addEntry("saku synthetic entry", "dummy@example.invalid", "example.test",
            "SYNTHETIC-spike".toCharArray())
        store.lock()

        val attempt = LocalVaultStore(directory)
        expectFailure<Exception> { attempt.unlock("SYNTHETIC-wrong".toCharArray()) }
        assertEquals(LocalVaultStore.State.LOCKED, attempt.state)
    }

    @Test
    fun truncatedFileRejected() {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.lock()
        val file = store.vaultFile()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))

        val attempt = LocalVaultStore(directory)
        expectFailure<Exception> { attempt.unlock(master) }
        assertEquals(LocalVaultStore.State.LOCKED, attempt.state)
    }

    @Test
    fun tamperedPayloadByteRejected() {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.lock()
        val file = store.vaultFile()
        val bytes = file.readBytes()
        val at = bytes.size * 3 / 4 // inside the encrypted payload
        bytes[at] = (bytes[at].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        val attempt = LocalVaultStore(directory)
        expectFailure<Exception> { attempt.unlock(master) }
        assertEquals(LocalVaultStore.State.LOCKED, attempt.state)
    }

    @Test
    fun overBudgetMemoryRejectedBeforeTransform() {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.lock()
        patchMemoryField(store.vaultFile(), 1_000_000_000uL) // ~1 GiB, over the test budget

        val attempt = LocalVaultStore(directory)
        val start = System.nanoTime()
        expectFailure<KDFMemoryDatabaseException> { attempt.unlock(master) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // Structural evidence is the exception type: the gate rejects before
        // Database.loadData, so the ~1 GiB native transform never starts. The
        // time bound is only a soft corroboration on an emulator.
        assertTrue("budget rejection should be immediate, was ${elapsedMs}ms", elapsedMs < 15_000)
        assertEquals(LocalVaultStore.State.LOCKED, attempt.state)
    }

    @Test
    fun syntheticGateRefusesRealLookingSecrets() {
        val store = LocalVaultStore(directory)
        expectFailure<VaultRefusal> { store.create("correct horse battery".toCharArray()) }
        assertEquals(LocalVaultStore.State.LOCKED, store.state)
        store.create(master)
        expectFailure<VaultRefusal> {
            store.addEntry("real looking", "someone@example.com", "example.com",
                "hunter2".toCharArray())
        }
        expectFailure<VaultRefusal> {
            store.addEntry(" ", "someone@example.com", "example.com",
                "SYNTHETIC-blank-title".toCharArray())
        }
        assertEquals(0, store.entries().size)
        store.lock()
    }

    @Test
    fun freshFixtureExportedForInterop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalVaultStore(directory)
        store.create(master)
        store.addEntry("saku synthetic entry", "dummy@example.invalid", "example.test",
            "SYNTHETIC-spike".toCharArray())
        store.lock()

        // External app-specific storage: pullable by adb without run-as.
        val exportDir = context.getExternalFilesDir(null)
            ?: error("external files dir unavailable")
        val export = File(exportDir, EXPORT_FILE_NAME)
        export.delete()
        Files.copy(store.vaultFile().toPath(), export.toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        assertTrue("exported fixture missing", export.isFile && export.length() > 0)

        // The exported copy must pass the gate and reopen with the synthetic entry.
        KdbxKdfBudgetGate.enforce(export, LocalVaultStore.TEST_LIMITS)
        val verifyDir = File(directory, "verify-copy")
        check(verifyDir.mkdirs())
        Files.copy(export.toPath(), File(verifyDir, LocalVaultStore.FILE_NAME).toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        val verify = LocalVaultStore(verifyDir)
        verify.unlock(master)
        val entry = verify.entries().single { it.url == "example.test" }
        assertEquals("SYNTHETIC-spike", entry.password.concatToString())
        verify.lock()
    }

    companion object {
        const val EXPORT_FILE_NAME = "saku-fresh-argon2id.kdbx"
    }
}
