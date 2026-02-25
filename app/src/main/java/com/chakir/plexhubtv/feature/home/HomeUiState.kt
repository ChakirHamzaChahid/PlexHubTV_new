package com.chakir.plexhubtv.feature.home

import com.chakir.plexhubtv.core.model.MediaItem

/**
 * État de l'UI pour l'écran d'accueil (Hero Billboard uniquement).
 * Les hubs et favoris sont désormais dans HubUiState.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: List<MediaItem> = emptyList(),
)

sealed interface HomeAction {
    data object Refresh : HomeAction

    data class OpenMedia(val media: MediaItem) : HomeAction

    data class PlayMedia(val media: MediaItem) : HomeAction
}
