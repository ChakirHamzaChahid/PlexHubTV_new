package com.chakir.plexhubtv.feature.player

interface StreamMetadata {
    val bitrate: Int
    val sampleMimeType: String?
    val frameRate: Float
    val width: Int
    val height: Int
    val decoderName: String?
    val audioMimeType: String?
    val audioChannelCount: Int
    val audioBitrate: Int
    val audioSampleRate: Int
}
