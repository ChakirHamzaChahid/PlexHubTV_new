package com.chakir.plexhubtv.feature.details

import com.chakir.plexhubtv.core.model.MediaItem

/**
 * État de l'UI pour la vue Détail Média.
 * Contient le média principal, les saisons (si série), et les items similaires.
 * Les erreurs sont maintenant émises via errorEvents channel pour une gestion centralisée.
 */
data class MediaDetailUiState(
    val isLoading: Boolean = false,
    val media: MediaItem? = null,
    val seasons: List<MediaItem> = emptyList(), // Only if media is Show
    val similarItems: List<MediaItem> = emptyList(),
    val collections: List<com.chakir.plexhubtv.core.model.Collection> = emptyList(),
    val showSourceSelection: Boolean = false,
    val selectedPlaybackItem: MediaItem? = null,
    val isOffline: Boolean = false,
    val isEnriching: Boolean = false, // Indicates if we are currently searching for other servers
    val isLoadingCollections: Boolean = false, // Indicates if we are currently loading collections
    val error: String? = null,
) {
    // Play button waits for enrichment — Room-first is ~5ms (imperceptible), network fallback properly blocks
    val isPlayButtonLoading: Boolean
        get() = isEnriching
}

sealed interface MediaDetailEvent {
    data object ToggleWatchStatus : MediaDetailEvent

    data object ToggleFavorite : MediaDetailEvent

    data object PlayClicked : MediaDetailEvent

    data object DownloadClicked : MediaDetailEvent

    data object ShowSourceSelection : MediaDetailEvent

    data object DismissSourceSelection : MediaDetailEvent

    data class PlaySource(val source: com.chakir.plexhubtv.core.model.MediaSource) : MediaDetailEvent

    data class OpenSeason(val season: MediaItem) : MediaDetailEvent

    data class OpenMediaDetail(val media: MediaItem) : MediaDetailEvent

    data object Back : MediaDetailEvent

    data object Retry : MediaDetailEvent
}
