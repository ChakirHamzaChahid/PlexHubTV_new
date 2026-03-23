package com.chakir.plexhubtv.data.playback

import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.network.ApiCache
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.data.repository.ServerClientResolver
import com.chakir.plexhubtv.domain.service.PlaybackReporter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plex-specific playback reporter.
 * Communicates with Plex Media Server via timeline updates and scrobble API.
 */
@Singleton
class PlexPlaybackReporter @Inject constructor(
    private val serverClientResolver: ServerClientResolver,
    private val api: PlexApiService,
    private val apiCache: ApiCache,
) : PlaybackReporter {

    override fun matches(serverId: String): Boolean = !SourcePrefix.isNonPlex(serverId)

    override suspend fun reportProgress(item: MediaItem, positionMs: Long): Result<Unit> {
        val client = serverClientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Server ${item.serverId} unavailable"))

        return safeApiCall("PlexPlaybackReporter.reportProgress") {
            val response = client.updateTimeline(
                ratingKey = item.ratingKey,
                state = "playing",
                timeMs = positionMs,
                durationMs = item.durationMs ?: 0L,
            )
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("API Error: ${response.code()}")
            }
            apiCache.evict("${item.serverId}:/library/metadata/${item.ratingKey}")
        }
    }

    override suspend fun reportStopped(item: MediaItem, positionMs: Long): Result<Unit> {
        val client = serverClientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Server ${item.serverId} unavailable"))

        return safeApiCall("PlexPlaybackReporter.reportStopped") {
            val response = client.updateTimeline(
                ratingKey = item.ratingKey,
                state = "stopped",
                timeMs = positionMs,
                durationMs = item.durationMs ?: 0L,
            )
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("API Error: ${response.code()}")
            }
            apiCache.evict("${item.serverId}:/library/metadata/${item.ratingKey}")
        }
    }

    override suspend fun toggleWatchStatus(item: MediaItem, isWatched: Boolean): Result<Unit> {
        val client = serverClientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Server ${item.serverId} unavailable"))

        return safeApiCall("PlexPlaybackReporter.toggleWatchStatus") {
            val response = if (isWatched) client.scrobble(item.ratingKey) else client.unscrobble(item.ratingKey)
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("API Error: ${response.code()}")
            }
            apiCache.evict("${item.serverId}:/library/metadata/${item.ratingKey}")
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

        return safeApiCall("PlexPlaybackReporter.updateStreamSelection") {
            val url = "${client.baseUrl.trimEnd('/')}/library/parts/$partId"
            val response = api.putStreamSelection(
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
