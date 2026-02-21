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

                val items =
                    entities.map { entity ->
                        val server = servers.find { it.clientIdentifier == entity.serverId }
                        val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                        val token = server?.accessToken

                        val mediaEntity = mediaDao.getMedia(entity.ratingKey, entity.serverId)
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

                        if (server != null && baseUrl != null) {
                            mediaUrlResolver.resolveUrls(domain, baseUrl, token ?: "").copy(
                                baseUrl = baseUrl,
                                accessToken = token,
                            )
                        } else {
                            domain
                        }
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

        override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> {
            return try {
                val isFav = favoriteDao.isFavorite(media.ratingKey, media.serverId).first()
                if (isFav) {
                    favoriteDao.deleteFavorite(media.ratingKey, media.serverId)
                    // Sync removal to Plex watchlist in background
                    applicationScope.launch(ioDispatcher) {
                        try {
                            val token = settingsDataStore.plexToken.first()
                            val clientId = settingsDataStore.clientId.first()
                            // Use GUID for global watchlist sync if available, fallback to ratingKey logic if needed (but usually GUID is safer for Plex Discover)
                            val idToSync = media.guid ?: media.ratingKey

                            if (token != null && clientId != null) {
                                if (idToSync.startsWith("plex://")) {
                                    api.removeFromWatchlist(idToSync, token, clientId)
                                } else {
                                    // If no global GUID, we can't reliably sync to Plex Watchlist (which is global)
                                    // But maybe the user wants to remove by ratingKey? Plex API usually expects `ratingKey` param to be the GUID string for Watchlist actions on metadata.provider.plex.tv
                                    Timber.w("Skipping Watchlist sync: No valid GUID for ${media.title} ($idToSync)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w("Failed to sync removal to Plex: ${e.message}")
                        }
                    }
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
                    // Sync addition to Plex watchlist in background
                    applicationScope.launch(ioDispatcher) {
                        try {
                            val token = settingsDataStore.plexToken.first()
                            val clientId = settingsDataStore.clientId.first()
                            val idToSync = media.guid ?: media.ratingKey

                            if (token != null && clientId != null) {
                                if (idToSync.startsWith("plex://")) {
                                    api.addToWatchlist(idToSync, token, clientId)
                                } else {
                                    Timber.w("Skipping Watchlist sync: No valid GUID for ${media.title} ($idToSync)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w("Failed to sync addition to Plex: ${e.message}")
                        }
                    }
                    Result.success(true)
                }
            } catch (e: Exception) {
                Result.failure(e.toAppError())
            }
        }
    }
