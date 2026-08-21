package com.cernunnos.authenticator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.theme.cernunnosChipColors
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.util.AegisExporter
import com.cernunnos.authenticator.util.TwoFasExporter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    var passphrase by remember { mutableStateOf("") }
    var exportedData by remember { mutableStateOf<String?>(null) }
    var exportFormat by remember { mutableStateOf("cernunnos") } // "cernunnos", "aegis", or "2fas"
    // Pending plaintext export format awaiting user confirmation ("aegis" or "2fas").
    var pendingPlaintextExport by remember { mutableStateOf<String?>(null) }

    fun performPlaintextExport(format: String) {
        exportedData = when (format) {
            "aegis" -> AegisExporter.export(state.entries)
            else -> TwoFasExporter.export(state.entries)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Format selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = exportFormat == "cernunnos",
                    onClick = { exportFormat = "cernunnos"; exportedData = null },
                    label = { Text("Cernunnos") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = exportFormat == "aegis",
                    onClick = { exportFormat = "aegis"; exportedData = null },
                    label = { Text("Aegis") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = exportFormat == "2fas",
                    onClick = { exportFormat = "2fas"; exportedData = null },
                    label = { Text("2FAS") },
                    colors = cernunnosChipColors(),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (exportFormat == "cernunnos") {
                Text(
                    stringResource(R.string.export_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.export_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { exportedData = vm.exportEntries(passphrase) },
                    enabled = passphrase.length >= 8 && state.entries.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_button)) }
            } else if (exportFormat == "aegis") {
                // Aegis export (plain JSON, no encryption)
                Text(
                    stringResource(R.string.export_aegis_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.export_aegis_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        pendingPlaintextExport = "aegis"
                    },
                    enabled = state.entries.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_aegis_button)) }
            } else {
                // 2FAS export (plain JSON, no encryption)
                Text(
                    stringResource(R.string.export_2fas_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.export_2fas_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(16.dp))
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        pendingPlaintextExport = "2fas"
                    },
                    enabled = state.entries.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_2fas_button)) }
            }

            exportedData?.let { data ->
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.export_result), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                // QR Code (only for Cernunnos encrypted format, small enough)
                if (exportFormat == "cernunnos" && data.length < 2000) {
                    val qrBitmap = remember(data) {
                        generateQrCode(data, 512)
                    }
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_qr_code),
                            modifier = Modifier
                                .size(280.dp)
                                .padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.export_qr_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (exportFormat == "aegis" || exportFormat == "2fas") {
                    Text(
                        if (exportFormat == "aegis")
                            stringResource(R.string.export_aegis_copy_hint)
                        else
                            stringResource(R.string.export_2fas_copy_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    data,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }

        // Plaintext export confirmation dialog (Aegis / 2FAS).
        pendingPlaintextExport?.let { format ->
            AlertDialog(
                onDismissRequest = { pendingPlaintextExport = null },
                title = { Text(stringResource(R.string.export_plaintext_warning_title)) },
                text = { Text(stringResource(R.string.export_plaintext_warning_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            performPlaintextExport(format)
                            pendingPlaintextExport = null
                        },
                    ) {
                        Text(
                            stringResource(R.string.export_anyway),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPlaintextExport = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
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
