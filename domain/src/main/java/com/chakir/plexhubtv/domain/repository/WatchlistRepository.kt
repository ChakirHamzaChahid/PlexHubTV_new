package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Interface de gestion de la Watchlist globale Plex (https://watch.plex.tv/watchlist).
 */
interface WatchlistRepository {
    /** Récupère la Watchlist unifiée (appel réseau). */
    suspend fun getWatchlist(): Result<List<MediaItem>>

    /** Observe tous les items watchlist depuis le cache local (Room). */
    fun getWatchlistItems(): Flow<List<MediaItem>>

    /** Ajoute un élément à la Watchlist Plex. */
    suspend fun addToWatchlist(ratingKey: String): Result<Unit>

    /** Retire un élément de la Watchlist Plex. */
    suspend fun removeFromWatchlist(ratingKey: String): Result<Unit>

    /** Synchronise la Watchlist globale Plex avec le cache local et les favoris. */
    suspend fun syncWatchlist(): Result<Unit>
}
