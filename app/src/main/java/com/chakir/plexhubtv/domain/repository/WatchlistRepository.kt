package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.MediaItem

/**
 * Interface de gestion de la Watchlist globale Plex (https://watch.plex.tv/watchlist).
 */
interface WatchlistRepository {
    /** Récupère la Watchlist unifiée. */
    suspend fun getWatchlist(): Result<List<MediaItem>>
    
    /** Ajoute un élément à la Watchlist Plex. */
    suspend fun addToWatchlist(ratingKey: String): Result<Unit>
    
    /** Retire un élément de la Watchlist Plex. */
    suspend fun removeFromWatchlist(ratingKey: String): Result<Unit>
}
