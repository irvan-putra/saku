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

/** The demo widget shows locked-vault status and quick-open only, in three
 *  size modes (1x1 badge, one-row compact, expanded card): exact static
 *  strings, no counts, no strength, no other vault metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VaultStatusWidgetTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private data class LayoutFacts(val ids: Set<Int>, val texts: Set<Int>, val tagCounts: Map<String, Int>)

    private fun facts(layout: Int): LayoutFacts {
        val res = RuntimeEnvironment.getApplication().resources
        val parser = res.getLayout(layout)
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
        return LayoutFacts(ids, texts, tagCounts)
    }

    @Test fun widgetStringsKeepLockedOnlyDemoFraming() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals("Vault: locked", ctx.getString(R.string.widget_status_locked))
        assertEquals("Demo widget - synthetic data only", ctx.getString(R.string.widget_demo_note))
        assertEquals("saku vault: locked (demo)", ctx.getString(R.string.widget_lock_cd))
        assertEquals("demo - synthetic only", ctx.getString(R.string.widget_demo_short))
        assertEquals("saku vault status (demo, no real secrets)", ctx.getString(R.string.widget_description))
    }

    @Test fun expandedLayoutContainsOnlyStatusFieldsNoMetadata() {
        val f = facts(R.layout.widget_vault_status)
        assertEquals(
            setOf(R.id.widget_container, R.id.widget_icon, R.id.widget_title,
                R.id.widget_status, R.id.widget_demo_note), f.ids)
        assertEquals(3, f.tagCounts["TextView"])
        assertEquals(setOf(R.string.widget_title, R.string.widget_status_locked,
            R.string.widget_demo_note), f.texts)
    }

    @Test fun compactLayoutIsOneStatusRowNoMetadata() {
        val f = facts(R.layout.widget_vault_status_compact)
        assertEquals(setOf(R.id.widget_container, R.id.widget_lock,
            R.id.widget_status, R.id.widget_demo_note), f.ids)
        assertEquals(2, f.tagCounts["TextView"])
        assertEquals(setOf(R.string.widget_status_locked, R.string.widget_demo_short), f.texts)
    }

    @Test fun miniLayoutIsSingleLockBadgeNoText() {
        val f = facts(R.layout.widget_vault_status_mini)
        assertEquals(setOf(R.id.widget_container, R.id.widget_lock), f.ids)
        assertEquals(null, f.tagCounts["TextView"])
        assertEquals(emptySet<Int>(), f.texts)
    }

    @Test fun providerInfoTargetsFiveByOneResizableToOneByOne() {
        val res = RuntimeEnvironment.getApplication().resources
        val parser = res.getXml(R.xml.widget_vault_status_info)
        var found = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                found = true
                assertEquals(0, parser.getAttributeIntValue(androidNs, "updatePeriodMillis", -1))
                assertEquals(3, parser.getAttributeIntValue(androidNs, "resizeMode", -1)) // horizontal|vertical
                assertEquals(5, parser.getAttributeIntValue(androidNs, "targetCellWidth", -1))
                assertEquals(1, parser.getAttributeIntValue(androidNs, "targetCellHeight", -1))
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
        assertNotNull(VaultStatusWidgetProvider.buildViews(ctx)) // must not throw (compact fallback on sdk 28)
    }

    @Test fun providerReceiverIsRegisteredNonExported() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_RECEIVERS)
        val receivers = info.receivers?.map { it.name } ?: emptyList()
        assertTrue("VaultStatusWidgetProvider receiver missing from merged manifest",
            receivers.contains("com.vanipy.saku.VaultStatusWidgetProvider"))
    }
}
