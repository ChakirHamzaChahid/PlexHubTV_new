package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionUseCase
    @Inject
    constructor(
        private val mediaDetailRepository: MediaDetailRepository,
    ) {
        operator fun invoke(
            collectionId: String,
            serverId: String,
        ): Flow<Collection?> {
            return mediaDetailRepository.getCollection(collectionId, serverId)
        }
    }
