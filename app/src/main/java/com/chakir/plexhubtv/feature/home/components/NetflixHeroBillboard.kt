package com.chakir.plexhubtv.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NetflixHeroBillboard(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateIntervalMs: Long = 8000L,
) {
    if (items.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = items[currentIndex]

    // Auto-rotation logic
    LaunchedEffect(items, autoRotateIntervalMs) {
        if (items.size > 1) {
            while (true) {
                delay(autoRotateIntervalMs)
                currentIndex = (currentIndex + 1) % items.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(550.dp) // Large immersive height
    ) {
        // Background Image Crossfade
        AnimatedContent(
            targetState = currentItem,
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) with fadeOut(animationSpec = tween(500))
            },
            label = "HeroImageTransition"
        ) { media ->
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.artUrl ?: media.thumbUrl)
                        .crossfade(true)
                        .size(coil.size.Size.ORIGINAL) // Don't upscale
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Gradient Overlay (Scrims)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NetflixBlack.copy(alpha = 0.3f), // Mild tint middle
                            NetflixBlack // Solid black at bottom to blend with rows
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixBlack.copy(alpha = 0.9f), // Darker on left for text readability
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = 2000f
                    )
                )
        )

        // Content: Title + Metadata + Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 48.dp) // Alignment with content rows
                .fillMaxWidth(0.5f) // Take up left half
        ) {
            Text(
                text = currentItem.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = NetflixWhite
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Metadata Line
            Row(verticalAlignment = Alignment.CenterVertically) {
                currentItem.year?.let {
                    Text(text = it.toString(), color = NetflixWhite.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                currentItem.contentRating?.let {
                    Box(
                        modifier = Modifier
                            .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                         Text(
                            text = it, 
                            color = NetflixWhite,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                     Spacer(modifier = Modifier.width(12.dp))
                }
                // Duration?
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentItem.summary ?: "No description available.",
                style = MaterialTheme.typography.bodyLarge,
                color = NetflixWhite.copy(alpha = 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NetflixPlayButton(
                    onClick = { onPlay(currentItem) }
                )
                NetflixInfoButton(
                    onClick = { onInfo(currentItem) }
                )
            }
        }

        // Page Indicators (Right side or Bottom Right)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { index, _ ->
                val width = if (index == currentIndex) 24.dp else 8.dp // Static conditional
                val alpha = if (index == currentIndex) 1f else 0.5f
                
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
fun NetflixPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color.White else Color.White, // Always white
            contentColor = Color.Black
        ),
        interactionSource = interactionSource,
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier
            .padding(vertical = if (isFocused) 0.dp else 2.dp) // Subtle scale effect via padding
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Play", 
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NetflixInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
             containerColor = if (isFocused) Color.Gray.copy(alpha=0.8f) else Color.Gray.copy(alpha = 0.5f),
            contentColor = Color.White
        ),
        interactionSource = interactionSource,
        modifier = modifier
             .padding(vertical = if (isFocused) 0.dp else 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "More Info",
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "More Info",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
