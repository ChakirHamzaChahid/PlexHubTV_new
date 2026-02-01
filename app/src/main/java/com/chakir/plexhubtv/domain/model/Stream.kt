package com.chakir.plexhubtv.domain.model

/**
 * Représente un flux élémentaire (Stream) au sein d'un fichier média.
 *
 * @property id ID unique du flux.
 * @property index Index séquentiel dans le conteneur.
 * @property language Langue (ex: "English").
 * @property languageCode Code ISO langue (ex: "eng").
 * @property title Titre ou label (ex: "Commentary track").
 * @property displayTitle Titre formaté pour l'affichage.
 * @property codec Codec utilisé (h264, aac, srt...).
 * @property selected Indique si ce flux est sélectionné par défaut.
 */
sealed class MediaStream {
    abstract val id: String
    abstract val index: Int?
    abstract val language: String?
    abstract val languageCode: String?
    abstract val title: String?
    abstract val displayTitle: String?
    abstract val codec: String?
    abstract val selected: Boolean
}

data class AudioStream(
    override val id: String,
    override val index: Int?,
    override val language: String?,
    override val languageCode: String?,
    override val title: String?,
    override val displayTitle: String?,
    override val codec: String?,
    override val selected: Boolean,
    val channels: Int?
) : MediaStream()

data class SubtitleStream(
    override val id: String,
    override val index: Int?,
    override val language: String?,
    override val languageCode: String?,
    override val title: String?,
    override val displayTitle: String?,
    override val codec: String?,
    override val selected: Boolean,
    val forced: Boolean,
    val key: String? // Key for external subtitle file
) : MediaStream() {
    val isExternal: Boolean get() = !key.isNullOrEmpty()
}

data class VideoStream(
    override val id: String,
    override val index: Int?,
    override val language: String?,
    override val languageCode: String?,
    override val title: String?,
    override val displayTitle: String?,
    override val codec: String?,
    override val selected: Boolean,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val hasHDR: Boolean = false
) : MediaStream()

data class UnknownStream(
    override val id: String,
    override val index: Int?,
    override val language: String?,
    override val languageCode: String?,
    override val title: String?,
    override val displayTitle: String?,
    override val codec: String?,
    override val selected: Boolean
) : MediaStream()
