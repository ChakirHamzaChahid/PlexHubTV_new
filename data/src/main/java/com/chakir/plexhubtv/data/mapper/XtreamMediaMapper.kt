package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.common.StringNormalizer
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.XtreamCategory
import com.chakir.plexhubtv.core.network.xtream.XtreamCategoryDto
import com.chakir.plexhubtv.core.network.xtream.XtreamEpisodeDto
import com.chakir.plexhubtv.core.network.xtream.XtreamSeriesDto
import com.chakir.plexhubtv.core.network.xtream.XtreamVodStreamDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamMediaMapper @Inject constructor() {

    fun mapVodToEntity(dto: XtreamVodStreamDto, accountId: String): MediaEntity {
        val serverId = "xtream_$accountId"
        val ratingKey = "vod_${dto.streamId ?: 0}"
        val (title, year) = parseTitleAndYear(dto.name)
        val rating = parseRating(dto.rating)

        return MediaEntity(
            ratingKey = ratingKey,
            serverId = serverId,
            librarySectionId = "xtream_vod",
            title = title,
            titleSortable = StringNormalizer.normalizeForSorting(title),
            type = "movie",
            thumbUrl = dto.streamIcon,
            year = year,
            addedAt = dto.added?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
            filter = dto.categoryId ?: "all",
            sortOrder = "default",
            pageOffset = dto.num ?: 0,
            displayRating = rating,
            rating = rating.takeIf { it > 0.0 },
            unificationId = buildUnificationId(title, year),
            historyGroupKey = buildUnificationId(title, year).ifEmpty { "$ratingKey$serverId" },
        )
    }

    fun mapSeriesToEntity(dto: XtreamSeriesDto, accountId: String): MediaEntity {
        val serverId = "xtream_$accountId"
        val ratingKey = "series_${dto.seriesId ?: 0}"
        val (title, _) = parseTitleAndYear(dto.name)
        val year = parseYear(dto.releaseDate)
        val rating = parseRating(dto.rating)

        return MediaEntity(
            ratingKey = ratingKey,
            serverId = serverId,
            librarySectionId = "xtream_series",
            title = title,
            titleSortable = StringNormalizer.normalizeForSorting(title),
            type = "show",
            thumbUrl = dto.cover,
            artUrl = dto.backdropPath?.firstOrNull(),
            year = year,
            summary = dto.plot,
            duration = dto.episodeRunTime?.toLongOrNull()?.times(60_000),
            genres = dto.genre,
            displayRating = rating,
            rating = rating.takeIf { it > 0.0 },
            filter = dto.categoryId ?: "all",
            sortOrder = "default",
            unificationId = buildUnificationId(title, year),
            historyGroupKey = buildUnificationId(title, year).ifEmpty { "$ratingKey$serverId" },
        )
    }

    fun mapEpisodeToEntity(
        episode: XtreamEpisodeDto,
        seriesDto: XtreamSeriesDto,
        seasonNumber: Int,
        accountId: String,
    ): MediaEntity {
        val serverId = "xtream_$accountId"
        return MediaEntity(
            ratingKey = "ep_${episode.id ?: "0"}",
            serverId = serverId,
            librarySectionId = "xtream_series",
            title = episode.title ?: "Episode ${episode.episodeNum}",
            titleSortable = StringNormalizer.normalizeForSorting(
                episode.title ?: "Episode ${episode.episodeNum}"
            ),
            type = "episode",
            thumbUrl = episode.info?.movieImage,
            summary = episode.info?.plot,
            duration = episode.info?.durationSecs?.toLong()?.times(1000),
            parentRatingKey = "season_${seriesDto.seriesId}_$seasonNumber",
            parentTitle = "Season $seasonNumber",
            parentIndex = seasonNumber,
            grandparentRatingKey = "series_${seriesDto.seriesId}",
            grandparentTitle = seriesDto.name,
            index = episode.episodeNum,
            filter = "all",
            sortOrder = "default",
            displayRating = parseRating(episode.info?.rating),
        )
    }

    fun mapCategoryDto(dto: XtreamCategoryDto): XtreamCategory? {
        val id = dto.categoryId?.toIntOrNull() ?: return null
        return XtreamCategory(
            categoryId = id,
            categoryName = dto.categoryName ?: "Unknown",
            parentId = dto.parentId ?: 0,
        )
    }

    /**
     * Parse IPTV title patterns, stripping common prefixes and extracting year.
     * Examples:
     * - "|VM| Le Monde après nous (2023)" → ("Le Monde après nous", 2023)
     * - "[FR] Breaking Bad" → ("Breaking Bad", null)
     * - "The Matrix (1999)" → ("The Matrix", 1999)
     */
    internal fun parseTitleAndYear(raw: String?): Pair<String, Int?> {
        if (raw.isNullOrBlank()) return Pair("Unknown", null)

        var cleaned = raw.trim()
        // Strip common IPTV prefixes: |XX|, [XX], |XX XX|
        cleaned = cleaned.replace(Regex("""^\|[^|]*\|\s*"""), "")
        cleaned = cleaned.replace(Regex("""^\[[^\]]*\]\s*"""), "")

        // Extract year from parentheses at end: "Title (2023)"
        val yearMatch = Regex("""\s*\((\d{4})\)\s*$""").find(cleaned)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        if (yearMatch != null) {
            cleaned = cleaned.removeRange(yearMatch.range).trim()
        }

        return Pair(cleaned.ifBlank { "Unknown" }, year)
    }

    private fun parseRating(ratingValue: String?): Double {
        if (ratingValue.isNullOrBlank()) return 0.0
        return ratingValue.toDoubleOrNull() ?: 0.0
    }

    private fun parseYear(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        // Try "2001-11-05" format
        return Regex("""(\d{4})""").find(dateStr)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun buildUnificationId(title: String, year: Int?): String {
        if (title == "Unknown") return ""
        val normalized = StringNormalizer.normalizeForSorting(title)
            .lowercase()
            .replace(Regex("\\s+"), "_")
        return if (year != null) "title_${normalized}_$year" else "title_$normalized"
    }
}
