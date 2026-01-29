package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApiCacheDao {
    @Query("SELECT * FROM api_cache WHERE cacheKey = :key")
    suspend fun getCache(key: String): ApiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: ApiCacheEntity)

    @Query("DELETE FROM api_cache WHERE cacheKey LIKE :prefix")
    suspend fun deleteForServer(prefix: String)

    @Query("SELECT * FROM api_cache WHERE pinned = 1")
    suspend fun getPinnedItems(): List<ApiCacheEntity>
}
