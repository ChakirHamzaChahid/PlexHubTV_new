package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend (PlexHub Backend) source handler.
 * Pre-enriched content synced via library sync. Room-first with backend API fallback.
 */
@Singleton
class BackendSourceHandler @Inject constructor(
    private val mediaDao: MediaDao,
    private val mapper: MediaMapper,
    private val backendRepository: BackendRepository,
) : MediaSourceHandler {

    override fun matches(serverId: String): Boolean = serverId.startsWith("backend_")

    override suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        // Room first
        val localEntity = mediaDao.getMedia(ratingKey, serverId)
        if (localEntity != null) {
            return Result.success(mapper.mapEntityToDomain(localEntity))
        }

        // Season: parse seriesId, fetch episodes, find matching season
        if (ratingKey.startsWith("season_")) {
            val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
            if (parts.size == 2) {
                val seriesRatingKey = "series_${parts[0]}"
                val seasonNumber = parts[1].toIntOrNull()
                val seasons = getSeasons(seriesRatingKey, serverId).getOrNull()
                if (seasons != null && seasonNumber != null) {
                    val season = seasons.find { it.seasonIndex == seasonNumber }
                    if (season != null) return Result.success(season)
                }
            }
        }

        // Fallback: fetch from backend API
        return backendRepository.getMediaDetail(ratingKey, serverId)
    }

    override suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        if (ratingKey.startsWith("series_")) {
            return buildVirtualSeasons(ratingKey, serverId)
        }
        return getEpisodes(ratingKey, serverId)
    }

    override suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>> {
        // Room first
        val localEntities = mediaDao.getChildren(seasonRatingKey, serverId)
        if (localEntities.isNotEmpty()) {
            return Result.success(localEntities.map { mapper.mapEntityToDomain(it) })
        }

        // API fallback: extract parent show key and optional season filter
        val parentRatingKey = if (seasonRatingKey.startsWith("season_")) {
            val parts = seasonRatingKey.removePrefix("season_").split("_", limit = 2)
            if (parts.size == 2) "series_${parts[0]}" else seasonRatingKey
        } else {
            seasonRatingKey
        }

        val seasonNumber = if (seasonRatingKey.startsWith("season_")) {
            seasonRatingKey.removePrefix("season_").split("_", limit = 2).getOrNull(1)?.toIntOrNull()
        } else null

        return backendRepository.getEpisodes(parentRatingKey, serverId).map { episodes ->
            if (seasonNumber != null) {
                episodes.filter { it.parentIndex == seasonNumber }
            } else {
                episodes
            }
        }
    }

    override suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>> =
        Result.success(emptyList())

    override fun needsEnrichment(): Boolean = true
    override fun needsUrlResolution(): Boolean = false
    override fun metadataScoreBonus(): Int = 0

    private suspend fun buildVirtualSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        val result = backendRepository.getEpisodes(ratingKey, serverId)
        return result.map { episodes ->
            episodes.groupBy { it.parentIndex ?: 0 }
                .toSortedMap()
                .map { (seasonNum, seasonEpisodes) ->
                    val firstEp = seasonEpisodes.first()
                    val seriesId = ratingKey.removePrefix("series_")
                    val seasonRatingKey = "season_${seriesId}_$seasonNum"
                    MediaItem(
                        id = "$serverId:$seasonRatingKey",
                        ratingKey = seasonRatingKey,
                        serverId = serverId,
                        title = firstEp.parentTitle ?: "Season $seasonNum",
                        type = MediaType.Season,
                        parentRatingKey = ratingKey,
                        seasonIndex = seasonNum,
                        thumbUrl = firstEp.thumbUrl,
                        parentTitle = firstEp.grandparentTitle,
                    )
                }
        }
    }
}
