package com.chakir.plexhubtv.feature.details

import com.chakir.plexhubtv.domain.model.MediaItem

/**
 * État de l'UI pour la vue Détail Média.
 * Contient le média principal, les saisons (si série), et les items similaires.
 */
data class MediaDetailUiState(
    val isLoading: Boolean = false,
    val media: MediaItem? = null,
    val seasons: List<MediaItem> = emptyList(), // Only if media is Show
    val similarItems: List<MediaItem> = emptyList(),
    val collections: List<com.chakir.plexhubtv.domain.model.Collection> = emptyList(),
    val error: String? = null,
    val showSourceSelection: Boolean = false,
    val selectedPlaybackItem: MediaItem? = null,
    val isOffline: Boolean = false,
    val isEnriching: Boolean = false // Indicates if we are currently searching for other servers
)

sealed interface MediaDetailEvent {
    data object ToggleWatchStatus : MediaDetailEvent
    data object ToggleFavorite : MediaDetailEvent
    data object PlayClicked : MediaDetailEvent
    data object DownloadClicked : MediaDetailEvent
    data object ShowSourceSelection : MediaDetailEvent
    data object DismissSourceSelection : MediaDetailEvent
    data class PlaySource(val source: com.chakir.plexhubtv.domain.model.MediaSource) : MediaDetailEvent
    data class OpenSeason(val season: MediaItem) : MediaDetailEvent
    data class OpenMediaDetail(val media: MediaItem) : MediaDetailEvent
    data object Back : MediaDetailEvent
    data object Retry : MediaDetailEvent
}
