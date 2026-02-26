package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "xtream_accounts")
data class XtreamAccountEntity(
    @PrimaryKey val id: String,
    val label: String,
    val baseUrl: String,
    val port: Int,
    val username: String,
    val passwordKey: String,
    val status: String,
    val expirationDate: Long?,
    val maxConnections: Int,
    val allowedFormatsJson: String,
    val serverUrl: String?,
    val httpsPort: Int?,
    val lastSyncedAt: Long = 0,
)
