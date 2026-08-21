package com.cernunnos.authenticator.crash

import android.content.Context
import android.os.Build
import android.util.Log
import com.cernunnos.authenticator.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, privacy-respecting crash reporter.
 *
 * - Catches uncaught exceptions and writes a crash log to internal storage.
 * - Crash logs are encrypted with a device-bound Keystore key (no user auth)
 *   so they are not readable via ADB backups or on rooted devices.
 * - No automatic sending to any server. The user decides whether to share.
 * - On next app launch, the app detects crash logs and offers to share them.
 * - Crash logs contain NO secrets: no passphrase, no TOTP seeds, no document data.
 *   Only: app version, Android version, device model, stack trace, timestamp.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 5
    private const val CRASH_KEY_ALIAS = "cernunnos_crash_log_key"
    private const val PLAINTEXT_PREFIX = "crash_"
    private const val ENCRYPTED_PREFIX = "crash_enc_"

    /**
     * Install the global uncaught exception handler.
     * Call this from Application.onCreate().
     */
    fun install(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(context, thread, throwable)
            } catch (e: Exception) {
                // Don't let crash logging itself crash — just log
                Log.e(TAG, "Failed to write crash log", e)
            }
            // Chain to the previous handler so the app still terminates normally
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Write a crash log file to internal storage.
     * The log is encrypted with a device-bound Keystore key. If encryption
     * fails (e.g., Keystore unavailable during crash), the log is written
     * in plaintext as a fallback — better to have the log than to lose it.
     */
    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val crashDir = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val log = buildString {
            appendLine("=== Cernunnos Diwaller Crash Report ===")
            appendLine("Timestamp: ${Date()}")
            appendLine()
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine()
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine()
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            appendLine("Stack trace:")
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            appendLine(sw.toString())
            appendLine()
            appendLine("=== End of crash report ===")
        }

        // Try to encrypt the log; fall back to plaintext if Keystore is unavailable.
        // Encrypted files use .enc extension, plaintext use .txt.
        val encrypted = try {
            encryptWithDeviceKey(context, log.toByteArray())
        } catch (e: Exception) {
            Log.w(TAG, "Crash log encryption failed, writing plaintext: ${e.message}")
            null
        }

        val crashFile = if (encrypted != null) {
            File(crashDir, "${ENCRYPTED_PREFIX}${timestamp}.enc").apply { writeBytes(encrypted) }
        } else {
            File(crashDir, "${PLAINTEXT_PREFIX}${timestamp}.txt").apply { writeText(log) }
        }

        // Rotate: keep only the most recent MAX_CRASH_FILES crash logs
        rotateCrashLogs(crashDir)
    }

    /**
     * Encrypt data with a device-bound Keystore key (no user auth required).
     * Format: [IV (12 bytes)][ciphertext].
     */
    private fun encryptWithDeviceKey(context: Context, plaintext: ByteArray): ByteArray {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(CRASH_KEY_ALIAS)) {
            val gen = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            gen.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    CRASH_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            gen.generateKey()
        }
        val key = keyStore.getKey(CRASH_KEY_ALIAS, null) as javax.crypto.SecretKey
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    /**
     * Decrypt data encrypted with [encryptWithDeviceKey].
     * Returns null if decryption fails (e.g., key invalidated).
     */
    private fun decryptWithDeviceKey(data: ByteArray): ByteArray? {
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(CRASH_KEY_ALIAS, null) as javax.crypto.SecretKey
            val iv = data.copyOfRange(0, 12)
            val ciphertext = data.copyOfRange(12, data.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.w(TAG, "Crash log decryption failed: ${e.message}")
            null
        }
    }

    /**
     * Delete old crash logs beyond MAX_CRASH_FILES.
     */
    private fun rotateCrashLogs(crashDir: File) {
        try {
            val files = crashDir.listFiles { f ->
                (f.name.startsWith(PLAINTEXT_PREFIX) && f.name.endsWith(".txt")) ||
                    (f.name.startsWith(ENCRYPTED_PREFIX) && f.name.endsWith(".enc"))
            }?.sortedByDescending { it.lastModified() }
                ?: return
            files.drop(MAX_CRASH_FILES).forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Crash log rotation failed: ${e.message}")
        }
    }

    /**
     * Check if there are pending crash logs.
     * Call from MainActivity to decide whether to show the crash dialog.
     */
    fun hasPendingCrashes(context: Context): Boolean {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return false
        return crashDir.listFiles { f ->
            (f.name.startsWith(PLAINTEXT_PREFIX) && f.name.endsWith(".txt")) ||
                (f.name.startsWith(ENCRYPTED_PREFIX) && f.name.endsWith(".enc"))
        }?.isNotEmpty() == true
    }

    /**
     * Get the list of pending crash log files.
     */
    fun getPendingCrashFiles(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles { f ->
            (f.name.startsWith(PLAINTEXT_PREFIX) && f.name.endsWith(".txt")) ||
                (f.name.startsWith(ENCRYPTED_PREFIX) && f.name.endsWith(".enc"))
        }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Read and concatenate all crash logs into a single string for sharing.
     * Encrypted logs are decrypted on-the-fly; plaintext logs are read directly.
     */
    fun getCombinedCrashLogs(context: Context): String {
        return getPendingCrashFiles(context).joinToString("\n\n") { file ->
            if (file.name.endsWith(".enc")) {
                val decrypted = decryptWithDeviceKey(file.readBytes())
                decrypted?.toString(Charsets.UTF_8) ?: "[Failed to decrypt crash log: ${file.name}]"
            } else {
                file.readText()
            }
        }
    }

    /**
     * Delete all crash logs after the user has shared or dismissed them.
     */
    fun clearCrashLogs(context: Context) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            crashDir.listFiles { f -> f.name.startsWith(PLAINTEXT_PREFIX) || f.name.startsWith(ENCRYPTED_PREFIX) }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear crash logs: ${e.message}")
        }
    }
}
