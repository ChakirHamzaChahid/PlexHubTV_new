package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.CategoryConfig
import com.chakir.plexhubtv.core.model.CategorySelection

/**
 * Repository interface for managing Xtream category filtering configuration.
 *
 * Provides operations to:
 * - Fetch current category filter config from backend
 * - Update category selections for an account
 * - Refresh categories from Xtream provider
 */
interface CategoryRepository {
    /**
     * Fetches the current category configuration for an account.
     *
     * @param accountId The Xtream account identifier
     * @return Result containing CategoryConfig with filter mode and selections
     */
    suspend fun getCategories(accountId: String): Result<CategoryConfig>

    /**
     * Updates the category filter configuration for an account.
     *
     * @param accountId The Xtream account identifier
     * @param filterMode "all", "whitelist", or "blacklist"
     * @param categories List of category selections with enabled/disabled state
     * @return Result indicating success or failure
     */
    suspend fun updateCategories(
        accountId: String,
        filterMode: String,
        categories: List<CategorySelection>
    ): Result<Unit>

    /**
     * Triggers a refresh of available categories from the Xtream provider.
     *
     * @param accountId The Xtream account identifier
     * @return Result indicating success or failure
     */
    suspend fun refreshCategories(accountId: String): Result<Unit>
}
