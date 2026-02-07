package com.chakir.plexhubtv.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    // Determine which collection(s) a specific media item belongs to
    @Query("""
        SELECT c.* 
        FROM collections c
        INNER JOIN media_collection_cross_ref ref ON c.id = ref.collectionId AND c.serverId = ref.serverId
        WHERE ref.mediaRatingKey = :mediaRatingKey AND ref.serverId = :serverId
    """)
    fun getCollectionsForMedia(mediaRatingKey: String, serverId: String): Flow<List<CollectionEntity>>
    
    // Get all items in a specific collection
    @Query("""
        SELECT m.* 
        FROM media m
        INNER JOIN media_collection_cross_ref ref ON m.ratingKey = ref.mediaRatingKey AND m.serverId = ref.serverId
        WHERE ref.collectionId = :collectionId AND ref.serverId = :serverId
        GROUP BY m.ratingKey, m.serverId
        ORDER BY m.year ASC
    """)
    fun getMediaInCollection(collectionId: String, serverId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM collections WHERE id = :collectionId AND serverId = :serverId LIMIT 1")
    fun getCollection(collectionId: String, serverId: String): Flow<CollectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<MediaCollectionCrossRef>)

    @Query("DELETE FROM media_collection_cross_ref WHERE collectionId = :collectionId AND serverId = :serverId")
    suspend fun clearCrossRefsForCollection(collectionId: String, serverId: String)
    
    @Transaction
    suspend fun upsertCollectionWithItems(collection: CollectionEntity, refs: List<MediaCollectionCrossRef>) {
        insertCollection(collection)
        clearCrossRefsForCollection(collection.id, collection.serverId)
        insertCrossRefs(refs)
    }

    @Query("DELETE FROM collections WHERE lastSync < :threshold")
    suspend fun deleteOldCollections(threshold: Long)
}
