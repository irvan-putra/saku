package com.vanipy.saku

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Host spike for KeePassJava2 2.2.6 as a kdbx implementation CANDIDATE.
 *  Not an endorsement: 2.2.6 and 3.x both need interop/fuzz/memory/device
 *  spikes per the independent review. Synthetic passwords only. */
class KdbxSpikeTest {

    private fun creds(pw: String) =
        org.linguafranca.pwdb.kdbx.KdbxCreds(pw.toByteArray())

    @Test fun roundtripSyntheticEntry() {
        val db = org.linguafranca.pwdb.kdbx.simple.SimpleDatabase()
        val root = db.rootGroup
        val e = db.newEntry("example.test")
        e.username = "demo-user"; e.password = "SYNTHETIC-spike"; e.url = "https://example.test"
        root.addGroup(db.newGroup("sites")).addEntry(e)
        val out = ByteArrayOutputStream()
        val t0 = System.nanoTime()
        db.save(creds("SYNTHETIC-master"), out)
        // Synthetic roundtrip output remains in memory.
        val saveMs = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime()
        val back = org.linguafranca.pwdb.kdbx.simple.SimpleDatabase.load(
            creds("SYNTHETIC-master"), ByteArrayInputStream(out.toByteArray()))
        val loadMs = (System.nanoTime() - t1) / 1_000_000
        val found = back.findEntries("example.test")
        assertEquals(1, found.size)
        assertEquals("SYNTHETIC-spike", found[0].password)
        assertEquals("demo-user", found[0].username)
        println("kdbx host save=${saveMs}ms load=${loadMs}ms (A24 measurement still pending)")
    }

    @Test fun readsPykeepassRoundtrip() {
        val db = org.linguafranca.pwdb.kdbx.simple.SimpleDatabase.load(
            creds("SYNTHETIC-master"), javaClass.getResourceAsStream("/saku-pykeepass-roundtrip.kdbx"))
        assertEquals("SYNTHETIC-from-python", db.findEntries("python-roundtrip.test")[0].password)
    }

    @Test fun wrongPasswordFails() {
        val db = org.linguafranca.pwdb.kdbx.simple.SimpleDatabase()
        db.rootGroup.addEntry(db.newEntry("x"))
        val out = ByteArrayOutputStream(); db.save(creds("SYNTHETIC-a"), out)
        try {
            org.linguafranca.pwdb.kdbx.simple.SimpleDatabase.load(
                creds("SYNTHETIC-b"), ByteArrayInputStream(out.toByteArray()))
            fail("wrong master password must fail")
        } catch (expected: Exception) {}
    }

    @Test fun malformedInputsFailFast() {
        var rejected = 0
        for (i in 0 until 50) {
            val garbage = ByteArray(64 + i) { (it * 31 + i).toByte() }
            val t0 = System.currentTimeMillis()
            try {
                org.linguafranca.pwdb.kdbx.simple.SimpleDatabase.load(
                    creds("SYNTHETIC-fuzz"), ByteArrayInputStream(garbage))
            } catch (expected: Exception) { rejected++ }
            assertTrue("malformed load must return fast",
                System.currentTimeMillis() - t0 < 5000)
        }
        assertEquals(50, rejected)
    }
}
