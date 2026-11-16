/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 * Test-only experiment. Not an inbound API, production vault or transport.
 */
package com.kunzisoft.keepass.tests.saku

import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.MasterCredential
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Owns only its newly-created private test directory; accepts no vault path/key/data. */
internal class DummyImportFixture(private val directory: File) : AutoCloseable {
    enum class State { LOCKED, REVIEW, CANCELLED, VERIFIED_FILE, RECOVERY_REQUIRED }
    enum class Fault { NONE, NULL_OUTPUT, PARTIAL_OUTPUT, SILENT_TRUNCATION, AFTER_REPLACE }
    class CandidateMissing : IOException("Synthetic candidate missing")
    class CandidateInvalid(cause: Exception) : IOException("Synthetic candidate failed reopen", cause)
    class PartialWrite : IOException("Synthetic partial write")
    class InterruptedAfterReplace : IOException("Synthetic interruption after replace")
    class ConflictingDeposit : IOException("Synthetic marker conflict")
    data class Preview(val title: String, val username: String, val site: String)

    companion object {
        private const val TITLE = "Saku synthetic entry"
        private const val USERNAME = "dummy@example.invalid"
        private const val SITE = "https://example.invalid"
        private const val DUMMY_PASSWORD = "PUBLIC-DUMMY-NOT-A-SECRET"
        private const val DUMMY_VAULT_KEY = "PUBLIC-SYNTHETIC-VAULT-ONLY"
        // Marker is deliberately visible metadata, not sender authentication.
        private const val MARKER = "Saku test deposit: fixed-fixture-v1"
        private val limits = Limits({ bytes, _ -> bytes <= 128_000_000uL }, 2L)
    }

    private val target = File(directory, "synthetic.kdbx")
    private var database: Database? = null
    var state = State.LOCKED
        private set

    init {
        // Only this test owns the fresh directory; never pass a user vault directory.
        require(directory.isDirectory)
        if (!target.exists()) {
            val fresh = Database()
            try {
                fresh.createData("Saku synthetic only", "Synthetic", null)
                writeAndVerify(fresh, null, Fault.NONE)
            } finally {
                fresh.clearAndClose()
            }
        }
    }

    private fun read(file: File): Database {
        val db = Database()
        val credential = MasterCredential(DUMMY_VAULT_KEY.toCharArray())
        try {
            file.inputStream().use {
                db.loadData(it, credential, { _, _ -> error("No hardware keys in fixture") },
                    false, false, directory, limits, false, null)
            }
            check(db.loaded)
            return db
        } catch (failure: Exception) {
            db.clearAndClose()
            throw failure
        } finally {
            credential.clear()
        }
    }

    private fun entries(db: Database): List<Entry> =
        requireNotNull(db.rootGroup).getChildEntries()

    private fun matches(entry: Entry): Boolean = entry.title == TITLE &&
        entry.username == USERNAME && entry.url == SITE &&
        entry.password.contentEquals(DUMMY_PASSWORD.toCharArray()) && entry.notes == MARKER

    /** Real KDBX decode using the public fixture key, not a phone-unlock simulation. */
    fun unlockFixture() {
        lock()
        database = read(target)
        state = State.REVIEW
    }

    /** Preview has no side effect and deliberately omits even the dummy password. */
    fun preview(): Preview {
        check(state == State.REVIEW && database != null)
        return Preview(TITLE, USERNAME, SITE)
    }

    fun cancel() {
        check(state == State.REVIEW)
        lock()
        state = State.CANCELLED
    }

    /** Exactly one hardcoded synthetic item; no arbitrary paths/fields/senders accepted. */
    fun approve(fault: Fault = Fault.NONE) {
        check(state == State.REVIEW)
        val db = requireNotNull(database)
        try {
            val sameId = entries(db).filter { it.notes == MARKER }
            if (sameId.isNotEmpty()) {
                if (sameId.size != 1 || !matches(sameId.single())) throw ConflictingDeposit()
            } else {
                val item = requireNotNull(db.createEntry())
                item.title = TITLE
                item.username = USERNAME
                item.password = DUMMY_PASSWORD.toCharArray()
                item.url = SITE
                item.notes = MARKER
                db.addEntryTo(item, requireNotNull(db.rootGroup))
                writeAndVerify(db, 1, fault)
            }
            // Reopen the committed target, not just the temporary candidate.
            val checkDb = read(target)
            try {
                check(entries(checkDb).count { it.notes == MARKER && matches(it) } == 1)
            } finally { checkDb.clearAndClose() }
            lock()
            state = State.VERIFIED_FILE
        } catch (failure: Exception) {
            lock()
            state = State.RECOVERY_REQUIRED
            throw failure
        }
    }

    private fun writeAndVerify(db: Database, expectedCount: Int?, fault: Fault) {
        val staging = File(directory, "serialization.tmp")
        val candidate = File(directory, "candidate.kdbx")
        val credential = MasterCredential(DUMMY_VAULT_KEY.toCharArray())
        try {
            check(!candidate.exists() || candidate.delete())
            db.saveData(staging, {
                when (fault) {
                    Fault.NULL_OUTPUT -> null
                    Fault.PARTIAL_OUTPUT -> object : OutputStream() {
                        private val output = candidate.outputStream()
                        private var count = 0
                        override fun write(value: Int) {
                            if (++count > 64) throw PartialWrite()
                            output.write(value)
                        }
                        override fun close() { output.close() }
                    }
                    Fault.SILENT_TRUNCATION -> object : OutputStream() {
                        private val output = candidate.outputStream()
                        private var count = 0
                        override fun write(value: Int) {
                            if (++count <= 64) output.write(value)
                        }
                        override fun close() { output.close() }
                    }
                    else -> candidate.outputStream()
                }
            }, credential, { _, _ -> error("No hardware keys in fixture") }, limits)
            // Null-output false-success and partial candidates cannot reach replace.
            if (!candidate.isFile) throw CandidateMissing()
            val verified = try { read(candidate) }
                catch (failure: Exception) { throw CandidateInvalid(failure) }
            try {
                if (expectedCount != null) {
                    check(entries(verified).count { it.notes == MARKER && matches(it) } == expectedCount)
                }
            } finally { verified.clearAndClose() }
            // Same-directory private test file only. Unsupported atomic move fails closed.
            Files.move(candidate.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            if (fault == Fault.AFTER_REPLACE) throw InterruptedAfterReplace()
        } finally {
            credential.clear()
            staging.delete()
            candidate.delete()
        }
    }

    /** Seed a conflicting fixed dummy item, never arbitrary caller-provided data. */
    fun seedConflictingFixture() {
        check(state == State.LOCKED)
        val db = read(target)
        try {
            val item = requireNotNull(db.createEntry())
            item.title = "Existing synthetic entry - must survive"
            item.notes = MARKER
            db.addEntryTo(item, requireNotNull(db.rootGroup))
            writeAndVerify(db, null, Fault.NONE)
        } finally { db.clearAndClose() }
    }

    fun committedEntryCount(): Int {
        val db = read(target)
        return try { entries(db).size } finally { db.clearAndClose() }
    }

    // Upstream cleanup is best effort, not a guarantee of JVM/native memory erasure.
    fun lock() {
        database?.clearAndClose()
        database = null
        state = State.LOCKED
    }
    override fun close() { lock() }
}
