package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import javax.inject.Inject

/**
 * Ajoute ou retire un élément de la Watchlist globale Plex (Cloud).
 */
class ToggleWatchlistUseCase @Inject constructor(
    private val watchlistRepository: WatchlistRepository
) {
    suspend operator fun invoke(ratingKey: String, isCurrentlyInWatchlist: Boolean): Result<Unit> {
        return if (isCurrentlyInWatchlist) {
            watchlistRepository.removeFromWatchlist(ratingKey)
        } else {
            watchlistRepository.addToWatchlist(ratingKey)
        }
    }
}
