package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import javax.inject.Inject

/**
 * Ajoute ou retire un élément des favoris locaux.
 */
class ToggleFavoriteUseCase
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
    ) {
        suspend operator fun invoke(media: MediaItem): Result<Boolean> {
            return favoritesRepository.toggleFavorite(media)
        }
    }
