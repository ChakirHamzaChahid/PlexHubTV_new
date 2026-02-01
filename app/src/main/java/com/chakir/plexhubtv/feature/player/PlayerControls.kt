package com.chakir.plexhubtv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.domain.model.AudioTrack
import com.chakir.plexhubtv.domain.model.SubtitleTrack

/**
 * Interface utilisateur des contrôles du lecteur vidéo (Overlay).
 * Gère l'affichage des boutons Lecture, Pause, Suivant/Précédent, Seekbar et Options.
 */
@Composable
fun PlayerControls(
    uiState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    // Gradient Overlays
    Box(modifier = modifier.fillMaxSize()) {
        // Top Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )

        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            TopControlBar(title = title, onClose = { onAction(PlayerAction.Close) })

            // Center Controls (Play/Pause/Seek)
            CenterControls(
                isPlaying = uiState.isPlaying,
                onPlayPause = { if (uiState.isPlaying) onAction(PlayerAction.Pause) else onAction(PlayerAction.Play) },
                onSeekForward = { onAction(PlayerAction.SeekTo(uiState.currentPosition + 10000)) },
                onSeekBackward = { onAction(PlayerAction.SeekTo(uiState.currentPosition - 10000)) },
                onNext = { onAction(PlayerAction.Next) },
                onPrevious = { onAction(PlayerAction.Previous) },
                hasNext = true, // TODO: Check playlist/episodes
                hasPrevious = true // TODO: Check playback history
            )

            // Bottom Bar (Seekbar + Options)
            BottomControlBar(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                bufferedPosition = uiState.bufferedPosition, // We'll add this to state
                onSeek = { onAction(PlayerAction.SeekTo(it)) },
                onAudioSettings = { onAction(PlayerAction.ShowAudioSelector) },
                onSubtitleSettings = { onAction(PlayerAction.ShowSubtitleSelector) },
                onVideoSettings = { /* TODO: Video Settings */ }
            )
        }
    }
}

@Composable
fun TopControlBar(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Rewind 10s
        IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.FastRewind,
                contentDescription = "Rewind 10s",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Play/Pause (Hero)
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Forward 10s
        IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = "Forward 10s",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Next
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun BottomControlBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    onAudioSettings: () -> Unit,
    onSubtitleSettings: () -> Unit,
    onVideoSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Time & Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Custom Slider (Simplified for now, can use Canvas for buffer later)
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.height(20.dp) // Compact touch target
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = Icons.Rounded.Audiotrack,
                label = "Audio",
                onClick = onAudioSettings
            )
            ControlButton(
                icon = Icons.Rounded.ClosedCaption,
                label = "Subtitles",
                onClick = onSubtitleSettings
            )
             ControlButton(
                icon = Icons.Rounded.AspectRatio,
                label = "Fit", // Aspect Ratio
                onClick = {}
            )
            ControlButton(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                onClick = onVideoSettings
            )
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    val h = m / 60
    
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m % 60, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
