package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.Collection
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(collectionId: String, serverId: String): Flow<Collection?> {
        return mediaRepository.getCollection(collectionId, serverId)
    }
}
