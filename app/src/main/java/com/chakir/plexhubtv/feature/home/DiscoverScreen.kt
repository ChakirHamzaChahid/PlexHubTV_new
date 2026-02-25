package com.chakir.plexhubtv.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.isRetryable
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.HomeScreenSkeleton
import com.chakir.plexhubtv.core.ui.NetflixMediaCard
import com.chakir.plexhubtv.core.ui.showError

// Backward compatibility wrapper
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
    subtitleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    NetflixMediaCard(
        media = media,
        onClick = onClick,
        onPlay = onPlay,
        onFocus = { isFocused -> if (isFocused) onFocus() },
        modifier = modifier
    )
}


/**
 * Écran d'accueil principal.
 * Affiche uniquement le Hero Billboard avec les items On Deck.
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents
    val errorEvents = viewModel.errorEvents
    val snackbarHostState = remember { SnackbarHostState() }

    val heroItems by remember {
        derivedStateOf { uiState.onDeck.take(10) }
    }

    // Handle navigation events
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeNavigationEvent.NavigateToDetails -> onNavigateToDetails(event.ratingKey, event.serverId)
                is HomeNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
            }
        }
    }

    // Handle error events with centralized error display
    LaunchedEffect(errorEvents) {
        errorEvents.collect { error ->
            val result = snackbarHostState.showError(error)
            if (result == SnackbarResult.ActionPerformed && error.isRetryable()) {
                viewModel.onAction(HomeAction.Refresh)
            }
        }
    }

    DiscoverScreen(
        state = uiState,
        heroItems = heroItems,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun DiscoverScreen(
    state: HomeUiState,
    heroItems: List<MediaItem>,
    onAction: (HomeAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { ErrorSnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isInitialSync && state.onDeck.isEmpty() ->
                    InitialSyncState(
                        state.syncProgress,
                        state.syncMessage,
                    )
                state.isLoading -> LoadingState()
                state.onDeck.isEmpty() -> EmptyState { onAction(HomeAction.Refresh) }
                else ->
                    NetflixHomeContent(
                        heroItems = heroItems,
                        onAction = onAction,
                    )
            }
        }
    }
}

@Composable
fun LoadingState() {
    HomeScreenSkeleton(
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error loading content",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun InitialSyncState(
    progress: Float,
    message: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to PlexHubTV",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                fontWeight = FontWeight.Bold,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.ifBlank { "Initializing database..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AnimatedBackground(
    targetUrl: String?,
    modifier: Modifier = Modifier,
) {
    // Simple placeholder
    if (targetUrl != null) {
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(targetUrl)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.15f },
            )
            // Gradient Scrim for better readability
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background,
                                    ),
                                startY = 0f,
                                endY = 1000f, // Approximate
                            ),
                        ),
            )
        }
    }
}
