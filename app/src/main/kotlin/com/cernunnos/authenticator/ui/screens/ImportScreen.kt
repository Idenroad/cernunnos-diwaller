package com.cernunnos.authenticator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.theme.cernunnosChipColors
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024L // 10 MB

/**
 * Read a file from a content URI with a size limit to prevent OOM.
 * Returns null if the file is too large or cannot be read.
 */
private fun readImportFile(context: android.content.Context, uri: Uri): String? {
    return try {
        val resolver = context.contentResolver
        // Check file size via cursor
        val size = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else -1L
        } ?: -1L
        if (size > MAX_IMPORT_FILE_SIZE) {
            return null // File too large — caller shows error
        }
        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importTab by remember { mutableStateOf("cernunnos") } // cernunnos, bitwarden, google, aegis, 2fas, authy, microsoft, freeotp, andotp, raivo, lastpass, steam, plain
    var data by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var bitwardenData by remember { mutableStateOf("") }
    var googleData by remember { mutableStateOf("") }
    var aegisData by remember { mutableStateOf("") }
    var aegisPass by remember { mutableStateOf("") }
    var genericData by remember { mutableStateOf("") }
    var cernunnosFileName by remember { mutableStateOf<String?>(null) }
    var bitwardenFileName by remember { mutableStateOf<String?>(null) }
    var aegisFileName by remember { mutableStateOf<String?>(null) }
    var genericFileName by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // Validate file extension against allowed list. Returns true if valid.
    fun validateFileExtension(uri: Uri, allowedExts: Set<String>): Boolean {
        val name = uri.lastPathSegment ?: uri.toString()
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in allowedExts
    }

    val cernunnosFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (!validateFileExtension(uri, setOf("json", "csv", "txt", "xml"))) {
                importError = "Unsupported file format. Expected: .json, .csv, .txt or .xml"
                return@rememberLauncherForActivityResult
            }
            importError = null
            cernunnosFileName = uri.lastPathSegment ?: uri.toString()
            scope.launch {
                val content = withContext(Dispatchers.IO) { readImportFile(context, uri) }
                if (content != null) data = content
            }
        }
    }

    val bitwardenFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (!validateFileExtension(uri, setOf("json", "csv"))) {
                importError = "Unsupported file format. Expected: .json or .csv"
                return@rememberLauncherForActivityResult
            }
            importError = null
            bitwardenFileName = uri.lastPathSegment ?: uri.toString()
            scope.launch {
                val content = withContext(Dispatchers.IO) { readImportFile(context, uri) }
                if (content != null) bitwardenData = content
            }
        }
    }

    val aegisFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (!validateFileExtension(uri, setOf("json"))) {
                importError = "Unsupported file format. Expected: .json"
                return@rememberLauncherForActivityResult
            }
            importError = null
            aegisFileName = uri.lastPathSegment ?: uri.toString()
            scope.launch {
                val content = withContext(Dispatchers.IO) { readImportFile(context, uri) }
                if (content != null) aegisData = content
            }
        }
    }

    val genericFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Validate extension based on current tab
            val exts = when (importTab) {
                "freeotp" -> setOf("xml")
                "lastpass", "plain" -> setOf("txt", "csv")
                else -> setOf("json", "txt")
            }
            if (!validateFileExtension(uri, exts)) {
                importError = "Unsupported file format. Expected: .${exts.joinToString(", .")}"
                return@rememberLauncherForActivityResult
            }
            importError = null
            genericFileName = uri.lastPathSegment ?: uri.toString()
            scope.launch {
                val content = withContext(Dispatchers.IO) { readImportFile(context, uri) }
                if (content != null) genericData = content
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
            // Tab selector — FlowRow wraps chips on small screens
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = importTab == "cernunnos",
                    onClick = { importTab = "cernunnos" },
                    label = { Text("Cernunnos") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "bitwarden",
                    onClick = { importTab = "bitwarden" },
                    label = { Text("Bitwarden") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "google",
                    onClick = { importTab = "google" },
                    label = { Text("Google Auth") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "aegis",
                    onClick = { importTab = "aegis" },
                    label = { Text("Aegis") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "2fas",
                    onClick = { importTab = "2fas" },
                    label = { Text("2FAS") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "authy",
                    onClick = { importTab = "authy" },
                    label = { Text("Authy") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "microsoft",
                    onClick = { importTab = "microsoft" },
                    label = { Text("MS Auth") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "freeotp",
                    onClick = { importTab = "freeotp" },
                    label = { Text("FreeOTP") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "andotp",
                    onClick = { importTab = "andotp" },
                    label = { Text("andOTP") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "raivo",
                    onClick = { importTab = "raivo" },
                    label = { Text("Raivo") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "lastpass",
                    onClick = { importTab = "lastpass" },
                    label = { Text("LastPass") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "steam",
                    onClick = { importTab = "steam" },
                    label = { Text("Steam") },
                    colors = cernunnosChipColors(),
                )
                FilterChip(
                    selected = importTab == "plain",
                    onClick = { importTab = "plain" },
                    label = { Text("Plain text") },
                    colors = cernunnosChipColors(),
                )
            }

            Spacer(Modifier.height(16.dp))

            importError?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            when (importTab) {
                "cernunnos" -> {
                    Text(
                        stringResource(R.string.import_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { cernunnosFileLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text("  ${cernunnosFileName ?: stringResource(R.string.import_pick_file)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = data,
                        onValueChange = { data = it },
                        label = { Text(stringResource(R.string.import_data)) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.import_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { vm.importEntries(data.trim(), passphrase) },
                        enabled = data.isNotBlank() && passphrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_button)) }
                }

                "bitwarden" -> {
                    Text(
                        stringResource(R.string.import_bitwarden_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.import_bitwarden_format_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { bitwardenFileLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text("  ${bitwardenFileName ?: stringResource(R.string.import_pick_file)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bitwardenData,
                        onValueChange = { bitwardenData = it },
                        label = { Text(stringResource(R.string.import_bitwarden_label)) },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { vm.importBitwarden(bitwardenData.trim()) },
                        enabled = bitwardenData.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_bitwarden_button)) }
                }

                "google" -> {
                    Text(
                        stringResource(R.string.import_google_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.import_google_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = googleData,
                        onValueChange = { googleData = it },
                        label = { Text(stringResource(R.string.import_google_label)) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { vm.importGoogleAuth(googleData.trim()) },
                        enabled = googleData.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_google_button)) }
                }

                "aegis" -> {
                    Text(
                        stringResource(R.string.import_aegis_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.import_aegis_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { aegisFileLauncher.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text("  ${aegisFileName ?: stringResource(R.string.import_pick_file)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aegisData,
                        onValueChange = { aegisData = it },
                        label = { Text(stringResource(R.string.import_aegis_label)) },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aegisPass,
                        onValueChange = { aegisPass = it },
                        label = { Text(stringResource(R.string.import_aegis_pass)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            val pass = if (aegisPass.isBlank()) null else aegisPass
                            vm.importAegis(aegisData.trim(), pass)
                        },
                        enabled = aegisData.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_aegis_button)) }
                }

                "2fas", "authy", "microsoft", "freeotp", "andotp", "raivo", "lastpass", "steam", "plain" -> {
                    val descRes = when (importTab) {
                        "2fas" -> R.string.import_2fas_desc
                        "authy" -> R.string.import_authy_desc
                        "microsoft" -> R.string.import_microsoft_desc
                        "freeotp" -> R.string.import_freeotp_desc
                        "andotp" -> R.string.import_andotp_desc
                        "raivo" -> R.string.import_raivo_desc
                        "lastpass" -> R.string.import_lastpass_desc
                        "steam" -> R.string.import_steam_desc
                        else -> R.string.import_plain_desc
                    }
                    val mimeTypes = when (importTab) {
                        "freeotp" -> arrayOf("application/xml", "text/xml")
                        "lastpass", "plain" -> arrayOf("text/plain", "text/csv")
                        else -> arrayOf("application/json", "text/plain")
                    }
                    val importAction: (String) -> Unit = when (importTab) {
                        "2fas" -> { d -> vm.importTwoFas(d) }
                        "authy" -> { d -> vm.importAuthy(d) }
                        "microsoft" -> { d -> vm.importMicrosoftAuth(d) }
                        "freeotp" -> { d -> vm.importFreeOtp(d) }
                        "andotp" -> { d -> vm.importAndOtp(d) }
                        "raivo" -> { d -> vm.importRaivoOtp(d) }
                        "lastpass" -> { d -> vm.importLastPass(d) }
                        "steam" -> { d -> vm.importSteam(d) }
                        else -> { d -> vm.importPlainText(d) }
                    }
                    Text(
                        stringResource(descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { genericFileLauncher.launch(mimeTypes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text("  ${genericFileName ?: stringResource(R.string.import_pick_file)}")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = genericData,
                        onValueChange = { genericData = it },
                        label = { Text(stringResource(R.string.import_data)) },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { importAction(genericData.trim()) },
                        enabled = genericData.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.import_button)) }
                }
            }
        }
    }
}
