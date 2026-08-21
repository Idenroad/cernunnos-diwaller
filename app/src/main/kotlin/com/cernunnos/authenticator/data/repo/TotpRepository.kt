package com.cernunnos.authenticator.data.repo

import android.content.Context
import com.cernunnos.authenticator.constants.TotpConfig
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.data.storage.EncryptedStore
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher

/**
 * Repository that manages TOTP entries with encrypted storage.
 * Supports two unlock modes: passphrase or device credential (biometric/PIN).
 *
 * All mutations are serialized via [lock] to prevent concurrent writes
 * from corrupting the vault. The in-memory cache is only updated AFTER
 * a successful persistence, so a failed save never desynchronizes the cache.
 *
 * The passphrase and master key are copied on input so that [lock] zeroing
 * does not destroy the caller's array.
 */
class TotpRepository(context: Context) {

    private val store = EncryptedStore(context)
    private var passphrase: CharArray? = null
    private var masterKey: ByteArray? = null
    @Volatile private var cachedEntries: List<TotpEntry> = emptyList()
    private val lock = ReentrantLock()

    val isInitialized: Boolean get() = store.isInitialized
    val isUnlocked: Boolean get() = passphrase != null || masterKey != null
    val entries: List<TotpEntry> get() = cachedEntries
    val mode: BiometricVault.VaultMode? get() = store.mode

    // ── Passphrase mode ──

    fun initializeWithPassphrase(passphrase: CharArray) {
        lock.withLock {
            store.initializeVault(passphrase)
            this.passphrase = passphrase.copyOf()
            cachedEntries = emptyList()
        }
    }

    fun unlockWithPassphrase(passphrase: CharArray): Boolean {
        lock.withLock {
            // Load entries first. If this throws (corrupt vault, wrong passphrase),
            // we must NOT set this.passphrase — the vault stays locked.
            val loaded = store.loadEntries(passphrase)
            // Only set passphrase after successful load
            this.passphrase = passphrase.copyOf()
            cachedEntries = loaded
            return loaded.isNotEmpty() || store.isInitialized
        }
    }

    // ── Device credential mode ──

    fun initializeWithDeviceCredential(cipher: Cipher) {
        lock.withLock {
            val mk = store.completeInitialization(cipher)
            this.masterKey = mk
            cachedEntries = emptyList()
        }
    }

    fun unlockWithDeviceCredential(cipher: Cipher): Boolean {
        lock.withLock {
            val mk = store.decryptMasterKey(cipher)
            val loaded = store.loadEntriesWithKey(mk)
            this.masterKey = mk
            cachedEntries = loaded
            return true
        }
    }

    fun getDecryptCipherForMasterKey(): Cipher = store.getDecryptCipherForMasterKey()
    fun prepareInitializationCipher(): Cipher = store.prepareInitializationCipher()

    // ── Common ──

    fun lock() {
        lock.withLock {
            passphrase?.fill(0.toChar())
            masterKey?.fill(0)
            passphrase = null
            masterKey = null
            cachedEntries = emptyList()
        }
    }

    fun addEntry(entry: TotpEntry) {
        validateEntry(entry)
        lock.withLock {
            val pass = passphrase
            val mk = masterKey
            when {
                pass != null -> {
                    val newEntries = cachedEntries + entry
                    if (store.saveEntries(newEntries, pass)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to save entry — vault unchanged")
                    }
                }
                mk != null -> {
                    val newEntries = cachedEntries + entry
                    if (store.saveEntriesWithKey(newEntries, mk)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to save entry — vault unchanged")
                    }
                }
                else -> error("Vault is locked")
            }
        }
    }

    fun removeEntry(id: String) {
        lock.withLock {
            val pass = passphrase
            val mk = masterKey
            when {
                pass != null -> {
                    val newEntries = cachedEntries.filter { it.id != id }
                    if (store.saveEntries(newEntries, pass)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to remove entry — vault unchanged")
                    }
                }
                mk != null -> {
                    val newEntries = cachedEntries.filter { it.id != id }
                    if (store.saveEntriesWithKey(newEntries, mk)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to remove entry — vault unchanged")
                    }
                }
                else -> error("Vault is locked")
            }
        }
    }

    /**
     * Remove multiple entries in a single vault write. This is far more
     * efficient than calling [removeEntry] in a loop (one encrypt + one
     * save instead of N) and avoids the OOM that occurred when multiple
     * sequential saves triggered heavy recomposition + backup churn.
     */
    fun removeEntries(ids: Set<String>) {
        if (ids.isEmpty()) return
        lock.withLock {
            val pass = passphrase
            val mk = masterKey
            val newEntries = cachedEntries.filter { it.id !in ids }
            when {
                pass != null -> {
                    if (store.saveEntries(newEntries, pass)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to remove entries — vault unchanged")
                    }
                }
                mk != null -> {
                    if (store.saveEntriesWithKey(newEntries, mk)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to remove entries — vault unchanged")
                    }
                }
                else -> error("Vault is locked")
            }
        }
    }

    fun getEntry(id: String): TotpEntry? = cachedEntries.find { it.id == id }

    fun updateEntry(entry: TotpEntry) {
        validateEntry(entry)
        lock.withLock {
            val pass = passphrase
            val mk = masterKey
            when {
                pass != null -> {
                    val newEntries = cachedEntries.map { if (it.id == entry.id) entry else it }
                    if (store.saveEntries(newEntries, pass)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to update entry — vault unchanged")
                    }
                }
                mk != null -> {
                    val newEntries = cachedEntries.map { if (it.id == entry.id) entry else it }
                    if (store.saveEntriesWithKey(newEntries, mk)) {
                        cachedEntries = newEntries
                    } else {
                        error("Failed to update entry — vault unchanged")
                    }
                }
                else -> error("Vault is locked")
            }
        }
    }

    fun changePassphrase(newPassphrase: CharArray) {
        lock.withLock {
            val pass = passphrase ?: error("Vault is locked (passphrase mode)")
            val saved = store.changePassphrase(cachedEntries, newPassphrase)
            if (!saved) {
                // store.changePassphrase already rolled back the salt; do NOT
                // touch the internal passphrase so the vault stays usable with
                // the old one.
                error("Failed to change passphrase — vault unchanged")
            }
            // Only modify the internal passphrase after a confirmed success.
            pass.fill(0.toChar())
            passphrase = newPassphrase.copyOf()
        }
    }

    fun getAllEntriesForExport(): List<TotpEntry> = cachedEntries.toList()

    /**
     * Validates a [TotpEntry] before it is persisted. Throws
     * [IllegalArgumentException] with a descriptive message when any field is
     * invalid, so callers can surface the error to the user instead of
     * silently corrupting the vault.
     */
    private fun validateEntry(entry: TotpEntry) {
        require(entry.issuer.isNotBlank()) { "Issuer cannot be blank" }
        require(entry.label.isNotBlank()) { "Label cannot be blank" }
        require(entry.secret.isNotEmpty()) { "Secret cannot be empty" }
        require(entry.secret.size <= 128) { "Secret too long: ${entry.secret.size} bytes (max 128)" }
        require(entry.algorithm in TotpConfig.SUPPORTED_ALGORITHMS) {
            "Unsupported algorithm: ${entry.algorithm}"
        }
        require(entry.digits in 6..8) {
            "Digits must be 6, 7 or 8, got: ${entry.digits}"
        }
        require(entry.period > 0) { "Period must be > 0, got: ${entry.period}" }
        require(entry.counter >= 0) { "Counter must be >= 0, got: ${entry.counter}" }
    }

    /**
     * Returns a copy of the current passphrase (passphrase mode only), or
     * `null` when the vault is unlocked with a device credential (master key
     * only). The caller is responsible for zeroing the returned array.
     *
     * Used to create an encrypted pre-import backup without re-prompting the
     * user. Exposing a copy is safe: the internal array is never handed out
     * directly, and [lock] still owns zeroing the internal copy.
     */
    fun currentPassphraseCopy(): CharArray? {
        return lock.withLock {
            passphrase?.copyOf()
        }
    }
}

private inline fun <T> java.util.concurrent.locks.ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
