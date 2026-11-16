package com.vanipy.saku

/** Mock vault. REFUSES anything that looks like a real credential until the
 *  storage layer passes review. Synthetic entries must be marked as such. */
class Refusal(message: String) : Exception(message)

object VaultGate {
    const val SYNTHETIC_PREFIX = "SYNTHETIC-"
    fun checkSynthetic(site: String, password: String) {
        if (!password.startsWith(SYNTHETIC_PREFIX)) {
            throw Refusal("real passwords refused in this build (storage not reviewed)")
        }
        if (site.isBlank()) throw Refusal("site must not be blank")
    }
}

class MockVault {
    private val entries = mutableListOf<Entry>()
    fun add(e: Entry) { VaultGate.checkSynthetic(e.site, e.password); entries += e }
    fun all(): List<Entry> = entries.toList()
}

/** Real store interface - deliberately not implemented in this build. */
interface VaultStore {
    fun open(masterPassword: CharArray): MockVault
}

class UnimplementedVaultStore : VaultStore {
    override fun open(masterPassword: CharArray): MockVault =
        throw Refusal("kdbx store not available until storage review; no real passwords accepted")
}
