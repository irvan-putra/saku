package com.vanipy.saku.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bounded background receive for the sync prototype. Starts the runtime,
 * polls once for SYNC1 envelopes, and applies each through SyncApplier
 * (SyncMerge-gated): APPLY_REMOTE writes the payload automatically (the
 * original requirement), CONFLICT is filed byte-untouched, never resolved
 * silently.
 *
 * arm64-v8a only: on other ABIs the runtime is unavailable and the worker
 * reports success without doing anything (sync stays "unavailable", never
 * a crash-loop).
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK = "saku-sync-poll"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bridge = try {
            SyncBridge(applicationContext).also { it.start() }
        } catch (u: SyncBridge.RuntimeUnavailable) {
            return@withContext Result.success()
        }
        try {
            val applier = SyncApplier(File(applicationContext.filesDir, "vault-sync"))
            for (env in bridge.pollOnce(timeoutSec = 20)) {
                val remote = SyncEnvelope(
                    vaultId = env.vaultId,
                    deviceId = env.deviceId,
                    seq = env.seq,
                    basePayloadSha256 = env.baseSha256,
                    payloadSha256 = env.payloadSha256,
                    createdAtEpochMs = env.createdAtMs,
                )
                applier.apply(remote, env.payload)
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            bridge.stop()
        }
    }
}
