package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.details.components.SourceSelectionDialog
import com.chakir.plexhubtv.feature.home.MediaCard
import timber.log.Timber

/**
 * Écran de détail principal pour les médias (Films, Séries).
 * Affiche les métadonnées riches, le casting, et les options de lecture.
 * Pour les séries, affiche la liste des saisons.
 * Gère aussi la sélection de source (résolution de conflits multi-serveurs).
 */
@Composable
fun MediaDetailRoute(
    viewModel: MediaDetailViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateToSeason: (String, String) -> Unit,
    onNavigateToCollection: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is MediaDetailNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateToMediaDetail -> onNavigateToDetail(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateToSeason -> onNavigateToSeason(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateToCollection -> onNavigateToCollection(event.collectionId, event.serverId)
                is MediaDetailNavigationEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    MediaDetailScreen(
        state = uiState,
        onAction = viewModel::onEvent,
        onCollectionClicked = viewModel::onCollectionClicked,
    )
}

@Composable
fun MediaDetailScreen(
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
    onCollectionClicked: (String, String) -> Unit,
) {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onAction(MediaDetailEvent.Retry) }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (state.media != null) {
                MediaDetailContent(
                    media = state.media,
                    seasons = state.seasons,
                    similarItems = state.similarItems,
                    state = state, // Pass full state to access collection
                    onAction = onAction,
                    onCollectionClicked = onCollectionClicked,
                )
            }
        }

        val sourceMedia = state.selectedPlaybackItem ?: state.media
        if (state.showSourceSelection && sourceMedia?.remoteSources?.isNotEmpty() == true) {
            SourceSelectionDialog(
                sources = sourceMedia.remoteSources,
                onDismiss = { onAction(MediaDetailEvent.DismissSourceSelection) },
                onSourceSelected = { source ->
                    onAction(MediaDetailEvent.PlaySource(source))
                },
            )
        }
    }
}

@Composable
fun MediaDetailContent(
    media: MediaItem,
    seasons: List<MediaItem>,
    similarItems: List<MediaItem>,
    state: MediaDetailUiState, // Added this
    onAction: (MediaDetailEvent) -> Unit,
    onCollectionClicked: (String, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background Backdrop
        AsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(media.artUrl ?: media.thumbUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Dark Overlay with Gradient for better readability
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color.Black,
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black.copy(alpha = 0.4f),
                                ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY,
                        ),
                    ),
        )

        // 2. Main Content Scrollable (TvLazyColumn for TV D-pad navigation)
        // 2. Main Content Scrollable (LazyColumn for TV D-pad navigation)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 64.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    // Left Column: Poster + Ratings
                    Column(
                        modifier =
                            Modifier
                                .width(200.dp) // Reduced from 240dp to make poster shorter
                                .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(media.thumbUrl)
                                        .crossfade(true)
                                        .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer

                        // Ratings Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            val rating = media.rating
                            if (rating != null && rating > 0) {
                                DetailRatingItem(
                                    label = "Critics",
                                    value = rating,
                                    icon = Icons.Default.Star,
                                    color = Color(0xFFFFD700),
                                )
                            }
                            val audienceRating = media.audienceRating
                            if (audienceRating != null && audienceRating > 0) {
                                DetailRatingItem(
                                    label = "Audience",
                                    value = audienceRating,
                                    icon = Icons.Default.Star,
                                    color = Color(0xFFFF9800),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer

                        // Technical Badges
                        com.chakir.plexhubtv.feature.details.components.TechnicalBadges(
                            media = media,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Right Column: Info details
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    ) {
                        // Title
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.headlineSmall, // Reduced from displaySmall
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )

                        // Metadata Row
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp), // Reduced vertical padding
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            media.year?.let {
                                Text(text = "$it", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.7f))
                                Spacer(Modifier.width(16.dp))
                            }

                            media.durationMs?.let {
                                val mins = it / 60000
                                Text(
                                    text = "$mins min",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.width(16.dp))
                            }

                            media.contentRating?.let { rating ->
                                if (rating.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    ) {
                                        Text(
                                            text = rating.uppercase(),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                }
                            }

                            media.studio?.let { studio ->
                                if (studio.isNotBlank()) {
                                    Text(
                                        text = studio,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // IDs in Top Right (Subtle)
                            if (!media.imdbId.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                ) {
                                    Text(
                                        text = "IMDB: ${media.imdbId}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (!media.tmdbId.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                ) {
                                    Text(
                                        text = "TMDB: ${media.tmdbId}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        // Available Servers (Discrete)
                        if (media.remoteSources.isNotEmpty()) {
                            val uniqueServers = media.remoteSources.map { it.serverName }.distinct().sorted()
                            Text(
                                text = "Available on: " + uniqueServers.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp)) // Reduced spacer

                        // Action Buttons
                        ActionButtonsRow(media = media, state = state, onAction = onAction)

                        Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer

                        // Synopsis
                        Text(
                            text = "Synopsis",
                            style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp)) // Reduced spacer
                        Text(
                            text = media.summary ?: "No summary available.",
                            style = MaterialTheme.typography.bodyMedium, // Reduced from bodyLarge
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 4, // More compact
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )

                        // Content specific rows (Seasons for TV)
                        if (media.type == MediaType.Show && seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(seasons) { season ->
                                    MediaCard(
                                        media = season,
                                        onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                                        onPlay = {},
                                        onFocus = {},
                                        width = 100.dp,
                                        height = 150.dp,
                                        titleStyle = MaterialTheme.typography.labelMedium,
                                        subtitleStyle = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }

                        // Collections Section
                        if (state.collections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Collections",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(state.collections) { collection ->
                                    Surface(
                                        onClick = { onCollectionClicked(collection.id, collection.serverId) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = collection.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White,
                                            )
                                            Text(
                                                text = "(${collection.items.size} items)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.6f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Similar Items Row (More Like This)
                        if (similarItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "More Like This",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(similarItems) { similarItem ->
                                    MediaCard(
                                        media = similarItem,
                                        onClick = { onAction(MediaDetailEvent.OpenMediaDetail(similarItem)) },
                                        onPlay = {},
                                        onFocus = {},
                                        width = 100.dp,
                                        height = 150.dp,
                                        titleStyle = MaterialTheme.typography.labelMedium,
                                        subtitleStyle = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Back Button
        IconButton(
            onClick = { onAction(MediaDetailEvent.Back) },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
fun DetailRatingItem(
    label: String,
    value: Double,
    icon: ImageVector,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun ActionButtonsRow(
    media: MediaItem,
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // Slightly tighter spacing
        val playFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            try {
                playFocusRequester.requestFocus()
            } catch (e: Exception) {
                Timber.e(e, "Failed to request focus")
            }
        }

        var playFocused by remember { mutableStateOf(false) }
        Button(
            onClick = {
                if (media.remoteSources.size > 1) {
                    onAction(MediaDetailEvent.ShowSourceSelection)
                } else {
                    onAction(MediaDetailEvent.PlayClicked)
                }
            },
            enabled = !state.isEnriching, // Disable while enriching
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (playFocused) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.15f),
                    contentColor = if (playFocused) MaterialTheme.colorScheme.onSecondary else Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f),
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), // Compact padding
            modifier =
                Modifier
                    .focusRequester(playFocusRequester)
                    .onFocusChanged { playFocused = it.isFocused }
                    .height(40.dp) // Fixed height to match IconButtons
                    .scale(if (playFocused) 1.05f else 1f),
        ) {
            if (state.isEnriching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                // Text("Loading...", style = MaterialTheme.typography.labelLarge) // Optional text
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("Play", style = MaterialTheme.typography.labelLarge)
        }

        // Watch Status
        var watchFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = { onAction(MediaDetailEvent.ToggleWatchStatus) },
            modifier =
                Modifier
                    .size(40.dp) // Smaller from 48dp
                    .onFocusChanged { watchFocused = it.isFocused }
                    .background(
                        if (watchFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
                        CircleShape,
                    )
                    .scale(if (watchFocused) 1.1f else 1f),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Watch Status",
                tint = if (media.isWatched) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Favorite
        var favFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = { onAction(MediaDetailEvent.ToggleFavorite) },
            modifier =
                Modifier
                    .size(40.dp) // Smaller
                    .onFocusChanged { favFocused = it.isFocused }
                    .background(
                        if (favFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
                        CircleShape,
                    )
                    .scale(if (favFocused) 1.1f else 1f),
        ) {
            Icon(
                imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (media.isFavorite) MaterialTheme.colorScheme.error else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=720dp,dpi=72")
@Composable
fun PreviewMediaDetailMovie() {
    val movie =
        MediaItem(
            id = "1", ratingKey = "1", serverId = "s1", title = "Inception", type = MediaType.Movie,
            year = 2010, summary = "A thief who steals corporate secrets through the use of dream-sharing technology...",
            durationMs = 8880000, rating = 8.8, audienceRating = 9.1, contentRating = "PG-13", studio = "Warner Bros.",
        )
    MediaDetailScreen(
        state = MediaDetailUiState(media = movie),
        onAction = {},
        onCollectionClicked = { _, _ -> },
    )
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=720dp,dpi=72")
@Composable
fun PreviewMediaDetailShow() {
    val show =
        MediaItem(
            id = "2", ratingKey = "2", serverId = "s1", title = "Breaking Bad", type = MediaType.Show,
            year = 2008, summary = "A high school chemistry teacher diagnosed with inoperable lung cancer...",
            rating = 9.5, audienceRating = 9.8, contentRating = "TV-MA", studio = "AMC",
        )
    val seasons =
        listOf(
            MediaItem(id = "s1", ratingKey = "s1", serverId = "s1", title = "Season 1", type = MediaType.Season, thumbUrl = ""),
            MediaItem(id = "s2", ratingKey = "s2", serverId = "s1", title = "Season 2", type = MediaType.Season, thumbUrl = ""),
        )
    MediaDetailScreen(
        state = MediaDetailUiState(media = show, seasons = seasons),
        onAction = {},
        onCollectionClicked = { _, _ -> },
    )
}
