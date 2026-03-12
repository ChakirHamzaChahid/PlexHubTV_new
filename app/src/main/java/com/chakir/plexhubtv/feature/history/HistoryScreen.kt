package com.chakir.plexhubtv.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
import com.chakir.plexhubtv.feature.home.MediaCard

import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.LoadState

/**
 * Écran affichant l'historique de visionnage.
 */
@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
) {
    val pagedItems = viewModel.pagedHistory.collectAsLazyPagingItems()

    HistoryScreen(
        pagedItems = pagedItems,
        onMediaClick = { media: com.chakir.plexhubtv.core.model.MediaItem -> onNavigateToMedia(media.ratingKey, media.serverId) },
    )
}

@Composable
fun HistoryScreen(
    pagedItems: LazyPagingItems<com.chakir.plexhubtv.core.model.MediaItem>,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
) {
    val screenDescription = stringResource(R.string.history_screen_description)
    val loadingDescription = stringResource(R.string.loading_please_wait)
    val emptyDescription = stringResource(R.string.history_empty_description)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_history")
                .semantics { contentDescription = screenDescription }
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 80.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (pagedItems.loadState.refresh is LoadState.Loading) {
            LibraryGridSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_loading")
                    .semantics { contentDescription = loadingDescription },
            )
        } else if (pagedItems.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_empty")
                    .semantics { contentDescription = emptyDescription },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.history_empty_hint),
                        style = typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            val gridFocusRequester = remember { FocusRequester() }

            // NAV-07: Request focus on first grid item when content loads
            LaunchedEffect(pagedItems.itemSnapshotList) {
                if (pagedItems.itemCount > 0) {
                    try { gridFocusRequester.requestFocus() } catch (_: Exception) { }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(top = 56.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().focusRequester(gridFocusRequester),
            ) {
                items(
                    count = pagedItems.itemCount,
                    key = { index -> 
                        val item = pagedItems[index]
                        if (item != null) "${item.serverId}_${item.ratingKey}_$index" else "placeholder_$index" 
                    }
                ) { index ->
                    val media = pagedItems[index]
                    if (media != null) {
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
}
