# Backend Xtream Integration & Category Selection Design

**Date:** 2026-02-28
**Status:** Approved

## Context

The PlexHub Backend has evolved with new API endpoints for:
- Full Xtream account CRUD (create/update/delete/test)
- Category management with whitelist/blacklist/all filter modes
- Category auto-refresh during sync
- Server-side category filtering (`is_in_allowed_categories`)

The Android app needs to adapt to these changes while maintaining the direct Xtream fallback for users without a backend server.

## Decisions

1. **Hybrid approach**: Backend API when a backend server is configured, direct Xtream fallback otherwise
2. **Backend account CRUD**: Account creation/deletion via backend API when available
3. **Default filter mode**: Whitelist (user picks categories to include)
4. **Navigation flow**: Category selection screen shown after account creation

## Architecture: Unified Abstraction Layer (Approach A)

### 1. Network Layer

**BackendApiService** — add missing endpoints:

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/accounts` | Create account |
| PUT | `/api/accounts/{id}` | Update account |
| DELETE | `/api/accounts/{id}` | Delete account |
| POST | `/api/accounts/{id}/test` | Test connection |
| GET | `/api/accounts/{id}/categories` | List categories + filterMode |
| PUT | `/api/accounts/{id}/categories` | Update category config |
| POST | `/api/accounts/{id}/categories/refresh` | Refresh from Xtream |
| POST | `/api/sync/xtream/all` | Sync all accounts |

**BackendDto** — add missing DTOs:

- `BackendAccountCreate` (label, baseUrl, port, username, password)
- `BackendAccountUpdate` (all optional)
- `BackendAccountTestResponse` (status, expirationDate, maxConnections, allowedFormats)
- `BackendCategoryItem` (categoryId: String, categoryName, categoryType, isAllowed, lastFetchedAt)
- `BackendCategoryListResponse` (items, filterMode)
- `BackendCategoryUpdate` (categoryId, categoryType, isAllowed)
- `BackendCategoryUpdateRequest` (filterMode, categories)
- `BackendCategoryRefreshResponse` (message, vodCount, seriesCount, total)

### 2. Domain Layer

**New interface `CategoryRepository`:**
- `getCategories(accountId: String): Result<CategoryConfig>`
- `updateCategories(accountId: String, filterMode: String, categories: List<CategorySelection>): Result<Unit>`
- `refreshCategories(accountId: String): Result<Unit>`

**Domain model `CategoryConfig`:**
- `filterMode: String` (all/whitelist/blacklist)
- `categories: List<Category>` (categoryId: String, categoryName, categoryType, isAllowed)

**Extend `BackendRepository`:**
- `createAccount(label, baseUrl, port, username, password): Result<BackendAccountResponse>`
- `deleteAccount(accountId): Result<Unit>`
- `testAccount(accountId): Result<BackendAccountTestResponse>`
- `syncAll(): Result<String>`

**New interface `AccountManagementRepository`:**
- `observeAccounts(): Flow<List<XtreamAccount>>`
- `addAccount(...)` / `removeAccount(...)` / `testAccount(...)`

### 3. Data Layer

**`HybridCategoryRepository`** (implements `CategoryRepository`):
- `hasBackend()`: checks `BackendServerDao` for active servers
- Backend path: `BackendApiService.getCategories()` / `.updateCategories()`
- Xtream path: `GetXtreamCategoriesUseCase` + maps to CategoryConfig, persists in DataStore

**`HybridAccountManagementRepository`** (implements `AccountManagementRepository`):
- Backend path: `BackendApiService` account CRUD
- Xtream path: delegates to `XtreamAccountRepository`

Both `@Singleton`, provided via Hilt `RepositoryModule`.

### 4. ViewModel Refactor

**XtreamCategorySelectionViewModel** changes:
- Replace `GetXtreamCategoriesUseCase` + `SettingsDataStore` with `CategoryRepository`
- `categoryId` type: `Int` -> `String`
- Add `filterMode` to UI state (default: "whitelist")
- On confirm: `categoryRepository.updateCategories()` then trigger sync
- Remove local DataStore purge logic (backend handles filtering server-side)

**Updated UI state:**
```kotlin
data class XtreamCategorySelectionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sections: List<CategorySection> = emptyList(),
    val filterMode: String = "whitelist",
    val isConfirming: Boolean = false,
    val isSyncing: Boolean = false,
)
```

### 5. UI Polish

- Add filter mode indicator chip
- Fix categoryId from Int to String in SelectableCategory
- Add sync progress after confirming
- Keep TV-friendly D-pad navigation and focus management

### 6. Navigation

- `Screen.XtreamCategorySelection` updated to accept `accountId` parameter
- Route: `xtream_category_selection/{accountId}`
- Shown after account creation, also accessible from Settings

## Files to Create

- `domain/src/.../repository/CategoryRepository.kt`
- `domain/src/.../repository/AccountManagementRepository.kt`
- `domain/src/.../model/CategoryConfig.kt` (or in core/model)
- `data/src/.../repository/HybridCategoryRepository.kt`
- `data/src/.../repository/HybridAccountManagementRepository.kt`

## Files to Modify

- `core/network/.../backend/BackendApiService.kt` (add endpoints)
- `core/network/.../backend/BackendDto.kt` (add DTOs)
- `domain/.../repository/BackendRepository.kt` (add methods)
- `data/.../repository/BackendRepositoryImpl.kt` (implement new methods)
- `data/.../di/RepositoryModule.kt` (bind new repositories)
- `app/.../feature/xtream/XtreamCategorySelectionViewModel.kt` (refactor)
- `app/.../feature/xtream/XtreamCategorySelectionScreen.kt` (polish + String categoryId)
- `core/navigation/.../Screen.kt` (add accountId param)
- `app/src/main/res/values/strings.xml` (new strings)
- `app/src/main/res/values-fr/strings.xml` (French translations)
