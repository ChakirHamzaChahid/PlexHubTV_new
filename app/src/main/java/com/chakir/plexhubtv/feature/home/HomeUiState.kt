package com.chakir.plexhubtv.feature.home

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour l'écran d'accueil (Hero Billboard uniquement).
 * Les hubs et favoris sont désormais dans HubUiState.
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: ImmutableList<MediaItem> = persistentListOf(),
)

sealed interface HomeAction {
    data object Refresh : HomeAction

    data class OpenMedia(val media: MediaItem) : HomeAction

    data class PlayMedia(val media: MediaItem) : HomeAction
}
