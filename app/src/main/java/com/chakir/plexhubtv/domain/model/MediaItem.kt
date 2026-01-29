package com.chakir.plexhubtv.domain.model

data class MediaItem(
    val id: String, // serverId + ratingKey
    val ratingKey: String,
    val serverId: String,
    val unificationId: String? = null, // For deduplication
    val title: String,
    val type: MediaType,
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    val isWatched: Boolean = false,
    val isFavorite: Boolean = false,
    val genres: List<String> = emptyList(),
    
    // Layout / ID
    val guid: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val studio: String? = null,
    val contentRating: String? = null,
    val audienceRating: Double? = null,
    val rating: Double? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val lastViewedAt: Long? = null,

    // Hierarchy
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentRatingKey: String? = null,
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val parentIndex: Int? = null,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val childCount: Int? = null,
    
    // Playback
    val viewCount: Long = 0,
    val viewOffset: Long = 0,
    val viewedStatus: String? = null,
    val playbackPositionMs: Long? = null,
    val mediaParts: List<MediaPart> = emptyList(),

    // Extra
    val tagline: String? = null,
    val role: List<CastMember>? = null,
    val baseUrl: String? = null,
    val accessToken: String? = null,
    
    // Rich Metadata
    val chapters: List<Chapter> = emptyList(),
    val markers: List<Marker> = emptyList(),
    
    // Aggregation
    val remoteSources: List<MediaSource> = emptyList()
)

data class MediaSource(
    val serverId: String,
    val ratingKey: String,
    val serverName: String,
    val resolution: String? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val displayTitle: String? = null,
    val fileSize: Long? = null,
    val bitrate: Int? = null,
    val hasHDR: Boolean = false,
    val languages: List<String> = emptyList(),
    val thumbUrl: String? = null,
    val artUrl: String? = null
)

data class CastMember(
    val id: String?,
    val filter: String?,
    val role: String?,
    val tag: String?,
    val thumb: String?
)

enum class MediaType {
    Movie, Show, Season, Episode, Collection, Playlist, Artist, Album, Track, Clip, Photo, Unknown
}
