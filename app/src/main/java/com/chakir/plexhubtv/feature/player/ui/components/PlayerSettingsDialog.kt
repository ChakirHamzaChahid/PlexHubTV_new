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

private enum class SettingsTab { Video, Subtitles, Audio, Advanced }

@Composable
fun PlayerSettingsDialog(
    uiState: PlayerUiState,
    onSelectQuality: (VideoQuality) -> Unit,
    onToggleStats: () -> Unit,
    onShowSubtitleSync: () -> Unit = {},
    onShowAudioSync: () -> Unit = {},
    onShowSubtitleDownload: () -> Unit = {},
    onShowEqualizer: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.Video) }
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
                    .fillMaxWidth(0.45f)
                    .heightIn(max = 550.dp)
                    .testTag("dialog_player_settings")
                    .semantics { contentDescription = qualityDescription },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    DialogHeader(
                        title = stringResource(R.string.player_settings_title),
                        onDismiss = onDismiss,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SettingsTab.entries.forEach { tab ->
                            val tabLabel = when (tab) {
                                SettingsTab.Video -> stringResource(R.string.player_settings_tab_video)
                                SettingsTab.Subtitles -> stringResource(R.string.player_settings_tab_subtitles)
                                SettingsTab.Audio -> stringResource(R.string.player_settings_tab_audio)
                                SettingsTab.Advanced -> stringResource(R.string.player_settings_tab_advanced)
                            }
                            TabButton(
                                label = tabLabel,
                                isSelected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab content
                    when (selectedTab) {
                        SettingsTab.Video -> {
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
                                            modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier,
                                        )
                                    }
                                }
                            }
                        }
                        SettingsTab.Subtitles -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { fr.requestFocus() }
                            LazyColumn {
                                item {
                                    SettingItem(
                                        text = stringResource(R.string.player_subtitle_sync),
                                        isSelected = false,
                                        onClick = {
                                            onDismiss()
                                            onShowSubtitleSync()
                                        },
                                        modifier = Modifier.focusRequester(fr),
                                    )
                                }
                                item {
                                    SettingItem(
                                        text = stringResource(R.string.player_subtitle_download),
                                        isSelected = false,
                                        onClick = {
                                            onDismiss()
                                            onShowSubtitleDownload()
                                        },
                                    )
                                }
                            }
                        }
                        SettingsTab.Audio -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { fr.requestFocus() }
                            LazyColumn {
                                item {
                                    SettingItem(
                                        text = stringResource(R.string.player_audio_sync),
                                        isSelected = false,
                                        onClick = {
                                            onDismiss()
                                            onShowAudioSync()
                                        },
                                        modifier = Modifier.focusRequester(fr),
                                    )
                                }
                                item {
                                    SettingItem(
                                        text = stringResource(R.string.player_equalizer),
                                        isSelected = false,
                                        onClick = {
                                            onDismiss()
                                            onShowEqualizer()
                                        },
                                    )
                                }
                            }
                        }
                        SettingsTab.Advanced -> {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { fr.requestFocus() }
                            LazyColumn {
                                item {
                                    // Playback speed
                                    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                                    Text(
                                        text = stringResource(R.string.player_settings_playback_speed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                    speeds.forEachIndexed { index, speed ->
                                        SettingItem(
                                            text = "${speed}x",
                                            isSelected = uiState.playbackSpeed == speed,
                                            onClick = { onSelectSpeed(speed) },
                                            modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier,
                                        )
                                    }
                                }
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
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
    }
}

@Composable
private fun TabButton(
    label: String,
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
            isSelected -> Color.White.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        interactionSource = interactionSource,
        modifier = modifier.height(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isFocused) Color.Black else Color.White,
            )
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
                    .fillMaxWidth(0.45f)
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

                    // Current delay display
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${if (currentDelayMs > 0) "+" else ""}$currentDelayMs ms",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Granular delay adjustment buttons
                    val firstFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { firstFocusRequester.requestFocus() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val delaySteps = listOf(-1000L, -250L, -50L, 0L, 50L, 250L, 1000L)
                        val delayLabels = listOf("-1s", "-250ms", "-50ms", "Reset", "+50ms", "+250ms", "+1s")

                        delaySteps.forEachIndexed { index, step ->
                            val isReset = step == 0L
                            DelayButton(
                                label = delayLabels[index],
                                onClick = {
                                    if (isReset) onDelayChanged(0L) else onDelayChanged(currentDelayMs + step)
                                },
                                isHighlighted = isReset,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fine-tuning hint
                    Text(
                        text = stringResource(R.string.player_settings_adjust_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
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
private fun DelayButton(
    label: String,
    onClick: () -> Unit,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color.White
            isHighlighted -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        interactionSource = interactionSource,
        modifier = modifier.height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (isFocused) Color.Black else Color.White,
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
