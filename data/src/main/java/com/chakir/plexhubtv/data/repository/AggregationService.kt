package com.chakir.plexhubtv.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.chakir.plexhubtv.core.database.MediaUnifiedDao
import com.chakir.plexhubtv.core.database.PlexDatabase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and executes INSERT OR REPLACE queries to populate the `media_unified` table
 * from the `media` table. Two modes:
 *
 * 1. **Full rebuild** — called once after sync chain completes (UnifiedRebuildWorker).
 *    Truncates + re-inserts all groups.
 *
 * 2. **Single group rebuild** — called after enrichment mutates a groupKey.
 *    Handles orphan cleanup + re-insertion of the merged/new group.
 */
@Singleton
class AggregationService @Inject constructor(
    private val database: PlexDatabase,
    private val mediaUnifiedDao: MediaUnifiedDao,
) {
    /**
     * Full rebuild of media_unified from media table.
     * Executed ONCE after all sync workers finish.
     * ~2-5 sec on 69K media rows → ~36K unified rows.
     */
    suspend fun rebuildAll() {
        val startTime = System.currentTimeMillis()
        database.withTransaction {
            mediaUnifiedDao.deleteAll()
            mediaUnifiedDao.rebuildFromRawQuery(SimpleSQLiteQuery(buildRebuildSql()))
        }
        val duration = System.currentTimeMillis() - startTime
        Timber.i("AGGREGATION: Full rebuild completed in ${duration}ms")
    }

    /**
     * Rebuild a single group (after enrichment changes a groupKey).
     */
    suspend fun rebuildGroup(groupKey: String) {
        mediaUnifiedDao.rebuildSingleGroup(
            SimpleSQLiteQuery(buildRebuildSql(groupKey = groupKey), arrayOf(groupKey))
        )
    }

    /**
     * Handles groupKey mutation (enrichMovieDetail adds tmdbId → groupKey changes).
     * 1. Deletes the old group if no media rows reference it anymore
     * 2. Rebuilds the new group (potential merge with existing Plex entry)
     */
    suspend fun handleGroupKeyMutation(oldGroupKey: String, newGroupKey: String) {
        database.withTransaction {
            mediaUnifiedDao.deleteOrphanedGroup(oldGroupKey)
            rebuildGroup(newGroupKey)
        }
    }

    // ── Correlated MAX row selection ──
    // Same pattern as MediaLibraryQueryBuilder: ensures all non-aggregated columns
    // come from the SAME winning row within each GROUP BY group.
    // Uses (score + 1000) for lexicographic sort of 0-161 range.

    private val SORT_KEY =
        "(media.metadataScore + 1000) || '|' || media.ratingKey || '|' || media.serverId"

    private val BEST_PICK =
        "MAX((media.metadataScore + 1000) || '|' || media.ratingKey || '|' || media.serverId)"

    private val BEST_PICK_TAIL = "SUBSTR($BEST_PICK, 6)"

    /**
     * Extracts a field from the winning row (highest metadataScore).
     * Uses CHAR(31) (unit separator) as delimiter — never appears in URLs or text.
     * Returns NULL for empty values (safe for nullable columns).
     */
    private fun bestField(field: String): String {
        val expr = "MAX($SORT_KEY || CHAR(31) || COALESCE(CAST($field AS TEXT), ''))"
        return "NULLIF(SUBSTR($expr, INSTR($expr, CHAR(31)) + 1), '')"
    }

    /**
     * Same as bestField but returns '' instead of NULL (for non-null String fields).
     */
    private fun bestFieldNonNull(field: String): String {
        val expr = "MAX($SORT_KEY || CHAR(31) || COALESCE(CAST($field AS TEXT), ''))"
        return "SUBSTR($expr, INSTR($expr, CHAR(31)) + 1)"
    }

    /**
     * Builds the INSERT OR REPLACE SQL for media_unified.
     * @param groupKey if provided, adds WHERE media.groupKey = ? for single-group rebuild.
     */
    private fun buildRebuildSql(groupKey: String? = null): String {
        val whereClause = if (groupKey != null) {
            "AND media.groupKey = ?"
        } else {
            ""
        }

        return """
            INSERT OR REPLACE INTO media_unified (
                groupKey, bestRatingKey, bestServerId,
                type, title, titleSortable, year, summary, duration,
                resolvedThumbUrl, resolvedArtUrl, resolvedBaseUrl,
                imdbId, tmdbId, guid,
                serverIds, alternativeThumbUrls, serverCount,
                genres, contentRating, displayRating, avgDisplayRating,
                addedAt, updatedAt,
                viewOffset, viewCount, lastViewedAt, historyGroupKey,
                metadataScore, isOwned, unificationId, scrapedRating,
                rating, audienceRating,
                librarySectionId, parentTitle, parentRatingKey, parentIndex,
                grandparentTitle, grandparentRatingKey, `index`,
                thumbUrl, artUrl, parentThumb, grandparentThumb, sourceServerId
            )
            SELECT
                media.groupKey,

                -- Winner: ratingKey from best metadataScore row (correlated extraction)
                SUBSTR($BEST_PICK, 6, INSTR($BEST_PICK_TAIL, '|') - 1) as bestRatingKey,
                SUBSTR($BEST_PICK_TAIL, INSTR($BEST_PICK_TAIL, '|') + 1) as bestServerId,

                -- Display fields from winning row
                media.type,
                ${bestFieldNonNull("media.title")},
                ${bestFieldNonNull("media.titleSortable")},
                ${bestField("media.year")},
                ${bestField("media.summary")},
                ${bestField("media.duration")},
                ${bestField("media.resolvedThumbUrl")},
                ${bestField("media.resolvedArtUrl")},
                ${bestField("media.resolvedBaseUrl")},

                -- External IDs from winning row
                ${bestField("media.imdbId")},
                ${bestField("media.tmdbId")},
                ${bestField("media.guid")},

                -- Pre-aggregated multi-source info
                GROUP_CONCAT(DISTINCT media.serverId || '=' || media.ratingKey) as serverIds,
                GROUP_CONCAT(DISTINCT CASE
                    WHEN media.resolvedThumbUrl IS NOT NULL AND media.resolvedThumbUrl != ''
                    THEN media.resolvedThumbUrl ELSE NULL END
                ) as alternativeThumbUrls,
                COUNT(DISTINCT media.serverId) as serverCount,

                -- Sort/filter from winning row
                ${bestField("media.genres")},
                ${bestField("media.contentRating")},
                ${bestField("media.displayRating")},
                AVG(media.displayRating) as avgDisplayRating,
                MAX(media.addedAt) as addedAt,
                MAX(media.updatedAt) as updatedAt,

                -- Watch state from winning row
                ${bestField("media.viewOffset")},
                ${bestField("media.viewCount")},
                MAX(media.lastViewedAt) as lastViewedAt,
                ${bestFieldNonNull("media.historyGroupKey")},

                -- Metadata
                MAX(media.metadataScore) as metadataScore,
                MAX(media.isOwned) as isOwned,
                ${bestFieldNonNull("media.unificationId")},
                ${bestField("media.scrapedRating")},
                ${bestField("media.rating")},
                ${bestField("media.audienceRating")},

                -- Navigation
                ${bestField("media.librarySectionId")},
                ${bestField("media.parentTitle")},
                ${bestField("media.parentRatingKey")},
                ${bestField("media.parentIndex")},
                ${bestField("media.grandparentTitle")},
                ${bestField("media.grandparentRatingKey")},
                ${bestField("media.`index`")},

                -- Thumbs
                ${bestField("media.thumbUrl")},
                ${bestField("media.artUrl")},
                ${bestField("media.parentThumb")},
                ${bestField("media.grandparentThumb")},
                ${bestField("media.sourceServerId")}

            FROM media
            WHERE media.type IN ('movie', 'show')
            AND media.groupKey != ''
            AND media.isHidden = 0
            $whereClause
            GROUP BY media.type, media.groupKey
        """.trimIndent()
    }
}
