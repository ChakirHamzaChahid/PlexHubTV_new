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
        // Incremental sync support
        androidx.room.Index(value = ["updatedAt"]),
        // Hierarchy support
        androidx.room.Index(value = ["parentRatingKey"]),
    ],
)
data class MediaEntity(
    val ratingKey: String,
    val serverId: String,
    val librarySectionId: String, // To store which library this belongs to (Crucial for filtering)
    val title: String,
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
    // Pre-resolved full URLs for offline-first instant display
    val resolvedThumbUrl: String? = null,
    val resolvedArtUrl: String? = null,
    val resolvedBaseUrl: String? = null,
)
