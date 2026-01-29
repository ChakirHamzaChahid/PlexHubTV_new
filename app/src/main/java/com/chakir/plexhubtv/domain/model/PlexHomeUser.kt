package com.chakir.plexhubtv.domain.model

data class PlexHomeUser(
    val id: Int,
    val uuid: String,
    val title: String,
    val username: String?,
    val email: String? = null,
    val friendlyName: String?,
    val thumb: String?,
    val hasPassword: Boolean,
    val restricted: Boolean = false,
    val protected: Boolean,
    val admin: Boolean,
    val guest: Boolean
) {
    val displayName: String get() = friendlyName ?: title
}
