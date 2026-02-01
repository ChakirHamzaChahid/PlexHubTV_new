package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * Cas d'utilisation pour construire la file de lecture (PlayQueue).
 *
 * Pour un épisode donné, génère une liste contenant cet épisode et tous les suivants
 * de la même saison, permettant le "Binge Watching" automatique.
 */
class GetPlayQueueUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(startEpisode: MediaItem): Result<List<MediaItem>> {
        if (startEpisode.type != MediaType.Episode) {
            return Result.success(listOf(startEpisode)) // Single item queue for Movies/Others
        }

        return try {
            val parentKey = startEpisode.parentRatingKey
            if (parentKey == null) return Result.success(listOf(startEpisode))

            // Get all episodes of the season
            val seasonEpisodes = mediaRepository.getSeasonEpisodes(parentKey, startEpisode.serverId).getOrNull()
                ?: return Result.success(listOf(startEpisode))

            // Find index of start episode
            val startIndex = seasonEpisodes.indexOfFirst { it.ratingKey == startEpisode.ratingKey }
            
            if (startIndex == -1) return Result.success(listOf(startEpisode))

            // Queue is startEpisode + everything after it
            // We include the start item at position 0 of queue? Yes, PlaybackManager expects full queue?
            // PlaybackManager logic: play(media, queue). Queue should usually contain everything.
            
            val queue = seasonEpisodes.drop(startIndex)
            Result.success(queue)
        } catch (e: Exception) {
            Result.success(listOf(startEpisode)) // Fallback to single item
        }
    }
}
