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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixRed
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
    onShowSubtitles: () -> Unit = {},
    onShowAudio: () -> Unit = {},
    onShowSettings: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val prevChapterDesc = stringResource(R.string.player_previous_chapter)
    val nextChapterDesc = stringResource(R.string.player_next_chapter)
    val controlsDesc = stringResource(R.string.player_controls_description)
    val unknownTitle = stringResource(R.string.player_unknown_title)
    val server = stringResource(R.string.player_server)
    val backDesc = stringResource(R.string.player_back)
    val pauseDesc = stringResource(R.string.player_pause)
    val playDesc = stringResource(R.string.player_play)
    val rewindDesc = stringResource(R.string.player_rewind_10s)
    val forwardDesc = stringResource(R.string.player_forward_30s)
    val playPauseDesc = stringResource(R.string.player_play_pause)
    val stopDesc = stringResource(R.string.player_stop)
    val subtitlesDesc = stringResource(R.string.player_subtitles)
    val audioDesc = stringResource(R.string.player_audio)
    val settingsDesc = stringResource(R.string.player_settings)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_controls")
                .semantics { contentDescription = controlsDesc }
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
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = backDesc,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = media?.title ?: unknownTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (media?.grandparentTitle != null) {
                            val playingFrom = stringResource(R.string.player_playing_from, media.remoteSources.firstOrNull { it.serverId == media.serverId }?.serverName ?: server)
                            Text(
                                text = "${media.grandparentTitle} - $playingFrom",
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
                        .testTag("player_playpause_button")
                        .then(
                             if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier
                        ),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) pauseDesc else playDesc,
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
                    // Chapter: Previous
                    if (chapters.isNotEmpty()) {
                        IconButton(
                            onClick = onPreviousChapter,
                            modifier = Modifier.testTag("player_prev_chapter")
                        ) {
                            Icon(Icons.Default.SkipPrevious, prevChapterDesc, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onSkipBackward,
                        modifier = Modifier.testTag("player_skip_backward")
                    ) {
                        Icon(Icons.Default.FastRewind, rewindDesc, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.testTag("player_transport_playpause")
                    ) {
                         Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            playPauseDesc,
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier.testTag("player_skip_forward")
                    ) {
                        Icon(Icons.Default.FastForward, forwardDesc, tint = Color.White)
                    }

                    // Chapter: Next
                    if (chapters.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onNextChapter,
                            modifier = Modifier.testTag("player_next_chapter")
                        ) {
                            Icon(Icons.Default.SkipNext, nextChapterDesc, tint = Color(0xFFE5A00D))
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))
                     IconButton(
                        onClick = onNext,
                        modifier = Modifier.testTag("player_next_button")
                     ) {
                        val nextEpisodeDesc = stringResource(R.string.player_next_episode)
                        Icon(Icons.Default.SkipNext, nextEpisodeDesc, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                     IconButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("player_stop_button")
                     ) {
                        Icon(Icons.Default.Stop, stopDesc, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(32.dp))
                    IconButton(
                        onClick = onShowSubtitles,
                        modifier = Modifier.testTag("player_subtitles_button")
                    ) {
                        Icon(Icons.Default.Subtitles, subtitlesDesc, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onShowAudio,
                        modifier = Modifier.testTag("player_audio_button")
                    ) {
                        Icon(Icons.Default.VolumeUp, audioDesc, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onShowSettings,
                        modifier = Modifier.testTag("player_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, settingsDesc, tint = Color.White)
                    }
                }
            }
        }
    }
}

