package com.chakir.plexhubtv.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.ui.R
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
fun NetflixHeroBillboard(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateIntervalMs: Long = 12000L,
    initialFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    buttonsFocusRequester: FocusRequester? = null, // For UP navigation from first hub
    backdropColors: BackdropColors = BackdropColors(),
) {
    if (items.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = items[currentIndex]
    var isVisibleOnScreen by remember { mutableStateOf(true) }

    // Focus requesters for navigation flow
    val playButtonFocusRequester = buttonsFocusRequester ?: remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }

    // Auto-rotation logic — stops when scrolled off-screen or app in background
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(items, autoRotateIntervalMs, isVisibleOnScreen) {
        if (!isVisibleOnScreen || items.size <= 1) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                isVisibleOnScreen = !bounds.isEmpty && bounds.bottom > 0f
            }
            .testTag("hero_section")
            .semantics { contentDescription = "Film à la une: ${currentItem.title}" }
    ) {
        // Background Image — Crossfade instead of AnimatedContent for less memory pressure
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(500),
            label = stringResource(R.string.hero_transition_label)
        ) { media ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .size(1280, 720) // Downscaled for GPU-limited TV devices (sufficient for 1080p with Crop)
                    .build(),
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hero_backdrop_${media.ratingKey}")
            )
        }

        // Billboard overlay removed — the large invisible focusable Box was causing
        // LazyColumn scroll conflicts when recycled off-screen.
        // Initial focus now goes directly to the Play button (like Netflix).

        // === CINEMA GOLD REFONTE ===
        val cs = MaterialTheme.colorScheme
        val gradientBase = if (backdropColors.isDefault) cs.background else backdropColors.secondary
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            gradientBase.copy(alpha = 0.3f),
                            gradientBase
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
                            gradientBase.copy(alpha = 0.9f),
                            if (backdropColors.isDefault) Color.Transparent else backdropColors.primary,
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
            // Genre / Metadata tagline in accent color
            Row(verticalAlignment = Alignment.CenterVertically) {
                val metaParts = buildList {
                    currentItem.contentRating?.let { add(it.uppercase()) }
                    currentItem.year?.let { add(it.toString()) }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = cs.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentItem.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = cs.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentItem.summary ?: "No description available.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onBackground.copy(alpha = 0.9f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons — Horizontal navigation with UP/DOWN handling
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.focusGroup()
            ) {
                NetflixPlayButton(
                    onClick = { onPlay(currentItem) },
                    onNavigateDown = onNavigateDown,
                    onNavigateUp = onNavigateUp,
                    modifier = Modifier
                        .focusRequester(playButtonFocusRequester)
                        .testTag("hero_play_button")
                )
                NetflixInfoButton(
                    onClick = { onInfo(currentItem) },
                    onNavigateDown = onNavigateDown,
                    onNavigateUp = onNavigateUp,
                    onNextHero = if (items.size > 1) {
                        { currentIndex = (currentIndex + 1) % items.size }
                    } else null,
                    modifier = Modifier
                        .focusRequester(infoButtonFocusRequester)
                        .testTag("hero_info_button")
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
                val isActive = index == currentIndex
                val indicatorWidth = if (isActive) 24.dp else 8.dp
                val dotColor = if (isActive) cs.primary else cs.outline

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(indicatorWidth)
                        .clip(RoundedCornerShape(4.dp))
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
fun NetflixPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val playDescription = stringResource(R.string.hero_play_description)
    val playText = stringResource(R.string.hero_play_button)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) cs.primary else cs.primary.copy(alpha = 0.9f),
            contentColor = cs.onPrimary
        ),
        interactionSource = interactionSource,
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            onNavigateUp?.invoke()
                            onNavigateUp != null
                        }
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
            .semantics { contentDescription = playDescription }
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = playText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NetflixInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNextHero: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val moreInfoDescription = stringResource(R.string.hero_more_info_description)
    val moreInfoText = stringResource(R.string.hero_more_info_button)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) cs.onBackground else Color.Transparent,
            contentColor = if (isFocused) cs.background else cs.onSurfaceVariant
        ),
        border = if (!isFocused) androidx.compose.foundation.BorderStroke(1.dp, cs.outline) else null,
        interactionSource = interactionSource,
        modifier = modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            onNavigateUp?.invoke()
                            onNavigateUp != null
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onNavigateDown?.invoke()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onNextHero?.invoke()
                            onNextHero != null
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .semantics { contentDescription = moreInfoDescription }
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = moreInfoText,
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
