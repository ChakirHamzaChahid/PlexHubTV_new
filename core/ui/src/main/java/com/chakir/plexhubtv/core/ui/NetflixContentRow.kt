package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items // ✅ items depuis compose standard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState // ✅ State reste dans foundation
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn // ✅ Depuis tv-material
import androidx.compose.foundation.lazy.LazyRow // ✅ Depuis tv-material (pas foundation)
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaItem

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NetflixContentRow(
    title: String,
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    cardType: CardType = CardType.POSTER,
    onItemClick: (MediaItem) -> Unit,
    onItemPlay: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // Row Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NetflixWhite,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
        )

        // Horizontal List — LazyRow for proper D-Pad navigation and focus restoration
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), // 8dp per plan spec
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    @Suppress("DEPRECATION")
                    exit = { direction ->
                        when (direction) {
                            FocusDirection.Right, FocusDirection.Left -> FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    }
                }
                .focusGroup() // Group horizontal navigation within this row
        ) {
            items(
                items = items,
                key = { "${it.ratingKey}_${it.serverId}" } // Composite key for uniqueness
            ) { item ->
                NetflixMediaCard(
                    media = item,
                    cardType = cardType,
                    onClick = { onItemClick(item) },
                    onPlay = { onItemPlay(item) }
                )
            }
        }
    }
}
