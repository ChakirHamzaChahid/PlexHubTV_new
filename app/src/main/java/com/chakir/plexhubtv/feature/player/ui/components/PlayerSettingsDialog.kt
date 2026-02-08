package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.VideoQuality

@Composable
fun PlayerSettingsDialog(
    uiState: PlayerUiState,
    onSelectQuality: (VideoQuality) -> Unit,
    onToggleStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Only Quality for now in the main settings, or we could add more generic settings later.
    // Audio and Subtitles are now separate.

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quality Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    uiState.availableQualities.forEach { quality ->
                        item {
                            SettingItem(
                                text = quality.name,
                                isSelected = quality.bitrate == uiState.selectedQuality.bitrate,
                                onClick = { onSelectQuality(quality) },
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingItem(
                            text = "Show Performance Stats",
                            isSelected = uiState.showPerformanceOverlay,
                            onClick = onToggleStats,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun AudioSelectionDialog(
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    onSelect: (AudioTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    SelectionDialog(
        title = "Select Audio",
        items = tracks,
        selectedItem = selectedTrack,
        itemLabel = { "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
fun SubtitleSelectionDialog(
    tracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onSelect: (SubtitleTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    // Add "Off" option to the list effectively
    val allItems = listOf(SubtitleTrack.OFF) + tracks

    SelectionDialog(
        title = "Select Subtitles",
        items = allItems,
        selectedItem = selectedTrack ?: allItems.first(), // Default to Off/First if null
        itemLabel = { if (it.id == "no") "Off" else "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    SelectionDialog(
        title = "Playback Speed",
        items = speeds,
        selectedItem = currentSpeed,
        itemLabel = { "${it}x" },
        itemKey = { it.toString() },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    itemKey: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items.forEach { settingItem ->
                        item {
                            SettingItem(
                                text = itemLabel(settingItem),
                                isSelected = selectedItem?.let { itemKey(it) == itemKey(settingItem) } ?: false,
                                onClick = { onSelect(settingItem) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SyncSettingsDialog(
    title: String,
    currentDelayMs: Long,
    onDelayChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Custom Focusable Adjuster for Android TV
                FocusableDelayAdjuster(
                    currentDelayMs = currentDelayMs,
                    onDelayChanged = onDelayChanged,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    // Reset Button
                    Button(
                        onClick = { onDelayChanged(0L) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                    ) {
                        Text("Reset")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Close Button
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun FocusableDelayAdjuster(
    currentDelayMs: Long,
    onDelayChanged: (Long) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Handle D-Pad events when focused
    fun handleKeyEvent(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false

        return when (event.nativeKeyEvent.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            -> {
                isEditing = !isEditing
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isEditing) {
                    onDelayChanged(currentDelayMs - 50)
                    true
                } else {
                    false
                }
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isEditing) {
                    onDelayChanged(currentDelayMs + 50)
                    true
                } else {
                    false
                }
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                if (isEditing) {
                    isEditing = false
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onKeyEvent { handleKeyEvent(it) }
                .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(12.dp),
        color =
            when {
                isEditing -> MaterialTheme.colorScheme.primaryContainer
                isFocused -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            },
        border =
            if (isFocused || isEditing) {
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${if (currentDelayMs > 0) "+" else ""}$currentDelayMs ms",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (isEditing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = if (isEditing) "Use < > to Adjust, OK to Save" else "Press OK to Edit",
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (isEditing) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.7f,
                        )
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
            )
        }
    }
}

@Composable
fun SettingItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
