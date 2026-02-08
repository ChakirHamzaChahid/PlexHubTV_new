package com.chakir.plexhubtv.core.network.model

/**
 * DTO pour un utilisateur d'une "Maison Plex" (Plex Home).
 */
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
    val protected: Boolean,
)

/**
 * Réponse lors du changement d'utilisateur (User Switch).
 */
data class UserSwitchResponseDto(
    val authToken: String,
    val user: PlexHomeUserDto?,
)
