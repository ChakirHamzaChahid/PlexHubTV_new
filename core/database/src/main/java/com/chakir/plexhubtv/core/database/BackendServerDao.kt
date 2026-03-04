package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BackendServerDao {
    @Query("SELECT * FROM backend_servers")
    fun observeAll(): Flow<List<BackendServerEntity>>

    @Query("SELECT * FROM backend_servers WHERE id = :id")
    suspend fun getById(id: String): BackendServerEntity?

    @Upsert
    suspend fun upsert(entity: BackendServerEntity)

    @Query("DELETE FROM backend_servers WHERE id = :id")
    suspend fun delete(id: String)
}
