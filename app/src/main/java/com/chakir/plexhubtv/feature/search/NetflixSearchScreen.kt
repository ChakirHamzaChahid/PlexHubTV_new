package com.chakir.plexhubtv.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.feature.home.components.CardType
import com.chakir.plexhubtv.feature.home.components.NetflixContentRow
import com.chakir.plexhubtv.feature.search.components.NetflixOnScreenKeyboard

@Composable
fun NetflixSearchScreen(
    state: SearchUiState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        keyboardFocusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .padding(top = 56.dp) // Leave room for TopBar overlay
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left: On-Screen Keyboard
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
        ) {
            Text(
                text = if (state.query.isEmpty()) "Search" else state.query,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = NetflixWhite,
                modifier = Modifier.padding(bottom = 24.dp)
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
                onSearch = {},
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
                            text = "Type to start searching",
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found for \"${state.query}\"",
                            color = NetflixWhite.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                SearchState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error ?: "Unknown Error",
                            color = Color.Red,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                SearchState.Results -> {
                    // Group results by type for Netflix-style horizontal rows
                    val groupedResults = remember(state.results) {
                        state.results.groupBy { it.type }
                    }

                    TvLazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        pivotOffsets = PivotOffsets(parentFraction = 0.0f)
                    ) {
                        groupedResults.forEach { (type, items) ->
                            item(key = "search_row_${type.name}") {
                                val title = when (type) {
                                    MediaType.Movie -> "Movies"
                                    MediaType.Show -> "TV Shows"
                                    MediaType.Episode -> "Episodes"
                                    MediaType.Season -> "Seasons"
                                    else -> "Results"
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
    }
}
