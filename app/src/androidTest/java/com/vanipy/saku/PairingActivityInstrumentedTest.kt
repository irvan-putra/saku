package com.vanipy.saku

import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties

/**
 * Pairing UI proof on the emulator: the screen renders, and tapping
 * "Show my pairing QR" reaches a terminal state. With a configured account
 * (ensured here from the CI-pushed config, so this class is independent of
 * SyncInstrumentedTest ordering) the invite QR must actually appear.
 *
 * The full SecureJoin round trip is proven at bridge level in
 * SyncInstrumentedTest; this class proves the UI wiring that shows it.
 */
@RunWith(AndroidJUnit4::class)
class PairingActivityInstrumentedTest {

    private fun attr(o: PyObject, name: String): PyObject =
        Python.getInstance().getBuiltins().callAttr("getattr", o, name)

    private fun ensureAccount() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx))
        val py = Python.getInstance()
        val mod = py.getModule("sync_runtime")
        val rt = mod.callAttr("SyncRuntime", ctx.filesDir.absolutePath,
            ctx.applicationInfo.nativeLibraryDir)
        rt.callAttr("start")
        try {
            val dc = attr(rt, "dc")
            val accounts = dc.callAttr("get_all_accounts").asList()
            val configured = accounts.any {
                it.callAttr("is_configured").toJava(Boolean::class.java)
            }
            if (!configured) {
                val f = File(ctx.getExternalFilesDir(null), "sync-test-config.properties")
                assertTrue("CI-pushed sync-test-config.properties missing", f.isFile)
                val cfg = Properties().apply { f.inputStream().use { load(it) } }
                val acct = dc.callAttr("add_account")
                acct.callAttr("set_config", "addr",
                    cfg.getProperty("addr.a") ?: error("addr.a missing"))
                acct.callAttr("set_config", "mail_pw",
                    cfg.getProperty("pw.a") ?: error("pw.a missing"))
                acct.callAttr("configure")
                assertTrue("account did not configure",
                    acct.callAttr("is_configured").toJava(Boolean::class.java))
            }
        } finally {
            rt.callAttr("stop")
        }
    }

    @Test
    fun showPairingQrRendersInvite() {
        ensureAccount()
        val initial = "Prototype pairing. arm64 devices only."
        ActivityScenario.launch(PairingActivity::class.java).use { scenario ->
            var qrVisible = false
            var statusText = ""
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                val texts = mutableListOf<TextView>()
                val buttons = mutableListOf<Button>()
                fun walk(v: android.view.View) {
                    if (v is Button) buttons.add(v) else if (v is TextView) texts.add(v)
                    if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                }
                walk(root)
                buttons.first { it.text.toString().startsWith("Show my pairing QR") }.performClick()
            }
            val end = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < end && !qrVisible) {
                scenario.onActivity { activity ->
                    val root = activity.findViewById<android.view.View>(android.R.id.content)
                    val texts = mutableListOf<TextView>()
                    val images = mutableListOf<android.widget.ImageView>()
                    fun walk(v: android.view.View) {
                        when (v) {
                            is Button -> Unit
                            is TextView -> texts.add(v)
                            is android.widget.ImageView -> images.add(v)
                        }
                        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                    }
                    walk(root)
                    statusText = texts.firstOrNull { it.textSize == 14f }?.text?.toString() ?: ""
                    qrVisible = images.any { it.visibility == android.view.View.VISIBLE && it.drawable != null }
                }
                if (!qrVisible) Thread.sleep(2_000)
            }
            assertNotEquals("pairing must leave the initial status", initial, statusText)
            assertTrue("invite QR must become visible, status was: $statusText", qrVisible)
        }
    }
}
