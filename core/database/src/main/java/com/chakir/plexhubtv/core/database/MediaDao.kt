package com.chakir.plexhubtv.core.database

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

    // For Watchlist Sync: Get ALL instances of a media across all servers
    @Query("SELECT * FROM media WHERE guid = :guid")
    suspend fun getAllMediaByGuid(guid: String): List<MediaEntity>

    @Query("DELETE FROM media WHERE librarySectionId = :libraryId AND filter = :filter AND sortOrder = :sortOrder")
    suspend fun clearByLibraryFilterSort(
        libraryId: String,
        filter: String,
        sortOrder: String,
    )

    @Query("SELECT * FROM media WHERE librarySectionId = :libraryId AND filter = :filter AND sortOrder = :sortOrder ORDER BY pageOffset LIMIT :limit OFFSET :offset")
    suspend fun getPagedItems(
        libraryId: String,
        filter: String,
        sortOrder: String,
        limit: Int,
        offset: Int,
    ): List<MediaEntity>

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

    @Query("DELETE FROM media WHERE serverId = :serverId AND librarySectionId = :libraryKey")
    suspend fun deleteMediaByLibrary(serverId: String, libraryKey: String)

    @Query("SELECT * FROM media WHERE type = :type AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchMedia(
        query: String,
        type: String,
    ): List<MediaEntity>

    // ========================================
    // Rating Sync Queries (IMDb/TMDb)
    // ========================================

    /**
     * Get all unique TMDb IDs for series.
     * DISTINCT ensures only 1 API call per unique series across all servers.
     */
    @Query("SELECT DISTINCT tmdbId FROM media WHERE type = 'show' AND tmdbId IS NOT NULL AND tmdbId != ''")
    suspend fun getAllSeriesWithTmdbId(): List<String>

    /**
     * Get all unique IMDb IDs for series WITHOUT TMDb ID (fallback).
     */
    @Query(
        "SELECT DISTINCT imdbId FROM media " +
            "WHERE type = 'show' AND imdbId IS NOT NULL AND imdbId != '' " +
            "AND (tmdbId IS NULL OR tmdbId = '')",
    )
    suspend fun getAllSeriesWithImdbIdNoTmdbId(): List<String>

    /**
     * Get all unique IMDb IDs for movies.
     * DISTINCT ensures only 1 API call per unique movie across all servers.
     */
    @Query("SELECT DISTINCT imdbId FROM media WHERE type = 'movie' AND imdbId IS NOT NULL AND imdbId != ''")
    suspend fun getAllMoviesWithImdbId(): List<String>

    /**
     * Get all unique TMDb IDs for movies.
     * DISTINCT ensures only 1 API call per unique movie across all servers.
     * Used when RatingSync is configured to use TMDb for movies instead of OMDb.
     */
    @Query("SELECT DISTINCT tmdbId FROM media WHERE type = 'movie' AND tmdbId IS NOT NULL AND tmdbId != ''")
    suspend fun getAllMoviesWithTmdbId(): List<String>

    /**
     * Update rating by IMDb ID.
     * Updates ALL servers with the same IMDb ID in a single query.
     * @return Number of rows updated (useful for logging)
     */
    @Query("UPDATE media SET scrapedRating = :rating, displayRating = :rating WHERE imdbId = :imdbId AND (scrapedRating IS NULL OR scrapedRating != :rating)")
    suspend fun updateRatingByImdbId(
        imdbId: String,
        rating: Double,
    ): Int

    /**
     * Update rating by TMDb ID.
     * Updates ALL servers with the same TMDb ID in a single query.
     * @return Number of rows updated (useful for logging)
     */
    @Query("UPDATE media SET scrapedRating = :rating, displayRating = :rating WHERE tmdbId = :tmdbId AND (scrapedRating IS NULL OR scrapedRating != :rating)")
    suspend fun updateRatingByTmdbId(
        tmdbId: String,
        rating: Double,
    ): Int

    @Query(
        "SELECT *, MAX(lastViewedAt) as lastViewedAt FROM media WHERE lastViewedAt > 0 " +
        "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END " +
        "ORDER BY lastViewedAt DESC LIMIT :limit OFFSET :offset"
    )
    fun getHistory(
        limit: Int,
        offset: Int,
    ): Flow<List<MediaEntity>>

    // Metadata for filters
    @Query(
        "SELECT COUNT(DISTINCT CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END) FROM media WHERE type = :type",
    )
    suspend fun getUniqueCountByType(type: String): Int

    @Query("UPDATE media SET viewOffset = :viewOffset, lastViewedAt = :lastViewedAt WHERE ratingKey = :ratingKey AND serverId = :serverId")
    suspend fun updateProgress(
        ratingKey: String,
        serverId: String,
        viewOffset: Long,
        lastViewedAt: Long,
    )

    @Query("SELECT COUNT(*) FROM media WHERE type = :type")
    suspend fun getRawCountByType(type: String): Int

    // Dynamic Query for Paging
    @androidx.room.RawQuery(observedEntities = [MediaEntity::class])
    fun getMediaPagedRaw(query: androidx.sqlite.db.SupportSQLiteQuery): androidx.paging.PagingSource<Int, MediaEntity>

    // Dynamic Query for Index/Count
    @androidx.room.RawQuery
    suspend fun getMediaCountRaw(query: androidx.sqlite.db.SupportSQLiteQuery): Int

    // REMOTE SOURCES: Find same media on other servers via unificationId (for enrichment)
    @Query(
        """
        SELECT * FROM media
        WHERE unificationId = :unificationId
        AND unificationId != ''
        AND serverId != :excludeServerId
        GROUP BY serverId
    """,
    )
    suspend fun findRemoteSources(
        unificationId: String,
        excludeServerId: String,
    ): List<MediaEntity>

    // REMOTE SOURCES: Find same episode on other servers by show title + season index + episode index
    @Query(
        """
        SELECT * FROM media
        WHERE type = 'episode'
        AND grandparentTitle = :showTitle
        AND parentIndex = :seasonIndex
        AND `index` = :episodeIndex
        AND serverId != :excludeServerId
        GROUP BY serverId
    """,
    )
    suspend fun findRemoteEpisodeSources(
        showTitle: String,
        seasonIndex: Int,
        episodeIndex: Int,
        excludeServerId: String,
    ): List<MediaEntity>

    // Persistence helper to survive library syncs
    @Query("SELECT ratingKey, scrapedRating FROM media WHERE ratingKey IN (:ratingKeys) AND serverId = :serverId")
    suspend fun getScrapedRatings(
        ratingKeys: List<String>,
        serverId: String,
    ): Map<@androidx.room.MapColumn(columnName = "ratingKey") String, @androidx.room.MapColumn(columnName = "scrapedRating") Double?>
}
