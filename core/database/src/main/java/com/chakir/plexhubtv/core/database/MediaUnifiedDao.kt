package com.chakir.plexhubtv.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * DAO for the materialized `media_unified` table (Solution C).
 *
 * Three categories of operations:
 * 1. **Full rebuild**: deleteAll + rebuildFromRawQuery (called by UnifiedRebuildWorker)
 * 2. **Paging**: getPagedUnified + getCountUnified (used by library screen)
 * 3. **Surgical updates**: rating, progress, orphan cleanup, single group rebuild
 */
@Dao
interface MediaUnifiedDao {

    // ═══════════════════════════════════════════
    // FULL REBUILD (called by UnifiedRebuildWorker)
    // ═══════════════════════════════════════════

    @Query("DELETE FROM media_unified")
    suspend fun deleteAll()

    @RawQuery
    suspend fun rebuildFromRawQuery(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // PAGING (used by library screen)
    // ═══════════════════════════════════════════

    @RawQuery(observedEntities = [MediaUnifiedEntity::class])
    fun getPagedUnified(query: SupportSQLiteQuery): PagingSource<Int, MediaUnifiedEntity>

    @RawQuery
    suspend fun getCountUnified(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // SURGICAL UPDATES
    // ═══════════════════════════════════════════

    /** After RatingSyncWorker updates a rating by IMDb ID. */
    @Query("""
        UPDATE media_unified
        SET displayRating = :rating, scrapedRating = :rating
        WHERE groupKey IN (
            SELECT DISTINCT groupKey FROM media WHERE imdbId = :imdbId
        )
    """)
    suspend fun updateRatingByImdbId(imdbId: String, rating: Double)

    /** After RatingSyncWorker updates a rating by TMDb ID. */
    @Query("""
        UPDATE media_unified
        SET displayRating = :rating, scrapedRating = :rating
        WHERE groupKey IN (
            SELECT DISTINCT groupKey FROM media WHERE tmdbId = :tmdbId
        )
    """)
    suspend fun updateRatingByTmdbId(tmdbId: String, rating: Double)

    /** After PlaybackRepo.flushLocalProgress(). Only updates if this is the best row. */
    @Query("""
        UPDATE media_unified
        SET viewOffset = :viewOffset, lastViewedAt = :lastViewedAt
        WHERE bestRatingKey = :ratingKey AND bestServerId = :serverId
    """)
    suspend fun updateProgress(ratingKey: String, serverId: String, viewOffset: Long, lastViewedAt: Long)

    /** After XtreamVodRepo.enrichMovieDetail() — deletes orphaned group if no media rows remain. */
    @Query("""
        DELETE FROM media_unified
        WHERE groupKey = :oldGroupKey
        AND NOT EXISTS (SELECT 1 FROM media WHERE groupKey = :oldGroupKey AND type IN ('movie', 'show'))
    """)
    suspend fun deleteOrphanedGroup(oldGroupKey: String)

    /** Rebuild a single group (after enrichment or merge). */
    @RawQuery
    suspend fun rebuildSingleGroup(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // UTILITY QUERIES
    // ═══════════════════════════════════════════

    @Query("SELECT COUNT(*) FROM media_unified WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT * FROM media_unified WHERE groupKey = :groupKey")
    suspend fun getByGroupKey(groupKey: String): MediaUnifiedEntity?
}
