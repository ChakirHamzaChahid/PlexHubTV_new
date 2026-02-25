package com.chakir.plexhubtv.core.network.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper racine pour une réponse Plex standard contenant un MediaContainer.
 */
data class PlexResponse(
    @SerializedName("MediaContainer") val mediaContainer: MediaContainer?,
)

data class GenreDTO(
    @SerializedName("tag") val tag: String,
)

data class CollectionDTO(
    val id: String? = null,
    val ratingKey: String? = null,
    @SerializedName("tag") val tag: String,
    val guid: String? = null,
)

/**
 * Conteneur principal des métadonnées renvoyées par Plex (PMS).
 * Peut contenir des métadonnées, des Hubs, des Serveurs, ou des Répertoires (Sections).
 */
data class MediaContainer(
    val size: Int = 0,
    val totalSize: Int = 0,
    val offset: Int = 0,
    val title: String? = null,
    @SerializedName("Metadata") val metadata: List<MetadataDTO>? = null,
    @SerializedName("Hub") val hubs: List<HubDTO>? = null,
    @SerializedName("Server") val servers: List<ServerDTO>? = null,
    @SerializedName("Directory") val directory: List<DirectoryDTO>? = null,
)

data class DirectoryDTO(
    val key: String?,
    val title: String?,
    val type: String?, // movie, show
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val composite: String? = null,
)

data class MetadataDTO(
    val ratingKey: String,
    val key: String,
    val title: String,
    val type: String, // movie, show, episode
    val summary: String? = null,
    val thumb: String? = null,
    val art: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val year: Int? = null,
    val tagline: String? = null,
    val studio: String? = null,
    val contentRating: String? = null,
    val audienceRating: Double? = null,
    val rating: Double? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val guid: String? = null,
    val lastViewedAt: Long? = null,
    val librarySectionID: String? = null,
    @SerializedName("Guid") val guids: List<GuidDTO>? = null,
    // Hierarchy
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val grandparentTheme: String? = null,
    val grandparentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val parentRatingKey: String? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val childCount: Int? = null,
    // Playback info
    @SerializedName("Media") val media: List<MediaDTO>? = null,
    @SerializedName("Role") val roles: List<RoleDTO>? = null,
    @SerializedName("Chapter") val chapters: List<ChapterDTO>? = null,
    @SerializedName("Marker") val markers: List<MarkerDTO>? = null,
    @SerializedName("Genre") val genres: List<GenreDTO>? = null,
    @SerializedName("Collection") val collections: List<CollectionDTO>? = null,
    val status: String? = null,
    @SerializedName("Status") val statusAlt: String? = null,
    val seriesStatus: String? = null,
    val originallyAvailableAt: String? = null,
    val subtype: String? = null, // Extra type: trailer, behindTheScenes, sceneOrSample, etc.
    @SerializedName("Extras") val extras: ExtrasContainerDTO? = null,
)

data class ExtrasContainerDTO(
    val size: Int = 0,
    @SerializedName("Metadata") val metadata: List<MetadataDTO>? = null,
)

data class ChapterDTO(
    val id: Long?,
    val filter: String?,
    @SerializedName("tag") val title: String?, // Plex uses 'tag' for title mainly
    val startTimeOffset: Long?,
    val endTimeOffset: Long?,
    val thumb: String?,
)

data class MarkerDTO(
    val id: Long?,
    val type: String?, // intro, credits
    val startTimeOffset: Long?,
    val endTimeOffset: Long?,
)

data class RoleDTO(
    val id: String?,
    val filter: String?,
    val role: String?,
    val tag: String?,
    val thumb: String?,
)

data class GuidDTO(
    val id: String,
)

data class MediaDTO(
    val id: String,
    val duration: Long?,
    val bitrate: Int?,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Double?,
    val audioChannels: Int?,
    val audioCodec: String?,
    val videoCodec: String?,
    val container: String?,
    @SerializedName("Part") val parts: List<PartDTO>? = null,
)

data class PartDTO(
    val id: String,
    val key: String,
    val duration: Long?,
    val file: String?,
    val size: Long?,
    val container: String?,
    @SerializedName("Stream") val streams: List<StreamDTO>? = null,
)

data class StreamDTO(
    val id: String,
    val streamType: Int, // 1: Video, 2: Audio, 3: Subtitle
    val codec: String?,
    val index: Int?,
    val language: String?,
    val languageCode: String?,
    val title: String?,
    val displayTitle: String?,
    val selected: Boolean = false,
    val forced: Boolean = false,
    val channels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val key: String? = null,
    val colorRange: String? = null,
    val colorSpace: String? = null,
    val colorPrimaries: String? = null,
    val colorTransfer: String? = null,
    val profile: String? = null,
)

data class HubDTO(
    val key: String,
    val title: String,
    val type: String,
    val hubIdentifier: String? = null,
    @SerializedName("Metadata") val metadata: List<MetadataDTO>? = null,
)

data class ServerDTO(
    val name: String,
    val address: String,
    val port: Int,
    val version: String?,
    val scheme: String?,
    val host: String?,
    val localAddresses: String?,
    val machineIdentifier: String,
    val accessToken: String?,
)
