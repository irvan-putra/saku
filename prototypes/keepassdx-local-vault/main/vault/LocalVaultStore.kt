/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 *
 * Test-only prototype: real KDBX4 local vault storage on the pinned KeePassDX
 * database/crypto modules, synthetic data only. Not production vault code, not
 * an inbound API, not a transport. No sync, no autofill, no real credentials.
 *
 * Hard rules:
 * - Synthetic data only. Master password and every stored password must carry
 *   the SYNTHETIC- prefix; anything that looks like a real credential is
 *   refused. This mirrors the Saku app's VaultGate.
 * - Every open goes through KdbxKdfBudgetGate BEFORE Database.loadData so a
 *   hostile header cannot force an over-budget native KDF allocation.
 * - Every save serializes to a staging file, fsyncs a candidate file, verifies
 *   the candidate reopens with the same entry count, then atomically renames
 *   it over the target and fsyncs the directory. A failure before the rename
 *   leaves the previous committed file untouched.
 *
 * The master password is held in memory while unlocked so the verification
 * reopen can run; cleanup is best effort, not a guarantee of JVM/native memory
 * erasure. KDF parameters are the KeePassDX Argon2id defaults (16 MiB,
 * 3 iterations) bounded by the caller's Limits. TEST_LIMITS (32000000 bytes /
 * 2 lanes) is a test value, not an endorsed Samsung A24 budget.
 */
package com.vanipy.saku.vault

import com.kunzisoft.keepass.database.crypto.kdf.AesKdf
import com.kunzisoft.keepass.database.crypto.kdf.Argon2Kdf
import com.kunzisoft.keepass.database.crypto.kdf.KdfEngine
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.MasterCredential
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class VaultRefusal(message: String) : Exception(message)

class LocalVaultStore(
    private val directory: File,
    private val limits: Limits = TEST_LIMITS,
) : AutoCloseable {

    enum class State { LOCKED, UNLOCKED }

    data class VaultEntry(
        val title: String,
        val username: String,
        val url: String,
        val password: CharArray,
    )

    private val target = File(directory, FILE_NAME)
    private var database: Database? = null
    private var masterPassword: CharArray? = null

    var state = State.LOCKED
        private set

    companion object {
        const val FILE_NAME = "saku-vault.kdbx"
        const val SYNTHETIC_PREFIX = "SYNTHETIC-"
        private const val ENTRY_NOTE = "Saku synthetic prototype entry"

        /** Test-only budget (32000000 bytes / 2 lanes). Not an A24 budget. */
        val TEST_LIMITS = Limits({ bytes, _ -> bytes <= 32_000_000uL }, 2L)

        /** KeePassDX Argon2id defaults (16 MiB, 3 iterations), parallelism
         *  clamped to the test budget so checkLimits stays silent. */
        fun argon2idEngine(): KdfEngine =
            Argon2Kdf(Argon2Kdf.Type.ARGON2_ID).apply {
                parameters = defaultParameters
                setParallelism(TEST_LIMITS.parallelism)
            }

        private fun checkSyntheticMaster(masterPassword: CharArray) {
            if (!masterPassword.concatToString().startsWith(SYNTHETIC_PREFIX)) {
                throw VaultRefusal("real-looking master passwords refused in this prototype")
            }
        }

        private fun checkSyntheticEntry(password: CharArray) {
            if (!password.concatToString().startsWith(SYNTHETIC_PREFIX)) {
                throw VaultRefusal("real-looking passwords refused in this prototype")
            }
        }
    }

    /** Creates a fresh vault; refuses to overwrite an existing file. */
    fun create(masterPassword: CharArray, kdfEngine: KdfEngine = argon2idEngine()) {
        check(state == State.LOCKED) { "vault already open" }
        require(directory.isDirectory) { "store owns only its private test directory" }
        if (target.exists()) throw VaultRefusal("vault file already exists")
        checkSyntheticMaster(masterPassword)
        val db = Database()
        try {
            db.createData("Saku synthetic vault", "Saku", null)
            db.kdfEngine = kdfEngine
            database = db
            this.masterPassword = masterPassword.copyOf()
            saveAtomic(db)
        } catch (failure: Exception) {
            db.clearAndClose()
            database = null
            this.masterPassword = null
            throw failure
        }
        state = State.UNLOCKED
    }

    /** Opens the existing vault after the budget gate passes. */
    fun unlock(masterPassword: CharArray) {
        check(state == State.LOCKED) { "vault already open" }
        require(directory.isDirectory) { "store owns only its private test directory" }
        checkSyntheticMaster(masterPassword)
        if (!target.isFile) throw VaultRefusal("vault file missing")
        // Budget gate BEFORE any KDF transform can allocate.
        KdbxKdfBudgetGate.enforce(target, limits)
        val db = Database()
        val credential = MasterCredential(masterPassword)
        try {
            target.inputStream().use {
                db.loadData(it, credential, { _, _ -> error("No hardware keys in prototype") },
                    false, false, directory, limits, false, null)
            }
            check(db.loaded)
        } catch (failure: Exception) {
            db.clearAndClose()
            throw failure
        } finally {
            credential.clear()
        }
        database = db
        this.masterPassword = masterPassword.copyOf()
        state = State.UNLOCKED
    }

    fun addEntry(title: String, username: String, url: String, password: CharArray) {
        val db = unlocked()
        checkSyntheticEntry(password)
        if (title.isBlank()) throw VaultRefusal("title must not be blank")
        val entry = db.createEntry() ?: throw IOException("entry creation failed")
        entry.title = title
        entry.username = username
        entry.password = password.copyOf()
        entry.url = url
        entry.notes = ENTRY_NOTE
        db.addEntryTo(entry, requireNotNull(db.rootGroup))
        saveAtomic(db)
    }

    fun entries(): List<VaultEntry> {
        val db = unlocked()
        return requireNotNull(db.rootGroup).getChildEntries().map {
            VaultEntry(it.title, it.username, it.url, it.password.copyOf())
        }
    }

    fun vaultFile(): File = target

    fun lock() {
        database?.clearAndClose()
        database = null
        masterPassword?.fill(' ')
        masterPassword = null
        state = State.LOCKED
    }

    override fun close() = lock()

    private fun unlocked(): Database {
        check(state == State.UNLOCKED && database != null) { "vault is locked" }
        return requireNotNull(database)
    }

    /** Serialize -> fsync candidate -> verify candidate reopens with the same
     *  entry count -> atomic rename -> fsync directory. */
    private fun saveAtomic(db: Database) {
        val staging = File(directory, ".saku-staging.tmp")
        val candidate = File(directory, ".saku-candidate.kdbx")
        val password = masterPassword ?: throw VaultRefusal("no credential held")
        val credential = MasterCredential(password)
        try {
            if (candidate.exists() && !candidate.delete()) {
                throw IOException("stale candidate not removable")
            }
            val syncingCandidate = object : FilterOutputStream(FileOutputStream(candidate)) {
                override fun close() {
                    out.flush()
                    (out as FileOutputStream).fd.sync()
                    out.close()
                }
            }
            db.saveData(staging, { syncingCandidate }, credential,
                { _, _ -> error("No hardware keys in prototype") }, limits)
            // Verify the candidate itself reopens before it may replace target.
            KdbxKdfBudgetGate.enforce(candidate, limits)
            val expected = requireNotNull(db.rootGroup).getChildEntries().size
            val verified = Database()
            val verifyCredential = MasterCredential(password)
            try {
                candidate.inputStream().use {
                    verified.loadData(it, verifyCredential,
                        { _, _ -> error("No hardware keys in prototype") },
                        false, false, directory, limits, false, null)
                }
                check(verified.loaded)
                check(requireNotNull(verified.rootGroup).getChildEntries().size == expected) {
                    "candidate entry count mismatch"
                }
            } catch (failure: Exception) {
                throw IOException("candidate failed verification", failure)
            } finally {
                verifyCredential.clear()
                verified.clearAndClose()
            }
            // Same-directory private test files only; unsupported atomic move
            // fails closed and leaves the previous target in place.
            Files.move(candidate.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            fsyncDirectory()
        } finally {
            credential.clear()
            staging.delete()
            candidate.delete()
        }
    }

    private fun fsyncDirectory() {
        try {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (failure: IOException) {
            throw IOException("directory fsync failed; durability unverified", failure)
        }
    }
}
