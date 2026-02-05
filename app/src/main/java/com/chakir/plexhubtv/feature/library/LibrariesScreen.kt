package com.chakir.plexhubtv.feature.library

import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.compose.itemContentType
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.feature.home.MediaCard
import com.chakir.plexhubtv.core.designsystem.PlexHubTheme

/**
 * Écran principal de la bibliothèque (Films ou Séries).
 * Affiche une grille ou liste de médias avec pagination, filtres (Genre, Serveur) et tri.
 * Gère également la navigation latérale alphabétique.
 */
@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val events = viewModel.navigationEvents
    var scrollRequest by remember { mutableStateOf<Int?>(null) }

    // Track previous filter values to detect actual changes
    var previousFilters by remember { 
        mutableStateOf<FilterSnapshot?>(null)
    }
    
    // CRITICAL FIX: Force instant refresh ONLY when filter parameters actually change
    LaunchedEffect(
        uiState.selectedGenre,
        uiState.selectedServerFilter,
        uiState.currentSort,
        uiState.isSortDescending,
        uiState.searchQuery
    ) {
        val currentSnapshot = FilterSnapshot(
            genre = uiState.selectedGenre,
            server = uiState.selectedServerFilter,
            sort = uiState.currentSort,
            isDescending = uiState.isSortDescending,
            query = uiState.searchQuery
        )
        
        // Only refresh if this is NOT the first composition AND values changed
        if (previousFilters != null && previousFilters != currentSnapshot) {
            pagedItems.refresh()
        }
        previousFilters = currentSnapshot
    }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is LibraryNavigationEvent.NavigateToDetail -> onNavigateToDetail(event.ratingKey, event.serverId)
                is LibraryNavigationEvent.ScrollToItem -> scrollRequest = event.index
            }
        }
    }

    LibrariesScreen(
        state = uiState,
        pagedItems = pagedItems,
        onAction = viewModel::onAction,
        scrollRequest = scrollRequest,
        onScrollConsumed = { scrollRequest = null }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    state: LibraryUiState,
    pagedItems: LazyPagingItems<MediaItem>,
    onAction: (LibraryAction) -> Unit,
    scrollRequest: Int? = null,
    onScrollConsumed: () -> Unit = {}
) {
    val gridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { index ->
            try {
                // Determine which state to use
                val targetState = if (state.viewMode == LibraryViewMode.Grid) gridState else listState
                
                // Wait for itemCount to be valid for this index
                // This handles the race condition where Paging 3 is resetting/loading
                androidx.compose.runtime.snapshotFlow { pagedItems.itemCount }
                    .collect { count ->
                        if (count > index) {
                            android.util.Log.d("LibraryContent", "Scrolling to $index (ItemCount: $count)")
                            if (state.viewMode == LibraryViewMode.Grid) {
                                gridState.scrollToItem(index)
                            } else {
                                listState.scrollToItem(index)
                            }
                            onScrollConsumed()
                            // Cancel this collector once scrolled
                            throw java.util.concurrent.CancellationException("Scrolled") 
                        }
                    }
            } catch (e: java.util.concurrent.CancellationException) {
                // Expected flow exit
            } catch (e: Exception) {
                android.util.Log.e("LibraryContent", "Error scrolling to $index", e)
                onScrollConsumed() // Consume anyway to avoid stuck loop
            }
        }
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Main Top Bar
                if (state.isSearchVisible) {
                    SearchAppBar(
                        query = state.searchQuery,
                        onQueryChange = { onAction(LibraryAction.UpdateSearchQuery(it)) },
                        onClose = { onAction(LibraryAction.ToggleSearch) }
                    )
                } else {
                    TopAppBar(
                        title = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (state.mediaType == MediaType.Movie) "Movies" else "TV Shows",
                                    style = MaterialTheme.typography.titleLarge, // Smaller than HeadlineMedium
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.totalItems > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "(${state.totalItems})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { onAction(LibraryAction.ToggleSearch) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            // View Mode Switch
                            IconButton(onClick = {
                                val newMode = if (state.viewMode == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
                                onAction(LibraryAction.ChangeViewMode(newMode))
                            }) {
                                Icon(
                                    imageVector = if (state.viewMode == LibraryViewMode.Grid) Icons.Default.List else Icons.Default.GridView,
                                    contentDescription = "Switch View Mode"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // Tabs
                // Use a smaller height for the tab row
                 val visibleTabs = remember(state.selectedTab) {
                    LibraryTab.values().filter { it == LibraryTab.Browse }
                }
                val selectedTabIndex = visibleTabs.indexOf(state.selectedTab).coerceAtLeast(0)

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 14.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(32.dp), // Reduced height further
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 2.dp
                            )
                        }
                    },
                    divider = {}
                ) {
                    visibleTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == selectedTabIndex,
                            onClick = { onAction(LibraryAction.SelectTab(tab)) },
                            modifier = Modifier.height(32.dp), // Match height
                            text = { 
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (index == selectedTabIndex) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Server Filter Button
                    val serverLabel = if (state.selectedServerFilter != null) "Server: ${state.selectedServerFilter}" else "Server"
                    val isServerFiltered = state.selectedServerFilter != null
                    
                    FilterButton(
                        text = serverLabel,
                        isActive = isServerFiltered,
                        onClick = { onAction(LibraryAction.OpenServerFilter) }
                    )

                    // Genre Filter Button
                    val genreLabel = if (state.selectedGenre != null) "Genre: ${state.selectedGenre}" else "Genre"
                    val isGenreFiltered = state.selectedGenre != null

                    FilterButton(
                        text = genreLabel, 
                        isActive = isGenreFiltered,
                        onClick = { onAction(LibraryAction.OpenGenreFilter) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    FilterButton(
                        text = state.currentSort ?: "Date Added", 
                        isActive = false, // Always active concept? Or standard style
                        onClick = { onAction(LibraryAction.OpenSortDialog) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            if (state.isServerFilterOpen) {
                ServerFilterDialog(
                    availableServers = state.availableServers,
                    selectedServer = state.selectedServerFilter,
                    onDismiss = { onAction(LibraryAction.CloseServerFilter) },
                    onApply = { server ->
                         onAction(LibraryAction.SelectServerFilter(server))
                         onAction(LibraryAction.CloseServerFilter)
                    }
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
                    }
                )
            }
            
            if (state.isSortDialogOpen) {
                SortDialog(
                    currentSort = state.currentSort,
                    isDescending = state.isSortDescending,
                    onDismiss = { onAction(LibraryAction.CloseSortDialog) },
                    onSelectSort = { sort: String, isDesc: Boolean -> onAction(LibraryAction.ApplySort(sort, isDesc)) }
                )
            }
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
                        CircularProgressIndicator()
                    }
                }
                // Content
                else -> {
                    if (state.selectedTab == LibraryTab.Recommended) {
                        RecommendedContent(
                            hubs = state.hubs, 
                            onItemClick = { onAction(LibraryAction.OpenMedia(it)) }
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
                            onScrollConsumed = onScrollConsumed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterButton(text: String, isActive: Boolean = false, onClick: () -> Unit) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderStroke = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderStroke,
        modifier = Modifier.height(32.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun LibraryContent(
    pagedItems: LazyPagingItems<MediaItem>,
    viewMode: LibraryViewMode,
    onItemClick: (MediaItem) -> Unit,
    onAction: (LibraryAction) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showSidebar: Boolean = false,
    scrollRequest: Int? = null,
    onScrollConsumed: () -> Unit = {}
) {
    // Handle scroll request
    // Handle scroll request - REMOVED (Handled by parent LibrariesScreen)

    
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (viewMode == LibraryViewMode.Grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id },
                        contentType = pagedItems.itemContentType { "media_item" }
                    ) { index ->
                        val item = pagedItems[index]
                        if (item != null) {
                            MediaCard(
                                media = item,
                                onClick = { onItemClick(item) },
                                onPlay = { },
                                onFocus = { onAction(LibraryAction.OnItemFocused(item)) },
                                width = 100.dp,
                                height = 150.dp,
                                titleStyle = MaterialTheme.typography.labelMedium,
                                subtitleStyle = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            // Placeholder
                            Box(modifier = Modifier.height(150.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                        }
                    }
                    
                    if (pagedItems.loadState.append is LoadState.Loading) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                             LoadingMoreIndicator()
                        }
                    }
                }
            } else {
                // List View... (Same pattern)
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id },
                        contentType = pagedItems.itemContentType { "media_item" }
                    ) { index ->
                         val item = pagedItems[index]
                         if (item != null) {
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onItemClick(item) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                             ) {
                                Thumbnail(url = item.thumbUrl, modifier = Modifier.size(50.dp, 75.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(text = "${item.year ?: ""}", style = MaterialTheme.typography.bodyMedium)
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
        
        if (showSidebar) {
            AlphabetSidebar(
                onLetterSelected = { letter -> onAction(LibraryAction.JumpToLetter(letter)) }
            )
        }
    }
}

@Composable
fun LoadingMoreIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun RecommendedContent(
    hubs: List<com.chakir.plexhubtv.domain.model.Hub>,
    onItemClick: (MediaItem) -> Unit
) {
    if (hubs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recommendations found.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(hubs) { hub ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = hub.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(hub.items) { item ->
                        MediaCard(
                            media = item,
                            onClick = { onItemClick(item) },
                            onPlay = { /* Play */ },
                            onFocus = { },
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
}

@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
         if (url != null) {
              AsyncImage(
                  model = coil.request.ImageRequest.Builder(LocalContext.current)
                      .data(url)
                      .crossfade(true) // Performance: Disable crossfade for smoother scrolling
                      .build(),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
              )
         }
    }
}

// Preview removed as LazyPagingItems is difficult to mock in Preview without flow helpers.
