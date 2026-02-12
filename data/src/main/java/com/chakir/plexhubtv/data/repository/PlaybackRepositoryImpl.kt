package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.common.exception.ServerUnavailableException
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiCache
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
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class PlaybackRepositoryImpl
    @Inject
    constructor(
        private val serverClientResolver: ServerClientResolver,
        private val mediaDao: MediaDao,
        private val plexApiCache: PlexApiCache,
        private val mapper: MediaMapper,
        private val mediaUrlResolver: MediaUrlResolver,
        private val mediaDetailRepository: MediaDetailRepository,
        @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PlaybackRepository {
        override suspend fun toggleWatchStatus(
            media: MediaItem,
            isWatched: Boolean,
        ): Result<Unit> {
            return try {
                val client = serverClientResolver.getClient(media.serverId) ?: return Result.failure(ServerUnavailableException(media.serverId))
                val response = if (isWatched) client.scrobble(media.ratingKey) else client.unscrobble(media.ratingKey)

                if (response.isSuccessful) {
                    // Invalidate cache to reflect new watch status immediately
                    val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
                    plexApiCache.evict(cacheKey)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error toggling watch status for ${media.ratingKey}")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} toggling watch status for ${media.ratingKey}")
                if (e.code() == 401) {
                    Result.failure(AuthException("Unauthorized", e))
                } else {
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling watch status for ${media.ratingKey}")
                Result.failure(e)
            }
        }

        override suspend fun updatePlaybackProgress(
            media: MediaItem,
            positionMs: Long,
        ): Result<Unit> {
            return try {
                val client = serverClientResolver.getClient(media.serverId) ?: return Result.failure(ServerUnavailableException(media.serverId))
                val response =
                    client.updateTimeline(
                        ratingKey = media.ratingKey,
                        state = "playing", // Typically called during playback
                        timeMs = positionMs,
                        durationMs = media.durationMs ?: 0L,
                    )

                if (response.isSuccessful) {
                    // Invalidate cache so that "Resume" position is updated
                    val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
                    plexApiCache.evict(cacheKey)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("API Error: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.w("Network error updating progress for ${media.ratingKey}", e)
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.w("HTTP error ${e.code()} updating progress for ${media.ratingKey}", e)
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Error updating progress for ${media.ratingKey}")
                Result.failure(e)
            } finally {
                // Update local DB regardless of network status (Optimistic UI / Offline support)
                try {
                    mediaDao.updateProgress(
                        ratingKey = media.ratingKey,
                        serverId = media.serverId,
                        viewOffset = positionMs,
                        lastViewedAt = System.currentTimeMillis(),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update local progress for ${media.ratingKey}")
                }
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
                // Fetch servers to resolve base URLs
                val servers = authRepository.getServers().getOrNull() ?: emptyList()

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
            }
        }

        override suspend fun updateStreamSelection(
            serverId: String,
            partId: String,
            audioStreamId: String?,
            subtitleStreamId: String?,
        ): Result<Unit> {
            return try {
                val client = serverClientResolver.getClient(serverId) ?: return Result.failure(ServerUnavailableException(serverId))
                val url = "${client.baseUrl}library/parts/$partId"
                val response =
                    api.putStreamSelection(
                        url = url,
                        audioStreamID = audioStreamId,
                        subtitleStreamID = subtitleStreamId,
                        token = client.server.accessToken ?: "",
                    )
                if (response.isSuccessful) Result.success(Unit) else Result.failure(Exception("API Error: ${response.code()}"))
            } catch (e: IOException) {
                Timber.e(e, "Network error updating stream selection for part $partId")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} updating stream selection for part $partId")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Error updating stream selection for part $partId")
                Result.failure(e)
            }
        }

        private suspend fun serverClientResolver.getClient(serverId: String): PlexClient? {
            val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
            val server = servers.find { it.clientIdentifier == serverId } ?: return null
            val baseUrl = connectionManager.findBestConnection(server) ?: return null
            return PlexClient(server, api, baseUrl)
        }
    }
