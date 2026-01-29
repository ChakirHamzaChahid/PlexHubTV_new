package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_cache")
data class ApiCacheEntity(
    @PrimaryKey
    val cacheKey: String, // serverId:endpoint
    val data: String, // Raw JSON
    val cachedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)
