package com.chakir.plexhubtv.feature.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.model.MediaSource
import timber.log.Timber

@Composable
fun SourceSelectionDialog(
    sources: List<MediaSource>,
    onDismiss: () -> Unit,
    onSourceSelected: (MediaSource) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Timber.e(e, "Error showing source selection dialog")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Server") },
        text = {
            LazyColumn {
                sources.forEachIndexed { index, source ->
                    item {
                        val modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier

                        Row(
                            modifier =
                                modifier
                                    .fillMaxWidth()
                                    .clickable { onSourceSelected(source) }
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.serverName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

                                val technicalInfo =
                                    listOfNotNull(
                                        source.container?.uppercase(),
                                        source.videoCodec?.uppercase(),
                                        source.audioCodec?.uppercase(),
                                        source.audioChannels?.let { "${it}ch" },
                                    ).joinToString(" • ")

                                if (technicalInfo.isNotEmpty()) {
                                    Text(
                                        technicalInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                val languages = source.languages.joinToString(", ")
                                if (languages.isNotEmpty()) {
                                    Text(
                                        "Lang: $languages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                }

                                source.fileSize?.let { size ->
                                    val sizeInGb = size.toDouble() / (1024 * 1024 * 1024)
                                    val sizeText =
                                        if (sizeInGb >= 1.0) {
                                            String.format(
                                                "%.2f GB",
                                                sizeInGb,
                                            )
                                        } else {
                                            String.format("%.0f MB", size.toDouble() / (1024 * 1024))
                                        }
                                    Text(sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (source.hasHDR) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Text(
                                            "HDR",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                        )
                                    }
                                }

                                source.resolution?.let { resolution ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color =
                                            if (resolution.contains("4k", ignoreCase = true) || resolution.startsWith("2160")) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                    ) {
                                        Text(
                                            resolution.uppercase(),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color =
                                                if (resolution.contains("4k", ignoreCase = true) || resolution.startsWith("2160")) {
                                                    MaterialTheme.colorScheme.onSecondary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    }
                                }
                            }
                        }
                        if (index < sources.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
