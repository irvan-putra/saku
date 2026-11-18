package com.vanipy.saku.sync

import java.security.MessageDigest

/**
 * DeltaChat sync envelope, groundwork stage: a versioned, bounded, self-
 * checking container for a vault sync payload plus the metadata the merge
 * decider needs. Transport (DeltaChat attachment) is out of scope here;
 * this is local encode/decode only - no accounts, no network, no secrets.
 *
 * Synthetic-only by construction: every id must carry the SYNTHETIC- prefix,
 * so a real vault id can never be encoded or accepted while the app is a
 * prototype. Drop the gate only with the real-secret readiness review.
 *
 * Wire format (canonical, deterministic):
 *   line 1: "SYNC1"
 *   line 2: vaultId
 *   line 3: deviceId
 *   line 4: seq (decimal, >= 0)
 *   line 5: basePayloadSha256 (64 lowercase hex, or "-" for none)
 *   line 6: payloadSha256 (64 lowercase hex of the payload bytes)
 *   line 7: createdAtEpochMs (decimal, >= 0)
 *   then one empty line, then the raw payload bytes.
 */
data class SyncEnvelope(
    val vaultId: String,
    val deviceId: String,
    val seq: Long,
    val basePayloadSha256: String?,
    val payloadSha256: String,
    val createdAtEpochMs: Long,
) {
    companion object {
        const val VERSION = "SYNC1"
        const val MAX_HEADER_BYTES = 4096
        const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
        private val HEX64 = Regex("[0-9a-f]{64}")
        private val ID = Regex("SYNTHETIC-[A-Za-z0-9._-]{1,64}")

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        private fun requireId(id: String, what: String) {
            require(ID.matches(id)) { "$what must be a synthetic id (SYNTHETIC-...)" }
        }

        fun create(
            vaultId: String,
            deviceId: String,
            seq: Long,
            basePayloadSha256: String?,
            createdAtEpochMs: Long,
            payload: ByteArray,
        ): Pair<SyncEnvelope, ByteArray> {
            requireId(vaultId, "vaultId"); requireId(deviceId, "deviceId")
            require(seq >= 0) { "seq must be >= 0" }
            require(createdAtEpochMs >= 0) { "createdAtEpochMs must be >= 0" }
            require(basePayloadSha256 == null || HEX64.matches(basePayloadSha256)) { "bad base hash" }
            require(payload.size <= MAX_PAYLOAD_BYTES) { "payload too large" }
            val env = SyncEnvelope(vaultId, deviceId, seq, basePayloadSha256, sha256(payload), createdAtEpochMs)
            return env to encode(env, payload)
        }

        fun encode(env: SyncEnvelope, payload: ByteArray): ByteArray {
            requireId(env.vaultId, "vaultId"); requireId(env.deviceId, "deviceId")
            require(HEX64.matches(env.payloadSha256)) { "bad payload hash" }
            require(env.basePayloadSha256 == null || HEX64.matches(env.basePayloadSha256)) { "bad base hash" }
            require(env.payloadSha256 == sha256(payload)) { "payload hash mismatch" }
            require(payload.size <= MAX_PAYLOAD_BYTES) { "payload too large" }
            val header = listOf(
                VERSION, env.vaultId, env.deviceId, env.seq.toString(),
                env.basePayloadSha256 ?: "-", env.payloadSha256, env.createdAtEpochMs.toString(),
            ).joinToString("\n") + "\n\n"
            val head = header.toByteArray(Charsets.UTF_8)
            require(head.size <= MAX_HEADER_BYTES) { "header too large" }
            return head + payload
        }

        /** Fail-closed parse: any malformed, oversized or hash-mismatching
         *  input throws IllegalArgumentException. */
        fun decode(bytes: ByteArray): Pair<SyncEnvelope, ByteArray> {
            require(bytes.size <= MAX_HEADER_BYTES + MAX_PAYLOAD_BYTES) { "envelope too large" }
            val text = bytes.copyOfRange(0, minOf(bytes.size, MAX_HEADER_BYTES + 2))
                .toString(Charsets.UTF_8)
            val sep = text.indexOf("\n\n")
            require(sep >= 0) { "missing header terminator" }
            val lines = text.substring(0, sep).split("\n")
            require(lines.size == 7) { "expected 7 header lines, got ${lines.size}" }
            require(lines[0] == VERSION) { "unsupported version" }
            val headerBytes = (text.substring(0, sep + 2)).toByteArray(Charsets.UTF_8).size
            val payload = bytes.copyOfRange(headerBytes, bytes.size)
            require(payload.size <= MAX_PAYLOAD_BYTES) { "payload too large" }
            requireId(lines[1], "vaultId"); requireId(lines[2], "deviceId")
            val seq = lines[3].toLongOrNull(); require(seq != null && seq >= 0) { "bad seq" }
            val base = if (lines[4] == "-") null else lines[4]
            require(base == null || HEX64.matches(base)) { "bad base hash" }
            require(HEX64.matches(lines[5])) { "bad payload hash" }
            val at = lines[6].toLongOrNull(); require(at != null && at >= 0) { "bad timestamp" }
            require(lines[5] == sha256(payload)) { "payload hash mismatch" }
            return SyncEnvelope(lines[1], lines[2], seq, base, lines[5], at) to payload
        }
    }
}
