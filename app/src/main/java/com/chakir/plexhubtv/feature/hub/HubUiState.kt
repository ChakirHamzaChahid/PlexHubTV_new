package com.chakir.plexhubtv.feature.hub

import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem

data class HubUiState(
    val isLoading: Boolean = false,
    val onDeck: List<MediaItem> = emptyList(),
    val hubs: List<Hub> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
)

sealed interface HubAction {
    data object Refresh : HubAction
    data class OpenMedia(val media: MediaItem) : HubAction
    data class PlayMedia(val media: MediaItem) : HubAction
}
