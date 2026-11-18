package com.vanipy.saku.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device (ART, real app filesystem) proof of SyncApplier: same merge
 * semantics as the host tests, executed where the app actually runs.
 * No network, no accounts. Synthetic payloads only.
 */
@RunWith(AndroidJUnit4::class)
class SyncApplierInstrumentedTest {

    private lateinit var dir: File
    private val p1 = "SYNTHETIC-device-payload-v1".toByteArray()
    private val p2 = "SYNTHETIC-device-payload-v2".toByteArray()

    @Before
    fun freshDir() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(ctx.cacheDir, "applier-instr").apply { deleteRecursively(); mkdirs() }
    }

    private fun env(seq: Long, device: String = "SYNTHETIC-device-1",
                    base: String? = null, body: ByteArray = p1) =
        SyncEnvelope.create("SYNTHETIC-vault-instr", device, seq, base, 1000L, body).first

    @Test
    fun applyPersistsAcrossInstancesOnDevice() {
        val a = SyncApplier(dir)
        assertEquals(SyncApplier.Outcome.APPLIED, a.apply(env(1), p1))
        assertArrayEquals(p1, a.currentPayload("SYNTHETIC-vault-instr"))
        val head = SyncApplier(dir).head("SYNTHETIC-vault-instr")!!
        assertEquals(1L, head.seq)
        assertEquals(SyncEnvelope.sha256(p1), head.payloadSha256)
    }

    @Test
    fun conflictFilesRemoteByteUntouchedOnDevice() {
        val a = SyncApplier(dir)
        a.apply(env(7), p1)
        val divergent = env(7, device = "SYNTHETIC-device-2", body = p2)
        assertEquals(SyncApplier.Outcome.CONFLICT_FILED, a.apply(divergent, p2))
        assertArrayEquals(p1, a.currentPayload("SYNTHETIC-vault-instr"))
        val conflicts = File(File(dir, "SYNTHETIC-vault-instr"), "conflicts").listFiles()!!
        assertTrue(conflicts.any { it.name.endsWith(".payload") && it.readBytes().contentEquals(p2) })
    }

    @Test
    fun tamperedPayloadRefusedOnDevice() {
        val a = SyncApplier(dir)
        val e = env(1)
        val tampered = p1.copyOf().also { it[it.size - 1] = 'X'.code.toByte() }
        try {
            a.apply(e, tampered)
            org.junit.Assert.fail("hash-mismatching payload must be refused")
        } catch (expected: IllegalArgumentException) { }
        assertNull(a.head("SYNTHETIC-vault-instr"))
        assertNull(a.currentPayload("SYNTHETIC-vault-instr"))
    }
}
