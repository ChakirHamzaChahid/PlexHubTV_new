package com.chakir.plexhubtv.core.network.model

import com.google.gson.annotations.SerializedName

data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitleResult> = emptyList(),
    @SerializedName("total_count") val totalCount: Int = 0,
)

data class OpenSubtitleResult(
    val id: String,
    val attributes: OpenSubtitleAttributes,
)

data class OpenSubtitleAttributes(
    val language: String?,
    @SerializedName("download_count") val downloadCount: Int = 0,
    val release: String?,
    @SerializedName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerializedName("foreign_parts_only") val foreignPartsOnly: Boolean = false,
    val fps: Double = 0.0,
    val ratings: Double = 0.0,
    val votes: Int = 0,
    val files: List<OpenSubtitleFile> = emptyList(),
    @SerializedName("feature_details") val featureDetails: FeatureDetails? = null,
)

data class OpenSubtitleFile(
    @SerializedName("file_id") val fileId: Long,
    @SerializedName("file_name") val fileName: String?,
)

data class FeatureDetails(
    val title: String?,
    val year: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("parent_title") val parentTitle: String?,
)

data class OpenSubtitlesDownloadRequest(
    @SerializedName("file_id") val fileId: Long,
)

data class OpenSubtitlesDownloadResponse(
    val link: String?,
    @SerializedName("file_name") val fileName: String?,
    val remaining: Int = 0,
)
