package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun MediaDetailRoute(
    viewModel: MediaDetailViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateToSeason: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is MediaDetailNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
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
    onAction: (MediaDetailEvent) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Header Image with Back Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.artUrl ?: media.thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                startY = 300f
                            )
                        )
                )

                IconButton(
                    onClick = { onAction(MediaDetailEvent.Back) },
                    modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    media.year?.let {
                        Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    media.durationMs?.let {
                         val minutes = it / 1000 / 60
                         Text(text = "${minutes} min", style = MaterialTheme.typography.bodyMedium)
                         Spacer(modifier = Modifier.width(16.dp))
                    }
                    // Metadata Row
                    if (!media.contentRating.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                            color = Color.Transparent
                        ) {
                            Text(
                                text = media.contentRating.uppercase(),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (media.rating != null && media.rating > 0) {
                        Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("%.1f", media.rating), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (media.audienceRating != null && media.audienceRating > 0) {
                        Icon(Icons.Default.Star, contentDescription = "Audience Rating", tint = Color(0xFFCD7F32), modifier = Modifier.size(16.dp)) // Bronze color for audience
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = String.format("%.1f", media.audienceRating), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
                
                // IDs Row
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!media.imdbId.isNullOrEmpty()) {
                        Text(text = "IMDB: ${media.imdbId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (!media.tmdbId.isNullOrEmpty()) {
                        Text(text = "TMDB: ${media.tmdbId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                // Technical Badges
                com.chakir.plexhubtv.feature.details.components.TechnicalBadges(
                    media = media,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            containerColor = if (playFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                            contentColor = if (playFocused) MaterialTheme.colorScheme.onPrimary else Color.White
                        ),
                        modifier = Modifier
                            .onFocusChanged { playFocused = it.isFocused }
                            .scale(if (playFocused) 1.1f else 1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    
                    var watchFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { onAction(MediaDetailEvent.ToggleWatchStatus) },
                        modifier = Modifier
                            .onFocusChanged { watchFocused = it.isFocused }
                            .scale(if (watchFocused) 1.1f else 1f),
                        border = BorderStroke(1.dp, if (watchFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(if (media.isWatched) Icons.Default.Check else Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }
                    
                    var favFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { onAction(MediaDetailEvent.ToggleFavorite) },
                        modifier = Modifier
                            .onFocusChanged { favFocused = it.isFocused }
                            .scale(if (favFocused) 1.1f else 1f),
                        border = BorderStroke(1.dp, if (favFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                    ) {
                         Icon(
                             imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                             contentDescription = null, 
                             tint = if (media.isFavorite) Color.Red else Color.White
                         )
                    }

                    var dlFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { onAction(MediaDetailEvent.DownloadClicked) },
                        modifier = Modifier
                            .onFocusChanged { dlFocused = it.isFocused }
                            .scale(if (dlFocused) 1.1f else 1f),
                        border = BorderStroke(1.dp, if (dlFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                    ) {
                         Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = media.summary ?: "No summary available.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Seasons Section
        if (seasons.isNotEmpty()) {
            item {
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(seasons) { season ->
                        MediaCard(
                            media = season,
                            onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                            onPlay = { /* Play season logic if needed */ },
                            onFocus = { /* Background update if needed */ }
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
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

@Preview(showBackground = true)
@Composable
fun PreviewMediaDetailMovie() {
    val movie = MediaItem(
        id = "1", ratingKey = "1", serverId = "s1", title = "Inception", type = MediaType.Movie, 
        year = 2010, summary = "A thief who steals corporate secrets through the use of dream-sharing technology...", durationMs = 8880000
    )
    MediaDetailScreen(
        state = MediaDetailUiState(media = movie),
        onAction = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewMediaDetailShow() {
    val show = MediaItem(
        id = "2", ratingKey = "2", serverId = "s1", title = "Breaking Bad", type = MediaType.Show, 
        year = 2008, summary = "A high school chemistry teacher diagnosed with inoperable lung cancer..."
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
