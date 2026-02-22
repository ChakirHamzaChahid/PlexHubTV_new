package com.chakir.plexhubtv.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixDarkGray
import com.chakir.plexhubtv.core.designsystem.NetflixLightGray
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.isRetryable
import com.chakir.plexhubtv.core.ui.DetailHeroSkeleton
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.showError
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
    val snackbarHostState = remember { SnackbarHostState() }

    val events = viewModel.navigationEvents
    val errorEvents = viewModel.errorEvents

    // Handle navigation events
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

    // Handle error events with centralized error display
    LaunchedEffect(errorEvents) {
        errorEvents.collect { error ->
            val result = snackbarHostState.showError(error)
            if (result == SnackbarResult.ActionPerformed && error.isRetryable()) {
                viewModel.onEvent(MediaDetailEvent.Retry)
            }
        }
    }

    MediaDetailScreen(
        state = uiState,
        onAction = viewModel::onEvent,
        onCollectionClicked = viewModel::onCollectionClicked,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun MediaDetailScreen(
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
    onCollectionClicked: (String, String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val detailScreenDesc = stringResource(R.string.detail_screen_description)
    Scaffold(
        snackbarHost = { ErrorSnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("screen_media_detail")
                .semantics { contentDescription = detailScreenDesc }
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.isLoading) {
                DetailHeroSkeleton(
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.media != null) {
                NetflixDetailScreen(
                    media = state.media,
                    seasons = state.seasons,
                    similarItems = state.similarItems,
                    state = state,
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
fun ActionButtonsRow(
    media: MediaItem,
    state: MediaDetailUiState,
    onAction: (MediaDetailEvent) -> Unit,
    playButtonFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val playLoadingDesc = stringResource(R.string.detail_loading_description)
    val playDesc = stringResource(R.string.detail_play_description)
    val removeFavDesc = stringResource(R.string.detail_remove_favorite_description)
    val addFavDesc = stringResource(R.string.detail_add_favorite_description)
    val watchStatusDesc = stringResource(R.string.detail_watch_status_description)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play Button
        var isPlayFocused by remember { mutableStateOf(false) }

        Button(
            onClick = { onAction(MediaDetailEvent.PlayClicked) },
            enabled = !state.isPlayButtonLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlayFocused) MaterialTheme.colorScheme.primary else Color.White,
                contentColor = if (isPlayFocused) MaterialTheme.colorScheme.onPrimary else Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.5f),
                disabledContentColor = Color.Black.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(40.dp)
                .testTag("play_button")
                .semantics { contentDescription = if (state.isPlayButtonLoading) "Chargement..." else "Lancer la lecture" }
                .scale(if (isPlayFocused) 1.05f else 1f)
                .then(if (playButtonFocusRequester != null) Modifier.focusRequester(playButtonFocusRequester) else Modifier)
                .onFocusChanged { isPlayFocused = it.isFocused },
        ) {
            if (state.isPlayButtonLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = if (isPlayFocused) MaterialTheme.colorScheme.onPrimary else Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Download Button
        var isDownloadFocused by remember { mutableStateOf(false) }

        Button(
            onClick = { onAction(MediaDetailEvent.DownloadClicked) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDownloadFocused) NetflixLightGray else NetflixDarkGray,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(40.dp)
                .scale(if (isDownloadFocused) 1.05f else 1f)
                .onFocusChanged { isDownloadFocused = it.isFocused },
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        // Watch Status
        var watchFocused by remember { mutableStateOf(false) }
        IconButton(
            onClick = { onAction(MediaDetailEvent.ToggleWatchStatus) },
            modifier =
                Modifier
                    .size(40.dp) // Smaller
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
                    .testTag("favorite_button")
                    .semantics { contentDescription = if (media.isFavorite) "Retirer des favoris" else "Ajouter aux favoris" }
                    .onFocusChanged { favFocused = it.isFocused }
                    .background(
                        if (favFocused) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f),
                        CircleShape,
                    )
                    .scale(if (favFocused) 1.1f else 1f),
        ) {
            Icon(
                imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
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
        snackbarHostState = remember { SnackbarHostState() },
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
        snackbarHostState = remember { SnackbarHostState() },
    )
}
