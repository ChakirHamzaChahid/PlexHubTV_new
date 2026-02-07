package com.chakir.plexhubtv.feature.collection

import com.chakir.plexhubtv.domain.model.Collection
import com.chakir.plexhubtv.domain.model.MediaItem

data class CollectionDetailUiState(
    val isLoading: Boolean = false,
    val collection: Collection? = null,
    val error: String? = null
)

sealed interface CollectionDetailEvent {
    data class OpenMediaDetail(val media: MediaItem) : CollectionDetailEvent
    data object Back : CollectionDetailEvent
}
