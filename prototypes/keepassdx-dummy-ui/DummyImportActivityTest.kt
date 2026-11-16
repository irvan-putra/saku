/* SPDX-License-Identifier: GPL-3.0-or-later
 * Public dummy-only UI tests. Pixel-enabled build is emulator-only, never release.
 * Programmatic clicks test control wiring, not tap hitboxes or accessibility.
 */
package com.kunzisoft.keepass.tests.saku

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vanipy.saku.synthetic.BuildConfig
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DummyImportActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else emptyList()
    private fun texts(activity: DummyImportActivity): List<TextView> =
        views(activity.window.decorView).filterIsInstance<TextView>()
    private fun button(activity: DummyImportActivity, text: String): Button =
        texts(activity).filterIsInstance<Button>().single { it.text.toString() == text }

    private fun await(scenario: ActivityScenario<DummyImportActivity>, condition: (DummyImportActivity) -> Boolean) {
        val until = android.os.SystemClock.uptimeMillis() + 30_000
        while (android.os.SystemClock.uptimeMillis() < until) {
            instrumentation.waitForIdleSync()
            var done = false
            scenario.onActivity { done = condition(it) }
            if (done) return
            android.os.SystemClock.sleep(75)
        }
        fail("Synthetic UI condition timed out")
    }
    private fun hasText(activity: DummyImportActivity, fragment: String): Boolean =
        texts(activity).any { it.text.contains(fragment) }
    private fun click(scenario: ActivityScenario<DummyImportActivity>, text: String) {
        scenario.onActivity {
            val control = button(it, text)
            assertTrue("Expected enabled dummy control", control.isEnabled)
            assertTrue(control.performClick())
        }
    }
    private fun capture(name: String) {
        check(BuildConfig.DUMMY_PIXEL_CAPTURE) { "This visual suite requires explicit disposable pixel build" }
        instrumentation.waitForIdleSync()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.cacheDir, "dummy-ui-pixels")
        check(directory.isDirectory || directory.mkdir())
        try {
            File(directory, "$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
        // Private disposable cache only; no upload/release is implemented here.
    }
    private fun launch(): ActivityScenario<DummyImportActivity> {
        check(BuildConfig.DUMMY_PIXEL_CAPTURE)
        return ActivityScenario.launch(DummyImportActivity::class.java).also { scenario ->
            await(scenario) { button(it, "Unlock dummy file and preview").isEnabled }
        }
    }
    @Test fun previewThenCancelKeepsApproveDisabledUntilFreshReview() {
        launch().use { scenario ->
            scenario.onActivity { assertFalse(button(it, "Approve one dummy entry").isEnabled) }
            capture("01-locked")
            click(scenario, "Unlock dummy file and preview")
            await(scenario) { button(it, "Approve one dummy entry").isEnabled }
            scenario.onActivity { assertTrue(hasText(it, "dummy@example.invalid")) }
            capture("02-preview")
            click(scenario, "Cancel without saving")
            await(scenario) { hasText(it, "Cancelled. No new entry written.") }
            scenario.onActivity {
                assertFalse(button(it, "Approve one dummy entry").isEnabled)
                assertFalse(hasText(it, "Username: dummy@example.invalid"))
            }
            capture("03-cancelled")
        }
    }
    @Test fun approveRequiresPreviewThenShowsReopenedFileResult() {
        launch().use { scenario ->
            click(scenario, "Unlock dummy file and preview")
            await(scenario) { button(it, "Approve one dummy entry").isEnabled }
            click(scenario, "Approve one dummy entry")
            await(scenario) { hasText(it, "One dummy entry verified by reopening local file.") }
            scenario.onActivity {
                assertFalse(button(it, "Approve one dummy entry").isEnabled)
                assertFalse(hasText(it, "Username: dummy@example.invalid"))
            }
            capture("04-approved")
        }
    }
    @Test fun explicitLockHidesPreviewAndRequiresFreshUnlock() {
        launch().use { scenario ->
            click(scenario, "Unlock dummy file and preview")
            await(scenario) { button(it, "Approve one dummy entry").isEnabled }
            click(scenario, "Lock and hide preview")
            await(scenario) { hasText(it, "Locked. Preview hidden.") }
            scenario.onActivity {
                assertFalse(button(it, "Approve one dummy entry").isEnabled)
                assertTrue(button(it, "Unlock dummy file and preview").isEnabled)
                assertFalse(hasText(it, "Username: dummy@example.invalid"))
            }
            capture("05-relocked")
        }
    }
    @Test fun backgroundEndsSessionInsteadOfResumingUnlockedPreview() {
        launch().use { scenario ->
            click(scenario, "Unlock dummy file and preview")
            await(scenario) { button(it, "Approve one dummy entry").isEnabled }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            val until = android.os.SystemClock.uptimeMillis() + 10_000
            while (scenario.state != Lifecycle.State.DESTROYED && android.os.SystemClock.uptimeMillis() < until) {
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(75)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            capture("06-launcher-after-session-ended")
        }
    }
}
