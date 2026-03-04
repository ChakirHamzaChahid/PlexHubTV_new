# Phase 3 - Performance & Optimization Audit

**Project:** PlexHubTV (Android TV Plex Client)
**Date:** 2026-02-25
**Auditor:** Claude Opus 4.6 (Phase 3 Agent)
**Scope:** Compose recomposition, startup time, memory, network, database, scrolling, APK size, player

---

## Top 20 Bottlenecks (Ranked by User-Visible Impact)

---

### P0-01: Duplicate `getUnifiedHomeContentUseCase()` calls in HomeViewModel + HubViewModel

**Severity:** P0 - Critical
**Files:**
- `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt:112`
- `app/src/main/java/com/chakir/plexhubtv/feature/hub/HubViewModel.kt:79`

**Problem:** Both ViewModels independently call `getUnifiedHomeContentUseCase()` in their `init {}` block, triggering two identical multi-server network fan-outs on every app launch. This use case aggregates data from all Plex servers (fetching hubs, on-deck, etc.), so duplicating it doubles the network traffic, server load, and time-to-first-content.

```kotlin
// HomeViewModel.kt:112
getUnifiedHomeContentUseCase()
    .catch { ... }
    .collect { result -> ... }

// HubViewModel.kt:79  (identical call)
getUnifiedHomeContentUseCase()
    .catch { ... }
    .collect { result -> ... }
```

**Solution:** Share a single upstream Flow for unified home content. Options:
1. Move the use case invocation to a shared `@Singleton` repository that exposes a `SharedFlow` / `stateIn()` result, consumed by both ViewModels.
2. Use `shareIn(applicationScope, SharingStarted.WhileSubscribed(5000), 1)` in the use case itself.
3. If both screens are never visible simultaneously, merge them into a single ViewModel.

**Estimated Impact:** ~50% reduction in home screen load time and network calls. Halves API pressure on Plex servers during cold start. Users on slow/relay connections will see content appear noticeably faster.

---

### P0-02: N+1 URL resolution in `getWatchHistory()`

**Severity:** P0 - Critical
**File:** `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt:130-153`

**Problem:** Every emission of the history Flow calls `authRepository.getServers()` and then iterates every entity to resolve URLs one by one. The `getServers()` call itself may hit network. For a list of 50 history items, this creates 50 individual `mediaUrlResolver.resolveUrls()` calls plus a server list fetch per emission.

```kotlin
override fun getWatchHistory(limit: Int, offset: Int): Flow<List<MediaItem>> {
    return mediaDao.getHistory(limit, offset).map { entities ->
        val servers = authRepository.getServers().getOrNull() ?: emptyList()  // Network call per emission!
        entities.map { entity ->
            val server = servers.find { it.clientIdentifier == entity.serverId }
            // ... resolveUrls per entity
        }
    }
}
```

**Solution:**
1. Cache the server list in-memory (it rarely changes during a session).
2. Pre-resolve URLs at sync time and store `resolvedThumbUrl`/`resolvedBaseUrl` in Room (the columns already exist in `MediaEntity`).
3. Use `distinctUntilChanged()` on the server list to avoid redundant resolutions.

**Estimated Impact:** History screen load time drops from 2-5s to <100ms. Eliminates repeated network calls during scroll/recomposition.

---

### P0-03: Missing `lastViewedAt` index for history queries

**Severity:** P0 - Critical
**Files:**
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt:17-39`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt:134-142`

**Problem:** The `getHistory()` query does `ORDER BY lastViewedAt DESC` but `lastViewedAt` has no index. With thousands of media entries, SQLite must perform a full table scan and filesort. The `GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END` also has no index support, forcing row-by-row evaluation.

```kotlin
@Query(
    "SELECT *, MAX(lastViewedAt) as lastViewedAt FROM media WHERE lastViewedAt > 0 " +
    "GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END " +
    "ORDER BY lastViewedAt DESC LIMIT :limit OFFSET :offset"
)
fun getHistory(limit: Int, offset: Int): Flow<List<MediaEntity>>
```

**Solution:**
1. Add index: `@Index(value = ["lastViewedAt"])` to `MediaEntity`.
2. Consider a materialized `historyGroupKey` column (computed at write time) to replace the `CASE WHEN` expression, enabling a proper index.

**Estimated Impact:** History query drops from ~200-500ms to <20ms on large libraries (5000+ items). Direct improvement to History screen responsiveness.

---

### P0-04: `searchMedia()` uses `LIKE '%query%'` without Full-Text Search

**Severity:** P0 - Critical
**File:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt:70-74`

**Problem:** `LIKE '%query%'` forces a full table scan on every keystroke (after the 300ms debounce). For libraries with 5000+ items, this creates visible lag in the search UI.

```kotlin
@Query("SELECT * FROM media WHERE type = :type AND title LIKE '%' || :query || '%' ORDER BY title ASC")
suspend fun searchMedia(query: String, type: String): List<MediaEntity>
```

**Solution:** Implement Room FTS4 (`@Fts4`) on the `title` (and optionally `summary`, `genres`) columns. This changes search from O(n) to O(log n).

```kotlin
@Fts4(contentEntity = MediaEntity::class)
@Entity(tableName = "media_fts")
data class MediaFts(val title: String, val summary: String?)
```

**Estimated Impact:** Search response time drops from ~100-500ms to <10ms. Eliminates keystroke lag on Android TV where D-pad input is already slow.

---

### P1-05: Unstable `List<MediaItem>` in UI state classes causes unnecessary recompositions

**Severity:** P1 - High
**Files:**
- `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeUiState.kt:14`
- `app/src/main/java/com/chakir/plexhubtv/feature/hub/HubUiState.kt:8-10`

**Problem:** Compose's stability system treats `List<T>` as unstable because `MutableList` implements `List`. Every `StateFlow.update {}` that copies the state (even with identical content) triggers full recomposition of all consumers, including all media cards and rows.

```kotlin
data class HomeUiState(
    val onDeck: List<MediaItem> = emptyList(),  // Unstable!
)

data class HubUiState(
    val onDeck: List<MediaItem> = emptyList(),   // Unstable!
    val hubs: List<Hub> = emptyList(),           // Unstable!
    val favorites: List<MediaItem> = emptyList(), // Unstable!
)
```

**Solution:** Use `kotlinx.collections.immutable.ImmutableList` (or `persistentListOf()`) for all list fields in UI state classes. Add the `kotlinx-collections-immutable` dependency and annotate with `@Immutable` where appropriate.

```kotlin
val onDeck: ImmutableList<MediaItem> = persistentListOf()
```

**Estimated Impact:** Eliminates redundant recompositions of content rows when unrelated state fields change. Reduces frame drops during UI state updates by ~30-50%.

---

### P1-06: Debug ID badges rendered in release builds

**Severity:** P1 - High
**File:** `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixMediaCard.kt:269-308`

**Problem:** The debug badge showing IMDb/TMDb/unificationId is only gated by `isFocused`, not by `BuildConfig.DEBUG`. In release builds, every focused card evaluates three null checks and potentially renders debug information (orange badge with IDs) visible to end users.

```kotlin
// Line 269 — no BuildConfig.DEBUG guard
if (isFocused && (media.imdbId != null || media.tmdbId != null || media.unificationId != null)) {
    Column(...) {
        // Renders IMDb, TMDb, and unificationId labels
    }
}
```

**Solution:** Wrap with `BuildConfig.DEBUG` check:
```kotlin
if (BuildConfig.DEBUG && isFocused && (...)) { ... }
```

**Estimated Impact:** Eliminates unnecessary composition of debug UI in release. Prevents user-facing debug information leak. Reduces per-card composition cost when focused.

---

### P1-07: Dead `metadataAlpha` animation always targets `1f`

**Severity:** P1 - High
**File:** `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixMediaCard.kt:332-336`

**Problem:** The `animateFloatAsState` for `metadataAlpha` always targets `1f`, meaning it never animates. However, the `animateFloatAsState` infrastructure still creates an animation object, a state holder, and triggers snapshot reads on every composition.

```kotlin
val metadataAlpha by animateFloatAsState(
    targetValue = 1f, // Always fully visible — never changes
    animationSpec = tween(durationMillis = 200),
    label = "metadataAlpha"
)
```

**Solution:** Remove the animation entirely and use a constant `1f` for the `graphicsLayer` alpha, or remove the `graphicsLayer { alpha = metadataAlpha }` modifier altogether since it's always 1f.

**Estimated Impact:** Eliminates one dead animation object per visible card (20-40 on screen). Reduces memory allocations and snapshot system pressure during scroll.

---

### P1-08: Synchronous security provider installation on main thread

**Severity:** P1 - High
**File:** `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt:84,297-315`

**Problem:** `installSecurityProviders()` is called synchronously in `onCreate()` before the parallel initialization. Both `Conscrypt.newProvider()` and `ProviderInstaller.installIfNeeded()` are blocking operations that can take 50-200ms each, directly adding to cold start time.

```kotlin
override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) { Timber.plant(Timber.DebugTree()) }
    installSecurityProviders()  // Blocking on main thread!
    initializeFirebase()        // Also blocking!
    initializeAppInParallel()
    // ...
}
```

**Solution:** Move security provider installation into the parallel initialization block as an async job. Conscrypt is only needed before the first network call, which happens in Job 5 (server pre-warming). Ensure it completes before network jobs start.

**Estimated Impact:** Reduces cold start time by 100-400ms. Users see the splash/home screen faster.

---

### P1-09: `material-icons-extended` bloats APK by ~2-4MB

**Severity:** P1 - High
**File:** `app/build.gradle.kts:209`

**Problem:** The full `material-icons-extended` library contains ~7000 icons. The app only uses a handful (`Star`, `Cloud`, `Home`, `Search`, `Settings`, `Favorite`, `History`, `Movie`, `Tv`, `Download`). R8 tree-shaking helps but the metadata still inflates the APK.

```kotlin
implementation("androidx.compose.material:material-icons-extended:1.7.8")
```

**Solution:**
1. Remove the dependency entirely.
2. Copy only the ~10 needed icons into a local `Icons.kt` file (they are simple vector path definitions).
3. Alternatively, use `material-icons-core` (which includes the common ones like Star, Home, etc.) and only add individual extended icons as needed.

**Estimated Impact:** APK size reduction of ~2-4MB (per ABI split). Faster install/update for users. Reduced cold start time (less DEX to load).

---

### P1-10: Sequential API calls in `getNextMedia()` for episode navigation

**Severity:** P1 - High
**File:** `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt:97-121`

**Problem:** `getNextMedia()` makes up to 3 sequential network calls: `getSeasonEpisodes()`, `getShowSeasons()`, then another `getSeasonEpisodes()` for the next season. Each call goes through `ServerClientResolver` which may also resolve a connection. This runs on the playback path (triggered at 80% progress for prefetch, and at end for auto-next), adding 1-3s latency.

```kotlin
override suspend fun getNextMedia(currentItem: MediaItem): MediaItem? {
    // 1. Try next episode in current season
    val episodes = mediaDetailRepository.getSeasonEpisodes(...).getOrNull()  // Network call
    // ...
    // 2. Try first episode of next season
    val seasons = mediaDetailRepository.getShowSeasons(...).getOrNull()      // Network call
    // ...
    val nextSeasonEpisodes = mediaDetailRepository.getSeasonEpisodes(...).getOrNull() // Network call
}
```

**Solution:**
1. Prefetch season episodes list when playback starts (cache in-memory).
2. Use Room as the first source (episodes from the same show are likely synced).
3. Run `getShowSeasons()` and `getSeasonEpisodes()` in parallel with `async/awaitAll` since they are independent.

**Estimated Impact:** Next-episode transition drops from 1-3s to <200ms. Smoother binge-watching experience.

---

### P1-11: Inline lambda allocations in MainScreen navigation callbacks

**Severity:** P1 - High
**File:** `app/src/main/java/com/chakir/plexhubtv/feature/main/MainScreen.kt:132-133,139-140,152-153,163-164,170-171`

**Problem:** Every composable destination creates new lambda instances on each recomposition, e.g., `{ ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) }`. Since `NavHost` content recomposes when the back stack changes, these allocations happen on every navigation event.

```kotlin
composable(Screen.Home.route) {
    HomeRoute(
        onNavigateToDetails = { ratingKey, serverId -> onNavigateToDetails(ratingKey, serverId) },
        onNavigateToPlayer = { ratingKey, serverId -> onNavigateToPlayer(ratingKey, serverId) },
        // ...
    )
}
// Repeated for Hub, Movies, TVShows, Search, Downloads, Favorites, History...
```

**Solution:** Hoist lambdas to `remember` blocks or pass the function references directly:

```kotlin
val navigateToDetails = remember { onNavigateToDetails }
// or
onNavigateToDetails = onNavigateToDetails  // Direct function reference
```

Since the wrapping lambdas `{ rk, sid -> onNavigateToDetails(rk, sid) }` do nothing beyond forwarding, they can be replaced with the function reference directly.

**Estimated Impact:** Eliminates ~20 lambda allocations per navigation recomposition. Reduces GC pressure during frequent navigation.

---

### P1-12: Orphan `CoroutineScope` in `PlayerScrobbler.stop()`

**Severity:** P1 - High
**File:** `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerScrobbler.kt:102-108`

**Problem:** `stop()` creates an unmanaged `CoroutineScope(Dispatchers.IO)` for a fire-and-forget coroutine. This scope is never cancelled, meaning if the Application is in the process of shutting down or the coroutine throws, there is no structured concurrency to handle it. If `stop()` is called multiple times rapidly, multiple orphan coroutines accumulate.

```kotlin
fun stop() {
    scrobbleJob?.cancel()
    // ...
    CoroutineScope(Dispatchers.IO).launch {  // Orphan scope!
        try {
            tvChannelManager.updateContinueWatching()
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Post-playback update failed")
        }
    }
}
```

**Solution:** Inject the `@ApplicationScope` `CoroutineScope` into `PlayerScrobbler` and use it for the fire-and-forget job. This ensures proper cancellation on app shutdown.

```kotlin
@Singleton
class PlayerScrobbler @Inject constructor(
    // ...
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun stop() {
        // ...
        appScope.launch(Dispatchers.IO) {
            tvChannelManager.updateContinueWatching()
        }
    }
}
```

**Estimated Impact:** Prevents potential memory leaks and ensures clean shutdown. Eliminates rare crash where orphan coroutine accesses destroyed resources.

---

### P1-13: Duplicate MPV property observation

**Severity:** P1 - Medium
**File:** `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:100-101`

**Problem:** The `pause` property is observed twice, causing duplicate callbacks on every pause/resume event. Each callback triggers a `MutableStateFlow.update`, doubling the snapshot invalidations.

```kotlin
MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)  // Duplicate!
```

**Solution:** Remove the duplicate line (line 101).

**Estimated Impact:** Eliminates redundant state updates during MPV playback. Minor but free fix.

---

### P2-14: `getHistory()` query uses `SELECT *` with `MAX(lastViewedAt)` alias collision

**Severity:** P2 - Medium
**File:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt:134-137`

**Problem:** The query uses `SELECT *, MAX(lastViewedAt) as lastViewedAt`. Since `lastViewedAt` already exists in the table, Room's cursor mapping picks the raw column value (first `getColumnIndex` match) instead of the `MAX()` aggregate. This is the same Room `@RawQuery` pitfall documented in MEMORY.md. The result is that grouped items may show a non-MAX `lastViewedAt`, causing incorrect ordering.

```kotlin
"SELECT *, MAX(lastViewedAt) as lastViewedAt FROM media WHERE lastViewedAt > 0 " +
"GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END " +
"ORDER BY lastViewedAt DESC LIMIT :limit OFFSET :offset"
```

**Solution:** Use an explicit column list (excluding `lastViewedAt`) and then alias the aggregate as `lastViewedAt`:

```sql
SELECT ratingKey, serverId, title, ..., MAX(lastViewedAt) as lastViewedAt
FROM media WHERE lastViewedAt > 0
GROUP BY ...
ORDER BY lastViewedAt DESC
```

**Estimated Impact:** Fixes potentially incorrect history ordering where unified items show stale timestamps. Users see actually-most-recent items first.

---

### P2-15: Synchronous Firebase initialization on main thread

**Severity:** P2 - Medium
**File:** `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt:86,321-337`

**Problem:** `initializeFirebase()` is called synchronously on the main thread during `onCreate()`. Firebase Crashlytics, Analytics, and Performance SDKs each perform initialization work (reading config, setting up transport channels). This adds ~50-150ms to cold start.

```kotlin
override fun onCreate() {
    // ...
    installSecurityProviders()  // Blocking
    initializeFirebase()        // Also blocking on main thread
    initializeAppInParallel()   // Only this is async
}
```

**Solution:** Move Firebase initialization into the parallel init block or use `AppInitializer` with `ProcessLifecycleOwner` to defer it. Firebase SDKs are safe to initialize on background threads since v21+.

**Estimated Impact:** Reduces cold start by 50-150ms. Combined with P1-08 (security providers), total cold start improvement of 150-550ms.

---

### P2-16: 7 individual `FocusRequester` instances per TopBar composition

**Severity:** P2 - Medium
**File:** `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixTopBar.kt`

**Problem:** The TopBar creates 7 individual `FocusRequester` objects (one per navigation item) plus multiple `animateColorAsState` and `animateFloatAsState` per item. On Android TV, the TopBar is persistently visible on main screens, so these allocations persist and their animation states are continuously read by the snapshot system.

**Solution:**
1. Use a single `LazyRow` or `Row` with indexed focus management instead of 7 individual `FocusRequester`s.
2. Consider using `FocusRequester.createRefs()` for the group.
3. Reduce animations to only the selected item (defer color/alpha calculations for non-focused items).

**Estimated Impact:** Reduces per-frame snapshot reads and memory allocations in the TopBar. Minor but consistent improvement on every frame.

---

### P2-17: `getNextMedia()` and `getPreviousMedia()` fetch entire season episode list

**Severity:** P2 - Medium
**File:** `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt:97-128`

**Problem:** To find the next/previous episode, the code fetches ALL episodes of a season via `getSeasonEpisodes()`, then searches by index. For seasons with 20+ episodes, this downloads and deserializes more data than needed.

```kotlin
val episodes = mediaDetailRepository.getSeasonEpisodes(currentItem.parentRatingKey ?: "", currentItem.serverId).getOrNull()
val currentIndex = episodes.indexOfFirst { it.ratingKey == currentItem.ratingKey }
if (currentIndex != -1 && currentIndex < episodes.size - 1) {
    return episodes[currentIndex + 1]
}
```

**Solution:** Cache episode lists per season in-memory when playback starts (they are needed for the episode selector UI anyway). Alternatively, query Room first since episodes may already be synced locally.

**Estimated Impact:** Avoids redundant network calls during binge-watching. Smoother next-episode transitions.

---

### P2-18: `WorkManager.getInstance(this)` called 4 times in `setupBackgroundSync()`

**Severity:** P2 - Low
**File:** `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt:236-284`

**Problem:** `WorkManager.getInstance(this)` is called 4 separate times. While `getInstance()` is internally cached, each call goes through a synchronized block and null check. More importantly, this is a readability/maintenance issue that can hide initialization order bugs.

```kotlin
WorkManager.getInstance(this@PlexHubApplication).enqueueUniqueWork(...)
WorkManager.getInstance(this).enqueueUniquePeriodicWork(...)
WorkManager.getInstance(this).enqueueUniquePeriodicWork(...)
WorkManager.getInstance(this).enqueueUniquePeriodicWork(...)
```

**Solution:** Extract to a local variable:
```kotlin
val workManager = WorkManager.getInstance(this)
workManager.enqueueUniqueWork(...)
workManager.enqueueUniquePeriodicWork(...)
```

**Estimated Impact:** Marginal runtime improvement. Primarily a code hygiene fix.

---

### P2-19: `SideEffect` in NetflixMediaCard triggers on every composition

**Severity:** P2 - Low
**File:** `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixMediaCard.kt:83`

**Problem:** `SideEffect { onFocus(isFocused) }` runs after every successful composition, not just when `isFocused` changes. If the card recomposes for any reason (parent state change, animation tick), the `onFocus` callback fires with the same value, potentially triggering unnecessary work in the parent.

```kotlin
SideEffect { onFocus(isFocused) }
```

**Solution:** Use `LaunchedEffect(isFocused)` instead, which only fires when `isFocused` actually changes:
```kotlin
LaunchedEffect(isFocused) { onFocus(isFocused) }
```

**Estimated Impact:** Eliminates redundant `onFocus` callbacks during scroll/animation recompositions. Reduces parent recomposition triggers.

---

### P2-20: PagingConfig `maxSize=2000` may cause excessive memory usage for large libraries

**Severity:** P2 - Low
**File:** `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt`

**Problem:** `PagingConfig(pageSize=50, maxSize=2000, enablePlaceholders=true)` allows up to 2000 items in memory. Each `MediaItem` contains multiple string fields (URLs, titles, summaries, GUIDs), potentially consuming ~2-5KB each. At max capacity, this is 4-10MB of media items alone, which on Android TV devices with limited RAM (1-2GB) can trigger garbage collection pauses.

```kotlin
PagingConfig(
    pageSize = 50,
    prefetchDistance = 15,
    initialLoadSize = 100,
    maxSize = 2000,           // Up to 2000 items cached
    enablePlaceholders = true
)
```

**Solution:** Reduce `maxSize` to `500` (10 pages). The `prefetchDistance=15` ensures smooth scrolling while `enablePlaceholders=true` maintains scroll bar accuracy. For Android TV where users typically browse 100-200 items per session, 500 is more than sufficient.

**Estimated Impact:** Reduces peak memory usage by ~6-8MB in large libraries. Fewer GC pauses during browsing.

---

## Summary by Severity

| Severity | Count | Key Areas |
|----------|-------|-----------|
| P0 | 4 | Duplicate network calls, N+1 queries, missing DB indexes, no FTS |
| P1 | 9 | Compose stability, dead code in release, startup blocking, memory leaks |
| P2 | 7 | Query correctness, minor allocations, config tuning |

## Quick Wins (< 30 min each)

1. **P1-13**: Remove duplicate `MPVLib.observeProperty("pause", ...)` - 1 line delete
2. **P1-06**: Add `BuildConfig.DEBUG` guard to debug badges - 1 line change
3. **P1-07**: Remove dead `metadataAlpha` animation - 5 line delete
4. **P2-19**: Replace `SideEffect` with `LaunchedEffect(isFocused)` - 1 line change
5. **P2-18**: Extract `WorkManager.getInstance()` to local variable - 5 line change

## High-Impact Architectural Changes

1. **P0-01**: Share unified home content Flow (biggest bang-for-buck)
2. **P0-03 + P0-04**: Add `lastViewedAt` index + implement FTS4 (database layer)
3. **P0-02**: Cache server list for URL resolution (network layer)
4. **P1-05**: Migrate to `ImmutableList` in UI state classes (Compose layer)
5. **P1-08 + P2-15**: Move security + Firebase init off main thread (startup)
