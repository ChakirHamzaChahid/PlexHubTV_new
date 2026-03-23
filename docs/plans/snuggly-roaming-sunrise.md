# Library Grid Infinite Refresh Loop — Analysis & Fix Plan

## Context

**Scenario**: User selects series library, scrolls down to put focus on a filter button, then cannot navigate to the grid because it endlessly refreshes in a loop (~2s cycles).

**Log file**: `scenario entrer dans la librairie serie.txt` (283 lines, ~20s of activity)

---

## Processing Map (Data Flow)

```
                        ┌─────────────────────────────────────────┐
                        │           LibraryViewModel.init          │
                        │                                         │
                        │  1. _uiState.update(mediaType=Show)     │
                        │  2. loadMetadata() → servers, genres     │
                        │  3. settingsRepository.excludedServerIds │
                        │     .safeCollectIn → _uiState.update     │
                        │  4. Restore saved sort/filter prefs      │
                        │     → _uiState.update                    │
                        └───────────────┬─────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────────────┐
                    ▼                   ▼                           ▼
        ┌───────────────────┐  ┌────────────────────┐  ┌───────────────────────┐
        │  pagedItems Flow  │  │ getFilteredCount   │  │  UI (Compose)          │
        │                   │  │     Flow           │  │                        │
        │ _uiState          │  │ _uiState           │  │ LibraryRoute collects: │
        │  .map{FilterParams}│  │  .map{CountParams} │  │  • uiState.collect()   │
        │  .distinctUntilCh │  │  .distinctUntilCh  │  │  • pagedItems.collect   │
        │  .flatMapLatest{  │  │  .collectLatest{   │  │    AsLazyPagingItems() │
        │    getLibContent  │  │    repo.getCount   │  │                        │
        │  }                │  │    → _uiState      │  │ when(loadState.refresh)│
        │  .cachedIn(scope) │  │      .update(      │  │  • Loading+empty→skel  │
        │  .map{profileFilt}│  │       filteredItems)│  │  • else → grid        │
        └───────┬───────────┘  └────────────────────┘  └──────────┬────────────┘
                │                                                  │
                ▼                                                  │
   ┌──────────────────────────┐                                    │
   │ Room PagingSource        │◄───────────────────────────────────┘
   │ @RawQuery(observedEntities                     collectAsLazyPagingItems()
   │   = [MediaEntity::class])│                     registers loadState dep
   │                          │
   │ ANY write to media table │──── INVALIDATION ──┐
   │ → creates new PagingSource│                    │
   │ → emits new PagingData   │                    │
   └──────────────────────────┘                    │
                                                   ▼
                                     ┌──────────────────────────────┐
                                     │  REFRESH LOOP (every ~2s)    │
                                     │                              │
                                     │ 1. media table write         │
                                     │    (background process)      │
                                     │ 2. Room invalidates          │
                                     │    PagingSource              │
                                     │ 3. New PagingData emitted    │
                                     │ 4. .map{profileFilter}       │
                                     │    wraps in new PagingData   │
                                     │ 5. collectAsLazyPagingItems  │
                                     │    → loadState transitions   │
                                     │    Loading → NotLoading      │
                                     │ 6. when(loadState) re-evals  │
                                     │ 7. Full recomposition due    │
                                     │    to unstable UiState       │
                                     │ 8. Grid items re-compose     │
                                     │ 9. Coil re-requests images   │
                                     │    (from MEMORY: 0-12ms)     │
                                     │ 10. GC runs (5-7MB freed)    │
                                     └──────────────────────────────┘
```

---

## Log Timeline Analysis

| Time | Event | Source |
|------|-------|--------|
| 22:13:37.990 | Loading metadata start for Show | LibraryViewModel |
| 22:13:38.110 | SQL query: Type=Show, Filter=All, Sort=Title, Server=all | LibraryRepositoryImpl |
| 22:13:38.570 | **SUCCESS**: 3646 unique / 6263 raw in 581ms | LibraryViewModel |
| 22:13:39.913 | Cycle 1: 12 images START (NETWORK, 484-1485ms) | PerformanceTracker |
| 22:13:40.506 | **Skipped 39 frames!** | Choreographer |
| 22:13:40.716 | **Davey! 929ms** frame render | OpenGLRenderer |
| 22:13:41.118 | **Davey! 1027ms** frame render | OpenGLRenderer |
| 22:13:41.506 | Cycle 1 END | PerformanceTracker |
| 22:13:44.126 | Cycle 2: 12 images (MEMORY, 0-2ms each) | PerformanceTracker |
| 22:13:45.294 | Cycle 3: 12 images (MEMORY) | PerformanceTracker |
| 22:13:47.112 | **22MB LOS alloc spike** (13%→free) | GC |
| 22:13:50.337 | Cycle 4: 12 images (MEMORY) | PerformanceTracker |
| 22:13:52.603 | Cycle 5: 12 images (MEMORY) | PerformanceTracker |
| 22:13:53.769 | **JNI critical lock 23.9ms** on disk_io_3 | Runtime |
| 22:13:54.959 | Cycle 6: 12 images (MEMORY) | PerformanceTracker |
| 22:13:57.278 | Cycle 7: 12 images (MEMORY) | PerformanceTracker |

**Pattern**: Data loads ONCE. No repeated `flatMapLatest` trigger (no duplicate "DATA [Library] Loading content" logs). No `MediaRemoteMediator` activity (it's `null` for `serverId == "all"`). Yet grid re-renders 7+ times with the same 12 items. GC runs every 1-2s freeing 5-7MB. ~2s cycle interval suggests external periodic table writes.

---

## Identified Problems

### P1 (ROOT CAUSE): Room PagingSource invalidation from background writes
- `@RawQuery(observedEntities = [MediaEntity::class])` in `MediaDao.getMediaPagedRaw()` observes the ENTIRE `media` table
- ANY write to ANY row invalidates ALL active PagingSources, even if the written rows are unrelated to the current query
- Background processes (`RatingSyncWorker`, `LibrarySyncWorker`, or similar) write to the media table ~every 2s
- Each invalidation creates a new PagingSource → emits new PagingData → triggers UI refresh
- **File**: `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt:214-219`

### P2 (AMPLIFIER): `.map{}` after `cachedIn()` creates new PagingData wrapper
- Profile age filtering runs AFTER `cachedIn()`, creating a new `TransformedPagingData` on every emission
- Even if the underlying data is identical, `collectAsLazyPagingItems()` treats it as new data
- This triggers `loadState` transitions (NotLoading → Loading → NotLoading) on every PagingSource invalidation
- **File**: `LibraryViewModel.kt:141-148`

### P3 (AMPLIFIER): Compose instability causes full screen recomposition
- `LibraryUiState` contains `Set<String>`, `List<String>`, `Map<String, String>` — all unstable for Compose compiler
- Reading `pagedItems.loadState.refresh` in the `when` block registers a composition dependency
- Every `loadState` change → `when` re-evaluates → parent recomposes → `LibraryContent` recomposes (unstable params) → grid items recompose
- **Files**: `LibraryUiState.kt`, `LibrariesScreen.kt:424-455`

### P4 (MINOR): Heavy initial frame (39 frames skipped + 2 Davey!)
- First batch of 12 images loaded from NETWORK (484-1485ms each) blocks main thread
- 929ms + 1027ms Davey! warnings
- This is an initial load issue, not the loop itself

---

## Fix Plan

### Fix 1: Move profile filtering BEFORE `cachedIn()` (P2)
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt`

Move the `.map { profileFilter }` into the `flatMapLatest` block, before `cachedIn()`. This way, `cachedIn()` caches the already-filtered data, and no additional wrapping happens on re-emission.

```kotlin
// BEFORE (current):
.flatMapLatest { params -> getLibraryContentUseCase(...) }
.cachedIn(viewModelScope)
.map { pagingData ->
    val profile = profileRepository.getActiveProfile()
    if (profile != null && ...) {
        pagingData.filter { ... }
    } else { pagingData }
}

// AFTER (fix):
.flatMapLatest { params ->
    getLibraryContentUseCase(...)
        .map { pagingData ->
            val profile = profileRepository.getActiveProfile()
            if (profile != null && ...) {
                pagingData.filter { ... }
            } else { pagingData }
        }
}
.cachedIn(viewModelScope)
```

**Trade-off**: If profile changes, the entire PagingData flow needs to be re-triggered (via `flatMapLatest`). Since profile changes are rare (user action), this is acceptable. We'd need to include profile in `FilterParams` or observe it as a trigger.

### Fix 2: Stabilize `LibraryUiState` for Compose (P3)
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryUiState.kt`

Use `kotlinx.collections.immutable` types (`ImmutableList`, `ImmutableSet`, `ImmutableMap`) or annotate sub-states with `@Immutable`/`@Stable` to prevent unnecessary recomposition.

```kotlin
// Change in LibraryFilterState:
val excludedServerIds: ImmutableSet<String> = persistentSetOf()
val availableGenres: ImmutableList<String> = persistentListOf()
val availableServers: ImmutableList<String> = persistentListOf()
val availableServersMap: ImmutableMap<String, String> = persistentMapOf()

// Change in LibraryDisplayState:
val items: ImmutableList<MediaItem> = persistentListOf()
val hubs: ImmutableList<Hub> = persistentListOf()
```

Then update all call sites that build these collections to use `.toImmutableList()`, `.toImmutableSet()`, `.toImmutableMap()`.

### Fix 3: Isolate `loadState` reads to prevent recomposition cascade (P3)
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt`

Wrap the `loadState.refresh` check in a separate composable or use `derivedStateOf` to prevent it from triggering full parent recomposition:

```kotlin
// Extract to dedicated composable to isolate loadState reads:
@Composable
fun LibraryContentSwitcher(
    pagedItems: LazyPagingItems<MediaItem>,
    content: @Composable () -> Unit,
) {
    val showSkeleton by remember {
        derivedStateOf {
            pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount == 0
        }
    }
    if (showSkeleton) {
        LibraryGridSkeleton(modifier = Modifier.fillMaxSize())
    } else {
        content()
    }
}
```

### Fix 4: Investigate and eliminate background media table writes (P1)
**Action**: Add Timber logging to `MediaDao.upsertMedia()` and `mediaDao.update*()` methods to identify what process writes to the media table while the library screen is active. Then either:
- **Option A**: Prevent those writes while library is active (e.g., suspend RatingSyncWorker)
- **Option B**: Use a separate table for sync metadata so it doesn't invalidate the PagingSource
- **Option C** (quickest): Accept the invalidation but ensure fixes 1-3 make it invisible to the user

---

## Implementation Order

1. **Fix 1** (highest impact) — Move profile filter before `cachedIn()`
2. **Fix 3** (quick win) — Isolate loadState reads
3. **Fix 2** (comprehensive) — Stabilize collections with `kotlinx.collections.immutable`
4. **Fix 4** (investigation) — Identify and address background writes

## Verification

1. Open series library with "All" servers
2. Scroll down to filter buttons
3. Verify grid does NOT continuously re-render (no repeated image load logs)
4. Verify navigation from filter buttons to grid works (D-pad down)
5. Check Logcat: no repeated `[PERF][START][IMAGE_LOAD]` cycles
6. Check no Davey! warnings after initial load
7. Check GC frequency normalizes (not every 1-2s)
