package com.chakir.plexhubtv.core.model

import androidx.compose.runtime.Immutable

/**
 * Entité principale représentant un élément multimédia unifié.
 *
 * Cette classe est agnostique du serveur (Server Agnostic) : elle peut représenter un média fusionné
 * provenant de plusieurs serveurs (via [remoteSources]).
 *
 * @property id Identifiant unique composite (souvent "serverId:ratingKey").
 * @property ratingKey La clé de métadonnées Plex originelle.
 * @property serverId L'identifiant du serveur source principal.
 * @property unificationId Identifiant de dédoublonnage (IMDB, TMDB, GUID).
 * @property title Titre du média.
 * @property type Type de média (Film, Série, Épisode...).
 * @property thumbUrl URL de l'affiche (poster).
 * @property artUrl URL de l'image de fond (fanart).
 * @property summary Résumé ou synopsis.
 * @property year Année de sortie.
 * @property durationMs Durée en millisecondes.
 * @property isWatched Si vrai, le média a été vu.
 * @property unificationId ID utilisé pour le dédoublonnage (IMDB/TMDB/GUID).
 * @property remoteSources Liste des serveurs alternatifs proposant ce même média.
 */

@Immutable
data class MediaItem(
    val id: String, // serverId + ratingKey
    val ratingKey: String,
    val serverId: String,
    val unificationId: String? = null, // For deduplication
    val title: String,
    val type: MediaType,
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    // Alternative poster URLs from other servers for fallback if primary fails
    val alternativeThumbUrls: List<String> = emptyList(),
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
    // Hierarchie (Séries/Saisons)
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentRatingKey: String? = null,
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val parentIndex: Int? = null, // Saison
    val episodeIndex: Int? = null, // Épisode
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
    // Métadonnées riches
    val chapters: List<Chapter> = emptyList(),
    val markers: List<Marker> = emptyList(),
    // Agrégation
    val remoteSources: List<MediaSource> = emptyList(),
    // Extras (trailers, behind the scenes, etc.)
    val extras: List<Extra> = emptyList(),
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
    val artUrl: String? = null,
)

data class CastMember(
    val id: String?,
    val filter: String?,
    val role: String?,
    val tag: String?,
    val thumb: String?,
)

data class Extra(
    val ratingKey: String,
    val title: String,
    val subtype: ExtraType,
    val thumbUrl: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val playbackKey: String? = null,
    val mediaParts: List<MediaPart> = emptyList(),
    val baseUrl: String? = null,
    val accessToken: String? = null,
)

enum class ExtraType {
    Trailer,
    BehindTheScenes,
    SceneOrSample,
    DeletedScene,
    Interview,
    Featurette,
    Unknown;

    companion object {
        fun fromPlex(subtype: String?): ExtraType = when (subtype) {
            "trailer" -> Trailer
            "behindTheScenes" -> BehindTheScenes
            "sceneOrSample" -> SceneOrSample
            "deletedScene" -> DeletedScene
            "interview" -> Interview
            "featurette" -> Featurette
            else -> Unknown
        }
    }
}

enum class MediaType {
    Movie,
    Show,
    Season,
    Episode,
    Collection,
    Playlist,
    Artist,
    Album,
    Track,
    Clip,
    Photo,
    Unknown,
}
