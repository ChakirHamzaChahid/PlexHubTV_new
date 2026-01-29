package com.chakir.plexhubtv.domain.model

data class PlexLibrary(
    val key: String,
    val title: String,
    val type: String,
    val serverId: String,
    val serverName: String? = null,
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val uuid: String? = null,
    val updatedAt: Long? = null,
    val createdAt: Long? = null,
    val hidden: Boolean = false,
    val isVirtual: Boolean = false // For client-side aggregations
) {
    val globalKey: String get() = "$serverId:$key"
}
