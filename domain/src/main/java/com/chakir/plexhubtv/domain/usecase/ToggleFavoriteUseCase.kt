package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Ajoute ou retire un élément des favoris locaux.
 */
class ToggleFavoriteUseCase
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
    ) {
        suspend operator fun invoke(media: MediaItem): Result<Boolean> {
            return mediaRepository.toggleFavorite(media)
        }
    }
