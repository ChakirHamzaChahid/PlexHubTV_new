package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import com.chakir.plexhubtv.core.designsystem.NetflixWhite
import com.chakir.plexhubtv.core.model.MediaItem

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

    val listState = rememberTvLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
        // .focusable(false) REMOVED — was blocking focus traversal to children
    ) {
        // Row Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NetflixWhite,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
        )

        // Horizontal List — TvLazyRow for proper D-Pad navigation and focus restoration
        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), // 8dp per plan spec
            pivotOffsets = PivotOffsets(parentFraction = 0.0f),
            modifier = Modifier.fillMaxWidth()
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
