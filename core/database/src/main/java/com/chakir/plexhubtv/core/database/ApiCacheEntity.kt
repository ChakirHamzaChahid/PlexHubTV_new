package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant une réponse API mise en cache.
 * Utilisée pour réduire les appels réseau et offrir un mode hors-ligne partiel.
 */
@Entity(tableName = "api_cache")
data class ApiCacheEntity(
    @PrimaryKey
    val cacheKey: String, // serverId:endpoint
    val data: String, // Raw JSON
    val cachedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val ttlSeconds: Int = 3600, // Default 1h
) {
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis()
        val expirationTime = cachedAt + (ttlSeconds * 1000L)
        return now > expirationTime
    }
}
