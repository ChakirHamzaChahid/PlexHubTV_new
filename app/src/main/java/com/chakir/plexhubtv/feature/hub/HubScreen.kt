package com.chakir.plexhubtv.feature.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.isRetryable
import com.chakir.plexhubtv.core.ui.CardType
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.HomeScreenSkeleton
import com.chakir.plexhubtv.core.ui.NetflixContentRow
import com.chakir.plexhubtv.core.ui.showError

@Composable
fun HubRoute(
    viewModel: HubViewModel = hiltViewModel(),
    onNavigateToDetails: (String, String) -> Unit,
    onNavigateToPlayer: (String, String) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents
    val errorEvents = viewModel.errorEvents
    val snackbarHostState = remember { SnackbarHostState() }

    val continueWatchingItems by remember {
        derivedStateOf { uiState.onDeck }
    }
    val hasContinueWatching by remember {
        derivedStateOf { continueWatchingItems.isNotEmpty() }
    }
    val hasMyList by remember {
        derivedStateOf { uiState.favorites.isNotEmpty() }
    }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HubNavigationEvent.NavigateToDetails -> onNavigateToDetails(event.ratingKey, event.serverId)
                is HubNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
            }
        }
    }

    LaunchedEffect(errorEvents) {
        errorEvents.collect { error ->
            val result = snackbarHostState.showError(error)
            if (result == SnackbarResult.ActionPerformed && error.isRetryable()) {
                viewModel.onAction(HubAction.Refresh)
            }
        }
    }

    HubScreen(
        state = uiState,
        continueWatchingItems = continueWatchingItems,
        hasContinueWatching = hasContinueWatching,
        hasMyList = hasMyList,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        onScrollStateChanged = onScrollStateChanged,
    )
}

@Composable
fun HubScreen(
    state: HubUiState,
    continueWatchingItems: List<MediaItem>,
    hasContinueWatching: Boolean,
    hasMyList: Boolean,
    onAction: (HubAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    onScrollStateChanged: (Boolean) -> Unit = {},
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
                state.isLoading -> HomeScreenSkeleton(modifier = Modifier.fillMaxSize())
                state.onDeck.isEmpty() && state.hubs.isEmpty() && state.favorites.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No content available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else ->
                    HubContent(
                        hubs = state.hubs,
                        favorites = state.favorites,
                        continueWatchingItems = continueWatchingItems,
                        hasContinueWatching = hasContinueWatching,
                        hasMyList = hasMyList,
                        onAction = onAction,
                        onScrollStateChanged = onScrollStateChanged,
                    )
            }
        }
    }
}

@Composable
fun HubContent(
    hubs: List<Hub>,
    favorites: List<MediaItem>,
    continueWatchingItems: List<MediaItem>,
    hasContinueWatching: Boolean,
    hasMyList: Boolean,
    onAction: (HubAction) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val firstRowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> onScrollStateChanged(index > 0) }
    }

    // Auto-focus first row on initial load
    LaunchedEffect(hasContinueWatching, hasMyList, hubs) {
        if (hasContinueWatching || hasMyList || hubs.isNotEmpty()) {
            try {
                firstRowFocusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_hub")
            .semantics { contentDescription = "Hub screen" },
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // TopBar Spacer
        item(key = "topbar_spacer") {
            Spacer(modifier = Modifier.height(72.dp))
        }

        // 1. Continue Watching Row
        if (hasContinueWatching) {
            item(key = "continue_watching") {
                NetflixContentRow(
                    title = "Continue Watching",
                    items = continueWatchingItems,
                    cardType = CardType.WIDE,
                    onItemClick = { onAction(HubAction.OpenMedia(it)) },
                    onItemPlay = { onAction(HubAction.PlayMedia(it)) },
                    rowId = "on_deck",
                    modifier = Modifier.focusRequester(firstRowFocusRequester)
                )
            }
        }

        // 2. My List (Favorites)
        if (hasMyList) {
            item(key = "my_list") {
                NetflixContentRow(
                    title = "My List",
                    items = favorites,
                    cardType = CardType.POSTER,
                    onItemClick = { onAction(HubAction.OpenMedia(it)) },
                    onItemPlay = { onAction(HubAction.PlayMedia(it)) },
                    rowId = "my_list",
                    modifier = if (!hasContinueWatching) Modifier.focusRequester(firstRowFocusRequester) else Modifier
                )
            }
        }

        // 3. Hub rows (Recently Added, Genres, etc.)
        hubs.forEachIndexed { index, hub ->
            item(key = hub.hubIdentifier ?: hub.title ?: hub.key ?: "hub_$index") {
                val isFirstItem = !hasContinueWatching && !hasMyList && index == 0
                NetflixContentRow(
                    title = hub.title ?: "",
                    items = hub.items,
                    cardType = CardType.POSTER,
                    onItemClick = { onAction(HubAction.OpenMedia(it)) },
                    onItemPlay = { onAction(HubAction.PlayMedia(it)) },
                    rowId = hub.hubIdentifier ?: hub.title?.lowercase()?.replace(" ", "_") ?: "hub_$index",
                    modifier = if (isFirstItem) Modifier.focusRequester(firstRowFocusRequester) else Modifier
                )
            }
        }

        // Bottom Spacer
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}
