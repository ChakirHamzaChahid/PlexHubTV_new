package com.chakir.plexhubtv.core.model

/**
 * Domain model representing category filtering configuration.
 * Used to control which VOD/Series categories are synced from Xtream servers.
 */
data class CategoryConfig(
    /**
     * Filter mode: "all", "whitelist", or "blacklist".
     * String type matches backend API for simpler DTO mapping.
     */
    val filterMode: String = "whitelist",
    val categories: List<Category> = emptyList()
)

/**
 * Represents a single category with its filter status.
 */
data class Category(
    val categoryId: String,
    val categoryName: String,
    /**
     * Category type: "vod" or "series".
     * String type matches backend API for simpler DTO mapping.
     */
    val categoryType: String,
    val isAllowed: Boolean
)

/**
 * Represents a user's selection for a specific category.
 * Used for updating category filter status.
 */
data class CategorySelection(
    val categoryId: String,
    /**
     * Category type: "vod" or "series".
     * String type matches backend API for simpler DTO mapping.
     */
    val categoryType: String,
    val isAllowed: Boolean
)
