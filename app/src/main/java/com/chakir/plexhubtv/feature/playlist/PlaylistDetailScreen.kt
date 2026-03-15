package com.chakir.plexhubtv.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
import com.chakir.plexhubtv.feature.home.MediaCard

@Composable
fun PlaylistDetailRoute(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onNavigateBack)

    // Navigate back after successful delete
    LaunchedEffect(uiState.playlist) {
        if (!uiState.isLoading && uiState.playlist == null && uiState.error == null && !uiState.isDeleting) {
            onNavigateBack()
        }
    }

    PlaylistDetailScreen(
        state = uiState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    onEvent: (PlaylistDetailEvent) -> Unit,
) {
    val screenDesc = stringResource(R.string.playlist_detail_screen_description)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.playlist?.title ?: stringResource(R.string.playlist_title))
                        state.playlist?.let { playlist ->
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.playlist_item_count, playlist.items.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(PlaylistDetailEvent.DeletePlaylistClicked) },
                        enabled = !state.isDeleting,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.playlist_delete),
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("screen_playlist_detail")
                .semantics { contentDescription = screenDesc }
                .padding(paddingValues)
                .background(Color.Black),
        ) {
            if (state.isLoading) {
                LibraryGridSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("playlist_loading"),
                )
            } else if (state.error != null) {
                Text(
                    text = state.error,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("playlist_error"),
                )
            } else {
                state.playlist?.let { playlist ->
                    if (playlist.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("playlist_empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_detail_empty),
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            playlist.summary?.let { summary ->
                                if (summary.isNotBlank()) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                                        maxLines = 5,
                                    )
                                }
                            }

                            val gridState = rememberLazyGridState()
                            val gridFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(playlist.items.isNotEmpty()) {
                                if (playlist.items.isNotEmpty()) {
                                    gridFocusRequester.requestFocus()
                                }
                            }

                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 120.dp),
                                contentPadding = PaddingValues(48.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(gridFocusRequester)
                                    .testTag("playlist_items_list"),
                            ) {
                                items(
                                    items = playlist.items,
                                    key = { item -> "${item.serverId}_${item.ratingKey}" },
                                ) { item ->
                                    MediaCard(
                                        media = item,
                                        onClick = { onNavigateToDetail(item.ratingKey, item.serverId) },
                                        onPlay = {},
                                        onFocus = {},
                                        width = 120.dp,
                                        height = 180.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (state.showDeleteConfirmation && state.playlist != null) {
        AlertDialog(
            onDismissRequest = { onEvent(PlaylistDetailEvent.DismissDeleteDialog) },
            title = { Text(stringResource(R.string.playlist_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.playlist_delete_confirm_message, state.playlist.title))
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(PlaylistDetailEvent.ConfirmDeletePlaylist) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.playlist_delete_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onEvent(PlaylistDetailEvent.DismissDeleteDialog) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
