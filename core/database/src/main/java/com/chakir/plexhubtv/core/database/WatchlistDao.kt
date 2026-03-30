package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY orderIndex ASC")
    fun getAllWatchlistItems(): Flow<List<WatchlistEntity>>

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WatchlistEntity>)

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<WatchlistEntity>) {
        clearAll()
        insertAll(items)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE guid = :guid)")
    fun isInWatchlistByGuid(guid: String): Flow<Boolean>
}
