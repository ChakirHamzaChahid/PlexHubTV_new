package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray

private val MenuBackground = Color(0xFF1A1A1A)

@Composable
fun PlayerMoreMenu(
    hasChapters: Boolean,
    hasQueue: Boolean,
    showPerformanceOverlay: Boolean = false,
    currentAspectRatioLabel: String = "Fit",
    onCycleAspectRatio: () -> Unit = {},
    onShowSettings: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowSubtitleSync: () -> Unit,
    onShowAudioSync: () -> Unit,
    onShowSubtitleDownload: () -> Unit,
    onShowEqualizer: () -> Unit,
    onToggleStats: () -> Unit,
    onShowChapters: () -> Unit,
    onShowQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // Consume click to prevent dismiss
                ),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            color = MenuBackground,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.player_more),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = NetflixLightGray,
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Menu items
                val items = buildList {
                    add(MenuItem(Icons.Default.HighQuality, stringResource(R.string.player_more_quality), onShowSettings))
                    add(MenuItem(Icons.Default.Speed, stringResource(R.string.player_more_speed), onShowSpeed))
                    add(MenuItem(Icons.Default.SyncAlt, stringResource(R.string.player_more_subtitle_sync), onShowSubtitleSync))
                    add(MenuItem(Icons.Default.Tune, stringResource(R.string.player_more_audio_sync), onShowAudioSync))
                    add(MenuItem(Icons.Default.CloudDownload, stringResource(R.string.player_more_download_subs), onShowSubtitleDownload))
                    add(MenuItem(Icons.Default.Equalizer, stringResource(R.string.player_more_equalizer), onShowEqualizer))
                    add(MenuItem(Icons.Default.AspectRatio, "Aspect Ratio: $currentAspectRatioLabel", onCycleAspectRatio))
                    add(MenuItem(Icons.Default.Analytics, stringResource(R.string.player_more_stats), onToggleStats, isToggle = true, isActive = showPerformanceOverlay))
                    if (hasChapters) {
                        add(MenuItem(Icons.Default.ViewList, stringResource(R.string.player_more_chapters), onShowChapters))
                    }
                    if (hasQueue) {
                        add(MenuItem(Icons.Default.QueueMusic, stringResource(R.string.player_more_queue), onShowQueue))
                    }
                }

                LazyColumn {
                    itemsIndexed(items) { index, item ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val itemModifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }

                        Row(
                            modifier = itemModifier
                                .fillMaxWidth()
                                .background(
                                    if (isFocused) Color.White else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = item.onClick,
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isFocused) Color.Black else NetflixLightGray,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isFocused) Color.Black else Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.isToggle && item.isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val isToggle: Boolean = false,
    val isActive: Boolean = false,
)
