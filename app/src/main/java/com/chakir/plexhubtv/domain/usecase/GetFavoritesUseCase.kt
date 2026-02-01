package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Récupère le flux des favoris (Watchlist locale).
 */
class GetFavoritesUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<List<MediaItem>> {
        return mediaRepository.getFavorites()
    }
}
