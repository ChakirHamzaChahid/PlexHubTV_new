package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Custom shimmer effect for skeleton loading states.
 * Creates an animated gradient that moves from left to right.
 */
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.15f),
        Color.White.copy(alpha = 0.25f),
        Color.White.copy(alpha = 0.15f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 100f, translateAnim - 100f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Skeleton for a single media card in a row.
 */
@Composable
fun MediaCardSkeleton(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f // Portrait poster aspect ratio
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(
                brush = shimmerBrush(),
                shape = RoundedCornerShape(6.dp)
            )
    )
}

/**
 * Skeleton for a horizontal row of media cards (like Netflix content row).
 */
@Composable
fun MediaRowSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title skeleton
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(24.dp)
                .padding(start = 48.dp)
                .background(
                    brush = shimmerBrush(),
                    shape = RoundedCornerShape(4.dp)
                )
        )

        // Cards row skeleton
        Row(
            modifier = Modifier.padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(itemCount) {
                MediaCardSkeleton(
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

/**
 * Skeleton for the Detail screen hero section.
 */
@Composable
fun DetailHeroSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title skeleton
        Box(
            modifier = Modifier
                .width(400.dp)
                .height(48.dp)
                .background(
                    brush = shimmerBrush(),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(Modifier.height(8.dp))

        // Metadata row skeleton (year, duration, rating)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(20.dp)
                        .background(
                            brush = shimmerBrush(),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Summary skeleton (3 lines)
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 2) 0.6f else 0.9f)
                    .height(18.dp)
                    .background(
                        brush = shimmerBrush(),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))

        // Action buttons skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp)
                    .background(
                        brush = shimmerBrush(),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(56.dp)
                    .background(
                        brush = shimmerBrush(),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

/**
 * Skeleton for the full Home screen (multiple content rows).
 */
@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 4
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero/Billboard skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .background(
                    brush = shimmerBrush()
                )
        )

        // Content rows skeleton
        repeat(rowCount) {
            MediaRowSkeleton()
        }
    }
}

/**
 * Skeleton for Library grid view.
 */
@Composable
fun LibraryGridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 5,
    rows: Int = 3
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Title skeleton
        Box(
            modifier = Modifier
                .width(250.dp)
                .height(32.dp)
                .background(
                    brush = shimmerBrush(),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(Modifier.height(16.dp))

        // Grid skeleton
        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(columns) {
                    MediaCardSkeleton(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Skeleton for Episode list items.
 */
@Composable
fun EpisodeItemSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail skeleton
        Box(
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(16f / 9f)
                .background(
                    brush = shimmerBrush(),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        // Text content skeleton
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .background(
                        brush = shimmerBrush(),
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            // Summary skeleton (2 lines)
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 1) 0.5f else 0.9f)
                        .height(16.dp)
                        .background(
                            brush = shimmerBrush(),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }

            // Duration skeleton
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .background(
                        brush = shimmerBrush(),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

/**
 * Skeleton for Season detail screen.
 */
@Composable
fun SeasonDetailSkeleton(
    modifier: Modifier = Modifier,
    episodeCount: Int = 5
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // Left: Season info skeleton
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .padding(end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(32.dp)
                        .background(
                            brush = shimmerBrush(),
                            shape = RoundedCornerShape(6.dp)
                        )
                )

                // Show title skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .background(
                            brush = shimmerBrush(),
                            shape = RoundedCornerShape(4.dp)
                        )
                )

                Spacer(Modifier.height(8.dp))

                // Summary skeleton (4 lines)
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 3) 0.5f else 0.95f)
                            .height(16.dp)
                            .background(
                                brush = shimmerBrush(),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // Right: Episodes skeleton
            Column(
                modifier = Modifier.weight(0.65f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(episodeCount) {
                    EpisodeItemSkeleton()
                }
            }
        }
    }
}
