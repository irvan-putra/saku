package com.vanipy.saku.sync

/**
 * Merge decision between the local vault state and an incoming sync
 * envelope. Pure logic over envelope metadata, no I/O and no crypto: the
 * payload bytes are only ever applied by the vault layer, which has its own
 * integrity checks, and only after this decision says APPLY_REMOTE.
 *
 * Model: one vault (vaultId), monotonically increasing seq per writer,
 * basePayloadSha256 naming the payload the writer based its revision on.
 * Groundwork semantics are deliberately conservative - divergence is a
 * CONFLICT for a human/flow to resolve, never silent data loss.
 */
object SyncMerge {
    enum class Decision { APPLY_REMOTE, KEEP_LOCAL, SAME, CONFLICT }

    fun decide(local: SyncEnvelope?, remote: SyncEnvelope): Decision {
        if (local == null) return Decision.APPLY_REMOTE
        if (local.vaultId != remote.vaultId) return Decision.CONFLICT
        if (remote.seq > local.seq) return Decision.APPLY_REMOTE
        if (remote.seq < local.seq) return Decision.KEEP_LOCAL
        return if (remote.payloadSha256 == local.payloadSha256) Decision.SAME else Decision.CONFLICT
    }
}
