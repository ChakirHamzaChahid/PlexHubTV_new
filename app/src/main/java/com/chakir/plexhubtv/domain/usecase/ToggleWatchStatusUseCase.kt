package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Marque un élément comme Vu ou Non Vu.
 */
class ToggleWatchStatusUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(media: MediaItem, isWatched: Boolean = !media.isWatched): Result<Unit> {
        return mediaRepository.toggleWatchStatus(media, isWatched)
    }
}
