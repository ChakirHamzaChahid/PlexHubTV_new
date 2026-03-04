# Backend Xtream Categories & Account Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Adapt the Android app to the evolved PlexHub Backend API — add category management via backend, hybrid category/account repositories, and polish the category selection screen.

**Architecture:** Unified Abstraction Layer — `HybridCategoryRepository` and `HybridAccountManagementRepository` check for an active backend server and delegate accordingly (backend API or direct Xtream). The ViewModel stays source-agnostic.

**Tech Stack:** Kotlin, Hilt, Retrofit, Room, Jetpack Compose, Coroutines/Flow, MockK, Turbine

**Design doc:** `docs/plans/2026-02-28-backend-xtream-categories-design.md`

---

## Task 1: Add Backend Category & Account DTOs

**Files:**
- Modify: `core/network/src/main/java/com/chakir/plexhubtv/core/network/backend/BackendDto.kt`

**Step 1: Add DTOs to BackendDto.kt**

Append the following data classes after the existing `BackendSyncStatusResponse`:

```kotlin
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
```

**Step 2: Commit**

```bash
git add core/network/src/main/java/com/chakir/plexhubtv/core/network/backend/BackendDto.kt
git commit -m "feat(XTREAM-02): add backend category and account management DTOs"
```

---

## Task 2: Add Backend API Endpoints

**Files:**
- Modify: `core/network/src/main/java/com/chakir/plexhubtv/core/network/backend/BackendApiService.kt`

**Step 1: Add missing endpoints**

Add to `BackendApiService` interface, after the existing methods:

```kotlin
// Account CRUD
@POST("api/accounts")
suspend fun createAccount(@Body account: BackendAccountCreate): BackendAccountResponse

@PUT("api/accounts/{accountId}")
suspend fun updateAccount(
    @Path("accountId") accountId: String,
    @Body update: BackendAccountUpdate,
): BackendAccountResponse

@DELETE("api/accounts/{accountId}")
suspend fun deleteAccount(@Path("accountId") accountId: String)

@POST("api/accounts/{accountId}/test")
suspend fun testAccount(
    @Path("accountId") accountId: String,
): BackendAccountTestResponse

// Categories
@GET("api/accounts/{accountId}/categories")
suspend fun getCategories(
    @Path("accountId") accountId: String,
): BackendCategoryListResponse

@PUT("api/accounts/{accountId}/categories")
suspend fun updateCategories(
    @Path("accountId") accountId: String,
    @Body request: BackendCategoryUpdateRequest,
)

@POST("api/accounts/{accountId}/categories/refresh")
suspend fun refreshCategories(
    @Path("accountId") accountId: String,
): BackendCategoryRefreshResponse

// Sync all
@POST("api/sync/xtream/all")
suspend fun triggerSyncAll(): BackendSyncJobResponse
```

**Step 2: Add missing imports**

Add the new DTO imports at the top of the file.

**Step 3: Commit**

```bash
git add core/network/src/main/java/com/chakir/plexhubtv/core/network/backend/BackendApiService.kt
git commit -m "feat(XTREAM-02): add backend category and account API endpoints"
```

---

## Task 3: Create Domain Models for Category Config

**Files:**
- Create: `core/model/src/main/java/com/chakir/plexhubtv/core/model/CategoryConfig.kt`

**Step 1: Create CategoryConfig.kt**

```kotlin
package com.chakir.plexhubtv.core.model

data class CategoryConfig(
    val filterMode: String = "whitelist",
    val categories: List<Category> = emptyList(),
)

data class Category(
    val categoryId: String,
    val categoryName: String,
    val categoryType: String, // "vod" or "series"
    val isAllowed: Boolean,
)

data class CategorySelection(
    val categoryId: String,
    val categoryType: String,
    val isAllowed: Boolean,
)
```

**Step 2: Commit**

```bash
git add core/model/src/main/java/com/chakir/plexhubtv/core/model/CategoryConfig.kt
git commit -m "feat(XTREAM-02): add CategoryConfig domain models"
```

---

## Task 4: Create CategoryRepository Interface

**Files:**
- Create: `domain/src/main/java/com/chakir/plexhubtv/domain/repository/CategoryRepository.kt`

**Step 1: Create interface**

```kotlin
package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.CategoryConfig
import com.chakir.plexhubtv.core.model.CategorySelection

interface CategoryRepository {
    suspend fun getCategories(accountId: String): Result<CategoryConfig>

    suspend fun updateCategories(
        accountId: String,
        filterMode: String,
        categories: List<CategorySelection>,
    ): Result<Unit>

    suspend fun refreshCategories(accountId: String): Result<Unit>
}
```

**Step 2: Commit**

```bash
git add domain/src/main/java/com/chakir/plexhubtv/domain/repository/CategoryRepository.kt
git commit -m "feat(XTREAM-02): add CategoryRepository domain interface"
```

---

## Task 5: Extend BackendRepository with Account CRUD & Sync All

**Files:**
- Modify: `domain/src/main/java/com/chakir/plexhubtv/domain/repository/BackendRepository.kt`
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt`

**Step 1: Add methods to BackendRepository interface**

Add after existing methods:

```kotlin
suspend fun createXtreamAccount(
    backendId: String,
    label: String,
    baseUrl: String,
    port: Int,
    username: String,
    password: String,
): Result<Unit>

suspend fun deleteXtreamAccount(backendId: String, accountId: String): Result<Unit>

suspend fun testXtreamAccount(backendId: String, accountId: String): Result<Unit>

suspend fun syncAll(backendId: String): Result<String>

suspend fun getCategories(backendId: String, accountId: String): Result<CategoryConfig>

suspend fun updateCategories(
    backendId: String,
    accountId: String,
    filterMode: String,
    categories: List<CategorySelection>,
): Result<Unit>

suspend fun refreshCategories(backendId: String, accountId: String): Result<Unit>
```

Add imports for `CategoryConfig` and `CategorySelection`.

**Step 2: Implement in BackendRepositoryImpl**

Add implementations after existing methods:

```kotlin
override suspend fun createXtreamAccount(
    backendId: String,
    label: String,
    baseUrl: String,
    port: Int,
    username: String,
    password: String,
): Result<Unit> = withContext(ioDispatcher) {
    runCatching {
        val backend = backendServerDao.getById(backendId)
            ?: throw IllegalStateException("Backend server $backendId not found")
        val service = backendApiClient.getService(backend.baseUrl)
        service.createAccount(
            BackendAccountCreate(
                label = label,
                baseUrl = baseUrl,
                port = port,
                username = username,
                password = password,
            ),
        )
        Unit
    }
}

override suspend fun deleteXtreamAccount(backendId: String, accountId: String): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.deleteAccount(accountId)
        }
    }

override suspend fun testXtreamAccount(backendId: String, accountId: String): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.testAccount(accountId)
            Unit
        }
    }

override suspend fun syncAll(backendId: String): Result<String> =
    withContext(ioDispatcher) {
        runCatching {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.triggerSyncAll().jobId
        }
    }

override suspend fun getCategories(backendId: String, accountId: String): Result<CategoryConfig> =
    withContext(ioDispatcher) {
        runCatching {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            val response = service.getCategories(accountId)
            CategoryConfig(
                filterMode = response.filterMode,
                categories = response.items.map {
                    Category(
                        categoryId = it.categoryId,
                        categoryName = it.categoryName,
                        categoryType = it.categoryType,
                        isAllowed = it.isAllowed,
                    )
                },
            )
        }
    }

override suspend fun updateCategories(
    backendId: String,
    accountId: String,
    filterMode: String,
    categories: List<CategorySelection>,
): Result<Unit> = withContext(ioDispatcher) {
    runCatching {
        val backend = backendServerDao.getById(backendId)
            ?: throw IllegalStateException("Backend server $backendId not found")
        val service = backendApiClient.getService(backend.baseUrl)
        service.updateCategories(
            accountId,
            BackendCategoryUpdateRequest(
                filterMode = filterMode,
                categories = categories.map {
                    BackendCategoryUpdate(
                        categoryId = it.categoryId,
                        categoryType = it.categoryType,
                        isAllowed = it.isAllowed,
                    )
                },
            ),
        )
    }
}

override suspend fun refreshCategories(backendId: String, accountId: String): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val backend = backendServerDao.getById(backendId)
                ?: throw IllegalStateException("Backend server $backendId not found")
            val service = backendApiClient.getService(backend.baseUrl)
            service.refreshCategories(accountId)
            Unit
        }
    }
```

Add imports for `BackendAccountCreate`, `BackendCategoryUpdateRequest`, `BackendCategoryUpdate`, `CategoryConfig`, `Category`, `CategorySelection`.

**Step 3: Commit**

```bash
git add domain/src/main/java/com/chakir/plexhubtv/domain/repository/BackendRepository.kt
git add data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt
git commit -m "feat(XTREAM-02): add account CRUD and category management to BackendRepository"
```

---

## Task 6: Implement HybridCategoryRepository

**Files:**
- Create: `data/src/main/java/com/chakir/plexhubtv/data/repository/HybridCategoryRepository.kt`

**Step 1: Create HybridCategoryRepository**

```kotlin
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
```

**Step 2: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/HybridCategoryRepository.kt
git commit -m "feat(XTREAM-02): implement HybridCategoryRepository with backend/Xtream fallback"
```

---

## Task 7: Wire Hilt Bindings for CategoryRepository

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/di/RepositoryModule.kt`

**Step 1: Add CategoryRepository binding**

Add a new `@Binds` method in the `@Module` abstract class:

```kotlin
@Binds
@Singleton
abstract fun bindCategoryRepository(
    impl: HybridCategoryRepository,
): CategoryRepository
```

Add imports:
```kotlin
import com.chakir.plexhubtv.domain.repository.CategoryRepository
import com.chakir.plexhubtv.data.repository.HybridCategoryRepository
```

**Step 2: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/di/RepositoryModule.kt
git commit -m "feat(XTREAM-02): bind HybridCategoryRepository in Hilt module"
```

---

## Task 8: Update Navigation Route with accountId Parameter

**Files:**
- Modify: `core/navigation/src/main/java/com/chakir/plexhubtv/core/navigation/Screen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/main/MainScreen.kt`

**Step 1: Update Screen.kt route**

Change `XtreamCategorySelection` from a static route to a parameterized one:

```kotlin
data object XtreamCategorySelection : Screen("xtream_category_selection/{accountId}") {
    fun createRoute(accountId: String) = "xtream_category_selection/$accountId"
}
```

**Step 2: Update MainActivity.kt NavHost**

Update the composable registration (around line 324):

```kotlin
composable(
    route = Screen.XtreamCategorySelection.route,
    arguments = listOf(
        navArgument("accountId") { type = NavType.StringType },
    ),
) { backStackEntry ->
    val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
    com.chakir.plexhubtv.feature.xtream.XtreamCategorySelectionRoute(
        accountId = accountId,
        onNavigateBack = { navController.popBackStack() },
    )
}
```

Update the navigation callback (around line 245):

```kotlin
onNavigateToXtreamCategorySelection = { accountId ->
    navController.navigate(Screen.XtreamCategorySelection.createRoute(accountId))
},
```

Add import: `import androidx.navigation.NavType` and `import androidx.navigation.navArgument` (if not already present).

**Step 3: Update MainScreen.kt**

Update the callback type (around line 63):

```kotlin
onNavigateToXtreamCategorySelection: (String) -> Unit = {},
```

Update the Settings route callback (around line 196):

```kotlin
onNavigateToXtreamCategorySelection = { accountId -> onNavigateToXtreamCategorySelection(accountId) },
```

**Step 4: Update SettingsScreen.kt**

Find where `onNavigateToXtreamCategorySelection` is called. It needs to pass an accountId. For the Settings entry point, we need to resolve which account to configure. Check current SettingsScreen/SettingsViewModel:

- If only one Xtream account: pass it directly
- If multiple: the settings screen should list accounts or pick the first

Read `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt` and `SettingsViewModel.kt` to understand how the navigation callback is currently passed and update the signature from `() -> Unit` to `(String) -> Unit`.

**Step 5: Commit**

```bash
git add core/navigation/src/main/java/com/chakir/plexhubtv/core/navigation/Screen.kt
git add app/src/main/java/com/chakir/plexhubtv/MainActivity.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/main/MainScreen.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt
git commit -m "feat(XTREAM-02): add accountId parameter to XtreamCategorySelection route"
```

---

## Task 9: Refactor XtreamCategorySelectionViewModel

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/xtream/XtreamCategorySelectionViewModel.kt`

**Step 1: Update data classes**

Replace the entire top of the file (SelectableCategory, CategorySection, UiState, Action):

```kotlin
data class SelectableCategory(
    val categoryId: String,       // changed from Int to String
    val categoryName: String,
    val categoryType: String,     // "vod" or "series"
    val isSelected: Boolean,
)

data class CategorySection(
    val accountId: String,
    val accountLabel: String,
    val vodCategories: List<SelectableCategory>,
    val seriesCategories: List<SelectableCategory>,
)

data class XtreamCategorySelectionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sections: List<CategorySection> = emptyList(),
    val filterMode: String = "whitelist",
    val isConfirming: Boolean = false,
    val isSyncing: Boolean = false,
)

sealed interface XtreamCategorySelectionAction {
    data class ToggleVodCategory(val accountId: String, val categoryId: String) :
        XtreamCategorySelectionAction
    data class ToggleSeriesCategory(val accountId: String, val categoryId: String) :
        XtreamCategorySelectionAction
    data class ToggleAllVod(val accountId: String) : XtreamCategorySelectionAction
    data class ToggleAllSeries(val accountId: String) : XtreamCategorySelectionAction
    data object Confirm : XtreamCategorySelectionAction
    data object Retry : XtreamCategorySelectionAction
}

sealed class XtreamCategorySelectionNavEvent {
    data object NavigateBack : XtreamCategorySelectionNavEvent()
}
```

**Step 2: Refactor ViewModel constructor and dependencies**

Replace the ViewModel class:

```kotlin
@HiltViewModel
class XtreamCategorySelectionViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: XtreamAccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: String = savedStateHandle.get<String>("accountId") ?: ""

    private val _uiState = MutableStateFlow(XtreamCategorySelectionUiState())
    val uiState: StateFlow<XtreamCategorySelectionUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<XtreamCategorySelectionNavEvent>()
    val navigationEvent: SharedFlow<XtreamCategorySelectionNavEvent> = _navigationEvent.asSharedFlow()

    init {
        loadCategories()
    }

    fun onAction(action: XtreamCategorySelectionAction) {
        when (action) {
            is XtreamCategorySelectionAction.ToggleVodCategory ->
                toggleCategory(action.accountId, action.categoryId, "vod")
            is XtreamCategorySelectionAction.ToggleSeriesCategory ->
                toggleCategory(action.accountId, action.categoryId, "series")
            is XtreamCategorySelectionAction.ToggleAllVod ->
                toggleAllByType(action.accountId, "vod")
            is XtreamCategorySelectionAction.ToggleAllSeries ->
                toggleAllByType(action.accountId, "series")
            is XtreamCategorySelectionAction.Confirm -> confirm()
            is XtreamCategorySelectionAction.Retry -> loadCategories()
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val account = accountRepository.getAccount(accountId)
                if (account == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Account not found")
                    }
                    return@launch
                }

                val result = categoryRepository.getCategories(accountId)
                val config = result.getOrThrow()

                val vodCategories = config.categories
                    .filter { it.categoryType == "vod" }
                    .map {
                        SelectableCategory(
                            categoryId = it.categoryId,
                            categoryName = it.categoryName,
                            categoryType = "vod",
                            isSelected = it.isAllowed,
                        )
                    }

                val seriesCategories = config.categories
                    .filter { it.categoryType == "series" }
                    .map {
                        SelectableCategory(
                            categoryId = it.categoryId,
                            categoryName = it.categoryName,
                            categoryType = "series",
                            isSelected = it.isAllowed,
                        )
                    }

                val section = CategorySection(
                    accountId = accountId,
                    accountLabel = account.label,
                    vodCategories = vodCategories,
                    seriesCategories = seriesCategories,
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        filterMode = config.filterMode,
                        sections = listOf(section),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load categories")
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load categories: ${e.message}")
                }
            }
        }
    }

    private fun toggleCategory(accountId: String, categoryId: String, type: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        if (type == "vod") {
                            section.copy(
                                vodCategories = section.vodCategories.map { cat ->
                                    if (cat.categoryId == categoryId) cat.copy(isSelected = !cat.isSelected)
                                    else cat
                                },
                            )
                        } else {
                            section.copy(
                                seriesCategories = section.seriesCategories.map { cat ->
                                    if (cat.categoryId == categoryId) cat.copy(isSelected = !cat.isSelected)
                                    else cat
                                },
                            )
                        }
                    } else section
                },
            )
        }
    }

    private fun toggleAllByType(accountId: String, type: String) {
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.accountId == accountId) {
                        if (type == "vod") {
                            val allSelected = section.vodCategories.all { it.isSelected }
                            section.copy(
                                vodCategories = section.vodCategories.map {
                                    it.copy(isSelected = !allSelected)
                                },
                            )
                        } else {
                            val allSelected = section.seriesCategories.all { it.isSelected }
                            section.copy(
                                seriesCategories = section.seriesCategories.map {
                                    it.copy(isSelected = !allSelected)
                                },
                            )
                        }
                    } else section
                },
            )
        }
    }

    private fun confirm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true) }

            try {
                val currentState = _uiState.value
                val section = currentState.sections.firstOrNull() ?: return@launch

                val selections = (section.vodCategories + section.seriesCategories).map {
                    CategorySelection(
                        categoryId = it.categoryId,
                        categoryType = it.categoryType,
                        isAllowed = it.isSelected,
                    )
                }

                categoryRepository.updateCategories(
                    accountId = accountId,
                    filterMode = currentState.filterMode,
                    categories = selections,
                ).getOrThrow()

                Timber.i("Category selection saved for account $accountId")
                _navigationEvent.emit(XtreamCategorySelectionNavEvent.NavigateBack)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save category selection")
                _uiState.update {
                    it.copy(isConfirming = false, error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
```

Add imports:
```kotlin
import androidx.lifecycle.SavedStateHandle
import com.chakir.plexhubtv.core.model.CategorySelection
import com.chakir.plexhubtv.domain.repository.CategoryRepository
```

Remove unused imports: `com.chakir.plexhubtv.core.database.MediaDao`, `com.chakir.plexhubtv.core.datastore.SettingsDataStore`, `com.chakir.plexhubtv.domain.usecase.GetXtreamCategoriesUseCase`, `kotlinx.coroutines.async`, `kotlinx.coroutines.awaitAll`.

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/xtream/XtreamCategorySelectionViewModel.kt
git commit -m "feat(XTREAM-02): refactor category ViewModel to use CategoryRepository"
```

---

## Task 10: Update XtreamCategorySelectionScreen

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/xtream/XtreamCategorySelectionScreen.kt`

**Step 1: Update Route to accept accountId**

```kotlin
@Composable
fun XtreamCategorySelectionRoute(
    accountId: String,
    viewModel: XtreamCategorySelectionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
```

The `accountId` is received via navigation argument and the ViewModel reads it from `SavedStateHandle`, so we just need to update the composable signature.

**Step 2: Update CategoryItem composable for String categoryId**

The `CategoryItem` composable references `SelectableCategory` which now uses `String` for `categoryId`. No changes needed to the composable itself since it only uses `category.categoryName` and `category.isSelected`. The actions in the parent already reference `category.categoryId` which is now String — this is handled by the updated action types from Task 9.

**Step 3: Add filter mode indicator**

Add a chip after the subtitle showing the filter mode. Insert between the subtitle and the `when` block (after the second Spacer around line 108):

```kotlin
// Filter mode indicator
Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.padding(bottom = 16.dp),
) {
    Text(
        text = when (state.filterMode) {
            "whitelist" -> stringResource(R.string.xtream_category_mode_whitelist)
            "blacklist" -> stringResource(R.string.xtream_category_mode_blacklist)
            else -> stringResource(R.string.xtream_category_mode_all)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
```

**Step 4: Add syncing state to confirm button**

Update the confirm button's `enabled` and text to handle `isSyncing`:

```kotlin
enabled = selectedCount > 0 && !state.isConfirming && !state.isSyncing,
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/xtream/XtreamCategorySelectionScreen.kt
git commit -m "feat(XTREAM-02): update category selection screen with String IDs and filter mode"
```

---

## Task 11: Add String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`

**Step 1: Add English strings**

Add near the existing xtream_category strings:

```xml
<string name="xtream_category_mode_whitelist">Whitelist mode</string>
<string name="xtream_category_mode_blacklist">Blacklist mode</string>
<string name="xtream_category_mode_all">All categories</string>
<string name="xtream_category_syncing">Syncing…</string>
```

**Step 2: Add French strings**

```xml
<string name="xtream_category_mode_whitelist">Mode liste blanche</string>
<string name="xtream_category_mode_blacklist">Mode liste noire</string>
<string name="xtream_category_mode_all">Toutes les categories</string>
<string name="xtream_category_syncing">Synchronisation…</string>
```

**Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-fr/strings.xml
git commit -m "feat(XTREAM-02): add category filter mode string resources"
```

---

## Task 12: Update SettingsScreen to Pass accountId

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt`

**Step 1: Read SettingsScreen.kt, SettingsViewModel.kt, SettingsUiState.kt**

Understand how `onNavigateToXtreamCategorySelection` is currently called. The callback needs to change from `() -> Unit` to `(String) -> Unit`.

**Step 2: Add Xtream account info to SettingsUiState**

Add a field to expose the first Xtream account ID for the category navigation:

```kotlin
val xtreamAccountId: String? = null,
```

**Step 3: Load Xtream account in SettingsViewModel**

In the ViewModel's init or loading function, observe Xtream accounts and expose the first account's ID:

```kotlin
// In init block or data loading
viewModelScope.launch {
    accountRepository.observeAccounts().collect { accounts ->
        _uiState.update { it.copy(xtreamAccountId = accounts.firstOrNull()?.id) }
    }
}
```

**Step 4: Update SettingsScreen callback**

Change the callback type:

```kotlin
onNavigateToXtreamCategorySelection: (String) -> Unit = {},
```

When the user taps "Manage Xtream Categories", pass the accountId:

```kotlin
onClick = {
    state.xtreamAccountId?.let { onNavigateToXtreamCategorySelection(it) }
}
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt
git commit -m "feat(XTREAM-02): pass accountId from Settings to category selection"
```

---

## Task 13: Build Verification

**Step 1: Run the build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors.

**Step 2: Run existing tests**

```bash
./gradlew test
```

Expected: All existing tests pass. Fix any test failures caused by the refactoring (especially if any existing tests reference the old ViewModel constructor or old action types).

**Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix(XTREAM-02): fix compilation and test issues from category refactor"
```

---

## Task 14: Final Review & Cleanup

**Step 1: Verify the complete flow**

Check that:
1. `BackendApiService` has all endpoints from the integration guide
2. `BackendDto` has all DTOs matching the API responses
3. `HybridCategoryRepository` correctly delegates based on backend availability
4. `XtreamCategorySelectionViewModel` uses `CategoryRepository` (not direct Xtream/DataStore)
5. Navigation passes `accountId` through the entire chain
6. String resources exist in both EN and FR

**Step 2: Remove dead code**

If `SettingsDataStore.selectedXtreamCategoryIds` is only used by `HybridCategoryRepository` (for the Xtream fallback path), keep it. If `mediaDao.deleteMediaByXtreamCategory` is no longer called from the ViewModel, verify it's still used elsewhere or remove it.

**Step 3: Final commit**

```bash
git add -A
git commit -m "chore(XTREAM-02): cleanup dead code from category refactor"
```

---

## Summary of All Files

### Created
| File | Purpose |
|------|---------|
| `core/model/.../CategoryConfig.kt` | Domain models: CategoryConfig, Category, CategorySelection |
| `domain/.../CategoryRepository.kt` | Hybrid category repository interface |
| `data/.../HybridCategoryRepository.kt` | Implementation with backend/Xtream fallback |

### Modified
| File | Changes |
|------|---------|
| `core/network/.../BackendDto.kt` | Add 8 new DTOs (account + category) |
| `core/network/.../BackendApiService.kt` | Add 8 new endpoints |
| `domain/.../BackendRepository.kt` | Add 7 new methods (CRUD + categories) |
| `data/.../BackendRepositoryImpl.kt` | Implement 7 new methods |
| `data/.../di/RepositoryModule.kt` | Bind HybridCategoryRepository |
| `core/navigation/.../Screen.kt` | Add accountId param to route |
| `app/.../MainActivity.kt` | Update NavHost for parameterized route |
| `app/.../feature/main/MainScreen.kt` | Update callback type |
| `app/.../feature/xtream/XtreamCategorySelectionViewModel.kt` | Full refactor to use CategoryRepository |
| `app/.../feature/xtream/XtreamCategorySelectionScreen.kt` | String IDs + filter mode chip |
| `app/.../feature/settings/SettingsScreen.kt` | Pass accountId |
| `app/.../feature/settings/SettingsViewModel.kt` | Expose xtreamAccountId |
| `app/.../feature/settings/SettingsUiState.kt` | Add xtreamAccountId field |
| `app/src/main/res/values/strings.xml` | 4 new strings |
| `app/src/main/res/values-fr/strings.xml` | 4 new strings |
