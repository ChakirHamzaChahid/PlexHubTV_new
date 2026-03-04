package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import javax.inject.Inject

/**
 * Marque un élément comme Vu ou Non Vu.
 */
class ToggleWatchStatusUseCase
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
    ) {
        suspend operator fun invoke(
            media: MediaItem,
            isWatched: Boolean = !media.isWatched,
        ): Result<Unit> {
            return playbackRepository.toggleWatchStatus(media, isWatched)
        }
    }
