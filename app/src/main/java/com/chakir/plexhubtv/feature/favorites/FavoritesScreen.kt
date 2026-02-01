package com.chakir.plexhubtv.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.feature.home.MediaCard

/**
 * Écran affichant les favoris de l'utilisateur.
 */
@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    FavoritesScreen(
        uiState = uiState,
        onMediaClick = { media -> onNavigateToMedia(media.ratingKey, media.serverId) }
    )
}

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.domain.model.MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.favorites.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No favorites yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.favorites) { media ->
                    MediaCard(
                        media = media,
                        onClick = { onMediaClick(media) },
                        onPlay = { /* Optional direct play */ },
                        onFocus = {},
                        width = 100.dp,
                        height = 150.dp,
                        titleStyle = MaterialTheme.typography.labelMedium,
                        subtitleStyle = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
