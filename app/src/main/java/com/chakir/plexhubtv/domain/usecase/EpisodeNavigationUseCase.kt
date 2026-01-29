package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.playback.PlaybackManager
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

data class AdjacentEpisodes(
    val previous: MediaItem? = null,
    val next: MediaItem? = null
)

/**
 * Use case for loading adjacent episodes (next/previous) for TV show navigation.
 */
class EpisodeNavigationUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackManager: PlaybackManager
) {

    /**
     * Load the next and previous episodes for a given episode
     */
    suspend fun loadAdjacentEpisodes(
        currentEpisode: MediaItem
    ): Result<AdjacentEpisodes> {
        return try {
            if (currentEpisode.type != MediaType.Episode) {
                return Result.success(AdjacentEpisodes())
            }

            // First check if we're in a playback queue
            val queueNext = playbackManager.getNextMedia()
            val queuePrevious = playbackManager.getPreviousMedia()

            if (queueNext != null || queuePrevious != null) {
                // Use queue-based navigation
                return Result.success(AdjacentEpisodes(
                    next = queueNext,
                    previous = queuePrevious
                ))
            }

            // Fall back to sequential season navigation
            val seasonKey = currentEpisode.parentRatingKey
                ?: return Result.success(AdjacentEpisodes())

val seasonEpisodes = mediaRepository.getSeasonEpisodes(
                seasonKey,
                currentEpisode.serverId
            ).getOrNull() ?: return Result.success(AdjacentEpisodes())

            // Find current episode index
            val currentIndex = seasonEpisodes.indexOfFirst {
                it.ratingKey == currentEpisode.ratingKey
            }

            if (currentIndex == -1) {
                return Result.success(AdjacentEpisodes())
            }

            val previous = if (currentIndex > 0) {
                seasonEpisodes[currentIndex - 1]
            } else null

            val next = if (currentIndex < seasonEpisodes.size - 1) {
                seasonEpisodes[currentIndex + 1]
            } else null

            Result.success(AdjacentEpisodes(
                previous = previous,
                next = next
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
