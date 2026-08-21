package com.cernunnos.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.p2p.WifiDirectManager
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import com.cernunnos.authenticator.util.ExportImport
import com.cernunnos.authenticator.util.OtpAuthParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val wifiDirect = remember { WifiDirectManager(context) }
    val peers = remember { mutableStateListOf<android.net.wifi.p2p.WifiP2pDevice>() }
    var status by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    wifiDirect.setPeerListListener { newPeers ->
        peers.clear()
        peers.addAll(newPeers)
    }
    wifiDirect.setConnectionListener { info ->
        if (isSending) {
            val pass = passphrase
            if (pass.length >= 8 && state.entries.isNotEmpty()) {
                val encrypted = ExportImport.export(state.entries, pass)
                wifiDirect.sendEncryptedData(
                    info,
                    encrypted,
                    onSent = { status = context.getString(R.string.sent_successfully); isSending = false },
                    onError = { status = it; isSending = false },
                )
            }
        }
    }

    // Ensure Wi-Fi Direct discovery and groups are cleaned up when leaving the screen
    DisposableEffect(Unit) {
        onDispose { wifiDirect.cleanup() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.p2p_title)) },
                navigationIcon = {
                    IconButton(onClick = { wifiDirect.cleanup(); onBack() }) {
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
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.p2p_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.p2p_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            // Send section
            Text(stringResource(R.string.p2p_send), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (passphrase.length < 8) {
                        status = "Passphrase min 8 characters"
                        return@Button
                    }
                    wifiDirect.initialize()
                    wifiDirect.discoverPeers(
                        onSuccess = { status = "Searching for devices..." },
                        onError = { status = it },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.p2p_discover)) }

            Spacer(Modifier.height(12.dp))

            // Peer list
            if (peers.isNotEmpty()) {
                Text(stringResource(R.string.p2p_devices_found), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(peers) { peer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    peer.deviceName.ifBlank { "Unknown device" },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    isSending = true
                                    status = "Connecting to ${peer.deviceName}..."
                                    wifiDirect.connect(
                                        peer,
                                        onSuccess = { status = "Connected, sending..." },
                                        onError = { status = it; isSending = false },
                                    )
                                }) { Text(stringResource(R.string.p2p_connect_send)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Receive section
            Text(stringResource(R.string.p2p_receive), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (passphrase.length < 8) {
                        status = "Passphrase min 8 characters"
                        return@Button
                    }
                    status = "Waiting for incoming data..."
                    wifiDirect.initialize()
                    wifiDirect.receiveEncryptedData(
                        onReceived = { data ->
                            val pass = passphrase
                            try {
                                val entries = ExportImport.import(data, pass)
                                // Add entries directly via repository
                                entries.forEach { entry ->
                                    // Build otpauth URI and import
                                    val otpauth = buildOtpAuthUri(entry)
                                    vm.addEntryFromOtpAuth(otpauth)
                                }
                                status = "Received ${entries.size} entries"
                            } catch (e: Exception) {
                                status = "Decrypt failed: ${e.message}"
                            }
                        },
                        onError = { status = it },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.p2p_listen)) }

            Spacer(Modifier.height(24.dp))

            // Status
            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun buildOtpAuthUri(entry: com.cernunnos.authenticator.data.model.TotpEntry): String {
    val secret = OtpAuthParser.encodeBase32(entry.secret)
    // URL-encode label and issuer to handle special chars (&, ?, #, :, =, spaces)
    val rawLabel = if (entry.issuer.isNotEmpty()) "${entry.issuer}:${entry.label}" else entry.label
    val label = android.net.Uri.encode(rawLabel, "/")
    val type = entry.type
    val params = mutableListOf(
        "secret=$secret",
        "issuer=${android.net.Uri.encode(entry.issuer)}",
        "algorithm=${entry.algorithm}",
        "digits=${entry.digits}",
        "period=${entry.period}",
    )
    if (entry.type == "hotp") {
        params.add("counter=${entry.counter}")
    }
    return "otpauth://$type/$label?${params.joinToString("&")}"
}
