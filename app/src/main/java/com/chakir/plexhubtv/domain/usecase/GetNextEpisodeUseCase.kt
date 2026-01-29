package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

class GetNextEpisodeUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(media: MediaItem): Result<MediaItem> {
        return try {
            when (media.type) {
                MediaType.Episode -> {
                    if (isFinished(media)) {
                        resolveNextEpisode(media)
                    } else {
                        Result.success(media)
                    }
                }
                
                MediaType.Season -> {
                    val episodes = mediaRepository.getSeasonEpisodes(media.ratingKey, media.serverId).getOrNull() 
                        ?: return Result.failure(Exception("Failed to fetch episodes"))
                    
                    val firstUnwatched = episodes.find { !isFinished(it) }
                        ?: episodes.firstOrNull()
                    
                    if (firstUnwatched != null) Result.success(firstUnwatched)
                    else Result.failure(Exception("No episodes found in season"))
                }
                
                MediaType.Show -> {
                    val seasons = mediaRepository.getShowSeasons(media.ratingKey, media.serverId).getOrNull()
                        ?: return Result.failure(Exception("Failed to fetch seasons"))
                        
                    for (season in seasons) {
                        val episodes = mediaRepository.getSeasonEpisodes(season.ratingKey, media.serverId).getOrNull() ?: continue
                        val unwatched = episodes.find { !isFinished(it) }
                        if (unwatched != null) return Result.success(unwatched)
                    }
                    
                    val firstSeason = seasons.firstOrNull() ?: return Result.failure(Exception("No seasons found"))
                    val firstEp = mediaRepository.getSeasonEpisodes(firstSeason.ratingKey, media.serverId).getOrNull()?.firstOrNull()
                    
                    if (firstEp != null) Result.success(firstEp)
                    else Result.failure(Exception("No episodes found"))
                }
                
                else -> Result.failure(Exception("Unsupported media type for smart play: ${media.type}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isFinished(item: MediaItem): Boolean {
        if (item.isWatched) return true
        val duration = item.durationMs ?: return false
        if (duration <= 0) return false
        val progress = item.viewOffset.toFloat() / duration.toFloat()
        return progress >= 0.9f
    }

    private suspend fun resolveNextEpisode(current: MediaItem): Result<MediaItem> {
        val parentKey = current.parentRatingKey ?: return Result.success(current)
        val episodes = mediaRepository.getSeasonEpisodes(parentKey, current.serverId).getOrNull() ?: return Result.success(current)
        
        val currentIndex = episodes.indexOfFirst { it.ratingKey == current.ratingKey }
        if (currentIndex != -1 && currentIndex < episodes.size - 1) {
            return Result.success(episodes[currentIndex + 1])
        }
        
        // If it's the last episode of the season, we could try resolving next season...
        // But for simplicity and to avoid recursive complexity, let's return the current one or a failure if we want.
        // Returning current one or the next if found is safer.
        return Result.success(current)
    }
}
