package com.chakir.plexhubtv.feature.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.home.MediaCard

/**
 * Hub Detail Screen - Shows expanded view of a hub (e.g., Recently Added, Continue Watching)
 * Displays all items in a grid layout with filtering/sorting options
 */
@Composable
fun HubDetailRoute(
    viewModel: HubDetailViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    HubDetailScreen(
        state = uiState,
        onAction = viewModel::onEvent,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubDetailScreen(
    state: HubDetailUiState,
    onAction: (HubDetailEvent) -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.hub?.title ?: "Hub",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${state.items.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                        },
                    ) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle view mode",
                        )
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
                            Text(
                                "Error loading hub",
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
                state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No items found",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    if (viewMode == ViewMode.GRID) {
                        val gridState = rememberLazyGridState()
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = state.items,
                                key = { item -> "${item.serverId}_${item.ratingKey}" },
                            ) { item ->
                                val index = state.items.indexOf(item)
                                val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                                if (index == 0) {
                                    LaunchedEffect(Unit) { fr.requestFocus() }
                                }
                                MediaCard(
                                    media = item,
                                    onClick = { onNavigateToDetail(item.ratingKey, item.serverId) },
                                    onPlay = { /* Direct play not implemented yet */ },
                                    onFocus = { /* Optional background update */ },
                                    modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier
                                )
                            }
                        }
                    } else {
                        // List view implementation
                        val listState = rememberLazyGridState()
                        LazyVerticalGrid(
                            state = listState,
                            columns = GridCells.Fixed(1),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = state.items,
                                key = { item -> "${item.serverId}_${item.ratingKey}" },
                            ) { item ->
                                val index = state.items.indexOf(item)
                                val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                                if (index == 0) {
                                    LaunchedEffect(Unit) { fr.requestFocus() }
                                }
                                MediaCard(
                                    media = item,
                                    onClick = { onNavigateToDetail(item.ratingKey, item.serverId) },
                                    onPlay = { /* Direct play not implemented yet */ },
                                    onFocus = { /* Optional background update */ },
                                    modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHubDetailScreen() {
    val items =
        listOf(
            MediaItem(id = "1", ratingKey = "1", serverId = "s1", title = "Movie 1", type = MediaType.Movie, year = 2022, thumbUrl = ""),
            MediaItem(id = "2", ratingKey = "2", serverId = "s1", title = "Movie 2", type = MediaType.Movie, year = 2023, thumbUrl = ""),
        )
    val hub = Hub(key = "h1", hubIdentifier = "hi1", title = "Recently Added", type = "movie", items = items)

    HubDetailScreen(
        state = HubDetailUiState(hub = hub, items = items),
        onAction = {},
        onNavigateToDetail = { _, _ -> },
        onNavigateBack = {},
    )
}
