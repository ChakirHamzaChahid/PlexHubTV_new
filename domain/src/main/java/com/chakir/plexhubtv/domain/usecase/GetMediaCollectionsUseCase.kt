package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Récupère toutes les collections auxquelles un média appartient.
 *
 * Agrège les collections de tous les serveurs où le film existe (via unificationId).
 * Permet d'afficher plusieurs collections sous forme de chips cliquables.
 */
class GetMediaCollectionsUseCase
    @Inject
    constructor(
        private val mediaDetailRepository: MediaDetailRepository,
    ) {
        operator fun invoke(
            ratingKey: String,
            serverId: String,
        ): Flow<List<Collection>> {
            return mediaDetailRepository.getMediaCollections(ratingKey, serverId)
        }
    }
