package com.cernunnos.authenticator.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.model.Category
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * App preferences.
 *
 * Non-sensitive settings are stored in a standard SharedPreferences.
 * Sensitive data (tokens, passphrases, passwords) is stored in a separate
 * EncryptedSharedPreferences backed by Android Keystore (AES-256-GCM).
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // Encrypted SharedPreferences for sensitive data (tokens, passphrases, passwords).
    // If EncryptedSharedPreferences fails (e.g. Keystore unavailable), we use a plain
    // fallback BUT expose [secureStorageAvailable] so the UI can warn the user and
    // disable cloud features rather than silently storing secrets in plaintext.
    var secureStorageAvailable: Boolean
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { secureStorageAvailable = true }
    } catch (e: Exception) {
        Log.e("AppPreferences", "EncryptedSharedPreferences unavailable — secrets will be stored in plaintext (INSECURE)", e)
        secureStorageAvailable = false
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = StorageConfig.PREFS_NAME
        private const val SECURE_PREFS_NAME = StorageConfig.SECURE_PREFS_NAME
        private const val KEY_SPLASH_ANIMATION = "splash_animation"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_LIST_MODE = "list_mode"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_AUTO_BACKUP_PASS = "auto_backup_pass"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_UNLOCK_ATTEMPTS = "unlock_attempts"
        private const val KEY_UNLOCK_LOCKOUT_UNTIL = "unlock_lockout_until"
        private const val KEY_CLOUD_BACKUP_URI = "cloud_backup_uri"
        private const val KEY_CLOUD_BACKUP_PASS = "cloud_backup_pass"
        private const val KEY_CLOUD_BACKUP_ENABLED = "cloud_backup_enabled"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_CLOUD_LAST_SYNC = "cloud_last_sync"
        private const val KEY_WIDGET_MODE = "widget_mode" // "favorites", "category", "all"
        private const val KEY_WIDGET_CATEGORY = "widget_category"
        private const val KEY_WIDGET_ENABLED = "widget_codes_enabled"

        // Cloud providers
        private const val KEY_CLOUD_PROVIDER = "cloud_provider" // "dropbox", "gdrive", "webdav", "sftp"
        private const val KEY_DROPBOX_TOKEN = "dropbox_token"
        private const val KEY_DROPBOX_APP_KEY = "dropbox_app_key"
        private const val KEY_DROPBOX_REFRESH_TOKEN = "dropbox_refresh_token"
        private const val KEY_DROPBOX_TOKEN_EXPIRY = "dropbox_token_expiry"
        private const val KEY_GDRIVE_TOKEN = "gdrive_token"
        private const val KEY_GDRIVE_CLIENT_ID = "gdrive_client_id"
        private const val KEY_GDRIVE_REFRESH_TOKEN = "gdrive_refresh_token"
        private const val KEY_GDRIVE_TOKEN_EXPIRY = "gdrive_token_expiry"
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USER = "webdav_user"
        private const val KEY_WEBDAV_PASS = "webdav_pass"
        private const val KEY_SFTP_HOST = "sftp_host"
        private const val KEY_SFTP_PORT = "sftp_port"
        private const val KEY_SFTP_USER = "sftp_user"
        private const val KEY_SFTP_PASS = "sftp_pass"
        private const val KEY_SFTP_PATH = "sftp_path"
        private const val KEY_SFTP_HOST_KEY = "sftp_host_key"
        private const val KEY_THEME_MODE = "theme_mode" // "dark", "light", "system"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled" // Material You (Android 12+)
        private const val KEY_WIDGET_MAX_ENTRIES = "widget_max_entries"
        private const val KEY_WIDGET_REQUIRE_UNLOCK = "widget_require_unlock"
        private const val KEY_LAST_APP_OPEN_TS = "last_app_open_ts"
        private const val KEY_VAULT_LOCKED_TS = "vault_locked_ts"
        private const val KEY_SORT_MODE = "sort_mode" // "name", "issuer", "date", "favorites", "manual"
        private const val KEY_MANUAL_ORDER = "manual_order" // ordered list of entry IDs
        private const val KEY_VIEW_MODE = "view_mode" // "list", "tiles", "compact"
        private const val KEY_LANGUAGE = "language" // "system", "en", "fr"
        private const val KEY_ALLOW_SCREENSHOTS = "allow_screenshots" // default false (FLAG_SECURE on)
        private const val KEY_TAP_TO_REVEAL = "tap_to_reveal" // default false
        private const val KEY_LAST_PAUSE_TIME = "last_pause_time"
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val CURRENT_PREFS_VERSION = 2

        // Default categories created on first access
        val DEFAULT_CATEGORIES = listOf(
            Category(id = "cat_pro", name = "Professional", isDefault = true),
            Category(id = "cat_mail", name = "Emails", isDefault = true),
            Category(id = "cat_bank", name = "Bank", isDefault = true),
            Category(id = "cat_social", name = "Social", isDefault = true),
            Category(id = "cat_other", name = "Other", isDefault = true),
        )
    }

    init {
        migratePreferences()
    }

    /**
     * Versioned preference migration.
     * Each version bump should add a migration step.
     */
    private fun migratePreferences() {
        val currentVersion = prefs.getInt(KEY_PREFS_VERSION, 1)
        if (currentVersion >= CURRENT_PREFS_VERSION) return

        if (currentVersion < 2) {
            // v1 → v2: migrate sensitive keys to securePrefs if they exist in plain prefs
            var allRemoved = true
            try {
                val sensitiveKeys = listOf(
                    "auto_backup_pass", "cloud_backup_pass",
                    "dropbox_token", "dropbox_refresh_token",
                    "gdrive_token", "gdrive_refresh_token",
                    "webdav_url", "webdav_user", "webdav_pass",
                    "sftp_host", "sftp_user", "sftp_pass",
                )
                val editor = securePrefs.edit()
                sensitiveKeys.forEach { key ->
                    val value = prefs.getString(key, null)
                    if (value != null) {
                        editor.putString(key, value)
                        // Use commit() and verify the key is removed from plain prefs
                        val removed = prefs.edit().remove(key).commit()
                        if (!removed) {
                            Log.e("AppPreferences", "Failed to remove sensitive key '$key' from plain prefs")
                            allRemoved = false
                        }
                    }
                }
                editor.commit()
            } catch (e: Exception) {
                Log.e("AppPreferences", "v1→v2 migration failed — some secrets may remain in plaintext", e)
                allRemoved = false
            }

            // Only increment version if all sensitive keys were successfully removed.
            // If some remain, the migration will retry on next launch.
            if (allRemoved) {
                prefs.edit().putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION).commit()
            } else {
                Log.w("AppPreferences", "Migration incomplete — will retry on next launch")
            }
        } else {
            prefs.edit().putInt(KEY_PREFS_VERSION, CURRENT_PREFS_VERSION).commit()
        }
    }

    /**
     * Whether the splash animation should play.
     * Default: true on first launch, false after first launch is complete.
     */
    var splashAnimationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLASH_ANIMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SPLASH_ANIMATION, value).apply()

    /**
     * Whether the first launch has been completed.
     * After the first launch, splash animation defaults to OFF.
     */
    var firstLaunchDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, value).apply()

    /**
     * List view mode: "all" (flat list) or "categories" (grouped by category).
     * Default: "all".
     */
    var listMode: String
        get() = prefs.getString(KEY_LIST_MODE, UiConstants.LIST_MODE_ALL) ?: UiConstants.LIST_MODE_ALL
        set(value) = prefs.edit().putString(KEY_LIST_MODE, value).apply()

    /**
     * Categories persisted as JSON. Returns defaults on first access.
     */
    var categories: List<Category>
        get() {
            val raw = prefs.getString(KEY_CATEGORIES, null) ?: return DEFAULT_CATEGORIES
            return try {
                json.decodeFromString<List<Category>>(raw)
            } catch (e: Exception) {
                DEFAULT_CATEGORIES
            }
        }
        set(value) = prefs.edit().putString(KEY_CATEGORIES, json.encodeToString(value)).apply()

    // ── Auto backup ──

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    var autoBackupPassphrase: String?
        get() = securePrefs.getString(KEY_AUTO_BACKUP_PASS, null)
        set(value) = securePrefs.edit().putString(KEY_AUTO_BACKUP_PASS, value).apply()

    // ── Auto lock ──

    /** Auto-lock timeout in seconds. 0 = disabled. Default: 60s. */
    var autoLockTimeout: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_TIMEOUT, 60)
        set(value) {
            val clamped = if (value < 0) 0 else value
            prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, clamped).apply()
        }

    // ── Rate limiting for vault unlock attempts ──

    /** Number of consecutive failed unlock attempts. Reset to 0 on successful unlock. */
    var unlockAttempts: Int
        get() = prefs.getInt(KEY_UNLOCK_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_UNLOCK_ATTEMPTS, value).apply()

    /** Timestamp (ms) until which unlock is blocked. 0 = no lockout. */
    var unlockLockoutUntil: Long
        get() = prefs.getLong(KEY_UNLOCK_LOCKOUT_UNTIL, 0)
        set(value) = prefs.edit().putLong(KEY_UNLOCK_LOCKOUT_UNTIL, value).apply()

    /**
     * Record a failed unlock attempt. After 5 attempts, locks out for 30s.
     * After 10 attempts, locks out for 5 minutes. After 15+, 30 minutes.
     * @return lockout duration in ms, or 0 if no lockout triggered.
     */
    fun recordFailedUnlock(): Long {
        val attempts = unlockAttempts + 1
        unlockAttempts = attempts
        val lockoutMs = when {
            attempts >= 15 -> 30 * 60 * 1000L
            attempts >= 10 -> 5 * 60 * 1000L
            attempts >= 5 -> 30 * 1000L
            else -> 0L
        }
        if (lockoutMs > 0) {
            unlockLockoutUntil = System.currentTimeMillis() + lockoutMs
        }
        return lockoutMs
    }

    /** Reset failed attempt counter on successful unlock. */
    fun resetUnlockAttempts() {
        unlockAttempts = 0
        unlockLockoutUntil = 0
    }

    /** Check if unlock is currently locked out. Returns remaining ms, or 0 if not locked. */
    fun unlockLockoutRemaining(): Long {
        val until = unlockLockoutUntil
        if (until == 0L) return 0L
        val remaining = until - System.currentTimeMillis()
        return if (remaining <= 0) {
            unlockLockoutUntil = 0
            0L
        } else remaining
    }

    /** Whether biometric unlock is enabled (only valid for passphrase-mode vaults). */
    var biometricUnlockEnabled: Boolean
        get() = prefs.getBoolean("biometric_unlock_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_unlock_enabled", value).apply()

    // ── Cloud backup via API providers ──

    var cloudBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_BACKUP_ENABLED, value).apply()

    var cloudBackupUri: String?
        get() = prefs.getString(KEY_CLOUD_BACKUP_URI, null)
        set(value) = prefs.edit().putString(KEY_CLOUD_BACKUP_URI, value).apply()

    var cloudBackupPassphrase: String?
        get() = securePrefs.getString(KEY_CLOUD_BACKUP_PASS, null)
        set(value) = securePrefs.edit().putString(KEY_CLOUD_BACKUP_PASS, value).apply()

    var cloudSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, value).apply()

    var cloudLastSync: Long
        get() = prefs.getLong(KEY_CLOUD_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_LAST_SYNC, value).apply()

    // ── Widget with codes ──

    var widgetCodesEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WIDGET_ENABLED, value).apply()

    /** "favorites", "category", or "all" */
    var widgetMode: String
        get() = prefs.getString(KEY_WIDGET_MODE, UiConstants.WIDGET_MODE_FAVORITES) ?: UiConstants.WIDGET_MODE_FAVORITES
        set(value) = prefs.edit().putString(KEY_WIDGET_MODE, value).apply()

    var widgetCategory: String?
        get() = prefs.getString(KEY_WIDGET_CATEGORY, null)
        set(value) = prefs.edit().putString(KEY_WIDGET_CATEGORY, value).apply()

    /** Maximum number of entries shown in the codes widget (1–10). Default: 10. */
    var widgetMaxEntries: Int
        get() = prefs.getInt(KEY_WIDGET_MAX_ENTRIES, UiConstants.WIDGET_MAX_ENTRIES)
            .coerceIn(1, UiConstants.WIDGET_MAX_ENTRIES)
        set(value) = prefs.edit().putInt(KEY_WIDGET_MAX_ENTRIES, value.coerceIn(1, UiConstants.WIDGET_MAX_ENTRIES)).apply()

    /** When true, widget codes are always masked until the user unlocks the app. */
    var widgetRequireUnlock: Boolean
        get() = prefs.getBoolean(KEY_WIDGET_REQUIRE_UNLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_WIDGET_REQUIRE_UNLOCK, value).apply()

    /** Timestamp of the last time the app was opened (used by widget to know if app is "active"). */
    var lastAppOpenTs: Long
        get() = prefs.getLong(KEY_LAST_APP_OPEN_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_APP_OPEN_TS, value).apply()

    /** Timestamp of the last time the vault was locked (used by widget to mask codes). */
    var vaultLockedTs: Long
        get() = prefs.getLong(KEY_VAULT_LOCKED_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_VAULT_LOCKED_TS, value).apply()

    // ── Cloud providers ──

    /** "dropbox", "gdrive", "webdav", or "sftp" */
    var cloudProvider: String
        get() = prefs.getString(KEY_CLOUD_PROVIDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_PROVIDER, value).apply()

    var dropboxToken: String?
        get() = securePrefs.getString(KEY_DROPBOX_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_DROPBOX_TOKEN, value).apply()

    var dropboxAppKey: String?
        get() = securePrefs.getString(KEY_DROPBOX_APP_KEY, null)
        set(value) = securePrefs.edit().putString(KEY_DROPBOX_APP_KEY, value).apply()

    var dropboxRefreshToken: String?
        get() = securePrefs.getString(KEY_DROPBOX_REFRESH_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_DROPBOX_REFRESH_TOKEN, value).apply()

    var dropboxTokenExpiry: Long
        get() = securePrefs.getLong(KEY_DROPBOX_TOKEN_EXPIRY, 0L)
        set(value) = securePrefs.edit().putLong(KEY_DROPBOX_TOKEN_EXPIRY, value).apply()

    var gdriveToken: String?
        get() = securePrefs.getString(KEY_GDRIVE_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_GDRIVE_TOKEN, value).apply()

    var gdriveClientId: String?
        get() = securePrefs.getString(KEY_GDRIVE_CLIENT_ID, null)
        set(value) = securePrefs.edit().putString(KEY_GDRIVE_CLIENT_ID, value).apply()

    var gdriveRefreshToken: String?
        get() = securePrefs.getString(KEY_GDRIVE_REFRESH_TOKEN, null)
        set(value) = securePrefs.edit().putString(KEY_GDRIVE_REFRESH_TOKEN, value).apply()

    var gdriveTokenExpiry: Long
        get() = securePrefs.getLong(KEY_GDRIVE_TOKEN_EXPIRY, 0L)
        set(value) = securePrefs.edit().putLong(KEY_GDRIVE_TOKEN_EXPIRY, value).apply()

    // WebDAV
    // URL and username are stored in EncryptedSharedPreferences because they
    // reveal the server location and account identity — sensitive metadata
    // that should not be readable from a backup or root shell.
    var webdavUrl: String?
        get() = securePrefs.getString(KEY_WEBDAV_URL, null)
        set(value) = securePrefs.edit().putString(KEY_WEBDAV_URL, value).apply()

    var webdavUser: String?
        get() = securePrefs.getString(KEY_WEBDAV_USER, null)
        set(value) = securePrefs.edit().putString(KEY_WEBDAV_USER, value).apply()

    var webdavPass: String?
        get() = securePrefs.getString(KEY_WEBDAV_PASS, null)
        set(value) = securePrefs.edit().putString(KEY_WEBDAV_PASS, value).apply()

    // SFTP
    // Host and user are stored in EncryptedSharedPreferences (same rationale
    // as WebDAV URL/user). Port and path are non-sensitive and remain in
    // plaintext prefs.
    var sftpHost: String?
        get() = securePrefs.getString(KEY_SFTP_HOST, null)
        set(value) = securePrefs.edit().putString(KEY_SFTP_HOST, value).apply()

    var sftpPort: Int
        get() = prefs.getInt(KEY_SFTP_PORT, 22)
        set(value) = prefs.edit().putInt(KEY_SFTP_PORT, value).apply()

    var sftpUser: String?
        get() = securePrefs.getString(KEY_SFTP_USER, null)
        set(value) = securePrefs.edit().putString(KEY_SFTP_USER, value).apply()

    var sftpPass: String?
        get() = securePrefs.getString(KEY_SFTP_PASS, null)
        set(value) = securePrefs.edit().putString(KEY_SFTP_PASS, value).apply()

    var sftpPath: String?
        get() = prefs.getString(KEY_SFTP_PATH, null)
        set(value) = prefs.edit().putString(KEY_SFTP_PATH, value).apply()

    /**
     * Pinned SFTP host key (Base64 of the server's public key) used to detect
     * MITM / changed host keys on subsequent connections. Stored in
     * EncryptedSharedPreferences. `null` means the host key has not been
     * pinned yet (first connection).
     */
    var sftpHostKey: String?
        get() = securePrefs.getString(KEY_SFTP_HOST_KEY, null)
        set(value) = securePrefs.edit().putString(KEY_SFTP_HOST_KEY, value).apply()

    // ── Theme ──

    /** "dark", "light", or "system" */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, UiConstants.THEME_DARK) ?: UiConstants.THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /**
     * Material You dynamic color (Android 12+ / API 31+).
     * When enabled, the app uses the system wallpaper-derived color scheme.
     * Default: false — preserves the Cernunnos brand identity by default.
     */
    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    // ── Sort ──

    /** "name", "issuer", "date", "favorites", or "manual" */
    var sortMode: String
        get() = prefs.getString(KEY_SORT_MODE, UiConstants.SORT_NAME) ?: UiConstants.SORT_NAME
        set(value) = prefs.edit().putString(KEY_SORT_MODE, value).apply()

    /**
     * Ordered list of entry IDs used when sortMode == "manual".
     * Entries not in this list are appended at the end (preserving their
     * natural order). Stored as a newline-separated string for simplicity.
     */
    var manualOrder: List<String>
        get() {
            val raw = prefs.getString(KEY_MANUAL_ORDER, null) ?: return emptyList()
            return raw.split('\n').filter { it.isNotEmpty() }
        }
        set(value) = prefs.edit().putString(KEY_MANUAL_ORDER, value.joinToString("\n")).apply()

    // ── View mode ──

    /** "list", "tiles", or "compact" */
    var viewMode: String
        get() = prefs.getString(KEY_VIEW_MODE, UiConstants.VIEW_MODE_LIST) ?: UiConstants.VIEW_MODE_LIST
        set(value) = prefs.edit().putString(KEY_VIEW_MODE, value).apply()

    /** Timestamp of last onPause — used for auto-lock on resume */
    var lastPauseTime: Long
        get() = prefs.getLong(KEY_LAST_PAUSE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PAUSE_TIME, value).apply()

    // ── Usage stats (per-entry view tracking) ──
    // Non-sensitive metadata stored in plaintext — acceptable per spec.

    fun incrementEntryViewCount(entryId: String) {
        val count = prefs.getInt("view_count_$entryId", 0)
        prefs.edit().putInt("view_count_$entryId", count + 1).apply()
        prefs.edit().putLong("last_viewed_$entryId", System.currentTimeMillis()).apply()
    }

    fun getEntryViewCount(entryId: String): Int = prefs.getInt("view_count_$entryId", 0)

    fun getEntryLastViewed(entryId: String): Long = prefs.getLong("last_viewed_$entryId", 0)

    // ── Language ──

    /** "system" (default), "en", or "fr" */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /**
     * Whether screenshots and screen recording are allowed.
     * Default: false (FLAG_SECURE enabled — blocks screenshots and screen recording).
     * When set to true, FLAG_SECURE is cleared, allowing screenshots.
     * This is a security risk and not recommended.
     */
    var allowScreenshots: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_SCREENSHOTS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_SCREENSHOTS, value).apply()

    /**
     * Whether TOTP codes are hidden behind a tap-to-reveal overlay.
     * When enabled, codes show as "••••••" until the user taps to reveal them.
     * Codes auto-hide after 10 seconds. Prevents shoulder-surfing.
     */
    var tapToReveal: Boolean
        get() = prefs.getBoolean(KEY_TAP_TO_REVEAL, false)
        set(value) = prefs.edit().putBoolean(KEY_TAP_TO_REVEAL, value).apply()
}
