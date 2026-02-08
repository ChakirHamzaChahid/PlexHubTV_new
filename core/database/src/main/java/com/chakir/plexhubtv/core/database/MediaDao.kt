package com.chakir.plexhubtv.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO principal encapsulant toute la logique d'accès aux médias.
 *
 * Contient :
 * - Requêtes basiques (Get, Insert, Delete).
 * - requêtes complexes d'agrégation (pour la vue Unifiée).
 * - PagingSources optimisées pour la performance UI.
 * - Logique de filtrage et de tri dynamique.
 */
@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE ratingKey = :ratingKey AND serverId = :serverId LIMIT 1")
    suspend fun getMedia(
        ratingKey: String,
        serverId: String,
    ): MediaEntity?

    @Query("SELECT * FROM media WHERE parentRatingKey = :parentRatingKey AND serverId = :serverId ORDER BY `index` ASC")
    suspend fun getChildren(
        parentRatingKey: String,
        serverId: String,
    ): List<MediaEntity>

    @Query("SELECT * FROM media WHERE ratingKey = :ratingKey AND serverId = :serverId LIMIT 1")
    fun getMediaFlow(
        ratingKey: String,
        serverId: String,
    ): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE guid = :guid AND serverId != :excludeServerId LIMIT 1")
    suspend fun getMediaByGuid(
        guid: String,
        excludeServerId: String,
    ): List<MediaEntity>

    // For Watchlist Sync: Get ALL instances of a media across all servers
    @Query("SELECT * FROM media WHERE guid = :guid")
    suspend fun getAllMediaByGuid(guid: String): List<MediaEntity>

    // Deduplicate for legacy calls
    @Query("SELECT * FROM media WHERE serverId = :serverId AND librarySectionId = :libraryId GROUP BY ratingKey")
    fun getLibraryItems(
        serverId: String,
        libraryId: String,
    ): Flow<List<MediaEntity>>

    // Paging Source (O(1) Ordered Query using Index)
    @Query(
        "SELECT * FROM media WHERE librarySectionId = :libraryId AND filter = :filter AND sortOrder = :sortOrder AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') AND (:query IS NULL OR title LIKE '%' || :query || '%') ORDER BY pageOffset ASC",
    )
    fun pagingSource(
        libraryId: String,
        filter: String,
        sortOrder: String,
        genre: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("DELETE FROM media WHERE librarySectionId = :libraryId AND filter = :filter AND sortOrder = :sortOrder")
    suspend fun clearByLibraryFilterSort(
        libraryId: String,
        filter: String,
        sortOrder: String,
    )

    @Query("SELECT * FROM media WHERE type = :type ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMediaByType(
        type: String,
        limit: Int,
        offset: Int,
    ): List<MediaEntity>

    // Aggregated Queries for Performance (Sorted by Date Added - Default)
    // NOTE: Paging Source queries (below) are preferred for UI.
    // Manual aggregated queries removed as they were unused and had incorrect GROUP BY logic.

    @Query("SELECT COUNT(*) FROM (SELECT DISTINCT title, year FROM media WHERE type = :type)")
    fun getMediaCountByType(type: String): Flow<Int>

    // Allow deleting items that are no longer present (optional cleanup)
    @Query("DELETE FROM media WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT * FROM media WHERE type = :type")
    fun getAllMediaByType(type: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: List<MediaEntity>)

    @Query("DELETE FROM media WHERE ratingKey = :ratingKey AND serverId = :serverId")
    suspend fun deleteMedia(
        ratingKey: String,
        serverId: String,
    )

    @Query("SELECT * FROM media WHERE type = :type AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchMedia(
        query: String,
        type: String,
    ): List<MediaEntity>

    @Query("DELETE FROM media WHERE serverId = :serverId AND librarySectionId = :libraryId")
    suspend fun deleteForLibrary(
        serverId: String,
        libraryId: String,
    )

    @Query("SELECT * FROM media WHERE lastViewedAt > 0 ORDER BY lastViewedAt DESC LIMIT :limit OFFSET :offset")
    fun getHistory(
        limit: Int,
        offset: Int,
    ): Flow<List<MediaEntity>>

    // Debug helper
    @Query("SELECT COUNT(*) FROM media WHERE librarySectionId = :libraryId")
    suspend fun countByLibrary(libraryId: String): Int

    @Query("SELECT COUNT(*) FROM media WHERE librarySectionId = :libraryId AND filter = :filter AND sortOrder = :sortOrder")
    suspend fun countByLibraryFilterSort(
        libraryId: String,
        filter: String,
        sortOrder: String,
    ): Int

    // Metadata for filters
    @Query(
        "SELECT COUNT(DISTINCT CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END) FROM media WHERE type = :type",
    )
    suspend fun getUniqueCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM media WHERE type = :type")
    suspend fun getRawCountByType(type: String): Int

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY addedAt DESC",
    )
    fun aggregatedPagingSourceByDateDesc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY addedAt ASC",
    )
    fun aggregatedPagingSourceByDateAsc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY title ASC",
    )
    fun aggregatedPagingSourceByTitleAsc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY title DESC",
    )
    fun aggregatedPagingSourceByTitleDesc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY year DESC, title ASC",
    )
    fun aggregatedPagingSourceByYearDesc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END ORDER BY year ASC, title ASC",
    )
    fun aggregatedPagingSourceByYearAsc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END " +
            "ORDER BY (COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) DESC, title ASC",
    )
    fun aggregatedPagingSourceByRatingDesc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    @Query(
        "SELECT *, MAX(addedAt) as addedAt, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as rating, " +
            "(COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) as audienceRating, " +
            "GROUP_CONCAT(ratingKey) as ratingKeys, GROUP_CONCAT(serverId) as serverIds FROM media " +
            "WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END " +
            "ORDER BY (COALESCE(SUM(rating), 0.0) + COALESCE(SUM(audienceRating), 0.0)) / NULLIF(COUNT(rating) + COUNT(audienceRating), 0) ASC, title ASC",
    )
    fun aggregatedPagingSourceByRatingAsc(
        type: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): androidx.paging.PagingSource<Int, MediaEntity>

    // Incremental Sync: Get latest updatedAt for a library
    @Query("SELECT MAX(updatedAt) FROM media WHERE serverId = :serverId AND librarySectionId = :libraryId")
    suspend fun getLastUpdatedAt(
        serverId: String,
        libraryId: String,
    ): Long?

    // Alphabet Scroll Helper: Unified View (Deduplicated)
    @Query(
        "SELECT COUNT(DISTINCT CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END) " +
            "FROM media WHERE type = :type " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:serverId IS NULL OR serverId = :serverId) " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "AND UPPER(title) < UPPER(:letter)",
    )
    suspend fun getUnifiedCountBeforeTitle(
        type: String,
        letter: String,
        genre: String? = null,
        serverId: String? = null,
        query: String? = null,
    ): Int

    // Alphabet Scroll Helper: Single Library View
    @Query(
        "SELECT COUNT(*) FROM media WHERE librarySectionId = :libraryId " +
            "AND filter = :filter AND sortOrder = :sortOrder " +
            "AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') " +
            "AND (:query IS NULL OR title LIKE '%' || :query || '%') " +
            "AND UPPER(title) < UPPER(:letter)",
    )
    suspend fun getLibraryCountBeforeTitle(
        libraryId: String,
        filter: String,
        sortOrder: String,
        letter: String,
        genre: String? = null,
        query: String? = null,
    ): Int

    // Dynamic Query for Paging
    @androidx.room.RawQuery(observedEntities = [MediaEntity::class])
    fun getMediaPagedRaw(query: androidx.sqlite.db.SupportSQLiteQuery): androidx.paging.PagingSource<Int, MediaEntity>

    // Dynamic Query for Index/Count
    @androidx.room.RawQuery
    suspend fun getMediaCountRaw(query: androidx.sqlite.db.SupportSQLiteQuery): Int

    // COLLECTION AGGREGATION: Get unificationId for a specific media
    @Query("SELECT unificationId FROM media WHERE ratingKey = :ratingKey AND serverId = :serverId LIMIT 1")
    suspend fun getUnificationId(
        ratingKey: String,
        serverId: String,
    ): String?

    // COLLECTION AGGREGATION: Get all server duplicates with the same unificationId
    @Query(
        """
        SELECT * FROM media 
        WHERE unificationId = :unificationId 
        AND unificationId != ''
        GROUP BY ratingKey, serverId
    """,
    )
    suspend fun getAllDuplicates(unificationId: String): List<MediaEntity>
}
