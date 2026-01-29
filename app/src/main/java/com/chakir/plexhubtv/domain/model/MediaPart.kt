package com.chakir.plexhubtv.domain.model

data class MediaPart(
    val id: String,
    val key: String,
    val duration: Long?,
    val file: String?,
    val size: Long?,
    val container: String?,
    val streams: List<MediaStream> = emptyList()
)
