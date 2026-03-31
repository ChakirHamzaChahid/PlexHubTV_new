package com.chakir.plexhubtv.feature.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.common.util.FormatUtils
import com.chakir.plexhubtv.core.model.Chapter

@Composable
fun ChapterOverlay(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    currentPosition: Long,
    onSelectChapter: (Chapter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val currentIndex = chapters.indexOfFirst { it == currentChapter }.coerceAtLeast(0)

    // Auto-scroll to current chapter and focus it
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        if (currentIndex > 0) {
            listState.animateScrollToItem(maxOf(0, currentIndex - 1))
        }
    }

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // Consume click
                ),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_chapters_title) + " (${chapters.size})",
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
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // Chapter cards
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusProperties { down = FocusRequester.Cancel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val isCurrent = chapter == currentChapter
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()

                    val cardModifier = if (index == currentIndex) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }

                    Column(
                        modifier = cardModifier
                            .padding(horizontal = 8.dp)
                            .width(200.dp)
                            .scale(if (isFocused) 1.05f else 1f)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectChapter(chapter) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Thumbnail
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isCurrent || isFocused) {
                                androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    if (isFocused) cs.onBackground else MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(200.dp, 112.dp),
                        ) {
                            if (chapter.thumbUrl != null) {
                                AsyncImage(
                                    model = chapter.thumbUrl,
                                    contentDescription = chapter.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                // Fallback: title centered on dark background
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title
                        Text(
                            text = if (isCurrent) "► ${chapter.title}" else chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else if (isFocused) cs.onBackground
                            else cs.onSurfaceVariant,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Timestamp
                        Text(
                            text = FormatUtils.formatDurationTimestamp(chapter.startTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFocused) cs.onBackground.copy(alpha = 0.8f) else cs.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
