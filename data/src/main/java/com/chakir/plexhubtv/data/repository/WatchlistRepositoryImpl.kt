package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.FavoriteDao
import com.chakir.plexhubtv.core.database.FavoriteEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.WatchlistDao
import com.chakir.plexhubtv.core.database.WatchlistEntity
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Repository pour la "Watchlist" (Liste de lecture à voir) synchronisée dans le cloud Plex.tv.
 * Stocke TOUS les items cloud (matchés localement ou non) dans la table watchlist.
 */
class WatchlistRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val settingsDataStore: SettingsDataStore,
        private val mediaMapper: MediaMapper,
        private val mediaDao: MediaDao,
        private val favoriteDao: FavoriteDao,
        private val watchlistDao: WatchlistDao,
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
                } while (offset < (body.mediaContainer?.totalSize ?: 0))

                allItems
            }
        }

        override fun getWatchlistItems(): Flow<List<MediaItem>> {
            return watchlistDao.getAllWatchlistItems().map { entities ->
                entities.map { entity ->
                    MediaItem(
                        id = if (entity.localRatingKey != null && entity.localServerId != null) {
                            "${entity.localServerId}_${entity.localRatingKey}"
                        } else {
                            "watchlist_${entity.cloudRatingKey}"
                        },
                        ratingKey = entity.localRatingKey ?: entity.cloudRatingKey,
                        serverId = entity.localServerId ?: "watchlist",
                        title = entity.title,
                        type = when (entity.type) {
                            "movie" -> MediaType.Movie
                            "show" -> MediaType.Show
                            "episode" -> MediaType.Episode
                            else -> MediaType.Movie
                        },
                        thumbUrl = entity.thumbUrl,
                        artUrl = entity.artUrl,
                        year = entity.year,
                        summary = entity.summary,
                        guid = entity.guid,
                        addedAt = entity.addedAt,
                    )
                }
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

                    Timber.i("Watchlist sync: fetched ${allMetadata.size} cloud items")

                    // Batch fetch local matches by GUID
                    val guids = allMetadata.mapNotNull { it.guid }.distinct()
                    val allLocalItems = if (guids.isNotEmpty()) {
                        mediaDao.getAllMediaByGuids(guids).groupBy { it.guid }
                    } else {
                        emptyMap()
                    }

                    // Build WatchlistEntity for ALL items (matched + unmatched)
                    val watchlistEntities = allMetadata.mapIndexed { index, dto ->
                        val guid = dto.guid
                        val localMatch = if (guid != null) allLocalItems[guid]?.firstOrNull() else null

                        WatchlistEntity(
                            cloudRatingKey = dto.ratingKey,
                            guid = guid,
                            title = dto.title,
                            type = dto.type,
                            year = dto.year,
                            thumbUrl = dto.thumb,
                            artUrl = dto.art,
                            summary = dto.summary,
                            addedAt = dto.addedAt?.times(1000)
                                ?: (System.currentTimeMillis() - index),
                            localRatingKey = localMatch?.ratingKey,
                            localServerId = localMatch?.serverId,
                            orderIndex = index,
                        )
                    }

                    // Atomically replace watchlist table
                    watchlistDao.replaceAll(watchlistEntities)

                    val matchedCount = watchlistEntities.count { it.localRatingKey != null }
                    Timber.i("Watchlist sync complete: ${watchlistEntities.size} total, $matchedCount matched locally")

                    // Backward compatibility: also sync matched items to favorites
                    val favoritesToInsert = mutableListOf<FavoriteEntity>()

                    allMetadata.forEachIndexed { index, item ->
                        val guid = item.guid
                        if (guid != null) {
                            val localItems = allLocalItems[guid] ?: emptyList()
                            val plexAddedAt = item.addedAt?.times(1000)
                                ?: (System.currentTimeMillis() - index)

                            localItems.forEach { local ->
                                favoritesToInsert.add(
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

                    if (favoritesToInsert.isNotEmpty()) {
                        favoriteDao.insertFavorites(favoritesToInsert)
                    }
                }
            }
    }
