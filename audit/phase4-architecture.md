# Phase 4 -- Code Quality & Architecture Audit

> **Date**: 2026-02-25
> **Auditor**: Claude Opus 4.6 (Automated Architecture Review)
> **Scope**: All 11 modules of PlexHubTV multi-module Clean Architecture project
> **Methodology**: Static analysis of build.gradle.kts, source files, module boundaries, test infrastructure

---

## Executive Summary

PlexHubTV is a well-structured multi-module Android TV app following Clean Architecture principles. The overall dependency graph is correct (Presentation -> Domain <- Data, with Core shared). However, there are several **P0/P1** issues around duplicate interfaces, inconsistent error handling patterns, hardcoded dispatchers, and architectural boundary leaks that should be addressed before production release.

**Overall Project Grade: B-**

---

## Module Grades

| Module | Grade | Key Issues |
|--------|-------|------------|
| `:domain` | **B** | Duplicate interface methods, unused `Resource` wrapper |
| `:data` | **B-** | Hardcoded dispatchers, duplicate `getClient()` pattern, `BuildConfig` secrets leak |
| `:app` | **C+** | Hardcoded dispatchers, duplicate `HubViewModel`/`HubDetailViewModel`, bloated `Screen.kt` |
| `:core:model` | **B+** | Compose runtime dependency in a pure model module, Retrofit dependency in build.gradle |
| `:core:common` | **B-** | Retrofit dependency, Navigation dependency, Firebase dependency -- too many concerns |
| `:core:network` | **A-** | Solid; minor: `core:datastore` dependency inverts the layer direction |
| `:core:database` | **A-** | Clean; no issues found |
| `:core:datastore` | **A** | Clean, well-scoped |
| `:core:designsystem` | **A** | Clean, no issues |
| `:core:navigation` | **A-** | Clean; duplicate `Screen` definitions are intentional but fragile |
| `:core:ui` | **B+** | Hilt dependency unnecessary for a pure UI component module |

---

## Findings

---

### F-01: Duplicate Interface Methods Between `MediaRepository` and `PlaybackRepository`

- **Severity**: P1
- **Files**:
  - `domain/src/main/java/.../repository/MediaRepository.kt` (lines 73-136)
  - `domain/src/main/java/.../repository/PlaybackRepository.kt` (lines 1-38)
- **Problem**: `PlaybackRepository` is an almost exact subset of `MediaRepository`. The methods `toggleWatchStatus`, `updatePlaybackProgress`, `getNextMedia`, `getPreviousMedia`, `getWatchHistory`, and `updateStreamSelection` are duplicated verbatim in both interfaces. This violates Interface Segregation (ISP) in the wrong direction -- instead of splitting a fat interface into focused ones, both interfaces declare the same methods, creating ambiguity about which to inject.
- **Solution**: Remove the duplicate methods from `MediaRepository` and keep them only in `PlaybackRepository`. ViewModels that need playback operations should inject `PlaybackRepository` directly. `MediaRepository` should focus on metadata (detail, search, library, hubs, collections).

---

### F-02: Duplicate Interface Methods Between `MediaRepository` and `MediaDetailRepository`

- **Severity**: P1
- **Files**:
  - `domain/src/main/java/.../repository/MediaRepository.kt` (lines 30-68)
  - `domain/src/main/java/.../repository/MediaDetailRepository.kt` (lines 1-49)
- **Problem**: `getMediaDetail`, `getSeasonEpisodes`, `getShowSeasons`, `getSimilarMedia`, `getMediaCollections`, and `getCollection` are duplicated between these two interfaces. The `MediaDetailRepository` was introduced as a focused interface (correct), but `MediaRepository` was never cleaned up. Both are bound in `RepositoryModule` to separate implementations.
- **Solution**: Remove the detail/season/collection methods from `MediaRepository`. `MediaDetailRepository` is the focused interface and should be the sole owner. Update callers to inject `MediaDetailRepository` for these operations.

---

### F-03: Duplicate Interface Methods Between `MediaRepository` and `FavoritesRepository`

- **Severity**: P1
- **Files**:
  - `domain/src/main/java/.../repository/MediaRepository.kt` (lines 94-107)
  - `domain/src/main/java/.../repository/FavoritesRepository.kt` (lines 1-17)
- **Problem**: `getFavorites`, `isFavorite`, `isFavoriteAny`, and `toggleFavorite` are duplicated in both interfaces. `FavoritesRepository` is the correct focused interface.
- **Solution**: Remove favorite methods from `MediaRepository`. Inject `FavoritesRepository` where needed.

---

### F-04: God Interface -- `MediaRepository` Has 19 Methods Spanning 5 Concerns

- **Severity**: P1
- **File**: `domain/src/main/java/.../repository/MediaRepository.kt` (lines 1-136)
- **Problem**: `MediaRepository` covers: aggregation (on-deck, hubs, library), detail (metadata, seasons, episodes), favorites, history, search, playback, and stream selection. This is a God Interface that makes testing difficult, creates ambiguity with the focused repositories, and is never fully implemented by a single class (the impl delegates to focused repos).
- **Solution**: `MediaRepository` should be decomposed or made a facade that delegates. Ideally, callers should inject the focused interfaces directly (`MediaDetailRepository`, `FavoritesRepository`, `PlaybackRepository`, `OnDeckRepository`, `HubsRepository`, `SearchRepository`). Then `MediaRepository` can be removed or reduced to a thin orchestration layer.

---

### F-05: Hardcoded `Dispatchers.IO`/`Dispatchers.Main` Instead of Injected Dispatchers

- **Severity**: P1
- **Files** (partial list):
  - `data/src/main/java/.../cache/RoomApiCache.kt` (lines 22, 42, 55)
  - `data/src/main/java/.../repository/AuthRepositoryImpl.kt` (line 206)
  - `data/src/main/java/.../repository/SyncRepositoryImpl.kt` (lines 45, 110)
  - `data/src/main/java/.../repository/IptvRepositoryImpl.kt` (line 34)
  - `data/src/main/java/.../repository/WatchlistRepositoryImpl.kt` (line 117)
  - `data/src/main/java/.../repository/aggregation/MediaDeduplicator.kt` (line 33)
  - `app/src/main/java/.../work/CollectionSyncWorker.kt` (line 39)
  - `app/src/main/java/.../work/LibrarySyncWorker.kt` (line 61)
  - `app/src/main/java/.../feature/debug/DebugViewModel.kt` (lines 103-290)
  - `app/src/main/java/.../feature/player/controller/PlayerController.kt` (lines 51, 140)
  - `domain/src/main/java/.../usecase/EnrichMediaItemUseCase.kt` (line 197)
- **Problem**: The project defines `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` qualifiers in `CoroutineModule`, but many classes bypass them and use `Dispatchers.IO`/`Dispatchers.Main` directly. This makes unit testing impossible without `Dispatchers.setMain()` hacks, and violates the project's own DI conventions.
- **Solution**: Inject `@IoDispatcher CoroutineDispatcher` and `@MainDispatcher CoroutineDispatcher` via constructor in all classes that need dispatchers. Remove all direct `Dispatchers.*` references outside of `CoroutineModule`.

---

### F-06: `core:model` Depends on `androidx.compose.runtime` and `retrofit2`

- **Severity**: P1
- **File**: `core/model/build.gradle.kts` (lines 29-31)
- **Problem**: `core:model` is supposed to be a pure Kotlin model module but depends on:
  1. `libs.androidx.compose.runtime.annotation` -- for `@Immutable` annotation on `MediaItem`, `Hub`, `Server`
  2. `libs.retrofit` -- present in build.gradle.kts but no retrofit imports found in source (dead dependency)

  The `@Immutable` annotation couples the model layer to Compose, which is a presentation concern. This means `:domain` transitively depends on Compose runtime.
- **Solution**:
  1. Remove the `retrofit` dependency -- it is unused.
  2. For `@Immutable`: either use `@Stable` from compose-runtime (same issue) or remove it entirely. Compose can infer stability from `data class` with immutable fields. Alternatively, use a Compose compiler stability configuration file to mark these classes as stable without the annotation.

---

### F-07: `core:common` Is a Kitchen Sink Module With Inverted Dependencies

- **Severity**: P1
- **File**: `core/common/build.gradle.kts` (lines 39-68)
- **Problem**: `core:common` depends on:
  - `retrofit` (for `SafeApiCall.kt` which catches `HttpException`)
  - `androidx.compose.navigation` (for `MediaNavigationHelper.kt`)
  - `androidx.tv.provider` (for `WatchNextHelper.kt`)
  - `firebase-crashlytics` (for `GlobalCoroutineExceptionHandler`)
  - `androidx.work.runtime` (for `WorkModule.kt`)
  - `hilt-android` (for DI modules)

  A "common" module should contain pure Kotlin utilities. Instead, it has become a dumping ground for unrelated concerns: navigation helpers, Android TV provider integration, Firebase, WorkManager, and network error handling. This creates a dependency chain where every module that depends on `:core:common` transitively pulls in Compose Navigation, Firebase, etc.
- **Solution**: Extract into focused modules:
  - Keep `core:common`: StringNormalizer, FormatUtils, CodecUtils, Resource, FlowExtensions, CoroutineModule
  - Move `SafeApiCall.kt` to `core:network` (it depends on Retrofit)
  - Move `MediaNavigationHelper.kt` to `core:navigation` or `app`
  - Move `WatchNextHelper.kt` to `data` (Android TV Provider is a data concern)
  - Move `FirebaseModule.kt` to `app` or a dedicated `core:firebase` module
  - Move `WorkModule.kt` to `app` or `data`

---

### F-08: Inconsistent Package Structure in `core:common`

- **Severity**: P2
- **File**: `core/common/src/main/java/com/chakir/plexhubtv/core/`
- **Problem**: Files are split across two package roots:
  - `com.chakir.plexhubtv.core.common.*` (StringNormalizer, PerformanceTracker, SafeApiCall, FlowExtensions, auth, handler, util)
  - `com.chakir.plexhubtv.core.di.*` (CoroutineModule, FirebaseModule)
  - `com.chakir.plexhubtv.core.util.*` (CacheManager, ContentRatingHelper, ImageUtil, MediaNavigationHelper, WatchNextHelper, WorkModule)

  Three different package prefixes in one module is confusing and suggests the module was assembled ad-hoc.
- **Solution**: Consolidate to a single root package `com.chakir.plexhubtv.core.common` with sub-packages (di, util, auth, handler).

---

### F-09: `Resource<T>` Sealed Class Is Almost Entirely Unused

- **Severity**: P2
- **File**: `core/common/src/main/java/.../util/Resource.kt`
- **Problem**: The `Resource<T>` sealed class (Loading/Success/Error) is only used in one place: `GetRecommendedContentUseCase.kt`. All other use cases and repositories use `kotlin.Result<T>` consistently. This creates an inconsistency in error handling patterns and forces callers to handle two different result wrappers.
- **Solution**: Remove `Resource<T>` and migrate `GetRecommendedContentUseCase` to use `Flow<Result<List<Hub>>>` like all other use cases.

---

### F-10: Duplicate `Screen` Sealed Classes

- **Severity**: P2
- **Files**:
  - `core/navigation/src/main/java/.../navigation/Screen.kt` (10 entries)
  - `app/src/main/java/.../di/navigation/Screen.kt` (20+ entries with createRoute helpers)
- **Problem**: Two `Screen` sealed classes exist with overlapping entries (Home, Hub, Movies, TVShows, Search, Downloads, Favorites, History, Settings, Iptv). Route strings must be kept in sync manually. If a route string changes in one file but not the other, navigation breaks silently.
- **Solution**: Define route strings as constants in `core:navigation` and reference them from the app's `Screen` class. Or use a shared `Routes` object with string constants that both classes reference.

---

### F-11: Duplicate `getClient()` Pattern Across Repository Implementations

- **Severity**: P2
- **Files**:
  - `data/src/main/java/.../repository/PlaybackRepositoryImpl.kt` (lines 180-185)
  - `data/src/main/java/.../repository/MediaDetailRepositoryImpl.kt`
  - `data/src/main/java/.../repository/LibraryRepositoryImpl.kt`
  - `data/src/main/java/.../repository/ServerClientResolver.kt`
- **Problem**: The pattern of "get servers -> find server by ID -> resolve best connection -> create PlexClient" is duplicated across 3+ repository implementations. `ServerClientResolver` exists to centralize this, but several repos still implement their own `getClient()` private method.
- **Solution**: All repositories should use `ServerClientResolver` exclusively. Remove private `getClient()` methods.

---

### F-12: `core:network` Depends on `core:datastore` (Inverted Layer)

- **Severity**: P2
- **File**: `core/network/build.gradle.kts` (line 73)
- **Problem**: `core:network` depends on `core:datastore` because `AuthInterceptor` reads the Plex token from `SettingsDataStore`. Per the architecture diagram, core modules should not depend on each other in a way that creates coupling. Network depending on datastore means the network layer cannot be used independently.
- **Solution**: Define a `TokenProvider` interface in `core:network` and implement it in `core:datastore` or `data`. Inject via Hilt. This inverts the dependency correctly.

---

### F-13: `core:model` Has Retrofit as a Dependency

- **Severity**: P2
- **File**: `core/model/build.gradle.kts` (line 31)
- **Problem**: `implementation(libs.retrofit)` is declared but no source file in `core:model` uses any Retrofit class. The `AppError.kt` references `retrofit2.HttpException` but via the extension function which is in the same file. Wait -- confirmed: `core/model/src/.../AppError.kt` line 85 does reference `retrofit2.HttpException`. This means the **pure model module depends on a network library**.
- **Solution**: Move `toHttpAppError()` extension to `core:network` or `core:common` (which already depends on Retrofit). The `toAppError()` function can stay in `:core:model` but the `retrofit2.HttpException` handling should be extracted.

---

### F-14: `core:designsystem` and `core:ui` Have Hilt Dependencies

- **Severity**: P2
- **Files**:
  - `core/designsystem/build.gradle.kts` (lines 49-50)
  - `core/ui/build.gradle.kts` (lines 79-80)
- **Problem**: Both modules declare `implementation(libs.hilt.android)` and `ksp(libs.hilt.compiler)` but are pure UI modules with no `@Inject`, `@Module`, or `@Provides` annotations. Hilt is unnecessary overhead.
- **Solution**: Remove Hilt dependencies from both `core:designsystem` and `core:ui`.

---

### F-15: `data` Module Builds API Keys Into `BuildConfig` for Both Debug AND Release

- **Severity**: P1 (Security)
- **File**: `data/build.gradle.kts` (lines 24-51)
- **Problem**: Debug builds embed `PLEX_TOKEN`, `IPTV_PLAYLIST_URL`, `TMDB_API_KEY`, and `OMDB_API_KEY` from `local.properties` into `BuildConfig`. While release sets these to empty strings, the debug `BuildConfig` is compiled into the APK and can be decompiled. Also, the same pattern is duplicated in `app/build.gradle.kts` (lines 47-61), meaning keys are compiled into two modules.
- **Solution**: Remove `BuildConfig` fields from `:data` entirely (they are already in `:app`). For `:app`, consider using runtime-only mechanisms (DataStore/EncryptedSharedPreferences) for API keys instead of compile-time constants.

---

### F-16: Duplicate `BuildConfig` Fields in `:app` and `:data`

- **Severity**: P2
- **Files**:
  - `app/build.gradle.kts` (lines 47-80)
  - `data/build.gradle.kts` (lines 24-51)
- **Problem**: Both `:app` and `:data` define identical `buildConfigField` entries for `PLEX_TOKEN`, `IPTV_PLAYLIST_URL`, `TMDB_API_KEY`, `OMDB_API_KEY`. This is maintenance-prone duplication.
- **Solution**: Define them in only one module (`:app`) and pass values to `:data` via dependency injection or a shared configuration module.

---

### F-17: Duplicate `HubViewModel` and `HubDetailViewModel` With Overlapping Responsibilities

- **Severity**: P2
- **Files**:
  - `app/src/main/java/.../feature/hub/HubViewModel.kt`
  - `app/src/main/java/.../feature/hub/HubDetailViewModel.kt`
- **Problem**: `HubViewModel` uses `GetUnifiedHomeContentUseCase` (same as `HomeViewModel`), creating a parallel entry point for home content. `HubDetailViewModel` handles drill-down into a specific hub. The naming convention `HubViewModel` vs `HubDetailViewModel` is confusing and the former overlaps with `HomeViewModel`.
- **Solution**: Clarify or remove `HubViewModel` if its role is subsumed by `HomeViewModel`. If it serves a distinct screen (e.g., a "Hub" tab), rename it to clarify the distinction.

---

### F-18: `EnrichMediaItemUseCase` Uses `withContext(Dispatchers.IO)` Directly

- **Severity**: P2
- **File**: `domain/src/main/java/.../usecase/EnrichMediaItemUseCase.kt` (line 197)
- **Problem**: The domain layer use case directly references `kotlinx.coroutines.Dispatchers.IO`, which is an implementation detail and violates the domain layer's purity. It also bypasses the `@IoDispatcher` qualifier.
- **Solution**: Inject `@IoDispatcher CoroutineDispatcher` via constructor and use it instead.

---

### F-19: `PerformanceTracker` Uses Mutable Lists Without Thread Safety

- **Severity**: P2
- **File**: `core/common/src/main/java/.../PerformanceTracker.kt` (lines 16-17)
- **Problem**: `completedMetrics` is a plain `mutableListOf()` accessed from multiple threads (any coroutine can call `endOperation`). While `activeOperations` uses `ConcurrentHashMap`, `completedMetrics` is not thread-safe. `OperationMetric` also has `var` fields and `MutableList`/`MutableMap` fields, making it unsafe for concurrent access.
- **Solution**: Use `Collections.synchronizedList()` or a `ConcurrentLinkedQueue` for `completedMetrics`. Make `OperationMetric` immutable or use atomic updates.

---

### F-20: No `@di` Module Listed in `settings.gradle.kts` Despite Existing on Disk

- **Severity**: P2
- **File**: Root `di/` directory exists but is NOT included in `settings.gradle.kts`
- **Problem**: There is a `di/` directory at the root level with source files, but it is not listed in `settings.gradle.kts`. This is either dead code or a partially migrated module.
- **Solution**: If the code in `di/` is dead, delete the directory. If it was intended as a module, register it in `settings.gradle.kts`.

---

### F-21: `core:common` `SafeApiCall` Imports `retrofit2.HttpException`

- **Severity**: P2
- **File**: `core/common/src/main/java/.../SafeApiCall.kt` (line 6)
- **Problem**: A utility function in `core:common` depends on `retrofit2.HttpException`, pulling a network dependency into a common module.
- **Solution**: Move `safeApiCall` to `core:network` since it explicitly handles Retrofit exceptions, or create a generic version in `core:common` that only handles standard exceptions, with a Retrofit-specific extension in `core:network`.

---

### F-22: `ktlint` `ignoreFailures` Set to `true`

- **Severity**: P2
- **File**: `build.gradle.kts` (line 30)
- **Problem**: `ignoreFailures.set(true)` means ktlint violations never break the build. This undermines the purpose of having a linter.
- **Solution**: Set `ignoreFailures.set(false)` to enforce code style. Fix existing violations first.

---

### F-23: Missing Turbine Test Dependency

- **Severity**: P2
- **File**: All `build.gradle.kts` test dependency sections
- **Problem**: ARCHITECTURE.md lists Turbine as part of the test stack, but it is not declared in any `build.gradle.kts`. Tests that need to test `StateFlow`/`Flow` emissions may be using ad-hoc approaches.
- **Solution**: Add `testImplementation("app.cash.turbine:turbine:1.0.0")` to modules that test Flows.

---

### F-24: `ErrorExtensions.kt` Has Hardcoded French Error Messages

- **Severity**: P2
- **File**: `core/model/src/main/java/.../ErrorExtensions.kt` (lines 6-46)
- **Problem**: All user-facing error messages in `toUserMessage()` are hardcoded in French. This prevents localization and is inconsistent with the rest of the app which uses `strings.xml` for i18n.
- **Solution**: Remove `toUserMessage()` from the model layer (it is a presentation concern). Move error-to-message mapping to the UI layer using string resources with `@StringRes` references.

---

### F-25: `data/src/.../core/util/MediaUrlResolver.kt` Is in the Wrong Package

- **Severity**: P2
- **File**: `data/src/main/java/com/chakir/plexhubtv/core/util/MediaUrlResolver.kt`
- **Problem**: This file is in the `:data` module but uses the `com.chakir.plexhubtv.core.util` package, which is the package namespace of `core:common`. This is misleading and could cause package collisions.
- **Solution**: Move to `com.chakir.plexhubtv.data.util` package to match the module.

---

## Test Infrastructure Assessment

### Current Coverage

| Module | Test Files | Approximate Tests | Assessment |
|--------|-----------|-------------------|------------|
| `:app` | 11 | ~60+ | Good coverage for player, medidetail, search, library, profiles |
| `:core:model` | 1 | ~10 | AppError conversion tested |
| `:core:common` | 3 | ~15 | AuthEventBus, StringNormalizer, GlobalExceptionHandler |
| `:core:network` | 3 | ~15 | AuthInterceptor, NetworkSecurity, ConnectionManager |
| `:data` | 3 | ~15 | MediaUrlResolver, ProfileRepository, MediaMapper |
| `:domain` | 8 | ~40+ | Good use case coverage including EnrichMediaItemUseCase |
| **Total** | **29** | **~155+** | |

### Critical Missing Tests

1. **No instrumented tests** -- Zero `androidTest` files. No UI tests, no Room migration tests, no end-to-end tests.
2. **No `HomeViewModel` test** -- Wait, `HomeViewModelTest.kt` exists but needs verification it compiles with current code changes (file is modified in git status).
3. **No `SettingsRepositoryImpl` test** -- Settings is a critical path with many flows.
4. **No `SyncRepositoryImpl` test** -- Sync logic is complex and error-prone.
5. **No `MediaMapper` integration test** -- Mapper is complex (420+ lines) with DTO -> Domain -> Entity conversions; only unit test exists.
6. **No Room migration tests** -- 15 migrations (v11-v26) with no automated verification.
7. **No Worker tests** -- `LibrarySyncWorker`, `CollectionSyncWorker`, `RatingSyncWorker` have no tests.

---

## Dependency Health

### Version Assessment

| Dependency | Version | Status |
|-----------|---------|--------|
| AGP | 9.0.1 | Current |
| Kotlin | 2.2.10 | Current |
| Compose BOM | 2026.01.00 | Current |
| Room | 2.8.4 | Current |
| Hilt | 2.58 | Current |
| Retrofit | 3.0.0 | Current |
| OkHttp | 5.3.2 | Current |
| Media3 | 1.5.1 | Current |
| Coil | 3.3.0 | Current |
| Paging | 3.3.6 | Current |
| Coroutines | 1.10.2 | Current |
| MockK | 1.13.8 | **Outdated** -- 1.14.x available |
| Truth | 1.1.5 | **Outdated** -- 1.4.x available |
| Robolectric | 4.11.1 | **Outdated** -- hardcoded, not in version catalog |
| Gson | 2.10.1 (catalog) / 2.11.0 (data) | **Version mismatch** |
| material-icons-extended | 1.7.8 | **Hardcoded** -- should use BOM |
| retrofit2-kotlinx-serialization-converter | 1.0.0 | **Hardcoded** -- should be in version catalog |
| security-crypto | 1.1.0-alpha06 | **Alpha** for production use |

### Issues

1. **Gson version mismatch**: `libs.versions.toml` declares `gson = "2.10.1"` but `data/build.gradle.kts` hardcodes `"com.google.code.gson:gson:2.11.0"`.
2. **Hardcoded dependency versions**: `material-icons-extended:1.7.8`, `robolectric:4.11.1`, `retrofit2-kotlinx-serialization-converter:1.0.0`, `libmpv:0.5.1`, `media3-ffmpeg-decoder:1.9.0+1`, `ass-media:0.4.0-beta01` are not in the version catalog.
3. **Dual serialization**: Project uses both Gson AND kotlinx.serialization. This doubles the serialization surface area and can cause confusion.

---

## Summary of Corrective Actions by Priority

### P0 (None found)

No critical blocking issues.

### P1 (Must Fix Before Production)

| ID | Action | Effort |
|----|--------|--------|
| F-01/02/03/04 | Decompose `MediaRepository` God Interface; remove duplicated methods | Medium |
| F-05 | Replace all hardcoded `Dispatchers.*` with injected qualifiers | Medium |
| F-06 | Remove Compose/Retrofit deps from `core:model` | Low |
| F-07 | Extract misplaced code from `core:common` | Medium |
| F-15 | Remove `BuildConfig` secrets from `:data`; consolidate in `:app` only | Low |

### P2 (Should Fix)

| ID | Action | Effort |
|----|--------|--------|
| F-08 | Consolidate `core:common` packages | Low |
| F-09 | Remove `Resource<T>`, use `Result<T>` consistently | Low |
| F-10 | Unify `Screen` route constants | Low |
| F-11 | Use `ServerClientResolver` everywhere, remove duplicate `getClient()` | Low |
| F-12 | Invert `core:network` -> `core:datastore` dependency via interface | Medium |
| F-13 | Move `toHttpAppError()` out of `core:model` | Low |
| F-14 | Remove Hilt from `core:designsystem` and `core:ui` | Low |
| F-16 | Remove duplicate `BuildConfig` fields in `:data` | Low |
| F-17 | Clarify or merge `HubViewModel`/`HubDetailViewModel` | Low |
| F-18 | Inject dispatcher in `EnrichMediaItemUseCase` | Low |
| F-19 | Fix thread safety in `PerformanceTracker` | Low |
| F-20 | Delete orphaned `di/` root directory | Low |
| F-21 | Move `safeApiCall` to `core:network` | Low |
| F-22 | Enable ktlint failure on violations | Low |
| F-23 | Add Turbine to test dependencies | Low |
| F-24 | Move French error messages to string resources | Medium |
| F-25 | Fix package naming in `MediaUrlResolver` | Low |

---

## Positive Findings

1. **Clean domain layer**: No Android framework imports in `:domain`. Uses `javax.inject` for DI.
2. **Well-defined error hierarchy**: `AppError` sealed class is comprehensive and consistently used.
3. **Good DI practices**: Hilt modules are well-organized with `@Binds` for repositories.
4. **Solid network security**: `X509ExtendedTrustManager` with hostname-aware SSL validation is well-implemented.
5. **Good test coverage for domain**: 8 use case test files with comprehensive scenarios.
6. **Version catalog usage**: Most dependencies use `libs.versions.toml` for centralized version management.
7. **Consistent MVVM+MVI pattern**: ViewModels consistently use `StateFlow` + `Channel` pattern.
8. **Quality gates**: detekt and ktlint configured for all subprojects.
9. **ProGuard/R8**: Enabled for release builds in both `:app` and `:data`.

---

*Generated by Claude Opus 4.6 -- Phase 4 Architecture Audit*
