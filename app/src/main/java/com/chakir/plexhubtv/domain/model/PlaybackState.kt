package com.chakir.plexhubtv.domain.model

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val currentMedia: MediaItem? = null
)
