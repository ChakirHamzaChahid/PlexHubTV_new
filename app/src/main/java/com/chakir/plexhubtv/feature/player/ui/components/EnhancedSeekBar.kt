package com.chakir.plexhubtv.feature.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.view.KeyEvent as NativeKeyEvent

/**
 * Barre de progression personnalisée (SeekBar) pour le lecteur vidéo. Affiche :
 * - La progression actuelle (glissable)
 * - Les marqueurs de chapitres (séparateurs visuels sur la barre)
 * - Les marqueurs spéciaux (Intro en vert, Crédits en rouge)
 * - Une vignette d'aperçu du chapitre pendant le scrubbing
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

    val seekbarDesc = stringResource(R.string.player_seekbar_description)
    val chaptersLabel = stringResource(R.string.player_chapters_count, chapters.size)

    // Find the chapter at the current scrub position
    val scrubChapter = remember(displayPosition, chapters) {
        chapters.firstOrNull { displayPosition >= it.startTime && displayPosition < it.endTime }
    }

    val density = LocalDensity.current
    val thumbWidth = 160.dp
    val thumbWidthPx = with(density) { thumbWidth.toPx() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Thumbnail preview popup (above the seek bar, follows drag position)
        if (isDrag && scrubChapter != null && chapters.isNotEmpty()) {
            val chapter = scrubChapter
            val progressPx = progress * boxWidth
            val offsetPx = (progressPx - thumbWidthPx / 2)
                .coerceIn(0f, (boxWidth - thumbWidthPx).coerceAtLeast(0f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetPx.roundToInt(), 0) }
                        .width(thumbWidth),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Thumbnail image
                        if (chapter.thumbUrl != null) {
                            AsyncImage(
                                model = chapter.thumbUrl,
                                contentDescription = chapter.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(thumbWidth)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray),
                            )
                        } else {
                            // Fallback: show chapter title in a box when no thumbnail
                            Box(
                                modifier = Modifier
                                    .width(thumbWidth)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = chapter.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }

                        // Chapter title + time below the thumbnail
                        Text(
                            text = "${chapter.title} · ${formatTime(displayPosition)}",
                            color = Color(0xFFE5A00D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Main seekbar container (Touch + Focus Area)
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp) // Touch area height
                    .testTag("player_seekbar")
                    .semantics { contentDescription = seekbarDesc }
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
                        .height(if (isFocused) 6.dp else 4.dp)
                        .background(
                            if (isFocused) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(2.dp),
                        ),
            )

            // Chapter boundary separators on the track
            if (chapters.size > 1) {
                chapters.drop(1).forEach { chapter ->
                    val chapterPos = chapter.startTime.toFloat() / duration.toFloat()
                    Box(
                        modifier = Modifier
                            .height(if (isFocused) 6.dp else 4.dp)
                            .width(2.dp)
                            .offset { IntOffset((chapterPos * boxWidth).roundToInt(), 0) }
                            .background(Color.White.copy(alpha = 0.6f)),
                    )
                }
            }

            // Progress (Played part)
            Box(
                modifier =
                    Modifier
                        .height(if (isFocused) 6.dp else 4.dp)
                        .fillMaxWidth(progress)
                        .background(playedColor, shape = RoundedCornerShape(2.dp)),
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
                            .offset { IntOffset((markerStart * boxWidth).roundToInt(), 0) },
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
                            .offset { IntOffset((markerStart * boxWidth).roundToInt(), 0) },
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
            if (!isDrag) {
                scrubChapter?.let { chapter ->
                    Text(text = chapter.title, color = Color(0xFFE5A00D), fontSize = 12.sp)
                }
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
            ) { Text(text = chaptersLabel, color = Color.Gray, fontSize = 12.sp) }
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
