package com.chakir.plexhubtv.core.network.xtream

import com.google.gson.annotations.SerializedName

// ── Authentication ─────────────────────────────────────────

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: XtreamUserInfo?,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo?,
)

data class XtreamUserInfo(
    val username: String?,
    val password: String?,
    val auth: Int?,
    val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("max_connections") val maxConnections: String?,
    @SerializedName("allowed_output_formats") val allowedOutputFormats: List<String>?,
)

data class XtreamServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val serverProtocol: String?,
)

// ── Categories ─────────────────────────────────────────────

data class XtreamCategoryDto(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("parent_id") val parentId: Int?,
)

// ── VOD Streams (Movies) ──────────────────────────────────

data class XtreamVodStreamDto(
    val num: Int?,
    val name: String?,
    @SerializedName("stream_type") val streamType: String?,
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("stream_icon") val streamIcon: String?,
    val rating: String?,
    @SerializedName("rating_5based") val rating5based: String?,
    val added: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("custom_sid") val customSid: String?,
    @SerializedName("direct_source") val directSource: String?,
)

// ── Series ─────────────────────────────────────────────────

data class XtreamSeriesDto(
    val num: Int?,
    val name: String?,
    @SerializedName("series_id") val seriesId: Int?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    @SerializedName("last_modified") val lastModified: String?,
    val rating: String?,
    @SerializedName("rating_5based") val rating5based: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("youtube_trailer") val youtubeTrailer: String?,
    @SerializedName("episode_run_time") val episodeRunTime: String?,
    @SerializedName("category_id") val categoryId: String?,
)

// ── Series Info (detail with seasons + episodes) ──────────

data class XtreamSeriesInfoResponse(
    val seasons: List<XtreamSeasonDto>?,
    val episodes: Map<String, List<XtreamEpisodeDto>>?,
    val info: XtreamSeriesDto?,
)

data class XtreamSeasonDto(
    @SerializedName("season_number") val seasonNumber: Int?,
    val name: String?,
    val cover: String?,
    @SerializedName("episode_count") val episodeCount: Int?,
)

data class XtreamEpisodeDto(
    val id: String?,
    @SerializedName("episode_num") val episodeNum: Int?,
    val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: XtreamEpisodeInfoDto?,
)

data class XtreamEpisodeInfoDto(
    @SerializedName("duration_secs") val durationSecs: Int?,
    val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
    val plot: String?,
    val releasedate: String?,
    val rating: String?,
)
