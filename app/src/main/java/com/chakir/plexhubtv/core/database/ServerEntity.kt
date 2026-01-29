package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

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
