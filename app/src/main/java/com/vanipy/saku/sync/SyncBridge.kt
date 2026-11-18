package com.vanipy.saku.sync

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Kotlin bridge to the python SyncRuntime (app/src/main/python/sync_runtime.py).
 *
 * Every call can block on network I/O (IMAP/SMTP via the bundled rpc server):
 * NEVER call from the main thread. Use SyncWorker or your own executor.
 *
 * Prototype scope: synthetic SYNTHETIC- ids only (enforced again python-side),
 * arm64-v8a only (librpcsrv.so has no other ABI). On any other ABI start()
 * throws RuntimeUnavailable; callers must treat sync as unavailable, not crash.
 */
class SyncBridge(context: Context) {

    class RuntimeUnavailable(message: String, cause: Throwable?) : Exception(message, cause)

    data class Incoming(
        val vaultId: String,
        val deviceId: String,
        val seq: Long,
        val baseSha256: String?,
        val payloadSha256: String,
        val createdAtMs: Long,
        val payload: ByteArray,
    )

    private val runtime: PyObject

    init {
        val app = context.applicationContext
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(app))
            }
            val mod = Python.getInstance().getModule("sync_runtime")
            runtime = mod.callAttr(
                "SyncRuntime",
                app.filesDir.absolutePath,
                app.applicationInfo.nativeLibraryDir,
            )
        } catch (t: Throwable) {
            throw RuntimeUnavailable("python sync runtime unavailable: ${t.message}", t)
        }
    }

    fun start() {
        try {
            runtime.callAttr("start")
        } catch (t: Throwable) {
            throw RuntimeUnavailable("sync runtime start failed: ${t.message}", t)
        }
    }

    fun stop() = runtime.callAttr("stop")

    /** This device's sync address, once an account is configured. */
    fun accountAddr(): String = runtime.callAttr("account_addr").toString()

    /** This device invites: QR text to show the peer (contains the fingerprint). */
    fun pairQr(): String = runtime.callAttr("pair_qr").toString()

    /** This device joins the peer's invite QR. */
    fun acceptQr(qrText: String) {
        runtime.callAttr("accept_qr", qrText)
    }

    fun sendEnvelope(
        peerAddr: String, vaultId: String, deviceId: String, seq: Long,
        baseSha256: String?, createdAtMs: Long, payload: ByteArray,
    ) {
        runtime.callAttr(
            "send_envelope", peerAddr, vaultId, deviceId, seq,
            baseSha256, createdAtMs, payload,
        )
    }

    /** One bounded poll; returns every SYNC1 envelope received in the window. */
    fun pollOnce(timeoutSec: Int): List<Incoming> {
        val rows = runtime.callAttr("poll_once", timeoutSec).asList()
        return rows.map { row ->
            val pair = row.asList()
            val fields = pair[0].asMap()
            Incoming(
                vaultId = fields[PyObject.fromJava("vault_id")].toString(),
                deviceId = fields[PyObject.fromJava("device_id")].toString(),
                seq = fields[PyObject.fromJava("seq")].toJava(Long::class.java),
                baseSha256 = fields[PyObject.fromJava("base_sha256")]?.toString(),
                payloadSha256 = fields[PyObject.fromJava("payload_sha256")].toString(),
                createdAtMs = fields[PyObject.fromJava("created_at_ms")].toJava(Long::class.java),
                payload = pair[1].toJava(ByteArray::class.java),
            )
        }
    }
}
