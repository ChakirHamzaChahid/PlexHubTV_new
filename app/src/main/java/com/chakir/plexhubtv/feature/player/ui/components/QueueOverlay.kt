package com.chakir.plexhubtv.feature.player.ui.components

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.common.util.FormatUtils
import com.chakir.plexhubtv.core.model.MediaItem

@Composable
fun QueueOverlay(
    queue: List<MediaItem>,
    currentIndex: Int,
    onSelectItem: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        if (currentIndex > 0) {
            listState.animateScrollToItem(maxOf(0, currentIndex - 1))
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background.copy(alpha = 0.5f))
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
                .width(380.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // Consume click
                ),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            color = cs.surface.copy(alpha = 0.95f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.player_queue_title) +
                            " (${stringResource(R.string.player_queue_count, queue.size)})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    color = cs.onBackground.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Queue items
                LazyColumn(state = listState) {
                    itemsIndexed(queue) { index, item ->
                        val isCurrent = index == currentIndex
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        val itemModifier = if (index == currentIndex) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }

                        Row(
                            modifier = itemModifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isFocused -> cs.onBackground
                                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onSelectItem(index) },
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Now Playing indicator
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isFocused) cs.background
                                            else MaterialTheme.colorScheme.primary,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Thumbnail
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp, 45.dp),
                            ) {
                                item.thumbUrl?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isFocused) cs.background
                                    else if (isCurrent) cs.onBackground
                                    else cs.onSurfaceVariant,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isCurrent) {
                                    Text(
                                        text = stringResource(R.string.player_queue_now_playing),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isFocused) cs.background.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            // Duration
                            val durationMs = item.durationMs
                            if (durationMs != null && durationMs > 0) {
                                Text(
                                    text = FormatUtils.formatDurationTimestamp(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFocused) cs.background.copy(alpha = 0.6f)
                                    else cs.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }

                        if (index < queue.size - 1) {
                            HorizontalDivider(
                                color = cs.onBackground.copy(alpha = 0.05f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
