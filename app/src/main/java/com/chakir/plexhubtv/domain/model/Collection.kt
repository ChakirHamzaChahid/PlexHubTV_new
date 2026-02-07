package com.chakir.plexhubtv.domain.model

data class Collection(
    val id: String,
    val serverId: String,
    val title: String,
    val summary: String? = null,
    val thumbUrl: String? = null,
    val items: List<MediaItem>
)
