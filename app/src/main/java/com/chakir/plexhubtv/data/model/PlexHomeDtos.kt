package com.chakir.plexhubtv.data.model

data class PlexHomeUserDto(
    val id: Int,
    val uuid: String,
    val title: String,
    val username: String?,
    val email: String?,
    val friendlyName: String?,
    val thumb: String?,
    val hasPassword: Boolean,
    val restricted: Boolean,
    val admin: Boolean,
    val guest: Boolean,
    val protected: Boolean
)

data class UserSwitchResponseDto(
    val authToken: String,
    val user: PlexHomeUserDto?
)
