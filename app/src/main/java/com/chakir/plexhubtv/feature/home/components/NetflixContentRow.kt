package com.chakir.plexhubtv.feature.home.components

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            // Fix nested focus: Ensure column is part of focus traversal but passes focus to children
             .focusable(false)
    ) {
        // Row Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NetflixWhite,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp) // Aligned with safe area
        )

        // Horizontal List
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = items,
                key = { it.ratingKey }
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
