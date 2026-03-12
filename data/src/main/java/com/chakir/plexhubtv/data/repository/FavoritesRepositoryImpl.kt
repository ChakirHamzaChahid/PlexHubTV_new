package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.FavoriteDao
import com.chakir.plexhubtv.core.database.FavoriteEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.aggregation.MediaDeduplicator
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class FavoritesRepositoryImpl
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val mediaDao: MediaDao,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDeduplicator: MediaDeduplicator,
        private val api: PlexApiService,
        private val settingsDataStore: SettingsDataStore,
        @com.chakir.plexhubtv.core.di.ApplicationScope private val applicationScope: CoroutineScope,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FavoritesRepository {
        override fun getFavorites(): Flow<List<MediaItem>> {
            return favoriteDao.getAllFavorites().map { entities ->
                val servers = authRepository.getServers().getOrNull() ?: emptyList()
                val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                // ISSUE #113 FIX: Batch fetch all mediaEntities to prevent N+1 query pattern
                // Group by serverId so each query uses the PK index prefix (ratingKey, serverId).
                // Typically 1-3 servers → 1-3 indexed queries instead of 1 full table scan.
                val mediaEntitiesMap = if (entities.isNotEmpty()) {
                    entities.groupBy { it.serverId }
                        .flatMap { (serverId, group) ->
                            mediaDao.getMediaByServerAndKeys(serverId, group.map { it.ratingKey })
                        }
                        .associateBy { "${it.ratingKey}|${it.serverId}" }
                } else {
                    emptyMap()
                }

                val items =
                    entities.map { entity ->
                        val server = servers.find { it.clientIdentifier == entity.serverId }
                        val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                        val token = server?.accessToken

                        // O(1) lookup from map instead of O(n) database query
                        val mediaEntity = mediaEntitiesMap["${entity.ratingKey}|${entity.serverId}"]
                        val domain =
                            if (mediaEntity != null) {
                                mapper.mapEntityToDomain(mediaEntity)
                            } else {
                                MediaItem(
                                    id = "${entity.serverId}_${entity.ratingKey}",
                                    ratingKey = entity.ratingKey,
                                    serverId = entity.serverId,
                                    title = entity.title,
                                    type =
                                        when (entity.type) {
                                            "movie" -> MediaType.Movie
                                            "show" -> MediaType.Show
                                            "episode" -> MediaType.Episode
                                            else -> MediaType.Movie
                                        },
                                    thumbUrl = entity.thumbUrl,
                                    artUrl = entity.artUrl,
                                    year = entity.year,
                                )
                            }

                        val resolved = if (server != null && baseUrl != null) {
                            mediaUrlResolver.resolveUrls(domain, baseUrl, token ?: "").copy(
                                baseUrl = baseUrl,
                                accessToken = token,
                            )
                        } else {
                            domain
                        }
                        // Override addedAt with the favorite-specific timestamp (not the Plex library date)
                        resolved.copy(addedAt = entity.addedAt)
                    }

                mediaDeduplicator.deduplicate(items, ownedServerIds, servers)
            }
        }

        override fun isFavorite(
            ratingKey: String,
            serverId: String,
        ): Flow<Boolean> {
            return favoriteDao.isFavorite(ratingKey, serverId)
        }

        override fun isFavoriteAny(ratingKeys: List<String>): Flow<Boolean> {
            return favoriteDao.isFavoriteAny(ratingKeys)
        }

        override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> {
            return try {
                val isFav = favoriteDao.isFavorite(media.ratingKey, media.serverId).first()
                if (isFav) {
                    favoriteDao.deleteFavorite(media.ratingKey, media.serverId)
                    syncWatchlistAction(media, isAdd = false)
                    Result.success(false)
                } else {
                    favoriteDao.insertFavorite(
                        FavoriteEntity(
                            ratingKey = media.ratingKey,
                            serverId = media.serverId,
                            title = media.title,
                            type = media.type.name.lowercase(),
                            thumbUrl = media.thumbUrl,
                            artUrl = media.artUrl,
                            year = media.year,
                        ),
                    )
                    syncWatchlistAction(media, isAdd = true)
                    Result.success(true)
                }
            } catch (e: Exception) {
                Result.failure(e.toAppError())
            }
        }

        /**
         * Syncs a favorite add/remove to the Plex Watchlist API in background.
         * The API expects the metadata ID (e.g. "5e16137cd88e76001f426746"),
         * NOT the full GUID URI ("plex://show/5e16137cd88e76001f426746").
         */
        private fun syncWatchlistAction(media: MediaItem, isAdd: Boolean) {
            val guid = media.guid ?: return
            if (!guid.startsWith("plex://")) {
                Timber.w("Watchlist: skip sync, no plex:// GUID for '${media.title}' (guid=$guid)")
                return
            }
            // Extract metadata ID: "plex://show/5e16137cd88e76001f426746" → "5e16137cd88e76001f426746"
            val metadataId = guid.substringAfterLast('/')

            applicationScope.launch(ioDispatcher) {
                try {
                    val token = settingsDataStore.plexToken.first()
                    val clientId = settingsDataStore.clientId.first()
                    if (token == null || clientId == null) {
                        Timber.w("Watchlist: skip sync, missing token or clientId")
                        return@launch
                    }
                    val response = if (isAdd) {
                        api.addToWatchlist(metadataId, token, clientId)
                    } else {
                        api.removeFromWatchlist(metadataId, token, clientId)
                    }
                    val action = if (isAdd) "ADD" else "REMOVE"
                    Timber.d("Watchlist $action '${media.title}' id=$metadataId → HTTP ${response.code()}")
                } catch (e: Exception) {
                    Timber.e(e, "Watchlist sync failed for '${media.title}'")
                }
            }
        }
    }
