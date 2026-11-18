/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026 Saku contributors.
 *
 * Test-only prototype. Not production vault code.
 *
 * KDBX4 header KDF budget gate. At pinned KeePassDX 77ecfa8, Database.loadData
 * applies the caller's Limits to the PREVIOUSLY loaded KDF engine
 * (kdfEngine?.checkLimits(limits) before the fresh parse), but the freshly
 * parsed header's engine reaches makeFinalKey -> KdfEngine.transform with only
 * intrinsic raw-parameter range checks. An intrinsic-range-valid but
 * over-budget Argon2 memory parameter (up to 4 GiB) is therefore not rejected
 * against a caller budget before the native transform allocates.
 *
 * This gate parses only the outer KDBX4 header framing, reuses upstream's own
 * VariantDictionary/KdfParameters parsing and KdfEngine.checkLimits validation
 * (no custom cryptography and no second KDF-parameter parser), and rejects an
 * over-budget or malformed header BEFORE Database.loadData runs, so the large
 * allocation never happens. It accepts KDBX 4.x only (4-byte field lengths);
 * older files fail closed. Header reads are bounded: 1 MiB total, 1 MiB per
 * field.
 */
package com.vanipy.saku.vault

import com.kunzisoft.keepass.database.crypto.kdf.AesKdf
import com.kunzisoft.keepass.database.crypto.kdf.Argon2Kdf
import com.kunzisoft.keepass.database.crypto.kdf.KdfEngine
import com.kunzisoft.keepass.database.crypto.kdf.KdfParameters
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import java.io.File
import java.io.IOException
import java.io.InputStream

object KdbxKdfBudgetGate {

    // Public KDBX format constants, https://keepass.info/help/kb/kdbx_4.html
    private const val SIGNATURE_1 = 0x9AA2D903L
    private const val SIGNATURE_2 = 0xB54BFB67L
    private const val SIGNATURE_2_PRE = 0xB54BFB66L
    private const val VERSION_CRITICAL_MASK = 0xFFFF0000L
    private const val VERSION_4_MAJOR = 0x00040000L
    private const val FIELD_END_OF_HEADER = 0
    private const val FIELD_KDF_PARAMETERS = 11
    private const val MAX_HEADER_BYTES = 1 shl 20
    private const val MAX_FIELD_BYTES = 1 shl 20

    class NotKdbx4 : IOException("Not a KDBX 4.x file; refusing without a budget check")
    class MalformedHeader(message: String) : IOException(message)
    class MissingKdfParameters : IOException("KDBX4 header carries no KDF parameters field")

    /** Reads only the header of [file], then closes it; the caller reopens the
     *  file for the real load. Throws before any KDF transform when the parsed
     *  KDF parameters are malformed or exceed [limits]. Returns the parsed
     *  parameters so callers can assert the KDF actually used. */
    fun enforce(file: File, limits: Limits): KdfParameters =
        file.inputStream().buffered().use { enforce(it, limits) }

    fun enforce(input: InputStream, limits: Limits): KdfParameters {
        val reader = LeReader(input)
        val sig1 = reader.uint32()
        val sig2 = reader.uint32()
        if (sig1 != SIGNATURE_1 || (sig2 != SIGNATURE_2 && sig2 != SIGNATURE_2_PRE)) {
            throw NotKdbx4()
        }
        val version = reader.uint32()
        if (version and VERSION_CRITICAL_MASK != VERSION_4_MAJOR) {
            throw NotKdbx4()
        }
        var kdfBytes: ByteArray? = null
        var total = 12
        while (true) {
            val fieldId = reader.byte()
            val fieldLength = reader.uint32()
            if (fieldLength > MAX_FIELD_BYTES) {
                throw MalformedHeader("Header field length $fieldLength over bound")
            }
            total += 5 + fieldLength.toInt()
            if (total > MAX_HEADER_BYTES) {
                throw MalformedHeader("Header over $MAX_HEADER_BYTES bytes")
            }
            val value = reader.bytes(fieldLength.toInt())
            if (fieldId == FIELD_END_OF_HEADER) break
            if (fieldId == FIELD_KDF_PARAMETERS) kdfBytes = value
        }
        val parameters = KdfParameters.deserialize(
            kdfBytes ?: throw MissingKdfParameters()
        ) ?: throw MalformedHeader("KDF parameters not deserializable")
        val engine = freshEngine(parameters)
        engine.parameters = parameters
        // Upstream's own validation: intrinsic range checks plus the caller
        // budget; throws KDFMemoryDatabaseException when memory is over budget.
        engine.checkLimits(limits)
        return parameters
    }

    private fun freshEngine(parameters: KdfParameters): KdfEngine = when (parameters.uuid) {
        AesKdf.CIPHER_UUID -> AesKdf()
        Argon2Kdf.Type.ARGON2_D.CIPHER_UUID -> Argon2Kdf(Argon2Kdf.Type.ARGON2_D)
        Argon2Kdf.Type.ARGON2_ID.CIPHER_UUID -> Argon2Kdf(Argon2Kdf.Type.ARGON2_ID)
        else -> throw MalformedHeader("Unknown KDF UUID ${parameters.uuid}")
    }

    private class LeReader(private val input: InputStream) {
        fun byte(): Int {
            val value = input.read()
            if (value < 0) throw MalformedHeader("Header ended early")
            return value
        }

        fun bytes(count: Int): ByteArray {
            val out = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(out, offset, count - offset)
                if (read < 0) throw MalformedHeader("Header ended early")
                offset += read
            }
            return out
        }

        fun uint32(): Long {
            val b = bytes(4)
            return (b[0].toLong() and 0xFF) or
                ((b[1].toLong() and 0xFF) shl 8) or
                ((b[2].toLong() and 0xFF) shl 16) or
                ((b[3].toLong() and 0xFF) shl 24)
        }
    }
}
