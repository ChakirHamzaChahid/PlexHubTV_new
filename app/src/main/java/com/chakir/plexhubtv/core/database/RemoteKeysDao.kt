package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO pour accéder aux clés de pagination (RemoteKeys).
 * Critique pour le bon fonctionnement du chargement infini (Infinite Scroll).
 */
@Dao
interface RemoteKeysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(remoteKey: RemoteKey)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKeys: List<RemoteKey>)

    // Query for a specific page (offset) - Used by Remote Mediator to find "current key"
    @Query("SELECT * FROM remote_keys WHERE libraryKey = :libraryKey AND filter = :filter AND sortOrder = :sortOrder AND offset = :offset")
    suspend fun getRemoteKey(libraryKey: String, filter: String, sortOrder: String, offset: Int): RemoteKey?

    @Query("SELECT * FROM remote_keys WHERE libraryKey = :libraryKey AND filter = :filter AND sortOrder = :sortOrder AND offset <= :offset ORDER BY offset DESC LIMIT 1")
    suspend fun getClosestKey(libraryKey: String, filter: String, sortOrder: String, offset: Int): RemoteKey?
    
    // Check if cache exists for a given library/filter/sort combination
    @Query("SELECT * FROM remote_keys WHERE libraryKey = :libraryKey AND filter = :filter AND sortOrder = :sortOrder ORDER BY offset ASC LIMIT 1")
    suspend fun getFirstKey(libraryKey: String, filter: String, sortOrder: String): RemoteKey?
    
    @Query("DELETE FROM remote_keys WHERE libraryKey = :libraryKey AND filter = :filter AND sortOrder = :sortOrder")
    suspend fun clearByLibraryFilterSort(libraryKey: String, filter: String, sortOrder: String)
    
    @Query("DELETE FROM remote_keys WHERE libraryKey = :libraryKey")
    suspend fun clearByLibrary(libraryKey: String)
}
