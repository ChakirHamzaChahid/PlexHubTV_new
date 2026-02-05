package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.database.ApiCacheDao
import com.chakir.plexhubtv.core.database.ApiCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to manage API response caching with TTL support.
 * Mimics Plezy's _fetchWithCacheFallback behavior using Room.
 */
@Singleton
class PlexApiCache @Inject constructor(
    private val apiCacheDao: ApiCacheDao
) {

    /**
     * Tries to get value from cache.
     * Returns null if not found or expired.
     */
    suspend fun get(cacheKey: String): String? {
        return withContext(Dispatchers.IO) {
            val entry = apiCacheDao.getEntry(cacheKey)
            if (entry != null) {
                if (entry.isExpired()) {
                    // Lazy expiration: delete if expired
                    apiCacheDao.deleteEntry(cacheKey)
                    null
                } else {
                    entry.data
                }
            } else {
                null
            }
        }
    }

    /**
     * Stores value in cache with specified TTL.
     */
    suspend fun put(cacheKey: String, data: String, ttlSeconds: Int = 3600) {
        withContext(Dispatchers.IO) {
            val entry = ApiCacheEntity(
                cacheKey = cacheKey,
                data = data,
                cachedAt = System.currentTimeMillis(),
                ttlSeconds = ttlSeconds
            )
            apiCacheDao.insertCache(entry)
        }
    }

    /**
     * Clears specific entry.
     */
    suspend fun evict(cacheKey: String) {
        withContext(Dispatchers.IO) {
            apiCacheDao.deleteEntry(cacheKey)
        }
    }
}
