package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.feature.home.MediaCard

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
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is MediaDetailNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateToMediaDetail -> onNavigateToDetail(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateToSeason -> onNavigateToSeason(event.ratingKey, event.serverId)
                is MediaDetailNavigationEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    MediaDetailScreen(
        state = uiState,
        onAction = viewModel::onEvent
    )
}

@Composable
fun MediaDetailScreen(
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit
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
                    onAction = onAction
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
                 }
             )
        }
    }
}

@Composable
fun MediaDetailContent(
    media: MediaItem,
    seasons: List<MediaItem>,
    similarItems: List<MediaItem>,
    onAction: (MediaDetailEvent) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background Backdrop
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(media.artUrl ?: media.thumbUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Dark Overlay with Gradient for better readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.4f)
                        ),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    )
                )
        )

        // 2. Main Content Scrollable
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 64.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left Column: Poster + Ratings
            Column(
                modifier = Modifier
                    .width(200.dp) // Reduced from 240dp to make poster shorter
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f/3f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(media.thumbUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer
                
                // Ratings Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (media.rating != null && media.rating > 0) {
                        DetailRatingItem(
                            label = "Critics",
                            value = media.rating,
                            icon = Icons.Default.Star,
                            color = Color(0xFFFFD700)
                        )
                    }
                    if (media.audienceRating != null && media.audienceRating > 0) {
                        DetailRatingItem(
                            label = "Audience",
                            value = media.audienceRating,
                            icon = Icons.Default.Star,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer
                
                // Technical Badges
                com.chakir.plexhubtv.feature.details.components.TechnicalBadges(
                    media = media,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right Column: Info details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Title
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.headlineSmall, // Reduced from displaySmall
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Metadata Row
                Row(
                    modifier = Modifier.padding(vertical = 8.dp), // Reduced vertical padding
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    media.year?.let { 
                        Text(text = "$it", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.width(16.dp))
                    }
                    
                    media.durationMs?.let { 
                        val mins = it / 60000
                        Text(text = "${mins} min", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.width(16.dp))
                    }

                    if (!media.contentRating.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = media.contentRating.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    
                    if (!media.studio.isNullOrBlank()) {
                         Text(text = media.studio, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // IDs in Top Right (Subtle)
                    if (!media.imdbId.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "IMDB: ${media.imdbId}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (!media.tmdbId.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "TMDB: ${media.tmdbId}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp)) // Reduced spacer

                // Action Buttons
                ActionButtonsRow(media = media, onAction = onAction)

                Spacer(modifier = Modifier.height(16.dp)) // Reduced spacer

                // Synopsis
                Text(
                    text = "Synopsis", 
                    style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp)) // Reduced spacer
                Text(
                    text = media.summary ?: "No summary available.",
                    style = MaterialTheme.typography.bodyMedium, // Reduced from bodyLarge
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 4, // More compact
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                // Content specific rows (Seasons for TV)
                if (media.type == MediaType.Show && seasons.isNotEmpty()) {
                    Text(
                        text = "Seasons", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
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
                                subtitleStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Similar Items Row (More Like This)
                if (similarItems.isNotEmpty()) {
                    if (media.type == MediaType.Show && seasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = "More Like This", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(similarItems) { item ->
                            MediaCard(
                                media = item,
                                onClick = { onAction(MediaDetailEvent.OpenMediaDetail(item)) },
                                onPlay = {},
                                onFocus = {},
                                width = 100.dp,
                                height = 150.dp,
                                titleStyle = MaterialTheme.typography.labelMedium,
                                subtitleStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        
        // Back Button
        IconButton(
            onClick = { onAction(MediaDetailEvent.Back) },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
fun DetailRatingItem(label: String, value: Double, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun ActionButtonsRow(media: MediaItem, onAction: (MediaDetailEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // Slightly tighter spacing
        val playFocusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            try { playFocusRequester.requestFocus() } catch (e: Exception) {}
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
            colors = ButtonDefaults.buttonColors(
                containerColor = if (playFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                contentColor = if (playFocused) MaterialTheme.colorScheme.onPrimary else Color.White
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), // Compact padding
            modifier = Modifier
                .focusRequester(playFocusRequester)
                .onFocusChanged { playFocused = it.isFocused }
                .height(40.dp) // Fixed height to match IconButtons
                .scale(if (playFocused) 1.05f else 1f)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Play", style = MaterialTheme.typography.labelLarge)
        }
        
        // Watch Status
        var watchFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = { onAction(MediaDetailEvent.ToggleWatchStatus) },
            modifier = Modifier
                .size(40.dp) // Smaller from 48dp
                .onFocusChanged { watchFocused = it.isFocused }
                .background(
                    if (watchFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .scale(if (watchFocused) 1.1f else 1f)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Watch Status",
                tint = if (media.isWatched) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Favorite
        var favFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = { onAction(MediaDetailEvent.ToggleFavorite) },
            modifier = Modifier
                .size(40.dp) // Smaller
                .onFocusChanged { favFocused = it.isFocused }
                .background(
                    if (favFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
                    CircleShape
                )
                .scale(if (favFocused) 1.1f else 1f)
        ) {
             Icon(
                 imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                 contentDescription = "Favorite", 
                 tint = if (media.isFavorite) Color.Red else Color.White,
                 modifier = Modifier.size(20.dp)
             )
        }
    }
}

@Composable
fun SourceSelectionDialog(
    sources: List<com.chakir.plexhubtv.domain.model.MediaSource>,
    onDismiss: () -> Unit,
    onSourceSelected: (com.chakir.plexhubtv.domain.model.MediaSource) -> Unit
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore if not identifiable
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Server") },
        text = {
            LazyColumn {
                itemsIndexed(sources) { index, source ->
                    val modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                    
                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(source) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.serverName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            
                            val technicalInfo = listOfNotNull(
                                source.container?.uppercase(),
                                source.videoCodec?.uppercase(),
                                source.audioCodec?.uppercase(),
                                source.audioChannels?.let { "${it}ch" }
                            ).joinToString(" • ")
                            
                            if (technicalInfo.isNotEmpty()) {
                                Text(technicalInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            val languages = source.languages.joinToString(", ")
                            if (languages.isNotEmpty()) {
                                Text("Lang: $languages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }

                            source.fileSize?.let { size ->
                                val sizeInGb = size.toDouble() / (1024 * 1024 * 1024)
                                val sizeText = if (sizeInGb >= 1.0) String.format("%.2f GB", sizeInGb) else String.format("%.0f MB", size.toDouble() / (1024 * 1024))
                                Text(sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (source.hasHDR) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF9800), // Orange for HDR
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        "HDR",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }

                            if (source.resolution != null) {
                                 Surface(
                                     shape = RoundedCornerShape(4.dp),
                                     color = if (source.resolution.contains("4k", ignoreCase = true) || source.resolution.startsWith("2160")) 
                                                Color(0xFFE91E63) else MaterialTheme.colorScheme.primaryContainer
                                 ) {
                                     Text(
                                         source.resolution.uppercase(), 
                                         modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                         style = MaterialTheme.typography.labelSmall, 
                                         fontWeight = FontWeight.Bold,
                                         color = if (source.resolution.contains("4k", ignoreCase = true) || source.resolution.startsWith("2160")) 
                                                    Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                     )
                                 }
                            }
                        }
                    }
                    if (index < sources.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=720dp,dpi=72")
@Composable
fun PreviewMediaDetailMovie() {
    val movie = MediaItem(
        id = "1", ratingKey = "1", serverId = "s1", title = "Inception", type = MediaType.Movie, 
        year = 2010, summary = "A thief who steals corporate secrets through the use of dream-sharing technology...", 
        durationMs = 8880000, rating = 8.8, audienceRating = 9.1, contentRating = "PG-13", studio = "Warner Bros."
    )
    MediaDetailScreen(
        state = MediaDetailUiState(media = movie),
        onAction = {}
    )
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=720dp,dpi=72")
@Composable
fun PreviewMediaDetailShow() {
    val show = MediaItem(
        id = "2", ratingKey = "2", serverId = "s1", title = "Breaking Bad", type = MediaType.Show, 
        year = 2008, summary = "A high school chemistry teacher diagnosed with inoperable lung cancer...",
        rating = 9.5, audienceRating = 9.8, contentRating = "TV-MA", studio = "AMC"
    )
    val seasons = listOf(
        MediaItem(id = "s1", ratingKey = "s1", serverId = "s1", title = "Season 1", type = MediaType.Season, thumbUrl = ""),
        MediaItem(id = "s2", ratingKey = "s2", serverId = "s1", title = "Season 2", type = MediaType.Season, thumbUrl = "")
    )
    MediaDetailScreen(
        state = MediaDetailUiState(media = show, seasons = seasons),
        onAction = {}
    )
}
