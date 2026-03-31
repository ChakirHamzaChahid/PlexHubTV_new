package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
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
import com.chakir.plexhubtv.core.ui.HandleErrors
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.LibraryGridSkeleton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.home.MediaCard


import com.chakir.plexhubtv.core.ui.NetflixMediaCard
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

// ... (Rest of imports likely preserved by smart apply, but I should be careful)

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToMedia: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    // derivedStateOf: recompute only when the relevant fields change,
    // not on every uiState change (22 fields)
    val visibleTabs by remember {
        derivedStateOf { LibraryTab.values().filter { it == LibraryTab.Browse } }
    }
    val selectedTabIndex by remember {
        derivedStateOf { visibleTabs.indexOf(state.display.selectedTab).coerceAtLeast(0) }
    }
    val showSidebar by remember {
        derivedStateOf { state.filter.currentSort == "Title" }
    }
    val serverLabel by remember {
        derivedStateOf {
            if (state.filter.selectedServerFilter != null) "Server: ${state.filter.selectedServerFilter}" else "Server: All"
        }
    }
    val isServerFiltered by remember {
        derivedStateOf { state.filter.selectedServerFilter != null }
    }
    val genreLabel by remember {
        derivedStateOf {
            if (state.filter.selectedGenre != null) "Genre: ${state.filter.selectedGenre}" else "Genre: All"
        }
    }
    val isGenreFiltered by remember {
        derivedStateOf { state.filter.selectedGenre != null }
    }

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

    HandleErrors(viewModel.errorEvents, snackbarHostState)

    LibrariesScreen(
        state = state,
        pagedItems = pagedItems,
        onAction = viewModel::onAction,
        scrollRequest = state.scroll.initialScrollIndex,
        onScrollConsumed = { },
        snackbarHostState = snackbarHostState,
        visibleTabs = visibleTabs,
        selectedTabIndex = selectedTabIndex,
        showSidebar = showSidebar,
        serverLabel = serverLabel,
        isServerFiltered = isServerFiltered,
        genreLabel = genreLabel,
        isGenreFiltered = isGenreFiltered,
        showYear = state.display.showYearOnCards,
        gridColumnsCount = state.display.gridColumnsCount,
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
    visibleTabs: List<LibraryTab> = emptyList(),
    selectedTabIndex: Int = 0,
    showSidebar: Boolean = false,
    serverLabel: String = "Server: All",
    isServerFiltered: Boolean = false,
    genreLabel: String = "Genre: All",
    isGenreFiltered: Boolean = false,
    showYear: Boolean = false,
    gridColumnsCount: Int = 6,
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val topBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var topBarHasFocus by remember { mutableStateOf(false) }

    // Back handler: move focus to top bar instead of leaving the screen.
    // If top bar already has focus, let the system handle back (exit to home).
    BackHandler(enabled = !topBarHasFocus) {
        try {
            topBarFocusRequester.requestFocus()
        } catch (_: Exception) { }
    }

    LaunchedEffect(scrollRequest) {
        if (scrollRequest != null) {
            gridState.scrollToItem(scrollRequest)
            listState.scrollToItem(scrollRequest)
            onScrollConsumed()
        }
    }

    // Restore scroll position after back-navigation (wait for paging data to load)
    val pendingScrollRestore = state.scroll.pendingScrollRestore
    LaunchedEffect(pendingScrollRestore) {
        if (pendingScrollRestore != null && pendingScrollRestore > 0) {
            val loaded = withTimeoutOrNull(3000L) {
                snapshotFlow { pagedItems.itemCount }.first { it > pendingScrollRestore }
            }
            if (pagedItems.itemCount > 0) {
                val target = pendingScrollRestore.coerceAtMost(pagedItems.itemCount - 1)
                gridState.scrollToItem(target)
                listState.scrollToItem(target)
            }
        }
    }

    // Scroll to top when sort, genre, or server filter changes
    var isFirstComposition by remember { mutableStateOf(true) }
    val sortKey = "${state.filter.currentSort}_${state.filter.isSortDescending}_${state.filter.selectedGenre}_${state.filter.selectedServerFilter}"
    LaunchedEffect(sortKey) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, // Set Scaffold background
        snackbarHost = { ErrorSnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) { // Update TopBar background
                // Main Top Bar
                if (state.filter.isSearchVisible) {
                    SearchAppBar(
                        query = state.filter.searchQuery,
                        onSearch = { onAction(LibraryAction.UpdateSearchQuery(it)) },
                        onClose = { onAction(LibraryAction.ToggleSearch) },
                    )
                } else {
                    // Compact header: Title on left, Filters on right (same row)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 58.dp, top = 80.dp, end = 58.dp, bottom = 16.dp)
                            .onFocusChanged { focusState ->
                                topBarHasFocus = focusState.hasFocus
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left side: Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(if (state.display.mediaType == MediaType.Movie) R.string.library_movies else R.string.library_tv_shows),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (state.display.totalItems > 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                val hasActiveFilter = state.display.filteredItems != null && state.display.filteredItems != state.display.totalItems
                                Text(
                                    text = if (hasActiveFilter) {
                                        stringResource(R.string.library_title_count_filtered, state.display.filteredItems ?: 0, state.display.totalItems)
                                    } else {
                                        stringResource(R.string.library_title_count, state.display.totalItems)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Right side: Filters + View Mode
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Filter Buttons (derived via derivedStateOf at Route level)
                            FilterButton(
                                text = serverLabel,
                                isActive = isServerFiltered,
                                onClick = { onAction(LibraryAction.OpenServerFilter) },
                                testTag = "library_filter_server",
                                focusRequester = topBarFocusRequester,
                            )

                            FilterButton(
                                text = genreLabel,
                                isActive = isGenreFiltered,
                                onClick = { onAction(LibraryAction.OpenGenreFilter) },
                                testTag = "library_filter_genre"
                            )

                            FilterButton(
                                text = state.filter.currentSort,
                                isActive = false,
                                onClick = { onAction(LibraryAction.OpenSortDialog) },
                                testTag = "library_sort_button"
                            )

                            // Refresh Button (10s cooldown to prevent flooding)
                            IconButton(
                                onClick = {
                                    pagedItems.refresh()
                                    onAction(LibraryAction.Refresh)
                                },
                                enabled = !state.display.isRefreshing,
                                modifier = Modifier.testTag("library_refresh_button")
                            ) {
                                if (state.display.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.library_refresh_description),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            // View Mode Switch (Grid → Compact → List → Grid)
                            IconButton(
                                onClick = {
                                    val newMode = when (state.display.viewMode) {
                                        LibraryViewMode.Grid -> LibraryViewMode.Compact
                                        LibraryViewMode.Compact -> LibraryViewMode.List
                                        LibraryViewMode.List -> LibraryViewMode.Grid
                                    }
                                    onAction(LibraryAction.ChangeViewMode(newMode))
                                },
                                modifier = Modifier.testTag("library_view_mode")
                            ) {
                                Icon(
                                    imageVector = when (state.display.viewMode) {
                                        LibraryViewMode.Grid -> Icons.Default.Apps
                                        LibraryViewMode.Compact -> Icons.Default.List
                                        LibraryViewMode.List -> Icons.Default.GridView
                                    },
                                    contentDescription = stringResource(R.string.library_view_mode_description),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                // Tabs (derived via derivedStateOf at Route level)
                if (visibleTabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 58.dp, // Align with content
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(48.dp),
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = MaterialTheme.colorScheme.primary,
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
                                        color = if (index == selectedTabIndex) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag(if (state.display.mediaType == MediaType.Movie) "screen_movies" else "screen_tvshows")
                    .semantics {
                        contentDescription = if (state.display.mediaType == MediaType.Movie) "Écran de films" else "Écran de séries"
                    }
                    .background(MaterialTheme.colorScheme.background),
        ) {
            // ... (Dialogs preserved)
            if (state.dialog.isServerFilterOpen) {
                ServerFilterDialog(
                    availableServers = state.filter.availableServers,
                    selectedServer = state.filter.selectedServerFilter,
                    onDismiss = { onAction(LibraryAction.CloseServerFilter) },
                    onApply = { server ->
                        onAction(LibraryAction.SelectServerFilter(server))
                        onAction(LibraryAction.CloseServerFilter)
                    },
                )
            }

            if (state.dialog.isGenreFilterOpen) {
                GenreFilterDialog(
                    availableGenres = state.filter.availableGenres,
                    selectedGenre = state.filter.selectedGenre,
                    onDismiss = { onAction(LibraryAction.CloseGenreFilter) },
                    onApply = { genre ->
                        onAction(LibraryAction.SelectGenre(genre))
                        onAction(LibraryAction.CloseGenreFilter)
                    },
                )
            }

            if (state.dialog.isSortDialogOpen) {
                SortDialog(
                    currentSort = state.filter.currentSort,
                    isDescending = state.filter.isSortDescending,
                    onDismiss = { onAction(LibraryAction.CloseSortDialog) },
                    onSelectSort = { sort: String, isDesc: Boolean -> onAction(LibraryAction.ApplySort(sort, isDesc)) },
                )
            }
            // ...

            // Isolate loadState read to prevent recomposition cascade:
            // Reading loadState.refresh registers a Compose snapshot dependency.
            // Using derivedStateOf limits recomposition to only this boolean check,
            // not the entire parent composable tree.
            val showSkeleton by remember {
                derivedStateOf {
                    pagedItems.loadState.refresh is androidx.paging.LoadState.Loading && pagedItems.itemCount == 0
                }
            }

            if (showSkeleton) {
                LibraryGridSkeleton(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                if (state.display.selectedTab == LibraryTab.Recommended) {
                    RecommendedContent(
                        hubs = state.display.hubs,
                        onItemClick = { onAction(LibraryAction.OpenMedia(it)) },
                    )
                } else {
                    LibraryContent(
                        pagedItems = pagedItems,
                        viewMode = state.display.viewMode,
                        onItemClick = { item ->
                            val scrollIndex = when (state.display.viewMode) {
                                LibraryViewMode.Grid, LibraryViewMode.Compact -> gridState.firstVisibleItemIndex
                                LibraryViewMode.List -> listState.firstVisibleItemIndex
                            }
                            onAction(LibraryAction.OpenMedia(item, scrollIndex))
                        },
                        onAction = onAction,
                        gridState = gridState,
                        listState = listState,
                        showSidebar = showSidebar,
                        scrollRequest = scrollRequest,
                        onScrollConsumed = onScrollConsumed,
                        lastFocusedId = state.scroll.lastFocusedId,
                        showYear = showYear,
                        gridColumnsCount = gridColumnsCount,
                    )
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
    testTag: String = "",
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    // Cinema Gold filter chips: surface bg, theme text
    val cs = MaterialTheme.colorScheme
    val containerColor = if (isActive) cs.surfaceVariant else cs.surface
    val contentColor = if (isActive) cs.onSurface else cs.onSurfaceVariant
    val borderStroke = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, cs.primary) else null

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        border = borderStroke, // Use border only if active or keep it transparent
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .height(32.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
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
    gridState: LazyGridState,
    listState: LazyListState,
    showSidebar: Boolean = false,
    scrollRequest: Int? = null,
    onScrollConsumed: () -> Unit = {},
    lastFocusedId: String? = null,
    showYear: Boolean = false,
    gridColumnsCount: Int = 6,
) {
    // Focus restoration: request focus on the item that was previously focused
    val focusRestorationRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var hasRestoredFocus by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (viewMode) {
                LibraryViewMode.Grid, LibraryViewMode.Compact -> {
                    val isCompact = viewMode == LibraryViewMode.Compact
                    LazyVerticalGrid(
                        columns = if (isCompact) GridCells.Adaptive(minSize = 90.dp) else GridCells.Fixed(gridColumnsCount),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = if (isCompact) 40.dp else 58.dp,
                            end = if (isCompact) 40.dp else 58.dp,
                            top = 16.dp,
                            bottom = 32.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(if (isCompact) "library_compact_grid" else "library_grid")
                            .semantics { contentDescription = "Grille de la bibliothèque" },
                    ) {
                        items(
                            count = pagedItems.itemCount,
                            key = pagedItems.itemKey { it.id },
                            contentType = pagedItems.itemContentType { "media_item" },
                        ) { index ->
                            val item = pagedItems[index]
                            if (item != null) {
                                val shouldRestoreFocus = !hasRestoredFocus && lastFocusedId != null && item.ratingKey == lastFocusedId

                                LaunchedEffect(shouldRestoreFocus) {
                                    if (shouldRestoreFocus) {
                                        delay(100) // Wait for layout stabilization
                                        try {
                                            focusRestorationRequester.requestFocus()
                                            hasRestoredFocus = true
                                        } catch (_: Exception) { }
                                    }
                                }

                                val itemOnClick = remember(item.ratingKey, item.serverId) { { onItemClick(item) } }
                                val itemOnFocus = remember(item.ratingKey, item.serverId) {
                                    { focused: Boolean -> if (focused) onAction(LibraryAction.OnItemFocused(item)) }
                                }
                                NetflixMediaCard(
                                    media = item,
                                    onClick = itemOnClick,
                                    onPlay = { },
                                    onFocus = itemOnFocus,
                                    compact = isCompact,
                                    showYear = showYear,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (shouldRestoreFocus) Modifier.focusRequester(focusRestorationRequester) else Modifier
                                        )
                                )
                            } else {
                                // Placeholder
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(if (isCompact) 135.dp else 250.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                                )
                            }
                        }

                         if (pagedItems.loadState.append is LoadState.Loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LoadingMoreIndicator()
                            }
                        }
                    }
                }

                LibraryViewMode.List -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = pagedItems.itemCount,
                            key = pagedItems.itemKey { it.id },
                            contentType = pagedItems.itemContentType { "media_item" },
                        ) { index ->
                            val item = pagedItems[index]
                            if (item != null) {
                                val shouldRestoreFocus = !hasRestoredFocus && lastFocusedId != null && item.ratingKey == lastFocusedId

                                LaunchedEffect(shouldRestoreFocus) {
                                    if (shouldRestoreFocus) {
                                        delay(150)
                                        try {
                                            focusRestorationRequester.requestFocus()
                                            hasRestoredFocus = true
                                        } catch (_: Exception) { }
                                    }
                                }

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (shouldRestoreFocus) Modifier.focusRequester(focusRestorationRequester) else Modifier
                                            )
                                            .clickable { onItemClick(item) }
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Thumbnail(url = item.thumbUrl, modifier = Modifier.size(60.dp, 90.dp), contentDescription = item.title)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(text = item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                        Text(text = "${item.year ?: ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                // Placeholder
                                Box(modifier = Modifier.height(80.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                        if (pagedItems.loadState.append is LoadState.Loading) {
                            item { LoadingMoreIndicator() }
                        }
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
                text = stringResource(R.string.library_no_recommendations),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
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

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    contentDescription: String? = null,
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
        if (url != null) {
            AsyncImage(
                model =
                    coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .size(180, 270) // Explicit size for 60dp×90dp at xxhdpi (×3 density)
                        .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

// Preview removed as LazyPagingItems is difficult to mock in Preview without flow helpers.
