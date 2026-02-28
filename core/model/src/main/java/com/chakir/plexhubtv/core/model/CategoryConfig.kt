package com.chakir.plexhubtv.core.model

/**
 * Domain model representing category filtering configuration.
 * Used to control which VOD/Series categories are synced from Xtream servers.
 */
data class CategoryConfig(
    val filterMode: String = "whitelist",
    val categories: List<Category> = emptyList()
)

/**
 * Represents a single category with its filter status.
 */
data class Category(
    val categoryId: String,
    val categoryName: String,
    val categoryType: String, // "vod" or "series"
    val isAllowed: Boolean
)

/**
 * Represents a user's selection for a specific category.
 * Used for updating category filter status.
 */
data class CategorySelection(
    val categoryId: String,
    val categoryType: String, // "vod" or "series"
    val isAllowed: Boolean
)
