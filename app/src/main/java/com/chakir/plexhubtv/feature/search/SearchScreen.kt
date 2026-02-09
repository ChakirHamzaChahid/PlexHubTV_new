package com.chakir.plexhubtv.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType

/**
 * Écran de recherche global.
 * Permet de rechercher des médias sur tous les serveurs disponibles.
 */
@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is SearchNavigationEvent.NavigateToDetail -> onNavigateToDetail(event.ratingKey, event.serverId)
            }
        }
    }

    NetflixSearchScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            SearchBar(
                query = state.query,
                onQueryChange = { onAction(SearchAction.QueryChange(it)) },
                onSearch = { keyboardController?.hide() },
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search movies, shows...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onAction(SearchAction.ClearQuery) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                // Not using internal SearchBar results, managing manually below
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.searchState) {
                SearchState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Type to start searching", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SearchState.Searching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                SearchState.NoResults -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found for \"${state.query}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SearchState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: "Unknown Error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                SearchState.Results -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.results.forEach { resultItem ->
                            item {
                                SearchResultItem(item = resultItem, onClick = { onAction(SearchAction.OpenMedia(resultItem)) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    item: MediaItem,
    onClick: () -> Unit,
) {
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
                            MaterialTheme.shapes.medium,
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(width = 80.dp, height = 120.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            )
            item.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSearchIdle() {
    SearchScreen(state = SearchUiState(searchState = SearchState.Idle), onAction = {})
}

@Preview(showBackground = true)
@Composable
fun PreviewSearchLoading() {
    SearchScreen(state = SearchUiState(query = "Avatar", searchState = SearchState.Searching), onAction = {})
}

@Preview(showBackground = true)
@Composable
fun PreviewSearchResults() {
    val items =
        listOf(
            MediaItem(id = "1", ratingKey = "1", serverId = "s1", title = "Avatar", type = MediaType.Movie, year = 2009, thumbUrl = ""),
            MediaItem(
                id = "2",
                ratingKey = "2",
                serverId = "s1",
                title = "Avatar: The Way of Water",
                type = MediaType.Movie,
                year = 2022,
                thumbUrl = "",
            ),
        )
    SearchScreen(
        state = SearchUiState(query = "Avatar", searchState = SearchState.Results, results = items),
        onAction = {},
    )
}
