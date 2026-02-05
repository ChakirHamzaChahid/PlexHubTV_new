package com.chakir.plexhubtv.feature.home

import com.chakir.plexhubtv.domain.model.Hub
import com.chakir.plexhubtv.domain.model.MediaItem

/**
 * État de l'UI pour l'écran d'accueil.
 * Gère le chargement, l'erreur, la synchronisation initiale, le contenu "On Deck" et les hubs.
 */
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * État de l'UI pour l'écran d'accueil.
 * Gère le chargement, l'erreur, la synchronisation initiale, le contenu "On Deck" et les hubs.
 */
@Parcelize
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: List<MediaItem> = emptyList(),
    val hubs: List<Hub> = emptyList(),
    val error: String? = null
) : Parcelable

sealed interface HomeAction {
    data object Refresh : HomeAction
    data class OpenMedia(val media: MediaItem) : HomeAction
    data class PlayMedia(val media: MediaItem) : HomeAction
}
