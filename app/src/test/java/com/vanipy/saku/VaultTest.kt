package com.vanipy.saku

import org.junit.Assert.*
import org.junit.Test

class VaultTest {
    private fun entry(pw: String, archived: Boolean = false) =
        Entry("id", "site.test", "u", pw, Origin.SYNTHETIC, 1, 1000L, null, archived)

    @Test fun realPasswordRefused() {
        val v = MockVault()
        try { v.add(entry("hunter2")); fail("must refuse real-looking password") }
        catch (e: Refusal) { assertTrue(e.message!!.contains("refused")) }
        assertTrue(v.all().isEmpty())
    }

    @Test fun syntheticAccepted() {
        val v = MockVault(); v.add(entry("SYNTHETIC-x")); assertEquals(1, v.all().size)
    }

    @Test fun blankSiteRefused() {
        val v = MockVault()
        try { v.add(Entry("id", "", "u", "SYNTHETIC-x", Origin.SYNTHETIC, 1, 1L, null)); fail() }
        catch (e: Refusal) {}
    }

    @Test fun storeRefusesOpen() {
        try { UnimplementedVaultStore().open(charArrayOf('x')); fail() }
        catch (e: Refusal) { assertTrue(e.message!!.contains("not available")) }
    }

    @Test fun archiveFilter() {
        val list = listOf(entry("SYNTHETIC-a"), entry("SYNTHETIC-b", archived = true))
        assertEquals(1, ArchiveFilter.visible(list, false).size)
        assertEquals(2, ArchiveFilter.visible(list, true).size)
    }

    @Test fun modelCarriesBothTimestamps() {
        val e = Entry("id", "s", "u", "SYNTHETIC-x", Origin.AGENT, 2, 100L, 200L)
        assertEquals(100L, e.lastChangeMillis); assertEquals(200L, e.lastSyncMillis)
    }
}
