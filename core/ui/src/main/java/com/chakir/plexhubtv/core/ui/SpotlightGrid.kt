package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.Dims
import com.chakir.plexhubtv.core.model.MediaItem

/**
 * SpotlightGrid — featured media grid for the home screen.
 * Layout: 60% large card (left) + 40% column of 2 stacked cards (right).
 * Replaces HomeHeader when enabled via settings.
 */
@Composable
fun SpotlightGrid(
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size < 3) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dims.CardSpacing),
    ) {
        // Large card (60% width)
        SpotlightCard(
            item = items[0],
            showSynopsis = true,
            onClick = { onItemClick(items[0]) },
            onFocused = { onItemFocused(items[0]) },
            modifier = Modifier.weight(0.6f),
        )

        // Right column: 2 stacked cards (40% width)
        Column(
            modifier = Modifier.weight(0.4f),
            verticalArrangement = Arrangement.spacedBy(Dims.CardSpacing),
        ) {
            SpotlightCard(
                item = items[1],
                showSynopsis = false,
                onClick = { onItemClick(items[1]) },
                onFocused = { onItemFocused(items[1]) },
                modifier = Modifier.weight(1f),
            )
            SpotlightCard(
                item = items[2],
                showSynopsis = false,
                onClick = { onItemClick(items[2]) },
                onFocused = { onItemFocused(items[2]) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpotlightCard(
    item: MediaItem,
    showSynopsis: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale = animateFocusScale(isFocused, targetScale = 1.03f)
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) cs.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "spotlightBorder",
    )

    if (isFocused) {
        onFocused()
    }

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Backdrop image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.artUrl ?: item.thumbUrl)
                .size(960, 540)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            cs.background.copy(alpha = 0.92f),
                        ),
                        startY = 100f,
                    )
                )
        )

        // Content overlay at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            // Rating badge
            val rating = item.rating
            if (rating != null && rating > 0) {
                Text(
                    text = String.format("%.1f", rating),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Title
            Text(
                text = item.title,
                style = if (showSynopsis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Year + Genre
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.first(),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            // Synopsis (large card only)
            if (showSynopsis && !item.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.summary!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onBackground.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
