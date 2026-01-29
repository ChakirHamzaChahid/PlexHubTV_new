package com.chakir.plexhubtv.domain.model

data class Hub(
    val key: String,
    val title: String,
    val type: String,
    val hubIdentifier: String? = null,
    val items: List<MediaItem>,
    val serverId: String? = null
)
