package com.chakir.plexhubtv.feature.details

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.core.model.UnifiedSeason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour la vue Détail Média.
 * Contient le média principal, les saisons (si série), et les items similaires.
 * Les erreurs sont maintenant émises via errorEvents channel pour une gestion centralisée.
 */
@Immutable
data class MediaDetailUiState(
    val isLoading: Boolean = false,
    val media: MediaItem? = null,
    val seasons: ImmutableList<MediaItem> = persistentListOf(), // Only if media is Show
    val unifiedSeasons: ImmutableList<UnifiedSeason> = persistentListOf(), // Cross-server unified seasons
    val similarItems: ImmutableList<MediaItem> = persistentListOf(),
    val collections: ImmutableList<com.chakir.plexhubtv.core.model.Collection> = persistentListOf(),
    val showSourceSelection: Boolean = false,
    val selectedPlaybackItem: MediaItem? = null,
    val isOffline: Boolean = false,
    val isEnriching: Boolean = false, // Indicates if we are currently searching for other servers
    val isLoadingCollections: Boolean = false, // Indicates if we are currently loading collections
    // isServerOwned supprimé : le soft-hide est disponible pour tous les utilisateurs
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val showAddToPlaylist: Boolean = false,
    val availablePlaylists: ImmutableList<Playlist> = persistentListOf(),
    val isLoadingPlaylists: Boolean = false,
    val isRefreshingMetadata: Boolean = false,
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

    data class PlayExtra(val extra: com.chakir.plexhubtv.core.model.Extra) : MediaDetailEvent

    data object Retry : MediaDetailEvent

    data class OpenPerson(val personName: String) : MediaDetailEvent

    data object DeleteClicked : MediaDetailEvent

    data object ConfirmDelete : MediaDetailEvent

    data object DismissDeleteDialog : MediaDetailEvent

    data object AddToPlaylistClicked : MediaDetailEvent

    data object DismissAddToPlaylist : MediaDetailEvent

    data class AddToPlaylist(val playlistId: String, val serverId: String) : MediaDetailEvent

    data class CreatePlaylistAndAdd(val title: String) : MediaDetailEvent

    data object RefreshMetadata : MediaDetailEvent
}
