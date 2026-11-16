package com.vanipy.saku

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
    @Test fun renderMain() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val view = activity.window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 360, 640)
        val b = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(b))
        File("build/saku-mock-main-robolectric.png").outputStream().use {
            b.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
