package com.chakir.plexhubtv.feature.iptv.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.EpgEntry
import com.chakir.plexhubtv.core.model.LiveChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerEpgColumn(
    selectedChannel: LiveChannel?,
    streamUrl: String?,
    isResolvingStream: Boolean,
    channelEpg: List<EpgEntry>,
    isLoadingEpg: Boolean,
    exoPlayer: ExoPlayer?,
    onGoFullscreen: () -> Unit,
    channelColumnFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp)
            .focusProperties {
                exit = { direction ->
                    when (direction) {
                        FocusDirection.Left -> channelColumnFocusRequester
                        else -> FocusRequester.Default
                    }
                }
            },
    ) {
        // Player area
        PlayerArea(
            selectedChannel = selectedChannel,
            streamUrl = streamUrl,
            isResolvingStream = isResolvingStream,
            exoPlayer = exoPlayer,
            onGoFullscreen = onGoFullscreen,
        )

        Spacer(Modifier.height(8.dp))

        // EPG area
        EpgArea(
            selectedChannel = selectedChannel,
            channelEpg = channelEpg,
            isLoadingEpg = isLoadingEpg,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerArea(
    selectedChannel: LiveChannel?,
    streamUrl: String?,
    isResolvingStream: Boolean,
    exoPlayer: ExoPlayer?,
    onGoFullscreen: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selectedChannel == null -> {
                // No channel selected
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.live_tv_select_channel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            isResolvingStream -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }

            streamUrl != null && exoPlayer != null -> {
                // ExoPlayer embedded view
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Fullscreen button overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    IconButton(
                        onClick = onGoFullscreen,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.live_tv_fullscreen),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgArea(
    selectedChannel: LiveChannel?,
    channelEpg: List<EpgEntry>,
    isLoadingEpg: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Channel name header
        if (selectedChannel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selectedChannel.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            selectedChannel == null -> {
                // Nothing to show
            }

            isLoadingEpg -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            channelEpg.isEmpty() -> {
                Text(
                    text = stringResource(R.string.live_tv_no_epg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = channelEpg, key = { it.id }) { epg ->
                        EpgItem(epg = epg)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgItem(epg: EpgEntry) {
    val now = System.currentTimeMillis()
    val isNowPlaying = now in epg.startTime..epg.endTime
    val containerColor = if (isNowPlaying) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Time range
            Text(
                text = "${formatTime(epg.startTime)} – ${formatTime(epg.endTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            // Title
            Text(
                text = epg.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Description
            epg.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Progress bar for current program
            if (isNowPlaying) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { epg.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(timestampMs: Long): String {
    return timeFormat.format(Date(timestampMs))
}
