package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApiCacheDao {

    @Query("SELECT * FROM api_cache WHERE cacheKey = :key")
    suspend fun getEntry(key: String): ApiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entry: ApiCacheEntity)

    @Query("DELETE FROM api_cache WHERE cacheKey = :key")
    suspend fun deleteEntry(key: String)

    @Query("DELETE FROM api_cache WHERE cachedAt + (ttlSeconds * 1000) < :currentTimeMillis")
    suspend fun purgeExpired(currentTimeMillis: Long)

    @Query("DELETE FROM api_cache")
    suspend fun clearAll()
}
