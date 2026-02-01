package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chakir.plexhubtv.domain.model.AudioTrack
import com.chakir.plexhubtv.domain.model.SubtitleTrack
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.VideoQuality

@Composable
fun PlayerSettingsDialog(
    uiState: PlayerUiState,
    onSelectQuality: (VideoQuality) -> Unit,
    onDismiss: () -> Unit
) {
    // Only Quality for now in the main settings, or we could add more generic settings later.
    // Audio and Subtitles are now separate.
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quality Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items(uiState.availableQualities) { quality ->
                         SettingItem(
                            text = quality.name,
                            isSelected = quality.bitrate == uiState.selectedQuality.bitrate, 
                            onClick = { onSelectQuality(quality) }
                         )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
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
    onDismiss: () -> Unit
) {
    SelectionDialog(
        title = "Select Audio",
        items = tracks,
        selectedItem = selectedTrack,
        itemLabel = { "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
fun SubtitleSelectionDialog(
    tracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onSelect: (SubtitleTrack) -> Unit,
    onDismiss: () -> Unit
) {
    // Add "Off" option to the list effectively
    val allItems = listOf(SubtitleTrack.OFF) + tracks

    SelectionDialog(
        title = "Select Subtitles",
        items = allItems,
        selectedItem = selectedTrack ?: allItems.first(), // Default to Off/First if null
        itemLabel = { if(it.id == "no") "Off" else "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss
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
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items(items) { item ->
                        SettingItem(
                            text = itemLabel(item),
                            isSelected = selectedItem?.let { itemKey(it) == itemKey(item) } ?: false,
                            onClick = { onSelect(item) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text, 
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check, 
                    contentDescription = null, 
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
