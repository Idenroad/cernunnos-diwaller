package com.cernunnos.authenticator.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.cernunnos.authenticator.constants.*
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages a vault key backed by Android Keystore with device authentication.
 *
 * Architecture:
 * 1. A 32-byte random master key is generated (used for AES-256-GCM encryption of TOTP entries)
 * 2. The master key is encrypted by a Keystore key that requires user authentication
 * 3. The encrypted master key + IV are stored in SharedPreferences
 * 4. To unlock: BiometricPrompt authenticates → Keystore key decrypts master key → entries decrypted
 *
 * The Keystore key is device-bound and cannot be exported, so biometric-mode vaults
 * are NOT portable to another device (unlike passphrase-mode exports).
 */
class BiometricVault(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class VaultMode { PASSPHRASE, DEVICE_CREDENTIAL }

    fun getMode(): VaultMode? {
        val mode = prefs.getString(KEY_MODE, null) ?: return null
        return if (mode == "device") VaultMode.DEVICE_CREDENTIAL else VaultMode.PASSPHRASE
    }

    fun setMode(mode: VaultMode) {
        prefs.edit().putString(KEY_MODE, if (mode == VaultMode.DEVICE_CREDENTIAL) "device" else "passphrase").commit()
    }

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_SETUP, false)

    /**
     * Prepare an encrypt cipher for initializing the biometric vault.
     * This cipher must be passed to BiometricPrompt for authentication.
     * After successful auth, call completeInitialization(cipher).
     */
    fun prepareInitializationCipher(): Cipher {
        ensureKeystoreKey()
        return getEncryptCipher()
    }

    /**
     * Complete initialization after BiometricPrompt authentication.
     * Uses the authenticated cipher to encrypt a random master key.
     * Returns the master key directly (it's already in memory, no need to
     * create a new decrypt cipher which would require another auth).
     */
    fun completeInitialization(cipher: Cipher): ByteArray {
        val masterKey = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH).also { SecureRandom().nextBytes(it) }
        val encryptedMaster = cipher.doFinal(masterKey)

        val success = prefs.edit()
            .putString(KEY_BIO_IV, encodeB64(cipher.iv))
            .putString(KEY_BIO_DATA, encodeB64(encryptedMaster))
            .putBoolean(KEY_SETUP, true)
            .setMode(VaultMode.DEVICE_CREDENTIAL)
            .commit()
        if (!success) error("Failed to initialize biometric vault")

        return masterKey
    }

    /**
     * Get a decryption cipher for the stored master key.
     * The caller must authenticate via BiometricPrompt before using this cipher.
     */
    fun getDecryptCipherForMasterKey(): Cipher {
        val iv = decodeB64(prefs.getString(KEY_BIO_IV, null) ?: error("Vault not initialized"))
        return getDecryptCipher(iv)
    }

    /**
     * Decrypt the master key using an already-authenticated cipher.
     */
    fun decryptMasterKey(cipher: Cipher): ByteArray {
        val encryptedMaster = decodeB64(prefs.getString(KEY_BIO_DATA, null) ?: error("Vault not initialized"))
        return try {
            cipher.doFinal(encryptedMaster)
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            // Biometric data changed (fingerprint added/removed). Key is invalid.
            android.util.Log.e("BiometricVault", "Keystore key invalidated — biometric data changed", e)
            error("Biometric data has changed. Please reinitialize the vault with your passphrase or restore from backup.")
        } catch (e: Exception) {
            android.util.Log.e("BiometricVault", "Master key decryption failed", e)
            error("Failed to decrypt vault. The biometric key may have been invalidated.")
        }
    }

    private fun ensureKeystoreKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val useAuth = !isEmulator()

            // Try StrongBox first (hardware-backed TEE/Secure Element), fallback to regular Keystore
            val strongBoxAvailable = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val km = context.getSystemService(android.content.Context.KEYGUARD_SERVICE)
                        as android.app.KeyguardManager
                    km.isKeyguardSecure &&
                        context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
                } else false
            } catch (e: Exception) {
                false
            }

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(SecurityConfig.AES_KEY_SIZE)
                .apply {
                    if (useAuth) {
                        setUserAuthenticationRequired(true)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            setInvalidatedByBiometricEnrollment(true)
                        }
                    }
                    if (strongBoxAvailable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        // Attempt StrongBox; if it fails, the catch below retries without it
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()

            try {
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            } catch (e: Exception) {
                // StrongBox may fail on some devices even if reported as supported.
                // Retry without StrongBox.
                val fallbackSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(SecurityConfig.AES_KEY_SIZE)
                    .apply {
                        if (useAuth) {
                            setUserAuthenticationRequired(true)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                setInvalidatedByBiometricEnrollment(true)
                            }
                        }
                    }
                    .build()
                keyGenerator.init(fallbackSpec)
                keyGenerator.generateKey()
            }
        }
    }

    private fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator"))
    }

    private fun getEncryptCipher(): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    private fun getDecryptCipher(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(SecurityConfig.GCM_TAG_BITS, iv))
        }
    }

    private fun encodeB64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)
    private fun decodeB64(data: String): ByteArray = Base64.decode(data, Base64.NO_WRAP)

    companion object {
        private const val PREFS_NAME = StorageConfig.VAULT_PREFS_NAME
        private const val KEY_MODE = StorageConfig.KEY_VAULT_MODE
        private const val KEY_SETUP = StorageConfig.KEY_VAULT_SETUP
        private const val KEY_BIO_IV = StorageConfig.KEY_VAULT_BIO_IV
        private const val KEY_BIO_DATA = StorageConfig.KEY_VAULT_BIO_DATA
        private const val ANDROID_KEYSTORE = SecurityConfig.ANDROID_KEYSTORE
        private const val KEY_ALIAS = SecurityConfig.KEYSTORE_KEY_ALIAS
    }
}

// Extension to chain setMode on SharedPreferences.Editor
private fun android.content.SharedPreferences.Editor.setMode(mode: BiometricVault.VaultMode): android.content.SharedPreferences.Editor {
    return this.putString(StorageConfig.KEY_VAULT_MODE, if (mode == BiometricVault.VaultMode.DEVICE_CREDENTIAL) "device" else "passphrase")
}
