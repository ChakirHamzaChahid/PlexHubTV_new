package com.chakir.plexhubtv.core.network

/**
 * Abstraction for API response caching with TTL support.
 * Implementation lives in the data layer to avoid network -> database dependency.
 */
interface ApiCache {
    suspend fun get(cacheKey: String): String?

    suspend fun put(
        cacheKey: String,
        data: String,
        ttlSeconds: Int = 3600,
    )

    suspend fun evict(cacheKey: String)
}
