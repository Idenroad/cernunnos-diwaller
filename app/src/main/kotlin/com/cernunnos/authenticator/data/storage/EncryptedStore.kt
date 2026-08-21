package com.cernunnos.authenticator.data.storage

import android.content.Context
import android.util.Base64
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Cipher

/**
 * Encrypted storage for TOTP entries.
 *
 * Two modes:
 * - PASSPHRASE: AES-256-GCM with Argon2id key derivation from user passphrase
 * - DEVICE_CREDENTIAL: AES-256-GCM with a random master key encrypted by Keystore (biometric/PIN)
 *
 * Storage format (passphrase mode):
 *   - "vault_salt": base64 salt for Argon2id
 *   - "vault_iv": base64 IV for AES-GCM
 *   - "vault_data": base64 encrypted JSON of entries
 *
 * Storage format (device credential mode):
 *   - "vault_bio_iv": base64 IV for Keystore key encryption
 *   - "vault_bio_data": base64 encrypted master key
 *   - "vault_iv": base64 IV for AES-GCM (entries)
 *   - "vault_data": base64 encrypted JSON of entries
 */
class EncryptedStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val bioVault = BiometricVault(context)

    @Serializable
    private data class StoredEntry(
        val id: String,
        val issuer: String,
        val label: String,
        val secret: String,
        val algorithm: String,
        val digits: Int,
        val period: Int,
        val categoryId: String? = null,
        val favorite: Boolean = false,
        val type: String = TotpConfig.TYPE_TOTP,
        val counter: Long = 0L,
        val iconName: String? = null,
        val customIconUri: String? = null,
        val pin: String? = null,
    )

    val mode: BiometricVault.VaultMode? get() = bioVault.getMode()
    val isInitialized: Boolean get() = prefs.getBoolean(KEY_SETUP, false)

    // ── Passphrase mode ──

    fun initializeVault(passphrase: CharArray) {
        val salt = Argon2id.generateSalt()
        val success = prefs.edit()
            .putString(KEY_SALT, encodeB64(salt))
            .putBoolean(KEY_SETUP, true)
            .commit()
        if (!success) error("Failed to initialize vault")
        bioVault.setMode(BiometricVault.VaultMode.PASSPHRASE)
        saveEntries(emptyList(), passphrase)
    }

    @Synchronized
    fun saveEntries(entries: List<TotpEntry>, passphrase: CharArray): Boolean {
        val salt = getSalt() ?: error("Vault not initialized")
        val jsonBytes = encodeEntries(entries)
        val encrypted = CryptoManager.encrypt(jsonBytes, passphrase, salt)

        // Atomic write with backup: save current state, write new, cleanup on success
        val currentIv = prefs.getString(KEY_IV, null)
        val currentData = prefs.getString(KEY_DATA, null)
        if (currentIv != null && currentData != null) {
            prefs.edit()
                .putString(KEY_IV_BACKUP, currentIv)
                .putString(KEY_DATA_BACKUP, currentData)
                .commit()
        }

        val success = prefs.edit()
            .putString(KEY_IV, encodeB64(encrypted.iv))
            .putString(KEY_DATA, encodeB64(encrypted.ciphertext))
            .commit()

        if (success) {
            prefs.edit()
                .remove(KEY_IV_BACKUP)
                .remove(KEY_DATA_BACKUP)
                .commit()
        }
        return success
    }

    fun loadEntries(passphrase: CharArray): List<TotpEntry> {
        if (!isInitialized) return emptyList()
        val salt = getSalt() ?: return emptyList()
        var iv = prefs.getString(KEY_IV, null)
        var data = prefs.getString(KEY_DATA, null)

        // If main data is missing/corrupt, try backup
        if (iv == null || data == null) {
            iv = prefs.getString(KEY_IV_BACKUP, null)
            data = prefs.getString(KEY_DATA_BACKUP, null)
            if (iv != null && data != null) {
                android.util.Log.w("EncryptedStore", "Main vault data missing, using backup")
            } else {
                // Vault data and backup are both missing — reset to uninitialized state
                // instead of crashing. This can happen after data migration issues or
                // partial data clearing. The user will see the setup screen and can
                // restore from a backup if available.
                android.util.Log.e("EncryptedStore", "Vault data and backup both missing — resetting to uninitialized state")
                resetVaultState()
                return emptyList()
            }
        }

        val decrypted = try {
            val encrypted = CryptoManager.EncryptedData(
                salt = salt,
                iv = decodeB64(iv),
                ciphertext = decodeB64(data),
            )
            CryptoManager.decrypt(encrypted, passphrase)
        } catch (e: Exception) {
            // Try backup if main decrypt fails
            val backupIv = prefs.getString(KEY_IV_BACKUP, null)
            val backupData = prefs.getString(KEY_DATA_BACKUP, null)
            if (backupIv != null && backupData != null) {
                android.util.Log.w("EncryptedStore", "Main vault decrypt failed, trying backup", e)
                try {
                    val backupEncrypted = CryptoManager.EncryptedData(
                        salt = salt,
                        iv = decodeB64(backupIv),
                        ciphertext = decodeB64(backupData),
                    )
                    CryptoManager.decrypt(backupEncrypted, passphrase)
                } catch (e2: Exception) {
                    android.util.Log.e("EncryptedStore", "Backup decrypt also failed — data may be lost", e2)
                    error("Vault data is corrupted and could not be decrypted. If you have a backup, please restore it.")
                }
            } else {
                android.util.Log.e("EncryptedStore", "Vault decrypt failed and no backup available — data may be lost", e)
                error("Vault data is corrupted and no backup is available. If you have a backup, please restore it.")
            }
        }
        return decodeEntries(decrypted)
    }

    @Synchronized
    fun changePassphrase(entries: List<TotpEntry>, newPassphrase: CharArray): Boolean {
        val oldSalt = getSalt()
        val newSalt = Argon2id.generateSalt()
        // Save old salt as backup before overwriting
        if (oldSalt != null) {
            prefs.edit().putString(StorageConfig.KEY_VAULT_SALT_BACKUP, encodeB64(oldSalt)).commit()
        }
        prefs.edit().putString(KEY_SALT, encodeB64(newSalt)).commit()
        val saved = saveEntries(entries, newPassphrase)
        if (!saved && oldSalt != null) {
            // Rollback salt on failure
            prefs.edit().putString(KEY_SALT, encodeB64(oldSalt)).commit()
            android.util.Log.e("EncryptedStore", "changePassphrase failed — salt rolled back")
        } else {
            // Clean up backup salt
            prefs.edit().remove(StorageConfig.KEY_VAULT_SALT_BACKUP).commit()
        }
        return saved
    }

    // ── Device credential mode ──

    /**
     * Prepare cipher for biometric vault initialization.
     */
    fun prepareInitializationCipher(): Cipher = bioVault.prepareInitializationCipher()

    /**
     * Complete initialization: encrypt a random master key with the authenticated cipher.
     * Returns the master key directly (no need for a second decrypt cipher).
     */
    fun completeInitialization(cipher: Cipher): ByteArray = bioVault.completeInitialization(cipher)

    /**
     * Save entries using a pre-derived master key (device credential mode).
     */
    @Synchronized
    fun saveEntriesWithKey(entries: List<TotpEntry>, masterKey: ByteArray): Boolean {
        val jsonBytes = encodeEntries(entries)
        val encrypted = CryptoManager.encryptWithKey(jsonBytes, masterKey)

        val currentIv = prefs.getString(KEY_IV, null)
        val currentData = prefs.getString(KEY_DATA, null)
        if (currentIv != null && currentData != null) {
            prefs.edit()
                .putString(KEY_IV_BACKUP, currentIv)
                .putString(KEY_DATA_BACKUP, currentData)
                .commit()
        }

        val success = prefs.edit()
            .putString(KEY_IV, encodeB64(encrypted.iv))
            .putString(KEY_DATA, encodeB64(encrypted.ciphertext))
            .commit()

        if (success) {
            prefs.edit()
                .remove(KEY_IV_BACKUP)
                .remove(KEY_DATA_BACKUP)
                .commit()
        }
        return success
    }

    /**
     * Load entries using a pre-derived master key (device credential mode).
     */
    fun loadEntriesWithKey(masterKey: ByteArray): List<TotpEntry> {
        if (!isInitialized) return emptyList()
        var iv = prefs.getString(KEY_IV, null)
        var data = prefs.getString(KEY_DATA, null)

        if (iv == null || data == null) {
            iv = prefs.getString(KEY_IV_BACKUP, null)
            data = prefs.getString(KEY_DATA_BACKUP, null)
            if (iv == null || data == null) {
                android.util.Log.e("EncryptedStore", "Vault data and backup both missing (device mode) — resetting to uninitialized state")
                resetVaultState()
                return emptyList()
            }
        }

        val encrypted = CryptoManager.EncryptedData(
            salt = ByteArray(SecurityConfig.ARGON2_SALT_SIZE), // placeholder, not used for key derivation
            iv = decodeB64(iv),
            ciphertext = decodeB64(data),
        )
        val decrypted = try {
            CryptoManager.decryptWithKey(encrypted, masterKey)
        } catch (e: Exception) {
            val backupIv = prefs.getString(KEY_IV_BACKUP, null)
            val backupData = prefs.getString(KEY_DATA_BACKUP, null)
            if (backupIv != null && backupData != null) {
                val backupEncrypted = CryptoManager.EncryptedData(
                    salt = ByteArray(SecurityConfig.ARGON2_SALT_SIZE),
                    iv = decodeB64(backupIv),
                    ciphertext = decodeB64(backupData),
                )
                try {
                    CryptoManager.decryptWithKey(backupEncrypted, masterKey)
                } catch (e2: Exception) {
                    android.util.Log.e("EncryptedStore", "Backup decrypt failed", e2)
                    error("Vault data is corrupted and could not be decrypted. If you have a backup, please restore it.")
                }
            } else {
                android.util.Log.e("EncryptedStore", "Decrypt failed, no backup", e)
                error("Vault data is corrupted and no backup is available. If you have a backup, please restore it.")
            }
        }
        return decodeEntries(decrypted)
    }

    fun getDecryptCipherForMasterKey(): Cipher = bioVault.getDecryptCipherForMasterKey()
    fun decryptMasterKey(cipher: Cipher): ByteArray = bioVault.decryptMasterKey(cipher)

    // ── Common ──

    private fun getSalt(): ByteArray? = prefs.getString(KEY_SALT, null)?.let { decodeB64(it) }

    private fun encodeEntries(entries: List<TotpEntry>): ByteArray {
        val stored = entries.map {
            StoredEntry(
                id = it.id,
                issuer = it.issuer,
                label = it.label,
                secret = encodeB64(it.secret),
                algorithm = it.algorithm,
                digits = it.digits,
                period = it.period,
                categoryId = it.categoryId,
                favorite = it.favorite,
                type = it.type,
                counter = it.counter,
                iconName = it.iconName,
                customIconUri = it.customIconUri,
                pin = it.pin,
            )
        }
        return json.encodeToString(stored).toByteArray()
    }

    private fun decodeEntries(data: ByteArray): List<TotpEntry> {
        val stored = json.decodeFromString<List<StoredEntry>>(String(data))
        return stored.map {
            TotpEntry(
                id = it.id,
                issuer = it.issuer,
                label = it.label,
                secret = decodeB64(it.secret),
                algorithm = it.algorithm,
                digits = it.digits,
                period = it.period,
                categoryId = it.categoryId,
                favorite = it.favorite,
                type = it.type,
                counter = it.counter,
                iconName = it.iconName,
                customIconUri = it.customIconUri,
                pin = it.pin,
            )
        }
    }

    private fun encodeB64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun decodeB64(data: String): ByteArray = Base64.decode(data, Base64.NO_WRAP)

    /**
     * Reset the vault to uninitialized state. Used when vault data is missing
     * but KEY_SETUP was true (e.g., after partial data clearing or migration issues).
     * This allows the user to re-initialize the vault or restore from a backup
     * instead of crashing in a loop.
     *
     * Only clears vault data keys (IV, data, backups, setup flag).
     * Preserves the vault mode and biometric key material so the user can
     * still attempt biometric unlock if the encrypted master key is still valid.
     */
    private fun resetVaultState() {
        prefs.edit()
            .remove(KEY_IV)
            .remove(KEY_DATA)
            .remove(KEY_IV_BACKUP)
            .remove(KEY_DATA_BACKUP)
            .remove(KEY_SETUP)
            .commit()
    }

    companion object {
        private const val PREFS_NAME = StorageConfig.VAULT_PREFS_NAME
        private const val KEY_SALT = StorageConfig.KEY_VAULT_SALT
        private const val KEY_IV = StorageConfig.KEY_VAULT_IV
        private const val KEY_DATA = StorageConfig.KEY_VAULT_DATA
        private const val KEY_SETUP = StorageConfig.KEY_VAULT_SETUP
        private const val KEY_IV_BACKUP = StorageConfig.KEY_VAULT_IV_BACKUP
        private const val KEY_DATA_BACKUP = StorageConfig.KEY_VAULT_DATA_BACKUP
    }
}
