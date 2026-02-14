package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import kotlinx.coroutines.delay

@Composable
fun NetflixHeroBillboard(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateIntervalMs: Long = 8000L,
    initialFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    buttonsFocusRequester: FocusRequester? = null, // For UP navigation from first hub
) {
    if (items.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = items[currentIndex]

    // Focus requesters for navigation flow
    val playButtonFocusRequester = buttonsFocusRequester ?: remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }

    // Auto-rotation logic
    LaunchedEffect(items, autoRotateIntervalMs) {
        if (items.size > 1) {
            while (true) {
                delay(autoRotateIntervalMs)
                currentIndex = (currentIndex + 1) % items.size
            }
        }
    }

    // Initial focus is now handled by the PARENT (NetflixHomeContent)
    // to avoid re-requesting focus when LazyColumn recycles this item.

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(450.dp) // Reduced height per design spec
    ) {
        // Background Image — Crossfade instead of AnimatedContent for less memory pressure
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(500),
            label = "HeroImageTransition"
        ) { media ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .crossfade(false) // Crossfade composable handles transition
                    .size(1920, 1080) // TV max resolution, not Size.ORIGINAL
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Billboard overlay removed — the large invisible focusable Box was causing
        // LazyColumn scroll conflicts when recycled off-screen.
        // Initial focus now goes directly to the Play button (like Netflix).

        // Gradient Overlay (Scrims)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NetflixBlack.copy(alpha = 0.3f),
                            NetflixBlack
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
                            NetflixBlack.copy(alpha = 0.9f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = 2000f
                    )
                )
        )

        // Content: Title + Metadata + Buttons — positioned at bottom with higher zIndex
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 40.dp)
                .fillMaxWidth(0.45f)
                .zIndex(1f) // Above billboard overlay
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

            // Buttons — Horizontal navigation with UP/DOWN handling
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .focusGroup()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> true // Stay on buttons
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    onNavigateDown?.invoke()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                NetflixPlayButton(
                    onClick = { onPlay(currentItem) },
                    modifier = Modifier.focusRequester(playButtonFocusRequester)
                )
                NetflixInfoButton(
                    onClick = { onInfo(currentItem) },
                    modifier = Modifier.focusRequester(infoButtonFocusRequester)
                )
            }
        }

        // Page Indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { index, _ ->
                val indicatorWidth = if (index == currentIndex) 24.dp else 8.dp
                val alpha = if (index == currentIndex) 1f else 0.5f

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(indicatorWidth)
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
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        interactionSource = interactionSource,
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier
            .border(2.dp, if (isFocused) Color.White else Color.Transparent)
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
            containerColor = if (isFocused) Color.Gray.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.5f),
            contentColor = Color.White
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .border(2.dp, if (isFocused) Color.White else Color.Transparent)
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

private val sampleMediaItems = listOf(
    MediaItem(
        id = "1",
        serverId = "1",
        ratingKey = "1",
        title = "PlexHubTV",
        summary = "PlexHubTV is a Plex client for Android TV.",
        artUrl = "https://github.com/Chakir-Amine/PlexHubTV/raw/main/fastlane/tv-banner.png",
        thumbUrl = "https://github.com/Chakir-Amine/PlexHubTV/raw/main/fastlane/tv-banner.png",
        year = 2023,
        contentRating = "G",
        type = MediaType.Movie
    ),
    MediaItem(
        id = "2",
        serverId = "1",
        ratingKey = "2",
        title = "Another Movie",
        summary = "This is another movie.",
        artUrl = "https://github.com/Chakir-Amine/PlexHubTV/raw/main/fastlane/tv-banner.png",
        thumbUrl = "https://github.com/Chakir-Amine/PlexHubTV/raw/main/fastlane/tv-banner.png",
        year = 2024,
        contentRating = "PG",
        type = MediaType.Movie
    )
)

@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
fun NetflixHeroBillboardPreview() {
    PlexHubTheme {
        NetflixHeroBillboard(
            items = sampleMediaItems,
            onPlay = {},
            onInfo = {}
        )
    }
}
