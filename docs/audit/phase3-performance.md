# Phase 3: Performance Audit

**Date**: 2026-03-22
**Auditor**: Performance Audit Agent (Claude Opus 4.6)
**Scope**: Compose recomposition, startup, memory, network, database, scrolling, APK size, player

---

## Executive Summary

PlexHubTV demonstrates strong performance engineering in several critical areas: the database layer is well-indexed with WAL mode and a materialized unified table (Solution C), the image cache is heap-adaptive with proper size hints, Paging3 configurations are sensible, and multi-server calls are already parallelized with `coroutineScope + async`. ABI splits are enabled, R8 minification is on, and the player has network-aware buffer tuning.

The primary performance risks are concentrated in **Compose recomposition** (unstable parameters on key home screen composables, missing `contentType` on the most-scrolled LazyRows, unremembered lambdas in `NetflixContentRow`) and **several UiState classes lacking `@Immutable`** annotations, which together can cause frame drops on low-end TV hardware like the Mi Box S. There is also a significant APK size concern from bundling all 4 ABI architectures in the universal APK, and a few network/database patterns that could be tightened.

**Top 3 user-visible impact areas:**
1. Home screen scroll jank from recomposition of every card on each focus change
2. APK size bloat from universal APK including all 4 ABIs + MPV native libs
3. `PlayerUiState` lacking `@Immutable` causes frequent player UI recompositions during playback

---

## Top 20 Bottlenecks (by user impact)

### Critical

| # | Issue | File:Line | Impact | Fix | Effort |
|---|-------|-----------|--------|-----|--------|
| 1 | `NetflixHomeContent` receives `List<Hub>`, `List<MediaItem>` (unstable types to Compose) for `hubs`, `favorites`, `suggestions`, `onDeck` -- despite `HomeUiState` using `ImmutableList`, `DiscoverScreen` calls `.toList()` on `onDeck` (L148) and passes plain `List<>` typed parameters to `NetflixHomeContent`. Compose compiler treats `List<>` as unstable, forcing full recomposition of the entire home screen on ANY state change. | `NetflixHomeScreen.kt:33-37`, `DiscoverScreen.kt:148` | Every focus change recomposes ALL home rows (10+ rows x 20+ cards). Frame drops on Mi Box S. | Change parameter types to `ImmutableList<MediaItem>` / `ImmutableList<Hub>`. Remove `.toList()` in `DiscoverScreen.kt:148`. | Low |
| 2 | `NetflixContentRow` receives `items: List<MediaItem>` (unstable). This is the most-used composable on the home screen, called once per row. | `NetflixContentRow.kt:36` | Each row recomposes fully when parent recomposes, even if its items haven't changed. | Change to `items: ImmutableList<MediaItem>`. | Low |
| 3 | `PlayerUiState` has no `@Immutable` annotation and contains mutable `List<>` fields (`audioTracks`, `subtitleTracks`, `availableQualities`, `playQueue`). Updated every 1 second by position tracker. | `PlayerUiState.kt:12-70` | Player controls recompose every second (1s position updates). On low-end devices, this causes dropped frames during playback overlay display. | Add `@Immutable`, use `ImmutableList` for list fields, or use `@Stable`. | Medium |
| 4 | `MediaDetailUiState` lacks `@Immutable` and has `List<MediaItem>` for `seasons`, `similarItems`, `collections`, `availablePlaylists`. | `MediaDetailUiState.kt:12-32` | Detail screen recomposes fully on any state change (e.g., enrichment loading toggle). | Add `@Immutable`, use `ImmutableList` for list fields. | Low |
| 5 | `Hub` data class contains `items: List<MediaItem>` and has no `@Immutable`/`@Stable` annotation. Since `Hub` is a composable parameter, Compose cannot skip recomposition. | `core/model/.../Hub.kt:16-23` | Every hub row on home screen is recomposed when ANY hub row changes. | Add `@Immutable` to `Hub`, change `items` to `ImmutableList`. | Low |

### Important

| # | Issue | File:Line | Impact | Fix | Effort |
|---|-------|-----------|--------|-----|--------|
| 6 | `NetflixContentRow` LazyRow is missing `contentType` parameter. All items share the same content type pool, preventing Compose from reusing item compositions efficiently. | `NetflixContentRow.kt:86-88` | Slower LazyRow recycling. Each scroll allocates new compositions instead of reusing. | Add `contentType = { "media_card" }` to the `items()` call. | Trivial |
| 7 | `NetflixHomeContent` LazyColumn items are missing `contentType`. Special rows (continue watching, my list, suggestions) and hub rows all use the same implicit type. | `NetflixHomeScreen.kt:104-177` | Less efficient recycling in the main vertical list. | Add `contentType` to each `item()` block: `"continue_watching"`, `"my_list"`, `"hub_row"`, etc. | Trivial |
| 8 | `onFocus` lambda in `NetflixContentRow.kt:101-103` is recreated on every recomposition. Not wrapped in `remember`. | `NetflixContentRow.kt:101-103` | Lambda allocation on every card recomposition. With 20+ cards per row and 10+ rows, this is hundreds of allocations per home screen recomposition. | Wrap in `remember(item.ratingKey, item.serverId)` like `onClick` and `onPlay` already are. | Trivial |
| 9 | Universal APK bundles all 4 ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`). With MPV native libs + FFmpeg decoder + ASS renderer, this inflates APK size significantly. | `app/build.gradle.kts:133-140` | Universal APK is likely 100MB+. Android TV devices are overwhelmingly ARM64. x86/x86_64 are emulator-only for TV. | Set `isUniversalApk = false` in release, or strip x86/x86_64 from release builds. Distribute per-ABI APKs via Play Store. | Low |
| 10 | `HttpLoggingInterceptor.Level.BODY` in debug builds logs full response bodies for all API calls, including large `/library/sections/{id}/all` responses (can be 1MB+ JSON). | `NetworkModule.kt:109` | Debug build network calls are 10-100x slower due to body logging. Confuses profiling. | Change to `Level.HEADERS` in debug. Use `Level.BODY` only via a runtime debug toggle. | Trivial |
| 11 | No HTTP `Cache-Control` interceptor to force caching for Plex API responses. While OkHttp disk cache is configured (50MB), Plex servers send `no-cache` headers, making the disk cache largely unused. | `NetworkModule.kt:127-137` | Every API call goes to network even for unchanged data. No conditional GET / ETag support. | Add a `CacheControlInterceptor` as a network interceptor that sets `max-age` for specific endpoints (e.g., `/library/sections/*/all` could cache for 5 minutes). | Medium |
| 12 | `DiscoverScreen.kt:148` converts `state.onDeck` (ImmutableList) back to plain `List` via `.toList()` before passing to `NetflixHomeContent`. This defeats the ImmutableList annotation on `HomeUiState`. | `DiscoverScreen.kt:148` | Unnecessary copy + type downgrade causes unstable parameter in Compose. | Remove `.toList()`: pass `state.onDeck` directly. | Trivial |

### Optimization

| # | Issue | File:Line | Impact | Fix | Effort |
|---|-------|-----------|--------|-----|--------|
| 13 | `FallbackAsyncImage` creates a new `ImageRequest` on every recomposition (not keyed/remembered). The `listener` lambdas capture outer state. | `FallbackAsyncImage.kt:62-84` | Minor: Coil deduplicates identical requests internally. But the builder allocation is unnecessary. | Wrap `ImageRequest.Builder(...).build()` in `remember(currentUrl, imageWidth, imageHeight)`. | Low |
| 14 | `HomeViewModel` has 4 separate `launchIn(viewModelScope)` calls for settings preferences (`observeHomeRowPreferences`), each updating state independently. Four rapid emissions = four separate state updates = four recompositions. | `HomeViewModel.kt:216-229` | Minor: Quick successive state updates on settings change. | Combine into a single `combine()` flow for all 4 preference flows, emitting one merged update. | Low |
| 15 | `data class SpecialRow` defined inside `NetflixHomeContent` composable function (L60) is re-created on every recomposition. | `NetflixHomeScreen.kt:60` | Minor allocation overhead per recomposition. Data class inside composable creates short-lived garbage. | Move `SpecialRow` outside the composable or remove it (it's defined but never actually used as a type; `rowAvailability` map is used instead). | Trivial |
| 16 | `searchMedia` query uses `LIKE '%query%'` which forces a full table scan. FTS alternative `searchMediaFts` exists but both are present, suggesting the LIKE version may still be called as a fallback. | `MediaDao.kt:115-119` | Slower search on large libraries (10K+ items). | Ensure only `searchMediaFts` is called in production code paths. Remove or deprecate the LIKE-based version. | Low |
| 17 | `PlayerController.loadMedia()` calls `settingsRepository.getVideoQuality().first()`, `settingsRepository.playerEngine.first()`, `settingsRepository.clientId.first()` in parallel via `coroutineScope + async`, which is good. But `settingsRepository.deinterlaceMode.first()` (L895) and `settingsRepository.skipIntroMode.first()` (L1163) are called synchronously later. | `PlayerController.kt:783-789, 895, 1163` | Minor: Extra DataStore reads during playback load. | Include `deinterlaceMode` in the initial parallel settings fetch. Skip mode reads are already optimized with 10s caching. | Low |
| 18 | `LibraryRepositoryImpl` PagingConfig sets `maxSize = 800` for unified queries. With rich MediaItem objects, 800 items in memory can be 50-100MB on detailed pages. | `LibraryRepositoryImpl.kt:218` | Potential memory pressure on low-RAM devices (Mi Box S has 2GB). | Consider reducing to `maxSize = 400` or `maxSize = 500`. | Trivial |
| 19 | `AnimatedBackground` composable in `DiscoverScreen.kt:256-291` loads a full-size `AsyncImage` with no size constraints for the backdrop blur effect, despite being displayed at 15% opacity. | `DiscoverScreen.kt:264-271` | Loads full-resolution backdrop image (potentially 1920x1080) just for a 15% opacity background. | Add `.size(640, 360)` to the ImageRequest to load a downscaled version. | Trivial |
| 20 | `setQueryCallback` on Room database builder is always registered (even in release), though the callback body is a no-op. The callback mechanism itself adds a small overhead per query. | `DatabaseModule.kt:762-764` | Negligible per-query overhead from the callback dispatch mechanism. | Wrap in `if (BuildConfig.DEBUG)` to skip registration entirely in release. | Trivial |

---

## Detailed Analysis

### 3.1 Compose Recomposition

**Findings:**

The codebase shows awareness of Compose stability with `@Immutable` on `HomeUiState`, `HubUiState`, and `MediaItem`. `kotlinx-collections-immutable` is used for `ImmutableList` in `HomeUiState`. However, several critical gaps exist:

1. **Parameter type mismatch**: `NetflixHomeContent` declares parameters as `List<Hub>` and `List<MediaItem>` instead of `ImmutableList<>`. Even though the ViewModel stores `ImmutableList`, the composable signature determines stability. Compose sees `List<>` as unstable.

2. **`.toList()` anti-pattern**: `DiscoverScreen.kt:148` calls `state.onDeck.toList()`, which creates a new `List` reference on every recomposition, defeating the `ImmutableList` storage.

3. **Missing `@Immutable` on 20+ UiState classes**: Only `HomeUiState` and `HubUiState` have `@Immutable`. The remaining 20+ UiState data classes (including `PlayerUiState`, `MediaDetailUiState`, `SearchUiState`, `LibraryUiState`, `SettingsUiState`) lack it. This means any state update triggers full subtree recomposition.

4. **`Hub` class instability**: `Hub` contains `items: List<MediaItem>` and no stability annotation. Since hubs are passed into composables, every hub row on the home screen is considered unstable.

**What's done well:**
- `NetflixContentRow` uses `remember` for `onClick`, `onPlay`, and `longPress` lambdas (L90-93) with proper keys
- `NetflixMediaCard` uses `remember` for `imageUrl` (L161)
- `LazyRow` in `NetflixContentRow` has proper `key` (composite `ratingKey_serverId`)
- `LibrariesScreen` uses `derivedStateOf` extensively to minimize recompositions
- `NetflixDetailScreen` uses `contentType` on all its LazyRow items

### 3.2 Startup Time

**Findings:**

`MainActivity.onCreate()` is lean:
- Requests notification permission (non-blocking)
- Calls `enableEdgeToEdge()`
- Sets Compose content with theme collection

The actual work happens in:
- **Splash screen**: `SplashViewModel` checks auto-login (DataStore read)
- **Loading screen**: `LoadingViewModel` waits for LibrarySyncWorker
- **`PlexHubApplication`**: Schedules initial sync worker and periodic workers

**What's done well:**
- Work scheduling uses `ExistingPeriodicWorkPolicy.KEEP` (doesn't restart existing work)
- Initial sync is a one-time work, not re-enqueued on every launch
- Theme is loaded via `collectAsState(initial = "Plex")` with a sensible default
- No heavy computation on the main thread in `onCreate()`

**Minor concern:**
- `PlexHubApplication` schedules 4+ periodic workers at app startup. While each individual worker registration is fast, the cumulative work of scheduling + the initial sync can add latency to reaching the first interactive frame.

### 3.3 Memory

**Findings:**

1. **Image cache (excellent)**: `ImageModule` uses heap-adaptive sizing (`maxMemory * 0.20`, clamped to 32-256MB). This is significantly better than the common anti-pattern of using `Runtime.getRuntime().totalMemory()` or system RAM percentage. Disk cache is 256MB. `Precision.INEXACT` allows Coil to downscale.

2. **PagingConfig**: Three configurations found:
   - Library unified: `pageSize=50, prefetchDistance=15, initialLoadSize=100, maxSize=800` - reasonable
   - Library non-unified: same config (from code structure)
   - Watch history: `pageSize=20, initialLoadSize=40, enablePlaceholders=false` - no `maxSize`, so items accumulate forever

3. **`PlayerUiState.availableQualities`**: Default list of 7 `VideoQuality` objects is recreated on every `PlayerUiState()` construction. Since `PlayerUiState` is copied frequently (position tracking every 1s), this creates garbage.

4. **Image connection pool**: `ImageModule` uses `ConnectionPool(4, 5, TimeUnit.MINUTES)` which is conservative for a TV app loading many posters simultaneously. Could be increased to 8 connections.

**What's done well:**
- `FallbackAsyncImage` specifies explicit `imageWidth`/`imageHeight` for size hints
- LeakCanary is debug-only dependency
- Coil `PlexImageKeyer` is used for proper cache deduplication
- `ConcurrentHashMap` cache in `EnrichMediaItemUseCase` prevents redundant enrichments

### 3.4 Network

**Findings:**

1. **HTTP disk cache configured but likely underused**: 50MB OkHttp cache is configured, but Plex servers typically return `no-cache` or `no-store` headers. Without a `CacheControlInterceptor` to override these headers for specific endpoints, the cache provides limited benefit.

2. **Multi-server parallelism (excellent)**: `ServerClientResolver.getActiveClients()`, `HubsRepositoryImpl`, `SearchRepositoryImpl`, `SyncRepositoryImpl`, and `PlaylistRepositoryImpl` all use `coroutineScope + async` for parallel server calls.

3. **Debug logging overhead**: `HttpLoggingInterceptor.Level.BODY` in debug logs entire response bodies. For library sync responses (potentially 1MB+ JSON), this adds significant overhead and can cause ANRs in debug builds.

4. **Image retry disabled wisely**: `ImageModule` sets `retryOnConnectionFailure(false)` because `FallbackAsyncImage` handles URL-level fallback. This prevents double retry (OkHttp + application).

5. **Connection pool tuning**: Default OkHttp pool is `ConnectionPool(5, 5, TimeUnit.MINUTES)`. For an app that connects to multiple Plex servers + TMDb + OMDb + OpenSubtitles, this is adequate.

### 3.5 Database

**Findings:**

The database layer is well-engineered:

1. **WAL mode**: Explicitly enabled via `setJournalMode(WRITE_AHEAD_LOGGING)`. This allows concurrent reads during writes.

2. **PRAGMA tuning**: `synchronous = NORMAL` and `cache_size = -8000` (8MB) are set in `onOpen()` callback. Good trade-off between safety and performance.

3. **Comprehensive indexing**: 20+ indexes covering all major query patterns:
   - Composite indexes for complex queries (e.g., `(type, grandparentTitle, parentIndex, index)` for episode lookup)
   - FTS4 virtual table for full-text search
   - Materialized `media_unified` table (Solution C) eliminates expensive GROUP BY on every library page load

4. **Pre-computed columns**: `displayRating`, `metadataScore`, `groupKey`, `historyGroupKey`, `titleSortable` are all pre-computed at write time, avoiding runtime computation in queries.

5. **`@RawQuery` usage**: Used for dynamic paging queries (`getMediaPagedRaw`, `getPagedUnified`). Index usage depends on the generated SQL; the `MediaLibraryQueryBuilder` (not fully audited) should ensure indexes are hit.

6. **Minor concern**: `getWatchHistoryPaged()` returns a PagingSource with no `maxSize` in PagingConfig, allowing unbounded growth.

### 3.6 Scrolling

**Findings:**

1. **`NetflixContentRow` LazyRow**:
   - Has `key = { "${it.ratingKey}_${it.serverId}" }` - correct composite key
   - Missing `contentType` - all items share one pool
   - `horizontalArrangement = Arrangement.spacedBy(8.dp)` - good spacing
   - Proper `focusGroup()` for D-Pad navigation

2. **`NetflixHomeContent` LazyColumn**:
   - Has per-item `key` (`"continue_watching"`, `"my_list"`, hub identifiers) - correct
   - Missing `contentType` on items
   - Uses snap-to-row scrolling via `scrollToItem(focusedRowIndex)` on every focus change
   - `focusVersion` counter increments on EVERY focus event (including horizontal navigation within same row), causing unnecessary vertical scroll snaps

3. **`NetflixDetailScreen` LazyRows**:
   - All LazyRow `items()` calls use both `key` and `contentType` - excellent
   - Seasons: `contentType = { "season" }`, Similar: `contentType = { "media" }`, etc.

4. **`LibrariesScreen`**:
   - Uses `contentType = pagedItems.itemContentType { "media_item" }` - correct
   - Uses `derivedStateOf` extensively to minimize recompositions

5. **Image sizing**: `FallbackAsyncImage` accepts `imageWidth`/`imageHeight` parameters and passes them to Coil's `ImageRequest.Builder().size()`. Poster cards use 300x450, wide cards use 540x304. This is good for preventing oversized decodes.

### 3.7 APK Size

**Findings:**

1. **ABI splits enabled**: `isEnable = true` with all 4 ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) and `isUniversalApk = true`. Per-ABI APKs will be smaller, but the universal APK includes ALL native libraries.

2. **R8/minification**: `isMinifyEnabled = true` and `isShrinkResources = true` for release builds - correct.

3. **Native library overhead**: The app bundles:
   - MPV (`dev.jdtech.mpv:libmpv:0.5.1`) - large native library (~15MB per ABI)
   - FFmpeg decoder (`org.jellyfin.media3:media3-ffmpeg-decoder`) - large native library (~10MB per ABI)
   - ASS subtitle renderer (`io.github.peerless2012:ass-media`) - native library (~3MB per ABI)
   - Conscrypt (`org.conscrypt:conscrypt-android`) - native library (~3MB per ABI)

   With 4 ABIs, the universal APK carries ~120MB of native libraries alone.

4. **Material Icons Extended**: `androidx.compose.material:material-icons-extended` is a large dependency (~2MB). R8 should tree-shake unused icons, but this depends on proper ProGuard rules.

5. **Compose UI Tooling Preview**: `implementation(libs.androidx.compose.ui.tooling.preview)` should be `debugImplementation` to exclude preview code from release.

### 3.8 Player

**Findings:**

1. **Buffer configuration (excellent)**:
   - **LAN**: `minBuffer=15s, maxBuffer=30s, playbackBuffer=2s, rebufferBuffer=4s` - balanced
   - **Relay/Remote**: `minBuffer=28s, maxBuffer=30s, playbackBuffer=2.5s, rebufferBuffer=5s` with 2s gap to keep TCP alive
   - Network-aware: `isLikelyRelay()` checks cached URL for private IP patterns

2. **Codec pre-flight (excellent)**: Proactively checks audio/video codecs before playback and routes to MPV if ExoPlayer would fail. Cached `MediaCodecList` avoids 100-300ms queries.

3. **Position tracking**: 1-second tick with `settingsRepository` reads cached every 10 ticks. Good optimization to avoid per-tick DataStore access.

4. **Player reuse**: `playNext()` reuses existing ExoPlayer instance, only resetting per-episode state. Avoids expensive player recreation for episode-to-episode transitions.

5. **Settings parallelism**: `loadMedia()` fetches quality, engine, and clientId in parallel via `coroutineScope + async`.

6. **Concern - Seek verification**: Both ExoPlayer and MPV paths include a 500ms-delayed seek verification (`delay(500)` + position check). This is diagnostic-only and logs but doesn't take corrective action, which is appropriate.

7. **Concern - Release on applicationScope**: `release()` fires player release on `applicationScope` to avoid blocking `onCleared()`. This is a good pattern but risks the release outliving the activity if `applicationScope` is long-lived.

---

## Strengths

1. **Database architecture**: The materialized `media_unified` table (Solution C) is a sophisticated optimization that pre-computes aggregation, avoiding expensive GROUP BY + JOIN on every library page load. Combined with 20+ targeted indexes, the database layer is production-grade.

2. **Image loading**: Heap-adaptive cache sizing, explicit size hints in all image requests, fallback URL chain via `FallbackAsyncImage`, and `Precision.INEXACT` for efficient decoding.

3. **Multi-server parallelism**: All multi-server operations (search, hubs, sync, playlists) use `coroutineScope + async` for parallel execution.

4. **Player engineering**: Network-aware buffer tuning, codec pre-flight routing to MPV, MediaCodecList caching, shared OkHttpClient across sessions, and structured concurrency with `SupervisorJob` + session scoping.

5. **ImmutableList usage in HomeUiState**: The most important UiState class uses `kotlinx.collections.immutable` and `@Immutable`, showing awareness of Compose stability.

6. **Paging3 with proper configuration**: `enablePlaceholders = true` for smooth scroll bars, reasonable `prefetchDistance`, and `maxSize` bounds.

7. **ABI splits**: Enabled for per-architecture APKs, reducing download size for Play Store distribution.

8. **Lambda memoization in NetflixContentRow**: `onClick`, `onPlay`, and `longPress` are all wrapped in `remember` with proper keys, preventing unnecessary recompositions of `NetflixMediaCard`.

---

## Summary

| Category | Grade | Notes |
|----------|-------|-------|
| Compose Recomposition | C+ | Strong patterns in some areas (remember, derivedStateOf, @Immutable on HomeUiState), but critical gaps in parameter types and missing annotations on 20+ UiState classes |
| Startup Time | A- | Lean MainActivity, deferred work scheduling, sensible defaults |
| Memory | A- | Heap-adaptive image cache, proper Paging maxSize (mostly), LeakCanary in debug |
| Network | B | Parallelized multi-server calls, but HTTP cache is underused and debug logging is heavy |
| Database | A | WAL + PRAGMA tuning, 20+ indexes, FTS, materialized unified table, pre-computed columns |
| Scrolling | B+ | Proper keys everywhere, contentType on detail screen, but missing on home screen LazyRows |
| APK Size | B- | ABI splits enabled, R8 on, but universal APK is bloated with 4 ABIs of native libs |
| Player | A | Network-aware buffers, codec pre-flight, session scoping, reuse across episodes |

**Overall: B+** -- The infrastructure is well-engineered. The highest-impact fixes (Issues 1-5) are all low-effort Compose stability annotations and type changes that would meaningfully improve frame rates on the Mi Box S and similar low-end Android TV hardware.
