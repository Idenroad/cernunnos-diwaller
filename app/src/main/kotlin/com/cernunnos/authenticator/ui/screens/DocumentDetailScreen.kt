package com.cernunnos.authenticator.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.ui.components.FullScreenImageViewer
import com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    vm: DocumentViewModel,
    documentId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entry = state.documents.find { it.id == documentId }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var versoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showingVerso by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showFullScreenViewer by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Recycle bitmaps when the composable leaves composition to avoid OOM
    DisposableEffect(Unit) {
        onDispose {
            imageBitmap?.recycle()
            versoBitmap?.recycle()
        }
    }

    // Load recto image when entry is found
    LaunchedEffect(entry?.id) {
        if (entry != null) {
            vm.getDocumentImage(entry) { bitmap ->
                imageBitmap = bitmap
            }
            if (entry.hasVerso) {
                vm.getDocumentVersoImage(entry) { bitmap ->
                    versoBitmap = bitmap
                }
            }
        }
    }

    if (entry == null) {
        // Document not found — go back
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.doc_share_encrypted))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.doc_delete_button), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Image — Recto or Verso (tap to open full-screen viewer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        val bmp = if (showingVerso) versoBitmap else imageBitmap
                        if (bmp != null) showFullScreenViewer = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                val displayedBitmap = if (showingVerso) versoBitmap else imageBitmap
                if (displayedBitmap != null) {
                    Image(
                        bitmap = displayedBitmap.asImageBitmap(),
                        contentDescription = if (showingVerso) "${entry.title} (verso)" else entry.title,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            // Recto/Verso toggle
            if (entry.hasVerso && versoBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showingVerso,
                        onClick = { showingVerso = false },
                        label = { Text(stringResource(R.string.doc_recto)) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = showingVerso,
                        onClick = { showingVerso = true },
                        label = { Text(stringResource(R.string.doc_verso)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Metadata
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(stringResource(R.string.doc_detail_type), documentTypeLabel(entry.type))
                    if (entry.expirationDate != null) {
                        val isExpired = entry.expirationDate < System.currentTimeMillis()
                        DetailRow(
                            stringResource(R.string.doc_detail_expiration),
                            dateFormat.format(Date(entry.expirationDate)),
                            valueColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DetailRow(stringResource(R.string.doc_detail_created), dateFormat.format(Date(entry.createdAt)))
                    if (entry.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.doc_detail_notes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Share button
            OutlinedButton(
                onClick = { showShareDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.doc_share_encrypted))
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.doc_delete_title)) },
            text = { Text(stringResource(R.string.doc_delete_msg, entry.title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDocument(entry.id)
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text(stringResource(R.string.doc_delete_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.doc_cancel)) }
            },
        )
    }

    // Share dialog — encrypted export
    if (showShareDialog) {
        ShareDocumentDialog(
            entry = entry,
            onShare = { passphrase ->
                vm.exportDocument(entry.id, passphrase) { data ->
                    if (data != null) {
                        // Save to cache and share via intent
                        scope.launch {
                            val sharedDir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                            val file = java.io.File(sharedDir, "cernunnos_doc_${entry.title}.enc")
                            file.writeBytes(data)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.document_share_subject, entry.title))
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    context.getString(R.string.document_share_body),
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_encrypted_document_chooser)))
                            } catch (e: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, context.getString(R.string.no_app_to_share), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            // Clean up the temp file after a delay to allow the share intent to read it
                            scope.launch {
                                kotlinx.coroutines.delay(60_000)
                                file.delete()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                showShareDialog = false
            },
            onDismiss = { showShareDialog = false },
        )
    }

    // Full-screen image viewer with zoom/pan
    if (showFullScreenViewer) {
        val bmp = if (showingVerso) versoBitmap else imageBitmap
        if (bmp != null) {
            FullScreenImageViewer(
                bitmap = bmp,
                onDismiss = { showFullScreenViewer = false },
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShareDocumentDialog(
    entry: DocumentEntry,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = passphrase != confirm && confirm.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_share_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.doc_share_desc, entry.title),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.doc_share_passphrase)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.doc_share_confirm)) },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShare(passphrase) },
                enabled = passphrase.length >= 6 && passphrase == confirm,
            ) { Text(stringResource(R.string.doc_share_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.doc_cancel)) }
        },
    )
}
