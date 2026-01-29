package com.chakir.plexhubtv.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType

@Composable
fun DownloadsRoute(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit
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
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onAction: (DownloadsAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Downloads") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloaded content.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.downloads) { item ->
                        DownloadItem(
                            item = item,
                            onClick = { onAction(DownloadsAction.PlayDownload(item)) },
                            onDelete = { onAction(DownloadsAction.DeleteDownload(item.id)) }
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
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.size(width = 100.dp, height = 56.dp),
            shape = MaterialTheme.shapes.small
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (item.type == MediaType.Episode) "${item.parentTitle} - S${item.seasonIndex}:E${item.episodeIndex}" else item.year?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val items = listOf(
        MediaItem(id = "1", ratingKey = "1", serverId = "s1", title = "Movie 1", type = MediaType.Movie, year = 2022, thumbUrl = ""),
        MediaItem(id = "2", ratingKey = "2", serverId = "s1", title = "Show 1", type = MediaType.Episode, parentTitle = "Series 1", seasonIndex = 1, episodeIndex = 5, thumbUrl = "")
    )
    DownloadsScreen(
        state = DownloadsUiState(downloads = items),
        onAction = {}
    )
}
