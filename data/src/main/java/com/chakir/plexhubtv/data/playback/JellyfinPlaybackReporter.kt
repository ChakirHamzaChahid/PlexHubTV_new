package com.chakir.plexhubtv.data.playback

import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinPlaybackProgressInfo
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinPlaybackStopInfo
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.data.repository.JellyfinClientResolver
import com.chakir.plexhubtv.domain.service.PlaybackReporter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin-specific playback reporter.
 *
 * Converts milliseconds to Jellyfin ticks (10,000 ticks/ms) and reports
 * progress/stop events via the Jellyfin Sessions API.
 */
@Singleton
class JellyfinPlaybackReporter @Inject constructor(
    private val clientResolver: JellyfinClientResolver,
) : PlaybackReporter {

    override fun matches(serverId: String): Boolean =
        serverId.startsWith(SourcePrefix.JELLYFIN)

    override suspend fun reportProgress(item: MediaItem, positionMs: Long): Result<Unit> {
        val client = clientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Jellyfin server ${item.serverId} unavailable"))

        return safeApiCall("JellyfinPlaybackReporter.reportProgress") {
            val response = client.reportPlaybackProgress(
                JellyfinPlaybackProgressInfo(
                    itemId = item.ratingKey,
                    mediaSourceId = item.ratingKey,
                    playSessionId = null,
                    positionTicks = msToTicks(positionMs),
                    isPaused = false,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                ),
            )
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Progress report failed: HTTP ${response.code()}")
            }
        }
    }

    override suspend fun reportStopped(item: MediaItem, positionMs: Long): Result<Unit> {
        val client = clientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Jellyfin server ${item.serverId} unavailable"))

        return safeApiCall("JellyfinPlaybackReporter.reportStopped") {
            val response = client.reportPlaybackStopped(
                JellyfinPlaybackStopInfo(
                    itemId = item.ratingKey,
                    mediaSourceId = item.ratingKey,
                    playSessionId = null,
                    positionTicks = msToTicks(positionMs),
                ),
            )
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Stop report failed: HTTP ${response.code()}")
            }
        }
    }

    override suspend fun toggleWatchStatus(item: MediaItem, isWatched: Boolean): Result<Unit> {
        val client = clientResolver.getClient(item.serverId)
            ?: return Result.failure(AppError.Network.ServerError("Jellyfin server ${item.serverId} unavailable"))

        return safeApiCall("JellyfinPlaybackReporter.toggleWatchStatus") {
            val response = if (isWatched) {
                client.markPlayed(item.ratingKey)
            } else {
                client.markUnplayed(item.ratingKey)
            }
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Watch status toggle failed: HTTP ${response.code()}")
            }
        }
    }

    override suspend fun updateStreamSelection(
        serverId: String,
        partId: String,
        audioStreamId: String?,
        subtitleStreamId: String?,
    ): Result<Unit> {
        // Jellyfin handles stream selection via playback start/progress — no separate API needed
        return Result.success(Unit)
    }

    /** Converts milliseconds to Jellyfin ticks (10,000 ticks per millisecond). */
    private fun msToTicks(ms: Long): Long = ms * 10_000L
}
