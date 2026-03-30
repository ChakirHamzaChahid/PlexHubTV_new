package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.XtreamSeriesRepository
import com.chakir.plexhubtv.domain.repository.XtreamVodRepository
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import com.chakir.plexhubtv.domain.usecase.MediaDetail
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream source handler.
 * Direct-play content — no URL resolution or multi-server enrichment needed.
 * Room-first with Xtream API fallback for series detail.
 */
@Singleton
class XtreamSourceHandler @Inject constructor(
    private val mediaDao: MediaDao,
    private val mapper: MediaMapper,
    private val xtreamSeriesRepository: XtreamSeriesRepository,
    private val xtreamVodRepository: XtreamVodRepository,
) : MediaSourceHandler {

    private val seriesDetailCache = ConcurrentHashMap<String, Pair<Long, MediaDetail>>()
    private val seriesDetailCacheTtlMs = 60 * 1000L

    override fun matches(serverId: String): Boolean = serverId.startsWith(SourcePrefix.XTREAM)

    override suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        // Room first
        val localEntity = mediaDao.getMedia(ratingKey, serverId)
        if (localEntity != null) {
            // VOD movies: enrich on first access (fetch plot, cast, genre, tmdb_id)
            if (ratingKey.startsWith("vod_") && localEntity.summary == null) {
                val vodId = ratingKey.removePrefix("vod_").substringBefore(".").toIntOrNull()
                if (vodId != null) {
                    val accountId = serverId.removePrefix("xtream_")
                    xtreamVodRepository.enrichMovieDetail(accountId, vodId, ratingKey)
                    val enriched = mediaDao.getMedia(ratingKey, serverId)
                    if (enriched != null) {
                        return Result.success(mapper.mapEntityToDomain(enriched))
                    }
                }
            }
            return Result.success(mapper.mapEntityToDomain(localEntity))
        }

        // Series not in Room: fetch full detail from Xtream API
        if (ratingKey.startsWith("series_")) {
            val detail = getCachedSeriesDetail(ratingKey, serverId)
            if (detail != null) return Result.success(detail.item)
        }

        // Season: parse seriesId, fetch series detail, find matching season
        if (ratingKey.startsWith("season_")) {
            val parts = ratingKey.removePrefix("season_").split("_", limit = 2)
            if (parts.size == 2) {
                val seriesRatingKey = "series_${parts[0]}"
                val seasonNumber = parts[1].toIntOrNull()
                val detail = getCachedSeriesDetail(seriesRatingKey, serverId)
                if (detail != null && seasonNumber != null) {
                    val season = detail.children.find { it.seasonIndex == seasonNumber }
                    if (season != null) return Result.success(season)
                }
            }
        }

        return Result.failure(AppError.Media.NotFound("Xtream media $ratingKey not found"))
    }

    override suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        if (ratingKey.startsWith("series_")) {
            val detail = getCachedSeriesDetail(ratingKey, serverId)
            return detail?.let { Result.success(it.children) }
                ?: Result.success(emptyList())
        }
        return getEpisodes(ratingKey, serverId)
    }

    override suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>> {
        // Room first
        val localEntities = mediaDao.getChildren(seasonRatingKey, serverId)
        if (localEntities.isNotEmpty()) {
            return Result.success(localEntities.map { mapper.mapEntityToDomain(it) })
        }

        // Fetch series detail (persists episodes to Room), then re-query
        if (seasonRatingKey.startsWith("season_")) {
            val parts = seasonRatingKey.removePrefix("season_").split("_", limit = 2)
            if (parts.size == 2) {
                val seriesRatingKey = "series_${parts[0]}"
                getCachedSeriesDetail(seriesRatingKey, serverId)
                val refreshed = mediaDao.getChildren(seasonRatingKey, serverId)
                if (refreshed.isNotEmpty()) {
                    return Result.success(refreshed.map { mapper.mapEntityToDomain(it) })
                }
            }
        }

        return Result.success(emptyList())
    }

    override suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>> =
        Result.success(emptyList())

    override fun needsEnrichment(): Boolean = true
    override fun needsUrlResolution(): Boolean = false
    override fun metadataScoreBonus(): Int = 0

    private suspend fun getCachedSeriesDetail(ratingKey: String, serverId: String): MediaDetail? {
        val cacheKey = "$ratingKey:$serverId"
        val cached = seriesDetailCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < seriesDetailCacheTtlMs) {
            return cached.second
        }
        val accountId = serverId.removePrefix("xtream_")
        val seriesId = ratingKey.removePrefix("series_").toIntOrNull() ?: return null
        return xtreamSeriesRepository.getSeriesDetail(accountId, seriesId)
            .onSuccess { detail ->
                seriesDetailCache[cacheKey] = System.currentTimeMillis() to detail
            }
            .getOrNull()
    }
}
