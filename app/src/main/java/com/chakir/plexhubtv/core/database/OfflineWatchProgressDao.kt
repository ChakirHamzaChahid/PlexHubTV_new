package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la file d'attente de synchronisation du statut de visionnage.
 * Permet de stocker les actions (vu/non-vu) faites hors-ligne pour les rejouer une fois connecté.
 */
@Dao
interface OfflineWatchProgressDao {

    @Query("SELECT * FROM offline_watch_progress WHERE serverId = :serverId ORDER BY createdAt ASC")
    suspend fun getPendingActionsForServer(serverId: String): List<OfflineWatchProgressEntity>

    @Query("SELECT * FROM offline_watch_progress WHERE globalKey = :globalKey ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestWatchAction(globalKey: String): OfflineWatchProgressEntity?

    @Query("SELECT * FROM offline_watch_progress WHERE globalKey IN (:globalKeys)")
    suspend fun getLatestWatchActionsForKeys(globalKeys: List<String>): List<OfflineWatchProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressAction(action: OfflineWatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchAction(action: OfflineWatchProgressEntity)

    @Query("DELETE FROM offline_watch_progress WHERE id = :id")
    suspend fun deleteWatchAction(id: Int)

    @Query("DELETE FROM offline_watch_progress WHERE globalKey = :globalKey")
    suspend fun deleteActionsForItem(globalKey: String)

    @Query("SELECT COUNT(*) FROM offline_watch_progress")
    suspend fun getPendingSyncCount(): Int

    @Query("SELECT COUNT(*) FROM offline_watch_progress")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM offline_watch_progress ORDER BY createdAt ASC")
    suspend fun getPendingWatchActions(): List<OfflineWatchProgressEntity>

    @Query("UPDATE offline_watch_progress SET syncAttempts = syncAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun updateSyncAttempt(id: Int, error: String)

    @Query("DELETE FROM offline_watch_progress")
    suspend fun clearAllWatchActions()
}
