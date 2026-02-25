package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Vérifie si un média est dans la Watchlist (Favoris).
 */
class IsFavoriteUseCase
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
    ) {
        operator fun invoke(
            ratingKey: String,
            serverId: String,
        ): Flow<Boolean> {
            return favoritesRepository.isFavorite(ratingKey, serverId)
        }

        fun anyOf(ratingKeys: List<String>): Flow<Boolean> {
            return favoritesRepository.isFavoriteAny(ratingKeys)
        }
    }
