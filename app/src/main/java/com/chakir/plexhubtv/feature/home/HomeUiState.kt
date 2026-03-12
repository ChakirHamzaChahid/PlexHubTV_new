package com.chakir.plexhubtv.feature.home

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour l'écran d'accueil.
 * Contient le Hero Billboard (On Deck), les hubs intégrés et les favoris.
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: ImmutableList<MediaItem> = persistentListOf(),
    val hubs: ImmutableList<Hub> = persistentListOf(),
    val favorites: ImmutableList<MediaItem> = persistentListOf(),
    val suggestions: ImmutableList<MediaItem> = persistentListOf(),
    val focusedItem: MediaItem? = null,
)

sealed interface HomeAction {
    data object Refresh : HomeAction

    data class OpenMedia(val media: MediaItem) : HomeAction

    data class PlayMedia(val media: MediaItem) : HomeAction

    data class FocusMedia(val media: MediaItem?) : HomeAction
}
