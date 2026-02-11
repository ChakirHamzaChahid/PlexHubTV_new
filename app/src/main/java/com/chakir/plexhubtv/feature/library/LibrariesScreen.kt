package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyGridState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.chakir.plexhubtv.core.model.isRetryable
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.showError
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.home.MediaCard
import timber.log.Timber

import com.chakir.plexhubtv.core.ui.NetflixMediaCard
import com.chakir.plexhubtv.di.designsystem.NetflixBlack
import com.chakir.plexhubtv.di.designsystem.NetflixRed
import androidx.compose.ui.zIndex

// ... (Rest of imports likely preserved by smart apply, but I should be careful)

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation events
    LaunchedEffect(viewModel.navigationEvents) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is LibraryNavigationEvent.NavigateToDetail -> {
                    onNavigateToMedia(event.ratingKey, event.serverId)
                }
                is LibraryNavigationEvent.ScrollToItem -> {
                     // Handled by state.initialScrollIndex
                }
            }
        }
    }

    // Handle error events with centralized error display
    LaunchedEffect(viewModel.errorEvents) {
        viewModel.errorEvents.collect { error ->
            snackbarHostState.showError(error)
        }
    }

    LibrariesScreen(
        state = state,
        pagedItems = pagedItems,
        onAction = viewModel::onAction,
        scrollRequest = state.initialScrollIndex,
        onScrollConsumed = { },
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    state: LibraryUiState,
    pagedItems: LazyPagingItems<MediaItem>,
    onAction: (LibraryAction) -> Unit,
    scrollRequest: Int? = null,
    onScrollConsumed: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
) {
    val gridState = rememberTvLazyGridState()
    val listState = rememberTvLazyListState()

    LaunchedEffect(scrollRequest) {
        if (scrollRequest != null) {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
            onScrollConsumed()
        }
    }
    Scaffold(
        containerColor = NetflixBlack, // Set Scaffold background
        snackbarHost = { ErrorSnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(NetflixBlack)) { // Update TopBar background
                // Main Top Bar
                if (state.isSearchVisible) {
                    SearchAppBar(
                        query = state.searchQuery,
                        onQueryChange = { onAction(LibraryAction.UpdateSearchQuery(it)) },
                        onClose = { onAction(LibraryAction.ToggleSearch) },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 58.dp, top = 24.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (state.mediaType == MediaType.Movie) "Movies" else "TV Shows",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (state.totalItems > 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${state.totalItems} Titles",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                        
                        Row {
                            // Search icon removed to avoid duplication with Global Top Bar
                            
                            // View Mode Switch
                            IconButton(onClick = {
                                val newMode = if (state.viewMode == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
                                onAction(LibraryAction.ChangeViewMode(newMode))
                            }) {
                                Icon(
                                    imageVector = if (state.viewMode == LibraryViewMode.Grid) Icons.Default.List else Icons.Default.GridView,
                                    contentDescription = "Switch View Mode",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Tabs
                // Use a smaller height for the tab row
                val visibleTabs =
                    remember(state.selectedTab) {
                        LibraryTab.values().filter { it == LibraryTab.Browse }
                    }
                val selectedTabIndex = visibleTabs.indexOf(state.selectedTab).coerceAtLeast(0)

                // Simplified Tab Row or remove if only Browse exists often?
                // The code filters to only Browse? "filter { it == LibraryTab.Browse }" means only Browse is visible?
                // If so, we can skip the TabRow if there's only one.
                if (visibleTabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 58.dp, // Align with content
                        containerColor = Color.Transparent,
                        contentColor = NetflixRed,
                        modifier = Modifier.height(48.dp),
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = NetflixRed,
                                    height = 2.dp,
                                )
                            }
                        },
                        divider = {},
                    ) {
                        visibleTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == selectedTabIndex,
                                onClick = { onAction(LibraryAction.SelectTab(tab)) },
                                modifier = Modifier.height(48.dp),
                                text = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (index == selectedTabIndex) FontWeight.Bold else FontWeight.Normal,
                                        color = if (index == selectedTabIndex) Color.White else Color.White.copy(alpha = 0.6f)
                                    )
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 58.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Filter Buttons updated style
                    val serverLabel = if (state.selectedServerFilter != null) "Server: ${state.selectedServerFilter}" else "All Servers"
                    val isServerFiltered = state.selectedServerFilter != null

                    FilterButton(
                        text = serverLabel,
                        isActive = isServerFiltered,
                        onClick = { onAction(LibraryAction.OpenServerFilter) },
                    )

                    val genreLabel = if (state.selectedGenre != null) "Genre: ${state.selectedGenre}" else "Values"
                    val isGenreFiltered = state.selectedGenre != null

                    FilterButton(
                        text = genreLabel,
                        isActive = isGenreFiltered,
                        onClick = { onAction(LibraryAction.OpenGenreFilter) },
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    FilterButton(
                        text = state.currentSort ?: "Date Added",
                        isActive = false,
                        onClick = { onAction(LibraryAction.OpenSortDialog) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(NetflixBlack),
        ) {
            // ... (Dialogs preserved)
            if (state.isServerFilterOpen) {
                ServerFilterDialog(
                    availableServers = state.availableServers,
                    selectedServer = state.selectedServerFilter,
                    onDismiss = { onAction(LibraryAction.CloseServerFilter) },
                    onApply = { server ->
                        onAction(LibraryAction.SelectServerFilter(server))
                        onAction(LibraryAction.CloseServerFilter)
                    },
                )
            }

            if (state.isGenreFilterOpen) {
                GenreFilterDialog(
                    availableGenres = state.availableGenres,
                    selectedGenre = state.selectedGenre,
                    onDismiss = { onAction(LibraryAction.CloseGenreFilter) },
                    onApply = { genre ->
                        onAction(LibraryAction.SelectGenre(genre))
                        onAction(LibraryAction.CloseGenreFilter)
                    },
                )
            }

            if (state.isSortDialogOpen) {
                SortDialog(
                    currentSort = state.currentSort,
                    isDescending = state.isSortDescending,
                    onDismiss = { onAction(LibraryAction.CloseSortDialog) },
                    onSelectSort = { sort: String, isDesc: Boolean -> onAction(LibraryAction.ApplySort(sort, isDesc)) },
                )
            }
            // ...

            when {
                // Error state
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.error, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onAction(LibraryAction.Refresh) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                // Loading state (only for INITIAL load)
                pagedItems.loadState.refresh is androidx.paging.LoadState.Loading && pagedItems.itemCount == 0 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NetflixRed)
                    }
                }
                // Content
                else -> {
                    if (state.selectedTab == LibraryTab.Recommended) {
                        RecommendedContent(
                            hubs = state.hubs,
                            onItemClick = { onAction(LibraryAction.OpenMedia(it)) },
                        )
                    } else {
                        LibraryContent(
                            pagedItems = pagedItems,
                            viewMode = state.viewMode,
                            onItemClick = { onAction(LibraryAction.OpenMedia(it)) },
                            onAction = onAction,
                            gridState = gridState,
                            listState = listState,
                            showSidebar = state.currentSort == "Title",
                            scrollRequest = scrollRequest,
                            onScrollConsumed = onScrollConsumed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterButton(
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    // Netflix style chips: Dark grey background, White text.
    val containerColor = if (isActive) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
    val contentColor = Color.White
    val borderStroke = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, Color.White) else null

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = borderStroke, // Use border only if active or keep it transparent
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(32.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
fun LibraryContent(
    pagedItems: LazyPagingItems<MediaItem>,
    viewMode: LibraryViewMode,
    onItemClick: (MediaItem) -> Unit,
    onAction: (LibraryAction) -> Unit,
    gridState: TvLazyGridState,
    listState: TvLazyListState,
    showSidebar: Boolean = false,
    scrollRequest: Int? = null,
    onScrollConsumed: () -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (viewMode == LibraryViewMode.Grid) {
                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id },
                        contentType = pagedItems.itemContentType { "media_item" },
                    ) { index ->
                        val item = pagedItems[index]
                        if (item != null) {
                            var isFocused by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
                                NetflixMediaCard(
                                    media = item,
                                    onClick = { onItemClick(item) },
                                    onPlay = { },
                                    onFocus = { focused -> 
                                        isFocused = focused
                                        if (focused) {
                                            onAction(LibraryAction.OnItemFocused(item))
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f/3f)
                                )
                            }
                        } else {
                            // Placeholder
                            Box(
                                modifier =
                                    Modifier
                                        .aspectRatio(2f/3f)
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                    
                     if (pagedItems.loadState.append is LoadState.Loading) {
                        item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(maxLineSpan) }) {
                            LoadingMoreIndicator()
                        }
                    }
                }
            } else {
                // List View
                TvLazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id },
                        contentType = pagedItems.itemContentType { "media_item" },
                    ) { index ->
                        val item = pagedItems[index]
                        if (item != null) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onItemClick(item) }
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Thumbnail(url = item.thumbUrl, modifier = Modifier.size(60.dp, 90.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text(text = "${item.year ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            // Placeholder
                            Box(modifier = Modifier.height(80.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.1f)))
                        }
                    }
                    if (pagedItems.loadState.append is LoadState.Loading) {
                        item { LoadingMoreIndicator() }
                    }
                }
            }
        }

        if (showSidebar) {
            AlphabetSidebar(
                onLetterSelected = { letter -> onAction(LibraryAction.JumpToLetter(letter)) },
            )
        }
    }
}

@Composable
fun LoadingMoreIndicator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun RecommendedContent(
    hubs: List<com.chakir.plexhubtv.core.model.Hub>,
    onItemClick: (MediaItem) -> Unit,
) {
    if (hubs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recommendations found.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        pivotOffsets = PivotOffsets(parentFraction = 0.0f)
    ) {
        items(
            items = hubs,
            key = { hub -> hub.hubIdentifier ?: hub.title },
        ) { hub ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = hub.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f)
                ) {
                    items(
                        items = hub.items,
                        key = { item -> "${item.serverId}_${item.ratingKey}" },
                    ) { item ->
                        MediaCard(
                            media = item,
                            onClick = { onItemClick(item) },
                            onPlay = { /* Play */ },
                            onFocus = { },
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

@Composable
fun Thumbnail(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
        if (url != null) {
            AsyncImage(
                model =
                    coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(false) // Performance: Disable crossfade for smoother scrolling
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

// Preview removed as LazyPagingItems is difficult to mock in Preview without flow helpers.
