package com.cernunnos.authenticator.ui.viewmodel

import com.cernunnos.authenticator.BuildConfig
import com.cernunnos.authenticator.R

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cernunnos.authenticator.cloud.AppAuthManager
import com.cernunnos.authenticator.cloud.CloudProvider
import com.cernunnos.authenticator.cloud.DropboxProvider
import com.cernunnos.authenticator.cloud.GoogleDriveProvider
import com.cernunnos.authenticator.cloud.SftpProvider
import com.cernunnos.authenticator.cloud.WebDavProvider
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.data.model.Category
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.data.repo.TotpRepository
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.AegisImporter
import com.cernunnos.authenticator.util.AndOtpImporter
import com.cernunnos.authenticator.util.AuthyImporter
import com.cernunnos.authenticator.util.BitwardenImporter
import com.cernunnos.authenticator.util.ExportImport
import com.cernunnos.authenticator.util.FreeOtpImporter
import com.cernunnos.authenticator.util.GoogleAuthImporter
import com.cernunnos.authenticator.util.LastPassImporter
import com.cernunnos.authenticator.util.MicrosoftAuthImporter
import com.cernunnos.authenticator.util.OtpAuthParser
import com.cernunnos.authenticator.util.PlainTextImporter
import com.cernunnos.authenticator.util.RaivoOtpImporter
import com.cernunnos.authenticator.util.SteamImporter
import com.cernunnos.authenticator.util.TwoFasImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Cipher

enum class VaultState {
    UNINITIALIZED, // No vault set yet → onboarding
    LOCKED,        // Vault set, need to unlock
    UNLOCKED,      // Ready to use
}

data class UiState(
    val vaultState: VaultState = VaultState.UNINITIALIZED,
    val vaultMode: BiometricVault.VaultMode? = null,
    val entries: List<TotpEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val listMode: String = "all", // "all" or "categories"
    val error: String? = null,
    val message: String? = null,
    val tick: Long = 0L,
    val pendingCipher: Cipher? = null, // For biometric unlock
    val themeMode: String = "dark", // "dark", "light", "system"
    val dynamicColorEnabled: Boolean = false, // Material You (Android 12+)
    val accessibilityWarning: String? = null,
    val backupError: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repo = TotpRepository(app)
    private val prefs = AppPreferences(app)
    private var lastActiveTime: Long = System.currentTimeMillis()

    /**
     * JSON instance used only for pre-import plaintext backups (device-credential
     * mode). Internal-storage only, never exported off-device.
     */
    private val preImportJson = Json { prettyPrint = false; ignoreUnknownKeys = true }

    /**
     * Pending otpauth:// URI received via deep linking while the vault was locked.
     * Processed (parsed + added) after the vault is unlocked.
     */
    var pendingOtpAuthUri: String? = null

    /**
     * Pending .cern file URI received via intent, stored in the ViewModel
     * (not static) so it survives configuration changes and process death
     * (via MainActivity.onSaveInstanceState).
     */
    private var _pendingCernUri: String? = null
    private var _cernFileTrigger: Int = 0

    /** Trigger that increments each time a new .cern URI is received. */
    val cernFileTrigger: Int get() = _cernFileTrigger

    /** Store a pending .cern URI from MainActivity. */
    fun setPendingCernUri(uri: String) {
        _pendingCernUri = uri
        _cernFileTrigger++
    }

    /** Get the pending .cern URI without consuming it (for onSaveInstanceState). */
    fun getPendingCernUri(): String? = _pendingCernUri

    /** Consume and return the pending .cern URI, then clear it. */
    fun consumePendingCernUri(): String? {
        val uri = _pendingCernUri
        _pendingCernUri = null
        return uri
    }

    private val _uiState = MutableStateFlow(
        UiState(
            vaultState = if (repo.isInitialized) VaultState.LOCKED else VaultState.UNINITIALIZED,
            vaultMode = repo.mode,
            categories = prefs.categories,
            listMode = prefs.listMode,
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            accessibilityWarning = checkAccessibilityWarning(),
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Ticker and auto-lock checker are started by onAppForegrounded()
        // when the activity resumes, and stopped by onAppBackgrounded()
        // when it pauses. This prevents battery drain in the background.
        onAppForegrounded()

        // Clean up stale temporary files from camera capture and sharing
        cleanupTempFiles()
    }

    /**
     * Clean up stale temporary files from camera capture and encrypted sharing.
     * These files may accumulate if the app was killed mid-operation.
     */
    private fun cleanupTempFiles() {
        try {
            val cacheDir = app.cacheDir
            val sharedDir = java.io.File(cacheDir, "shared")
            val secureSendDir = java.io.File(cacheDir, "secure_send")
            // Clean up old camera photos
            cacheDir.listFiles { f -> f.name.startsWith("doc_photo_") && f.name.endsWith(".jpg") }
                ?.forEach { it.delete() }
            // Clean up old share files in shared/ and root
            listOf(cacheDir, sharedDir).forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles { f -> f.name.startsWith("cernunnos_doc_") && f.name.endsWith(".enc") }
                        ?.forEach { it.delete() }
                    dir.listFiles { f -> f.name.startsWith("cernunnos_totp_") && f.name.endsWith(".txt") }
                        ?.forEach { it.delete() }
                    // Clean up decrypted documents from .cern files
                    dir.listFiles { f -> f.name.startsWith("decrypted_") }
                        ?.forEach { it.delete() }
                    // Clean up camera photos from SendDocumentScreen
                    dir.listFiles { f -> f.name.startsWith("photo_") && f.name.endsWith(".jpg") }
                        ?.forEach { it.delete() }
                }
            }
            // Clean up encrypted send files (PDF + .cern)
            if (secureSendDir.exists()) {
                secureSendDir.listFiles { f -> f.name.startsWith("cernunnos_secure_") }
                    ?.forEach { it.delete() }
            }
            // Clean up old pre-import encrypted backups from filesDir
            app.filesDir.listFiles { f -> f.name.startsWith("pre_import_backup_") && f.name.endsWith(".enc") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("AppViewModel", "temp file cleanup failed: ${e.message}")
        }
    }

    private fun checkAccessibilityWarning(): String? {
        val state = com.cernunnos.authenticator.util.AccessibilityDetector.getState(app)
        if (!state.enabled) return null
        // Only warn if there are non-system accessibility services.
        // Filter by checking if the service package is a system app.
        val pm = app.packageManager
        val nonSystem = state.services.filter { svc ->
            try {
                val pkgName = svc.substringBefore("/")
                val pkgInfo = pm.getPackageInfo(pkgName, 0)
                val isSystem = ((pkgInfo.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystem
            } catch (_: Exception) {
                // If we can't determine, be cautious and warn
                !svc.contains("google") && !svc.contains("android")
            }
        }
        return if (nonSystem.isNotEmpty()) {
            "Warning: ${nonSystem.size} accessibility service(s) detected. They can read your screen. Consider disabling them for security."
        } else null
    }

    // ── Lifecycle-aware background work ──
    // Ticker and auto-lock checker are only running while the app is in the
    // foreground. When the app goes to background they are cancelled to save
    // battery. Auto-lock on background is handled by MainActivity.onResume()
    // checking the elapsed time since lastPauseTime.
    private var tickerJob: kotlinx.coroutines.Job? = null
    private var autoLockJob: kotlinx.coroutines.Job? = null

    /** Called by MainActivity when the app enters the foreground. */
    fun onAppForegrounded() {
        // Record timestamp for widget "require unlock" feature
        prefs.lastAppOpenTs = System.currentTimeMillis()
        // Start ticker (only needed while UI is visible — drives TOTP countdowns)
        if (tickerJob?.isActive != true) {
            tickerJob = viewModelScope.launch {
                while (true) {
                    _uiState.value = _uiState.value.copy(tick = System.currentTimeMillis() / 1000)
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
        // Start auto-lock checker (only meaningful while user is actively using the app)
        if (autoLockJob?.isActive != true) {
            autoLockJob = viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    val timeout = prefs.autoLockTimeout
                    if (timeout > 0 && _uiState.value.vaultState == VaultState.UNLOCKED) {
                        val elapsed = (System.currentTimeMillis() - lastActiveTime) / 1000
                        if (elapsed >= timeout) {
                            // Grace period: if the user was active in the last 5 seconds
                            // (e.g. typing in a field), defer the lock to avoid losing work.
                            if (elapsed - timeout < 5) {
                                lastActiveTime = System.currentTimeMillis() - (timeout * 1000) + 5000
                            } else {
                                lock()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by MainActivity when the app enters the background.
     * Stops the ticker and auto-lock checker to save battery, and flushes
     * any pending debounced backup immediately so data isn't lost if the
     * app is killed by the OS.
     */
    fun onAppBackgrounded() {
        tickerJob?.cancel()
        tickerJob = null
        autoLockJob?.cancel()
        autoLockJob = null
        // Flush pending backup immediately — don't wait for the 10s debounce
        // because the app may be killed in the background.
        backupJob?.let {
            it.cancel()
            viewModelScope.launch(Dispatchers.IO) {
                autoBackup()
            }
        }
        backupJob = null
    }

    /** Called when user interacts with the app — resets the auto-lock timer. */
    fun onUserActivity() {
        lastActiveTime = System.currentTimeMillis()
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel all background jobs to prevent leaks
        tickerJob?.cancel()
        autoLockJob?.cancel()
        backupJob?.cancel()
    }

    // ── Passphrase mode ──

    fun initializeVault(passphrase: String) {
        if (passphrase.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_passphrase_empty))
            return
        }
        if (passphrase.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_passphrase_short))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pass = passphrase.toCharArray()
            try {
                repo.initializeWithPassphrase(pass)
                lastActiveTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNLOCKED,
                    vaultMode = BiometricVault.VaultMode.PASSPHRASE,
                    entries = emptyList(),
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            } finally {
                pass.fill(0.toChar())
            }
        }
    }

    fun unlock(passphrase: String) {
        // Check rate limiting
        val lockoutRemaining = prefs.unlockLockoutRemaining()
        if (lockoutRemaining > 0) {
            val seconds = lockoutRemaining / 1000
            _uiState.value = _uiState.value.copy(
                error = app.getString(R.string.err_unlock_locked_out, seconds),
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pass = passphrase.toCharArray()
            val success = try {
                repo.unlockWithPassphrase(pass)
            } finally {
                pass.fill(0.toChar())
            }
            if (success) {
                prefs.resetUnlockAttempts()
                lastActiveTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNLOCKED,
                    entries = repo.entries,
                    error = null,
                )
                // Sync from cloud after unlock
                syncFromCloud()
                // Process any pending otpauth:// URI received while locked
                processPendingOtpAuthUri()
            } else if (!repo.isInitialized) {
                // Vault was reset (data was missing) — go back to setup screen
                prefs.resetUnlockAttempts()
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNINITIALIZED,
                    vaultMode = null,
                    error = app.getString(R.string.err_vault_reset),
                )
            } else {
                val lockoutMs = prefs.recordFailedUnlock()
                val msg = if (lockoutMs > 0) {
                    val seconds = lockoutMs / 1000
                    app.getString(R.string.err_unlock_locked_out, seconds)
                } else {
                    val remaining = 5 - prefs.unlockAttempts
                    if (remaining > 0) {
                        app.getString(R.string.err_wrong_passphrase_attempts, remaining)
                    } else {
                        app.getString(R.string.err_wrong_passphrase)
                    }
                }
                _uiState.value = _uiState.value.copy(error = msg)
            }
        }
    }

    // ── Device credential mode ──

    /**
     * Get a cipher for initializing the biometric vault.
     * The caller must pass this to BiometricPrompt for authentication.
     */
    fun prepareDeviceCredentialInit(): Cipher? {
        return try {
            repo.prepareInitializationCipher()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_prepare, e.message))
            null
        }
    }

    fun completeDeviceCredentialInit(cipher: Cipher) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.initializeWithDeviceCredential(cipher)
                lastActiveTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNLOCKED,
                    vaultMode = BiometricVault.VaultMode.DEVICE_CREDENTIAL,
                    entries = emptyList(),
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_prepare, e.message))
            }
        }
    }

    /**
     * Prepare cipher for biometric unlock.
     */
    fun prepareDeviceCredentialUnlock(): Cipher? {
        return try {
            repo.getDecryptCipherForMasterKey()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_prepare_unlock, e.message))
            null
        }
    }

    fun completeDeviceCredentialUnlock(cipher: Cipher) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                repo.unlockWithDeviceCredential(cipher)
            } catch (e: Exception) {
                // Vault data may be missing — check if reset is needed
                if (!repo.isInitialized) {
                    _uiState.value = _uiState.value.copy(
                        vaultState = VaultState.UNINITIALIZED,
                        vaultMode = null,
                        error = app.getString(R.string.err_vault_reset),
                    )
                    return@launch
                }
                false
            }
            if (success) {
                lastActiveTime = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNLOCKED,
                    entries = repo.entries,
                    error = null,
                )
                // Sync from cloud after unlock
                syncFromCloud()
                processPendingOtpAuthUri()
            } else if (!repo.isInitialized) {
                _uiState.value = _uiState.value.copy(
                    vaultState = VaultState.UNINITIALIZED,
                    vaultMode = null,
                    error = app.getString(R.string.err_vault_reset),
                )
            } else {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_unlock_failed))
            }
        }
    }

    // ── Common ──

    fun lock() {
        try {
            repo.lock()
        } catch (e: Exception) {
            android.util.Log.e("AppViewModel", "lock() failed: ${e.message}", e)
        }
        prefs.vaultLockedTs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            vaultState = VaultState.LOCKED,
            entries = emptyList(),
        )
    }

    fun addEntryFromOtpAuth(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = OtpAuthParser.parse(uri)
                repo.addEntry(entry)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entry_added, entry.issuer),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_invalid_qr, e.message))
            }
        }
    }

    /**
     * Handle an otpauth:// URI received via deep linking (intent).
     * If the vault is unlocked, the entry is added immediately.
     * If the vault is locked or uninitialized, the URI is stored and processed after unlock.
     */
    fun handleOtpAuthUri(uri: String) {
        if (!uri.startsWith("otpauth://")) return
        when (_uiState.value.vaultState) {
            VaultState.UNLOCKED -> addEntryFromOtpAuth(uri)
            VaultState.LOCKED, VaultState.UNINITIALIZED -> {
                pendingOtpAuthUri = uri
                _uiState.value = _uiState.value.copy(
                    message = app.getString(R.string.msg_account_after_unlock),
                )
            }
            null -> {
                pendingOtpAuthUri = uri
            }
        }
    }

    /**
     * Process a pending otpauth:// URI (if any) after the vault has been unlocked.
     */
    private fun processPendingOtpAuthUri() {
        val uri = pendingOtpAuthUri ?: return
        pendingOtpAuthUri = null
        try {
            val entry = OtpAuthParser.parse(uri)
            repo.addEntry(entry)
            _uiState.value = _uiState.value.copy(
                entries = repo.entries,
                error = null,
                message = app.getString(R.string.msg_entry_added, entry.issuer),
            )
            scheduleAutoBackup()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_invalid_otpauth, e.message))
        }
    }

    fun addEntryManual(issuer: String, label: String, secretBase32: String, digits: Int, period: Int, categoryId: String? = null, type: String = "totp", counter: Long = 0L, pin: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // mOTP uses hex secrets, all other types use base32
                val secret = if (type == "motp") {
                    decodeHexSecret(secretBase32)
                } else {
                    OtpAuthParser.decodeBase32(secretBase32)
                }
                val entry = TotpEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    issuer = issuer,
                    label = label,
                    secret = secret,
                    digits = digits,
                    period = period,
                    categoryId = categoryId,
                    type = type,
                    counter = counter,
                    pin = pin,
                )
                repo.addEntry(entry)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entry_added, entry.issuer),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_invalid_secret, e.message))
            }
        }
    }

    /**
     * Decode a hex string secret (used by mOTP).
     * Accepts both uppercase and lowercase hex, with or without spaces.
     */
    private fun decodeHexSecret(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace(":", "")
        require(cleaned.length % 2 == 0) { "Hex secret must have an even number of characters" }
        require(cleaned.all { it in "0123456789abcdefABCDEF" }) { "Hex secret contains invalid characters" }
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun removeEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.removeEntry(id)
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    /**
     * Restore a previously-deleted entry, preserving its original ID.
     * Used by the swipe-to-delete Undo action.
     *
     * NOTE: The entry passed here is captured from `state.entries` at swipe time, which
     * reflects the latest state flow (including any HOTP counter increments applied via
     * [incrementHotp] or [updateEntryFields]). Both of those functions update
     * `_uiState.value.entries` before the user can swipe, so the captured entry already
     * has the correct counter. No stale-counter bug exists here.
     */
    fun restoreEntry(entry: TotpEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.addEntry(entry)
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    fun getEntry(id: String): TotpEntry? = repo.getEntry(id)

    fun exportEntries(passphrase: String): String? {
        if (passphrase.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_export_pass_short))
            return null
        }
        return try {
            ExportImport.export(repo.getAllEntriesForExport(), passphrase)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_export_failed, e.message))
            null
        }
    }

    /**
     * Create an automatic backup of the current vault state before a
     * potentially destructive import operation. The backup is written to
     * app-internal storage (filesDir) so it never leaves the device sandbox.
     *
     * In passphrase mode the backup is AES-256-GCM encrypted (ExportImport
     * format) using the current master passphrase. In device-credential mode
     * (no passphrase available) the backup is written as plaintext JSON to
     * internal storage only — it is still protected by the Android app
     * sandbox and is only used as a last-resort rollback snapshot.
     *
     * Returns the backup [File] (or null on failure) so the caller can
     * reference it in error messages.
     */
    private fun backupBeforeImport(): File? {
        return try {
            val currentEntries = repo.getAllEntriesForExport()
            val pass = repo.currentPassphraseCopy()
            val backupData: ByteArray = if (pass != null) {
                // Pass CharArray directly — ExportImport.export zeroes it after use.
                // This avoids creating an immutable String copy of the passphrase.
                ExportImport.export(currentEntries, pass).toByteArray()
            } else {
                // Device-credential mode: no passphrase available.
                // Encrypt with a device-bound Keystore key (no auth required)
                // so the backup is NOT plaintext on disk.
                val json = preImportJson.encodeToString(currentEntries).toByteArray()
                encryptWithDeviceKey(json)
            }
            val backupFile = File(app.filesDir, "pre_import_backup_${System.currentTimeMillis()}.enc")
            backupFile.writeBytes(backupData)
            backupFile
        } catch (e: Exception) {
            android.util.Log.w("AppViewModel", "pre-import backup failed: ${e.message}")
            null
        }
    }

    /**
     * Encrypt data with a device-bound Keystore key (no user auth required).
     * Used for pre-import backups in device-credential mode so that the
     * backup is never written as plaintext to disk.
     */
    private fun encryptWithDeviceKey(plaintext: ByteArray): ByteArray {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "cernunnos_pre_import_backup"
        if (!keyStore.containsAlias(alias)) {
            val gen = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            gen.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            gen.generateKey()
        }
        val key = keyStore.getKey(alias, null) as javax.crypto.SecretKey
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        // Prepend IV (12 bytes) to ciphertext
        return iv + ciphertext
    }

    /**
     * Decrypt data with the device-bound Keystore key (no user auth required).
     * Counterpart to [encryptWithDeviceKey] — used to restore pre-import
     * backups created in device-credential mode.
     *
     * The input must be in the format produced by [encryptWithDeviceKey]:
     * [IV (12 bytes)][ciphertext].
     */
    private fun decryptWithDeviceKey(data: ByteArray): ByteArray {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "cernunnos_pre_import_backup"
        val key = keyStore.getKey(alias, null) as javax.crypto.SecretKey
        // Extract the IV from the first 12 bytes
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Add entries transactionally with rollback on failure.
     * Returns the number of entries actually added.
     */
    private fun addEntriesTransaction(entries: List<TotpEntry>): Int {
        val addedIds = mutableListOf<String>()
        return try {
            entries.forEach { entry ->
                repo.addEntry(entry)
                addedIds.add(entry.id)
            }
            addedIds.size
        } catch (e: Exception) {
            // Rollback: remove entries that were already added
            addedIds.forEach { repo.removeEntry(it) }
            throw e
        }
    }

    fun importEntries(data: String, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = ExportImport.import(data, passphrase)
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message) +
                        (backup?.let { " — a pre-import backup was saved at ${it.absolutePath}" } ?: ""),
                )
            }
        }
    }

    fun importBitwarden(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val trimmed = data.trim()
                // Auto-detect: JSON starts with '{', CSV starts with header or comma
                val imported = if (trimmed.startsWith("{")) {
                    BitwardenImporter.import(trimmed)
                } else {
                    BitwardenImporter.importCsv(trimmed)
                }
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_bitwarden_import_failed, e.message) +
                        (backup?.let { " — a pre-import backup was saved at ${it.absolutePath}" } ?: ""),
                )
            }
        }
    }

    fun importGoogleAuth(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = GoogleAuthImporter.import(uri)
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_google_auth_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_google_auth_import_failed, e.message) +
                        (backup?.let { " — a pre-import backup was saved at ${it.absolutePath}" } ?: ""),
                )
            }
        }
    }

    fun importAegis(data: String, passphrase: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = AegisImporter.import(data, passphrase)
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_aegis_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_aegis_import_failed, e.message) +
                        (backup?.let { " — a pre-import backup was saved at ${it.absolutePath}" } ?: ""),
                )
            }
        }
    }

    // ── Generic import for additional formats ──

    fun importTwoFas(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = TwoFasImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importAuthy(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = AuthyImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importMicrosoftAuth(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = MicrosoftAuthImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importFreeOtp(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = FreeOtpImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importAndOtp(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = AndOtpImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importRaivoOtp(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = RaivoOtpImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importLastPass(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = LastPassImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importSteam(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = SteamImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun importPlainText(data: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val imported = PlainTextImporter.import(data.trim())
                val count = addEntriesTransaction(imported)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_entries_imported, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_import_failed, e.message),
                )
            }
        }
    }

    fun changePassphrase(oldPass: String, newPass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldChars = oldPass.toCharArray()
            val newChars = newPass.toCharArray()
            try {
                if (!repo.unlockWithPassphrase(oldChars)) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_wrong_current_passphrase))
                    return@launch
                }
                if (newPass.length < 8) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_new_passphrase_short))
                    return@launch
                }
                repo.changePassphrase(newChars)
                _uiState.value = _uiState.value.copy(error = null, message = app.getString(R.string.msg_passphrase_changed))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_change_passphrase_failed, e.message))
            } finally {
                oldChars.fill(0.toChar())
                newChars.fill(0.toChar())
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
    fun clearBackupError() { _uiState.value = _uiState.value.copy(backupError = null) }
    fun setError(msg: String) { _uiState.value = _uiState.value.copy(error = msg) }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.dynamicColorEnabled = enabled
        _uiState.value = _uiState.value.copy(dynamicColorEnabled = enabled)
    }

    /** Returns list of available backup files, newest first. */
    fun listBackups(): List<File> {
        val backupDir = File(app.filesDir, "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { f -> f.name.startsWith("cernunnos_backup_") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Restores from a backup file using the given passphrase. */
    fun restoreBackup(file: File, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = if (file.name.endsWith(".enc")) {
                    // Encrypted pre-import backup (device-credential mode):
                    // decrypt with the device-bound Keystore key before importing.
                    val encryptedData = file.readBytes()
                    val plaintext = decryptWithDeviceKey(encryptedData)
                    val jsonStr = String(plaintext, Charsets.UTF_8)
                    ExportImport.import(jsonStr, passphrase)
                } else {
                    // Old format (.txt/.json) or passphrase mode: use the
                    // existing ExportImport.import() with the passphrase.
                    val data = file.readText()
                    ExportImport.import(data, passphrase)
                }
                entries.forEach { repo.addEntry(it) }
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_backup_restored, entries.size),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_restore_failed, e.message))
            }
        }
    }

    /**
     * Debounced backup — waits 10 seconds after the last interaction before
     * actually writing. This prevents OOM when multiple mutations happen in
     * quick succession (e.g. multi-select delete of 3 entries), because each
     * backup calls Argon2id which allocates ~96MB.
     */
    private var backupJob: kotlinx.coroutines.Job? = null

    private fun scheduleAutoBackup() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(10_000) // 10 seconds
            autoBackup()
        }
    }

    private fun autoBackup() {
        // Local backup (independent)
        if (prefs.autoBackupEnabled) {
            val pass = prefs.autoBackupPassphrase
            if (pass != null && pass.length >= 8) {
                try {
                    val encrypted = ExportImport.export(repo.getAllEntriesForExport(), pass)
                    val backupDir = File(app.filesDir, "backups")
                    backupDir.mkdirs()

                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                    val uid = java.util.UUID.randomUUID().toString().take(8)
                    val backupFile = File(backupDir, "cernunnos_backup_${ts}_$uid.txt")
                    // Atomic write: write to .tmp first, then rename
                    val tmpFile = File(backupDir, "${backupFile.name}.tmp")
                    tmpFile.writeText(encrypted)
                    if (!tmpFile.renameTo(backupFile)) {
                        // Fallback: copy if rename fails (cross-filesystem)
                        tmpFile.copyTo(backupFile, overwrite = true)
                        tmpFile.delete()
                    }

                    // Only rotate if the new backup is valid
                    if (backupFile.length() > 16) {
                        val backups = backupDir.listFiles { f -> f.name.startsWith("cernunnos_backup_") }
                            ?.sortedByDescending { it.lastModified() }
                            ?: emptyList()
                        backups.drop(10).forEach { it.delete() }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Cernunnos", "Local backup failed: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        backupError = app.getString(R.string.err_local_backup_failed, e.message)
                    )
                }
            }
        }

        // Cloud backup (independent of local)
        if (prefs.cloudBackupEnabled) {
            val pass = prefs.cloudBackupPassphrase
            if (pass != null && pass.length >= 8) {
                try {
                    val entries = repo.getAllEntriesForExport()
                    val encrypted = ExportImport.export(entries, pass)
                    cloudBackup(encrypted)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_backup_error, e.message))
                }
            }
        }

        // Documents backup — copy encrypted document files to a backup directory
        // Uses atomic write (tmp + rename) to avoid partial files on crash.
        try {
            val docsDir = java.io.File(app.filesDir, "documents")
            if (docsDir.exists()) {
                val docBackupDir = java.io.File(app.filesDir, "document_backups")
                if (!docBackupDir.exists() && !docBackupDir.mkdirs()) {
                    android.util.Log.w("Cernunnos", "Failed to create document backup directory")
                } else {
                    // Copy all .enc files atomically
                    docsDir.listFiles { f -> f.name.endsWith(".enc") }?.forEach { src ->
                        val dst = java.io.File(docBackupDir, src.name)
                        val tmp = java.io.File(docBackupDir, "${src.name}.tmp")
                        src.copyTo(tmp, overwrite = true)
                        if (dst.exists()) dst.delete()
                        if (!tmp.renameTo(dst)) {
                            // Fallback: copy if rename fails (cross-filesystem)
                            tmp.copyTo(dst, overwrite = true)
                            tmp.delete()
                        }
                    }
                    // Copy the encrypted index from SharedPreferences
                    val docPrefs = app.getSharedPreferences("cernunnos_documents", android.content.Context.MODE_PRIVATE)
                    val iv = docPrefs.getString("doc_index_iv", null)
                    val data = docPrefs.getString("doc_index_data", null)
                    if (iv != null && data != null) {
                        val indexFile = java.io.File(docBackupDir, "index.txt")
                        val tmpIndex = java.io.File(docBackupDir, "index.txt.tmp")
                        tmpIndex.writeText("$iv\n$data")
                        if (indexFile.exists()) indexFile.delete()
                        tmpIndex.renameTo(indexFile)
                    }
                    // Clean up orphaned .tmp files from previous interrupted backups
                    docBackupDir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Cernunnos", "Documents backup failed: ${e.message}")
        }

        // Documents cloud backup — upload encrypted document archive to cloud
        // The archive is a simple concatenation: index line + base64 of each .enc file.
        // The whole archive is then encrypted with the cloud backup passphrase.
        if (prefs.cloudBackupEnabled) {
            val cloudPass = prefs.cloudBackupPassphrase
            if (cloudPass != null && cloudPass.length >= 8) {
                try {
                    val docsDir = java.io.File(app.filesDir, "documents")
                    val docPrefs = app.getSharedPreferences("cernunnos_documents", android.content.Context.MODE_PRIVATE)
                    val iv = docPrefs.getString("doc_index_iv", null)
                    val data = docPrefs.getString("doc_index_data", null)
                    if (docsDir.exists() && iv != null && data != null) {
                        val archive = buildDocumentCloudArchive(docsDir, iv, data)
                        val encrypted = encryptDocumentArchive(archive, cloudPass)
                        if (encrypted != null) {
                            cloudBackupDocuments(encrypted)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Cernunnos", "Documents cloud backup failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Build a portable archive of all encrypted document files + index.
     * Format (line-based, easy to parse):
     *   INDEX:<iv>:<data>
     *   FILE:<filename>:<base64 content>
     *   FILE:<filename>:<base64 content>
     *   ...
     */
    private fun buildDocumentCloudArchive(docsDir: java.io.File, indexIv: String, indexData: String): String {
        val sb = StringBuilder()
        sb.append("INDEX:").append(indexIv).append(":").append(indexData).append("\n")
        docsDir.listFiles { f -> f.name.endsWith(".enc") }?.forEach { file ->
            val content = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            sb.append("FILE:").append(file.name).append(":").append(content).append("\n")
        }
        return sb.toString()
    }

    /**
     * Encrypt a document archive using AES-256-GCM with Argon2id key derivation.
     * Format: docv1:base64(salt):base64(iv):base64(ciphertext)
     */
    private fun encryptDocumentArchive(archive: String, passphrase: String): String? {
        return try {
            val salt = com.cernunnos.authenticator.data.crypto.Argon2id.generateSalt()
            val encrypted = com.cernunnos.authenticator.data.crypto.CryptoManager.encrypt(
                archive.toByteArray(),
                passphrase.toCharArray(),
                salt,
            )
            val payload = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP) + ":" +
                android.util.Base64.encodeToString(encrypted.iv, android.util.Base64.NO_WRAP) + ":" +
                android.util.Base64.encodeToString(encrypted.ciphertext, android.util.Base64.NO_WRAP)
            "docv1:" + payload
        } catch (e: Exception) {
            android.util.Log.w("Cernunnos", "Document archive encryption failed: ${e.message}")
            null
        }
    }

    /**
     * Upload encrypted document archive to the cloud provider.
     * Uses a distinct filename prefix so it doesn't clash with TOTP backups.
     * Rotates: keeps only the 5 most recent document backups.
     */
    private fun cloudBackupDocuments(encrypted: String) {
        val provider = getCloudProvider() ?: return
        try {
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val uid = java.util.UUID.randomUUID().toString().take(8)
            val fileName = "cernunnos_docs_${ts}_$uid.txt"
            val ok = provider.upload(fileName, encrypted.toByteArray())
            if (!ok) {
                android.util.Log.w("Cernunnos", "Documents cloud upload failed")
            } else {
                // Rotate: delete old document backups beyond the 5 most recent
                try {
                    val docBackups = provider.listBackups()
                        .filter { it.name.startsWith("cernunnos_docs_") }
                        .sortedByDescending { it.modified }
                    docBackups.drop(5).forEach { old ->
                        provider.deleteBackup(old.name)
                    }
                } catch (e: Exception) {
                    // Rotation failure is non-fatal
                    android.util.Log.w("Cernunnos", "Document backup rotation failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("Cernunnos", "Documents cloud backup error: ${e.message}")
        }
    }

    /**
     * Restore documents from a cloud backup archive.
     * Downloads the latest docs backup, decrypts it, and restores files + index.
     * Returns true on success.
     */
    fun restoreDocumentsFromCloud(passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = getCloudProvider() ?: error(app.getString(R.string.err_no_cloud_provider))
                // Find the latest docs backup
                val backups = provider.listBackups().filter { it.name.startsWith("cernunnos_docs_") }
                if (backups.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_no_cloud_backup))
                    return@launch
                }
                val latest = backups.first()
                val data = provider.downloadLatestByName(latest.name)
                if (data == null) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_download_failed))
                    return@launch
                }
                val content = String(data, Charsets.UTF_8).trim()
                // Parse: docv1:salt:iv:ciphertext
                val parts = content.split(":")
                if (parts.size != 4 || parts[0] != "docv1") {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_invalid_backup_format))
                    return@launch
                }
                val salt = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                val iv = android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP)
                val ciphertext = android.util.Base64.decode(parts[3], android.util.Base64.NO_WRAP)
                val decrypted = com.cernunnos.authenticator.data.crypto.CryptoManager.decrypt(
                    com.cernunnos.authenticator.data.crypto.CryptoManager.EncryptedData(salt, iv, ciphertext),
                    passphrase.toCharArray(),
                )
                val archive = String(decrypted)
                // Parse archive and restore
                val docsDir = java.io.File(app.filesDir, "documents")
                docsDir.mkdirs()
                // Clear existing documents to avoid mixed state
                docsDir.listFiles { f -> f.name.endsWith(".enc") }?.forEach { it.delete() }
                val docPrefs = app.getSharedPreferences("cernunnos_documents", android.content.Context.MODE_PRIVATE)
                for (line in archive.lines()) {
                    if (line.startsWith("INDEX:")) {
                        val idx = line.substring(6)
                        val colonIdx = idx.indexOf(":")
                        if (colonIdx > 0) {
                            docPrefs.edit()
                                .putString("doc_index_iv", idx.substring(0, colonIdx))
                                .putString("doc_index_data", idx.substring(colonIdx + 1))
                                .commit()
                        }
                    } else if (line.startsWith("FILE:")) {
                        val rest = line.substring(5)
                        val colonIdx = rest.indexOf(":")
                        if (colonIdx > 0) {
                            val rawFileName = rest.substring(0, colonIdx)
                            // Sanitize filename to prevent path traversal
                            val safeFileName = rawFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            if (safeFileName.isBlank() || safeFileName.startsWith(".")) continue
                            val contentB64 = rest.substring(colonIdx + 1)
                            val fileBytes = android.util.Base64.decode(contentB64, android.util.Base64.NO_WRAP)
                            java.io.File(docsDir, safeFileName).writeBytes(fileBytes)
                        }
                    }
                }
                android.util.Log.i("Cernunnos", "Documents restored from cloud backup")
                _uiState.value = _uiState.value.copy(message = app.getString(R.string.msg_docs_restored))
            } catch (e: Exception) {
                android.util.Log.e("Cernunnos", "Documents cloud restore failed: ${e.message}")
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_restore_failed, e.message))
            }
        }
    }

    /**
     * Restore documents from the local backup directory.
     * Copies encrypted files and index back to the documents directory.
     * The vault must be unlocked with the correct passphrase afterwards.
     */
    fun restoreDocumentsBackup(): Boolean {
        return try {
            val docBackupDir = java.io.File(app.filesDir, "document_backups")
            if (!docBackupDir.exists()) return false

            val docsDir = java.io.File(app.filesDir, "documents")
            docsDir.mkdirs()

            // Clear existing documents to avoid mixed state
            docsDir.listFiles { f -> f.name.endsWith(".enc") }?.forEach { it.delete() }

            // Restore encrypted files
            docBackupDir.listFiles { f -> f.name.endsWith(".enc") }?.forEach { src ->
                val dst = java.io.File(docsDir, src.name)
                src.copyTo(dst, overwrite = true)
            }

            // Restore index
            val indexFile = java.io.File(docBackupDir, "index.txt")
            if (indexFile.exists()) {
                val lines = indexFile.readLines()
                if (lines.size >= 2) {
                    val docPrefs = app.getSharedPreferences("cernunnos_documents", android.content.Context.MODE_PRIVATE)
                    docPrefs.edit()
                        .putString("doc_index_iv", lines[0])
                        .putString("doc_index_data", lines[1])
                        .commit()
                }
            }
            android.util.Log.i("Cernunnos", "Documents backup restored successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e("Cernunnos", "Documents backup restore failed", e)
            false
        }
    }

    /** Manual cloud backup — triggered by user button */
    fun backupNow() {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("Cernunnos", "backupNow() called")
            android.util.Log.d("Cernunnos", "cloudBackupEnabled=${prefs.cloudBackupEnabled}")
            android.util.Log.d("Cernunnos", "cloudProvider=${prefs.cloudProvider}")
            android.util.Log.d("Cernunnos", "gdriveToken=${prefs.gdriveToken != null}")
            android.util.Log.d("Cernunnos", "passphrase set=${prefs.cloudBackupPassphrase != null}")
        }

        if (!prefs.cloudBackupEnabled) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_not_enabled))
            return
        }
        val pass = prefs.cloudBackupPassphrase
        if (pass == null || pass.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_no_backup_passphrase))
            return
        }

        _uiState.value = _uiState.value.copy(message = app.getString(R.string.msg_backing_up), error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = getCloudProvider()
                if (provider == null) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_no_cloud_provider))
                    return@launch
                }
                if (BuildConfig.DEBUG) android.util.Log.d("Cernunnos", "provider=${provider.id}, authenticated=${provider.isAuthenticated()}")
                if (!provider.isAuthenticated() && !provider.authenticate()) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_auth_failed))
                    return@launch
                }

                val entries = repo.getAllEntriesForExport()
                if (BuildConfig.DEBUG) android.util.Log.d("Cernunnos", "entries to backup: ${entries.size}")
                val encrypted = ExportImport.export(entries, pass)
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val uid = java.util.UUID.randomUUID().toString().take(8)
                val fileName = "cernunnos_backup_${ts}_$uid.txt"
                if (BuildConfig.DEBUG) android.util.Log.d("Cernunnos", "uploading $fileName (${encrypted.length} chars)")
                val ok = provider.upload(fileName, encrypted.toByteArray())
                if (BuildConfig.DEBUG) android.util.Log.d("Cernunnos", "upload result: $ok")

                _uiState.value = _uiState.value.copy(
                    message = if (ok) app.getString(R.string.msg_backup_uploaded, fileName, entries.size) else app.getString(R.string.msg_upload_failed),
                    error = if (ok) null else app.getString(R.string.err_upload_failed),
                )
            } catch (e: Exception) {
                android.util.Log.e("Cernunnos", "backup error", e)
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_sync_error, e.message))
            }
        }
    }

    private fun getCloudProvider(): CloudProvider? {
        return when (prefs.cloudProvider) {
            "dropbox" -> DropboxProvider(app)
            "gdrive" -> GoogleDriveProvider(app)
            "webdav" -> WebDavProvider(app)
            "sftp" -> SftpProvider(app)
            else -> null
        }
    }

    private fun cloudBackup(encrypted: String) {
        val provider = getCloudProvider() ?: return
        try {
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val uid = java.util.UUID.randomUUID().toString().take(8)
            val fileName = "cernunnos_backup_${ts}_$uid.txt"
            val ok = provider.upload(fileName, encrypted.toByteArray())
            if (!ok) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_upload_failed))
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_backup_error, e.message))
        }
    }

    /** Set up Dropbox — just stores passphrase, then OAuth flow */
    fun setupDropbox(passphrase: String) {
        if (!prefs.secureStorageAvailable) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_secure_storage_unavailable))
            return
        }
        prefs.cloudBackupPassphrase = passphrase
        prefs.cloudProvider = "dropbox"
    }

    /** Start Dropbox OAuth flow — opens browser via AppAuth */
    fun startDropboxOAuth(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
        val authManager = AppAuthManager(app)
        val deferred = authManager.startDropboxFlow(activity)
        viewModelScope.launch {
            val success = deferred.await()
            if (success) {
                prefs.cloudBackupEnabled = true
                scheduleAutoBackup()
            }
            _uiState.value = _uiState.value.copy(
                error = if (success) null else app.getString(R.string.err_dropbox_oauth_failed),
            )
            onResult(success)
        }
    }

    /** Set up Google Drive — just stores passphrase, then OAuth flow */
    fun setupGoogleDrive(passphrase: String) {
        if (!prefs.secureStorageAvailable) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_secure_storage_unavailable))
            return
        }
        prefs.cloudBackupPassphrase = passphrase
        prefs.cloudProvider = "gdrive"
    }

    /** Start Google OAuth flow — opens browser via AppAuth */
    fun startGoogleOAuth(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
        val authManager = AppAuthManager(app)
        val deferred = authManager.startGoogleFlow(activity)
        viewModelScope.launch {
            val success = deferred.await()
            if (success) {
                prefs.cloudBackupEnabled = true
                scheduleAutoBackup()
            }
            _uiState.value = _uiState.value.copy(
                error = if (success) null else app.getString(R.string.err_google_oauth_failed),
            )
            onResult(success)
        }
    }

    /** Handle OAuth redirect result — call from Activity.onActivityResult */
    fun handleOAuthResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val authManager = AppAuthManager(app)
        authManager.handleResult(requestCode, resultCode, data)
    }

    /** Set up WebDAV provider */
    fun setupWebDav(url: String, username: String, password: String, passphrase: String): Boolean {
        if (!prefs.secureStorageAvailable) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_secure_storage_unavailable))
            return false
        }
        val provider = WebDavProvider(app)
        provider.setCredentials(url, username, password)
        if (!provider.authenticate()) return false
        prefs.cloudProvider = "webdav"
        prefs.cloudBackupPassphrase = passphrase
        prefs.cloudBackupEnabled = true
        autoBackup()
        return true
    }

    /** Set up SFTP provider */
    fun setupSftp(host: String, port: Int, username: String, password: String, remotePath: String, passphrase: String): Boolean {
        if (!prefs.secureStorageAvailable) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_secure_storage_unavailable))
            return false
        }
        val provider = SftpProvider(app)
        provider.setCredentials(host, port, username, password, remotePath)
        if (!provider.authenticate()) return false
        prefs.cloudProvider = "sftp"
        prefs.cloudBackupPassphrase = passphrase
        prefs.cloudBackupEnabled = true
        autoBackup()
        return true
    }

    fun disableCloudBackup() {
        getCloudProvider()?.logout()
        prefs.cloudBackupEnabled = false
        prefs.cloudSyncEnabled = false
        prefs.cloudProvider = ""
        prefs.cloudBackupPassphrase = null
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        prefs.cloudSyncEnabled = enabled
    }

    /**
     * Sync from cloud: download the most recent backup, decrypt, merge new entries.
     *
     * NOTE: This is a ONE-WAY MERGE, not a bidirectional sync.
     * - New entries on the remote are added locally.
     * - Entries with the same ID but different secret/counter are updated locally.
     * - Entries with the same issuer+label but different ID are flagged as conflicts.
     * - Deletions on the remote are NOT propagated locally (no tombstones).
     *   This is a deliberate design choice to prevent accidental data loss.
     */
    fun syncFromCloud() {
        if (!prefs.cloudBackupEnabled) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_backup_not_enabled))
            return
        }
        val pass = prefs.cloudBackupPassphrase
        if (pass == null || pass.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_no_backup_passphrase))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = getCloudProvider()
                if (provider == null) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_no_cloud_provider))
                    return@launch
                }
                if (!provider.isAuthenticated() && !provider.authenticate()) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_cloud_auth_failed_short))
                    return@launch
                }

                val backups = provider.listBackups()
                if (backups.isEmpty()) {
                    _uiState.value = _uiState.value.copy(message = app.getString(R.string.msg_no_cloud_backups))
                    return@launch
                }
                val latest = backups.first()

                if (latest.modified <= prefs.cloudLastSync) {
                    _uiState.value = _uiState.value.copy(message = app.getString(R.string.msg_already_up_to_date, java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(prefs.cloudLastSync))))
                    return@launch
                }

                val data = provider.downloadLatest()
                if (data == null) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_download_failed))
                    return@launch
                }
                val remoteEntries = ExportImport.import(String(data), pass)

                val localEntries = repo.entries
                val localById = localEntries.associateBy { it.id }.toMutableMap()
                val localByKey = localEntries.associateBy { "${it.issuer.lowercase()}:${it.label.lowercase()}" }.toMutableMap()

                val toAdd = mutableListOf<TotpEntry>()
                val toUpdate = mutableListOf<TotpEntry>()
                var conflicts = 0

                remoteEntries.forEach { remote ->
                    val key = "${remote.issuer.lowercase()}:${remote.label.lowercase()}"
                    val existingById = localById[remote.id]
                    val existingByKey = localByKey[key]

                    when {
                        // Same ID exists locally → update if remote is newer (different secret/counter)
                        existingById != null -> {
                            if (existingById.secret.contentEquals(remote.secret).not() ||
                                existingById.counter != remote.counter
                            ) {
                                toUpdate.add(remote)
                            }
                        }
                        // Same issuer+label exists locally but different ID → conflict
                        existingByKey != null -> {
                            if (existingByKey.secret.contentEquals(remote.secret).not()) {
                                conflicts++
                                // Skip conflicting entry; user must resolve manually
                            }
                        }
                        // New entry
                        else -> toAdd.add(remote)
                    }
                }

                // Transactional add: rollback on failure
                val newCount = try {
                    addEntriesTransaction(toAdd)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_sync_failed, e.message))
                    return@launch
                }

                // Apply updates — transactional: stop at first error and restore previous state
                val entriesBeforeSync = repo.entries.toList()
                var updatedCount = 0
                var syncFailed = false
                toUpdate.forEach { entry ->
                    if (syncFailed) return@forEach
                    try {
                        repo.updateEntry(entry)
                        updatedCount++
                    } catch (e: Exception) {
                        android.util.Log.e("Cernunnos", "Sync update failed for ${entry.id}, aborting sync", e)
                        syncFailed = true
                    }
                }
                if (syncFailed) {
                    // Restore previous state
                    entriesBeforeSync.forEach { entry ->
                        try { repo.updateEntry(entry) } catch (e: Exception) { if (BuildConfig.DEBUG) android.util.Log.w("Cernunnos", "Rollback failed for ${entry.id}: ${e.message}") }
                    }
                    _uiState.value = _uiState.value.copy(
                        entries = repo.entries,
                        error = app.getString(R.string.err_sync_partial_failure),
                    )
                    return@launch
                }

                prefs.cloudLastSync = latest.modified

                val msg = buildString {
                    if (newCount > 0) append(app.getString(R.string.sync_new_entries, newCount))
                    if (updatedCount > 0) {
                        if (isNotEmpty()) append(", ")
                        append(app.getString(R.string.sync_updated, updatedCount))
                    }
                    if (conflicts > 0) {
                        if (isNotEmpty()) append(", ")
                        append(app.getString(R.string.sync_conflicts, conflicts))
                    }
                    if (newCount == 0 && updatedCount == 0 && conflicts == 0) {
                        append(app.getString(R.string.sync_no_changes))
                    }
                }
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    message = msg,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_sync_error, e.message))
            }
        }
    }

    /** Restore latest from cloud */
    fun restoreFromCloud(passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backup = backupBeforeImport()
            try {
                val provider = getCloudProvider() ?: error(app.getString(R.string.err_no_cloud_provider))
                if (!provider.isAuthenticated() && !provider.authenticate()) {
                    error(app.getString(R.string.err_cloud_auth_failed_short))
                }
                val data = provider.downloadLatest() ?: error(app.getString(R.string.err_no_backup_found))
                val entries = ExportImport.import(String(data), passphrase)
                val count = addEntriesTransaction(entries)
                _uiState.value = _uiState.value.copy(
                    entries = repo.entries,
                    error = null,
                    message = app.getString(R.string.msg_cloud_restored, count),
                )
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = app.getString(R.string.err_cloud_restore_failed, e.message) +
                        (backup?.let { " — a pre-import backup was saved at ${it.absolutePath}" } ?: ""),
                )
            }
        }
    }

    // ── Categories ──

    fun addCategory(name: String) {
        val current = _uiState.value.categories.toMutableList()
        if (name.isBlank() || current.any { it.name.equals(name, ignoreCase = true) }) return
        val cat = Category(id = "cat_" + java.util.UUID.randomUUID().toString().take(8), name = name.trim())
        current.add(cat)
        prefs.categories = current
        _uiState.value = _uiState.value.copy(categories = current)
    }

    fun renameCategory(id: String, newName: String) {
        val current = _uiState.value.categories.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0 || newName.isBlank()) return
        current[idx] = current[idx].copy(name = newName.trim())
        prefs.categories = current
        _uiState.value = _uiState.value.copy(categories = current)
    }

    fun deleteCategory(id: String) {
        // Removing a category does NOT delete TOTP entries; they just become uncategorized.
        val current = _uiState.value.categories.toMutableList()
        current.removeAll { it.id == id }
        prefs.categories = current
        // Null out categoryId on entries that referenced it
        val updatedEntries = _uiState.value.entries.map {
            if (it.categoryId == id) it.copy(categoryId = null) else it
        }
        updatedEntries.forEach { repo.updateEntry(it) }
        _uiState.value = _uiState.value.copy(categories = current, entries = repo.entries)
    }

    fun assignCategory(entryId: String, categoryId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = repo.getEntry(entryId) ?: return@launch
            val updated = entry.copy(categoryId = categoryId)
            repo.updateEntry(updated)
            _uiState.value = _uiState.value.copy(entries = repo.entries)
            scheduleAutoBackup()
        }
    }

    fun setListMode(mode: String) {
        prefs.listMode = mode
        _uiState.value = _uiState.value.copy(listMode = mode)
    }

    /**
     * Persist a new manual ordering of entry IDs.
     * Used by the drag-and-drop / up-down reorder UI when sortMode == "manual".
     */
    fun reorderEntries(newOrder: List<String>) {
        prefs.manualOrder = newOrder
    }

    /**
     * Remove multiple entries at once (multi-select delete).
     */
    fun removeEntries(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Single vault write instead of N writes — prevents OOM from
                // repeated encrypt/save/recompose cycles.
                repo.removeEntries(ids.toSet())
                // Clean up manual order for removed entries
                val updatedOrder = prefs.manualOrder.filter { it !in ids }
                if (updatedOrder != prefs.manualOrder) prefs.manualOrder = updatedOrder
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    /**
     * Export only the selected entries (multi-select export).
     */
    fun exportSelectedEntries(ids: Set<String>, passphrase: String): String? {
        if (passphrase.length < 8) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_export_pass_short))
            return null
        }
        return try {
            val selected = repo.entries.filter { it.id in ids }
            ExportImport.export(selected, passphrase)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_export_failed, e.message))
            null
        }
    }

    fun updateEntryFields(
        id: String,
        issuer: String,
        label: String,
        secret: ByteArray,
        algorithm: String,
        digits: Int,
        period: Int,
        iconName: String? = null,
        customIconUri: String? = null,
        type: String = "totp",
        counter: Long = 0L,
        pin: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repo.getEntry(id) ?: return@launch
                val updated = entry.copy(
                    issuer = issuer.trim(),
                    label = label.trim(),
                    secret = secret,
                    algorithm = algorithm,
                    digits = digits,
                    period = period,
                    iconName = iconName,
                    customIconUri = customIconUri,
                    type = type,
                    counter = counter,
                    pin = pin,
                )
                repo.updateEntry(updated)
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    fun toggleFavorite(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repo.getEntry(entryId) ?: return@launch
                val updated = entry.copy(favorite = !entry.favorite)
                repo.updateEntry(updated)
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    fun incrementHotp(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repo.getEntry(entryId) ?: return@launch
                if (entry.type != "hotp") return@launch
                if (entry.counter == Long.MAX_VALUE) {
                    _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_hotp_overflow))
                    return@launch
                }
                val updated = entry.copy(counter = entry.counter + 1)
                repo.updateEntry(updated)
                _uiState.value = _uiState.value.copy(entries = repo.entries)
                scheduleAutoBackup()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = app.getString(R.string.err_generic, e.message))
            }
        }
    }

    // ── Usage stats ──

    fun incrementEntryViewCount(entryId: String) = prefs.incrementEntryViewCount(entryId)

    fun getEntryViewCount(entryId: String): Int = prefs.getEntryViewCount(entryId)

    fun getEntryLastViewed(entryId: String): Long = prefs.getEntryLastViewed(entryId)
}
