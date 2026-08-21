package com.cernunnos.authenticator.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object ExportPinManager {
    private const val PREFS_NAME = "export_pin_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ENABLED = "pin_enabled"

    fun isPinEnabled(context: Context): Boolean {
        val prefs = getSecurePrefs(context)
        return prefs.getBoolean(KEY_PIN_ENABLED, false)
    }

    fun setPin(context: Context, pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        val prefs = getSecurePrefs(context)
        return prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putBoolean(KEY_PIN_ENABLED, true)
            .commit()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getSecurePrefs(context)
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltStr = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val salt = android.util.Base64.decode(saltStr, android.util.Base64.NO_WRAP)
        val hash = hashPin(pin, salt)
        // Constant-time comparison to prevent timing attacks
        val hashBytes = android.util.Base64.decode(hash, android.util.Base64.NO_WRAP)
        val storedBytes = android.util.Base64.decode(storedHash, android.util.Base64.NO_WRAP)
        return java.security.MessageDigest.isEqual(hashBytes, storedBytes)
    }

    fun disablePin(context: Context) {
        val prefs = getSecurePrefs(context)
        prefs.edit().clear().commit()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        // Use PBKDF2 with 100,000 iterations to make brute-force of short PINs expensive.
        // A 4-digit PIN has 10,000 possibilities — with SHA-256 this would crack in
        // milliseconds. PBKDF2 with 100k iterations makes each attempt ~100ms,
        // so brute-forcing a 4-digit PIN takes ~17 minutes, and a 6-digit PIN ~28 hours.
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt,
            100_000,
            256,
        )
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
        return android.util.Base64.encodeToString(key.encoded, android.util.Base64.NO_WRAP)
    }

    private fun getSecurePrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
