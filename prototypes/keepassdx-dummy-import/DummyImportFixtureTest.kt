/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 * Instrumentation-only, public dummy data in a fresh private test directory.
 * Never supply a user's vault directory, key or data. No UI/process-kill/SAF claim.
 */
package com.kunzisoft.keepass.tests.saku

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.keepass.database.exception.DatabaseException
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class DummyImportFixtureTest {
    private var directory: File? = null
    private var fixtureOrNull: DummyImportFixture? = null
    private val fixture: DummyImportFixture get() = requireNotNull(fixtureOrNull)

    @Before fun setUp() {
        assumeTrue("Atomic private-file replacement requires API26", android.os.Build.VERSION.SDK_INT >= 26)
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val fresh = File.createTempFile("saku-dummy-", "", cache)
        check(fresh.delete() && fresh.mkdir())
        directory = fresh
        fixtureOrNull = DummyImportFixture(fresh)
    }
    @After fun tearDown() {
        try { fixtureOrNull?.close() }
        finally {
            try { directory?.deleteRecursively() }
            finally { fixtureOrNull = null; directory = null }
        }
    }
    private inline fun <reified T : Throwable> expectFailure(action: () -> Unit) {
        val failure = try { action(); null } catch (caught: Exception) { caught }
        assertNotNull("Expected failure: ${T::class.java.simpleName}", failure)
        var cause: Throwable? = failure
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>())
        while (cause != null && cause !is T && visited.add(cause)) {
            // Upstream DatabaseException stores its wrapped error separately.
            cause = cause.cause ?: (cause as? DatabaseException)?.mThrowable
        }
        assertTrue("Expected cause ${T::class.java.simpleName}, got ${failure?.javaClass?.simpleName}", cause is T)
    }
    private fun restart() {
        fixture.close()
        fixtureOrNull = DummyImportFixture(requireNotNull(directory))
    }
    @Test fun lockedAndCancelNeverCreateEntry() {
        expectFailure<IllegalStateException> { fixture.preview() }
        expectFailure<IllegalStateException> { fixture.approve() }
        assertEquals(0, fixture.committedEntryCount())
        fixture.unlockFixture()
        assertEquals("Saku synthetic entry", fixture.preview().title)
        fixture.cancel()
        assertEquals(DummyImportFixture.State.CANCELLED, fixture.state)
        assertEquals(0, fixture.committedEntryCount())
    }
    @Test fun approvedCreateReopensAndReplayDoesNotDuplicate() {
        fixture.unlockFixture(); fixture.preview(); fixture.approve()
        assertEquals(DummyImportFixture.State.VERIFIED_FILE, fixture.state)
        assertEquals(1, fixture.committedEntryCount())
        restart()
        fixture.unlockFixture(); fixture.approve()
        assertEquals(1, fixture.committedEntryCount())
    }
    @Test fun conflictingDepositDoesNotOverwriteExistingEntry() {
        fixture.seedConflictingFixture()
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.ConflictingDeposit> { fixture.approve() }
        assertEquals(DummyImportFixture.State.RECOVERY_REQUIRED, fixture.state)
        assertEquals(1, fixture.committedEntryCount())
        restart()
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.ConflictingDeposit> { fixture.approve() }
    }
    @Test fun nullOutputCannotAcknowledgeOrReplace() {
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.CandidateMissing> { fixture.approve(DummyImportFixture.Fault.NULL_OUTPUT) }
        assertEquals(DummyImportFixture.State.RECOVERY_REQUIRED, fixture.state)
        assertEquals(0, fixture.committedEntryCount())
    }
    @Test fun throwingPartialOutputCannotAcknowledgeOrReplace() {
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.PartialWrite> { fixture.approve(DummyImportFixture.Fault.PARTIAL_OUTPUT) }
        assertEquals(DummyImportFixture.State.RECOVERY_REQUIRED, fixture.state)
        assertEquals(0, fixture.committedEntryCount())
    }
    @Test fun silentTruncationIsRejectedByReopenVerification() {
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.CandidateInvalid> { fixture.approve(DummyImportFixture.Fault.SILENT_TRUNCATION) }
        assertEquals(DummyImportFixture.State.RECOVERY_REQUIRED, fixture.state)
        assertEquals(0, fixture.committedEntryCount())
    }
    @Test fun restartAfterReplaceBeforeAcknowledgementDoesNotDuplicate() {
        fixture.unlockFixture()
        expectFailure<DummyImportFixture.InterruptedAfterReplace> { fixture.approve(DummyImportFixture.Fault.AFTER_REPLACE) }
        assertEquals(DummyImportFixture.State.RECOVERY_REQUIRED, fixture.state)
        restart()
        assertEquals(1, fixture.committedEntryCount())
        fixture.unlockFixture(); fixture.approve()
        assertEquals(DummyImportFixture.State.VERIFIED_FILE, fixture.state)
        assertEquals(1, fixture.committedEntryCount())
    }
}
