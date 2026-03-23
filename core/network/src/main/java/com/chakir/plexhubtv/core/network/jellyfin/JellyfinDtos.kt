package com.chakir.plexhubtv.core.network.jellyfin

import com.google.gson.annotations.SerializedName

// ============================================================
// Jellyfin API DTOs — modeled after Jellyfin REST API v10.10+
// ============================================================

// --- Authentication ---

data class JellyfinAuthRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Pw") val password: String,
)

data class JellyfinAuthResponse(
    @SerializedName("User") val user: JellyfinUserDto?,
    @SerializedName("AccessToken") val accessToken: String?,
    @SerializedName("ServerId") val serverId: String?,
)

data class JellyfinUserDto(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String?,
    @SerializedName("ServerId") val serverId: String?,
    @SerializedName("HasPassword") val hasPassword: Boolean?,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String?,
)

// --- Public Server Info (unauthenticated) ---

data class JellyfinPublicInfo(
    @SerializedName("ServerName") val serverName: String?,
    @SerializedName("Version") val version: String?,
    @SerializedName("Id") val id: String?,
    @SerializedName("LocalAddress") val localAddress: String?,
    @SerializedName("OperatingSystem") val operatingSystem: String?,
)

// --- Library / Views ---

data class JellyfinItemsResponse(
    @SerializedName("Items") val items: List<JellyfinItem>?,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int?,
    @SerializedName("StartIndex") val startIndex: Int?,
)

data class JellyfinItem(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String?,
    @SerializedName("ServerId") val serverId: String?,
    @SerializedName("Type") val type: String?, // Movie, Series, Episode, Season, etc.
    @SerializedName("Overview") val overview: String?,
    @SerializedName("ProductionYear") val productionYear: Int?,
    @SerializedName("OfficialRating") val officialRating: String?, // PG-13, R, etc.
    @SerializedName("CommunityRating") val communityRating: Float?,
    @SerializedName("CriticRating") val criticRating: Float?,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long?,
    @SerializedName("PremiereDate") val premiereDate: String?,
    @SerializedName("DateCreated") val dateCreated: String?,

    // Hierarchy
    @SerializedName("SeriesId") val seriesId: String?,
    @SerializedName("SeriesName") val seriesName: String?,
    @SerializedName("SeasonId") val seasonId: String?,
    @SerializedName("SeasonName") val seasonName: String?,
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int?, // Season number
    @SerializedName("IndexNumber") val indexNumber: Int?, // Episode number

    // Images
    @SerializedName("ImageTags") val imageTags: Map<String, String>?,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>?,
    @SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>?,
    @SerializedName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String?,

    // External IDs
    @SerializedName("ProviderIds") val providerIds: Map<String, String>?,

    // Genres
    @SerializedName("Genres") val genres: List<String>?,
    @SerializedName("GenreItems") val genreItems: List<JellyfinNameIdPair>?,
    @SerializedName("Studios") val studios: List<JellyfinNameIdPair>?,

    // Playback / User Data
    @SerializedName("UserData") val userData: JellyfinUserData?,
    @SerializedName("MediaSources") val mediaSources: List<JellyfinMediaSource>?,
    @SerializedName("MediaType") val mediaType: String?, // Video, Audio, Photo
    @SerializedName("Container") val container: String?,

    // People (Cast/Crew)
    @SerializedName("People") val people: List<JellyfinPersonDto>?,
    @SerializedName("Taglines") val taglines: List<String>?,

    // Chapters
    @SerializedName("Chapters") val chapters: List<JellyfinChapter>?,

    // Collection info
    @SerializedName("ChildCount") val childCount: Int?,
    @SerializedName("RecursiveItemCount") val recursiveItemCount: Int?,

    // Parent IDs for URL construction
    @SerializedName("ParentId") val parentId: String?,
    @SerializedName("ParentBackdropItemId") val parentBackdropItemId: String?,

    // Library view type (only present on views/folders: "movies", "tvshows", "music", etc.)
    @SerializedName("CollectionType") val collectionType: String?,
)

data class JellyfinNameIdPair(
    @SerializedName("Name") val name: String?,
    @SerializedName("Id") val id: String?,
)

data class JellyfinUserData(
    @SerializedName("PlaybackPositionTicks") val playbackPositionTicks: Long?,
    @SerializedName("PlayCount") val playCount: Int?,
    @SerializedName("IsFavorite") val isFavorite: Boolean?,
    @SerializedName("Played") val played: Boolean?,
    @SerializedName("LastPlayedDate") val lastPlayedDate: String?,
    @SerializedName("UnplayedItemCount") val unplayedItemCount: Int?,
)

// --- Media Streams ---

data class JellyfinMediaSource(
    @SerializedName("Id") val id: String?,
    @SerializedName("Name") val name: String?,
    @SerializedName("Path") val path: String?,
    @SerializedName("Container") val container: String?,
    @SerializedName("Size") val size: Long?,
    @SerializedName("Bitrate") val bitrate: Int?,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long?,
    @SerializedName("SupportsDirectPlay") val supportsDirectPlay: Boolean?,
    @SerializedName("SupportsDirectStream") val supportsDirectStream: Boolean?,
    @SerializedName("SupportsTranscoding") val supportsTranscoding: Boolean?,
    @SerializedName("MediaStreams") val mediaStreams: List<JellyfinMediaStream>?,
)

data class JellyfinMediaStream(
    @SerializedName("Index") val index: Int?,
    @SerializedName("Type") val type: String?, // Video, Audio, Subtitle
    @SerializedName("Codec") val codec: String?,
    @SerializedName("Language") val language: String?,
    @SerializedName("DisplayTitle") val displayTitle: String?,
    @SerializedName("Title") val title: String?,
    @SerializedName("IsDefault") val isDefault: Boolean?,
    @SerializedName("IsForced") val isForced: Boolean?,
    @SerializedName("IsExternal") val isExternal: Boolean?,

    // Video-specific
    @SerializedName("Width") val width: Int?,
    @SerializedName("Height") val height: Int?,
    @SerializedName("BitRate") val bitRate: Int?,
    @SerializedName("VideoRange") val videoRange: String?, // SDR, HDR, etc.

    // Audio-specific
    @SerializedName("Channels") val channels: Int?,
    @SerializedName("SampleRate") val sampleRate: Int?,

    // Subtitle-specific
    @SerializedName("DeliveryMethod") val deliveryMethod: String?, // Embed, External, Hls
    @SerializedName("Path") val path: String?, // External subtitle path
)

data class JellyfinChapter(
    @SerializedName("StartPositionTicks") val startPositionTicks: Long?,
    @SerializedName("Name") val name: String?,
    @SerializedName("ImageTag") val imageTag: String?,
)

data class JellyfinPersonDto(
    @SerializedName("Id") val id: String?,
    @SerializedName("Name") val name: String?,
    @SerializedName("Role") val role: String?,
    @SerializedName("Type") val type: String?, // Actor, Director, Writer, etc.
    @SerializedName("PrimaryImageTag") val primaryImageTag: String?,
)

// --- Playback Reporting ---

data class JellyfinPlaybackStartInfo(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("MediaSourceId") val mediaSourceId: String?,
    @SerializedName("PlaySessionId") val playSessionId: String?,
    @SerializedName("PositionTicks") val positionTicks: Long?,
    @SerializedName("IsPaused") val isPaused: Boolean?,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int?,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int?,
)

data class JellyfinPlaybackProgressInfo(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("MediaSourceId") val mediaSourceId: String?,
    @SerializedName("PlaySessionId") val playSessionId: String?,
    @SerializedName("PositionTicks") val positionTicks: Long?,
    @SerializedName("IsPaused") val isPaused: Boolean?,
    @SerializedName("AudioStreamIndex") val audioStreamIndex: Int?,
    @SerializedName("SubtitleStreamIndex") val subtitleStreamIndex: Int?,
)

data class JellyfinPlaybackStopInfo(
    @SerializedName("ItemId") val itemId: String,
    @SerializedName("MediaSourceId") val mediaSourceId: String?,
    @SerializedName("PlaySessionId") val playSessionId: String?,
    @SerializedName("PositionTicks") val positionTicks: Long?,
)

// --- Views (User Libraries) ---

data class JellyfinViewsResponse(
    @SerializedName("Items") val items: List<JellyfinItem>?,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int?,
)

// --- Similar / Recommendations ---

data class JellyfinSimilarResponse(
    @SerializedName("Items") val items: List<JellyfinItem>?,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int?,
)

// --- Search ---

data class JellyfinSearchHintResponse(
    @SerializedName("SearchHints") val searchHints: List<JellyfinSearchHint>?,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int?,
)

data class JellyfinSearchHint(
    @SerializedName("ItemId") val itemId: String?,
    @SerializedName("Id") val id: String?,
    @SerializedName("Name") val name: String?,
    @SerializedName("Type") val type: String?,
    @SerializedName("ProductionYear") val productionYear: Int?,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String?,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long?,
    @SerializedName("SeriesName") val seriesName: String?,
    @SerializedName("IndexNumber") val indexNumber: Int?,
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int?,
)
