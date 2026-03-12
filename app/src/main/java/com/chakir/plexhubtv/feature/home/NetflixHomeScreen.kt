package com.chakir.plexhubtv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.ui.CardType
import com.chakir.plexhubtv.core.ui.HomeHeader
import com.chakir.plexhubtv.core.ui.NetflixContentRow

@Composable
fun NetflixHomeContent(
    focusedItem: MediaItem?,
    hubs: List<Hub>,
    favorites: List<MediaItem>,
    suggestions: List<MediaItem>,
    onDeck: List<MediaItem>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    onFocusChanged: ((MediaItem?) -> Unit)? = null,
) {
    val firstRowFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Initial focus goes directly to the first content row card
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus) {
            firstRowFocusRequester.requestFocus()
            hasRequestedInitialFocus = true
        }
    }

    val hasContinueWatching = onDeck.isNotEmpty()
    val hasMyList = favorites.isNotEmpty()
    val hasSuggestions = suggestions.isNotEmpty()

    // Pre-compute row indices for snap-to-row scrolling
    var nextIdx = 0
    val continueWatchingIdx = if (hasContinueWatching) nextIdx++ else -1
    val myListIdx = if (hasMyList) nextIdx++ else -1
    val suggestionsIdx = if (hasSuggestions) nextIdx++ else -1
    val hubStartIdx = nextIdx

    // Track which row has focus — snap LazyColumn so the focused row is at the top
    var focusedRowIndex by remember { mutableIntStateOf(0) }
    // Counter increments on every focus event (including horizontal navigation within the same row)
    // so the scroll snap re-triggers even when the row index hasn't changed
    var focusVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedRowIndex, focusVersion) {
        listState.scrollToItem(focusedRowIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_home")
            .semantics { contentDescription = "Écran d'accueil" }
    ) {
        // Fixed header — purely informational, shows metadata of focused item
        HomeHeader(
            item = focusedItem,
            modifier = Modifier.fillMaxHeight(0.40f),
        )

        // Scrollable content rows
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Continue Watching Row
            if (hasContinueWatching) {
                item(key = "continue_watching") {
                    NetflixContentRow(
                        title = "Continue Watching",
                        items = onDeck,
                        cardType = CardType.WIDE,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        onItemFocused = {
                            onFocusChanged?.invoke(it)
                            focusedRowIndex = continueWatchingIdx
                            focusVersion++
                        },
                        rowId = "home_on_deck",
                        modifier = Modifier.focusRequester(firstRowFocusRequester),
                    )
                }
            }

            // My List (Favorites)
            if (hasMyList) {
                item(key = "my_list") {
                    NetflixContentRow(
                        title = "My List",
                        items = favorites,
                        cardType = CardType.POSTER,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        onItemFocused = {
                            onFocusChanged?.invoke(it)
                            focusedRowIndex = myListIdx
                            focusVersion++
                        },
                        rowId = "home_my_list",
                        modifier = if (!hasContinueWatching) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                    )
                }
            }

            // Suggestions Row
            if (hasSuggestions) {
                item(key = "suggestions") {
                    NetflixContentRow(
                        title = "Suggested for You",
                        items = suggestions,
                        cardType = CardType.POSTER,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        onItemFocused = {
                            onFocusChanged?.invoke(it)
                            focusedRowIndex = suggestionsIdx
                            focusVersion++
                        },
                        rowId = "home_suggestions",
                        modifier = if (!hasContinueWatching && !hasMyList) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                    )
                }
            }

            // Hub Rows
            hubs.forEachIndexed { index, hub ->
                item(key = "home_hub_${hub.hubIdentifier ?: hub.title ?: index}") {
                    val isFirstRow = !hasContinueWatching && !hasMyList && !hasSuggestions && index == 0
                    val isEpisodeHub = hub.type == "episode"
                            || hub.items.firstOrNull()?.type == MediaType.Episode
                    val hubIdx = hubStartIdx + index
                    NetflixContentRow(
                        title = hub.title ?: "",
                        items = hub.items,
                        cardType = if (isEpisodeHub) CardType.WIDE else CardType.POSTER,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        onItemFocused = {
                            onFocusChanged?.invoke(it)
                            focusedRowIndex = hubIdx
                            focusVersion++
                        },
                        rowId = "home_hub_${hub.hubIdentifier ?: hub.title?.lowercase()?.replace(" ", "_") ?: index}",
                        modifier = if (isFirstRow) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}
