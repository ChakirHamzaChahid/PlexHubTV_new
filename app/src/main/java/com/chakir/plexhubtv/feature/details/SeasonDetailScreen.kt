package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.common.util.FormatUtils
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.details.components.SourceSelectionDialog

/**
 * Écran de détail pour une Saison.
 * Affiche la liste des épisodes avec leur statut de lecture et de téléchargement.
 * Permet de lancer la lecture d'un épisode spécifique.
 */
@Composable
fun SeasonDetailRoute(
    viewModel: SeasonDetailViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadsState by viewModel.downloadStates.collectAsState(initial = emptyMap())
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SeasonDetailNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
                is SeasonDetailNavigationEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    SeasonDetailScreen(
        state = uiState,
        downloadStates = downloadsState,
        onAction = viewModel::onEvent,
        onNavigateToPlayer = onNavigateToPlayer,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonDetailScreen(
    state: SeasonDetailUiState,
    downloadStates: Map<String, DownloadState>,
    onAction: (SeasonDetailEvent) -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.season?.title ?: "Season Details",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${state.episodes.size} episodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isOfflineMode) {
                        IconButton(onClick = { onAction(SeasonDetailEvent.ToggleFavorite) }) {
                            val isFav = state.season?.isFavorite == true
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onAction(SeasonDetailEvent.MarkSeasonWatched) }) {
                            Icon(Icons.Filled.Check, contentDescription = "Mark All Watched")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Error loading episodes",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                state.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.episodes.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No episodes found",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        itemsIndexed(state.episodes, key = { _, episode -> episode.ratingKey }) { index, episode ->
                                val downloadState = downloadStates[episode.ratingKey]
                                EnhancedEpisodeItem(
                                    episode = episode,
                                    downloadState = downloadState,
                                    isOfflineMode = state.isOfflineMode,
                                    onClick = {
                                        onAction(SeasonDetailEvent.PlayEpisode(episode))
                                    },
                                )
                                if (index < state.episodes.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                        }
                    }
                }
            }

            // Loading Overlay for Source Resolution
            if (state.isResolvingSources) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // Source Selection Dialog
            if (state.showSourceSelection && state.selectedEpisodeForSources?.remoteSources?.isNotEmpty() == true) {
                SourceSelectionDialog(
                    sources = state.selectedEpisodeForSources.remoteSources,
                    onDismiss = { onAction(SeasonDetailEvent.DismissSourceSelection) },
                    onSourceSelected = { source ->
                        onAction(SeasonDetailEvent.PlaySource(source))
                    },
                )
            }
        }
    }
}

@Composable
fun EnhancedEpisodeItem(
    episode: MediaItem,
    downloadState: DownloadState?,
    isOfflineMode: Boolean,
    onClick: () -> Unit,
) {
    val hasProgress = !isOfflineMode && episode.viewOffset > 0
    val progress =
        if (hasProgress) {
            (episode.viewOffset.toFloat() / (episode.durationMs ?: 1)).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val borderColor by androidx.compose.animation.animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .scale(scale)
                .background(if (isFocused) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                .then(
                    if (isFocused) {
                        Modifier.border(
                            1.dp,
                            borderColor,
                            RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Episode Thumbnail
        Box(
            modifier =
                Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(
                model = episode.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient overlay
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.2f),
                                    ),
                            ),
                        ),
            )

            // Play button overlay
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                    )
                }
            }

            // Progress indicator at bottom
            if (hasProgress && episode.viewedStatus != "watched") {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Episode Info
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Episode number and title with download status
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Episode number badge
                episode.episodeIndex?.let { index ->
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Text(
                            "E$index",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Download status icon
                if (!isOfflineMode && downloadState != null) {
                    DownloadStatusIcon(downloadState)
                    Spacer(Modifier.width(6.dp))
                }

                // Episode title
                Text(
                    episode.title,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            // Summary
            episode.summary?.let { summary ->
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        summary,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 18.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Metadata row (duration, watched status)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                episode.durationMs?.let { duration ->
                    Text(
                        FormatUtils.formatDuration(duration),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isOfflineMode && episode.viewedStatus == "watched") {
                    Text(
                        " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Watched ✓",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadStatusIcon(state: DownloadState) {
    val (icon, tint) =
        when (state) {
            is DownloadState.Queued -> Icons.Filled.Schedule to MaterialTheme.colorScheme.tertiary
            is DownloadState.Downloading -> {
                // Show circular progress
                Box(modifier = Modifier.size(12.dp)) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    )
                }
                return
            }
            is DownloadState.Paused -> Icons.Filled.PauseCircleOutline to Color(0xFFFFA726)
            is DownloadState.Completed -> Icons.Filled.FileDownloadDone to Color(0xFF66BB6A)
            is DownloadState.Failed -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
            is DownloadState.Cancelled -> Icons.Filled.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
            else -> return
        }

    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = tint.copy(alpha = 0.7f),
    )
}

@Preview(showBackground = true)
@Composable
fun SeasonDetailScreenPreview() {
    val sampleEpisodes =
        listOf(
            MediaItem(
                id = "1",
                ratingKey = "1",
                serverId = "1",
                title = "Episode 1: The Beginning",
                type = MediaType.Episode,
                summary = "This is the first episode of the season, where it all begins. A lot of exciting things happen and characters are introduced.",
                thumbUrl = "https://via.placeholder.com/160x90",
                episodeIndex = 1,
                durationMs = 2400000,
                viewOffset = 1200000,
            ),
            MediaItem(
                id = "2",
                ratingKey = "2",
                serverId = "1",
                title = "Episode 2: The Middle",
                type = MediaType.Episode,
                summary = "The story continues and the plot thickens. Something unexpected happens.",
                thumbUrl = "https://via.placeholder.com/160x90",
                episodeIndex = 2,
                durationMs = 2400000,
                viewedStatus = "watched",
            ),
            MediaItem(
                id = "3",
                ratingKey = "3",
                serverId = "1",
                title = "Episode 3: The End",
                type = MediaType.Episode,
                summary = "The season finale. Everything comes to a conclusion, or does it? A cliffhanger for the next season.",
                thumbUrl = "https://via.placeholder.com/160x90",
                episodeIndex = 3,
                durationMs = 2400000,
            ),
        )

    val sampleDownloadStates =
        mapOf(
            "1" to DownloadState.Downloading(0.5f),
            "2" to DownloadState.Completed,
            "3" to DownloadState.Queued,
        )

    val sampleSeason =
        MediaItem(
            id = "season1",
            ratingKey = "season1",
            serverId = "1",
            title = "Season 1",
            type = MediaType.Season,
        )

    MaterialTheme {
        SeasonDetailScreen(
            state =
                SeasonDetailUiState(
                    season = sampleSeason,
                    episodes = sampleEpisodes,
                    isLoading = false,
                    error = null,
                ),
            downloadStates = sampleDownloadStates,
            onAction = {},
            onNavigateToPlayer = { _, _ -> },
            onNavigateBack = {},
        )
    }
}
