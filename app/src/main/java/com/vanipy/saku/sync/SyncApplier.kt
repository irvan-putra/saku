package com.vanipy.saku.sync

import java.io.File

/**
 * Applies incoming sync envelopes to the sync-received vault store, behind
 * SyncMerge's decision. Persists only the current head metadata per vaultId;
 * payload bytes are stored verbatim (the vault layer owns parsing/crypto).
 *
 * CONFLICT never overwrites: the remote bytes go under conflicts/
 * byte-untouched for a human/flow to resolve, matching the groundwork's
 * "divergent same-seq CONFLICT byte-untouched" emulator evidence.
 *
 * Synthetic prototype: payloads are SYNC1 test payloads, not real KDBX data.
 * Automatic-save requirement unchanged: APPLY_REMOTE writes happen with no
 * unlock step; any unlock/preview UX is only a proposal.
 *
 * State is a 6-line envelope-style file (no JSON dependency) so host tests
 * stay plain JUnit:
 *   vaultId \n deviceId \n seq \n baseSha256-or-"-" \n payloadSha256 \n createdAt
 */
class SyncApplier(private val rootDir: File) {

    enum class Outcome { APPLIED, KEPT_LOCAL, ALREADY_SAME, CONFLICT_FILED }

    private fun vaultDir(vaultId: String) = File(rootDir, vaultId).apply { mkdirs() }
    private fun stateFile(vaultId: String) = File(vaultDir(vaultId), "state.syncstate")
    private fun payloadFile(vaultId: String) = File(vaultDir(vaultId), "current.payload")

    /** Current head as envelope metadata, or null when nothing applied yet. */
    fun head(vaultId: String): SyncEnvelope? {
        val f = stateFile(vaultId)
        if (!f.exists()) return null
        val l = f.readLines()
        require(l.size == 6) { "corrupt sync state for $vaultId" }
        return SyncEnvelope(
            vaultId = l[0],
            deviceId = l[1],
            seq = l[2].toLong(),
            basePayloadSha256 = if (l[3] == "-") null else l[3],
            payloadSha256 = l[4],
            createdAtEpochMs = l[5].toLong(),
        )
    }

    /** Bytes of the currently applied payload, or null. */
    fun currentPayload(vaultId: String): ByteArray? =
        payloadFile(vaultId).takeIf { it.exists() }?.readBytes()

    fun apply(remote: SyncEnvelope, payload: ByteArray): Outcome {
        require(remote.payloadSha256 == SyncEnvelope.sha256(payload)) { "payload hash mismatch" }
        return when (SyncMerge.decide(head(remote.vaultId), remote)) {
            SyncMerge.Decision.APPLY_REMOTE -> {
                payloadFile(remote.vaultId).writeBytes(payload)
                stateFile(remote.vaultId).writeText(stateText(remote))
                Outcome.APPLIED
            }
            SyncMerge.Decision.KEEP_LOCAL -> Outcome.KEPT_LOCAL
            SyncMerge.Decision.SAME -> Outcome.ALREADY_SAME
            SyncMerge.Decision.CONFLICT -> {
                val cdir = File(vaultDir(remote.vaultId), "conflicts").apply { mkdirs() }
                val stem = "${remote.createdAtEpochMs}-${remote.deviceId}-${remote.seq}"
                File(cdir, "$stem.payload").writeBytes(payload)
                File(cdir, "$stem.syncstate").writeText(stateText(remote))
                Outcome.CONFLICT_FILED
            }
        }
    }

    private fun stateText(env: SyncEnvelope): String = listOf(
        env.vaultId, env.deviceId, env.seq.toString(),
        env.basePayloadSha256 ?: "-", env.payloadSha256, env.createdAtEpochMs.toString(),
    ).joinToString("\n") + "\n"
}
