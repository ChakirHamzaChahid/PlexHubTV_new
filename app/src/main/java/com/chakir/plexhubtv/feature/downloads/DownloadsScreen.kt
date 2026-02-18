package com.chakir.plexhubtv.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType

/**
 * Écran de gestion des téléchargements.
 * Affiche la liste des contenus synchronisés localement pour une lcture hors-ligne.
 */
@Composable
fun DownloadsRoute(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val events = viewModel.navigationEvents

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is DownloadsNavigationEvent.NavigateToPlayer -> onNavigateToPlayer(event.ratingKey, event.serverId)
            }
        }
    }

    DownloadsScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Downloads") })
        },
        modifier = Modifier.padding(top = 56.dp), // Clear Netflix TopBar overlay
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .testTag("screen_downloads")
                .semantics { contentDescription = "Écran des téléchargements" }
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("downloads_loading")
                        .semantics { contentDescription = "Chargement des téléchargements" },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("downloads_empty")
                        .semantics { contentDescription = "Aucun téléchargement" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("No downloaded content.")
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .testTag("downloads_list")
                        .semantics { contentDescription = "Liste des téléchargements" }
                ) {
                    items(state.downloads, key = { it.id }) { item ->
                        DownloadItem(
                            item = item,
                            onClick = { onAction(DownloadsAction.PlayDownload(item)) },
                            onDelete = { onAction(DownloadsAction.DeleteDownload(item.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
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
                .testTag("download_item_${item.id}")
                .semantics { contentDescription = "Téléchargement: ${item.title}" }
                .onFocusChanged { isFocused = it.isFocused }
                .scale(scale)
                .background(if (isFocused) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                .then(
                    if (isFocused) {
                        Modifier.border(1.dp, borderColor, MaterialTheme.shapes.small)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.size(width = 100.dp, height = 56.dp),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (item.type == MediaType.Episode) "${item.parentTitle} - S${item.seasonIndex}:E${item.episodeIndex}" else item.year?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDownloadsScreen() {
    val items =
        listOf(
            MediaItem(id = "1", ratingKey = "1", serverId = "s1", title = "Movie 1", type = MediaType.Movie, year = 2022, thumbUrl = ""),
            MediaItem(id = "2", ratingKey = "2", serverId = "s1", title = "Show 1", type = MediaType.Episode, parentTitle = "Series 1", seasonIndex = 1, episodeIndex = 5, thumbUrl = ""),
        )
    DownloadsScreen(
        state = DownloadsUiState(downloads = items),
        onAction = {},
    )
}
