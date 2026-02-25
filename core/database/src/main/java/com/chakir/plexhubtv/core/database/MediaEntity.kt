package com.chakir.plexhubtv.core.database

import androidx.room.Entity

/**
 * Cœur de la persistance locale : Représente un élément Média (Film, Épisode, Série).
 *
 * Cette table est massivement indexée pour garantir des performances d'interface fluides
 * (60fps) même avec des milliers d'éléments.
 *
 * Concepts Clés :
 * - **Paging Context** (`filter`, `sortOrder`, `pageOffset`) : Permet de stocker l'ordre exact
 *   des listes retournées par Plex, rendant la pagination locale O(1).
 * - **Unification** (`unificationId`) : Permet de regrouper visuellement le même film
 *   présent sur plusieurs serveurs.
 */
@Entity(
    tableName = "media",
    primaryKeys = ["ratingKey", "serverId", "filter", "sortOrder"],
    indices = [
        androidx.room.Index(value = ["serverId", "librarySectionId", "filter", "sortOrder", "pageOffset"], unique = true),
        // PERFORMANCE FIX: Add missing indexes for fallback queries and sorting
        androidx.room.Index(value = ["guid"]),
        androidx.room.Index(value = ["type", "addedAt"]),
        androidx.room.Index(value = ["imdbId"]),
        androidx.room.Index(value = ["tmdbId"]),
        // CRITICAL: Support for getLibraryItems queries
        androidx.room.Index(value = ["serverId", "librarySectionId"]),
        // PHASE 2 OPTIMIZATION: Index for strict aggregations
        androidx.room.Index(value = ["unificationId"]),
        // Rating sort performance: composite index for ORDER BY displayRating queries
        androidx.room.Index(value = ["type", "displayRating"]),
        // Incremental sync support
        androidx.room.Index(value = ["updatedAt"]),
        // Hierarchy support
        androidx.room.Index(value = ["parentRatingKey"]),
        // Locale-aware sorting support
        androidx.room.Index(value = ["titleSortable"]),
    ],
)
data class MediaEntity(
    val ratingKey: String,
    val serverId: String,
    val librarySectionId: String, // To store which library this belongs to (Crucial for filtering)
    val title: String,
    val titleSortable: String = "", // Normalized title for locale-aware alphabetical sorting
    // Paging Context
    val filter: String = "all",
    val sortOrder: String = "default",
    val pageOffset: Int = 0, // Position in the list for O(1) paging
    val type: String, // movie, show, episode, etc.
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val summary: String? = null,
    // Playback Progress
    val viewOffset: Long = 0,
    val lastViewedAt: Long = 0,
    // Hierarchy / Episode details
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null, // Season number (for episodes)
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val index: Int? = null, // episode/season index
    val mediaParts: List<com.chakir.plexhubtv.core.model.MediaPart> = emptyList(),
    // External IDs
    val guid: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val contentRating: String? = null,
    val genres: String? = null, // Comma separated
    // SQL Optimization: Pre-calculated ID to avoid GROUP BY CASE
    val unificationId: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0,
    // AGGREGATION FIELDS (Used only by GROUP_CONCAT queries in Unified View)
    val serverIds: String? = null,
    val ratingKeys: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    // Canonical display rating, pre-computed at write time: COALESCE(scrapedRating, audienceRating, rating, 0.0)
    // Updated by: LibrarySyncWorker (insert), RatingSyncWorker (scrapedRating update)
    // Used for: ORDER BY in both unified and non-unified views, UI display via MediaMapper
    val displayRating: Double = 0.0,
    // Pre-resolved full URLs for offline-first instant display
    val resolvedThumbUrl: String? = null,
    val resolvedArtUrl: String? = null,
    val resolvedBaseUrl: String? = null,
    // Alternative poster URLs from other servers (pipe-separated: "url1|url2|url3")
    // Used for fallback if primary resolvedThumbUrl fails/times out
    val alternativeThumbUrls: String? = null,
    // PERSISTENCE: Rating fetched from external sources (TMDb/OMDb) - Preserved during sync
    val scrapedRating: Double? = null,
)
