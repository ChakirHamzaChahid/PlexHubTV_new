package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Repository pour la "Watchlist" (Liste de lecture à voir) synchronisée dans le cloud Plex.tv.
 * Gère la pagination API pour récupérer des listes complètes si nécessaire.
 */
class WatchlistRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val settingsDataStore: SettingsDataStore,
    private val mediaMapper: MediaMapper
) : WatchlistRepository {

    override suspend fun getWatchlist(): Result<List<MediaItem>> {
        return try {
            val token = settingsDataStore.plexToken.first() 
                ?: return Result.failure(Exception("Not authenticated"))
            val clientId = settingsDataStore.clientId.first() 
                ?: return Result.failure(Exception("Client ID not found"))

            val allItems = mutableListOf<MediaItem>()
            var offset = 0
            val pageSize = 100
            
            do {
                val response = api.getWatchlist(
                    token = token, 
                    clientId = clientId,
                    start = offset,
                    size = pageSize
                )
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    return Result.failure(Exception("Failed to fetch watchlist: ${response.code()} ${response.message()}"))
                }

                val container = body.mediaContainer
                val items = container?.metadata?.map { 
                    mediaMapper.mapDtoToDomain(it, "watchlist", "https://metadata.provider.plex.tv", token)
                } ?: emptyList()
                
                allItems.addAll(items)
                
                val totalSize = container?.totalSize ?: 0
                offset += pageSize
                
                // Continue if there are more items to fetch
            } while (offset < (body.mediaContainer?.totalSize ?: 0))
            
            Result.success(allItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToWatchlist(ratingKey: String): Result<Unit> {
        return try {
            val token = settingsDataStore.plexToken.first() 
                ?: return Result.failure(Exception("Not authenticated"))
            val clientId = settingsDataStore.clientId.first() 
                ?: return Result.failure(Exception("Client ID not found"))

            val response = api.addToWatchlist(
                ratingKey = ratingKey,
                token = token,
                clientId = clientId
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add to watchlist: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromWatchlist(ratingKey: String): Result<Unit> {
        return try {
            val token = settingsDataStore.plexToken.first() 
                ?: return Result.failure(Exception("Not authenticated"))
            val clientId = settingsDataStore.clientId.first() 
                ?: return Result.failure(Exception("Client ID not found"))

            val response = api.removeFromWatchlist(
                ratingKey = ratingKey,
                token = token,
                clientId = clientId
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove from watchlist: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
