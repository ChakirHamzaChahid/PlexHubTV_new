package com.chakir.plexhubtv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.focusRequester
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.ui.CardType
import com.chakir.plexhubtv.core.ui.NetflixContentRow
import com.chakir.plexhubtv.core.ui.NetflixHeroBillboard

@Composable
fun NetflixHomeContent(
    onDeck: List<MediaItem>,
    hubs: List<Hub>,
    favorites: List<MediaItem>,
    onAction: (HomeAction) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> onScrollStateChanged(index > 0) }
    }

    // FocusRequesters for navigation flow
    val firstRowFocusRequester = remember { FocusRequester() }
    val billboardButtonsFocusRequester = remember { FocusRequester() }

    // Request initial focus on billboard Play button ONCE, from outside LazyColumn
    // so it survives item recycling (remember state here is never disposed)
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus) {
            billboardButtonsFocusRequester.requestFocus()
            hasRequestedInitialFocus = true
        }
    }

    // Scroll to top when billboard buttons regain focus (e.g. UP from first row)
    var isBillboardFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isBillboardFocused) {
        if (isBillboardFocused) {
            listState.animateScrollToItem(0)
        }
    }

    // Calculate which rows will be visible (outside LazyColumn scope)
    val continueWatchingItems = onDeck.filter { (it.playbackPositionMs ?: 0) > 0 }
    val hasContinueWatching = continueWatchingItems.isNotEmpty()
    val hasMyList = favorites.isNotEmpty()
    val isFirstHub = !hasContinueWatching && !hasMyList

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_home")
            .semantics { contentDescription = "Écran d'accueil" },
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // TopBar Spacer
        item(key = "topbar_spacer") {
            Spacer(modifier = Modifier.height(72.dp))
        }

        // 1. Hero Billboard
        item(key = "hero_billboard") {
            val heroItems = remember(onDeck) { onDeck.take(10) }
            Box(
                modifier = Modifier.onFocusChanged { focusState ->
                    isBillboardFocused = focusState.hasFocus
                }
            ) {
                NetflixHeroBillboard(
                    items = heroItems,
                    onPlay = { onAction(HomeAction.PlayMedia(it)) },
                    onInfo = { onAction(HomeAction.OpenMedia(it)) },
                    onNavigateDown = { firstRowFocusRequester.requestFocus() },
                    buttonsFocusRequester = billboardButtonsFocusRequester
                )
            }
        }

        // 2. Continue Watching Row
        if (hasContinueWatching) {
            item(key = "continue_watching") {
                Box(
                    modifier = Modifier.onKeyEvent { keyEvent ->
                        // UP from first row → back to billboard buttons
                        if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                        ) {
                            billboardButtonsFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                ) {
                    NetflixContentRow(
                        title = "Continue Watching",
                        items = continueWatchingItems,
                        cardType = CardType.WIDE,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        rowId = "on_deck",
                        modifier = Modifier.focusRequester(firstRowFocusRequester)
                    )
                }
            }
        }

        // 3. My List (Favorites)
        if (hasMyList) {
            item(key = "my_list") {
                Box(
                    modifier = if (!hasContinueWatching) {
                        // This is the first row - handle UP navigation
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                            ) {
                                billboardButtonsFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        Modifier
                    }
                ) {
                    NetflixContentRow(
                        title = "My List",
                        items = favorites,
                        cardType = CardType.POSTER,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        rowId = "my_list",
                        modifier = if (!hasContinueWatching) Modifier.focusRequester(firstRowFocusRequester) else Modifier
                    )
                }
            }
        }

        // 4. Hubs (Recently Added, Genres, etc.)
        hubs.forEachIndexed { index, hub ->
            item(key = hub.hubIdentifier ?: hub.title ?: hub.key ?: "hub_$index") {
                val isFirstItem = isFirstHub && index == 0
                Box(
                    modifier = if (isFirstItem) {
                        Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                            ) {
                                billboardButtonsFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        Modifier
                    }
                ) {
                    NetflixContentRow(
                        title = hub.title ?: "",
                        items = hub.items,
                        cardType = CardType.POSTER,
                        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
                        rowId = hub.hubIdentifier ?: hub.title?.lowercase()?.replace(" ", "_") ?: "hub_$index",
                        modifier = if (isFirstItem) Modifier.focusRequester(firstRowFocusRequester) else Modifier
                    )
                }
            }
        }

        // Bottom Spacer for overscan
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}
