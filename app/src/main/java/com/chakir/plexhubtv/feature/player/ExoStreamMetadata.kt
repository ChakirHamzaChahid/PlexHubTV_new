package com.chakir.plexhubtv.feature.player

import androidx.media3.common.Format

class ExoStreamMetadata(private val format: Format?, private val videoSize: androidx.media3.common.VideoSize?) : StreamMetadata {
    override val bitrate: Int = format?.bitrate ?: 0
    override val sampleMimeType: String? = format?.sampleMimeType
    override val frameRate: Float = format?.frameRate ?: 0f
    override val width: Int = videoSize?.width ?: 0
    override val height: Int = videoSize?.height ?: 0
}
