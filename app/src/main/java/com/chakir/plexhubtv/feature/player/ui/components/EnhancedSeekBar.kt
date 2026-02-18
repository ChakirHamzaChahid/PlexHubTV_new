package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import kotlin.math.max
import kotlin.math.min
import android.view.KeyEvent as NativeKeyEvent

/**
 * Barre de progression personnalisée (SeekBar) pour le lecteur vidéo. Affiche :
 * - La progression actuelle (glissable)
 * - Les marqueurs de chapitres (bandes sombres/claires)
 * - Les marqueurs spéciaux (Intro en vert, Crédits en rouge)
 */
@Composable
fun EnhancedSeekBar(
    currentPosition: Long,
    duration: Long,
    chapters: List<Chapter> = emptyList(),
    markers: List<Marker> = emptyList(),
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    playedColor: Color = Color(0xFFE5A00D),
) {
    if (duration <= 0L) return

    var isDrag by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(currentPosition) }

    val displayPosition = if (isDrag) dragPosition else currentPosition
    val progress = if (duration > 0) displayPosition.toFloat() / duration.toFloat() else 0f
    var boxWidth by remember { mutableStateOf(0f) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Main seekbar container (Touch Area)
        // Main seekbar container (Touch + Focus Area)
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp) // Touch area height
                    .testTag("player_seekbar")
                    .semantics { contentDescription = "Barre de progression" }
                    .onSizeChanged { boxWidth = it.width.toFloat() }
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val current = if (isDrag) dragPosition else currentPosition
                                    val newPos = max(0L, current - 10000) // -10s
                                    isDrag = true
                                    dragPosition = newPos
                                    onSeek(newPos)
                                    true
                                }
                                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val current = if (isDrag) dragPosition else currentPosition
                                    val newPos = min(duration, current + 10000) // +10s
                                    isDrag = true
                                    dragPosition = newPos
                                    onSeek(newPos)
                                    true
                                }
                                NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER -> {
                                    if (isDrag) {
                                        onSeek(dragPosition)
                                        isDrag = false
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            // Optional: Reset isDrag on key up if we want "seek on release" behavior,
                            // but for immediate seek "on press" is usually better for D-pad scrubbing.
                            // We will let the "isDragging" state persist slightly or rely on the parent updating currentPosition.
                            false
                        } else {
                            false
                        }
                    }
                    .pointerInput(Unit) {
                        var dragStartX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDrag = true
                                dragStartX = offset.x
                                dragPosition = currentPosition // Start drag from current
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                isDrag = true
                                val dragDelta = change.position.x - dragStartX
                                val seekDelta = (dragDelta / boxWidth) * duration
                                dragPosition =
                                    max(
                                        0L,
                                        min(
                                            currentPosition +
                                                seekDelta.toLong(),
                                            duration,
                                        ),
                                    )
                            },
                            onDragEnd = {
                                isDrag = false
                                onSeek(dragPosition)
                                dragStartX = 0f
                            },
                        )
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Visual Track (Background)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (isFocused) 6.dp else 4.dp) // Visual thickness increases on focus
                        .background(
                            if (isFocused) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                        ),
            ) {
                // Chapter markers logic can be added here
            }

            // Progress (Played part)
            Box(
                modifier =
                    Modifier
                        .height(if (isFocused) 6.dp else 4.dp)
                        .fillMaxWidth(progress)
                        .background(playedColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )

            // Markers (Intro/Credits)
            markers.filter { it.type == "intro" }.forEach { marker ->
                val markerStart = (marker.startTime.toFloat() / duration.toFloat())
                Box(
                    modifier =
                        Modifier
                            .height(if (isFocused) 6.dp else 4.dp)
                            .width(4.dp)
                            .background(Color.Green)
                            .offset(x = (markerStart * boxWidth).dp),
                )
            }
            markers.filter { it.type == "credits" }.forEach { marker ->
                val markerStart = (marker.startTime.toFloat() / duration.toFloat())
                Box(
                    modifier =
                        Modifier
                            .height(if (isFocused) 6.dp else 4.dp)
                            .width(4.dp)
                            .background(Color.Red)
                            .offset(x = (markerStart * boxWidth).dp),
                )
            }
        }

        // Time display
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = formatTime(displayPosition), color = Color.White, fontSize = 12.sp)

            // Current chapter display
            chapters
                .firstOrNull { chapter ->
                    displayPosition >= chapter.startTime && displayPosition < chapter.endTime
                }
                ?.let { chapter ->
                    Text(text = chapter.title, color = Color(0xFFE5A00D), fontSize = 11.sp)
                }

            Text(text = formatTime(duration), color = Color.White, fontSize = 12.sp)
        }

        // Chapter indicators legend
        if (chapters.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(text = "Chapitres: ${chapters.size}", color = Color.Gray, fontSize = 10.sp) }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
