package com.chakir.plexhubtv.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery

/**
 * Builds parameterized SQL queries for the media library.
 * Extracted from LibraryRepositoryImpl to make SQL construction testable.
 *
 * Three query types share the same WHERE clause logic:
 * - Paged query (full SELECT + GROUP BY + ORDER BY) for PagingSource
 * - Count query (COUNT) for total item count
 * - Index query (COUNT + alphabet constraint) for alphabet jump navigation
 */
object MediaLibraryQueryBuilder {

    data class QueryConfig(
        val isUnified: Boolean,
        val mediaTypeStr: String,
        val libraryKey: String = "",
        val filter: String = "",
        val sortOrder: String = "",
        val genre: List<String>? = null,
        val selectedServerId: String? = null,
        val excludedServerIds: List<String> = emptyList(),
        val query: String? = null,
        val baseSort: String = "addedAt",
        val isDescending: Boolean = true,
        val maxAgeRating: Int? = null,
    )

    data class BuiltQuery(val sql: String, val args: List<Any>) {
        fun toSimpleSQLiteQuery() = SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun buildPagedQuery(config: QueryConfig): BuiltQuery {
        val sql = StringBuilder()
        val args = mutableListOf<Any>()

        if (config.isUnified) {
            sql.append(UNIFIED_SELECT)
            sql.append(UNIFIED_FROM)
            sql.append("WHERE media.type = ? ")
            args.add(config.mediaTypeStr)
        } else {
            sql.append(NON_UNIFIED_SELECT)
            sql.append("FROM media ")
            sql.append("WHERE type = ? AND librarySectionId = ? AND filter = ? AND sortOrder = ? ")
            args.add(config.mediaTypeStr)
            args.add(config.libraryKey)
            args.add(config.filter)
            args.add(config.sortOrder)
        }

        appendSharedWhereFilters(sql, args, config)

        if (config.isUnified) {
            sql.append(UNIFIED_GROUP_BY)
        } else {
            sql.append(NON_UNIFIED_GROUP_BY)
        }

        val safeDirection = if (config.isDescending) "DESC" else "ASC"
        val orderBy = if (config.isUnified) {
            when (config.baseSort) {
                "title" -> "title $safeDirection"
                "year" -> "year $safeDirection, title ASC"
                "rating" -> "AVG(media.displayRating) $safeDirection, title ASC"
                "addedAt" -> "MAX(media.addedAt) $safeDirection, title ASC"
                else -> "MAX(media.addedAt) $safeDirection, title ASC"
            }
        } else {
            // Non-unified: Use MIN(pageOffset) to respect Plex API order while supporting GROUP BY
            "MIN(media.pageOffset) ASC"
        }
        sql.append("ORDER BY $orderBy")

        return BuiltQuery(sql.toString(), args)
    }

    fun buildCountQuery(config: QueryConfig): BuiltQuery {
        val (sql, args) = buildCountSql(config)
        return BuiltQuery(sql.toString(), args)
    }

    fun buildIndexQuery(config: QueryConfig, letter: String): BuiltQuery {
        val (sql, args) = buildCountSql(config)
        sql.append("AND UPPER(title) < UPPER(?) ")
        args.add(letter)
        return BuiltQuery(sql.toString(), args)
    }

    private fun buildCountSql(config: QueryConfig): Pair<StringBuilder, MutableList<Any>> {
        val sql = StringBuilder()
        val args = mutableListOf<Any>()

        if (config.isUnified) {
            sql.append(UNIFIED_COUNT_SELECT)
            sql.append(UNIFIED_COUNT_FROM)
            sql.append("WHERE media.type = ? ")
            args.add(config.mediaTypeStr)
        } else {
            sql.append("SELECT COUNT(*) FROM media ")
            sql.append("WHERE type = ? AND librarySectionId = ? AND filter = ? AND sortOrder = ? ")
            args.add(config.mediaTypeStr)
            args.add(config.libraryKey)
            args.add(config.filter)
            args.add(config.sortOrder)
        }

        appendSharedWhereFilters(sql, args, config)

        return sql to args
    }

    private fun appendSharedWhereFilters(
        sql: StringBuilder,
        args: MutableList<Any>,
        config: QueryConfig,
    ) {
        if (!config.genre.isNullOrEmpty()) {
            sql.append("AND (")
            config.genre.forEachIndexed { index, keyword ->
                if (index > 0) sql.append(" OR ")
                sql.append("genres LIKE ?")
                args.add("%$keyword%")
            }
            sql.append(") ")
        }

        if (config.isUnified && config.excludedServerIds.isNotEmpty()) {
            val placeholders = config.excludedServerIds.joinToString(",") { "?" }
            sql.append("AND serverId NOT IN ($placeholders) ")
            args.addAll(config.excludedServerIds)
        }

        if (config.isUnified && config.selectedServerId != null) {
            sql.append("AND serverId = ? ")
            args.add(config.selectedServerId)
        }

        if (config.query != null) {
            // Use FTS4 index for fast prefix/token search instead of LIKE '%query%' (full table scan)
            sql.append("AND media.rowid IN (SELECT rowid FROM media_fts WHERE media_fts MATCH ?) ")
            args.add("${config.query}*")
        }

        // Age-based content filtering (parental controls) — pushed to SQL to avoid
        // iterating pagingData.filter{} on every loaded page in the ViewModel.
        // Content ratings are normalized by ContentRatingHelper to: "TP", "NR", "XXX", or numeric ("7", "13", "16+", etc.)
        if (config.maxAgeRating != null && config.maxAgeRating < 99) {
            sql.append(
                "AND (contentRating IS NULL OR contentRating = '' " +
                "OR contentRating IN ('TP','NR') " +
                "OR (contentRating NOT IN ('TP','NR','XXX') " +
                    "AND CAST(REPLACE(contentRating, '+', '') AS INTEGER) <= ?) " +
                ") "
            )
            args.add(config.maxAgeRating)
        }
    }

    // ── Correlated MAX row selection ──
    // Ensures all non-aggregated columns (ratingKey, serverId, thumbUrl, etc.)
    // come from the SAME winning row within each GROUP BY group.
    // Without this, GROUP BY picks arbitrary values for each column independently,
    // causing mismatches (e.g., ratingKey from server A + thumbUrl from server B
    // → resolving thumbUrl against server B loads a completely different movie's poster).

    // Sort key for unified: metadata quality score + deterministic tiebreaker.
    // Uses (score + 1000) instead of PRINTF('%020d') — same lexicographic order for 0-161 range,
    // but eliminates ~43K PRINTF calls per query on ARM (critical for Mi Box S).
    private const val UNIFIED_SORT_KEY =
        "(media.metadataScore + 1000) || '|' || media.ratingKey || '|' || media.serverId"

    private const val BEST_PICK =
        "MAX((media.metadataScore + 1000) || '|' || media.ratingKey || '|' || media.serverId)"

    // Skip 4-digit score + 1 pipe = offset 6 (was 22 with PRINTF '%020d')
    private val BEST_PICK_TAIL = "SUBSTR($BEST_PICK, 6)"

    // Extracts a field from the same winning row as BEST_PICK.
    // Uses CHAR(31) (unit separator) as delimiter — never appears in URLs or text.
    // Returns NULL for empty values (safe for nullable columns).
    private fun bestRowField(field: String, alias: String): String {
        val expr = "MAX($UNIFIED_SORT_KEY || CHAR(31) || COALESCE($field, ''))"
        return "NULLIF(SUBSTR($expr, INSTR($expr, CHAR(31)) + 1), '') as $alias"
    }

    // Variant for non-nullable String columns (unificationId, historyGroupKey).
    // Returns '' instead of NULL to prevent Room NPE on non-null fields.
    private fun bestRowFieldNonNull(field: String, alias: String): String {
        val expr = "MAX($UNIFIED_SORT_KEY || CHAR(31) || COALESCE($field, ''))"
        return "SUBSTR($expr, INSTR($expr, CHAR(31)) + 1) as $alias"
    }

    // ── Unified SELECT (metadataScore is a pre-computed column) ──
    // Only ratingKey/serverId and image URLs use correlated MAX (bestRowField) to ensure
    // they come from the same winning row. All other fields use plain column references
    // which is safe because unified groups contain the same logical movie (same external IDs).
    private val UNIFIED_SELECT =
        """SELECT
                        SUBSTR($BEST_PICK, 6, INSTR($BEST_PICK_TAIL, '|') - 1) as ratingKey,
                        SUBSTR($BEST_PICK_TAIL, INSTR($BEST_PICK_TAIL, '|') + 1) as serverId,
                        media.librarySectionId, media.title,
                        media.titleSortable, media.filter, media.sortOrder,
                        MIN(media.pageOffset) as pageOffset,
                        media.type, media.year, media.duration,
                        media.summary, media.viewOffset, media.lastViewedAt, media.parentTitle,
                        media.parentRatingKey, media.parentIndex, media.grandparentTitle,
                        media.grandparentRatingKey, media.`index`, media.mediaParts, media.guid,
                        media.imdbId, media.tmdbId, media.rating, media.audienceRating,
                        media.contentRating, media.genres, media.unificationId,
                        MAX(media.addedAt) as addedAt, MAX(media.updatedAt) as updatedAt,
                        media.parentThumb, media.grandparentThumb,
                        media.displayRating,
                        media.scrapedRating,
                        media.historyGroupKey,
                        media.viewCount,
                        media.sourceServerId,
                        media.metadataScore,
                        media.isOwned,
                        ${bestRowField("media.thumbUrl", "thumbUrl")},
                        ${bestRowField("media.artUrl", "artUrl")},
                        ${bestRowField("media.resolvedThumbUrl", "resolvedThumbUrl")},
                        ${bestRowField("media.resolvedArtUrl", "resolvedArtUrl")},
                        ${bestRowField("media.resolvedBaseUrl", "resolvedBaseUrl")},
                        MAX(media.metadataScore) as _bestScore,
                        NULL as ratingKeys,
                        GROUP_CONCAT(DISTINCT media.serverId || '=' || media.ratingKey) as serverIds,
                        GROUP_CONCAT(DISTINCT CASE WHEN media.resolvedThumbUrl IS NOT NULL AND media.resolvedThumbUrl != '' THEN media.resolvedThumbUrl ELSE NULL END) as alternativeThumbUrls """

    // ── Non-unified SELECT (direct from media table, no id_bridge JOIN) ──
    // Uses plain column references. Groups are typically single-row per media item
    // since the WHERE clause filters by server-specific librarySectionId.
    private const val NON_UNIFIED_SELECT =
        """SELECT media.ratingKey, media.serverId,
                        media.librarySectionId, media.title,
                        media.titleSortable, media.filter, media.sortOrder,
                        MIN(media.pageOffset) as pageOffset,
                        media.type, media.thumbUrl, media.artUrl, media.year, media.duration,
                        media.summary, media.viewOffset, media.lastViewedAt, media.parentTitle,
                        media.parentRatingKey, media.parentIndex, media.grandparentTitle,
                        media.grandparentRatingKey, media.`index`, media.mediaParts, media.guid,
                        media.imdbId, media.tmdbId, media.rating, media.audienceRating,
                        media.contentRating, media.genres, media.unificationId,
                        MAX(media.addedAt) as addedAt, MAX(media.updatedAt) as updatedAt,
                        media.parentThumb, media.grandparentThumb,
                        media.displayRating,
                        media.resolvedThumbUrl, media.resolvedArtUrl, media.resolvedBaseUrl,
                        media.scrapedRating,
                        media.historyGroupKey,
                        media.viewCount,
                        media.sourceServerId,
                        media.metadataScore,
                        media.isOwned,
                        NULL as _bestScore,
                        NULL as ratingKeys,
                        GROUP_CONCAT(DISTINCT media.serverId || '=' || media.ratingKey) as serverIds,
                        GROUP_CONCAT(DISTINCT CASE WHEN media.resolvedThumbUrl IS NOT NULL AND media.resolvedThumbUrl != '' THEN media.resolvedThumbUrl ELSE NULL END) as alternativeThumbUrls """

    // metadataScore is now a pre-computed column (MIGRATION_36_37), eliminating the subquery
    private const val UNIFIED_FROM =
        "FROM media LEFT JOIN id_bridge ON media.tmdbId = id_bridge.tmdbId AND media.imdbId IS NULL "

    // Unified GROUP BY: uses id_bridge.imdbId from the LEFT JOIN for TMDb→IMDb bridging.
    // No title+year fallback — only external IDs (imdb/tmdb) trigger unification.
    // Movies without any external ID stay as separate entries (ratingKey||serverId).
    private const val UNIFIED_GROUP_BY =
        """GROUP BY media.type, COALESCE(
                        media.imdbId,
                        id_bridge.imdbId,
                        CASE WHEN media.tmdbId IS NOT NULL AND media.tmdbId != '' THEN 'tmdb_' || media.tmdbId ELSE NULL END,
                        media.ratingKey || media.serverId
                    ) """

    // Non-unified GROUP BY: no id_bridge JOIN available, so no bridgedImdbId.
    // Same rule: no title+year fallback, only external IDs unify.
    private const val NON_UNIFIED_GROUP_BY =
        """GROUP BY media.type, COALESCE(
                        media.imdbId,
                        CASE WHEN media.tmdbId IS NOT NULL AND media.tmdbId != '' THEN 'tmdb_' || media.tmdbId ELSE NULL END,
                        media.ratingKey || media.serverId
                    ) """

    private const val UNIFIED_COUNT_SELECT =
        """SELECT COUNT(DISTINCT COALESCE(
                    media.imdbId,
                    id_bridge.imdbId,
                    CASE WHEN media.tmdbId IS NOT NULL AND media.tmdbId != '' THEN 'tmdb_' || media.tmdbId ELSE NULL END,
                    media.ratingKey || media.serverId
                )) """

    private const val UNIFIED_COUNT_FROM =
        "FROM media LEFT JOIN id_bridge ON media.tmdbId = id_bridge.tmdbId AND media.imdbId IS NULL "
}
