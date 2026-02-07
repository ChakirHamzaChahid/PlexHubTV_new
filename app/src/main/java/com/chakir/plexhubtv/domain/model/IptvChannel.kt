package com.chakir.plexhubtv.domain.model

data class IptvChannel(
    val id: String, // tvg-id or unique hash
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val streamUrl: String
)
