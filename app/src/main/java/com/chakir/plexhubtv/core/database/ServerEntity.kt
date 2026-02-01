package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant un Serveur Plex Media Server (PMS).
 * Stocke l'adresse IP, le token et les identifiants de connexion.
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
    val isOwned: Boolean = false
) {
    fun getBaseUrl(): String = "$protocol://$address:$port/"
}
