package com.cernunnos.authenticator.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.data.model.DocumentType
import com.cernunnos.authenticator.ui.viewmodel.DocumentUiState
import com.cernunnos.authenticator.ui.viewmodel.DocumentVaultState
import com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    vm: DocumentViewModel,
    onAdd: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onBack: () -> Unit,
    onDecryptCern: (String) -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // File picker for opening .cern files from within the app
    val cernFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onDecryptCern(uri.toString())
        }
    }

    // Show messages/errors as snackbar
    LaunchedEffect(state.message, state.error) {
        state.message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearMessage()
        }
        state.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.doc_back))
                    }
                },
                actions = {
                    // Open .cern file — always visible (has its own password, independent of vault)
                    IconButton(onClick = { cernFilePicker.launch("*/*") }) {
                        Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.doc_open_cern))
                    }
                    if (state.vaultState == DocumentVaultState.UNLOCKED) {
                        IconButton(onClick = { vm.lock() }) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.doc_lock))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.vaultState == DocumentVaultState.UNLOCKED) {
                FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.doc_add))
                }
            }
        },
    ) { padding ->
        when (state.vaultState) {
            DocumentVaultState.UNINITIALIZED -> {
                DocumentVaultInitScreen(vm = vm, modifier = Modifier.padding(padding).imePadding())
            }
            DocumentVaultState.LOCKED -> {
                DocumentVaultUnlockScreen(vm = vm, modifier = Modifier.padding(padding).imePadding())
            }
            DocumentVaultState.UNLOCKED -> {
                DocumentListContent(
                    state = state,
                    onDocumentClick = onDocumentClick,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun DocumentVaultInitScreen(vm: DocumentViewModel, modifier: Modifier) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = passphrase != confirm && confirm.isNotEmpty()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.doc_create_vault),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.doc_create_vault_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.doc_passphrase)) },
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
            label = { Text(stringResource(R.string.doc_passphrase_confirm)) },
            singleLine = true,
            isError = mismatch,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
            ),
            supportingText = if (mismatch) { { Text(stringResource(R.string.doc_passphrase_mismatch)) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.initializeVault(passphrase) },
            enabled = passphrase.length >= 8 && passphrase == confirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doc_create))
        }
    }
}

@Composable
private fun DocumentVaultUnlockScreen(vm: DocumentViewModel, modifier: Modifier) {
    var passphrase by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.doc_unlock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.doc_unlock_passphrase)) },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (passphrase.isNotEmpty()) vm.unlock(passphrase) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.unlock(passphrase) },
            enabled = passphrase.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.doc_unlock))
        }
    }
}

@Composable
private fun DocumentListContent(
    state: DocumentUiState,
    onDocumentClick: (String) -> Unit,
    modifier: Modifier,
) {
    // Expiration warnings
    val hasExpiring = state.expiringSoon.isNotEmpty() || state.expired.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        if (hasExpiring) {
            ExpirationWarningBanner(
                expiringCount = state.expiringSoon.size,
                expiredCount = state.expired.size,
            )
        }

        if (state.documents.isEmpty()) {
            EmptyDocumentsState()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.documents, key = { it.id }) { entry ->
                    DocumentCard(
                        entry = entry,
                        onClick = { onDocumentClick(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpirationWarningBanner(expiringCount: Int, expiredCount: Int) {
    val message = when {
        expiredCount > 0 && expiringCount > 0 ->
            stringResource(R.string.doc_expiring_banner, expiredCount, expiringCount)
        expiredCount > 0 -> stringResource(R.string.doc_expired_banner, expiredCount)
        expiringCount > 0 -> stringResource(R.string.doc_expiring_soon_banner, expiringCount)
        else -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyDocumentsState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.doc_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.doc_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DocumentCard(entry: DocumentEntry, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM yyyy", Locale.getDefault()) }
    val isExpired = entry.expirationDate != null && entry.expirationDate < System.currentTimeMillis()
    val isExpiringSoon = entry.expirationDate != null && entry.expirationDate < System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 && !isExpired

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumbnail = remember(entry.thumbnailBase64) {
                    entry.thumbnailBase64?.let {
                        try {
                            val bytes = Base64.decode(it, Base64.NO_WRAP)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (e: Exception) { null }
                    }
                }
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Info
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    documentTypeLabel(entry.type) + if (entry.hasVerso) " • ${stringResource(R.string.doc_recto_verso_badge)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.expirationDate != null) {
                    val color = when {
                        isExpired -> MaterialTheme.colorScheme.error
                        isExpiringSoon -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        stringResource(R.string.doc_exp_label, dateFormat.format(Date(entry.expirationDate))),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
fun documentTypeLabel(type: DocumentType): String = when (type) {
    DocumentType.DRIVER_LICENSE -> stringResource(R.string.doc_type_driver_license)
    DocumentType.PASSPORT -> stringResource(R.string.doc_type_passport)
    DocumentType.ID_CARD -> stringResource(R.string.doc_type_id_card)
    DocumentType.INSURANCE -> stringResource(R.string.doc_type_insurance)
    DocumentType.VEHICLE_REGISTRATION -> stringResource(R.string.doc_type_vehicle_reg)
    DocumentType.TAX_DOCUMENT -> stringResource(R.string.doc_type_tax)
    DocumentType.MEDICAL -> stringResource(R.string.doc_type_medical)
    DocumentType.OTHER -> stringResource(R.string.doc_type_other)
}
