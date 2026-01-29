package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(ratingKey: String, serverId: String): Flow<Boolean> {
        return mediaRepository.isFavorite(ratingKey, serverId)
    }
}
