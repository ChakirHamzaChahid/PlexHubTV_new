package com.chakir.plexhubtv.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.feature.home.MediaCard

@Composable
fun CollectionDetailRoute(
    viewModel: CollectionDetailViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                    ),
            )
        },
        containerColor = Color.Black,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("screen_collection_detail")
                    .semantics { contentDescription = screenDesc }
                    .padding(paddingValues)
                    .background(Color.Black),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("collection_loading")
                        .semantics { contentDescription = loadingDesc },
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (state.error != null) {
                val errorDesc = stringResource(R.string.collection_error_description, state.error)
                Text(
                    text = state.error,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("collection_error")
                        .semantics { contentDescription = errorDesc },
                )
            } else {
                state.collection?.let { collection ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        collection.summary?.let { summary ->
                            if (summary.isNotBlank()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(16.dp),
                                    maxLines = 5,
                                )
                            }
                        }

                        val gridState = rememberLazyGridState()
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("collection_items_list")
                                .semantics { contentDescription = itemsDesc },
                        ) {
                            items(
                                items = collection.items,
                                key = { item -> "${item.serverId}_${item.ratingKey}" },
                            ) { item ->
                                val index = collection.items.indexOf(item)
                                val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                                if (index == 0) {
                                    LaunchedEffect(Unit) { fr.requestFocus() }
                                }
                                MediaCard(
                                    media = item,
                                    onClick = { onNavigateToDetail(item.ratingKey, item.serverId) },
                                    onPlay = {},
                                    onFocus = {},
                                    width = 120.dp,
                                    height = 180.dp,
                                    modifier = if (index == 0) Modifier.focusRequester(fr) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
