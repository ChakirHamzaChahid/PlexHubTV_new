package com.chakir.plexhubtv.domain.usecase

import android.util.Log
import com.chakir.plexhubtv.core.database.FavoriteDao
import com.chakir.plexhubtv.core.database.FavoriteEntity
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Synchronise la Watchlist globale Plex (Cloud) vers la base de données locale (Room).
 *
 * Processus :
 * 1. Récupère la liste depuis Plex.tv.
 * 2. Pour chaque élément, cherche une correspondance locale (sur les serveurs connectés).
 * 3. Si trouvé, l'ajoute aux Favoris locaux pour un accès rapide.
 */
class SyncWatchlistUseCase @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val favoriteDao: FavoriteDao,
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(): Result<Int> {
        return try {
            // 1. Fetch Watchlist from Plex.tv
            val watchlistResult = watchlistRepository.getWatchlist()
            val watchlistItems = watchlistResult.getOrElse { return Result.failure(it) }

            if (watchlistItems.isEmpty()) return Result.success(0)

            var addedCount = 0

            // 2. Iterate and match with local servers (Standard for loop to avoid lambda suspend issues)
            for (watchItem in watchlistItems) {
                // Search for the item
                val searchResult = mediaRepository.searchMedia(watchItem.title)
                val searchMatches = searchResult.getOrNull() ?: emptyList()

                // Find best match
                val match = searchMatches.find { localItem ->
                    // Match by Title and Year (if available)
                    val titleMatch = localItem.title.equals(watchItem.title, ignoreCase = true)
                    val yearMatch = (watchItem.year == null) || (localItem.year == watchItem.year)
                    titleMatch && yearMatch
                }

                if (match != null) {
                    // Check if already favorite
                    val isFav = favoriteDao.isFavorite(match.ratingKey, match.serverId).first()
                    if (!isFav) {
                        favoriteDao.insertFavorite(
                            FavoriteEntity(
                                ratingKey = match.ratingKey,
                                serverId = match.serverId,
                                title = match.title,
                                type = match.type.name.lowercase(),
                                thumbUrl = match.thumbUrl,
                                artUrl = match.artUrl,
                                year = match.year,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                        addedCount++
                        Log.d("SyncWatchlist", "Added ${match.title} to Favorites from Watchlist")
                    }
                }
            }
            
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
