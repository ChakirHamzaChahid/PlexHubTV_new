package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyfinServerDao {

    @Query("SELECT * FROM jellyfin_servers ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<JellyfinServerEntity>>

    @Query("SELECT * FROM jellyfin_servers")
    suspend fun getAll(): List<JellyfinServerEntity>

    @Query("SELECT * FROM jellyfin_servers WHERE id = :id")
    suspend fun getById(id: String): JellyfinServerEntity?

    @Upsert
    suspend fun upsert(entity: JellyfinServerEntity)

    @Query("DELETE FROM jellyfin_servers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE jellyfin_servers SET lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateLastSyncedAt(id: String, timestamp: Long)
}
