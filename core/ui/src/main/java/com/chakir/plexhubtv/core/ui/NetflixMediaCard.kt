package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType

enum class CardType {
    POSTER,
    WIDE,
    TOP_TEN
}

@Composable
fun NetflixMediaCard(
    media: MediaItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    cardType: CardType = CardType.POSTER,
    onFocus: (Boolean) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Notify parent of focus changes
    androidx.compose.runtime.LaunchedEffect(isFocused) {
        onFocus(isFocused)
    }

    // Animations
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "border"
    )

    // Dimensions based on CardType
    val cardWidth = when (cardType) {
        CardType.POSTER -> 140.dp
        CardType.WIDE -> 240.dp
        CardType.TOP_TEN -> 140.dp // + rank styling
    }
    val cardAspectRatio = when (cardType) {
        CardType.POSTER, CardType.TOP_TEN -> 2f / 3f
        CardType.WIDE -> 16f / 9f
    }


    Column(
        modifier = modifier
            .width(cardWidth)
            .testTag("media_card_${media.ratingKey}")
            .semantics {
                contentDescription = when (media.type) {
                    MediaType.Movie -> "Film: ${media.title}"
                    MediaType.Show -> "Série: ${media.title}"
                    MediaType.Episode -> "Épisode: ${media.title}"
                    MediaType.Season -> "Saison: ${media.title}"
                    else -> media.title
                }
            }
            .zIndex(if (isFocused) 10f else 0f)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio)
                .clip(RoundedCornerShape(8.dp)) // Rounded corners like modern Netflix
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
        ) {
            // Image Logic
            val imageUrl = remember(media.ratingKey, cardType) {
                when (cardType) {
                    CardType.WIDE -> media.artUrl ?: media.thumbUrl // Prefer backdrop for WIDE
                    else -> media.thumbUrl // Prefer poster for others
                }
            }

            FallbackAsyncImage(
                primaryUrl = imageUrl,
                alternativeUrls = media.alternativeThumbUrls,
                contentDescription = "Affiche de ${media.title}",
                contentScale = ContentScale.Crop,
                imageWidth = when (cardType) {
                    CardType.POSTER, CardType.TOP_TEN -> 420
                    CardType.WIDE -> 720
                },
                imageHeight = when (cardType) {
                    CardType.POSTER, CardType.TOP_TEN -> 630
                    CardType.WIDE -> 405
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("media_poster_${media.ratingKey}")
            )

            // Gradient Scrim on Focus — uses graphicsLayer alpha to avoid layout add/remove
            val scrimAlpha by animateFloatAsState(
                targetValue = if (isFocused) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "scrimAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Rating Badge — Always visible in top-right corner
            val rating = media.rating
            if (rating != null && rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700), // Gold color
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Progress Bar with Remaining Time
            val durationMs = media.durationMs
            val playbackPositionMs = media.playbackPositionMs ?: 0L
            if (playbackPositionMs > 0 && durationMs != null && durationMs > 0) {
                val progress by remember(playbackPositionMs, durationMs) {
                    mutableFloatStateOf(
                        (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    )
                }
                val remainingMs = (durationMs - playbackPositionMs).coerceAtLeast(0)
                NetflixProgressBar(
                    progress = progress,
                    remainingMs = remainingMs,
                    showRemainingTime = isFocused && cardType == CardType.WIDE,
                    ratingKey = media.ratingKey,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Title and Metadata — always reserves space to prevent LazyColumn scroll jumps
        // Uses alpha instead of AnimatedVisibility to keep height stable
        val metadataAlpha by animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "metadataAlpha"
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .graphicsLayer { alpha = metadataAlpha }
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val rating = media.rating
                if (rating != null && rating > 0) {
                    Text(
                        text = "${(rating * 10).toInt()}% Match",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF46D369),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                val metaText = media.contentRating ?: media.year?.toString()
                if (metaText != null) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixProgressBar(
    progress: Float,
    remainingMs: Long = 0,
    showRemainingTime: Boolean = false,
    ratingKey: String = "",
    modifier: Modifier = Modifier
) {
    val progressPercent = (progress * 100).toInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_indicator_$ratingKey")
            .semantics { contentDescription = "Progression: $progressPercent%" }
    ) {
        // Remaining Time Text (optional, only for Continue Watching WIDE cards)
        if (showRemainingTime && remainingMs > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val remainingMinutes = (remainingMs / 60000).toInt()
                val remainingText = when {
                    remainingMinutes < 1 -> "< 1 min left"
                    remainingMinutes == 1 -> "1 min left"
                    else -> "$remainingMinutes min left"
                }
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp) // Increased from 2dp for better visibility
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(NetflixRed)
            )
        }
    }
}
