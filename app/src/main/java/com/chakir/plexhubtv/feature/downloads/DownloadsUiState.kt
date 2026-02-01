package com.chakir.plexhubtv.feature.downloads

import com.chakir.plexhubtv.domain.model.MediaItem

/**
 * État de l'UI pour les téléchargements.
 */
data class DownloadsUiState(
    val isLoading: Boolean = false,
    val downloads: List<MediaItem> = emptyList(),
    val error: String? = null
)

sealed interface DownloadsAction {
    data object Refresh : DownloadsAction
    data class PlayDownload(val media: MediaItem) : DownloadsAction
    data class DeleteDownload(val mediaId: String) : DownloadsAction
    data class RetryDownload(val mediaId: String) : DownloadsAction
}
