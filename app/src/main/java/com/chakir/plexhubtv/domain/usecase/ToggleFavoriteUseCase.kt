package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(media: MediaItem): Result<Boolean> {
        return mediaRepository.toggleFavorite(media)
    }
}
