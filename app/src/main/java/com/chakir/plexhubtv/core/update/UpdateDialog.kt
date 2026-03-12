package com.chakir.plexhubtv.core.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Update Available",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Version ${updateInfo.versionName} is available.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "What's new:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateInfo.releaseNotes.take(500),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
                if (updateInfo.apkSize > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Size: ${formatSize(updateInfo.apkSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
