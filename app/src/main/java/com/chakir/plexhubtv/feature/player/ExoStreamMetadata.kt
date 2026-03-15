package com.chakir.plexhubtv.feature.player

import androidx.media3.common.Format

class ExoStreamMetadata(
    private val format: Format?,
    private val videoSize: androidx.media3.common.VideoSize?,
    private val audioFormat: Format? = null,
    override val decoderName: String? = null,
) : StreamMetadata {
    override val bitrate: Int = format?.bitrate ?: 0
    override val sampleMimeType: String? = format?.sampleMimeType
    override val frameRate: Float = format?.frameRate ?: 0f
    override val width: Int = videoSize?.width ?: 0
    override val height: Int = videoSize?.height ?: 0
    override val audioMimeType: String? = audioFormat?.sampleMimeType
    override val audioChannelCount: Int = audioFormat?.channelCount ?: 0
    override val audioBitrate: Int = audioFormat?.bitrate ?: 0
    override val audioSampleRate: Int = audioFormat?.sampleRate ?: 0
}
