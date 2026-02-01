package com.chakir.plexhubtv.data.model

import com.google.gson.annotations.SerializedName

/**
 * DTO représentant la réponse lors d'une demande de PIN (Plex.tv/api/v2/pins).
 */
data class PinResponse(
    val id: Int,
    val code: String,
    val product: String,
    val trusted: Boolean,
    @SerializedName("clientIdentifier")
    val clientIdentifier: String,
    val location: Location? = null,
    @SerializedName("expiresIn")
    val expiresIn: Int,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("expiresAt")
    val expiresAt: String,
    @SerializedName("authToken")
    val authToken: String?,
    @SerializedName("newRegistration")
    val newRegistration: Boolean?
)

data class Location(
    val code: String,
    val european_union_member: Boolean,
    val continent_code: String,
    val country: String,
    val city: String,
    val time_zone: String,
    val postal_code: String,
    val in_privacy_restricted_country: Boolean,
    val subdivisions: String,
    val coordinates: String
)

/**
 * DTO représentant une ressource Plex (Serveur, Client, etc.) récupérée via /api/v2/resources.
 * Contient la liste des connexions possibles.
 */
data class PlexResource(
    val name: String,
    val product: String,
    @SerializedName("productVersion")
    val productVersion: String,
    val platform: String? = null,
    @SerializedName("clientIdentifier")
    val clientIdentifier: String,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("lastSeenAt")
    val lastSeenAt: String,
    val provides: String,
    @SerializedName("ownerId")
    val ownerId: String?,
    @SerializedName("accessToken")
    val accessToken: String?,
    val owned: Boolean,
    val home: Boolean,
    val synced: Boolean,
    val relay: Boolean,
    val presence: Boolean,
    @SerializedName("httpsRequired")
    val httpsRequired: String? = null,
    @SerializedName("publicAddress")
    val publicAddress: String? = null,
    val connections: List<PlexConnection>?
)

data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean
)
