package com.chakir.plexhubtv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.home.components.CardType
import com.chakir.plexhubtv.feature.home.components.NetflixContentRow
import com.chakir.plexhubtv.feature.home.components.NetflixHeroBillboard

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

    // Notify TopBar about scroll state for transparency
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
            .collect { isScrolled ->
                onScrollStateChanged(isScrolled)
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Hero Billboard (Top of the screen)
        item {
             val heroItems = remember(onDeck) { onDeck.take(10) }
             NetflixHeroBillboard(
                 items = heroItems,
                 onPlay = { onAction(HomeAction.PlayMedia(it)) },
                 onInfo = { onAction(HomeAction.OpenMedia(it)) }
             )
        }

        // 2. Continue Watching Row (if any items left after hero or just repeat?)
        // Netflix usually repeats "Continue Watching" as the first row if relevant.
        // Let's filter onDeck items that have progress.
        item {
            val continueWatchingItems = remember(onDeck) {
                onDeck.filter { (it.playbackPositionMs ?: 0) > 0 }
            }
            if (continueWatchingItems.isNotEmpty()) {
                NetflixContentRow(
                    title = "Continue Watching",
                    items = continueWatchingItems,
                    cardType = CardType.WIDE, // WIDE cards for continue watching
                    onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                    onItemPlay = { onAction(HomeAction.PlayMedia(it)) }
                )
            }
        }

        // 3. My List (Favorites)
        if (favorites.isNotEmpty()) {
            item {
                NetflixContentRow(
                    title = "My List",
                    items = favorites,
                    cardType = CardType.POSTER,
                    onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                    onItemPlay = { onAction(HomeAction.PlayMedia(it)) }
                )
            }
        }

        // 4. Hubs (Recently Added, Genres, etc.)
        items(
            items = hubs,
            key = { it.hubIdentifier ?: it.title ?: it.key }
        ) { hub ->
            NetflixContentRow(
                title = hub.title ?: "",
                items = hub.items,
                cardType = CardType.POSTER,
                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                onItemPlay = { onAction(HomeAction.PlayMedia(it)) }
            )
        }
        
        // Bottom Spacer for overscan
        item { 
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}
