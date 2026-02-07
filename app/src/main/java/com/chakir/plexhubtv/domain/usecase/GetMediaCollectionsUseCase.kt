package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.Collection
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Récupère toutes les collections auxquelles un média appartient.
 * 
 * Agrège les collections de tous les serveurs où le film existe (via unificationId).
 * Permet d'afficher plusieurs collections sous forme de chips cliquables.
 */
class GetMediaCollectionsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(ratingKey: String, serverId: String): Flow<List<Collection>> {
        return mediaRepository.getMediaCollections(ratingKey, serverId)
    }
}
