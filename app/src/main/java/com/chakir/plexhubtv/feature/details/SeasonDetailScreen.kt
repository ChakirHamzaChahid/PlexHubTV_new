package com.chakir.plexhubtv.feature.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.chakir.plexhubtv.core.common.util.FormatUtils
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.details.components.SourceSelectionDialog

/**
 * Écran de détail pour une Saison — layout TV Netflix-style.
 * Backdrop plein écran avec dégradé, infos saison à gauche, liste épisodes à droite.
 * Navigation D-Pad avec focus initial sur le premier épisode.
 */
@Composable
fun SeasonDetailRoute(
    viewModel: SeasonDetailViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String, Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadsState by viewModel.downloadStates.collectAsState(initial = emptyMap())
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SeasonDetailNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId, event.startOffset)
                is SeasonDetailNavigationEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    SeasonDetailScreen(
        state = uiState,
        downloadStates = downloadsState,
        onAction = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun SeasonDetailScreen(
    state: SeasonDetailUiState,
    downloadStates: Map<String, DownloadState>,
    onAction: (SeasonDetailEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val episodeListFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onNavigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .testTag("screen_season_detail")
            .semantics { contentDescription = "Écran de détails de saison" }
    ) {
        // 1. Full-screen backdrop with gradient overlays
        val backdropUrl = state.season?.artUrl ?: state.season?.thumbUrl
        if (backdropUrl != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backdropUrl)
                        .crossfade(true)
                        .size(1920, 1080)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().zIndex(0f),
                )

                // Vertical gradient: transparent top → black bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NetflixBlack.copy(alpha = 0.5f),
                                    NetflixBlack.copy(alpha = 0.9f),
                                    NetflixBlack,
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            ),
                        )
                        .zIndex(1f),
                )

                // Horizontal gradient: black left → transparent right
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NetflixBlack.copy(alpha = 0.9f),
                                    NetflixBlack.copy(alpha = 0.5f),
                                    Color.Transparent,
                                ),
                                startX = 0f,
                                endX = 1500f,
                            ),
                        )
                        .zIndex(1f),
                )
            }
        }

        // 2. Content layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("season_loading")
                            .semantics { contentDescription = "Chargement des épisodes" },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                state.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("season_error")
                            .semantics { contentDescription = "Erreur: ${state.error}" },
                        contentAlignment = Alignment.Center,
                    ) {
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
                                color = Color.White,
                            )
                            Text(
                                state.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NetflixLightGray,
                            )
                        }
                    }
                }
                state.episodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("season_empty")
                            .semantics { contentDescription = "Aucun épisode trouvé" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = NetflixLightGray,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No episodes found",
                                style = MaterialTheme.typography.titleLarge,
                                color = NetflixLightGray,
                            )
                        }
                    }
                }
                else -> {
                    // Two-column layout: season info (left) + episodes (right)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp), // Overscan safe area
                    ) {
                        // Left column: season info
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .padding(end = 32.dp),
                        ) {
                            // Season title
                            Text(
                                text = state.season?.title ?: "Season Details",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                ),
                                color = Color.White,
                            )

                            // Show title (grandparent)
                            state.season?.grandparentTitle?.let { showTitle ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = showTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NetflixLightGray,
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Episode count
                            Text(
                                text = "${state.episodes.size} episodes",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = Color.White.copy(alpha = 0.7f),
                            )

                            // Summary
                            state.season?.summary?.let { summary ->
                                if (summary.isNotBlank()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 24.sp,
                                            fontSize = 16.sp,
                                        ),
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 6,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Action buttons
                            if (!state.isOfflineMode) {
                                Spacer(Modifier.height(24.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SeasonActionButton(
                                        icon = Icons.Filled.Check,
                                        label = "Mark Watched",
                                        onClick = { onAction(SeasonDetailEvent.MarkSeasonWatched) },
                                    )
                                    SeasonActionButton(
                                        icon = if (state.season?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        label = "Favorite",
                                        isFavorite = state.season?.isFavorite == true,
                                        onClick = { onAction(SeasonDetailEvent.ToggleFavorite) },
                                    )
                                }
                            }
                        }

                        // Right column: episode list
                        val listState = rememberLazyListState()
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.65f)
                                .focusRequester(episodeListFocusRequester)
                                .testTag("episodes_list")
                                .semantics { contentDescription = "Liste des épisodes" },
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
                                        color = Color.White.copy(alpha = 0.1f),
                                    )
                                }
                            }
                        }
                    }

                    // Request focus on the episode list once content is ready
                    LaunchedEffect(Unit) {
                        episodeListFocusRequester.requestFocus()
                    }
                }
            }

            // Loading Overlay for Source Resolution
            if (state.isResolvingSources) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .testTag("source_resolution_overlay")
                        .semantics { contentDescription = "Preparing playback" },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Preparing playback\u2026",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
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
private fun SeasonActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isFocused) Color.White else Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isFavorite) Color.Red else Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = if (isFocused) Color.White else NetflixLightGray,
        )
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
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "scale")
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode_item_${episode.ratingKey}")
            .semantics { contentDescription = "Épisode ${episode.episodeIndex}: ${episode.title}" }
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .then(
                if (isFocused) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Episode Thumbnail
        Box(
            modifier = Modifier
                .width(180.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.thumbUrl)
                    .size(360, 202)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
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
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                }
            }

            // Progress indicator at bottom
            if (hasProgress && episode.viewedStatus != "watched") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

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
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            "E$index",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Download status icon
                if (!isOfflineMode && downloadState != null) {
                    DownloadStatusIcon(downloadState)
                    Spacer(Modifier.width(8.dp))
                }

                // Episode title
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            fontSize = 14.sp,
                        ),
                        color = NetflixLightGray,
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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = NetflixLightGray,
                    )
                }

                if (!isOfflineMode && episode.viewedStatus == "watched") {
                    Text(
                        " \u2022 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixLightGray,
                    )
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Watched",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF66BB6A),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Watched",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = Color(0xFF66BB6A),
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
                Box(modifier = Modifier.size(14.dp)) {
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
            is DownloadState.Cancelled -> Icons.Filled.Cancel to NetflixLightGray
            else -> return
        }

    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = tint.copy(alpha = 0.7f),
    )
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
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
            summary = "The thrilling first season of an epic saga. Follow our heroes as they discover their destiny.",
            grandparentTitle = "Breaking Bad",
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
            onNavigateBack = {},
        )
    }
}
