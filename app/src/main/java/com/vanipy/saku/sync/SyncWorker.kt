package com.vanipy.saku.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Bounded background receive for the sync prototype. Starts the runtime,
 * polls once for SYNC1 envelopes, and files them under filesDir/sync-inbox
 * with a JSON sidecar. Nothing is applied to a vault here - automatic vault
 * application is the next milestone and keeps its original requirement.
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
            val inbox = File(applicationContext.filesDir, "sync-inbox").apply { mkdirs() }
            for (env in bridge.pollOnce(timeoutSec = 20)) {
                val name = "${env.createdAtMs}-${env.deviceId}-${env.seq}"
                File(inbox, "$name.sync1").writeBytes(env.payload)
                File(inbox, "$name.json").writeText(
                    JSONObject()
                        .put("vaultId", env.vaultId)
                        .put("deviceId", env.deviceId)
                        .put("seq", env.seq)
                        .put("baseSha256", env.baseSha256 ?: JSONObject.NULL)
                        .put("payloadSha256", env.payloadSha256)
                        .put("createdAtMs", env.createdAtMs)
                        .toString(2)
                )
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            bridge.stop()
        }
    }
}
