package com.chakir.plexhubtv.feature.hub

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class HubUiState(
    val isLoading: Boolean = false,
    val onDeck: ImmutableList<MediaItem> = persistentListOf(),
    val hubs: ImmutableList<Hub> = persistentListOf(),
    val favorites: ImmutableList<MediaItem> = persistentListOf(),
    val pendingRemoval: MediaItem? = null,
)

sealed interface HubAction {
    data object Refresh : HubAction
    data class OpenMedia(val media: MediaItem) : HubAction
    data class PlayMedia(val media: MediaItem) : HubAction
    data class ShowRemoveDialog(val media: MediaItem) : HubAction
    data object DismissRemoveDialog : HubAction
    data object ConfirmRemoveFromOnDeck : HubAction
}
