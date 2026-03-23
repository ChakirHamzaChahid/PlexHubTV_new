# Phase 4: Architecture & Code Quality Audit

> **Date**: 2026-03-22
> **Branch**: `claude/continue-plexhubtv-refactor-YO43N`
> **Auditor**: Architecture Audit Agent (Claude Opus 4.6)

---

## Executive Summary

PlexHubTV demonstrates a **well-structured Clean Architecture** across its multi-module Android TV project. The domain module is genuinely pure Kotlin (zero Android framework imports), the Strategy pattern for multi-source media handling (`MediaSourceHandler`) is exemplary, and the project leverages a modern version catalog for dependency management. Error handling follows a comprehensive typed hierarchy (`AppError`), and the codebase has grown its test suite significantly since the initial refactor.

Key areas for improvement include: inconsistent ViewModel base class adoption (only 5 of 34 ViewModels use `BaseViewModel`), 5 hardcoded dependencies outside the version catalog, direct Firebase SDK usage in ViewModels (tight coupling), and the `domain/build.gradle.kts` declaring `androidx.core.ktx` as a dependency despite never importing it.

**Overall Grade: B+**

---

## Module Health Scores

| Module | Score | Key Issues | Strengths |
|--------|-------|------------|-----------|
| `:domain` | **A** | Unnecessary `androidx.core.ktx` dep in build.gradle | Zero Android imports, pure Kotlin, clean interfaces |
| `:core:model` | **A-** | `@Immutable` from Compose in `MediaItem.kt` | Lean dependencies, strong domain modeling |
| `:core:network` | **A-** | Hardcoded `javax.inject:1` outside catalog | Well-isolated API layer, Hilt scoping |
| `:core:database` | **A** | None significant | Clean DAO layer, proper schema exports |
| `:core:common` | **A** | None significant | Good dispatcher qualifiers, Timber via `api` |
| `:core:datastore` | **A-** | Hardcoded `javax.inject:1` outside catalog | Proper encrypted/unencrypted split |
| `:core:navigation` | **A** | None significant | Minimal dependency footprint |
| `:core:designsystem` | **A** | None significant | Clean theme abstraction |
| `:core:ui` | **B+** | Hardcoded `material-icons-extended:1.7.8` | Good reusable component library |
| `:data` | **B+** | Hardcoded Robolectric version; some catch swallowing | Excellent Strategy pattern, clean DI |
| `:app` | **B** | BaseViewModel inconsistency, Firebase coupling, broad exception catching | Good ViewModel decomposition, comprehensive DI |

---

## Detailed Findings

### 4.1 Clean Architecture Violations

**Domain Module Purity: PASS**

The `:domain` module contains **zero** `import android.*` or `import androidx.*` statements in its source code. All 59 files under `domain/src/main/java/` are pure Kotlin, using only:
- `javax.inject` for DI annotations
- `kotlinx.coroutines` for async
- `com.chakir.plexhubtv.core.model` for domain entities
- `com.chakir.plexhubtv.core.common` for shared utilities
- `timber.log.Timber` for logging

This is excellent adherence to the Clean Architecture principle that the domain layer should have no framework dependencies.

**Issue D-1: Phantom `androidx.core.ktx` dependency in domain build.gradle**

File: `domain/build.gradle.kts`, line 49:
```kotlin
implementation(libs.androidx.core.ktx)
```
This dependency is declared but never used (grep confirms zero `import androidx.core` in domain sources). It introduces an unnecessary Android framework dependency into a module that should be pure Kotlin. **Severity: Low** (no functional impact, but misleads about module purity).

**Issue M-1: Compose annotation in core:model**

File: `core/model/src/main/java/com/chakir/plexhubtv/core/model/MediaItem.kt`, line 3:
```kotlin
import androidx.compose.runtime.Immutable
```
The `@Immutable` annotation from Compose is used on `MediaItem`, the primary domain entity. While this improves Compose recomposition performance, it creates a Compose dependency in what should be a framework-agnostic model module. The `build.gradle.kts` properly declares this via `libs.androidx.compose.runtime.annotation`, so this is a deliberate tradeoff. **Severity: Low** (pragmatic optimization, but technically leaks UI concern into model).

**Business Logic in :app -- PASS**

No business logic was found misplaced in the `:app` module. All use cases reside in `:domain`, all repositories in `:data`. The `app/di/` directory contains only Hilt wiring modules (`ImageModule`, `AppModule`, `WorkModule`, `FirebaseModule`, `AppScopeModule`). The `app/di/network/` and `app/di/image/` packages contain infrastructure-level code (connection management, image loading) that appropriately lives in the presentation layer.

### 4.2 Module Boundaries

**Dependency Graph (from build.gradle.kts analysis):**

```
:app --> :domain, :data, :core:model, :core:common, :core:network,
         :core:navigation, :core:database, :core:datastore,
         :core:designsystem, :core:ui

:data --> :domain, :core:model, :core:common, :core:network,
          :core:database, :core:datastore

:domain --> :core:model, :core:common

:core:ui --> :core:model, :core:navigation, :core:designsystem, :core:common
:core:network --> :core:model, :core:common
:core:database --> :core:model, :core:common
:core:datastore --> :core:common
:core:common --> :core:model
:core:navigation --> (Compose only, no project deps)
:core:designsystem --> (Compose only, no project deps)
:core:model --> (Kotlin Serialization only)
```

**Circular Dependencies: NONE FOUND**

The dependency graph is strictly acyclic. Dependencies flow downward as documented in `ARCHITECTURE.md`.

**Issue B-1: :app depends on :core:network and :core:database directly**

The `:app` module declares `implementation(project(":core:network"))` and `implementation(project(":core:database"))`. While this is sometimes necessary for Hilt module wiring (e.g., `ConnectionManager` needs network types, `ImageModule` needs `AuthInterceptor`), it creates a pathway for presentation-layer code to bypass the data layer. In practice, the app module appears to use these only for DI wiring and worker implementations, which is acceptable.

**Issue B-2: ViewModels directly import core:datastore**

Three ViewModels directly depend on `SettingsDataStore` from `:core:datastore`:
- `SplashViewModel.kt`
- `LibrarySelectionViewModel.kt`
- `LoadingViewModel.kt`

Additionally, `HomeViewModel` imports `SettingsDataStore` via a fully-qualified constructor parameter. These should ideally go through a `:domain` repository interface (`SettingsRepository`) for consistency, though the current approach works.

**DI Module Placement: GOOD**

- Repository bindings in `data/di/RepositoryModule.kt` (correct -- data layer owns implementations)
- Source handler bindings in `data/di/SourceHandlerModule.kt` (correct -- uses `@IntoSet` multibinding)
- App-level DI in `app/di/` (correct -- presentation infrastructure)

### 4.3 Code Duplication

**Source Handlers: MINIMAL DUPLICATION (Good)**

The four `MediaSourceHandler` implementations (`PlexSourceHandler`, `JellyfinSourceHandler`, `XtreamSourceHandler`, `BackendSourceHandler`) share a similar Room-first-then-API pattern, but the specifics differ enough to justify separate implementations:
- Plex: connection retry with cache invalidation, override merging
- Jellyfin: relative URL resolution, tick-to-ms conversion
- Xtream: VOD enrichment on first access, virtual season building
- Backend: pre-enriched content, season parsing from rating keys

The `MediaSourceResolver` (18 lines) cleanly routes to handlers via Hilt multibinding. This is well-designed.

**Issue DUP-1: Similar cache patterns across handlers**

`PlexSourceHandler`, `JellyfinSourceHandler`, and `XtreamSourceHandler` each have their own `ConcurrentHashMap<String, Pair<Long, ...>>` cache with TTL logic. This could be extracted to a generic `TtlCache<K, V>` utility.

Files:
- `data/src/main/java/.../source/PlexSourceHandler.kt` (lines 29-31)
- `data/src/main/java/.../source/JellyfinSourceHandler.kt` (lines 31-32)
- `data/src/main/java/.../source/XtreamSourceHandler.kt` (lines 30-31)

**Mappers: ACCEPTABLE DIVERGENCE**

The six mappers (`MediaMapper`, `JellyfinMapper`, `BackendMediaMapper`, `XtreamMediaMapper`, `ServerMapper`, `UserMapper`) each handle source-specific DTO structures. While `MediaMapper` (Plex) and `JellyfinMapper` both map to `MediaEntity`, they handle fundamentally different source formats (Plex XML/JSON vs Jellyfin REST). The `computeMetadataScore()` call is shared via `core:database`, which is appropriate.

**ViewModel Patterns: INCONSISTENT BASE CLASS**

Only **5 of 34 ViewModels** extend `BaseViewModel`:
- `HomeViewModel`
- `LibraryViewModel`
- `HubViewModel`
- `SearchViewModel`
- `MediaDetailViewModel`

The remaining **29 ViewModels** extend `ViewModel()` directly. `BaseViewModel` provides a standardized error channel (`Channel<AppError>`). ViewModels that handle errors but do NOT use `BaseViewModel` include `FavoritesViewModel`, `HistoryViewModel`, `SettingsViewModel`, `AuthViewModel`, and others. These either swallow errors silently or use ad-hoc error handling.

### 4.4 Naming Consistency

**Naming Conventions: CONSISTENT (Good)**

| Pattern | Convention Used | Consistent? |
|---------|----------------|-------------|
| ViewModels | `*ViewModel` suffix | Yes (all 34) |
| Use Cases | `*UseCase` suffix | Yes (all 22) |
| Repositories | `*Repository` / `*RepositoryImpl` | Yes (all 25+ pairs) |
| Entities | `*Entity` suffix | Yes |
| DTOs | `*DTO` / `*Dto` suffix | Mixed (see below) |

**Issue N-1: DTO suffix inconsistency**

Plex DTOs use uppercase `DTO` (`MetadataDTO`, `StreamDTO`), while Jellyfin and Xtream DTOs use `Dto` (`JellyfinItem`, `XtreamVodStreamDto`, `XtreamSeriesDto`). The Jellyfin DTOs don't even use a `Dto` suffix consistently (`JellyfinItem`, `JellyfinMediaSource`, `JellyfinMediaStream`).

**Id vs ID / Url vs URL: CONSISTENT**

The codebase consistently uses camelCase abbreviations: `imdbId` (not `imdbID`), `tmdbId`, `serverId`, `thumbUrl`, `baseUrl`, `artUrl`. This is the Kotlin convention and is applied uniformly across all modules.

### 4.5 Dead Code

**TODOs Found (5):**

| File | TODO | Priority |
|------|------|----------|
| `PlaybackManager.kt:80` | `// TODO: actually shuffle playQueue when enabling` | P2 |
| `AppProfileSelectionScreen.kt:98` | `// TODO: Show error snackbar` | P1 |
| `FavoritesScreen.kt:71` | `// TODO: Get from ViewModel/UiState` | P2 |
| `MediaDetailViewModel.kt:218` | `// TODO: Download feature not implemented` | P3 |
| `MediaDetailScreen.kt:268` | `// TODO: Download button hidden` | P3 |

**Commented-Out Code: MINIMAL**

No significant commented-out code blocks were found. The codebase is clean in this regard.

**Silent Exception Swallowing: MODERATE CONCERN**

Found **46 instances** of `catch (_: Exception) {}` or `catch (_: Exception) { }` that silently swallow exceptions. While many are justifiable (e.g., `focusRequester.requestFocus()` which can throw on initial composition), some are concerning:

- `LibrarySyncWorker.kt` lines 124, 261, 341 -- silently swallowing sync errors
- `ServerNameResolver.kt` lines 44, 51, 58 -- three consecutive silent catches
- `NetworkModule.kt` line 60 -- silently catching network init errors

### 4.6 Dependency Health

**Version Catalog: WELL MANAGED**

The project uses `gradle/libs.versions.toml` with 47 version entries and proper BOM usage for Compose (`composeBom = "2026.01.00"`) and Firebase (`firebaseBom = "34.9.0"`). Versions are current:
- AGP 9.0.1, Kotlin 2.2.10, Room 2.8.4, Hilt 2.58, Coroutines 1.10.2

**Issue DEP-1: 5 hardcoded dependencies outside version catalog**

File: `app/build.gradle.kts`:
```kotlin
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")  // line 182
implementation("dev.jdtech.mpv:libmpv:0.5.1")                                                // line 198
implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")                          // line 201
implementation("io.github.peerless2012:ass-media:0.4.0-beta01")                              // line 202
implementation("androidx.compose.material:material-icons-extended:1.7.8")                    // line 226
```

File: `core/ui/build.gradle.kts`:
```kotlin
implementation("androidx.compose.material:material-icons-extended:1.7.8")                    // line 76
```

File: `data/build.gradle.kts`:
```kotlin
testImplementation("org.robolectric:robolectric:4.11.1")                                     // line 101
```

File: `app/build.gradle.kts`:
```kotlin
testImplementation("org.robolectric:robolectric:4.11.1")                                     // line 233
```

These should be moved to `libs.versions.toml` for centralized version management.

**Issue DEP-2: `material-icons-extended` version mismatch risk**

Both `:app` and `:core:ui` hardcode `material-icons-extended:1.7.8`, which may drift from the Compose BOM version (`2026.01.00`). This should use the BOM-managed version instead.

**Issue DEP-3: `javax.inject:javax.inject:1` hardcoded in 3 modules**

Files: `domain/build.gradle.kts`, `core/network/build.gradle.kts`, `core/datastore/build.gradle.kts`

This should be in the version catalog.

**Static Analysis Tooling: GOOD**

Both detekt and ktlint are configured project-wide via `build.gradle.kts` `subprojects {}` block. However, `ignoreFailures.set(true)` for ktlint means linting violations won't fail the build.

**LeakCanary: PROPERLY SCOPED**

`debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")` is correctly debug-only.

### 4.7 Error Handling

**AppError Hierarchy: COMPREHENSIVE (Grade: A)**

File: `core/model/src/main/java/.../AppError.kt`

The sealed class hierarchy covers 7 error categories with 22 specific error types:
- `Network` (5 types: NoConnection, Timeout, ServerError, NotFound, Unauthorized)
- `Auth` (5 types: InvalidToken, SessionExpired, NoServersFound, PinGenerationFailed, InvalidCredentials)
- `Media` (4 types: NotFound, LoadFailed, NoPlayableContent, UnsupportedFormat)
- `Playback` (4 types: InitializationFailed, StreamingError, CodecNotSupported, DrmError)
- `Search` (3 types: QueryTooShort, NoResults, SearchFailed)
- `Storage` (3 types: DiskFull, ReadError, WriteError)
- `Unknown`, `Validation`

**Extension Functions: WELL DESIGNED**

File: `core/model/src/main/java/.../ErrorExtensions.kt`

Three utility extensions provide:
- `toUserMessage()`: French-language user-facing messages (exhaustive when-matching)
- `isCritical()`: Identifies errors requiring user action
- `isRetryable()`: Identifies transient errors

The `toAppError()` extension on `Throwable` correctly maps Java exceptions to typed `AppError` instances.

**Issue E-1: Broad `catch (e: Exception)` usage**

Found **50+ instances** of `catch (e: Exception)` across the codebase. While many correctly use `toAppError()` or log via Timber, some patterns are concerning:

- `RatingSyncWorker.kt` has 6 `catch (e: Exception)` blocks, some of which increment counters without propagating
- `LibrarySyncWorker.kt` has both logged and silenced catches -- the silenced ones (`catch (_: Exception) {}`) can mask data corruption during sync

**Issue E-2: Firebase direct usage in ViewModels**

Firebase Analytics and Crashlytics are imported directly in 5 files:
- `AuthViewModel.kt` -- `Firebase.analytics.logEvent`
- `MediaDetailViewModel.kt` -- `Firebase.analytics.logEvent`
- `SearchViewModel.kt` -- `Firebase.analytics.logEvent`
- `DebugViewModel.kt` -- `FirebaseCrashlytics`
- `PlayerController.kt` -- `FirebaseCrashlytics`

These should be abstracted behind an `AnalyticsService` interface in `:domain` with a Firebase implementation in `:data` or `:app`. Direct Firebase usage in ViewModels creates tight coupling and makes testing harder.

**Result<T> Usage: CONSISTENT**

Repository interfaces in `:domain` consistently use `Result<T>` for fallible operations. The `MediaSourceHandler` interface and all implementations use `Result<MediaItem>` and `Result<List<MediaItem>>`. This is well-standardized.

### 4.8 Test Infrastructure

**Current Test Count: 38 files**

The test suite has grown significantly since the initial refactor. The `MISSING_TESTS.md` document is now partially outdated -- many listed tests have been restored:

| Layer | Test Files | Status |
|-------|-----------|--------|
| `:app` (ViewModels) | 12 | HomeViewModelTest, LibraryViewModelTest, SearchViewModelTest, MediaDetailViewModelTest, PlayerControlViewModelTest, PlaybackStatsViewModelTest, TrackSelectionViewModelTest, PlexHomeSwitcherViewModelTest, AppProfileViewModelTest, PlaylistListViewModelTest, PlaylistDetailViewModelTest, MainViewModelTest |
| `:app` (Controllers) | 4 | ChapterMarkerManagerTest, PlayerStatsTrackerTest, PlayerTrackControllerTest, PlayerScrobblerTest |
| `:app` (Other) | 2 | GlobalCoroutineExceptionHandlerTest, SyncStatusModelTest |
| `:domain` (UseCases) | 8 | GetMediaDetailUseCaseTest, GetUnifiedHomeContentUseCaseTest, PrefetchNextEpisodeUseCaseTest, SearchAcrossServersUseCaseTest, SyncWatchlistUseCaseTest, ToggleFavoriteUseCaseTest, EnrichMediaItemUseCaseTest, FilterContentByAgeUseCaseTest |
| `:domain` (Services) | 1 | PlaybackManagerTest |
| `:data` | 3 | MediaUrlResolverTest, ProfileRepositoryImplTest, MediaMapperTest, MediaLibraryQueryBuilderTest |
| `:core:model` | 2 | AppErrorTest, UnificationIdTest |
| `:core:common` | 1 | StringNormalizerTest |
| `:core:network` | 3 | NetworkSecurityTest, AuthEventBusTest, AuthInterceptorTest, ConnectionManagerTest |

**Tests Still Missing (Priority):**

| Priority | Test | Reason |
|----------|------|--------|
| P1 | `JellyfinMapperTest` | New mapper, untested |
| P1 | `XtreamMediaMapperTest` | Complex mapping logic, untested |
| P1 | `MediaDetailRepositoryImplTest` | Critical repository, core data path |
| P1 | `PlaybackRepositoryImplTest` | URL resolution, transcoding |
| P2 | `JellyfinSourceHandlerTest` | New source handler |
| P2 | `AggregationServiceTest` | Multi-source deduplication logic |
| P2 | `SettingsViewModelTest` | Complex preference management |
| P2 | `SeasonDetailViewModelTest` | Episode playback path |

**Test Stack: MODERN AND APPROPRIATE**

- JUnit 4.13.2 + MockK 1.13.8 + Truth 1.1.5 + Coroutines Test 1.10.2
- Robolectric 4.11.1 for Android-dependent tests
- MockWebServer for network tests
- `unitTests.isReturnDefaultValues = true` prevents Android framework crashes

### 4.9 Additional Observations

**Strategy Pattern (MediaSourceHandler): EXEMPLARY**

The `MediaSourceHandler` interface with `MediaSourceResolver` using Hilt's `@IntoSet` multibinding is the best architectural pattern in the codebase. Adding a new media source (e.g., Emby) requires only:
1. Implementing `MediaSourceHandler`
2. Adding a `@Binds @IntoSet` binding in `SourceHandlerModule`

No existing code needs modification. This is textbook Open/Closed Principle.

**ProGuard Rules: COMPREHENSIVE**

File: `app/proguard-rules.pro`

Covers Retrofit, Gson, Room, Hilt, Media3, MPV, Firebase, Conscrypt, and kotlinx.serialization. The rules are well-organized with section headers.

**Build Configuration: SOLID**

- ABI splits enabled (armeabi-v7a, arm64-v8a, x86, x86_64 + universal)
- API keys loaded from `local.properties` (not committed)
- Release signing from external `keystore.properties`
- R8 minification + resource shrinking for release builds

---

## Priority Actions

| # | Action | Files | Effort | Impact |
|---|--------|-------|--------|--------|
| 1 | Move 5 hardcoded deps to version catalog | `app/build.gradle.kts`, `core/ui/build.gradle.kts`, `data/build.gradle.kts` | Low (30 min) | Medium -- prevents version drift |
| 2 | Remove unused `androidx.core.ktx` from domain | `domain/build.gradle.kts` line 49 | Trivial (5 min) | Low -- module purity |
| 3 | Standardize ViewModel base class | 29 ViewModels extending `ViewModel()` directly | Medium (2-3 hrs) | High -- consistent error handling |
| 4 | Extract Firebase to AnalyticsService interface | 5 files with direct Firebase imports | Medium (2 hrs) | High -- testability, decoupling |
| 5 | Add tests for Jellyfin/Xtream mappers | New test files in `data/src/test/` | Medium (3-4 hrs) | High -- new code coverage |
| 6 | Extract `TtlCache<K,V>` from source handlers | `core/common/`, 3 source handler files | Low (1 hr) | Low -- reduces duplication |
| 7 | Audit silent exception catches in workers | `LibrarySyncWorker.kt`, `RatingSyncWorker.kt` | Low (1 hr) | Medium -- error visibility |
| 8 | Standardize DTO naming convention | Jellyfin DTOs in `core/network/jellyfin/` | Low (30 min) | Low -- consistency |
| 9 | Add `MediaDetailRepositoryImplTest` | `data/src/test/` | Medium (2-3 hrs) | High -- core path coverage |
| 10 | Move `javax.inject:1` to version catalog | 3 module build.gradle.kts files | Trivial (10 min) | Low -- consistency |

---

## Summary

### Strengths
- **Domain purity**: Zero Android imports in `:domain` -- genuine Clean Architecture
- **Strategy pattern**: `MediaSourceHandler` + `MediaSourceResolver` is textbook OCP
- **Error type system**: Comprehensive `AppError` sealed hierarchy with utility extensions
- **DI architecture**: Well-structured Hilt modules with `@Binds`, `@IntoSet`, proper scoping
- **Version catalog**: Modern `libs.versions.toml` with BOM management
- **Test growth**: From 4 surviving tests to 38 test files (significant improvement)
- **Static analysis**: Both detekt and ktlint configured project-wide

### Weaknesses
- **Inconsistent ViewModel base**: Only 5/34 ViewModels use `BaseViewModel`
- **Firebase tight coupling**: Direct SDK usage in 5 ViewModels/controllers
- **Hardcoded dependencies**: 5+ deps outside version catalog
- **Silent exception handling**: 46+ `catch (_: Exception) {}` instances
- **Missing mapper tests**: New Jellyfin and Xtream mappers lack test coverage

### Overall Grade: **B+**

The architecture is sound and well-layered. The main issues are consistency gaps (ViewModel base class, dependency management) rather than structural problems. The Strategy pattern for source handlers and the typed error system demonstrate mature architectural thinking. The project would benefit most from standardizing ViewModel error handling and decoupling Firebase analytics.
