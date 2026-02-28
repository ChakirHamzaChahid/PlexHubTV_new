package com.chakir.plexhubtv.core.network.backend

import com.google.gson.annotations.SerializedName

/**
 * DTOs for the PlexHub Backend REST API.
 * All field names match the camelCase JSON responses from the backend.
 */

data class BackendMediaListResponse(
    val items: List<BackendMediaItemDto>,
    val total: Int,
    val hasMore: Boolean,
)

data class BackendMediaItemDto(
    val ratingKey: String,
    val serverId: String,
    val librarySectionId: String,
    val title: String,
    val titleSortable: String = "",
    val filter: String = "all",
    val sortOrder: String = "default",
    val pageOffset: Int = 0,
    val type: String,
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val summary: String? = null,
    val genres: String? = null,
    val contentRating: String? = null,
    val viewOffset: Long = 0,
    val viewCount: Long = 0,
    val lastViewedAt: Long = 0,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val index: Int? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    val mediaParts: String = "[]",
    val guid: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val unificationId: String = "",
    val historyGroupKey: String = "",
    val serverIds: String? = null,
    val ratingKeys: String? = null,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val displayRating: Double = 0.0,
    val scrapedRating: Double? = null,
    val resolvedThumbUrl: String? = null,
    val resolvedArtUrl: String? = null,
    val resolvedBaseUrl: String? = null,
    val alternativeThumbUrls: String? = null,
    val isBroken: Boolean = false,
    val tmdbMatchConfidence: Double? = null,
)

data class BackendStreamResponse(
    val url: String,
    val expiresAt: Long? = null,
)

data class BackendHealthResponse(
    val status: String,
    val version: String,
    val accounts: Int,
    val totalMedia: Int,
    val enrichedMedia: Int,
    val brokenStreams: Int,
    val lastSyncAt: Long? = null,
)

data class BackendAccountResponse(
    val id: String,
    val label: String,
    val baseUrl: String,
    val port: Int,
    val username: String,
    val status: String,
    val expirationDate: Long? = null,
    val maxConnections: Int = 1,
    val allowedFormats: String = "",
    val serverUrl: String? = null,
    val httpsPort: Int? = null,
    val lastSyncedAt: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
)

data class BackendSyncRequest(
    val accountId: String? = null,
    val force: Boolean = false,
)

data class BackendSyncJobResponse(
    val jobId: String,
)

data class BackendSyncStatusResponse(
    val status: String,
    val progress: Map<String, Any>? = null,
)

// --- Account Management ---

data class BackendAccountCreate(
    val label: String,
    val baseUrl: String,
    val port: Int = 80,
    val username: String,
    val password: String,
)

data class BackendAccountUpdate(
    val label: String? = null,
    val baseUrl: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean? = null,
)

data class BackendAccountTestResponse(
    val status: String,
    val expirationDate: Long? = null,
    val maxConnections: Int = 1,
    val allowedFormats: String = "",
)

// --- Categories ---

data class BackendCategoryItem(
    val categoryId: String,
    val categoryName: String,
    val categoryType: String,
    val isAllowed: Boolean,
    val lastFetchedAt: Long,
)

data class BackendCategoryListResponse(
    val items: List<BackendCategoryItem>,
    val filterMode: String,
)

data class BackendCategoryUpdate(
    val categoryId: String,
    val categoryType: String,
    val isAllowed: Boolean,
)

data class BackendCategoryUpdateRequest(
    val filterMode: String,
    val categories: List<BackendCategoryUpdate>,
)

data class BackendCategoryRefreshResponse(
    val message: String,
    val vodCount: Int? = null,
    val seriesCount: Int? = null,
    val total: Int? = null,
)
