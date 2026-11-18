package com.vanipy.saku

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** The demo widget shows locked-vault status and quick-open only: exact static
 *  strings, no counts, no strength, no other vault metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VaultStatusWidgetTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"


    @Test fun widgetStringsKeepLockedOnlyDemoFraming() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("Vault: locked", ctx.getString(R.string.widget_status_locked))
        assertEquals("Demo widget - synthetic data only", ctx.getString(R.string.widget_demo_note))
        assertEquals("saku vault status (demo, no real secrets)", ctx.getString(R.string.widget_description))
    }

    @Test fun layoutContainsOnlyStatusFieldsNoMetadata() {
        val res = RuntimeEnvironment.getApplication().resources
        val parser = res.getLayout(R.layout.widget_vault_status)
        val ids = mutableSetOf<Int>()
        val texts = mutableSetOf<Int>()
        val tagCounts = mutableMapOf<String, Int>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                tagCounts.merge(parser.name, 1, Int::plus)
                val id = parser.getAttributeResourceValue(androidNs, "id", 0)
                if (id != 0) ids.add(id)
                val text = parser.getAttributeResourceValue(androidNs, "text", 0)
                if (text != 0) texts.add(text)
            }
            parser.next()
        }
        assertEquals(
            setOf(R.id.widget_container, R.id.widget_icon, R.id.widget_title,
                R.id.widget_status, R.id.widget_demo_note), ids)
        assertEquals(3, tagCounts["TextView"])
        assertEquals(setOf(R.string.widget_title, R.string.widget_status_locked,
            R.string.widget_demo_note), texts)
    }

    @Test fun providerInfoIsStaticWithNoPeriodicUpdates() {
        val res = RuntimeEnvironment.getApplication().resources
        val parser = res.getXml(R.xml.widget_vault_status_info)
        var found = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                found = true
                assertEquals(0, parser.getAttributeIntValue(androidNs, "updatePeriodMillis", -1))
                assertEquals(0, parser.getAttributeIntValue(androidNs, "resizeMode", -1))
                assertEquals(R.layout.widget_vault_status,
                    parser.getAttributeResourceValue(androidNs, "initialLayout", 0))
            }
            parser.next()
        }
        assertTrue("appwidget-provider root not found", found)
    }

    @Test fun quickOpenTargetsMainActivityAndViewsBuild() {
        val ctx = RuntimeEnvironment.getApplication()
        val intent = VaultStatusWidgetProvider.openAppIntent(ctx)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNotNull(VaultStatusWidgetProvider.buildViews(ctx)) // RemoteViews build must not throw
    }

    @Test fun providerReceiverIsRegisteredNonExported() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_RECEIVERS)
        val receivers = info.receivers?.map { it.name } ?: emptyList()
        assertTrue("VaultStatusWidgetProvider receiver missing from merged manifest",
            receivers.contains("com.vanipy.saku.VaultStatusWidgetProvider"))
    }
}
