package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Materialized aggregation table for the unified library view (Solution C).
 *
 * Pre-aggregated from `media` table via AggregationService.rebuildAll().
 * ~36K rows (movies + shows) vs ~69K in media — no GROUP BY at read time.
 *
 * Updated by:
 * - **Full rebuild**: UnifiedRebuildWorker after sync chain completes
 * - **Surgical updates**: RatingSyncWorker (ratings), PlaybackRepo (progress),
 *   XtreamVodRepo (enrichment groupKey mutation)
 *
 * Does NOT contain mediaParts (heavy JSON blob) — fetched from `media` on detail click.
 */
@Entity(
    tableName = "media_unified",
    indices = [
        Index(value = ["type", "titleSortable"]),
        Index(value = ["type", "displayRating"]),
        Index(value = ["type", "addedAt"]),
        Index(value = ["type", "year"]),
        Index(value = ["type", "genres"]),
        Index(value = ["type", "contentRating"]),
    ],
)
data class MediaUnifiedEntity(
    @PrimaryKey val groupKey: String,

    // Winner of the group (best metadataScore)
    val bestRatingKey: String,
    val bestServerId: String,

    // Display fields
    val type: String,
    val title: String,
    val titleSortable: String = "",
    val year: Int? = null,
    val summary: String? = null,
    val duration: Long? = null,
    val resolvedThumbUrl: String? = null,
    val resolvedArtUrl: String? = null,
    val resolvedBaseUrl: String? = null,

    // External IDs
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val guid: String? = null,

    // Pre-aggregated (replace GROUP_CONCAT at read-time)
    val serverIds: String? = null,              // "server1=rk1,server2=rk2"
    val alternativeThumbUrls: String? = null,   // "url1|url2|url3"
    val serverCount: Int = 1,

    // Sort / filter
    val genres: String? = null,
    val contentRating: String? = null,
    val displayRating: Double = 0.0,
    val avgDisplayRating: Double = 0.0,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,

    // Watch state (updated surgically)
    val viewOffset: Long = 0,
    val viewCount: Long = 0,
    val lastViewedAt: Long = 0,
    val historyGroupKey: String = "",

    // Metadata
    val metadataScore: Int = 0,
    val isOwned: Boolean = false,
    val unificationId: String = "",
    val scrapedRating: Double? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,

    // Navigation to detail (no mediaParts — fetched from media table on click)
    val librarySectionId: String? = null,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val index: Int? = null,

    // Thumbs for hierarchy display
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,

    // Source tracking
    val sourceServerId: String? = null,
)
