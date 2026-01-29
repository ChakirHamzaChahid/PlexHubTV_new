package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HomeContentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeContent(items: List<HomeContentEntity>)

    @Query("DELETE FROM home_content WHERE type = :type AND hubIdentifier = :hubIdentifier")
    suspend fun clearHomeContent(type: String, hubIdentifier: String)
    
    @Query("DELETE FROM home_content WHERE type = :type")
    suspend fun clearHomeContentByType(type: String)

    // Join with MediaEntity to get full objects
    // Note: We need to handle the join carefully. Room can do this via a POJO or map.
    // Simpler: Get the references, then fetch media. Or use a join query.
    
    @Transaction
    @Query("""
        SELECT m.* 
        FROM home_content h
        INNER JOIN media m ON h.itemServerId = m.serverId AND h.itemRatingKey = m.ratingKey
        WHERE h.type = :type AND h.hubIdentifier = :hubIdentifier
        ORDER BY h.orderIndex ASC
    """)
    suspend fun getHomeMediaItems(type: String, hubIdentifier: String): List<MediaEntity>

    @Query("SELECT DISTINCT hubIdentifier, title FROM home_content WHERE type = 'hub' ORDER BY title ASC") // Or use stored order if we add hubOrder
    suspend fun getHubsList(): List<HubInfo>
    
    data class HubInfo(val hubIdentifier: String, val title: String)
}
