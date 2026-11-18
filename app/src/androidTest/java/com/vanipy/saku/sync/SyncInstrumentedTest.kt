package com.vanipy.saku.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/**
 * Live on-emulator proof of the sync stack, synthetic accounts only.
 *
 * Ordered: one app holds one account dir, so account A lives in the app's
 * real filesDir (exactly what SyncBridge and SyncWorker use) and peer B
 * stands in via a second SyncRuntime on filesDir/b (a device cannot run two
 * installs). Credentials arrive as a CI-pushed properties file in the app's
 * external files dir; they are never hardcoded and never printed.
 *
 * t01 configures, t02 proves SecureJoin pairing, t03 proves the Kotlin
 * bridge round trip byte-exact, t04 proves SyncWorker itself receives and
 * applies through SyncApplier on device.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncInstrumentedTest {

    companion object {
        private lateinit var ctx: Context
        private lateinit var py: Python
        private lateinit var mod: PyObject
        private lateinit var addrA: String
        private lateinit var addrB: String
        private var rtB: PyObject? = null

        private fun attr(o: PyObject, name: String): PyObject =
            py.getBuiltins().callAttr("getattr", o, name)

        private fun configure(rt: PyObject, addr: String, pw: String) {
            val acct = attr(rt, "dc").callAttr("add_account")
            acct.callAttr("set_config", "addr", addr)
            acct.callAttr("set_config", "mail_pw", pw)
            acct.callAttr("configure")
            assertTrue("account did not configure",
                acct.callAttr("is_configured").toJava(Boolean::class.java))
        }

        private fun sha256hex(b: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(b)
                .joinToString("") { "%02x".format(it) }

        /** poll_once on a raw python runtime, parsed like SyncBridge does. */
        private fun pollPy(rt: PyObject, timeoutSec: Int): List<SyncBridge.Incoming> {
            val rows = rt.callAttr("poll_once", timeoutSec).asList()
            return rows.map { row ->
                val pair = row.asList()
                val fields = pair[0].asMap()
                fun req(key: String): PyObject =
                    fields[PyObject.fromJava(key)] ?: error("missing field $key")
                SyncBridge.Incoming(
                    vaultId = req("vault_id").toString(),
                    deviceId = req("device_id").toString(),
                    seq = req("seq").toJava(Long::class.java),
                    baseSha256 = fields[PyObject.fromJava("base_sha256")]?.toString(),
                    payloadSha256 = req("payload_sha256").toString(),
                    createdAtMs = req("created_at_ms").toJava(Long::class.java),
                    payload = pair[1].toJava(ByteArray::class.java),
                )
            }
        }

        private fun waitVerified(rt: PyObject, peer: String, timeoutMs: Long = 90_000) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                try {
                    rt.callAttr("verified_chat", peer)
                    return
                } catch (t: Throwable) {
                    if (System.currentTimeMillis() > deadline)
                        throw AssertionError("no verified chat for peer within ${timeoutMs}ms")
                    Thread.sleep(2_000)
                }
            }
        }
    }

    @Test
    fun t01_configureAccounts() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        py = Python.getInstance()
        mod = py.getModule("sync_runtime")
        val f = File(ctx.getExternalFilesDir(null), "sync-test-config.properties")
        assertTrue("CI-pushed sync-test-config.properties missing", f.isFile)
        val cfg = Properties().apply { f.inputStream().use { load(it) } }
        addrA = cfg.getProperty("addr.a") ?: error("addr.a missing")
        addrB = cfg.getProperty("addr.b") ?: error("addr.b missing")
        val nativeLib = ctx.applicationInfo.nativeLibraryDir
        // Account A into the app's real filesDir: the path SyncBridge and
        // SyncWorker actually use.
        val rtSetup = mod.callAttr("SyncRuntime", ctx.filesDir.absolutePath, nativeLib)
        rtSetup.callAttr("start")
        configure(rtSetup, addrA, cfg.getProperty("pw.a") ?: error("pw.a missing"))
        rtSetup.callAttr("stop")
        // Peer B on its own files dir; same shipped module and librpcsrv.so.
        val b = mod.callAttr("SyncRuntime", File(ctx.filesDir, "b").absolutePath, nativeLib)
        b.callAttr("start")
        configure(b, addrB, cfg.getProperty("pw.b") ?: error("pw.b missing"))
        rtB = b
        Thread.sleep(8_000) // let IO settle, per the proven transport flow
    }

    @Test
    fun t02_pairSecureJoin() {
        val bridge = SyncBridge(ctx).also { it.start() }
        try {
            val qr = bridge.pairQr()
            assertTrue("pair QR must be non-empty", qr.isNotEmpty())
            rtB!!.callAttr("accept_qr", qr)
            // Peer side must end up with the verified 1:1 chat to A.
            waitVerified(rtB!!, bridge.accountAddr())
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun t03_bridgeRoundTripByteExact() {
        val bridge = SyncBridge(ctx).also { it.start() }
        try {
            val p1 = "SYNTHETIC-emu-v1-from-a".toByteArray()
            // The first send doubles as the bridge-side verified-chat proof:
            // send_envelope raises until the SecureJoin chat exists.
            val deadline = System.currentTimeMillis() + 90_000
            while (true) {
                try {
                    bridge.sendEnvelope(addrB, "SYNTHETIC-vault-sync",
                        "SYNTHETIC-device-a", 1, null, 1000, p1)
                    break
                } catch (t: Throwable) {
                    if (System.currentTimeMillis() > deadline) throw t
                    Thread.sleep(2_000)
                }
            }
            val inboxB = pollPy(rtB!!, 60)
            assertEquals("B must receive exactly one SYNC1 envelope", 1, inboxB.size)
            assertEquals(1L, inboxB[0].seq)
            assertEquals("SYNTHETIC-device-a", inboxB[0].deviceId)
            assertArrayEquals(p1, inboxB[0].payload)

            val base = sha256hex(p1)
            val p2 = "SYNTHETIC-emu-v2-from-b".toByteArray()
            rtB!!.callAttr("send_envelope", addrA, "SYNTHETIC-vault-sync",
                "SYNTHETIC-device-b", 2L, base, 2000L, p2)
            val inboxA = bridge.pollOnce(60)
            assertTrue("bridge received nothing", inboxA.isNotEmpty())
            assertEquals(2L, inboxA[0].seq)
            assertEquals(base, inboxA[0].baseSha256)
            assertArrayEquals(p2, inboxA[0].payload)
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun t04_workerReceivesAndApplies() {
        // B -> A one more envelope; then the WORKER (not test code) must
        // receive it and apply it through SyncApplier on device.
        val p3 = "SYNTHETIC-emu-v3-worker".toByteArray()
        rtB!!.callAttr("send_envelope", addrA, "SYNTHETIC-vault-sync",
            "SYNTHETIC-device-b", 3L, null, 3000L, p3)
        Thread.sleep(5_000) // let the relay deliver
        val worker = TestListenableWorkerBuilder<SyncWorker>(ctx).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        val applier = SyncApplier(File(ctx.filesDir, "vault-sync"))
        assertArrayEquals(p3, applier.currentPayload("SYNTHETIC-vault-sync"))
        val head = applier.head("SYNTHETIC-vault-sync")
        assertNotNull(head)
        assertEquals(3L, head!!.seq)
    }

    @Test
    fun t99_stopPeer() {
        rtB?.callAttr("stop")
        rtB = null
    }
}
