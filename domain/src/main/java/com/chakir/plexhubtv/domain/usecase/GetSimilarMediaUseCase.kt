package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Récupère les médias similaires (suggestions, "More like this").
 */
class GetSimilarMediaUseCase
    @Inject
    constructor(
        private val mediaRepository: MediaRepository,
    ) {
        suspend operator fun invoke(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            return mediaRepository.getSimilarMedia(ratingKey, serverId)
        }
    }
