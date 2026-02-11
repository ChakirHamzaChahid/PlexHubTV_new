package com.chakir.plexhubtv.feature.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.di.designsystem.NetflixRed
import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.player.ui.components.EnhancedSeekBar
import com.chakir.plexhubtv.feature.player.ui.components.SkipMarkerButton
import kotlinx.coroutines.delay

@Composable
fun NetflixPlayerControls(
    media: MediaItem?,
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    chapters: List<Chapter> = emptyList(),
    markers: List<Marker> = emptyList(),
    visibleMarkers: List<Marker> = emptyList(),
    onSkipMarker: (Marker) -> Unit = {},
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)) // Dim background
        ) {
            // Top Bar: Back & Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = media?.title ?: "Unknown Title",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (media?.grandparentTitle != null) {
                            Text(
                                text = "${media.grandparentTitle} - Playing from ${media.remoteSources.firstOrNull { it.serverId == media.serverId }?.serverName ?: "Server"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Center: Play/Pause Big Icon
            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(80.dp)
                        .then(
                             if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier
                        ),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Skip Marker Buttons (Intro / Credits)
            visibleMarkers.forEach { marker ->
                SkipMarkerButton(
                    marker = marker,
                    markerType = marker.type,
                    isVisible = true,
                    onSkip = { onSkipMarker(marker) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 200.dp, end = 32.dp)
                )
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(32.dp)
            ) {
                // Enhanced Seek Bar with chapters & markers
                EnhancedSeekBar(
                    currentPosition = currentTimeMs,
                    duration = durationMs,
                    chapters = chapters,
                    markers = markers,
                    onSeek = onSeek,
                    playedColor = NetflixRed,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     IconButton(onClick = onSkipBackward) {
                        Icon(Icons.Default.FastRewind, "Rewind 10s", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(onClick = onPlayPauseToggle) {
                         Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Default.FastForward, "Forward 30s", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(32.dp))
                     IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, "Next Episode", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                     IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color.White)
                    }
                }
            }
        }
    }
}

