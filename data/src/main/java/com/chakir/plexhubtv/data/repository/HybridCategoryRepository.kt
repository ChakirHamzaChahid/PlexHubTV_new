package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.BackendServerDao
import com.chakir.plexhubtv.core.database.ProfileDao
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.Category
import com.chakir.plexhubtv.core.model.CategoryConfig
import com.chakir.plexhubtv.core.model.CategorySelection
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.CategoryRepository
import com.chakir.plexhubtv.domain.usecase.GetXtreamCategoriesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridCategoryRepository @Inject constructor(
    private val backendServerDao: BackendServerDao,
    private val backendRepository: BackendRepository,
    private val getCategoriesUseCase: GetXtreamCategoriesUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val profileDao: ProfileDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CategoryRepository {

    private suspend fun getActiveBackendId(): String? =
        withContext(ioDispatcher) {
            backendServerDao.observeAll().first().firstOrNull { it.isActive }?.id
        }

    override suspend fun getCategories(accountId: String): Result<CategoryConfig> {
        val backendId = getActiveBackendId()

        return if (backendId != null) {
            Timber.d("HybridCategory: using backend $backendId for categories")
            val result = backendRepository.getCategories(backendId, accountId)
            if (result.isSuccess) {
                // Overlay profile-specific local filter (does NOT touch backend whitelist)
                val config = result.getOrThrow()
                val filtered = applyLocalFilter(accountId, config)
                Result.success(filtered)
            } else {
                result
            }
        } else {
            Timber.d("HybridCategory: using direct Xtream for categories")
            getXtreamCategories(accountId)
        }
    }

    override suspend fun updateCategories(
        accountId: String,
        filterMode: String,
        categories: List<CategorySelection>,
    ): Result<Unit> {
        val backendId = getActiveBackendId()

        return if (backendId != null) {
            // For backend accounts: save locally (preserves backend whitelist)
            Timber.d("HybridCategory: saving local category filter for backend account $accountId")
            saveLocalFilter(accountId, categories)
        } else {
            Timber.d("HybridCategory: saving categories locally")
            saveXtreamCategoriesLocally(accountId, categories)
        }
    }

    override suspend fun refreshCategories(accountId: String): Result<Unit> {
        val backendId = getActiveBackendId()

        return if (backendId != null) {
            backendRepository.refreshCategories(backendId, accountId)
        } else {
            // Direct Xtream: categories are always fresh from the API call
            Result.success(Unit)
        }
    }

    private suspend fun getXtreamCategories(accountId: String): Result<CategoryConfig> =
        runCatching {
            val vodResult = getCategoriesUseCase.getVodCategories(accountId)
            val seriesResult = getCategoriesUseCase.getSeriesCategories(accountId)

            val previouslySelected = settingsDataStore.selectedXtreamCategoryIds.first()

            val vodCategories = vodResult.getOrDefault(emptyList()).map { cat ->
                val compositeId = "$accountId:vod:${cat.categoryId}"
                Category(
                    categoryId = cat.categoryId.toString(),
                    categoryName = cat.categoryName,
                    categoryType = "vod",
                    isAllowed = if (previouslySelected.isEmpty()) true
                    else previouslySelected.contains(compositeId),
                )
            }

            val seriesCategories = seriesResult.getOrDefault(emptyList()).map { cat ->
                val compositeId = "$accountId:series:${cat.categoryId}"
                Category(
                    categoryId = cat.categoryId.toString(),
                    categoryName = cat.categoryName,
                    categoryType = "series",
                    isAllowed = if (previouslySelected.isEmpty()) true
                    else previouslySelected.contains(compositeId),
                )
            }

            CategoryConfig(
                filterMode = "whitelist",
                categories = vodCategories + seriesCategories,
            )
        }

    private suspend fun saveXtreamCategoriesLocally(
        accountId: String,
        categories: List<CategorySelection>,
    ): Result<Unit> = runCatching {
        val selectedIds = categories
            .filter { it.isAllowed }
            .map { "$accountId:${it.categoryType}:${it.categoryId}" }
            .toSet()
        settingsDataStore.mergeSelectedXtreamCategoryIds(accountId, selectedIds)
    }

    /** Returns active profile ID, or a stable default when no profile system is in use. */
    private suspend fun getActiveProfileId(): String =
        profileDao.getActiveProfile()?.id ?: DEFAULT_PROFILE_ID

    /**
     * Overlay local category filter on backend categories.
     * If no local filter exists for this profile+account, returns backend config as-is.
     * Works with or without profiles — uses a default ID when no profile is active.
     */
    private suspend fun applyLocalFilter(accountId: String, config: CategoryConfig): CategoryConfig {
        val profileId = getActiveProfileId()
        val filter = settingsDataStore.getProfileCategoryFilter(profileId, accountId)
            ?: return config  // No local filter → show backend's isAllowed as-is

        val prefix = "$profileId:$accountId:"

        // Determine which category types are present in the saved filter.
        // Only override isAllowed for those types; leave others (e.g. "live") unchanged.
        val filteredTypes = filter.mapTo(mutableSetOf()) { entry ->
            entry.removePrefix(prefix).substringBefore(":")
        }

        Timber.d("HybridCategory: applying local filter for profileId=$profileId, account=$accountId (${filter.size} allowed, types=$filteredTypes)")
        return config.copy(
            categories = config.categories.map { cat ->
                if (cat.categoryType in filteredTypes) {
                    val compositeId = "$prefix${cat.categoryType}:${cat.categoryId}"
                    cat.copy(isAllowed = compositeId in filter)
                } else {
                    cat // Keep backend's isAllowed for types not in the local filter
                }
            }
        )
    }

    /**
     * Save category selection locally for the current profile (or default).
     * Does NOT update the backend whitelist or trigger sync.
     */
    private suspend fun saveLocalFilter(
        accountId: String,
        categories: List<CategorySelection>,
    ): Result<Unit> = runCatching {
        val profileId = getActiveProfileId()

        val prefix = "$profileId:$accountId:"
        val allowedIds = categories
            .filter { it.isAllowed }
            .map { "$prefix${it.categoryType}:${it.categoryId}" }
            .toSet()

        settingsDataStore.saveProfileCategoryFilter(profileId, accountId, allowedIds)
        Timber.i("HybridCategory: saved local filter for profileId=$profileId, account=$accountId (${allowedIds.size} allowed)")
    }

    companion object {
        /** Fallback profile ID used when no profile system is active. */
        private const val DEFAULT_PROFILE_ID = "_default"
    }
}
