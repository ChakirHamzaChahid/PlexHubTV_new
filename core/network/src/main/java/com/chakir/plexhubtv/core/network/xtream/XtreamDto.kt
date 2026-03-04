package com.chakir.plexhubtv.core.network.xtream

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

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
    @JsonAdapter(StringOrStringListDeserializer::class)
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("youtube_trailer") val youtubeTrailer: String?,
    @SerializedName("episode_run_time") val episodeRunTime: String?,
    @SerializedName("category_id") val categoryId: String?,
)

// ── VOD Info (detail for a single movie) ──────────────────

data class XtreamVodInfoResponse(
    val info: XtreamVodInfoDto?,
    @SerializedName("movie_data") val movieData: XtreamVodMovieDataDto?,
)

data class XtreamVodInfoDto(
    @SerializedName("tmdb_id") val tmdbId: Int?,
    val name: String?,
    @SerializedName("o_name") val originalName: String?,
    @SerializedName("cover_big") val coverBig: String?,
    @SerializedName("movie_image") val movieImage: String?,
    val releasedate: String?,
    val director: String?,
    val cast: String?,
    val description: String?,
    val plot: String?,
    val genre: String?,
    @JsonAdapter(StringOrStringListDeserializer::class)
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("duration_secs") val durationSecs: Int?,
    val rating: String?,
)

data class XtreamVodMovieDataDto(
    @SerializedName("stream_id") val streamId: Int?,
    val name: String?,
    val added: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
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

/**
 * Xtream APIs inconsistently return `backdrop_path` as either a JSON array
 * (`["path1","path2"]`) or a plain string (`""` / `"some_path"`).
 * This deserializer handles both cases gracefully.
 */
class StringOrStringListDeserializer : JsonDeserializer<List<String>?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): List<String>? {
        if (json == null || json.isJsonNull) return null
        if (json.isJsonArray) {
            return json.asJsonArray.mapNotNull { elem ->
                elem.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            }
        }
        if (json.isJsonPrimitive) {
            val value = json.asString
            return if (value.isNullOrBlank()) emptyList() else listOf(value)
        }
        return null
    }
}
