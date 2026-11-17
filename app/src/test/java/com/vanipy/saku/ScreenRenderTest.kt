package com.vanipy.saku

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    private fun findBanner(v: View): TextView? {
        if (v is TextView && v.tag == DemoBanner.TAG) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findBanner(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Lays the screen out at 360x640, saves a PNG, and checks the persistent warning is
     *  on screen, at the top, visible and showing the exact warning text. */
    private fun renderAndCheck(activity: Activity, png: String) {
        val view = activity.window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 360, 640)
        val banner = findBanner(view)
        assertTrue("demo banner missing on $png", banner != null)
        assertEquals(DemoBanner.TEXT, banner!!.text.toString())
        assertEquals(View.VISIBLE, banner.visibility)
        assertTrue("banner has no height", banner.height > 0)
        val loc = IntArray(2); banner.getLocationInWindow(loc)
        assertTrue("banner not in the top 120px", loc[1] in 0..120)
        val b = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(b))
        File("build/$png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderMain() {
        renderAndCheck(Robolectric.buildActivity(MainActivity::class.java).setup().get(),
            "saku-mock-main-robolectric.png")
    }

    @Test fun renderDetail() {
        val i = Intent(org.robolectric.RuntimeEnvironment.getApplication(), EntryDetailActivity::class.java)
            .putExtra("site", "example.test").putExtra("username", "demo-user")
            .putExtra("password", "SYNTHETIC-correct-horse").putExtra("origin", "AGENT")
            .putExtra("version", 2).putExtra("lastChange", 1577880000000L).putExtra("lastSync", -1L)
        renderAndCheck(Robolectric.buildActivity(EntryDetailActivity::class.java, i).setup().get(),
            "saku-mock-detail-robolectric.png")
    }

    @Test fun renderPairing() {
        renderAndCheck(Robolectric.buildActivity(PairingActivity::class.java).setup().get(),
            "saku-mock-pairing-robolectric.png")
    }
}
