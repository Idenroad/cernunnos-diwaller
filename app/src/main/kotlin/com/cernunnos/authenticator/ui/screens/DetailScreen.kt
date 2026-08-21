package com.cernunnos.authenticator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.totp.TotpGenerator
import com.cernunnos.authenticator.ui.components.TotpCanvasView
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.util.ExportImport
import com.cernunnos.authenticator.util.OtpAuthParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: AppViewModel,
    entryId: String,
    onBack: () -> Unit,
    onDeleteWithUndo: (com.cernunnos.authenticator.data.model.TotpEntry) -> Unit,
) {
    val state by vm.uiState.collectAsState()
    val entry = state.entries.find { it.id == entryId }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Edit mode state
    var editMode by remember { mutableStateOf(false) }
    var editIssuer by remember { mutableStateOf("") }
    var editLabel by remember { mutableStateOf("") }
    var editSecret by remember { mutableStateOf("") }
    var editAlgorithm by remember { mutableStateOf("SHA1") }
    var editDigits by remember { mutableStateOf("6") }
    var editPeriod by remember { mutableStateOf("30") }
    var editType by remember { mutableStateOf("totp") }
    var editCounter by remember { mutableStateOf("0") }
    var editError by remember { mutableStateOf<String?>(null) }
    var algorithmDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var editIconName by remember { mutableStateOf<String?>(null) }
    var editCustomIconUri by remember { mutableStateOf<String?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Custom icon image picker (from gallery)
    // Copies the image into the app's internal storage so it survives URI permission revocation.
    val customIconPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Copy image to internal storage — no persistable URI permission needed
            try {
                val iconsDir = java.io.File(context.filesDir, "custom_icons").also { it.mkdirs() }
                val iconFile = java.io.File(iconsDir, "icon_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    iconFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (iconFile.exists() && iconFile.length() > 0) {
                    editCustomIconUri = iconFile.absolutePath
                    editIconName = null
                    hasUnsavedChanges = true
                }
            } catch (e: Exception) {
                android.util.Log.w("DetailScreen", "Failed to copy custom icon: ${e.message}")
            }
        }
    }
    val clipboardScope = androidx.compose.runtime.rememberCoroutineScope()
    val strErrIssuerEmpty = stringResource(R.string.error_issuer_empty)
    val strErrDigits = stringResource(R.string.error_digits)
    val strErrPeriod = stringResource(R.string.error_period)
    val strErrSecret = stringResource(R.string.error_secret_invalid)
    val strErrCounter = stringResource(R.string.error_counter_invalid)

    // ── Usage stats ──
    var viewCount by remember { mutableStateOf(0) }
    var lastViewed by remember { mutableStateOf(0L) }
    LaunchedEffect(entryId) {
        vm.incrementEntryViewCount(entryId)
        viewCount = vm.getEntryViewCount(entryId)
        lastViewed = vm.getEntryLastViewed(entryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.issuer ?: "Detail") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editMode && hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else if (editMode) {
                            editMode = false
                            editError = null
                            hasUnsavedChanges = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editMode) {
                        IconButton(onClick = {
                            // Save
                            val issuerTrim = editIssuer.trim()
                            if (issuerTrim.isEmpty()) {
                                editError = strErrIssuerEmpty
                                return@IconButton
                            }
                            val digitsVal = editDigits.trim().toIntOrNull()
                            if (digitsVal == null || (digitsVal != 6 && digitsVal != 8)) {
                                editError = strErrDigits
                                return@IconButton
                            }
                            val periodVal = editPeriod.trim().toIntOrNull()
                            if (periodVal == null || periodVal <= 0) {
                                editError = strErrPeriod
                                return@IconButton
                            }
                            val secretBytes = try {
                                OtpAuthParser.decodeBase32(editSecret.trim())
                            } catch (e: Exception) {
                                editError = strErrSecret
                                return@IconButton
                            }
                            val counterVal = if (editType == "hotp") {
                                editCounter.trim().toLongOrNull() ?: run {
                                    editError = strErrCounter
                                    return@IconButton
                                }
                            } else {
                                0L
                            }
                            vm.updateEntryFields(
                                id = entryId,
                                issuer = issuerTrim,
                                label = editLabel.trim(),
                                secret = secretBytes,
                                algorithm = editAlgorithm,
                                digits = digitsVal,
                                period = periodVal,
                                iconName = editIconName,
                                customIconUri = editCustomIconUri,
                                type = editType,
                                counter = counterVal,
                            )
                            editMode = false
                            editError = null
                            hasUnsavedChanges = false
                        }) {
                            Text(stringResource(R.string.save))
                        }
                        IconButton(onClick = {
                            if (hasUnsavedChanges) {
                                showDiscardDialog = true
                            } else {
                                editMode = false
                                editError = null
                            }
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        IconButton(onClick = { vm.toggleFavorite(entryId) }) {
                            Icon(
                                if (entry?.favorite == true) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = stringResource(R.string.favorite_add),
                                tint = if (entry?.favorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry != null) {
                            IconButton(onClick = {
                                editIssuer = entry.issuer
                                editLabel = entry.label
                                editSecret = OtpAuthParser.encodeBase32(entry.secret)
                                editAlgorithm = entry.algorithm
                                editDigits = entry.digits.toString()
                                editPeriod = entry.period.toString()
                                editType = entry.type
                                editCounter = entry.counter.toString()
                                editIconName = entry.iconName
                                editCustomIconUri = entry.customIconUri
                                editError = null
                                hasUnsavedChanges = false
                                editMode = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { showQrDialog = true }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Show QR")
                            }
                        }
                        IconButton(onClick = { showShareDialog = true }, enabled = entry != null) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_title))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.detail_not_found))
            }
            return@Scaffold
        }

        val code = com.cernunnos.authenticator.ui.screens.generateCodeForEntry(entry, System.currentTimeMillis() / 1000)
        val remaining = TotpGenerator.remainingSeconds(entry.period)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (editMode) {
                // ── Edit mode ──
                OutlinedTextField(
                    value = editIssuer,
                    onValueChange = { editIssuer = it; hasUnsavedChanges = true },
                    label = { Text(stringResource(R.string.field_issuer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it; hasUnsavedChanges = true },
                    label = { Text(stringResource(R.string.field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editSecret,
                    onValueChange = { editSecret = it.uppercase(); hasUnsavedChanges = true },
                    label = { Text(stringResource(R.string.field_secret_base32)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = algorithmDropdownExpanded,
                    onExpandedChange = { algorithmDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = editAlgorithm,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_algorithm)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = algorithmDropdownExpanded,
                        onDismissRequest = { algorithmDropdownExpanded = false },
                    ) {
                        listOf("SHA1", "SHA256", "SHA512").forEach { algo ->
                            DropdownMenuItem(
                                text = { Text(algo) },
                                onClick = {
                                    editAlgorithm = algo
                                    hasUnsavedChanges = true
                                    algorithmDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = editType.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.edit_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                    ) {
                        listOf("totp", "hotp").forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.uppercase()) },
                                onClick = {
                                    editType = t
                                    hasUnsavedChanges = true
                                    typeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                if (editType == "hotp") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editCounter,
                        onValueChange = { editCounter = it.filter { c -> c.isDigit() }; hasUnsavedChanges = true },
                        label = { Text(stringResource(R.string.edit_counter)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editDigits,
                    onValueChange = { editDigits = it.filter { c -> c.isDigit() }; hasUnsavedChanges = true },
                    label = { Text(stringResource(R.string.field_digits)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editPeriod,
                    onValueChange = { editPeriod = it.filter { c -> c.isDigit() }; hasUnsavedChanges = true },
                    label = { Text(stringResource(R.string.field_period)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                // Change icon section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.cernunnos.authenticator.ui.components.ServiceIcon(
                        name = editIssuer.ifBlank { editLabel },
                        size = 40.dp,
                        textSize = 16.sp,
                        iconName = editIconName,
                        customIconUri = editCustomIconUri,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.detail_icon),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            editIconName ?: stringResource(R.string.detail_icon_default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Buttons in a row that wraps on small screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.detail_change_icon))
                    }
                    OutlinedButton(
                        onClick = { customIconPicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.detail_custom_icon))
                    }
                }
                if (editIconName != null || editCustomIconUri != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            editIconName = null
                            editCustomIconUri = null
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier.align(Alignment.Start),
                    ) { Text(stringResource(R.string.detail_icon_reset), fontSize = 12.sp) }
                }
                editError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            } else {
                // ── View mode ──
                // Service icon
                com.cernunnos.authenticator.ui.components.ServiceIcon(
                    name = entry.issuer.ifEmpty { entry.label },
                    size = 64.dp,
                    textSize = 26.sp,
                    iconName = entry.iconName,
                    customIconUri = entry.customIconUri,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        entry.issuer,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { copyToClipboard(context, entry.issuer) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy issuer")
                    }
                }
                if (entry.label.isNotEmpty()) {
                    Text(entry.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TotpCanvasView(
                                code = code,
                                remainingSeconds = remaining,
                                period = entry.period,
                                textSize = 56.sp,
                            )
                            IconButton(onClick = { copyToClipboardWithClear(context, code, scope = clipboardScope) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                            }
                        }
                        if (entry.type == "hotp") {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { vm.incrementHotp(entryId) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.next_code))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Counter: #${entry.counter}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.detail_algorithm, entry.algorithm),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.detail_digits_period, entry.digits, entry.period),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Secret: ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        OtpAuthParser.encodeBase32(entry.secret),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Spacer(Modifier.height(24.dp))
                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.categories.find { it.id == entry.categoryId }?.name
                            ?: stringResource(R.string.add_no_category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.add_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_no_category)) },
                            onClick = {
                                vm.assignCategory(entryId, null)
                                categoryDropdownExpanded = false
                            },
                        )
                        state.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    vm.assignCategory(entryId, cat.id)
                                    categoryDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                // ── Usage stats ──
                val now = System.currentTimeMillis()
                val lastViewedText = if (lastViewed == 0L) {
                    stringResource(R.string.last_viewed_never)
                } else {
                    val diffMs = now - lastViewed
                    val diffMin = diffMs / 60000
                    when {
                        diffMin < 1 -> stringResource(R.string.last_viewed_just_now)
                        diffMin < 60 -> stringResource(R.string.last_viewed_minutes_ago, diffMin)
                        else -> {
                            val diffHours = diffMin / 60
                            if (diffHours < 24) stringResource(R.string.last_viewed_hours_ago, diffHours)
                            else stringResource(R.string.last_viewed_days_ago, diffHours / 24)
                        }
                    }
                }
                val frequency = when {
                    viewCount > 10 && (now - lastViewed) < 3_600_000L -> stringResource(R.string.usage_frequent)
                    viewCount > 5 && (now - lastViewed) < 86_400_000L -> stringResource(R.string.usage_regular)
                    viewCount > 0 -> stringResource(R.string.usage_occasional)
                    else -> stringResource(R.string.usage_never)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.usage_stats),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                lastViewedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Viewed $viewCount times",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                frequency,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (entry.type == "hotp") {
                                    "Current code: $code (counter #${entry.counter})"
                                } else {
                                    "Current code: $code (valid for $remaining more seconds)"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_msg, entry?.issuer ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    entry?.let { onDeleteWithUndo(it) }
                    onBack()
                }) { Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.detail_cancel)) }
            },
        )
    }

    // Discard changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    editMode = false
                    editError = null
                    hasUnsavedChanges = false
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }

    // Share dialog
    if (showShareDialog && entry != null) {
        ShareTotpDialog(
            entry = entry,
            onDismiss = { showShareDialog = false },
        )
    }

    // QR dialog
    if (showQrDialog && entry != null) {
        QrCodeDialog(
            entry = entry,
            onDismiss = { showQrDialog = false },
        )
    }

    // Icon picker dialog
    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = editIconName,
            onIconSelected = {
                editIconName = it
                hasUnsavedChanges = true
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Cernunnos", text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

/**
 * Copy a TOTP code to the clipboard and auto-clear it after 30 seconds
 * to prevent sensitive codes from lingering in the clipboard.
 *
 * Uses a coroutine scope to ensure the clear runs reliably,
 * unlike a raw Thread which can be killed by the OS.
 */
fun copyToClipboardWithClear(
    context: Context,
    text: String,
    label: String = "TOTP Code",
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    // Auto-clear after 30 seconds using a coroutine
    scope.launch {
        kotlinx.coroutines.delay(30_000)
        if (clipboard.primaryClip?.getItemAt(0)?.text == text) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrCodeDialog(
    entry: com.cernunnos.authenticator.data.model.TotpEntry,
    onDismiss: () -> Unit,
) {
    val uri = remember(entry) {
        val secret = OtpAuthParser.encodeBase32(entry.secret)
        val label = if (entry.issuer.isNotEmpty()) "${entry.issuer}:${entry.label}" else entry.label
        val type = entry.type
        val params = mutableListOf(
            "secret=$secret",
            "issuer=${entry.issuer}",
            "algorithm=${entry.algorithm}",
            "digits=${entry.digits}",
            "period=${entry.period}",
        )
        if (entry.type == "hotp") {
            params.add("counter=${entry.counter}")
        }
        "otpauth://$type/$label?${params.joinToString("&")}"
    }
    val bitmap = remember(uri) { generateQrCode(uri, 600) }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    entry.issuer,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(280.dp),
                    )
                } else {
                    Text(stringResource(R.string.qr_generate_failed), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

private fun generateQrCode(content: String, size: Int): android.graphics.Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareTotpDialog(
    entry: com.cernunnos.authenticator.data.model.TotpEntry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var recipientEmail by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column {
                // Security warning
                Text(
                    stringResource(R.string.share_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { recipientEmail = it },
                    label = { Text(stringResource(R.string.share_recipient)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.share_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.share_passphrase_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (passphrase.length < 8) {
                    error = context.getString(R.string.share_passphrase_too_short)
                    return@Button
                }
                try {
                    val encrypted = ExportImport.export(listOf(entry), passphrase)
                    val safeName = entry.issuer.lowercase().replace(Regex("[^a-z0-9]"), "_")
                    val fileName = "cernunnos_totp_${safeName}.txt"

                    // Write to cache dir for FileProvider
                    val sharedDir = java.io.File(context.cacheDir, "shared")
                    sharedDir.mkdirs()
                    val file = java.io.File(sharedDir, fileName)
                    file.writeText(encrypted)

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )

                    val body = context.getString(R.string.share_email_body, entry.issuer)
                    val subject = context.getString(R.string.share_email_subject, entry.issuer)

                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        if (recipientEmail.isNotBlank()) {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_chooser)))
                    // Clean up the temp file after a delay to allow the share intent to read it
                    scope.launch {
                        kotlinx.coroutines.delay(60_000)
                        file.delete()
                    }
                    onDismiss()
                } catch (e: Exception) {
                    error = e.message
                }
            }) { Text(stringResource(R.string.share_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPickerDialog(
    currentIcon: String?,
    onIconSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.detail_icon_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = com.cernunnos.authenticator.ui.components.IconRegistry.icons,
                        key = { it.first },
                    ) { (name, vector) ->
                        val isSelected = currentIcon == name
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { onIconSelected(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = name,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (currentIcon != null) {
                        TextButton(onClick = { onIconSelected(null) }) {
                            Text(stringResource(R.string.detail_icon_reset))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.detail_cancel))
                    }
                }
            }
        }
    }
}
