package com.cernunnos.authenticator.crash

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.cernunnos.authenticator.R

/**
 * Dialog shown on app launch when crash logs are detected.
 * Offers the user to share the crash report or delete it.
 * No automatic sending — the user is always in control.
 */
@Composable
fun CrashReportDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val crashCount = remember { CrashReporter.getPendingCrashFiles(context).size }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.crash_message, crashCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.crash_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Share crash logs via system share sheet
                val crashLogs = CrashReporter.getCombinedCrashLogs(context)
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Cernunnos Diwaller crash report")
                    putExtra(Intent.EXTRA_TEXT, crashLogs)
                }
                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.crash_share_chooser)))
                CrashReporter.clearCrashLogs(context)
                onDismiss()
            }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.crash_share))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    CrashReporter.clearCrashLogs(context)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.crash_delete))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.crash_later))
                }
            }
        },
    )
}
