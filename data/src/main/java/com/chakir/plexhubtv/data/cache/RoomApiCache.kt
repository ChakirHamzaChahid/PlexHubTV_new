package com.chakir.plexhubtv.data.cache

import com.chakir.plexhubtv.core.database.ApiCacheDao
import com.chakir.plexhubtv.core.database.ApiCacheEntity
import com.chakir.plexhubtv.core.network.ApiCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ApiCache].
 * Manages API response caching with TTL support using Room.
 */
@Singleton
class RoomApiCache
    @Inject
    constructor(
        private val apiCacheDao: ApiCacheDao,
    ) : ApiCache {
        override suspend fun get(cacheKey: String): String? {
            return withContext(Dispatchers.IO) {
                val entry = apiCacheDao.getEntry(cacheKey)
                if (entry != null) {
                    if (entry.isExpired()) {
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

        override suspend fun put(
            cacheKey: String,
            data: String,
            ttlSeconds: Int,
        ) {
            withContext(Dispatchers.IO) {
                val entry =
                    ApiCacheEntity(
                        cacheKey = cacheKey,
                        data = data,
                        cachedAt = System.currentTimeMillis(),
                        ttlSeconds = ttlSeconds,
                    )
                apiCacheDao.insertCache(entry)
            }
        }

        override suspend fun evict(cacheKey: String) {
            withContext(Dispatchers.IO) {
                apiCacheDao.deleteEntry(cacheKey)
            }
        }
    }
