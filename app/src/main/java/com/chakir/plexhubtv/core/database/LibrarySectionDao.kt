package com.chakir.plexhubtv.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour accéder aux métadonnées des bibliothèques (Sections) mises en cache.
 */
@Dao
interface LibrarySectionDao {
    @Query("SELECT * FROM library_sections WHERE serverId = :serverId")
    fun getLibrarySections(serverId: String): Flow<List<LibrarySectionEntity>>

    @Query("SELECT * FROM library_sections WHERE serverId = :serverId AND type = :type LIMIT 1")
    suspend fun getLibrarySectionByType(serverId: String, type: String): LibrarySectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrarySections(sections: List<LibrarySectionEntity>)

    @Query("DELETE FROM library_sections WHERE serverId = :serverId")
    suspend fun deleteLibrarySections(serverId: String)
}
