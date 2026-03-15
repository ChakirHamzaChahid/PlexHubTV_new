package com.chakir.plexhubtv.core.model

data class Playlist(
    val id: String,
    val serverId: String,
    val title: String,
    val summary: String? = null,
    val thumbUrl: String? = null,
    val itemCount: Int = 0,
    val durationMs: Long = 0,
    val items: List<MediaItem> = emptyList(),
)
