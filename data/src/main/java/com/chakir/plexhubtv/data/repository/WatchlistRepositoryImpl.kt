package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.database.FavoriteDao
import com.chakir.plexhubtv.core.database.FavoriteEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import com.chakir.plexhubtv.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository pour la "Watchlist" (Liste de lecture à voir) synchronisée dans le cloud Plex.tv.
 * Gère la pagination API pour récupérer des listes complètes si nécessaire.
 */
class WatchlistRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val settingsDataStore: SettingsDataStore,
        private val mediaMapper: MediaMapper,
        private val mediaDao: MediaDao,
        private val favoriteDao: FavoriteDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WatchlistRepository {
        override suspend fun getWatchlist(): Result<List<MediaItem>> {
            val token = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("getWatchlist") {
                val allItems = mutableListOf<MediaItem>()
                var offset = 0
                val pageSize = 100

                do {
                    val response =
                        api.getWatchlist(
                            token = token,
                            clientId = clientId,
                            start = offset,
                            size = pageSize,
                        )

                    if (!response.isSuccessful) {
                        throw AppError.Network.ServerError("Failed to fetch watchlist: ${response.code()} ${response.message()}")
                    }

                    val body = response.body()
                        ?: throw AppError.Network.ServerError("Empty watchlist response")

                    val container = body.mediaContainer
                    val items =
                        container?.metadata?.map {
                            mediaMapper.mapDtoToDomain(it, "watchlist", "https://metadata.provider.plex.tv", token)
                        } ?: emptyList()

                    allItems.addAll(items)

                    val totalSize = container?.totalSize ?: 0
                    offset += pageSize

                    // Continue if there are more items to fetch
                } while (offset < (body.mediaContainer?.totalSize ?: 0))

                allItems
            }
        }

        override suspend fun addToWatchlist(ratingKey: String): Result<Unit> {
            val token = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("addToWatchlist") {
                val response =
                    api.addToWatchlist(
                        ratingKey = ratingKey,
                        token = token,
                        clientId = clientId,
                    )

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("Failed to add to watchlist: ${response.code()} ${response.message()}")
                }
            }
        }

        override suspend fun removeFromWatchlist(ratingKey: String): Result<Unit> {
            val token = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("removeFromWatchlist") {
                val response =
                    api.removeFromWatchlist(
                        ratingKey = ratingKey,
                        token = token,
                        clientId = clientId,
                    )

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("Failed to remove from watchlist: ${response.code()} ${response.message()}")
                }
            }
        }

        override suspend fun syncWatchlist(): Result<Unit> =
            withContext(ioDispatcher) {
                val token = settingsDataStore.plexToken.first()
                    ?: return@withContext Result.failure(AppError.Auth.InvalidToken("No token"))
                val clientId = settingsDataStore.clientId.first()
                    ?: return@withContext Result.failure(AppError.Auth.InvalidToken("No client ID"))

                safeApiCall("syncWatchlist") {
                    // Paginate to fetch ALL watchlist items (not just first 100)
                    val allMetadata = mutableListOf<com.chakir.plexhubtv.core.network.model.MetadataDTO>()
                    var offset = 0
                    val pageSize = 100

                    do {
                        val response = api.getWatchlist(token, clientId, start = offset, size = pageSize)

                        if (!response.isSuccessful) {
                            throw AppError.Network.ServerError("Failed to fetch watchlist: ${response.code()}")
                        }

                        val container = response.body()?.mediaContainer
                        val metadata = container?.metadata ?: emptyList()
                        allMetadata.addAll(metadata)

                        val totalSize = container?.totalSize ?: 0
                        offset += pageSize
                    } while (offset < totalSize)

                    // For each item in watchlist, check if we have it locally matching by GUID
                    allMetadata.forEachIndexed { index, item ->
                        val guid = item.guid // This is the Plex GUID e.g. plex://movie/5d77682...
                        if (guid != null) {
                            // Find local item(s) that match this GUID
                            val localItems = mediaDao.getAllMediaByGuid(guid) // Returns List<MediaEntity>

                            // Use Plex API's addedAt (seconds → ms) to preserve watchlist order.
                            // Fallback: descending index so first API item (newest) has highest value.
                            val plexAddedAt = item.addedAt?.times(1000)
                                ?: (System.currentTimeMillis() - index)

                            // Mark all matching local instances as Favorites
                            localItems.forEach { local ->
                                favoriteDao.insertFavorite(
                                    FavoriteEntity(
                                        ratingKey = local.ratingKey,
                                        serverId = local.serverId,
                                        title = local.title,
                                        type = local.type,
                                        thumbUrl = local.thumbUrl,
                                        artUrl = local.artUrl,
                                        year = local.year,
                                        addedAt = plexAddedAt,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
    }
