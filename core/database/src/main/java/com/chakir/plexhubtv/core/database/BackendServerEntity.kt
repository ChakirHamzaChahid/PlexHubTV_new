package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backend_servers")
data class BackendServerEntity(
    @PrimaryKey val id: String,
    val label: String,
    val baseUrl: String,
    val isActive: Boolean = true,
    val lastSyncedAt: Long = 0,
)
