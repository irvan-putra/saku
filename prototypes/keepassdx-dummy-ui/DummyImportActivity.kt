/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 * Disposable synthetic harness only. Never register in the production app.
 */
package com.kunzisoft.keepass.tests.saku

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import android.widget.ScrollView

/** No intent data, URI picker, network, vault selector or arbitrary fields. */
class DummyImportActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var ready = false
    private var reviewed = false
    private var pausedPermanently = false
    private var inReview = false
    private var fixture: DummyImportFixture? = null
    private var directory: File? = null
    private lateinit var status: TextView
    private lateinit var preview: TextView
    private lateinit var unlock: Button
    private lateinit var approve: Button
    private lateinit var cancel: Button
    private lateinit var lock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Only this standalone public-dummy harness can opt into pixel capture.
        // Generated build flag defaults false; never use this in a real-vault app.
        if (com.vanipy.saku.synthetic.BuildConfig.DUMMY_PIXEL_CAPTURE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun label(text: String): TextView = TextView(this).also {
            it.text = text; layout.addView(it)
        }
        fun button(text: String, action: () -> Unit): Button = Button(this).also {
            it.text = text; it.setOnClickListener { action() }; layout.addView(it)
        }
        label("Synthetic import experiment")
        label("Public dummy data only. No phone vault, accounts or transport. Nothing here proves real password saving.")
        status = label("Preparing isolated dummy file")
        preview = label("Preview hidden while locked")
        unlock = button("Unlock dummy file and preview") {
            runOperation("Opening dummy file") {
                val current = requireNotNull(fixture)
                current.unlockFixture()
                val item = current.preview()
                "Title: ${item.title}\nUsername: ${item.username}\nSite: ${item.site}\nPassword: public fixed test value (not displayed)"
            }
        }
        approve = button("Approve one dummy entry") {
            runOperation("Checking dummy file") {
                requireNotNull(fixture).approve()
                "One dummy entry verified by reopening local file. No durability, phone or transport claim."
            }
        }
        cancel = button("Cancel without saving") {
            runOperation("Cancelling") {
                requireNotNull(fixture).cancel()
                "Cancelled. No new entry written."
            }
        }
        lock = button("Lock and hide preview") {
            runOperation("Locking") {
                requireNotNull(fixture).lock()
                "Locked. Preview hidden."
            }
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        setBusy(true)
        worker.execute {
            try {
                val fresh = File.createTempFile("saku-ui-dummy-", "", cacheDir)
                check(fresh.delete() && fresh.mkdir())
                directory = fresh
                fixture = DummyImportFixture(fresh)
                runOnUiThread {
                    if (!closed && !pausedPermanently) {
                        ready = true
                        status.text = "Dummy file locked"
                        setBusy(false)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!closed && !pausedPermanently) { status.text = "Dummy setup failed. No import available."; setBusy(true) }
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        val canReview = !busy && ready && inReview && !pausedPermanently
        unlock.isEnabled = !busy && ready && !canReview && !pausedPermanently
        approve.isEnabled = canReview && reviewed
        cancel.isEnabled = canReview
        lock.isEnabled = canReview
    }

    private fun runOperation(pending: String, action: () -> String) {
        setBusy(true)
        reviewed = false
        preview.text = "Preview hidden"
        status.text = pending
        worker.execute {
            try {
                val message = action()
                val inReview = fixture?.state == DummyImportFixture.State.REVIEW
                runOnUiThread {
                    if (!closed && !pausedPermanently) {
                        this@DummyImportActivity.inReview = inReview
                        reviewed = inReview
                        if (inReview) { preview.text = message; status.text = "Review the fixed dummy entry, then approve or cancel." }
                        else status.text = message
                        setBusy(false)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!closed && !pausedPermanently) {
                        inReview = false
                        reviewed = false
                        status.text = "Operation failed. Not acknowledged as saved. Reopen dummy file to review recovery."
                        setBusy(false)
                    }
                }
            }
        }
    }

    override fun onStop() {
        // A backgrounded dummy screen ends its session. Returning starts nothing.
        // Invalidate queued UI callbacks before serially locking behind any write.
        pausedPermanently = true
        reviewed = false
        inReview = false
        preview.text = "Preview hidden"
        setBusy(true)
        worker.execute {
            try { fixture?.lock() }
            catch (_: Exception) { /* Session remains permanently closed to UI. */ }
        }
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        closed = true
        // Queued after any active operation; never races close against a write.
        worker.execute {
            try { fixture?.close() }
            catch (_: Exception) { /* Best-effort upstream close; still clean owned files. */ }
            finally {
                try { directory?.deleteRecursively() }
                catch (_: Exception) { /* Disposable app-data wipe is the final fallback. */ }
            }
        }
        worker.shutdown()
        super.onDestroy()
    }
}
