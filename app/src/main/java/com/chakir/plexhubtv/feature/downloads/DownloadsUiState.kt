package com.chakir.plexhubtv.feature.downloads

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour les téléchargements.
 */
@Immutable
data class DownloadsUiState(
    val isLoading: Boolean = false,
    val downloads: ImmutableList<MediaItem> = persistentListOf(),
    val error: String? = null,
)

sealed interface DownloadsAction {
    data object Refresh : DownloadsAction

    data class PlayDownload(val media: MediaItem) : DownloadsAction

    data class DeleteDownload(val mediaId: String) : DownloadsAction

    data class RetryDownload(val mediaId: String) : DownloadsAction
}
