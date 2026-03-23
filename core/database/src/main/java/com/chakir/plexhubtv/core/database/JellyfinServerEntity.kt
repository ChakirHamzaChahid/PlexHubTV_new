package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists Jellyfin server credentials in Room.
 *
 * The [id] is the Jellyfin server's own `Id` from `/System/Info/Public`.
 * The access token is stored in [SecurePreferencesManager] under
 * key `"jellyfin_token_{id}"` — NOT in this table.
 */
@Entity(tableName = "jellyfin_servers")
data class JellyfinServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val userId: String,
    val userName: String,
    val version: String = "",
    val isActive: Boolean = true,
    val lastSyncedAt: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
