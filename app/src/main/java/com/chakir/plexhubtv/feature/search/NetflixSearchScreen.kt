package com.chakir.plexhubtv.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakir.plexhubtv.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.ui.CardType
import com.chakir.plexhubtv.core.ui.ErrorSnackbarHost
import com.chakir.plexhubtv.core.ui.NetflixContentRow
import com.chakir.plexhubtv.core.ui.NetflixOnScreenKeyboard

@Composable
fun NetflixSearchScreen(
    state: SearchUiState,
    groupedResults: Map<MediaType, List<MediaItem>> = emptyMap(),
    onAction: (SearchAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val keyboardFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    val screenDesc = stringResource(R.string.search_screen_description)
    val searchTitle = stringResource(R.string.search_title)
    val searchEmpty = stringResource(R.string.search_empty)
    val noResultsDesc = stringResource(R.string.search_no_results_description)

    LaunchedEffect(Unit) {
        keyboardFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_search")
            .semantics { contentDescription = screenDesc },
        snackbarHost = { ErrorSnackbarHost(snackbarHostState) },
        containerColor = NetflixBlack
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 56.dp) // Leave room for TopBar overlay
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
        // Left: On-Screen Keyboard
        // UX18: Handle D-Pad DOWN to navigate from keyboard to results
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .onPreviewKeyEvent { event ->
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        when (event.key.nativeKeyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (state.searchState == SearchState.Results && groupedResults.isNotEmpty()) {
                                    try {
                                        resultsFocusRequester.requestFocus()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            val queryDesc = stringResource(R.string.search_query_description, state.query.ifEmpty { searchEmpty })
            Text(
                text = if (state.query.isEmpty()) searchTitle else state.query,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("search_input")
                    .semantics { contentDescription = queryDesc }
            )

            NetflixOnScreenKeyboard(
                onKeyPress = { key ->
                    onAction(SearchAction.QueryChange(state.query + key))
                },
                onBackspace = {
                    if (state.query.isNotEmpty()) {
                        onAction(SearchAction.QueryChange(state.query.dropLast(1)))
                    }
                },
                onClear = {
                    onAction(SearchAction.ClearQuery)
                },
                onSearch = {
                    onAction(SearchAction.ExecuteSearch)
                },
                initialFocusRequester = keyboardFocusRequester
            )
        }

        // Right: Search Results — organized by type in horizontal rows
        Box(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
        ) {
            when (state.searchState) {
                SearchState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_idle_message),
                            color = NetflixWhite.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                SearchState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NetflixWhite)
                    }
                }
                SearchState.NoResults -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_no_results")
                            .semantics { contentDescription = noResultsDesc },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_results, state.query),
                            color = NetflixWhite.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                SearchState.Error -> {
                    // Errors are now displayed via ErrorSnackbarHost
                    // Show previous results or idle state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.search_error_message),
                            color = NetflixWhite.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                SearchState.Results -> {
                    // groupedResults derived via derivedStateOf at Route level
                    // UX18: focusRequester for first result to enable keyboard → results navigation
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        modifier = Modifier.focusRequester(resultsFocusRequester)
                    ) {
                        groupedResults.forEach { (type, items) ->
                            item(key = "search_row_${type.name}") {
                                val title = when (type) {
                                    MediaType.Movie -> stringResource(R.string.search_type_movies)
                                    MediaType.Show -> stringResource(R.string.search_type_shows)
                                    MediaType.Episode -> stringResource(R.string.search_type_episodes)
                                    MediaType.Season -> stringResource(R.string.search_type_seasons)
                                    else -> stringResource(R.string.search_type_results)
                                }
                                val cardType = when (type) {
                                    MediaType.Episode -> CardType.WIDE
                                    else -> CardType.POSTER
                                }
                                NetflixContentRow(
                                    title = title,
                                    items = items,
                                    cardType = cardType,
                                    onItemClick = { onAction(SearchAction.OpenMedia(it)) },
                                    onItemPlay = { onAction(SearchAction.OpenMedia(it)) }
                                )
                            }
                        }
                    }
                }
            }
        }
        } // Close Row
    } // Close Scaffold
}
