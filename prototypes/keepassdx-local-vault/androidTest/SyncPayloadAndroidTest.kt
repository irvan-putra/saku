/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 *
 * Test-only prototype. Not production vault code.
 *
 * Emulator tests for delivering a sync envelope payload into a second local
 * vault store on pinned KeePassDX database/crypto modules. REAL native
 * Argon2id transforms with synthetic data only. No DeltaChat transport, no
 * accounts, no network, no real vault, no merge of decrypted contents.
 */
package com.vanipy.saku.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.vanipy.saku.sync.SyncEnvelope
import com.vanipy.saku.sync.SyncMerge
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPayloadAndroidTest {

    private lateinit var directoryA: File
    private lateinit var directoryB: File
    private val master = "SYNTHETIC-master".toCharArray()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directoryA = File(context.filesDir, "saku-sync-test-a")
        directoryB = File(context.filesDir, "saku-sync-test-b")
        directoryA.deleteRecursively(); directoryB.deleteRecursively()
        check(directoryA.mkdirs()); check(directoryB.mkdirs())
    }

    @After
    fun tearDown() {
        directoryA.deleteRecursively(); directoryB.deleteRecursively()
    }

    private fun populatedVault(directory: File, title: String, password: String): ByteArray {
        val store = LocalVaultStore(directory)
        store.create(master)
        store.addEntry(title, "demo-user", "https://$title", password.toCharArray())
        store.lock()
        return store.vaultFile().readBytes()
    }

    @Test fun envelopeDeliversSyntheticVaultToSecondStore() {
        val bytes = populatedVault(directoryA, "synced.test", "SYNTHETIC-synced")
        val (envelope, wire) = SyncEnvelope.create(
            "SYNTHETIC-vault-sync", "SYNTHETIC-device-a", 1, null, 1234L, bytes)
        val (decoded, payload) = SyncEnvelope.decode(wire)
        assertEquals(SyncMerge.Decision.APPLY_REMOTE, SyncMerge.decide(null, decoded))
        File(directoryB, LocalVaultStore.FILE_NAME).writeBytes(payload)
        val storeB = LocalVaultStore(directoryB)
        storeB.unlock(master)
        val entries = storeB.entries()
        assertEquals(1, entries.size)
        assertEquals("synced.test", entries[0].title)
        assertEquals("SYNTHETIC-synced", entries[0].password.concatToString())
        storeB.lock()
    }

    @Test fun divergentSameSeqConflictLeavesTargetUntouched() {
        val remoteBytes = populatedVault(directoryA, "remote.test", "SYNTHETIC-remote")
        val localBytes = populatedVault(directoryB, "local.test", "SYNTHETIC-local")
        val local = SyncEnvelope.create(
            "SYNTHETIC-vault-sync", "SYNTHETIC-device-b", 5, null, 2000L, localBytes).first
        val remote = SyncEnvelope.create(
            "SYNTHETIC-vault-sync", "SYNTHETIC-device-a", 5, null, 2001L, remoteBytes).first
        assertEquals(SyncMerge.Decision.CONFLICT, SyncMerge.decide(local, remote))
        // No apply on conflict: the local vault file must be byte-identical.
        assertArrayEquals(localBytes,
            File(directoryB, LocalVaultStore.FILE_NAME).readBytes())
        val storeB = LocalVaultStore(directoryB)
        storeB.unlock(master)
        assertEquals("local.test", storeB.entries().single().title)
        storeB.lock()
    }

    @Test fun tamperedEnvelopeRefusedBeforeVaultOpen() {
        val bytes = populatedVault(directoryA, "synced.test", "SYNTHETIC-synced")
        val wire = SyncEnvelope.create(
            "SYNTHETIC-vault-sync", "SYNTHETIC-device-a", 1, null, 1234L, bytes).second
        wire[wire.size - 1] = (wire.last() + 1).toByte()
        val refused = try {
            SyncEnvelope.decode(wire)
            false
        } catch (expected: IllegalArgumentException) {
            true
        }
        assertTrue("tampered envelope must be refused before any vault open", refused)
        assertTrue(File(directoryB, LocalVaultStore.FILE_NAME).let { !it.exists() })
    }
}
