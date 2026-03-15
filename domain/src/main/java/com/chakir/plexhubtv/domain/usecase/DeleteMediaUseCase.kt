package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import javax.inject.Inject

class DeleteMediaUseCase
    @Inject
    constructor(
        private val mediaDetailRepository: MediaDetailRepository,
    ) {
        /**
         * Soft-hide : masque le média du hub localement.
         * Utilise unificationId pour masquer toutes les instances cross-serveur.
         */
        suspend operator fun invoke(ratingKey: String, serverId: String): Result<Unit> {
            return mediaDetailRepository.deleteMedia(ratingKey, serverId)
        }
    }
