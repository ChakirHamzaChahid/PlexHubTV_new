package com.chakir.plexhubtv.core.util

import com.chakir.plexhubtv.core.model.MediaType

/**
 * Constantes et aides pour la classification des types de contenu Plex.
 */
object ContentTypes {
    const val MOVIE = "movie"
    const val SHOW = "show"
    const val SEASON = "season"
    const val EPISODE = "episode"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val TRACK = "track"
    const val COLLECTION = "collection"
    const val PLAYLIST = "playlist"
    const val CLIP = "clip"

    val MUSIC_TYPES = setOf(ARTIST, ALBUM, TRACK)
    val VIDEO_TYPES = setOf(MOVIE, SHOW, SEASON, EPISODE)
    val PLAYABLE_TYPES = setOf(MOVIE, EPISODE, CLIP, TRACK)
}

object ContentTypeHelper {
    fun isMusicContent(type: String): Boolean = ContentTypes.MUSIC_TYPES.contains(type.lowercase())

    fun isVideoContent(type: String): Boolean = ContentTypes.VIDEO_TYPES.contains(type.lowercase())

    fun isPlayable(type: String): Boolean = ContentTypes.PLAYABLE_TYPES.contains(type.lowercase())
}

/**
 * Formats content rating by removing country prefixes (e.g., "gb/12A" -> "12A")
 */
fun formatContentRating(contentRating: String?): String {
    if (contentRating.isNullOrBlank()) return ""

    // Remove common country prefixes like "gb/", "us/", "de/"
    val regex = Regex("^[a-z]{2,3}/(.+)$", RegexOption.IGNORE_CASE)
    return regex.find(contentRating)?.groupValues?.get(1) ?: contentRating
}

// Extension properties for MediaItem type checking
val com.chakir.plexhubtv.core.model.MediaItem.isShow: Boolean
    get() = type == MediaType.Show

val com.chakir.plexhubtv.core.model.MediaItem.isMovie: Boolean
    get() = type == MediaType.Movie

val com.chakir.plexhubtv.core.model.MediaItem.isSeason: Boolean
    get() = type == MediaType.Season

val com.chakir.plexhubtv.core.model.MediaItem.isEpisode: Boolean
    get() = type == MediaType.Episode

val com.chakir.plexhubtv.core.model.MediaItem.isCollection: Boolean
    get() = type == MediaType.Collection
