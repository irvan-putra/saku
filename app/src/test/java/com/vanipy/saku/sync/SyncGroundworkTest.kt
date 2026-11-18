package com.vanipy.saku.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for the sync groundwork: envelope codec, bounds, synthetic
 *  gate and merge decisions. Synthetic payloads only. */
class SyncGroundworkTest {
    private val payload = "SYNTHETIC-payload-bytes".toByteArray()

    private fun env(seq: Long, vault: String = "SYNTHETIC-vault-a", device: String = "SYNTHETIC-device-1",
                    base: String? = null, at: Long = 1000L, body: ByteArray = payload) =
        SyncEnvelope.create(vault, device, seq, base, at, body).first

    private fun assertRefused(body: ByteArray) {
        try {
            SyncEnvelope.decode(body)
            org.junit.Assert.fail("malformed envelope must be refused")
        } catch (expected: IllegalArgumentException) {}
    }

    @Test fun encodeDecodeRoundtrip() {
        val (e, bytes) = SyncEnvelope.create("SYNTHETIC-vault-a", "SYNTHETIC-device-1", 7,
            "0".repeat(64), 42L, payload)
        val (back, body) = SyncEnvelope.decode(bytes)
        assertEquals(e, back)
        assertArrayEquals(payload, body)
    }

    @Test fun deterministicEncoding() {
        val a = SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, payload).second
        val b = SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, payload).second
        assertArrayEquals(a, b)
    }

    @Test fun rejectsBadVersion() {
        val good = SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, payload).second
        val bad = "SYNC2".toByteArray() + good.copyOfRange(5, good.size)
        assertRefused(bad)
    }

    @Test fun rejectsTruncatedAndMissingLines() {
        val good = SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, payload).second
        assertRefused(good.copyOfRange(0, 20))
        assertRefused("SYNC1\nSYNTHETIC-v\nSYNTHETIC-d\n".toByteArray())
    }

    @Test fun rejectsPayloadHashMismatch() {
        val good = SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, payload).second
        val tampered = good.copyOf()
        tampered[tampered.size - 1] = (tampered.last() + 1).toByte()
        assertRefused(tampered)
    }

    @Test fun rejectsOversizedPayload() {
        val big = ByteArray(SyncEnvelope.MAX_PAYLOAD_BYTES + 1)
        try {
            SyncEnvelope.create("SYNTHETIC-v", "SYNTHETIC-d", 1, null, 5L, big)
            org.junit.Assert.fail("oversized payload must be refused")
        } catch (expected: IllegalArgumentException) {}
    }

    @Test fun rejectsNonSyntheticIds() {
        for (bad in listOf("real-vault", "synthetic-v", "", "SYNTHETIC-" + "x".repeat(100))) {
            try {
                SyncEnvelope.create(bad, "SYNTHETIC-d", 1, null, 5L, payload)
                org.junit.Assert.fail("non-synthetic vault id must be refused: '$bad'")
            } catch (expected: IllegalArgumentException) {}
        }
    }

    @Test fun mergeAppliesHigherSeqAndNewLocal() {
        assertEquals(SyncMerge.Decision.APPLY_REMOTE, SyncMerge.decide(env(3), env(4)))
        assertEquals(SyncMerge.Decision.APPLY_REMOTE, SyncMerge.decide(null, env(0)))
    }

    @Test fun mergeKeepsLocalOnLowerSeq() {
        assertEquals(SyncMerge.Decision.KEEP_LOCAL, SyncMerge.decide(env(4), env(3)))
    }

    @Test fun mergeSameSeqSamePayloadIsSame() {
        val a = env(4)
        val b = env(4, device = "SYNTHETIC-device-2")
        assertEquals(SyncMerge.Decision.SAME, SyncMerge.decide(a, b))
    }

    @Test fun mergeSameSeqDivergentOrDifferentVaultConflicts() {
        val other = env(4, body = "SYNTHETIC-other".toByteArray())
        assertEquals(SyncMerge.Decision.CONFLICT, SyncMerge.decide(env(4), other))
        assertEquals(SyncMerge.Decision.CONFLICT,
            SyncMerge.decide(env(4), env(4, vault = "SYNTHETIC-vault-b")))
    }

    @Test fun payloadHashMatchesSha256() {
        val e = env(0)
        assertTrue(e.payloadSha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(SyncEnvelope.sha256(payload), e.payloadSha256)
    }
}
