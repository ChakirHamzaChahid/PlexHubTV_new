package com.chakir.plexhubtv.core.network.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper pour la réponse de l'endpoint /api/v2/home/users.
 * La réponse est encapsulée dans un MediaContainer.
 */
data class PlexHomeUsersResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlexHomeUsersContainer?,
)

/**
 * Conteneur pour la liste des utilisateurs Plex Home.
 */
data class PlexHomeUsersContainer(
    @SerializedName("User") val users: List<PlexHomeUserDto>? = null,
)

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
