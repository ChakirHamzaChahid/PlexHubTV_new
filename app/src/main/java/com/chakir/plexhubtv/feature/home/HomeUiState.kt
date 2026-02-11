package com.chakir.plexhubtv.feature.home

import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem

/**
 * État de l'UI pour l'écran d'accueil.
 * Gère le chargement, la synchronisation initiale, le contenu "On Deck" et les hubs.
 * Les erreurs sont maintenant émises via errorEvents channel pour une gestion centralisée.
 *
 * NOTE: Ne PAS utiliser @Parcelize/SavedStateHandle — les listes (hubs, onDeck, favorites)
 * peuvent contenir des centaines d'items, causant TransactionTooLargeException et des freezes.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: List<MediaItem> = emptyList(),
    val hubs: List<Hub> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
)

sealed interface HomeAction {
    data object Refresh : HomeAction

    data class OpenMedia(val media: MediaItem) : HomeAction

    data class PlayMedia(val media: MediaItem) : HomeAction
}
