package com.chakir.plexhubtv.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import com.chakir.plexhubtv.core.ui.SpotlightGrid
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NetflixHomeContent(
    focusedItem: MediaItem?,
    hubs: ImmutableList<Hub>,
    favorites: ImmutableList<MediaItem>,
    suggestions: ImmutableList<MediaItem>,
    onDeck: ImmutableList<MediaItem>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    showContinueWatching: Boolean = true,
    showMyList: Boolean = true,
    showSuggestions: Boolean = true,
    homeRowOrder: ImmutableList<String> = persistentListOf("continue_watching", "my_list", "suggestions"),
    useSpotlightGrid: Boolean = false,
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

    // Determine which special rows are active (visible + have data)
    val rowAvailability = mapOf(
        "continue_watching" to (showContinueWatching && onDeck.isNotEmpty()),
        "my_list" to (showMyList && favorites.isNotEmpty()),
        "suggestions" to (showSuggestions && suggestions.isNotEmpty()),
    )
    val activeSpecialRows = homeRowOrder.filter { rowAvailability[it] == true }

    // Pre-compute row indices for snap-to-row scrolling
    val specialRowIndices = activeSpecialRows.mapIndexed { index, id -> id to index }.toMap()
    val hubStartIdx = activeSpecialRows.size

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
        // Hero section — SpotlightGrid (interactive) or HomeHeader (passive)
        val spotlightItems = remember(onDeck, hubs) {
            val candidates = mutableListOf<MediaItem>()
            candidates.addAll(onDeck.take(3))
            if (candidates.size < 3) {
                hubs.flatMap { it.items }.take(3 - candidates.size).let { candidates.addAll(it) }
            }
            candidates.take(3)
        }

        if (useSpotlightGrid && spotlightItems.size >= 3) {
            SpotlightGrid(
                items = spotlightItems,
                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                onItemFocused = { onFocusChanged?.invoke(it) },
                modifier = Modifier.fillMaxHeight(0.45f),
            )
        } else {
            HomeHeader(
                item = focusedItem,
                modifier = Modifier.fillMaxHeight(0.40f),
            )
        }

        // Staggered entrance animation — each row fades in with increasing delay
        var rowsVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { rowsVisible = true }

        // Scrollable content rows
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Special rows in user-configured order — with staggered entrance animation
            activeSpecialRows.forEachIndexed { displayIndex, rowId ->
                val isFirst = displayIndex == 0
                when (rowId) {
                    "continue_watching" -> item(key = "continue_watching") {
                        StaggeredRow(visible = rowsVisible, index = displayIndex) {
                            NetflixContentRow(
                                title = "Continue Watching",
                                items = onDeck,
                                cardType = CardType.WIDE,
                                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                                onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                                onItemFocused = {
                                    onFocusChanged?.invoke(it)
                                    focusedRowIndex = specialRowIndices["continue_watching"] ?: 0
                                    focusVersion++
                                },
                                rowId = "home_on_deck",
                                modifier = if (isFirst) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                            )
                        }
                    }
                    "my_list" -> item(key = "my_list") {
                        StaggeredRow(visible = rowsVisible, index = displayIndex) {
                            NetflixContentRow(
                                title = "My List",
                                items = favorites,
                                cardType = CardType.POSTER,
                                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                                onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                                onItemFocused = {
                                    onFocusChanged?.invoke(it)
                                    focusedRowIndex = specialRowIndices["my_list"] ?: 0
                                    focusVersion++
                                },
                                rowId = "home_my_list",
                                modifier = if (isFirst) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                            )
                        }
                    }
                    "suggestions" -> item(key = "suggestions") {
                        StaggeredRow(visible = rowsVisible, index = displayIndex) {
                            NetflixContentRow(
                                title = "Suggested for You",
                                items = suggestions,
                                cardType = CardType.POSTER,
                                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                                onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                                onItemFocused = {
                                    onFocusChanged?.invoke(it)
                                    focusedRowIndex = specialRowIndices["suggestions"] ?: 0
                                    focusVersion++
                                },
                                rowId = "home_suggestions",
                                modifier = if (isFirst) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                            )
                        }
                    }
                }
            }

            // Hub Rows — with staggered entrance animation (offset by special rows count)
            hubs.forEachIndexed { index, hub ->
                item(key = "home_hub_${hub.hubIdentifier ?: hub.title ?: index}") {
                    val isFirstRow = activeSpecialRows.isEmpty() && index == 0
                    val isEpisodeHub = hub.type == "episode"
                            || hub.items.firstOrNull()?.type == MediaType.Episode
                    val hubIdx = hubStartIdx + index
                    StaggeredRow(visible = rowsVisible, index = activeSpecialRows.size + index) {
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
}

/** Staggered entrance animation — each row fades in with an increasing delay. */
@Composable
private fun StaggeredRow(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit,
) {
    val delayMs = (index * 80).coerceAtMost(400) // cap at 400ms
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delayMs)) +
                slideInVertically(tween(300, delayMillis = delayMs)) { it / 4 },
    ) {
        content()
    }
}
