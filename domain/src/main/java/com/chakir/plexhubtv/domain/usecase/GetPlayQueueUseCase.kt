package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Cas d'utilisation pour construire la file de lecture (PlayQueue).
 *
 * Pour un épisode donné, génère une liste contenant cet épisode et tous les suivants
 * de la même saison, permettant le "Binge Watching" automatique.
 */
class GetPlayQueueUseCase
    @Inject
    constructor(
        private val mediaDetailRepository: MediaDetailRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(startEpisode: MediaItem): Result<List<MediaItem>> =
            withContext(ioDispatcher) {
                if (startEpisode.type != MediaType.Episode) {
                    Timber.d("PlayQueue: Non-episode type ${startEpisode.type}, single-item queue")
                    return@withContext Result.success(listOf(startEpisode))
                }

                try {
                    val parentKey = startEpisode.parentRatingKey
                    if (parentKey == null) {
                        Timber.w("PlayQueue: parentRatingKey is NULL for episode '${startEpisode.title}' (rk=${startEpisode.ratingKey}, sid=${startEpisode.serverId}) → single-item queue, Next will not work!")
                        return@withContext Result.success(listOf(startEpisode))
                    }

                    // Get all episodes of the season
                    val seasonEpisodes =
                        mediaDetailRepository.getSeasonEpisodes(parentKey, startEpisode.serverId).getOrNull()
                    if (seasonEpisodes == null) {
                        Timber.w("PlayQueue: getSeasonEpisodes($parentKey, ${startEpisode.serverId}) returned null → single-item queue")
                        return@withContext Result.success(listOf(startEpisode))
                    }

                    // Find index of start episode
                    val startIndex = seasonEpisodes.indexOfFirst { it.ratingKey == startEpisode.ratingKey }

                    if (startIndex == -1) {
                        Timber.w("PlayQueue: Episode rk=${startEpisode.ratingKey} not found in ${seasonEpisodes.size} season episodes (parentKey=$parentKey) → single-item queue")
                        return@withContext Result.success(listOf(startEpisode))
                    }

                    val queue = seasonEpisodes.drop(startIndex)
                    Timber.d("PlayQueue: Built queue of ${queue.size} items (from index $startIndex of ${seasonEpisodes.size} episodes) for '${startEpisode.title}'")
                    Result.success(queue)
                } catch (e: Exception) {
                    Timber.w(e, "PlayQueue: failed to build queue for ${startEpisode.ratingKey}")
                    Result.success(listOf(startEpisode))
                }
            }
    }
