package com.chakir.plexhubtv.feature.favorites

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.R
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.ui.NetflixMediaCard

/**
 * Écran affichant les favoris de l'utilisateur.
 */
@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    FavoritesScreen(
        uiState = uiState,
        onMediaClick = { media -> onNavigateToMedia(media.ratingKey, media.serverId) },
    )
}

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
) {
    val screenDescription = stringResource(R.string.favorites_screen_description)
    val loadingDescription = stringResource(R.string.favorites_loading_description)
    val emptyDescription = stringResource(R.string.favorites_empty_description)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_favorites")
                .semantics { contentDescription = screenDescription }
                .background(NetflixBlack) // Netflix Black Background
                .padding(start = 58.dp, end = 58.dp, top = 80.dp), // 56dp TopBar + 24dp content padding
    ) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_loading")
                    .semantics { contentDescription = loadingDescription },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NetflixRed)
            }
        } else if (uiState.favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_empty")
                    .semantics { contentDescription = emptyDescription },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 140.dp), // Matched card size
                contentPadding = PaddingValues(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.favorites,
                    key = { media -> "${media.serverId}_${media.ratingKey}" },
                ) { media ->
                    var isFocused by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                        NetflixMediaCard(
                            media = media,
                            onClick = { onMediaClick(media) },
                            onPlay = { /* Optional direct play */ },
                            onFocus = { focused -> isFocused = focused },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f/3f)
                        )
                    }
                }
            }
        }
    }
}
