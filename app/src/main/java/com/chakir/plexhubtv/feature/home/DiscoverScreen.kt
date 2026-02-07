package com.chakir.plexhubtv.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.model.MediaItem
import kotlinx.coroutines.delay

/**
 * Écran d'accueil principal (Discover).
 * Affiche le contenu "On Deck" (en cours) et les "Hubs" (sections recommandées, récemment ajoutés, etc.).
 * Gère l'affichage de l'état de synchronisation initiale.
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToPlayer: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeNavigationEvent.NavigateToDetails -> onNavigateToDetails(event.ratingKey, event.serverId)
                is HomeNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
            }
        }
    }

    DiscoverScreen(
        state = uiState,
        onAction = viewModel::onAction
    )
}

@Composable
fun DiscoverScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background // Ensure dark background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            var backgroundUrl by remember { mutableStateOf<String?>(null) }
            
            // Background Layer
            AnimatedBackground(targetUrl = backgroundUrl ?: state.onDeck.firstOrNull()?.artUrl)

            when {
                state.isInitialSync && state.onDeck.isEmpty() && state.hubs.isEmpty() -> InitialSyncState(state.syncProgress, state.syncMessage)
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error) { onAction(HomeAction.Refresh) }
                state.onDeck.isEmpty() && state.hubs.isEmpty() -> EmptyState { onAction(HomeAction.Refresh) }
                else -> ContentState(
                    onDeck = state.onDeck, 
                    hubs = state.hubs, 
                    onAction = onAction,
                    onFocusMedia = { backgroundUrl = it.artUrl ?: it.thumbUrl }
                )
            }
        }
    }
}

@Composable
fun InitialSyncState(progress: Float, message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to PlexHubTV",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        if (progress > 0) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.width(300.dp).height(8.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${progress.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.ifBlank { "Initializing database..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
fun ContentState(
    onDeck: List<MediaItem>,
    hubs: List<Hub>,
    onAction: (HomeAction) -> Unit,
    onFocusMedia: (MediaItem) -> Unit
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero Carousel (On Deck)
        if (onDeck.isNotEmpty()) {
            item {
                // PERFORMANCE FIX: Memoize sorting/filtering to avoid Main Thread work on every frame
                val heroItems = remember(onDeck) { onDeck.take(25) }
                
                HeroCarousel(
                    items = heroItems, // Top 5 for Hero
                    onPlay = { media -> onAction(HomeAction.PlayMedia(media)) },
                    onDetails = { media -> onAction(HomeAction.OpenMedia(media)) },
                    onFocus = onFocusMedia
                )
            }
        }

        // Remaining On Deck (if any) as a normal row? 
        // Or just Hubs. For now, let's treat OnDeck strictly as Hero for the "Wow" factor.
        // Plezy uses OnDeck as the Hero.

        // Hubs Section
        items(
            hubs,
            key = { it.hubIdentifier ?: it.title ?: it.key },
            contentType = { "hub" }
        ) { hub ->
            Column {
                SectionHeader(hub.title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        hub.items,
                        key = { it.ratingKey },
                        contentType = { "media_card" }
                    ) { media ->
                        MediaCard(
                            media = media,
                            onClick = { onAction(HomeAction.OpenMedia(media)) },
                            onPlay = { onAction(HomeAction.PlayMedia(media)) },
                            onFocus = { onFocusMedia(media) }
                        )
                    }
                }
            }
        }
    }
}

// ... HeroCarousel ...

@Composable
fun MediaCard(
    media: MediaItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    height: androidx.compose.ui.unit.Dp = 210.dp,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    subtitleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "scale")
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.secondary else Color.Transparent, 
        label = "border"
    )

    Column(
        modifier = modifier
            .width(width)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .scale(scale)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    2.dp, borderColor, RoundedCornerShape(12.dp)
                )
        ) {
            // For episodes, prioritize series poster (grandparentThumb) over episode thumbnail
            val thumbUrls = remember(media.ratingKey) {
                when (media.type) {
                    MediaType.Episode -> {
                        // For episodes: grandparentThumb (series poster) > parentThumb (season poster) > thumbUrl (episode thumbnail)
                        listOfNotNull(
                            media.grandparentThumb,
                            media.parentThumb,
                            media.thumbUrl
                        ) + media.remoteSources.mapNotNull { it.thumbUrl }
                    }
                    else -> {
                        listOfNotNull(media.thumbUrl) + media.remoteSources.mapNotNull { it.thumbUrl }
                    }
                }
            }
            var currentUrlIndex by remember { mutableStateOf(0) }
            val currentUrl = thumbUrls.getOrNull(currentUrlIndex)

            if (currentUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentUrl)
                        .crossfade(false) // Performance: Disable crossfade
                        .listener(
                            onError = { _, _ ->
                                if (currentUrlIndex < thumbUrls.size - 1) {
                                    currentUrlIndex++
                                }
                            }
                        )
                        .build(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = coil.compose.rememberAsyncImagePainter(
                        model = android.R.drawable.ic_menu_gallery // Built-in fallback
                    )
                )
            }

            // Left Side: Content Rating Overlay
            if (!media.contentRating.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = media.contentRating,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // Right Side: Rating and Multi Indicator
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Rating Overlay
                val ratingValue = if (media.audienceRating != null && media.audienceRating > 0) media.audienceRating else media.rating
                val starColor = if (media.audienceRating != null && media.audienceRating > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary

                if (ratingValue != null && ratingValue > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = starColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", ratingValue),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Multi-Server Indicator
                if (media.remoteSources.size > 1) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MULTI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Progress Bar
            if ((media.playbackPositionMs ?: 0L) > 0 && media.durationMs != null) {
                // PERFORMANCE FIX: Memoize progress calculation to avoid overhead on every frame
                val progress by remember(media.playbackPositionMs, media.durationMs) {
                    mutableFloatStateOf(
                        ((media.playbackPositionMs ?: 0L).toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = media.title,
            style = titleStyle,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurface
        )
        // Secondary text
        val secondaryText = media.parentTitle ?: media.year?.toString()
        if (secondaryText != null) {
            Text(
                text = secondaryText,
                style = subtitleStyle,
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Error loading content", style = MaterialTheme.typography.headlineSmall)
        Text(text = message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiscoverScreenPreview() {
    val sampleMediaItem = MediaItem(
        id = "1",
        ratingKey = "1",
        serverId = "1",
        title = "The Mandalorian",
        parentTitle = "The Mandalorian",
        thumbUrl = "https://www.themoviedb.org/t/p/w600_and_h900_bestv2/5LpNvLhJcg8eJ8T2323hY74V2bA.jpg",
        artUrl = "https://www.themoviedb.org/t/p/w1920_and_h1080_bestv2/9ijMGlJKqcslswWUzPO0O61FqLh.jpg",
        type = MediaType.Episode,
        year = 2020,
        durationMs = 2400000,
        playbackPositionMs = 1200000
    )

    val sampleHub = Hub(
        key = "1",
        hubIdentifier = "1",
        title = "Recently Added",
        type = "movie",
        items = listOf(sampleMediaItem)
    )

    PlexHubTheme {
        DiscoverScreen(
            state = HomeUiState(
                onDeck = listOf(sampleMediaItem),
                hubs = listOf(sampleHub)
            ),
            onAction = {}
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun HeroCarousel(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    onFocus: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Continue Watching")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items, 
                key = { it.ratingKey }
            ) { item ->
                MediaCard(
                    media = item,
                    onClick = { onDetails(item) },
                    onPlay = { onPlay(item) },
                    onFocus = { onFocus(item) }
                )
            }
        }
    }
}

@Composable
fun AnimatedBackground(
    targetUrl: String?,
    modifier: Modifier = Modifier
) {
    // Simple placeholder
    if (targetUrl != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(targetUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.15f }
            )
            // Gradient Scrim for better readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            ),
                            startY = 0f,
                            endY = 1000f // Approximate
                        )
                    )
            )
        }
    }
}
