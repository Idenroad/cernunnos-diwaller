package com.cernunnos.authenticator.ui.screens

import android.view.WindowManager
import com.cernunnos.authenticator.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.ui.theme.cernunnosChipColors
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.ui.viewmodel.VaultState
import com.cernunnos.authenticator.util.AccessibilityDetector

/**
 * A reusable collapsible settings section with a clickable header.
 * The header shows an icon + title + an expand/collapse arrow.
 */
@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onP2P: () -> Unit,
    onDecryptCern: (String) -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val accessibility = remember { AccessibilityDetector.getState(context) }
    val prefs = remember { AppPreferences(context) }
    var splashAnimEnabled by remember { mutableStateOf(prefs.splashAnimationEnabled) }
    var dynamicColorEnabled by remember { mutableStateOf(prefs.dynamicColorEnabled) }

    // Widget settings state
    var widgetEnabled by remember { mutableStateOf(prefs.widgetCodesEnabled) }
    var widgetMode by remember { mutableStateOf(prefs.widgetMode) }
    var widgetCategory by remember { mutableStateOf(prefs.widgetCategory) }
    var widgetMaxEntries by remember { mutableStateOf(prefs.widgetMaxEntries.toFloat()) }
    var widgetRequireUnlock by remember { mutableStateOf(prefs.widgetRequireUnlock) }
    var widgetCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(prefs.language) }
    var allowScreenshots by remember { mutableStateOf(prefs.allowScreenshots) }
    var tapToReveal by remember { mutableStateOf(prefs.tapToReveal) }

    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var editingCategoryName by remember { mutableStateOf("") }
    var autoBackupEnabled by remember { mutableStateOf(prefs.autoBackupEnabled) }
    var pendingCernUri by remember { mutableStateOf<String?>(null) }
    val cernFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingCernUri = uri.toString()
        }
    }
    LaunchedEffect(pendingCernUri) {
        pendingCernUri?.let {
            pendingCernUri = null
            onDecryptCern(it)
        }
    }
    var autoBackupPass by remember { mutableStateOf(prefs.autoBackupPassphrase ?: "") }
    var autoLockTimeout by remember { mutableStateOf(prefs.autoLockTimeout.toString()) } // default 10s
    var cloudPass by remember { mutableStateOf("") }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(state.themeMode) }
    var sortMode by remember { mutableStateOf(prefs.sortMode) }
    var viewMode by remember { mutableStateOf(prefs.viewMode) }
    var selectedProvider by remember { mutableStateOf("webdav") }
    var dropboxAuthInProgress by remember { mutableStateOf(false) }
    var gdriveAuthInProgress by remember { mutableStateOf(false) }
    var webdavUrl by remember { mutableStateOf("") }
    var webdavUser by remember { mutableStateOf("") }
    var webdavPass by remember { mutableStateOf("") }
    var sftpHost by remember { mutableStateOf("") }
    var sftpPort by remember { mutableStateOf("22") }
    var sftpUser by remember { mutableStateOf("") }
    var sftpPass by remember { mutableStateOf("") }
    var sftpPath by remember { mutableStateOf("Cernunnos") }

    // Show/hide password toggles
    var showAutoBackupPass by remember { mutableStateOf(false) }
    var showCloudPass by remember { mutableStateOf(false) }
    var showWebdavPass by remember { mutableStateOf(false) }
    var showSftpPass by remember { mutableStateOf(false) }
    var showOldPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }

    // Biometric unlock toggle — only configurable for passphrase-mode vaults.
    // For device-credential mode, biometric is always active (it's the unlock method).
    var biometricUnlockEnabled by remember { mutableStateOf(prefs.biometricUnlockEnabled) }
    val vaultIsPassphraseMode =
        state.vaultState != VaultState.UNINITIALIZED &&
            state.vaultMode == BiometricVault.VaultMode.PASSPHRASE
    val vaultIsDeviceCredentialMode =
        state.vaultState != VaultState.UNINITIALIZED &&
            state.vaultMode == BiometricVault.VaultMode.DEVICE_CREDENTIAL

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Appearance ──
            CollapsibleSection(
                title = stringResource(R.string.settings_theme),
                icon = Icons.Default.Palette,
                defaultExpanded = true,
            ) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "dark" to R.string.theme_dark,
                        "light" to R.string.theme_light,
                        "system" to R.string.theme_system,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = themeMode == key,
                            onClick = {
                                themeMode = key
                                vm.setThemeMode(key)
                            },
                            label = { Text(stringResource(label)) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.settings_sort), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "name" to R.string.sort_name,
                        "issuer" to R.string.sort_issuer,
                        "date" to R.string.sort_date,
                        "favorites" to R.string.sort_favorites,
                        "manual" to R.string.sort_manual,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = sortMode == key,
                            onClick = {
                                sortMode = key
                                prefs.sortMode = key
                            },
                            label = { Text(stringResource(label)) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // View mode selector
                Text(stringResource(R.string.settings_view_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "list" to R.string.view_mode_list,
                        "tiles" to R.string.view_mode_tiles,
                        "compact" to R.string.view_mode_compact,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = viewMode == key,
                            onClick = {
                                viewMode = key
                                prefs.viewMode = key
                            },
                            label = { Text(stringResource(label)) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Animation de démarrage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_splash_anim), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_splash_anim_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = splashAnimEnabled,
                        onCheckedChange = {
                            splashAnimEnabled = it
                            prefs.splashAnimationEnabled = it
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Language selector
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "system" to R.string.language_system,
                        "en" to R.string.language_english,
                        "fr" to R.string.language_french,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = language == key,
                            onClick = {
                                language = key
                                prefs.language = key
                                // Apply the new language immediately
                                val locales = when (key) {
                                    "en" -> androidx.core.os.LocaleListCompat.forLanguageTags("en")
                                    "fr" -> androidx.core.os.LocaleListCompat.forLanguageTags("fr")
                                    else -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                }
                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                            },
                            label = { Text(stringResource(label)) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Dynamic color (Material You)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dynamicColorEnabled,
                        onCheckedChange = {
                            dynamicColorEnabled = it
                            prefs.dynamicColorEnabled = it
                            vm.setDynamicColorEnabled(it)
                        },
                    )
                }
            }

            HorizontalDivider()

            // ── Security ──
            CollapsibleSection(
                title = stringResource(R.string.settings_security),
                icon = Icons.Default.Security,
                defaultExpanded = true,
            ) {
                // Auto lock
                Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_auto_lock_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = autoLockTimeout,
                    onValueChange = {
                        autoLockTimeout = it.filter { c -> c.isDigit() }
                        val timeout = autoLockTimeout.toIntOrNull() ?: 0
                        prefs.autoLockTimeout = timeout
                    },
                    label = { Text("0 / 30 / 60 / 120…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                // Biometric unlock toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_biometric_unlock),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(R.string.settings_biometric_unlock_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            vaultIsDeviceCredentialMode -> {
                                Text(
                                    stringResource(R.string.settings_biometric_unlock_device_mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            !vaultIsPassphraseMode -> {
                                Text(
                                    stringResource(R.string.settings_biometric_unlock_requires_passphrase),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            else -> {}
                        }
                    }
                    Switch(
                        checked = if (vaultIsDeviceCredentialMode) true else biometricUnlockEnabled,
                        enabled = vaultIsPassphraseMode,
                        onCheckedChange = {
                            biometricUnlockEnabled = it
                            prefs.biometricUnlockEnabled = it
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Change passphrase
                Text(stringResource(R.string.settings_change_pass), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text(stringResource(R.string.settings_current_pass)) },
                    visualTransformation = if (showOldPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOldPass = !showOldPass }) {
                            Icon(
                                if (showOldPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showOldPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text(stringResource(R.string.settings_new_pass)) },
                    visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPass = !showNewPass }) {
                            Icon(
                                if (showNewPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showNewPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(
                    onClick = {
                        vm.changePassphrase(oldPass, newPass)
                        oldPass = ""
                        newPass = ""
                    },
                    enabled = oldPass.isNotBlank() && newPass.length >= 8,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_change_button)) }

                Spacer(Modifier.height(16.dp))

                // Allow screenshots toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_allow_screenshots),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_not_recommended),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            stringResource(R.string.settings_allow_screenshots_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowScreenshots,
                        onCheckedChange = {
                            allowScreenshots = it
                            prefs.allowScreenshots = it
                            // Apply immediately
                            if (it) {
                                (context as? android.app.Activity)?.window?.clearFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                            } else {
                                (context as? android.app.Activity)?.window?.setFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Tap to reveal toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_tap_to_reveal),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_tap_to_reveal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = tapToReveal,
                        onCheckedChange = {
                            tapToReveal = it
                            prefs.tapToReveal = it
                        },
                    )
                }
            }

            HorizontalDivider()

            // ── Backup ──
            CollapsibleSection(
                title = stringResource(R.string.settings_auto_backup),
                icon = Icons.Default.Backup,
                defaultExpanded = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_auto_backup), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_auto_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = {
                            autoBackupEnabled = it
                            prefs.autoBackupEnabled = it
                            if (it && autoBackupPass.length >= 8) {
                                prefs.autoBackupPassphrase = autoBackupPass
                            }
                        },
                    )
                }
                if (autoBackupEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoBackupPass,
                        onValueChange = {
                            autoBackupPass = it
                            if (it.length >= 8) prefs.autoBackupPassphrase = it
                        },
                        label = { Text(stringResource(R.string.settings_auto_backup_pass)) },
                        visualTransformation = if (showAutoBackupPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showAutoBackupPass = !showAutoBackupPass }) {
                                Icon(
                                    if (showAutoBackupPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showAutoBackupPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider()

            // ── Cloud ──
            CollapsibleSection(
                title = stringResource(R.string.cloud_backup_title),
                icon = Icons.Default.CloudUpload,
                defaultExpanded = false,
            ) {
                Text(
                    stringResource(R.string.cloud_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (prefs.cloudBackupEnabled) {
                    // Active — show provider + controls
                    Text(
                        "${stringResource(R.string.cloud_backup_active)} (${prefs.cloudProvider})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.disableCloudBackup(); cloudPass = "" }) {
                            Text(stringResource(R.string.cloud_backup_disable))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.cloud_sync_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = prefs.cloudSyncEnabled,
                            onCheckedChange = { vm.setCloudSyncEnabled(it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.backupNow() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.cloud_backup_now)) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.syncFromCloud() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.cloud_sync_now)) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.cloud_restore)) }
                } else {
                    // Provider selection — dropdown
                    Text(stringResource(R.string.cloud_provider_select), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    var providerMenuExpanded by remember { mutableStateOf(false) }
                    val providerOptions = listOf(
                        "dropbox" to R.string.cloud_dropbox,
                        "gdrive" to R.string.cloud_gdrive,
                        "webdav" to R.string.cloud_webdav,
                        "sftp" to R.string.cloud_sftp,
                    )
                    val selectedLabel = providerOptions.find { it.first == selectedProvider }?.second ?: R.string.cloud_dropbox

                    Box {
                        OutlinedButton(
                            onClick = { providerMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(selectedLabel))
                            Spacer(Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false },
                        ) {
                            providerOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        selectedProvider = key
                                        providerMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Provider-specific credentials
                    when (selectedProvider) {
                        "dropbox" -> {
                            Spacer(Modifier.height(8.dp))
                            if (dropboxAuthInProgress) {
                                Text(
                                    stringResource(R.string.cloud_dropbox_auth_pending),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        "gdrive" -> {
                            Spacer(Modifier.height(8.dp))
                            if (gdriveAuthInProgress) {
                                Text(
                                    stringResource(R.string.cloud_gdrive_auth_pending),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        "webdav" -> {
                            OutlinedTextField(
                                value = webdavUrl,
                                onValueChange = { webdavUrl = it },
                                label = { Text(stringResource(R.string.cloud_webdav_url)) },
                                placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = webdavUser,
                                onValueChange = { webdavUser = it },
                                label = { Text(stringResource(R.string.cloud_webdav_user)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = webdavPass,
                                onValueChange = { webdavPass = it },
                                label = { Text(stringResource(R.string.cloud_webdav_pass)) },
                                visualTransformation = if (showWebdavPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showWebdavPass = !showWebdavPass }) {
                                        Icon(
                                            if (showWebdavPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (showWebdavPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.cloud_webdav_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        "sftp" -> {
                            OutlinedTextField(
                                value = sftpHost,
                                onValueChange = { sftpHost = it },
                                label = { Text(stringResource(R.string.cloud_sftp_host)) },
                                placeholder = { Text("ssh.example.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sftpUser,
                                    onValueChange = { sftpUser = it },
                                    label = { Text(stringResource(R.string.cloud_sftp_user)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = sftpPort,
                                    onValueChange = { sftpPort = it.filter { c -> c.isDigit() } },
                                    label = { Text(stringResource(R.string.cloud_sftp_port)) },
                                    singleLine = true,
                                    modifier = Modifier.width(100.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sftpPass,
                                onValueChange = { sftpPass = it },
                                label = { Text(stringResource(R.string.cloud_sftp_pass)) },
                                visualTransformation = if (showSftpPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showSftpPass = !showSftpPass }) {
                                        Icon(
                                            if (showSftpPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (showSftpPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sftpPath,
                                onValueChange = { sftpPath = it },
                                label = { Text(stringResource(R.string.cloud_sftp_path)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cloudPass,
                        onValueChange = { cloudPass = it },
                        label = { Text(stringResource(R.string.cloud_backup_pass)) },
                        visualTransformation = if (showCloudPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCloudPass = !showCloudPass }) {
                                Icon(
                                    if (showCloudPass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCloudPass) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password),
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                    Button(
                        onClick = {
                            when (selectedProvider) {
                                "dropbox" -> {
                                    if (cloudPass.length >= 8) {
                                        vm.setupDropbox(cloudPass)
                                        dropboxAuthInProgress = true
                                        vm.startDropboxOAuth(context as android.app.Activity) { ok ->
                                            dropboxAuthInProgress = false
                                            if (!ok) vm.setError("Dropbox OAuth failed")
                                        }
                                    }
                                }
                                "gdrive" -> {
                                    if (cloudPass.length >= 8) {
                                        vm.setupGoogleDrive(cloudPass)
                                        gdriveAuthInProgress = true
                                        vm.startGoogleOAuth(context as android.app.Activity) { ok ->
                                            gdriveAuthInProgress = false
                                            if (!ok) vm.setError("Google OAuth failed")
                                        }
                                    }
                                }
                                "webdav" -> {
                                    if (webdavUrl.isNotBlank() && webdavUser.isNotBlank() && webdavPass.isNotBlank() && cloudPass.length >= 8) {
                                        val ok = vm.setupWebDav(webdavUrl, webdavUser, webdavPass, cloudPass)
                                        if (!ok) vm.setError("WebDAV authentication failed")
                                    }
                                }
                                "sftp" -> {
                                    if (sftpHost.isNotBlank() && sftpUser.isNotBlank() && sftpPass.isNotBlank() && cloudPass.length >= 8) {
                                        val port = sftpPort.toIntOrNull() ?: 22
                                        val ok = vm.setupSftp(sftpHost, port, sftpUser, sftpPass, sftpPath, cloudPass)
                                        if (!ok) vm.setError("SFTP authentication failed")
                                    }
                                }
                            }
                        },
                        enabled = cloudPass.length >= 8 && when (selectedProvider) {
                            "dropbox" -> true
                            "gdrive" -> true
                            "webdav" -> webdavUrl.isNotBlank() && webdavUser.isNotBlank() && webdavPass.isNotBlank()
                            "sftp" -> sftpHost.isNotBlank() && sftpUser.isNotBlank() && sftpPass.isNotBlank()
                            else -> false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.cloud_backup_connect)) }
                }
            }

            HorizontalDivider()

            // ── Categories ──
            CollapsibleSection(
                title = stringResource(R.string.settings_categories),
                icon = Icons.Default.Category,
                defaultExpanded = false,
            ) {
                Text(
                    stringResource(R.string.settings_categories_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))

                state.categories.forEach { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editingCategoryId == cat.id) {
                            OutlinedTextField(
                                value = editingCategoryName,
                                onValueChange = { editingCategoryName = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                vm.renameCategory(cat.id, editingCategoryName)
                                editingCategoryId = null
                            }) { Icon(Icons.Default.Edit, contentDescription = null) }
                            IconButton(onClick = { editingCategoryId = null }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_cancel))
                            }
                        } else {
                            Text(cat.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = {
                                editingCategoryId = cat.id
                                editingCategoryName = cat.name
                            }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_rename)) }
                            IconButton(onClick = { vm.deleteCategory(cat.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_delete))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text(stringResource(R.string.settings_new_category)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        vm.addCategory(newCategoryName)
                        newCategoryName = ""
                    }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_category)) }
                }
            }

            HorizontalDivider()

            // ── Widget ──
            CollapsibleSection(
                title = stringResource(R.string.settings_widget),
                icon = Icons.Default.Widgets,
                defaultExpanded = false,
            ) {
                // Show codes widget toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_widget_show), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_widget_show_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = widgetEnabled,
                        onCheckedChange = {
                            widgetEnabled = it
                            prefs.widgetCodesEnabled = it
                        },
                    )
                }

                if (widgetEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_widget_require_unlock),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = widgetRequireUnlock,
                            onCheckedChange = {
                                widgetRequireUnlock = it
                                prefs.widgetRequireUnlock = it
                            },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Widget mode selector
                Text(stringResource(R.string.settings_widget_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "favorites" to R.string.widget_mode_favorites,
                        "category" to R.string.widget_mode_category,
                        "all" to R.string.widget_mode_all,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = widgetMode == key,
                            onClick = {
                                widgetMode = key
                                prefs.widgetMode = key
                            },
                            label = { Text(stringResource(label)) },
                            colors = cernunnosChipColors(),
                        )
                    }
                }

                // Category selector (only if mode = category)
                if (widgetMode == "category") {
                    Spacer(Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = widgetCategoryDropdownExpanded,
                        onExpandedChange = { widgetCategoryDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.categories.find { it.id == widgetCategory }?.name
                                ?: stringResource(R.string.add_no_category),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_widget_category)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = widgetCategoryDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = widgetCategoryDropdownExpanded,
                            onDismissRequest = { widgetCategoryDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_no_category)) },
                                onClick = {
                                    widgetCategory = null
                                    prefs.widgetCategory = null
                                    widgetCategoryDropdownExpanded = false
                                },
                            )
                            state.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        widgetCategory = cat.id
                                        prefs.widgetCategory = cat.id
                                        widgetCategoryDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Max entries slider
                Text(
                    stringResource(R.string.settings_widget_max_entries, widgetMaxEntries.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = widgetMaxEntries,
                    onValueChange = { widgetMaxEntries = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChangeFinished = {
                        prefs.widgetMaxEntries = widgetMaxEntries.toInt()
                    },
                )
            }

            HorizontalDivider()

            // ── Import/Export ──
            CollapsibleSection(
                title = stringResource(R.string.settings_data),
                icon = Icons.Default.ImportExport,
                defaultExpanded = false,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Text("  ${stringResource(R.string.settings_export)}")
                    }
                    Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text("  ${stringResource(R.string.settings_import)}")
                    }
                }
            }

            HorizontalDivider()

            // ── P2P ──
            CollapsibleSection(
                title = stringResource(R.string.p2p_title),
                icon = Icons.Default.Wifi,
                defaultExpanded = false,
            ) {
                Text(
                    stringResource(R.string.p2p_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onP2P, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.p2p_open))
                }
            }

            HorizontalDivider()

            // ── Open .cern encrypted file ──
            CollapsibleSection(
                title = stringResource(R.string.settings_open_cern),
                icon = Icons.Default.LockOpen,
                defaultExpanded = false,
            ) {
                Text(
                    stringResource(R.string.settings_open_cern_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { cernFilePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open_cern_button))
                }
            }

            HorizontalDivider()

            // ── Crash reports ──
            val context = LocalContext.current
            val hasCrashes = remember { com.cernunnos.authenticator.crash.CrashReporter.hasPendingCrashes(context) }
            if (hasCrashes) {
                CollapsibleSection(
                    title = stringResource(R.string.settings_crash_reports),
                    icon = Icons.Default.BugReport,
                    defaultExpanded = false,
                ) {
                    Text(
                        stringResource(R.string.settings_crash_reports_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val crashLogs = com.cernunnos.authenticator.crash.CrashReporter.getCombinedCrashLogs(context)
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Cernunnos Diwaller crash report")
                                    putExtra(android.content.Intent.EXTRA_TEXT, crashLogs)
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, context.getString(R.string.crash_share_chooser)))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.crash_share))
                        }
                        OutlinedButton(
                            onClick = {
                                com.cernunnos.authenticator.crash.CrashReporter.clearCrashLogs(context)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.crash_delete))
                        }
                    }
                }
                HorizontalDivider()
            }

            // ── About ──
            CollapsibleSection(
                title = stringResource(R.string.settings_security_status),
                icon = Icons.Default.Info,
                defaultExpanded = false,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.settings_security_status),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (allowScreenshots) stringResource(R.string.settings_flag_secure_off) else stringResource(R.string.settings_flag_secure),
                            color = if (allowScreenshots) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Text(stringResource(R.string.settings_storage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(stringResource(R.string.settings_network), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        if (accessibility.enabled) {
                            Text(
                                stringResource(R.string.settings_accessibility_warn, accessibility.services.size),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                            )
                            Text(
                                stringResource(R.string.settings_accessibility_desc),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        } else {
                            Text(stringResource(R.string.settings_accessibility_none), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Cloud restore confirmation dialog
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.confirm_restore_title)) },
            text = { Text(stringResource(R.string.confirm_restore_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreFromCloud(cloudPass.ifBlank { prefs.cloudBackupPassphrase ?: "" })
                    showRestoreConfirm = false
                }) { Text(stringResource(R.string.confirm_restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
