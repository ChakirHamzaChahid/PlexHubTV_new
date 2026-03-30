package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.common.StringNormalizer
import timber.log.Timber
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.database.computeMetadataScore
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.CastMember
import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaPart
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.SubtitleStream
import com.chakir.plexhubtv.core.model.UnificationId
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.core.model.UnknownStream
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinItem
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinMediaSource
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinMediaStream
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinSearchHint
import com.chakir.plexhubtv.core.util.ContentRatingHelper
import java.time.Instant
import javax.inject.Inject

/**
 * Mapper for Jellyfin DTO → Entity (persistence) and DTO → Domain (live display).
 *
 * Key differences vs Plex mapper:
 * - Image URLs stored as RELATIVE paths (no token) — resolved at display time by JellyfinSourceHandler.
 * - Ticks → ms conversion: Jellyfin uses 10,000 ticks per millisecond.
 * - ProviderIds map gives IMDb/TMDb directly (no GUID regex parsing needed).
 * - Hierarchy: SeriesName/SeasonName instead of grandparentTitle/parentTitle.
 */
class JellyfinMapper @Inject constructor() {

    // ========================================
    // DTO → Entity (sync / persistence)
    // ========================================

    /**
     * Maps a Jellyfin API item to a Room entity for local persistence.
     *
     * Image URLs are stored as RELATIVE paths (no baseUrl, no token).
     * This prevents token expiration from invalidating cached URLs.
     */
    fun mapDtoToEntity(
        item: JellyfinItem,
        serverId: String, // Already prefixed: "jellyfin_{id}"
        librarySectionId: String,
        isOwned: Boolean = false,
    ): MediaEntity {
        val type = mapTypeToPlexString(item.type)
        // Normalize empty strings to null — Jellyfin may return {"Imdb": ""} for items
        // without an IMDB ID. Empty strings break COALESCE in updateGroupKeys SQL.
        val imdbId = item.providerIds?.get("Imdb")?.ifEmpty { null }
        val tmdbId = item.providerIds?.get("Tmdb")?.ifEmpty { null }
        val title = item.name ?: "Unknown"
        val year = item.productionYear
        val unificationId = UnificationId.calculate(imdbId, tmdbId, title, year)

        // Relative image paths (no baseUrl, no token)
        val thumbUrl = buildPrimaryImagePath(item)
        val artUrl = buildBackdropImagePath(item)

        val durationMs = ticksToMs(item.runTimeTicks)
        val communityRating = item.communityRating?.toDouble()
        val contentRating = ContentRatingHelper.normalize(item.officialRating)
        val genres = item.genres?.joinToString(",")

        // Pre-compute groupKey: same COALESCE logic as updateGroupKeys SQL.
        // Safety net — ensures items are never excluded by WHERE groupKey != ''.
        val groupKey = imdbId
            ?: tmdbId?.let { "tmdb_$it" }
            ?: (item.id + serverId)

        return MediaEntity(
            ratingKey = item.id,
            serverId = serverId,
            librarySectionId = librarySectionId,
            title = title,
            titleSortable = StringNormalizer.normalizeForSorting(title),
            unificationId = unificationId,
            groupKey = groupKey,
            historyGroupKey = unificationId.ifEmpty { item.id + serverId },
            guid = null, // Jellyfin doesn't use Plex GUIDs
            imdbId = imdbId,
            tmdbId = tmdbId,
            type = type,
            thumbUrl = thumbUrl,
            artUrl = artUrl,
            summary = item.overview,
            year = year,
            duration = durationMs,
            viewOffset = ticksToMs(item.userData?.playbackPositionTicks),
            viewCount = item.userData?.playCount?.toLong() ?: 0L,
            lastViewedAt = parseIsoDateSeconds(item.userData?.lastPlayedDate),
            // Hierarchy
            parentTitle = mapParentTitle(item),
            parentRatingKey = mapParentRatingKey(item),
            parentIndex = item.parentIndexNumber,
            grandparentTitle = mapGrandparentTitle(item),
            grandparentRatingKey = mapGrandparentRatingKey(item),
            index = item.indexNumber,
            // Media parts (from mediaSources when available)
            mediaParts = item.mediaSources?.map { mapMediaSource(it, item.id) } ?: emptyList(),
            // Ratings
            rating = item.criticRating?.toDouble(),
            audienceRating = communityRating,
            displayRating = communityRating ?: 0.0,
            contentRating = contentRating,
            genres = genres,
            addedAt = parseIsoDateSeconds(item.dateCreated),
            updatedAt = System.currentTimeMillis(),
            parentThumb = buildParentThumbPath(item),
            grandparentThumb = buildGrandparentThumbPath(item),
            metadataScore = computeMetadataScore(
                summary = item.overview,
                thumbUrl = thumbUrl,
                imdbId = imdbId,
                tmdbId = tmdbId,
                year = year,
                genres = genres,
                serverId = serverId,
                rating = item.criticRating?.toDouble(),
                audienceRating = communityRating,
                contentRating = contentRating,
                isOwned = isOwned,
            ),
            isOwned = isOwned,
        )
    }

    // ========================================
    // DTO → Domain (live display)
    // ========================================

    /**
     * Maps a Jellyfin API item to a domain model for immediate display.
     *
     * Image URLs include baseUrl but NO token — authentication is handled
     * by [JellyfinImageInterceptor] adding `Authorization: MediaBrowser Token="..."`
     * at request time. This prevents token expiration from invalidating cached URLs.
     */
    fun mapDtoToDomain(
        item: JellyfinItem,
        serverId: String,
        baseUrl: String,
        accessToken: String,
    ): MediaItem {
        val imdbId = item.providerIds?.get("Imdb")?.ifEmpty { null }
        val tmdbId = item.providerIds?.get("Tmdb")?.ifEmpty { null }
        val title = item.name ?: "Unknown"
        val year = item.productionYear
        val communityRating = item.communityRating?.toDouble()

        // Full URLs with baseUrl (no token — JellyfinImageInterceptor adds auth header)
        val thumbUrl = buildPrimaryImagePath(item)?.let { resolveUrl(baseUrl, it) }
        val artUrl = buildBackdropImagePath(item)?.let { resolveUrl(baseUrl, it) }

        val viewCount = item.userData?.playCount?.toLong() ?: 0L
        val viewOffset = ticksToMs(item.userData?.playbackPositionTicks)
        val durationMs = ticksToMs(item.runTimeTicks)

        return MediaItem(
            id = "${serverId}_${item.id}",
            ratingKey = item.id,
            serverId = serverId,
            unificationId = UnificationId.calculate(imdbId, tmdbId, title, year),
            title = title,
            type = mapType(item.type),
            thumbUrl = thumbUrl,
            artUrl = artUrl,
            summary = item.overview,
            year = year,
            durationMs = durationMs,
            viewOffset = viewOffset,
            viewCount = viewCount,
            isWatched = item.userData?.played == true || run {
                val dur = durationMs ?: 0L
                viewOffset > 0 && dur > 0 && viewOffset.toFloat() / dur.toFloat() >= 0.9f
            },
            // Rich metadata
            guid = null,
            imdbId = imdbId,
            tmdbId = tmdbId,
            studio = item.studios?.firstOrNull()?.name,
            contentRating = ContentRatingHelper.normalize(item.officialRating),
            audienceRating = communityRating,
            rating = item.criticRating?.toDouble(),
            addedAt = parseIsoDateSeconds(item.dateCreated),
            updatedAt = parseIsoDateSeconds(item.dateCreated),
            tagline = item.taglines?.firstOrNull(),
            genres = item.genres ?: emptyList(),
            // Hierarchy
            parentTitle = mapParentTitle(item),
            grandparentTitle = mapGrandparentTitle(item),
            grandparentThumb = buildGrandparentThumbPath(item)?.let { resolveUrl(baseUrl, it) },
            parentThumb = buildParentThumbPath(item)?.let { resolveUrl(baseUrl, it) },
            parentIndex = item.parentIndexNumber,
            parentRatingKey = mapParentRatingKey(item),
            episodeIndex = if (item.type == "Episode") item.indexNumber else null,
            seasonIndex = if (item.type == "Episode" || item.type == "Season") item.parentIndexNumber ?: item.indexNumber else null,
            childCount = item.childCount,
            grandparentRatingKey = mapGrandparentRatingKey(item),
            // Parts & Streams
            mediaParts = item.mediaSources?.map { mapMediaSource(it, item.id) } ?: emptyList(),
            // Directors
            directors = item.people
                ?.filter { it.type == "Director" }
                ?.mapNotNull { it.name }
                ?: emptyList(),
            // Cast
            role = item.people
                ?.filter { it.type == "Actor" }
                ?.map { person ->
                    CastMember(
                        id = person.id,
                        filter = null,
                        role = person.role,
                        tag = person.name,
                        thumb = person.primaryImageTag?.let { tag ->
                            person.id?.let { pid ->
                                resolveUrl(baseUrl, "/Items/$pid/Images/Primary?maxWidth=200&tag=$tag")
                            }
                        },
                    )
                },
            baseUrl = baseUrl,
            accessToken = accessToken,
            // Chapters
            chapters = item.chapters?.mapIndexed { i, ch ->
                Chapter(
                    title = ch.name ?: "Chapter ${i + 1}",
                    startTime = ticksToMs(ch.startPositionTicks),
                    endTime = 0L, // Jellyfin doesn't provide end time; UI calculates from next chapter
                    thumbUrl = ch.imageTag?.let {
                        resolveUrl(baseUrl, "/Items/${item.id}/Images/Chapter/$i?tag=$it")
                    },
                )
            } ?: emptyList(),
        )
    }

    // ========================================
    // Search Hint → Domain
    // ========================================

    fun mapSearchHintToDomain(
        hint: JellyfinSearchHint,
        serverId: String,
        baseUrl: String,
        accessToken: String,
    ): MediaItem {
        val itemId = hint.itemId ?: hint.id ?: ""
        val thumbUrl = hint.primaryImageTag?.let {
            resolveUrl(baseUrl, "/Items/$itemId/Images/Primary?maxWidth=300&tag=$it")
        }
        return MediaItem(
            id = "${serverId}_$itemId",
            ratingKey = itemId,
            serverId = serverId,
            unificationId = null,
            title = hint.name ?: "Unknown",
            type = mapType(hint.type),
            thumbUrl = thumbUrl,
            year = hint.productionYear,
            durationMs = ticksToMs(hint.runTimeTicks),
            grandparentTitle = hint.seriesName,
            episodeIndex = hint.indexNumber,
            parentIndex = hint.parentIndexNumber,
        )
    }

    // ========================================
    // Type Mapping
    // ========================================

    /** Jellyfin PascalCase type → Plex lowercase string (for MediaEntity.type). */
    private fun mapTypeToPlexString(type: String?): String = when (type) {
        "Movie" -> "movie"
        "Series" -> "show"
        "Episode" -> "episode"
        "Season" -> "season"
        "BoxSet" -> "collection"
        else -> "movie" // Default to movie for unknown types
    }

    /** Jellyfin PascalCase type → MediaType enum. */
    private fun mapType(type: String?): MediaType = when (type) {
        "Movie" -> MediaType.Movie
        "Series" -> MediaType.Show
        "Episode" -> MediaType.Episode
        "Season" -> MediaType.Season
        "BoxSet" -> MediaType.Collection
        else -> MediaType.Unknown
    }

    // ========================================
    // Hierarchy Helpers
    // ========================================

    private fun mapParentTitle(item: JellyfinItem): String? = when (item.type) {
        "Episode" -> item.seasonName
        "Season" -> item.seriesName
        else -> null
    }

    private fun mapParentRatingKey(item: JellyfinItem): String? = when (item.type) {
        "Episode" -> item.seasonId
        "Season" -> item.seriesId
        else -> null
    }

    private fun mapGrandparentTitle(item: JellyfinItem): String? = when (item.type) {
        "Episode" -> item.seriesName
        else -> null
    }

    private fun mapGrandparentRatingKey(item: JellyfinItem): String? = when (item.type) {
        "Episode" -> item.seriesId
        else -> null
    }

    // ========================================
    // Image URL Builders (relative paths)
    // ========================================

    /** Primary poster image — relative path without token. */
    private fun buildPrimaryImagePath(item: JellyfinItem): String? {
        val tag = item.imageTags?.get("Primary") ?: return null
        return "/Items/${item.id}/Images/Primary?maxWidth=300&tag=$tag"
    }

    /** Backdrop/fanart image — relative path without token. Falls back to parent backdrop. */
    private fun buildBackdropImagePath(item: JellyfinItem): String? {
        // Own backdrop
        val ownTag = item.backdropImageTags?.firstOrNull()
        if (ownTag != null) {
            return "/Items/${item.id}/Images/Backdrop/0?maxWidth=1920&tag=$ownTag"
        }
        // Parent backdrop (episodes use series backdrop)
        val parentTag = item.parentBackdropImageTags?.firstOrNull()
        if (parentTag != null) {
            val parentId = item.parentBackdropItemId ?: item.seriesId ?: return null
            return "/Items/$parentId/Images/Backdrop/0?maxWidth=1920&tag=$parentTag"
        }
        return null
    }

    /** Season poster for episodes (parentThumb equivalent). */
    private fun buildParentThumbPath(item: JellyfinItem): String? {
        if (item.type != "Episode") return null
        val seasonId = item.seasonId ?: return null
        // No tag available for season — Jellyfin will serve current image
        return "/Items/$seasonId/Images/Primary?maxWidth=300"
    }

    /** Series poster for episodes (grandparentThumb equivalent). */
    private fun buildGrandparentThumbPath(item: JellyfinItem): String? {
        if (item.type != "Episode") return null
        val seriesId = item.seriesId ?: return null
        val tag = item.seriesPrimaryImageTag
        val tagParam = if (tag != null) "&tag=$tag" else ""
        return "/Items/$seriesId/Images/Primary?maxWidth=300$tagParam"
    }

    // ========================================
    // Media Source / Stream Mapping
    // ========================================

    private fun mapMediaSource(source: JellyfinMediaSource, itemId: String): MediaPart {
        val container = source.container ?: "mkv"
        return MediaPart(
            id = source.id ?: itemId,
            // Relative playback path — JellyfinUrlBuilder will resolve to full URL
            key = "/Videos/$itemId/stream.$container?static=true",
            duration = ticksToMs(source.runTimeTicks),
            file = source.path,
            size = source.size,
            container = container,
            streams = source.mediaStreams?.mapNotNull { mapMediaStream(it) } ?: emptyList(),
        )
    }

    private fun mapMediaStream(stream: JellyfinMediaStream): com.chakir.plexhubtv.core.model.MediaStream? {
        val id = stream.index?.toString() ?: "0"
        val index = stream.index
        val language = stream.language
        val title = stream.title
        val displayTitle = stream.displayTitle
        val codec = stream.codec
        val selected = stream.isDefault ?: false

        return when (stream.type) {
            "Video" -> {
                val isHdr = stream.videoRange in listOf("HDR", "HDR10", "HDR10+", "HLG", "DV", "Dolby Vision")
                VideoStream(
                    id = id,
                    index = index,
                    language = language,
                    languageCode = language,
                    title = title,
                    displayTitle = displayTitle,
                    codec = codec,
                    selected = selected,
                    width = stream.width,
                    height = stream.height,
                    bitrate = stream.bitRate,
                    hasHDR = isHdr,
                    scanType = null, // Jellyfin doesn't expose scan type
                )
            }
            "Audio" -> AudioStream(
                id = id,
                index = index,
                language = language,
                languageCode = language,
                title = title,
                displayTitle = displayTitle,
                codec = codec,
                selected = selected,
                channels = stream.channels,
            )
            "Subtitle" -> SubtitleStream(
                id = id,
                index = index,
                language = language,
                languageCode = language,
                title = title,
                displayTitle = displayTitle,
                codec = codec,
                selected = selected,
                forced = stream.isForced ?: false,
                key = stream.path, // External subtitle file path
            )
            else -> UnknownStream(
                id = id,
                index = index,
                language = language,
                languageCode = language,
                title = title,
                displayTitle = displayTitle,
                codec = codec,
                selected = selected,
            )
        }
    }

    // ========================================
    // Utility
    // ========================================

    /** Converts Jellyfin ticks (10,000 ticks/ms) to milliseconds. Null-safe with Long literals. */
    private fun ticksToMs(ticks: Long?): Long = (ticks ?: 0L) / 10_000L

    /** Parses ISO 8601 date string to Unix epoch seconds. Returns 0 on failure. */
    private fun parseIsoDateSeconds(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            Instant.parse(dateStr).epochSecond
        } catch (e: Exception) {
            Timber.d(e, "JellyfinMapper: Failed to parse ISO date '$dateStr'")
            0
        }
    }

    /** Prepends baseUrl to a relative Jellyfin path. Auth handled by JellyfinImageInterceptor. */
    private fun resolveUrl(baseUrl: String, relativePath: String): String =
        "$baseUrl$relativePath"
}
