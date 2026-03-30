package com.chakir.plexhubtv.core.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    apkInstaller: ApkInstaller,
    onDismiss: () -> Unit,
) {
    val installState by apkInstaller.state.collectAsState()
    val hasApkAsset = updateInfo.downloadUrl.endsWith(".apk")

    AlertDialog(
        onDismissRequest = {
            if (installState !is InstallState.Downloading) onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.update_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.update_dialog_version_available, updateInfo.versionName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.update_dialog_whats_new),
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
                        text = stringResource(R.string.update_dialog_size, formatSize(updateInfo.apkSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Download progress
                when (val state = installState) {
                    is InstallState.Downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_downloading, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is InstallState.Error -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (installState) {
                is InstallState.Downloading -> {
                    // No button during download
                }
                is InstallState.Error -> {
                    TextButton(onClick = {
                        apkInstaller.reset()
                    }) {
                        Text(stringResource(R.string.update_dialog_retry))
                    }
                }
                else -> {
                    if (hasApkAsset) {
                        TextButton(onClick = {
                            apkInstaller.downloadAndInstallAsync(
                                updateInfo.downloadUrl,
                                updateInfo.versionName,
                            )
                        }) {
                            Text(stringResource(R.string.update_dialog_install))
                        }
                    } else {
                        TextButton(onClick = {
                            apkInstaller.openInBrowser(updateInfo.htmlUrl)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.update_dialog_download))
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (installState !is InstallState.Downloading) {
                TextButton(onClick = {
                    apkInstaller.reset()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.update_dialog_later))
                }
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
