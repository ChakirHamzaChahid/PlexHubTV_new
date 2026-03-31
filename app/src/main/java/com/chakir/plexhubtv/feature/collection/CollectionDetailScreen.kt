package com.chakir.plexhubtv.feature.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
fun CollectionDetailRoute(
    viewModel: CollectionDetailViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onNavigateBack)

    CollectionDetailScreen(
        state = uiState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    state: CollectionDetailUiState,
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val collectionTitle = stringResource(R.string.collection_title)
    val screenDesc = stringResource(R.string.collection_screen_description)
    val loadingDesc = stringResource(R.string.collection_loading_description)
    val itemsDesc = stringResource(R.string.collection_items_description)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.title ?: collectionTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_description))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("screen_collection_detail")
                    .semantics { contentDescription = screenDesc }
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            if (state.isLoading) {
                LibraryGridSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("collection_loading")
                        .semantics { contentDescription = loadingDesc },
                )
            } else if (state.error != null) {
                val errorDesc = stringResource(R.string.collection_error_description, state.error)
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("collection_error")
                        .semantics { contentDescription = errorDesc },
                )
            } else {
                state.collection?.let { collection ->
                    if (collection.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("collection_empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.collection_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            collection.summary?.let { summary ->
                                if (summary.isNotBlank()) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                                        maxLines = 5,
                                    )
                                }
                            }

                            val gridState = rememberLazyGridState()
                            val gridFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                            // Data-reactive focus: fires only when items are available
                            LaunchedEffect(collection.items.isNotEmpty()) {
                                if (collection.items.isNotEmpty()) {
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
                                    .testTag("collection_items_list")
                                    .semantics { contentDescription = itemsDesc },
                            ) {
                                items(
                                    items = collection.items,
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
}
