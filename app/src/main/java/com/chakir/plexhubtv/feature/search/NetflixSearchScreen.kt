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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.chakir.plexhubtv.core.designsystem.NetflixBlack
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.home.components.NetflixMediaCard
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
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left: On-Screen Keyboard
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            // Search Query Display
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
                onSearch = {
                    // Search is triggered automatically via debounce in ViewModel
                },
                initialFocusRequester = keyboardFocusRequester
            )
        }

        // Right: Search Results
        Box(
            modifier = Modifier
                .weight(0.6f)
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.results) { item ->
                            NetflixMediaCard(
                                media = item,
                                onClick = { onAction(SearchAction.OpenMedia(item)) },
                                onPlay = { onAction(SearchAction.OpenMedia(item)) },
                                onFocus = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
