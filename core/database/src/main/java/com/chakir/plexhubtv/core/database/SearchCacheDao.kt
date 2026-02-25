package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE query = :query AND serverId = :serverId")
    suspend fun get(query: String, serverId: String): SearchCacheEntity?

    @Query("SELECT * FROM search_cache WHERE query = :query")
    suspend fun getAll(query: String): List<SearchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE lastUpdated < :minTimestamp")
    suspend fun deleteExpired(minTimestamp: Long = System.currentTimeMillis() - 86_400_000) // 24h

    @Query("DELETE FROM search_cache")
    suspend fun deleteAll()
}
