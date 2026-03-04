package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdBridgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IdBridgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IdBridgeEntity>)

    @Query("SELECT imdbId FROM id_bridge WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getImdbIdByTmdb(tmdbId: String): String?
}
