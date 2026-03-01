package com.chakir.plexhubtv.domain.source

import com.chakir.plexhubtv.core.model.MediaItem

/**
 * Strategy interface for media source operations.
 * Each source type (Plex, Xtream, Backend) implements this to encapsulate
 * source-specific logic: detail fetching, season/episode resolution, URL handling.
 *
 * Eliminates the `startsWith("xtream_")`/`startsWith("backend_")` branching
 * scattered across repositories and ViewModels.
 */
interface MediaSourceHandler {
    /** Returns true if this handler can process the given serverId. */
    fun matches(serverId: String): Boolean

    /** Fetch full detail for a media item. */
    suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem>

    /** Fetch seasons for a show (or children for a season). */
    suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>>

    /** Fetch episodes for a season. */
    suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>>

    /** Fetch similar/related media. Returns empty list if not supported. */
    suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>>

    /** Whether this source supports multi-server enrichment (source discovery). */
    fun needsEnrichment(): Boolean

    /** Whether URLs need server-specific resolution (baseUrl + token). */
    fun needsUrlResolution(): Boolean

    /** Bonus score for metadata quality ranking (Plex metadata is richer). */
    fun metadataScoreBonus(): Int
}
