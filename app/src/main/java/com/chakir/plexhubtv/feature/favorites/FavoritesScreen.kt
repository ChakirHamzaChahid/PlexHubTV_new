package com.chakir.plexhubtv.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
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
        onSortChanged = { option -> viewModel.setSortOption(option) },
    )
}

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
    onSortChanged: (FavoritesSortOption) -> Unit = {},
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
                .background(NetflixBlack)
                .padding(start = 58.dp, end = 58.dp, top = 80.dp),
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
            // Sort chip
            FavoritesSortChip(
                currentSort = uiState.sortOption,
                isDescending = uiState.isDescending,
                onSortChanged = onSortChanged,
            )
            Spacer(modifier = Modifier.height(16.dp))

            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
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
                                .aspectRatio(2f / 3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesSortChip(
    currentSort: FavoritesSortOption,
    isDescending: Boolean,
    onSortChanged: (FavoritesSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrow = if (isDescending) " ↓" else " ↑"

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sortOptionLabel(currentSort) + arrow)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.White.copy(alpha = 0.15f),
                selectedLabelColor = Color.White,
            ),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FavoritesSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sortOptionLabel(option))
                            if (option == currentSort) {
                                Text(
                                    text = arrow,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSortChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun sortOptionLabel(option: FavoritesSortOption): String {
    return when (option) {
        FavoritesSortOption.DATE_ADDED -> stringResource(R.string.filter_sort_date_added)
        FavoritesSortOption.TITLE -> stringResource(R.string.filter_sort_title)
        FavoritesSortOption.YEAR -> stringResource(R.string.filter_sort_year)
        FavoritesSortOption.RATING -> stringResource(R.string.filter_sort_rating)
    }
}
