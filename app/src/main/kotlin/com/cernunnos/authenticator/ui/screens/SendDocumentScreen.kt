package com.cernunnos.authenticator.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.util.SecureDocumentBuilder
import com.cernunnos.authenticator.util.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendDocumentScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var files by remember { mutableStateOf(listOf<SelectedFile>()) }
    var password by remember { mutableStateOf("") }
    var useCernFormat by remember { mutableStateOf(false) }
    var passwordChannel by remember { mutableStateOf("sms") }
    var documentChannel by remember { mutableStateOf("email") }
    var isBuilding by remember { mutableStateOf(false) }
    var builtFile by remember { mutableStateOf<File?>(null) }
    var builtMimeType by remember { mutableStateOf("application/pdf") }
    // Sequential send steps: 0=configure, 1=document ready to send, 2=password ready to send, 3=done
    var sendStep by remember { mutableStateOf(0) }
    var contactPhone by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }
    var manualPhone by rememberSaveable { mutableStateOf("") }
    var showEmailWarning by remember { mutableStateOf(false) }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = UriUtils.getFileName(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            files = files + SelectedFile(uri, name, mime)
        }
    }

    // Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Save bitmap to temp file and add to list
            scope.launch {
                val tempFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    val fos = java.io.FileOutputStream(tempFile)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.close()
                }
                files = files + SelectedFile(
                    Uri.fromFile(tempFile),
                    tempFile.name,
                    "image/jpeg",
                )
            }
        }
    }

    // Contact picker
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIdx >= 0) contactName = it.getString(nameIdx)

                            val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                            if (idIdx >= 0) {
                                val contactId = it.getString(idIdx)
                                val phones = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    null,
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(contactId),
                                    null,
                                )
                                phones?.use { p ->
                                    if (p.moveToFirst()) {
                                        val phoneIdx = p.getColumnIndex(
                                            ContactsContract.CommonDataKinds.Phone.NUMBER
                                        )
                                        if (phoneIdx >= 0) {
                                            contactPhone = p.getString(phoneIdx)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.send_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            // Description
            Text(
                stringResource(R.string.send_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // File selection
            Text(
                stringResource(R.string.send_supported_formats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        filePicker.launch(arrayOf(
                            "application/pdf",
                            "image/jpeg",
                            "image/png",
                            "image/webp",
                            "text/markdown",
                            "text/csv",
                            "text/plain",
                        ))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.send_add_file))
                }
                OutlinedButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.send_take_photo))
                }
            }

            // File list
            if (files.isEmpty()) {
                Text(
                    stringResource(R.string.send_no_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Text(
                    stringResource(R.string.send_files_added, files.size),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                files.forEachIndexed { idx, file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { files = files.filterIndexed { i, _ -> i != idx } },
                        ) {
                            Text(stringResource(R.string.send_remove_file))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Format selection
            Text(
                stringResource(R.string.send_format),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !useCernFormat,
                    onClick = { useCernFormat = false },
                    label = { Text(stringResource(R.string.send_format_pdf)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = useCernFormat,
                    onClick = { useCernFormat = true },
                    label = { Text(stringResource(R.string.send_format_cern)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (useCernFormat) stringResource(R.string.send_format_cern_desc)
                else stringResource(R.string.send_format_pdf_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Password
            Text(
                stringResource(R.string.send_password),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.send_password_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = password.isNotEmpty() && password.length < 8,
                    supportingText = {
                        if (password.isNotEmpty() && password.length < 8) {
                            Text(stringResource(R.string.send_password_too_short))
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        password = SecureDocumentBuilder.generatePassword()
                    },
                ) {
                    Text(stringResource(R.string.send_generate_password))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Password channel
            Text(
                stringResource(R.string.send_password_channel),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // SMS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = passwordChannel == "sms",
                        onClick = { passwordChannel = "sms" },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.send_channel_sms), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.send_channel_sms_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Copy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = passwordChannel == "copy",
                        onClick = { passwordChannel = "copy" },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.send_channel_copy), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Email (discouraged)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = passwordChannel == "email",
                        onClick = {
                            passwordChannel = "email"
                            showEmailWarning = true
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.send_channel_email),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.send_channel_email_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showEmailWarning && passwordChannel == "email") {
                    Text(
                        stringResource(R.string.send_channel_email_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 36.dp),
                    )
                }
            }

            // Contact picker or manual number entry for SMS
            if (passwordChannel == "sms") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { contactPicker.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(contactName?.let { "$it" }
                            ?: stringResource(R.string.send_pick_contact))
                    }
                }
                if (contactName != null) {
                    Text(
                        "Contact: $contactName — $contactPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Manual phone number entry
                Text(
                    stringResource(R.string.send_phone_or_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val isPhoneInvalid = manualPhone.isNotBlank() &&
                    (manualPhone.count { it.isDigit() } < 7 ||
                        !manualPhone.matches(Regex("^[+\\d\\s\\-\\(\\).]*$")))
                OutlinedTextField(
                    value = manualPhone,
                    onValueChange = { manualPhone = it.filter { c -> c.isDigit() || c in " +().-" } },
                    label = { Text(stringResource(R.string.send_phone_number)) },
                    placeholder = { Text(stringResource(R.string.send_phone_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isPhoneInvalid,
                    supportingText = if (isPhoneInvalid) {
                        { Text(stringResource(R.string.send_phone_invalid), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Document channel
            Text(
                stringResource(R.string.send_send_via),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = documentChannel == "email",
                    onClick = { documentChannel = "email" },
                    label = { Text(stringResource(R.string.send_send_email)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = documentChannel == "sms",
                    onClick = { documentChannel = "sms" },
                    label = { Text(stringResource(R.string.send_send_sms)) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Channel confirmation
            if (passwordChannel != "copy" && documentChannel != "copy") {
                if (passwordChannel != documentChannel) {
                    Text(
                        stringResource(R.string.send_confirm_channels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.send_confirm_same_channel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Sequential send flow ──

            when (sendStep) {
                0 -> {
                    // Step 1: Build the encrypted document
                    Button(
                        onClick = {
                            if (files.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.send_no_files_error))
                                }
                                return@Button
                            }
                            if (password.length < 8) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.send_password_too_short))
                                }
                                return@Button
                            }

                            isBuilding = true
                            scope.launch {
                                try {
                                    val outputDir = File(context.cacheDir, "secure_send")
                                    val result = withContext(Dispatchers.IO) {
                                        if (useCernFormat) {
                                            SecureDocumentBuilder.buildEncryptedCern(
                                                context,
                                                files.map { it.uri },
                                                password,
                                                outputDir,
                                            )
                                        } else {
                                            SecureDocumentBuilder.buildEncryptedPdf(
                                                context,
                                                files.map { it.uri },
                                                password,
                                                outputDir,
                                            )
                                        }
                                    }
                                    builtFile = result.file
                                    builtMimeType = result.mimeType
                                    isBuilding = false
                                    sendStep = 1
                                } catch (e: Exception) {
                                    isBuilding = false
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.send_error, e.message ?: "Unknown error")
                                    )
                                }
                            }
                        },
                        enabled = !isBuilding && files.isNotEmpty() && password.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.send_building))
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.send_build))
                        }
                    }
                }

                1 -> {
                    // Step 2: Send the document
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.send_success),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                stringResource(R.string.send_file_label, builtFile?.name ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val channelLabel = if (documentChannel == "email")
                                stringResource(R.string.send_channel_email_label)
                            else
                                stringResource(R.string.send_channel_sms_label)
                            Text(
                                stringResource(R.string.send_channel_label, channelLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val file = builtFile ?: return@Button
                            if (documentChannel == "email") {
                                sendDocument(context, file, builtMimeType)
                            } else {
                                val smsTarget = contactPhone ?: manualPhone.ifBlank { null }
                                sendDocumentViaSms(context, file, builtMimeType, smsTarget)
                            }
                            sendStep = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_send_document))
                    }

                    TextButton(
                        onClick = { sendStep = 0 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.send_start_over))
                    }
                }

                2 -> {
                    // Step 3: Send the password
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.send_doc_sent),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                stringResource(R.string.send_password_next_step),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            if (passwordChannel != documentChannel) {
                                Text(
                                    stringResource(R.string.send_confirm_channels),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.send_confirm_same_channel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val smsTarget = contactPhone ?: manualPhone.ifBlank { null }
                            when (passwordChannel) {
                                "sms" -> {
                                    if (smsTarget != null) {
                                        sendPasswordSms(context, smsTarget, password)
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.send_password_sent_sms)) }
                                    } else {
                                        copyPasswordWithClear(context, password, scope)
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.send_password_copied)) }
                                    }
                                }
                                "copy" -> {
                                    copyPasswordWithClear(context, password, scope)
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.send_password_copied)) }
                                }
                                "email" -> {
                                    sendPasswordEmail(context, password)
                                }
                            }
                            sendStep = 3
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_send_password))
                    }

                    TextButton(
                        onClick = { sendStep = 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.send_back))
                    }
                }

                3 -> {
                    // Step 4: Done
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                stringResource(R.string.send_both_sent),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.send_clipboard_clear_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Button(
                        onClick = { onBack() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_finish))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun sendDocument(
    context: android.content.Context,
    file: File,
    mimeType: String,
) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_chooser_document)))
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_no_email_app), android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_file_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun sendDocumentViaSms(
    context: android.content.Context,
    file: File,
    mimeType: String,
    contactPhone: String?,
) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (contactPhone != null) {
                putExtra("address", contactPhone)
            }
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_chooser_mms)))
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_no_mms_app), android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_file_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun sendPasswordSms(
    context: android.content.Context,
    phone: String,
    password: String,
) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("smsto:$phone")
            putExtra("sms_body", context.getString(R.string.send_sms_body, password))
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_chooser_password)))
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_no_sms_app), android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun sendPasswordEmail(
    context: android.content.Context,
    password: String,
) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.send_email_subject))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.send_email_body, password))
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_chooser_password)))
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.send_no_email_app), android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun copyPasswordWithClear(
    context: android.content.Context,
    password: String,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", password))
    // Auto-clear after 30 seconds
    scope.launch {
        kotlinx.coroutines.delay(30_000)
        if (clipboard.primaryClip?.getItemAt(0)?.text == password) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
    }
}
