/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 *
 * Test-only prototype. Not production vault code.
 *
 * Host JVM tests for the KDBX4 header KDF budget gate. These tests parse
 * headers only; no KDF transform runs, so no native crypto is needed. Real
 * KeePassDX-written files are covered on the Android emulator in
 * LocalVaultStoreAndroidTest, because KeePassDX's AES/Argon2 transforms are
 * native-only and do not run on the host JVM.
 */
package com.vanipy.saku.vault

import com.kunzisoft.keepass.database.crypto.kdf.AesKdf
import com.kunzisoft.keepass.database.crypto.kdf.Argon2Kdf
import com.kunzisoft.keepass.database.crypto.kdf.KdfParameters
import com.kunzisoft.keepass.database.exception.KDFMemoryDatabaseException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KdbxKdfBudgetGateTest {

    private val limits = LocalVaultStore.TEST_LIMITS

    private fun le32(out: ByteArrayOutputStream, value: Long) {
        repeat(4) { out.write(((value shr (8 * it)) and 0xFF).toInt()) }
    }

    /** Minimal KDBX4 header for gate tests. The gate reads only the header,
     *  so no encrypted payload is needed. */
    private fun kdbx4(kdfBytes: ByteArray?, version: Long = 0x00040000L): ByteArray {
        val out = ByteArrayOutputStream()
        le32(out, 0x9AA2D903L)
        le32(out, 0xB54BFB67L)
        le32(out, version)
        if (kdfBytes != null) {
            out.write(11) // KdfParameters field
            le32(out, kdfBytes.size.toLong())
            out.write(kdfBytes)
        }
        out.write(0) // EndOfHeader
        le32(out, 0)
        return out.toByteArray()
    }

    private fun aesParamsBytes(): ByteArray {
        val engine = AesKdf()
        engine.parameters = engine.defaultParameters
        return KdfParameters.serialize(engine.parameters)
    }

    private fun argon2idParamsBytes(memory: ULong, parallelism: Long = 2): ByteArray {
        val engine = Argon2Kdf(Argon2Kdf.Type.ARGON2_ID)
        engine.parameters = engine.defaultParameters
        engine.setMemoryUsage(memory)
        engine.setParallelism(parallelism)
        return KdfParameters.serialize(engine.parameters)
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

    @Test
    fun acceptsAesKdfWithinBudget() {
        val params = KdbxKdfBudgetGate.enforce(ByteArrayInputStream(kdbx4(aesParamsBytes())), limits)
        assertEquals(AesKdf.CIPHER_UUID, params.uuid)
    }

    @Test
    fun acceptsArgon2idWithinBudget() {
        val params = KdbxKdfBudgetGate.enforce(
            ByteArrayInputStream(kdbx4(argon2idParamsBytes(16uL * 1024u * 1024u))), limits)
        assertEquals(Argon2Kdf.Type.ARGON2_ID.CIPHER_UUID, params.uuid)
        // VariantDictionary key "M" is the KDBX Argon2 memory parameter (bytes).
        assertEquals(16uL * 1024u * 1024u, params.getUInt64("M"))
    }

    @Test
    fun rejectsArgon2idMemoryOverBudgetBeforeTransform() {
        // 1 GiB is inside KeePassDX's intrinsic range (up to 4 GiB) but over
        // the 32000000-byte test budget: the historical unchecked path.
        expectFailure<KDFMemoryDatabaseException> {
            KdbxKdfBudgetGate.enforce(
                ByteArrayInputStream(kdbx4(argon2idParamsBytes(1_000_000_000uL))), limits)
        }
    }

    @Test
    fun rejectsMissingKdfParameters() {
        expectFailure<KdbxKdfBudgetGate.MissingKdfParameters> {
            KdbxKdfBudgetGate.enforce(ByteArrayInputStream(kdbx4(null)), limits)
        }
    }

    @Test
    fun rejectsTruncatedHeader() {
        val bytes = kdbx4(argon2idParamsBytes(16uL * 1024u * 1024u))
        expectFailure<KdbxKdfBudgetGate.MalformedHeader> {
            KdbxKdfBudgetGate.enforce(ByteArrayInputStream(bytes.copyOf(bytes.size - 3)), limits)
        }
    }

    @Test
    fun rejectsHugeFieldLengthWithoutAllocating() {
        val out = ByteArrayOutputStream()
        le32(out, 0x9AA2D903L)
        le32(out, 0xB54BFB67L)
        le32(out, 0x00040000L)
        out.write(11)
        le32(out, 0x7FFFFFFFL) // ~2 GiB field claim, no data follows
        val start = System.nanoTime()
        expectFailure<KdbxKdfBudgetGate.MalformedHeader> {
            KdbxKdfBudgetGate.enforce(ByteArrayInputStream(out.toByteArray()), limits)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("bounded rejection should be immediate, was ${elapsedMs}ms", elapsedMs < 10_000)
    }

    @Test
    fun rejectsKdbx3Header() {
        expectFailure<KdbxKdfBudgetGate.NotKdbx4> {
            KdbxKdfBudgetGate.enforce(
                ByteArrayInputStream(kdbx4(aesParamsBytes(), version = 0x00030001L)), limits)
        }
    }

    @Test
    fun rejectsUnknownKdfUuid() {
        val unknown = KdfParameters(UUID(0x1111111111111111L, 0x2222222222222222L))
        unknown.setParamUUID()
        expectFailure<KdbxKdfBudgetGate.MalformedHeader> {
            KdbxKdfBudgetGate.enforce(
                ByteArrayInputStream(kdbx4(KdfParameters.serialize(unknown))), limits)
        }
    }
}
