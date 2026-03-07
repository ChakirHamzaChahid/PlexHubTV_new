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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixRed
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
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
        showYear = false, // TODO: Get from ViewModel/UiState
        gridColumnsCount = 6, // Fixed for now, can be made configurable later
    )
}

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
    onSortChanged: (FavoritesSortOption) -> Unit = {},
    showYear: Boolean = false,
    gridColumnsCount: Int = 6,
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
            text = stringResource(R.string.favorites_title_with_count, uiState.favorites.size),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.isLoading) {
            LibraryGridSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_loading")
                    .semantics { contentDescription = loadingDescription },
            )
        } else if (uiState.favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_empty")
                    .semantics { contentDescription = emptyDescription },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        style = typography.titleLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.favorites_empty_hint),
                        style = typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
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
            val gridFocusRequester = remember { FocusRequester() }

            // NAV-06: Request focus on first grid item when content loads
            LaunchedEffect(uiState.favorites) {
                if (uiState.favorites.isNotEmpty()) {
                    try { gridFocusRequester.requestFocus() } catch (_: Exception) { }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumnsCount),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().focusRequester(gridFocusRequester),
            ) {
                items(
                    items = uiState.favorites,
                    key = { media -> "${media.serverId}_${media.ratingKey}" },
                ) { media ->
                    NetflixMediaCard(
                        media = media,
                        onClick = { onMediaClick(media) },
                        onPlay = { /* Optional direct play */ },
                        showYear = showYear,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                    )
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
