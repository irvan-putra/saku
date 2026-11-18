package com.vanipy.saku.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Host tests for SyncApplier: merge-driven application, persistence,
 *  conflict safety. Synthetic payloads only. */
class SyncApplierTest {
    @get:Rule val tmp = TemporaryFolder()

    private val p1 = "SYNTHETIC-payload-v1".toByteArray()
    private val p2 = "SYNTHETIC-payload-v2".toByteArray()

    private fun env(seq: Long, device: String = "SYNTHETIC-device-1",
                    base: String? = null, at: Long = 1000L, body: ByteArray = p1,
                    vault: String = "SYNTHETIC-vault-a") =
        SyncEnvelope.create(vault, device, seq, base, at, body).first

    private fun applier() = SyncApplier(tmp.root)

    @Test fun firstEnvelopeAppliesAndPersists() {
        val a = applier()
        assertEquals(SyncApplier.Outcome.APPLIED, a.apply(env(1), p1))
        assertArrayEquals(p1, a.currentPayload("SYNTHETIC-vault-a"))
        val head = applier().head("SYNTHETIC-vault-a")!! // fresh instance: state survived
        assertEquals(1L, head.seq)
        assertEquals(SyncEnvelope.sha256(p1), head.payloadSha256)
    }

    @Test fun higherSeqReplacesPayload() {
        val a = applier()
        assertEquals(SyncApplier.Outcome.APPLIED, a.apply(env(1), p1))
        val e2 = env(2, base = SyncEnvelope.sha256(p1), body = p2)
        assertEquals(SyncApplier.Outcome.APPLIED, a.apply(e2, p2))
        assertArrayEquals(p2, a.currentPayload("SYNTHETIC-vault-a"))
    }

    @Test fun lowerSeqKeptLocal() {
        val a = applier()
        a.apply(env(5), p1)
        assertEquals(SyncApplier.Outcome.KEPT_LOCAL, a.apply(env(4), p2))
        assertArrayEquals(p1, a.currentPayload("SYNTHETIC-vault-a"))
    }

    @Test fun sameSeqSamePayloadIsNoOp() {
        val a = applier()
        a.apply(env(3), p1)
        assertEquals(SyncApplier.Outcome.ALREADY_SAME, a.apply(env(3), p1))
    }

    @Test fun divergentSameSeqConflictsAndCurrentStaysUntouched() {
        val a = applier()
        a.apply(env(7), p1)
        val divergent = env(7, device = "SYNTHETIC-device-2", body = p2)
        assertEquals(SyncApplier.Outcome.CONFLICT_FILED, a.apply(divergent, p2))
        assertArrayEquals(p1, a.currentPayload("SYNTHETIC-vault-a"))
        assertEquals(7L, a.head("SYNTHETIC-vault-a")!!.seq)
        val conflicts = java.io.File(java.io.File(tmp.root, "SYNTHETIC-vault-a"), "conflicts").listFiles()!!
        assertTrue(conflicts.any { it.name.endsWith(".payload") && it.readBytes().contentEquals(p2) })
    }

    @Test fun tamperedPayloadRefusedBeforeAnyWrite() {
        val a = applier()
        val e = env(1)
        val tampered = p1.copyOf().also { it[it.size - 1] = 'X'.code.toByte() }
        try {
            a.apply(e, tampered)
            org.junit.Assert.fail("hash-mismatching payload must be refused")
        } catch (expected: IllegalArgumentException) { }
        assertNull(a.head("SYNTHETIC-vault-a"))
        assertNull(a.currentPayload("SYNTHETIC-vault-a"))
    }
}
