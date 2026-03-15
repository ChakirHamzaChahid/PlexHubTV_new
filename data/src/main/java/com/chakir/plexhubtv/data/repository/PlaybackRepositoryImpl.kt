package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaUnifiedDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.ApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.paging.map
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class PlaybackRepositoryImpl
    @Inject
    constructor(
        private val serverClientResolver: ServerClientResolver,
        private val authRepository: AuthRepository,
        private val connectionManager: ConnectionManager,
        private val api: PlexApiService,
        private val mediaDao: MediaDao,
        private val mediaUnifiedDao: MediaUnifiedDao,
        private val apiCache: ApiCache,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDetailRepository: MediaDetailRepository,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PlaybackRepository {
        private data class ProgressEntry(
            val ratingKey: String,
            val serverId: String,
            val viewOffset: Long,
            val lastViewedAt: Long,
        )

        private val progressCache = ConcurrentHashMap<String, ProgressEntry>()

        override suspend fun toggleWatchStatus(
            media: MediaItem,
            isWatched: Boolean,
        ): Result<Unit> {
            val client = serverClientResolver.getClient(media.serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server ${media.serverId} unavailable"))

            return safeApiCall("toggleWatchStatus") {
                val response = if (isWatched) client.scrobble(media.ratingKey) else client.unscrobble(media.ratingKey)

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("API Error: ${response.code()}")
                }

                // Invalidate cache to reflect new watch status immediately
                val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
                apiCache.evict(cacheKey)
            }
        }

        override suspend fun updatePlaybackProgress(
            media: MediaItem,
            positionMs: Long,
        ): Result<Unit> {
            val client = serverClientResolver.getClient(media.serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server ${media.serverId} unavailable"))

            val result = safeApiCall("updatePlaybackProgress") {
                val response =
                    client.updateTimeline(
                        ratingKey = media.ratingKey,
                        state = "playing", // Typically called during playback
                        timeMs = positionMs,
                        durationMs = media.durationMs ?: 0L,
                    )

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("API Error: ${response.code()}")
                }

                // Invalidate cache so that "Resume" position is updated
                val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
                apiCache.evict(cacheKey)
            }

            // Cache progress in memory — flushed to DB once at playback stop to avoid
            // Room PagingSource invalidation every 30s (critical for Mi Box S performance).
            val key = "${media.serverId}:${media.ratingKey}"
            progressCache[key] = ProgressEntry(
                ratingKey = media.ratingKey,
                serverId = media.serverId,
                viewOffset = positionMs,
                lastViewedAt = System.currentTimeMillis(),
            )

            return result
        }

        override suspend fun sendStoppedTimeline(
            media: MediaItem,
            positionMs: Long,
        ): Result<Unit> {
            val client = serverClientResolver.getClient(media.serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server ${media.serverId} unavailable"))

            return safeApiCall("sendStoppedTimeline") {
                val response = client.updateTimeline(
                    ratingKey = media.ratingKey,
                    state = "stopped",
                    timeMs = positionMs,
                    durationMs = media.durationMs ?: 0L,
                )
                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("API Error: ${response.code()}")
                }
                apiCache.evict("${media.serverId}:/library/metadata/${media.ratingKey}")
            }
        }

        override suspend fun flushLocalProgress() {
            val entries = progressCache.values.toList()
            progressCache.clear()
            for (entry in entries) {
                try {
                    mediaDao.updateProgress(
                        ratingKey = entry.ratingKey,
                        serverId = entry.serverId,
                        viewOffset = entry.viewOffset,
                        lastViewedAt = entry.lastViewedAt,
                    )
                    // Surgical update: mirror progress into media_unified (only if bestRatingKey matches)
                    mediaUnifiedDao.updateProgress(
                        ratingKey = entry.ratingKey,
                        serverId = entry.serverId,
                        viewOffset = entry.viewOffset,
                        lastViewedAt = entry.lastViewedAt,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to flush progress for ${entry.ratingKey}")
                }
            }
            if (entries.isNotEmpty()) {
                Timber.d("Flushed ${entries.size} progress entries to DB")
            }
        }

        override suspend fun getNextMedia(currentItem: MediaItem): MediaItem? {
            if (currentItem.type != MediaType.Episode) return null
            
            // 1. Try next episode in current season
            val episodes = mediaDetailRepository.getSeasonEpisodes(currentItem.parentRatingKey ?: "", currentItem.serverId).getOrNull()
            if (episodes != null) {
                val currentIndex = episodes.indexOfFirst { it.ratingKey == currentItem.ratingKey }
                if (currentIndex != -1 && currentIndex < episodes.size - 1) {
                    return episodes[currentIndex + 1]
                }
            }

            // 2. Try first episode of next season
            val showId = currentItem.grandparentRatingKey ?: return null
            val seasons = mediaDetailRepository.getShowSeasons(showId, currentItem.serverId).getOrNull()?.sortedBy { it.seasonIndex ?: Int.MAX_VALUE } ?: return null
            
            val currentSeasonIndex = seasons.indexOfFirst { it.ratingKey == currentItem.parentRatingKey }
            if (currentSeasonIndex != -1 && currentSeasonIndex < seasons.size - 1) {
                val nextSeason = seasons[currentSeasonIndex + 1]
                val nextSeasonEpisodes = mediaDetailRepository.getSeasonEpisodes(nextSeason.ratingKey, currentItem.serverId).getOrNull()
                return nextSeasonEpisodes?.minByOrNull { it.episodeIndex ?: Int.MAX_VALUE }
            }
            
            return null
        }

        override suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem? {
            if (currentItem.type != MediaType.Episode) return null
            val episodes = mediaDetailRepository.getSeasonEpisodes(currentItem.parentRatingKey ?: "", currentItem.serverId).getOrNull() ?: return null
            val currentIndex = episodes.indexOfFirst { it.ratingKey == currentItem.ratingKey }
            return if (currentIndex > 0) episodes[currentIndex - 1] else null
        }

        override fun getWatchHistory(
            limit: Int,
            offset: Int,
        ): Flow<List<MediaItem>> {
            return mediaDao.getHistory(limit, offset).map { entities ->
                // Build server map once for O(1) lookups (memory-cached in AuthRepository)
                val serverMap = authRepository.getServers().getOrNull()
                    ?.associateBy { it.clientIdentifier }
                    .orEmpty()

                entities.map { entity ->
                    val domain = mapper.mapEntityToDomain(entity)
                    val server = serverMap[entity.serverId]
                    // Always use current connection URL, not stale resolvedBaseUrl
                    val baseUrl = server?.let {
                        connectionManager.getCachedUrl(it.clientIdentifier) ?: it.address
                    }

                    if (server != null && baseUrl != null) {
                        mediaUrlResolver.resolveUrls(domain, baseUrl, server.accessToken).copy(
                            baseUrl = baseUrl,
                            accessToken = server.accessToken,
                        )
                    } else {
                        domain
                    }
                }
            }
        }

        override fun getWatchHistoryPaged(): Flow<androidx.paging.PagingData<MediaItem>> {
            return androidx.paging.Pager(
                config = androidx.paging.PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 40
                ),
                pagingSourceFactory = { mediaDao.getHistoryPaged() }
            ).flow.map { pagingData ->
                // Build server map once for O(1) lookups (memory-cached in AuthRepository)
                val serverMap = authRepository.getServers().getOrNull()
                    ?.associateBy { it.clientIdentifier }
                    .orEmpty()

                pagingData.map { entity ->
                    val domain = mapper.mapEntityToDomain(entity)
                    val server = serverMap[entity.serverId]
                    // Always use current connection URL, not stale resolvedBaseUrl
                    val baseUrl = server?.let {
                        connectionManager.getCachedUrl(it.clientIdentifier) ?: it.address
                    }

                    if (server != null && baseUrl != null) {
                        mediaUrlResolver.resolveUrls(domain, baseUrl, server.accessToken).copy(
                            baseUrl = baseUrl,
                            accessToken = server.accessToken,
                        )
                    } else {
                        domain
                    }
                }
            }
        }

        override suspend fun updateStreamSelection(
            serverId: String,
            partId: String,
            audioStreamId: String?,
            subtitleStreamId: String?,
        ): Result<Unit> {
            val client = serverClientResolver.getClient(serverId)
                ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

            return safeApiCall("updateStreamSelection") {
                val url = "${client.baseUrl.trimEnd('/')}/library/parts/$partId"
                val response =
                    api.putStreamSelection(
                        url = url,
                        audioStreamID = audioStreamId,
                        subtitleStreamID = subtitleStreamId,
                        token = client.server.accessToken ?: "",
                    )

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("API Error: ${response.code()}")
                }
            }
        }

    }
