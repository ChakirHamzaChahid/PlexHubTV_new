package com.chakir.plexhubtv.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.feature.player.PlayerAction
import com.chakir.plexhubtv.feature.player.PlayerUiState
import com.chakir.plexhubtv.feature.player.ui.components.EnhancedSeekBar
import com.chakir.plexhubtv.feature.player.ui.components.SkipMarkerButton
import com.chakir.plexhubtv.domain.model.Chapter
import com.chakir.plexhubtv.domain.model.Marker

@Composable
fun PlezyPlayerControls(
    uiState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    title: String,
    chapters: List<Chapter> = emptyList(),
    markers: List<Marker> = emptyList(),
    visibleMarkers: List<Marker> = emptyList(),
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onAction(PlayerAction.Close) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )
            
            Row {
                CircularControlButton(
                    icon = Icons.Default.Audiotrack,
                    onClick = { onAction(PlayerAction.ShowAudioSelector) },
                    size = 40
                )
                CircularControlButton(
                    icon = Icons.Default.Subtitles,
                    onClick = { onAction(PlayerAction.ShowSubtitleSelector) },
                    size = 40
                )
                CircularControlButton(
                    icon = Icons.Default.Settings,
                    onClick = { onAction(PlayerAction.ToggleSettings) },
                    size = 40
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Center Controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Episode
            CircularControlButton(
                icon = Icons.Default.SkipPrevious,
                onClick = { onAction(PlayerAction.Previous) },
                size = 48
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Previous Chapter
            CircularControlButton(
                icon = Icons.Default.FastRewind,
                onClick = { onAction(PlayerAction.SeekToPreviousChapter) },
                size = 48
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // Play/Pause
            CircularControlButton(
                icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = { 
                    if (uiState.isPlaying) onAction(PlayerAction.Pause) 
                    else onAction(PlayerAction.Play) 
                },
                size = 72,
                modifier = if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier
            )
            
            Spacer(modifier = Modifier.width(24.dp))

            // Next Chapter
            CircularControlButton(
                icon = Icons.Default.FastForward,
                onClick = { onAction(PlayerAction.SeekToNextChapter) },
                size = 48
            )

            Spacer(modifier = Modifier.width(24.dp))
            
            // Next Episode
            CircularControlButton(
                icon = Icons.Default.SkipNext,
                onClick = { onAction(PlayerAction.Next) },
                size = 48
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Bottom Bar ---
        Column(modifier = Modifier.fillMaxWidth()) {
            // Enhanced Seek Bar
            EnhancedSeekBar(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                chapters = chapters,
                markers = markers,
                onSeek = { onAction(PlayerAction.SeekTo(it)) }
            )
        }
    }
    
    // --- Overlays for Skip Buttons ---
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
         // Intro
        val introMarker = markers.find { it.type == "intro" }
        val isIntroVisible = visibleMarkers.any { it.type == "intro" }
        SkipMarkerButton(
            marker = introMarker,
            markerType = "intro",
            isVisible = isIntroVisible,
            onSkip = { introMarker?.let { onAction(PlayerAction.SkipMarker(it)) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 32.dp)
        )

        // Credits
        val creditsMarker = markers.find { it.type == "credits" }
        val isCreditsVisible = visibleMarkers.any { it.type == "credits" }
        SkipMarkerButton(
            marker = creditsMarker,
            markerType = "credits",
            isVisible = isCreditsVisible,
            onSkip = { creditsMarker?.let { onAction(PlayerAction.SkipMarker(it)) } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 32.dp)
        )
    }
}

@Composable
fun CircularControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.1f else 1f)
    val backgroundColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
    val contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        modifier = modifier
            .size(size.dp)
            .scale(scale),
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size((size * 0.6).dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
