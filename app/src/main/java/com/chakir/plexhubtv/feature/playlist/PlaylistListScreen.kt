package com.chakir.plexhubtv.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
import coil3.compose.AsyncImage

@Composable
fun PlaylistListRoute(
    viewModel: PlaylistListViewModel = hiltViewModel(),
    onNavigateToPlaylist: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    PlaylistListScreen(
        uiState = uiState,
        onPlaylistClick = { playlist -> onNavigateToPlaylist(playlist.id, playlist.serverId) },
    )
}

@Composable
fun PlaylistListScreen(
    uiState: PlaylistListUiState,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val screenDescription = stringResource(R.string.playlist_screen_description)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_playlists")
            .semantics { contentDescription = screenDescription }
            .background(NetflixBlack)
            .padding(start = 58.dp, end = 58.dp, top = 80.dp),
    ) {
        Text(
            text = stringResource(R.string.playlist_title_with_count, uiState.playlists.size),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            LibraryGridSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playlists_loading"),
            )
        } else if (uiState.playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playlists_empty"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.playlist_empty),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.playlist_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            val gridFocusRequester = remember { FocusRequester() }

            LaunchedEffect(uiState.playlists) {
                if (uiState.playlists.isNotEmpty()) {
                    try { gridFocusRequester.requestFocus() } catch (_: Exception) { }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().focusRequester(gridFocusRequester),
            ) {
                items(
                    items = uiState.playlists,
                    key = { playlist -> "${playlist.serverId}_${playlist.id}" },
                ) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
            )
            .scale(if (isFocused) 1.05f else 1f)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        if (playlist.thumbUrl != null) {
            AsyncImage(
                model = playlist.thumbUrl,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Title overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.playlist_item_count, playlist.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
