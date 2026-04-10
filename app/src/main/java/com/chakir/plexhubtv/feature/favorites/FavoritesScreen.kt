package com.chakir.plexhubtv.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
import com.chakir.plexhubtv.core.ui.NetflixMediaCard

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
    onNavigateToPersonDetail: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    FavoritesScreen(
        uiState = uiState,
        onMediaClick = { media -> onNavigateToMedia(media.ratingKey, media.serverId) },
        onActorClick = { actor -> onNavigateToPersonDetail(actor.name) },
        onCategoryChanged = { category -> viewModel.setCategory(category) },
        onSortChanged = { option -> viewModel.setSortOption(option) },
        onActorSortChanged = { option -> viewModel.setActorSortOption(option) },
        showYear = false,
        gridColumnsCount = 6,
    )
}

@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
    onActorClick: (FavoriteActor) -> Unit = {},
    onCategoryChanged: (FavoritesCategory) -> Unit = {},
    onSortChanged: (FavoritesSortOption) -> Unit = {},
    onActorSortChanged: (ActorSortOption) -> Unit = {},
    showYear: Boolean = false,
    gridColumnsCount: Int = 6,
) {
    val screenDescription = stringResource(R.string.favorites_screen_description)
    val loadingDescription = stringResource(R.string.favorites_loading_description)

    val activeCount = when (uiState.category) {
        FavoritesCategory.MEDIA -> uiState.favorites.size
        FavoritesCategory.ACTORS -> uiState.favoriteActors.size
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_favorites")
                .semantics { contentDescription = screenDescription }
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 58.dp, end = 58.dp, top = 80.dp),
    ) {
        com.chakir.plexhubtv.core.ui.SectionTitle(
            title = stringResource(R.string.favorites_title_with_count, activeCount),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Category chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip(
                label = stringResource(R.string.favorites_category_media),
                selected = uiState.category == FavoritesCategory.MEDIA,
                onClick = { onCategoryChanged(FavoritesCategory.MEDIA) },
            )
            CategoryChip(
                label = stringResource(R.string.favorites_category_actors),
                selected = uiState.category == FavoritesCategory.ACTORS,
                onClick = { onCategoryChanged(FavoritesCategory.ACTORS) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            LibraryGridSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("favorites_loading")
                    .semantics { contentDescription = loadingDescription },
            )
        } else {
            when (uiState.category) {
                FavoritesCategory.MEDIA -> MediaFavoritesContent(
                    uiState = uiState,
                    onMediaClick = onMediaClick,
                    onSortChanged = onSortChanged,
                    showYear = showYear,
                    gridColumnsCount = gridColumnsCount,
                )
                FavoritesCategory.ACTORS -> ActorFavoritesContent(
                    uiState = uiState,
                    onActorClick = onActorClick,
                    onActorSortChanged = onActorSortChanged,
                )
            }
        }
    }
}

@Composable
private fun MediaFavoritesContent(
    uiState: FavoritesUiState,
    onMediaClick: (com.chakir.plexhubtv.core.model.MediaItem) -> Unit,
    onSortChanged: (FavoritesSortOption) -> Unit,
    showYear: Boolean,
    gridColumnsCount: Int,
) {
    val emptyDescription = stringResource(R.string.favorites_empty_description)

    if (uiState.favorites.isEmpty()) {
        FavoritesEmptyState(
            icon = Icons.Default.FavoriteBorder,
            message = stringResource(R.string.favorites_empty),
            hint = stringResource(R.string.favorites_empty_hint),
            testTag = "favorites_empty",
            contentDescription = emptyDescription,
        )
    } else {
        FavoritesSortChip(
            currentSort = uiState.sortOption,
            isDescending = uiState.isDescending,
            onSortChanged = onSortChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))

        val gridState = rememberLazyGridState()
        val gridFocusRequester = remember { FocusRequester() }

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

@Composable
private fun ActorFavoritesContent(
    uiState: FavoritesUiState,
    onActorClick: (FavoriteActor) -> Unit,
    onActorSortChanged: (ActorSortOption) -> Unit,
) {
    if (uiState.favoriteActors.isEmpty()) {
        FavoritesEmptyState(
            icon = Icons.Default.Person,
            message = stringResource(R.string.favorites_actors_empty),
            hint = stringResource(R.string.favorites_actors_empty_hint),
            testTag = "favorites_actors_empty",
            contentDescription = stringResource(R.string.favorites_actors_empty),
        )
    } else {
        ActorSortChip(
            currentSort = uiState.actorSortOption,
            isDescending = uiState.isActorSortDescending,
            onSortChanged = onActorSortChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))

        val gridState = rememberLazyGridState()
        val gridFocusRequester = remember { FocusRequester() }

        LaunchedEffect(uiState.favoriteActors) {
            if (uiState.favoriteActors.isNotEmpty()) {
                try { gridFocusRequester.requestFocus() } catch (_: Exception) { }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().focusRequester(gridFocusRequester),
        ) {
            items(
                items = uiState.favoriteActors,
                key = { actor -> actor.tmdbId },
            ) { actor ->
                ActorCard(
                    actor = actor,
                    onClick = { onActorClick(actor) },
                )
            }
        }
    }
}

@Composable
private fun ActorCard(
    actor: FavoriteActor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val borderModifier = if (isFocused) {
            Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
        } else {
            Modifier
        }

        if (actor.photoUrl != null) {
            AsyncImage(
                model = actor.photoUrl,
                contentDescription = actor.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .then(borderModifier),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(borderModifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actor.name.firstOrNull()?.uppercase() ?: "?",
                    style = typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = actor.name,
            style = typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        actor.knownFor?.let { knownFor ->
            Text(
                text = knownFor,
                style = typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FavoritesEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    hint: String,
    testTag: String,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            selectedLabelColor = MaterialTheme.colorScheme.onBackground,
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            selectedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            enabled = true,
            selected = selected,
        ),
    )
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
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.onBackground,
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
private fun ActorSortChip(
    currentSort: ActorSortOption,
    isDescending: Boolean,
    onSortChanged: (ActorSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrow = if (isDescending) " ↓" else " ↑"

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(actorSortOptionLabel(currentSort) + arrow)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ActorSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(actorSortOptionLabel(option))
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

@Composable
private fun actorSortOptionLabel(option: ActorSortOption): String {
    return when (option) {
        ActorSortOption.DATE_ADDED -> stringResource(R.string.favorites_actors_sort_date_added)
        ActorSortOption.NAME -> stringResource(R.string.favorites_actors_sort_name)
    }
}
