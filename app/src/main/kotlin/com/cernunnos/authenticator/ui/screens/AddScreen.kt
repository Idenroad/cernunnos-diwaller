package com.cernunnos.authenticator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.components.QrScannerScreen
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var showScanner by remember { mutableStateOf(false) }

    var galleryError by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        galleryError = null
        uri?.let {
            try {
                val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap != null) {
                    val intArray = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    try {
                        val result = MultiFormatReader().decode(binaryBitmap)
                        val text = result.text
                        if (text.startsWith("otpauth://")) {
                            vm.addEntryFromOtpAuth(text)
                            onDone()
                        } else {
                            galleryError = context.getString(R.string.add_gallery_no_qr)
                        }
                    } catch (e: Exception) {
                        galleryError = context.getString(R.string.add_gallery_no_qr)
                    }
                } else {
                    galleryError = context.getString(R.string.add_gallery_error)
                }
            } catch (e: Exception) {
                galleryError = context.getString(R.string.add_gallery_error)
            }
        }
    }

    LaunchedEffect(state.message) {
        if (state.message != null && state.error == null) {
            onDone()
        }
    }

    // If addEntryFromOtpAuth fails, close the scanner so the user sees the error.
    LaunchedEffect(state.error) {
        if (state.error != null && showScanner) {
            showScanner = false
        }
    }

    var showManual by remember { mutableStateOf(false) }
    var issuer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("6") }
    var period by remember { mutableStateOf("30") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var entryType by remember { mutableStateOf("totp") }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var counter by remember { mutableStateOf("0") }
    var motpPin by remember { mutableStateOf("") }

    // Validation error state
    var digitsError by remember { mutableStateOf<String?>(null) }
    var periodError by remember { mutableStateOf<String?>(null) }
    val digitsValue = digits.trim().toIntOrNull()
    val periodValue = period.trim().toIntOrNull()
    val hasDigitsError = digitsValue == null || (digitsValue != 6 && digitsValue != 8)
    val hasPeriodError = periodValue == null || periodValue <= 0
    val canAdd = secret.isNotBlank() && !hasDigitsError && !hasPeriodError

    // Pre-capture string resources for use in non-composable lambdas
    val digitsErrorMsg = stringResource(R.string.error_digits)
    val periodErrorMsg = stringResource(R.string.error_period)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!showManual) {
                Text(stringResource(R.string.add_scan_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            showScanner = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_scan)) }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_gallery))
                }

                galleryError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showManual = true }) { Text(stringResource(R.string.add_manual)) }
            } else {
                Text(stringResource(R.string.add_manual), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text(stringResource(R.string.add_issuer)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.add_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = if (entryType == "motp") it.lowercase() else it.uppercase() },
                    label = { Text(if (entryType == "motp") stringResource(R.string.add_secret_hex) else stringResource(R.string.add_secret)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = digits,
                        onValueChange = {
                            digits = it.filter { c -> c.isDigit() }
                            val v = digits.trim().toIntOrNull()
                            digitsError = if (v == null || (v != 6 && v != 8))
                                digitsErrorMsg else null
                        },
                        label = { Text(stringResource(R.string.add_digits)) },
                        singleLine = true,
                        isError = digitsError != null,
                        supportingText = {
                            digitsError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = period,
                        onValueChange = {
                            period = it.filter { c -> c.isDigit() }
                            val v = period.trim().toIntOrNull()
                            periodError = if (v == null || v <= 0)
                                periodErrorMsg else null
                        },
                        label = { Text(stringResource(R.string.add_period)) },
                        singleLine = true,
                        isError = periodError != null,
                        supportingText = {
                            periodError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Type selector (TOTP/HOTP)
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = entryType.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.add_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                    ) {
                        listOf("totp", "hotp", "steam", "yandex", "motp").forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.uppercase()) },
                                onClick = {
                                    entryType = t
                                    typeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                if (entryType == "hotp") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = counter,
                        onValueChange = { counter = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.add_counter)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (entryType == "motp") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = motpPin,
                        onValueChange = { motpPin = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.add_motp_pin)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.categories.find { it.id == selectedCategoryId }?.name
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
                                selectedCategoryId = null
                                categoryDropdownExpanded = false
                            },
                        )
                        state.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val dv = digits.trim().toIntOrNull()
                        val pv = period.trim().toIntOrNull()
                        // Validate before adding
                        if (dv == null || (dv != 6 && dv != 8)) {
                            digitsError = digitsErrorMsg
                            return@Button
                        }
                        if (pv == null || pv <= 0) {
                            periodError = periodErrorMsg
                            return@Button
                        }
                        vm.addEntryManual(
                            issuer = issuer,
                            label = label,
                            secretBase32 = secret,
                            digits = dv,
                            period = pv,
                            categoryId = selectedCategoryId,
                            type = entryType,
                            counter = counter.toLongOrNull() ?: 0L,
                            pin = if (entryType == "motp") motpPin else null,
                        )
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_button)) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showManual = false }) { Text(stringResource(R.string.add_back_scan)) }
            }
        }
    }

    // Full-screen QR scanner overlay (CameraX + ML Kit)
    if (showScanner) {
        QrScannerScreen(
            onResult = { value ->
                if (value.startsWith("otpauth://")) {
                    // Don't close the scanner here — let the LaunchedEffect(state.message)
                    // below handle navigation. This avoids a black-screen flash caused by
                    // removing the scanner overlay before the navigation transition starts.
                    vm.addEntryFromOtpAuth(value)
                } else {
                    // Not an otpauth URI — close scanner so user sees the AddScreen.
                    showScanner = false
                }
            },
            onDismiss = { showScanner = false },
        )
    }
}
