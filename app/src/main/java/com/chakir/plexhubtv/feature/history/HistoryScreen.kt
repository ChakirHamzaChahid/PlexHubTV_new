package com.chakir.plexhubtv.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.feature.home.MediaCard

/**
 * Écran affichant l'historique de visionnage.
 */
@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    HistoryScreen(
        uiState = uiState,
        onMediaClick = { media -> onNavigateToMedia(media.ratingKey, media.serverId) },
    )
}

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_history")
                .semantics { contentDescription = "Écran de l'historique" }
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 80.dp),
    ) {
        Text(
            text = "Watch History",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_loading")
                    .semantics { contentDescription = "Chargement de l'historique" },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_empty")
                    .semantics { contentDescription = "Aucun historique" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No history available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(top = 56.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = uiState.historyItems.size,
                    key = { index -> "${uiState.historyItems[index].serverId}_${uiState.historyItems[index].ratingKey}_$index" }
                ) { index ->
                    val media = uiState.historyItems[index]
                    MediaCard(
                        media = media,
                        onClick = { onMediaClick(media) },
                        onPlay = { /* Optional direct play */ },
                        onFocus = {},
                        width = 100.dp,
                        height = 150.dp,
                        titleStyle = MaterialTheme.typography.labelMedium,
                        subtitleStyle = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
