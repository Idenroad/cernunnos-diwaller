package com.cernunnos.authenticator.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.util.SecureDocumentBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecryptCernScreen(
    fileUri: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var password by remember { mutableStateOf("") }
    var isDecrypting by remember { mutableStateOf(false) }
    var decryptedDocs by remember { mutableStateOf<List<SecureDocumentBuilder.DecryptedDocument>>(emptyList()) }
    var hasDecrypted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.decrypt_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasDecrypted) {
                // Decrypt form
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text(
                    stringResource(R.string.decrypt_encrypted_file),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    stringResource(R.string.decrypt_enter_password),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.decrypt_password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = errorMessage != null,
                    supportingText = {
                        val msg = errorMessage
                        if (msg != null) {
                            Text(msg, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )

                Button(
                    onClick = {
                        if (password.isEmpty()) {
                            errorMessage = context.getString(R.string.decrypt_enter_password_error)
                            return@Button
                        }
                        isDecrypting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val content = withContext(Dispatchers.IO) {
                                    val uri = Uri.parse(fileUri)
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        // Limit read to 20MB to prevent OOM
                                        val maxBytes = 20 * 1024 * 1024
                                        val buffer = ByteArray(maxBytes + 1)
                                        val read = input.read(buffer)
                                        if (read > maxBytes) {
                                            error(context.getString(R.string.decrypt_file_too_large))
                                        }
                                        String(buffer, 0, read, Charsets.UTF_8)
                                    } ?: error(context.getString(R.string.decrypt_cannot_read))
                                }
                                val docs = withContext(Dispatchers.IO) {
                                    SecureDocumentBuilder.decryptCern(content, password)
                                }
                                decryptedDocs = docs
                                hasDecrypted = true
                                isDecrypting = false
                            } catch (e: Exception) {
                                isDecrypting = false
                                errorMessage = e.message ?: context.getString(R.string.decrypt_failed)
                            }
                        }
                    },
                    enabled = !isDecrypting && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isDecrypting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.decrypting))
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.decrypt_button))
                    }
                }
            } else {
                // Decrypted documents list
                Text(
                    stringResource(R.string.decrypt_success, decryptedDocs.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )

                decryptedDocs.forEachIndexed { idx, doc ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(doc.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                val outputDir = File(context.cacheDir, "shared")
                                                outputDir.mkdirs()
                                                // Sanitize filename to prevent path traversal
                                                val safeName = doc.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                                val outputFile = File(outputDir, "decrypted_$safeName")
                                                outputFile.writeBytes(doc.data)
                                            }
                                            val outputDir2 = File(context.cacheDir, "shared")
                                            val safeName2 = doc.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                            val outputFile = File(outputDir2, "decrypted_$safeName2")
                                            // Open with external app
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                outputFile,
                                            )
                                            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, doc.mimeType)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(viewIntent)
                                            } catch (e: android.content.ActivityNotFoundException) {
                                                snackbarHostState.showSnackbar(context.getString(R.string.decrypt_no_app, doc.fileName))
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.decrypt_error, e.message ?: ""))
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.cd_open))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        // Save all to the shared cache directory
                        scope.launch {
                            var saved = 0
                            withContext(Dispatchers.IO) {
                                val outputDir = File(context.cacheDir, "shared")
                                outputDir.mkdirs()
                                for (doc in decryptedDocs) {
                                    val safeName = doc.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                    val outputFile = File(outputDir, "decrypted_$safeName")
                                    outputFile.writeBytes(doc.data)
                                    saved++
                                }
                            }
                            snackbarHostState.showSnackbar(context.getString(R.string.decrypt_saved_cache, saved))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.decrypt_save_all))
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.decrypt_temp_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.decrypt_finish))
                }
            }
        }
    }
}
