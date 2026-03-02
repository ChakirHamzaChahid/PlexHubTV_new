package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.VideoQuality

private val DialogBackground = Color(0xFF1A1A1A)

@Composable
fun PlayerSettingsDialog(
    uiState: PlayerUiState,
    onSelectQuality: (VideoQuality) -> Unit,
    onToggleStats: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val qualityDescription = stringResource(R.string.player_settings_quality_description)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DialogBackground,
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .heightIn(max = 500.dp)
                    .testTag("dialog_player_settings")
                    .semantics { contentDescription = qualityDescription },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.player_settings_quality),
                        onDismiss = onDismiss,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        uiState.availableQualities.forEachIndexed { index, quality ->
                            item {
                                val fr = remember { FocusRequester() }
                                if (index == 0) {
                                    LaunchedEffect(Unit) { fr.requestFocus() }
                                }
                                SettingItem(
                                    text = quality.name,
                                    isSelected = quality.bitrate == uiState.selectedQuality.bitrate,
                                    onClick = { onSelectQuality(quality) },
                                    modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingItem(
                                text = stringResource(R.string.player_settings_show_stats),
                                isSelected = uiState.showPerformanceOverlay,
                                onClick = onToggleStats,
                            )
                        }
                    }
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
        title = stringResource(R.string.player_settings_select_audio),
        items = tracks,
        selectedItem = selectedTrack,
        itemLabel = { "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss,
        dialogTestTag = "dialog_audio_selection"
    )
}

@Composable
fun SubtitleSelectionDialog(
    tracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onSelect: (SubtitleTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val offLabel = stringResource(R.string.player_settings_off)

    SelectionDialog(
        title = stringResource(R.string.player_settings_select_subtitles),
        items = tracks,
        selectedItem = selectedTrack ?: tracks.firstOrNull() ?: SubtitleTrack.OFF,
        itemLabel = { if (it.id == "no") offLabel else "${it.title} (${it.language})" },
        itemKey = { it.id },
        onSelect = onSelect,
        onDismiss = onDismiss,
        dialogTestTag = "dialog_subtitle_selection"
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
        title = stringResource(R.string.player_settings_playback_speed),
        items = speeds,
        selectedItem = currentSpeed,
        itemLabel = { "${it}x" },
        itemKey = { it.toString() },
        onSelect = onSelect,
        onDismiss = onDismiss,
        dialogTestTag = "dialog_speed_selection"
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
    dialogTestTag: String = "dialog_selection"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DialogBackground,
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .heightIn(max = 500.dp)
                    .testTag(dialogTestTag)
                    .semantics { contentDescription = title },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = title,
                        onDismiss = onDismiss,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        items.forEachIndexed { index, settingItem ->
                            item {
                                val fr = remember { FocusRequester() }
                                if (index == 0) {
                                    LaunchedEffect(Unit) { fr.requestFocus() }
                                }
                                SettingItem(
                                    text = itemLabel(settingItem),
                                    isSelected = selectedItem?.let { itemKey(it) == itemKey(settingItem) } ?: false,
                                    onClick = { onSelect(settingItem) },
                                    modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier
                                )
                            }
                        }
                    }
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DialogBackground,
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .heightIn(max = 400.dp)
                    .testTag("dialog_sync_settings")
                    .semantics { contentDescription = title },
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DialogHeader(
                        title = title,
                        onDismiss = onDismiss,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    FocusableDelayAdjuster(
                        currentDelayMs = currentDelayMs,
                        onDelayChanged = onDelayChanged,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { onDelayChanged(0L) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(stringResource(R.string.player_settings_reset))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

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
                if (isEditing) { onDelayChanged(currentDelayMs - 50); true } else false
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isEditing) { onDelayChanged(currentDelayMs + 50); true } else false
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                if (isEditing) { isEditing = false; true } else false
            }
            else -> false
        }
    }

    val adjustHint = stringResource(R.string.player_settings_adjust_hint)
    val editHint = stringResource(R.string.player_settings_edit_hint)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .onKeyEvent { handleKeyEvent(it) }
            .focusable(interactionSource = interactionSource),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isEditing -> Color.White
            isFocused -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.05f)
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
                color = if (isEditing) Color.Black else Color.White,
            )
            Text(
                text = if (isEditing) adjustHint else editHint,
                style = MaterialTheme.typography.labelMedium,
                color = if (isEditing) Color.Black.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Shared components ──────────────────────────────────────────────

@Composable
private fun DialogHeader(
    title: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = NetflixLightGray,
            )
        }
    }
}

@Composable
fun SettingItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White,
                )
            }
        }
    }
}
