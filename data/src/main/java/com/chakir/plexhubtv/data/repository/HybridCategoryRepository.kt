package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.BackendServerDao
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
            backendRepository.getCategories(backendId, accountId)
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
            Timber.d("HybridCategory: updating categories via backend $backendId")
            backendRepository.updateCategories(backendId, accountId, filterMode, categories)
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
        settingsDataStore.saveSelectedXtreamCategoryIds(selectedIds)
    }
}
