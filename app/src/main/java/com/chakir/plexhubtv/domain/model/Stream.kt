package com.chakir.plexhubtv.domain.model

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
