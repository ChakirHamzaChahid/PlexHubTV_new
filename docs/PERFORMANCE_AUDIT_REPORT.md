# Performance Audit Report
## Issue #117 — AGENT-8-001 à 006

**Date**: 2026-03-09
**Branch**: `claude/continue-plexhubtv-refactor-YO43N`
**Status**: 🔄 IN PROGRESS (Audit Complete, Fixes Pending)

---

## Executive Summary

**Overall Assessment**: Out of 4 performance optimization areas:
- ❌ **2 require fixes** (OkHttp cache, request deduplication)
- ✅ **1 already optimal** (Coil cache)
- ⚠️ **1 mixed** (batch loading — mostly good, one N+1 pattern found)

**Key Findings**:
1. ❌ **OkHttp Cache (AGENT-8-001)**: No disk cache configured — API responses not cached
2. ❌ **Request Deduplication (AGENT-8-002)**: No in-flight request tracking — duplicate concurrent requests hit network
3. ✅ **Coil Cache (AGENT-8-003)**: Already optimal — 20% heap memory + 512MB disk
4. ⚠️ **Batch Loading (AGENT-8-004 to 006)**: Mostly good (parallel execution), but one N+1 in OfflineWatchSyncRepositoryImpl

**Performance Impact**:
- **Before**: Cold start loads, repeated API calls, no HTTP caching
- **After Fixes**: 50MB HTTP cache, in-flight deduplication, reduced network overhead

**Recommendation**: Implement OkHttp disk cache and request deduplication. Batch loading N+1 may be unavoidable due to Plex API limitations.

---

## Findings

### 1. ❌ REQUIRES FIX: OkHttp Cache (AGENT-8-001)

**Issue Reported**: "Cache HTTP non configuré"

**Audit Method**:
```bash
Read NetworkModule.kt (all OkHttpClient providers)
Grep for "cache|Cache" in NetworkModule.kt
Result: Zero cache configuration found
```

**Finding**: ❌ **NO DISK CACHE CONFIGURED**

**Analysis**:
- Two OkHttpClient instances: `@Named("public")` and default
- Both have timeout configs, connection pooling, but NO `.cache()`
- API responses not cached — every request hits network (even for identical requests)
- No cache-control header benefits

**Current Configuration** (NetworkModule.kt:114-130, 145-261):
```kotlin
// Public OkHttpClient
OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
    // ❌ NO .cache() configuration
    .build()

// Default OkHttpClient (for Plex/LAN)
OkHttpClient.Builder()
    .sslSocketFactory(sslContext.socketFactory, localAwareTrustManager)
    .hostnameVerifier { ... }
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    // ❌ NO .cache() configuration
    .build()
```

**Impact**:
- **Network Overhead**: Every API call hits network (no HTTP cache)
- **Slow Performance**: No benefit from cache-control headers (e.g., `max-age=3600`)
- **Data Usage**: Repeated downloads of same content
- **Battery Drain**: More network activity = more battery consumption

**Fix Required** (Issue #117 Request):
```kotlin
// Add to NetworkModule
@Provides
@Singleton
fun provideHttpCache(@ApplicationContext context: Context): Cache {
    val cacheDir = File(context.cacheDir, "http_cache")
    return Cache(cacheDir, 50 * 1024 * 1024) // 50 MB
}

// Modify OkHttpClient providers
OkHttpClient.Builder()
    .cache(cache) // ✅ Add HTTP disk cache
    .build()
```

**Benefits**:
- ✅ Identical API calls served from disk (instant response)
- ✅ Respects cache-control headers (server-driven caching)
- ✅ 50MB cache (configurable via Issue #117 requirement)
- ✅ LRU eviction policy (automatic cleanup)
- ✅ Thread-safe (OkHttp handles concurrency)

**Conclusion**: ❌ **FIX REQUIRED** — Add 50MB HTTP disk cache to OkHttpClient.

---

### 2. ❌ REQUIRES FIX: Request Deduplication (AGENT-8-002)

**Issue Reported**: "Trop de requêtes réseau (pas de deduplication)"

**Audit Method**:
```bash
Grep for "dedup|inflight|pending.*request" across repositories
Read SearchRepositoryImpl.kt, HubsRepositoryImpl.kt
Result: No in-flight request tracking found
```

**Finding**: ❌ **NO IN-FLIGHT REQUEST DEDUPLICATION**

**Current State**:
- **DB Cache-Aside Pattern**: Many repositories use Room/ApiCacheDao for persistent caching
  - `AuthRepositoryImpl`: 3-level cache (memory + DB + network)
  - `HubsRepositoryImpl`: DB cache for hubs (1 hour TTL)
  - `SearchRepositoryImpl`: DB cache for search results
  - `OnDeckRepositoryImpl`: Emits cache first, then network
- **However**: These are **persistent caches**, not **in-flight deduplication**

**Problem**:
If the same request is made twice simultaneously (e.g., from different screens):
1. Request A starts → cache miss → starts network call
2. Request B starts → cache miss → starts network call (duplicate!)
3. Both requests hit network in parallel
4. Both wait for response
5. Both cache the result

**Example Scenario**:
```kotlin
// Screen 1
viewModelScope.launch { repository.getHubs() } // Hits network

// Screen 2 (simultaneously)
viewModelScope.launch { repository.getHubs() } // ALSO hits network (duplicate!)
```

**What's Missing**:
No mechanism to share the result of an ongoing request with new identical requests.

**Impact**:
- **Duplicate Network Calls**: Same request triggers multiple parallel API calls
- **Wasted Bandwidth**: Downloading same data multiple times
- **Server Load**: More requests to Plex servers
- **Race Conditions**: Both requests may try to cache result simultaneously

**Fix Required** (In-Flight Request Deduplication Pattern):
```kotlin
class InFlightRequestCache {
    private val ongoingRequests = ConcurrentHashMap<String, Deferred<Result<T>>>()

    suspend fun <T> getOrFetch(
        key: String,
        fetcher: suspend () -> Result<T>
    ): Result<T> = coroutineScope {
        // Check if request is already in-flight
        val existing = ongoingRequests[key]
        if (existing != null && existing.isActive) {
            return@coroutineScope existing.await() // ✅ Share result
        }

        // Start new request
        val deferred = async { fetcher() }
        ongoingRequests[key] = deferred

        try {
            deferred.await()
        } finally {
            ongoingRequests.remove(key) // Cleanup
        }
    }
}
```

**Benefits**:
- ✅ Duplicate concurrent requests share single network call
- ✅ 100% reduction in duplicate requests (N parallel requests → 1 network call)
- ✅ Thread-safe with `ConcurrentHashMap`
- ✅ Automatic cleanup after request completes
- ✅ Works with any suspend function

**Conclusion**: ❌ **FIX REQUIRED** — Implement in-flight request deduplication.

---

### 3. ✅ PASS: Coil Cache Configuration (AGENT-8-003)

**Issue Reported**: "Images non optimisées, cache non configuré"

**Audit Method**:
```bash
Read ImageModule.kt
Check memory cache config
Check disk cache config
Result: Both memory AND disk cache already configured optimally
```

**Finding**: ✅ **ALREADY OPTIMAL**

**Current Configuration** (ImageModule.kt:64-74):

**Memory Cache** (lines 64-68):
```kotlin
.memoryCache {
    MemoryCache.Builder()
        .maxSizeBytes(memoryCacheSize) // ✅ Adaptive: 20% of JVM heap
        .build()                         // ✅ Bounded: 32 MB min, 256 MB max
}
```

**Calculation Logic** (lines 45-47):
```kotlin
val maxHeap = Runtime.getRuntime().maxMemory()
val memoryCacheSize = (maxHeap * 0.20).toLong() // 20% of heap
    .coerceIn(32 * 1024 * 1024L, 256 * 1024 * 1024L) // Min 32 MB, Max 256 MB
```

**Disk Cache** (lines 69-74):
```kotlin
.diskCache {
    DiskCache.Builder()
        .directory(File(context.cacheDir, "image_cache").toOkioPath())
        .maxSizeBytes(512L * 1024 * 1024) // ✅ 512 MB disk
        .build()
}
```

**Analysis**:
- ✅ **Memory Cache**: 20% of JVM heap (industry standard: 15-25%)
- ✅ **Bounded**: Min 32 MB, Max 256 MB (prevents OOM on low-RAM devices)
- ✅ **Disk Cache**: 512 MB persistent cache (survives app restart)
- ✅ **LRU Eviction**: Automatic cache management
- ✅ **Performance**: ~5ms memory cache hit, ~20ms disk cache hit vs 500-2000ms network

**Logging** (lines 49-54):
```kotlin
Timber.i(
    "ImageCache: Heap limit = %.0f MB, Cache = %.1f MB (%.0f%%)",
    maxHeap / (1024.0 * 1024.0),
    memoryCacheSize / (1024.0 * 1024.0),
    (memoryCacheSize.toDouble() / maxHeap) * 100
)
```

**Why This Is Optimal**:
1. **Adaptive to Device**: Uses JVM heap (not system RAM) to avoid OOM
2. **Bounded Safety**: Min/Max prevents extremes (32-256MB range)
3. **Two-Tier Cache**: Memory (fast) + Disk (persistent)
4. **Large Disk Cache**: 512MB holds ~500-1000 images (better than typical 50-100MB)
5. **Already Audited**: This was verified in Issue #111 (Memory Leak Audit)

**Verification**:
From Issue #111 audit (ISSUE_111_SUMMARY.md:44-69):
```
✅ 20% of JVM heap (industry standard: 15-25%)
✅ Bounded: Min 32 MB, Max 256 MB
✅ LRU eviction policy
✅ Uses JVM heap, not system RAM
```

**Conclusion**: ✅ **ALREADY OPTIMAL** — Coil cache exceeds Issue #117 requirements. No changes needed.

---

### 4. ⚠️ MIXED: Batch Loading Patterns (AGENT-8-004 to 006)

**Issue Reported**: "Pour les listes, charge les métadonnées en batch plutôt qu'une par une"

**Audit Method**:
```bash
Grep for "for.*in|forEach|map.*api" in repositories
Read SearchRepositoryImpl.kt, HubsRepositoryImpl.kt, OfflineWatchSyncRepositoryImpl.kt
Check for N+1 patterns (loop with API call inside)
```

**Finding**: ⚠️ **MIXED** — Mostly good, one N+1 found

#### ✅ GOOD: Parallel Execution Pattern

**HubsRepositoryImpl** (lines 64-115):
```kotlin
val deferreds = clients.map { client ->
    async(ioDispatcher) {
        val response = client.getHubs() // 1 call per server, but in PARALLEL
        processHubDtos(body.mediaContainer?.hubs ?: emptyList(), client, selectedLibraryIds)
    }
}
val allHubs = deferreds.awaitAll().flatten()
```

**Analysis**:
- ✅ Uses `async` + `awaitAll()` for parallel execution
- ✅ All servers queried simultaneously (not sequentially)
- ✅ Necessary in multi-server architecture (can't batch across servers)
- ✅ Each server queried only ONCE
- ✅ Pattern used in: `HubsRepositoryImpl`, `OnDeckRepositoryImpl`, `SearchRepositoryImpl`

**Performance**:
- Sequential: 3 servers × 500ms = 1500ms total
- Parallel: max(500ms, 500ms, 500ms) = 500ms total
- **Improvement**: 3x faster

**SearchRepositoryImpl** (lines 58-76):
```kotlin
val results: List<MediaItem> = servers.map { server ->
    async {
        val searchResult = withTimeoutOrNull(5000L) {
            searchOnServer(server, query, year, type, unwatched)
        }
        searchResult?.getOrNull() ?: emptyList()
    }
}.awaitAll().flatten()
```

**Analysis**:
- ✅ Parallel search across all servers (5 second timeout per server)
- ✅ Timeout prevents slow servers blocking entire search
- ✅ Graceful degradation (failed servers return empty list)
- ✅ Combined with 400ms debounce from Issue #114 fix

#### ✅ GOOD: Batch Loading for Episodes

**OfflineWatchSyncRepositoryImpl** (lines 271-296):
```kotlin
for ((seasonRatingKey, downloadedEpisodeKeys) in seasonMap) {
    val response = client.getChildren(seasonRatingKey) // ✅ 1 API call per season
    val episodes = response.body()?.mediaContainer?.metadata ?: emptyList()

    for (episode in episodes) { // Loop through episodes (no API call here)
        if (downloadedEpisodeKeys.contains(episode.ratingKey)) {
            // Cache episode metadata
        }
    }
}
```

**Analysis**:
- ✅ **Batch Loading**: `getChildren(seasonRatingKey)` returns ALL episodes for season in ONE call
- ✅ Avoids N+1: If season has 20 episodes, makes 1 call (not 20 calls)
- ✅ Inner loop is local-only (no API calls)
- ✅ Efficient pattern for hierarchical data (Season → Episodes)

**Performance**:
- N+1 Pattern: 10 seasons × 20 episodes = 200 API calls
- Batch Pattern: 10 seasons × 1 call = 10 API calls
- **Improvement**: 20x reduction in API calls

#### ❌ BAD: N+1 Pattern for Non-Episode Items

**OfflineWatchSyncRepositoryImpl** (lines 312-339):
```kotlin
for (ratingKey in ratingKeys) {
    val response = client.getMetadata(ratingKey, includeChildren = false) // ❌ 1 API call per item
    if (response.isSuccessful) {
        val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
        // Cache metadata
    }
}
```

**Analysis**:
- ❌ **N+1 Pattern**: If `ratingKeys` has 100 items, makes 100 sequential API calls
- ❌ Each call: `getMetadata(ratingKey)` for individual item
- ❌ No batching or parallelization (sequential loop)

**Impact**:
- **Performance**: 100 items × 200ms = 20 seconds total
- **Network**: 100 separate HTTP requests
- **Battery**: More network activity

**Why This Exists**:
This is for syncing offline watch history for movies/shows (non-episode items). These are individual items that don't have a parent-child relationship like seasons/episodes.

**Can This Be Fixed?**

**Option 1: Check if Plex API has batch endpoint**
- Plex API may not have a `/library/metadata/batch` endpoint
- If it does: `client.getBatchMetadata(ratingKeys)` would reduce 100 calls to 1 call
- **Need to verify**: Plex API documentation

**Option 2: Parallelize the loop**
```kotlin
ratingKeys.map { ratingKey ->
    async {
        client.getMetadata(ratingKey, includeChildren = false)
    }
}.awaitAll()
```
- Reduces time from 20s to ~200ms (100x faster)
- Still makes 100 network calls, but in parallel
- **Trade-off**: More concurrent connections (may hit server rate limits)

**Option 3: Accept the limitation**
- If Plex API doesn't support batch metadata AND server rate limits parallel calls
- This may be unavoidable
- Document as known limitation

**Recommendation**: Investigate if Plex API has batch metadata endpoint. If yes, implement batching. If no, consider parallelizing with rate limiting.

**Conclusion**: ⚠️ **MIXED** — Parallel execution and episode batching are good. N+1 for non-episode items requires investigation (may be unavoidable).

---

## Summary Table

| Issue | AGENT ID | Reported Problem | Status | Action Required |
|-------|----------|-----------------|--------|-----------------|
| OkHttp Cache | AGENT-8-001 | Cache HTTP non configuré | ❌ Requires Fix | Add 50MB disk cache to OkHttpClient |
| Request Deduplication | AGENT-8-002 | Pas de deduplication | ❌ Requires Fix | Implement in-flight request tracking |
| Coil Cache | AGENT-8-003 | Images non optimisées | ✅ Already Optimal | None - exceeds requirements |
| Batch Loading | AGENT-8-004 to 006 | Métadonnées une par une | ⚠️ Mixed | Investigate Plex batch API or parallelize N+1 |

---

## Files Audited

### Network Module
1. `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt`
   - **Lines Read**: 1-346 (complete file)
   - **Finding**: No HTTP cache configured on OkHttpClient providers

### Image Module
2. `app/src/main/java/com/chakir/plexhubtv/di/image/ImageModule.kt`
   - **Lines Read**: 1-79 (complete file)
   - **Finding**: Memory + Disk cache already configured optimally

### Repository Implementations
3. `data/src/main/java/com/chakir/plexhubtv/data/repository/SearchRepositoryImpl.kt`
   - **Lines Read**: 1-100 (partial)
   - **Finding**: No in-flight deduplication, parallel search across servers (good)

4. `data/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt`
   - **Lines Read**: 60-119 (partial)
   - **Finding**: Parallel execution with async/awaitAll (good)

5. `data/src/main/java/com/chakir/plexhubtv/data/repository/OfflineWatchSyncRepositoryImpl.kt`
   - **Lines Read**: 264-341 (partial)
   - **Finding**: Batch loading for episodes (good), N+1 for non-episode items (bad)

### Content Deduplication
6. `data/src/main/java/com/chakir/plexhubtv/data/repository/aggregation/MediaDeduplicator.kt`
   - **Lines Read**: 1-134 (complete file)
   - **Finding**: Content deduplication (not request deduplication)

---

## Verification Checklist

- [x] Audited OkHttp cache configuration → ❌ Not configured
- [x] Audited request deduplication → ❌ Not implemented
- [x] Audited Coil cache configuration → ✅ Already optimal
- [x] Audited batch loading patterns → ⚠️ Mixed (mostly good, one N+1)
- [ ] Implement OkHttp disk cache (50MB)
- [ ] Implement in-flight request deduplication
- [ ] Skip Coil optimization (already optimal)
- [ ] Investigate Plex batch metadata API for N+1 fix
- [ ] Profile with Android Studio Network Profiler (before/after)
- [ ] Build and test changes
- [ ] Open PR and update issue #117

---

## Performance Impact Projections

### Before Optimizations
- **Cold Start**: No HTTP cache, every request hits network
- **Duplicate Requests**: 2 screens requesting same data = 2 network calls
- **Images**: Memory + Disk cache already working (no change needed)
- **Batch Loading**: Mostly good, except N+1 for offline sync (100 items = 100 sequential calls)

### After Optimizations
- **HTTP Cache**: 50MB disk cache, cache-control headers respected
  - **Benefit**: Identical requests served from disk (~10ms vs 500ms)
  - **Example**: Repeated `/hubs` calls = instant response after first load
- **Request Deduplication**: In-flight tracking prevents duplicate concurrent calls
  - **Benefit**: 2 screens requesting same data = 1 network call (100% reduction)
  - **Example**: Home + Library both loading hubs = 1 shared network call
- **Coil Cache**: No change (already optimal)
- **Batch Loading**: N+1 fix depends on Plex API capabilities
  - **If batch API exists**: 100 calls → 1 call (100x reduction)
  - **If no batch API**: 100 sequential → 100 parallel (~100x faster via parallelization)

### Estimated Performance Gains
- **Network Requests**: 30-50% reduction (HTTP cache + deduplication)
- **API Response Time**: 10-50ms for cached responses (vs 200-2000ms network)
- **Battery Life**: 10-20% improvement (less network activity)
- **Data Usage**: 20-40% reduction (HTTP cache)

---

## Related Documents

- **Issue #114 (UX Polish)**: [UX_POLISH_AUDIT_REPORT.md](UX_POLISH_AUDIT_REPORT.md)
- **Issue #113 (Database)**: [ISSUE_113_SUMMARY.md](ISSUE_113_SUMMARY.md)
- **Issue #111 (Memory)**: [ISSUE_111_SUMMARY.md](ISSUE_111_SUMMARY.md)
- **Issue #110 (Coroutines)**: [ISSUE_110_SUMMARY.md](ISSUE_110_SUMMARY.md)

---

## Conclusion

**Issue #117 Status**: 🔄 **AUDIT COMPLETE — FIXES PENDING**

Out of 4 optimization areas:
- ❌ **2 require fixes**: OkHttp cache, request deduplication
- ✅ **1 already optimal**: Coil cache (exceeds requirements)
- ⚠️ **1 mixed**: Batch loading (mostly good, one N+1)

**Next Steps**:
1. Implement OkHttp disk cache (50MB)
2. Implement in-flight request deduplication with `ConcurrentHashMap`
3. Skip Coil optimization (already optimal)
4. Investigate Plex API for batch metadata endpoint to fix N+1
5. Profile with Android Studio Network Profiler before/after
6. Open PR and update Issue #117 on GitHub

**Priority**: HIGH — HTTP cache and request deduplication will significantly improve perceived performance and reduce network overhead.
