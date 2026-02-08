package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.aggregation.MediaDeduplicator
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.FavoritesRepository
import com.chakir.plexhubtv.domain.repository.HubsRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.repository.WatchlistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl
    @Inject
    constructor(
        private val mediaDao: MediaDao,
        private val mapper: MediaMapper,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDeduplicator: MediaDeduplicator,
        private val onDeckRepository: OnDeckRepository,
        private val hubsRepository: HubsRepository,
        private val favoritesRepository: FavoritesRepository,
        private val watchlistRepository: WatchlistRepository,
        private val mediaDetailRepository: MediaDetailRepository,
        private val playbackRepository: PlaybackRepository,
    ) : MediaRepository {
        override fun getUnifiedOnDeck(): Flow<List<MediaItem>> {
            return onDeckRepository.getUnifiedOnDeck()
        }

        override fun getUnifiedHubs(): Flow<List<Hub>> {
            return hubsRepository.getUnifiedHubs()
        }

        override suspend fun getMediaDetail(
            ratingKey: String,
            serverId: String,
        ): Result<MediaItem> {
            return mediaDetailRepository.getMediaDetail(ratingKey, serverId)
        }

        override suspend fun getSeasonEpisodes(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            return mediaDetailRepository.getSeasonEpisodes(ratingKey, serverId)
        }

        override suspend fun getShowSeasons(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            return mediaDetailRepository.getShowSeasons(ratingKey, serverId)
        }

        override suspend fun getSimilarMedia(
            ratingKey: String,
            serverId: String,
        ): Result<List<MediaItem>> {
            return mediaDetailRepository.getSimilarMedia(ratingKey, serverId)
        }

        override fun getMediaCollections(
            ratingKey: String,
            serverId: String,
        ): Flow<List<com.chakir.plexhubtv.core.model.Collection>> {
            return mediaDetailRepository.getMediaCollections(ratingKey, serverId)
        }

        override fun getCollection(
            collectionId: String,
            serverId: String,
        ): Flow<com.chakir.plexhubtv.core.model.Collection?> {
            return mediaDetailRepository.getCollection(collectionId, serverId)
        }

        override suspend fun toggleWatchStatus(
            media: MediaItem,
            isWatched: Boolean,
        ): Result<Unit> {
            return playbackRepository.toggleWatchStatus(media, isWatched)
        }

        override suspend fun updatePlaybackProgress(
            media: MediaItem,
            positionMs: Long,
        ): Result<Unit> {
            return playbackRepository.updatePlaybackProgress(media, positionMs)
        }

        override suspend fun getNextMedia(currentItem: MediaItem): MediaItem? {
            return playbackRepository.getNextMedia(currentItem)
        }

        override suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem? {
            return playbackRepository.getPreviousMedia(currentItem)
        }

        override fun getFavorites(): Flow<List<MediaItem>> {
            return favoritesRepository.getFavorites()
        }

        override fun isFavorite(
            ratingKey: String,
            serverId: String,
        ): Flow<Boolean> {
            return favoritesRepository.isFavorite(ratingKey, serverId)
        }

        override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> {
            return favoritesRepository.toggleFavorite(media)
        }

        override suspend fun syncWatchlist(): Result<Unit> {
            return watchlistRepository.syncWatchlist()
        }

        override fun getWatchHistory(
            limit: Int,
            offset: Int,
        ): Flow<List<MediaItem>> {
            return playbackRepository.getWatchHistory(limit, offset)
        }

        override suspend fun getUnifiedLibrary(mediaType: String): Result<List<MediaItem>> =
            coroutineScope {
                try {
                    val entities = mediaDao.getAllMediaByType(mediaType).first()
                    val servers = authRepository.getServers().getOrNull() ?: emptyList()
                    val ownedServerIds = servers.filter { it.isOwned }.map { it.clientIdentifier }.toSet()

                    val items =
                        entities.map { entity ->
                            val server = servers.find { it.clientIdentifier == entity.serverId }
                            val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null

                            val domain = mapper.mapEntityToDomain(entity)

                            if (server != null && baseUrl != null) {
                                mediaUrlResolver.resolveUrls(domain, baseUrl, server.accessToken).copy(
                                    baseUrl = baseUrl,
                                    accessToken = server.accessToken,
                                )
                            } else {
                                domain
                            }
                        }

                    val unifiedItems = mediaDeduplicator.deduplicate(items, ownedServerIds, servers)
                    Result.success(unifiedItems)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun searchMedia(query: String): Result<List<MediaItem>> =
            coroutineScope {
                try {
                    val movies = async { mediaDao.searchMedia(query, "movie") }
                    val shows = async { mediaDao.searchMedia(query, "show") }

                    val allEntities = movies.await() + shows.await()
                    val servers = authRepository.getServers().getOrNull() ?: emptyList()

                    val items =
                        allEntities.map { entity ->
                            val server = servers.find { it.clientIdentifier == entity.serverId }
                            val baseUrl = if (server != null) connectionManager.getCachedUrl(server.clientIdentifier) ?: server.address else null
                            val token = server?.accessToken

                            val domain = mapper.mapEntityToDomain(entity)
                            if (server != null && baseUrl != null) {
                                mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                                    baseUrl = baseUrl,
                                    accessToken = token,
                                )
                            } else {
                                domain
                            }
                        }
                    Result.success(items)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun updateStreamSelection(
            serverId: String,
            partId: String,
            audioStreamId: String?,
            subtitleStreamId: String?,
        ): Result<Unit> {
            return playbackRepository.updateStreamSelection(serverId, partId, audioStreamId, subtitleStreamId)
        }
    }
