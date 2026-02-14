package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant un Serveur Plex Media Server (PMS).
 * Stocke l'adresse IP, le token, les identifiants de connexion
 * et toutes les connexions candidates (local, public, relay) en JSON.
 */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey
    val clientIdentifier: String,
    val name: String,
    val address: String,
    val port: Int,
    val protocol: String = "http",
    val accessToken: String? = null,
    val isOwned: Boolean = false,
    val relay: Boolean = false,
    val publicAddress: String? = null,
    val httpsRequired: Boolean = false,
    val connectionCandidatesJson: String = "[]",
) {
    fun getBaseUrl(): String = "$protocol://$address:$port/"
}

data class ConnectionCandidateEntity(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
)
