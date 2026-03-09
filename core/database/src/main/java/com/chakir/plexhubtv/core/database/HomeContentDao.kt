package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO pour le contenu de l'écran d'accueil mis en cache.
 * Permet d'afficher l'interface instantanément au démarrage avant le rafraîchissement réseau.
 */
@Dao
interface HomeContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeContent(items: List<HomeContentEntity>)

    @Query("DELETE FROM home_content WHERE type = :type AND hubIdentifier = :hubIdentifier")
    suspend fun clearHomeContent(
        type: String,
        hubIdentifier: String,
    )

    @Query("DELETE FROM home_content WHERE type = :type")
    suspend fun clearHomeContentByType(type: String)

    /**
     * Atomically replaces home content: clears existing entries then inserts fresh ones.
     * Without @Transaction, a failure after clear leaves the section empty until next refresh.
     */
    @Transaction
    suspend fun replaceHomeContent(type: String, hubIdentifier: String, items: List<HomeContentEntity>) {
        clearHomeContent(type, hubIdentifier)
        insertHomeContent(items)
    }

    // Join with MediaEntity to get full objects
    @Transaction
    @Query(
        """
        SELECT m.* 
        FROM home_content h
        INNER JOIN media m ON h.itemServerId = m.serverId AND h.itemRatingKey = m.ratingKey
        WHERE h.type = :type AND h.hubIdentifier = :hubIdentifier
        AND m.filter = 'all'
        ORDER BY h.orderIndex ASC
    """,
    )
    suspend fun getHomeMediaItems(
        type: String,
        hubIdentifier: String,
    ): List<MediaEntity>

    @Query("SELECT DISTINCT hubIdentifier, title FROM home_content WHERE type = 'hub' ORDER BY title ASC")
    suspend fun getHubsList(): List<HubInfo>
}

data class HubInfo(val hubIdentifier: String, val title: String)
