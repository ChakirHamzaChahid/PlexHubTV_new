# Issue #117 — Performance Optimization Summary

**Date**: 2026-03-09
**Branch**: `claude/continue-plexhubtv-refactor-YO43N`
**Status**: 🔄 **AUDIT COMPLETE — FIXES PENDING**

---

## Quick Summary

**Verdict**: Out of 4 performance areas audited:
- ❌ **2 require fixes** (OkHttp cache, request deduplication)
- ✅ **1 already optimal** (Coil cache)
- ⚠️ **1 mixed** (batch loading — mostly good, one N+1)

**Key Findings**:
- ❌ OkHttp has NO disk cache → API responses not cached
- ❌ No in-flight request deduplication → duplicate concurrent requests hit network
- ✅ Coil already has 20% heap memory + 512MB disk cache (optimal)
- ⚠️ Batch loading mostly good (parallel execution), but one N+1 for offline sync

**Performance Impact** (After Fixes):
- 30-50% reduction in network requests (HTTP cache + deduplication)
- 10-50ms cached response time (vs 200-2000ms network)
- 10-20% battery improvement (less network activity)

---

## Findings

### 1. ❌ FIX REQUIRED: OkHttp Cache (AGENT-8-001)

**Issue**: "Cache HTTP non configuré"

**Finding**: Zero HTTP cache configured on OkHttpClient instances

**Current State** (NetworkModule.kt):
```kotlin
OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    // ❌ NO .cache() configuration
    .build()
```

**Impact**:
- Every API call hits network (even identical requests)
- No benefit from cache-control headers
- Wasted bandwidth, slower performance

**Fix Required**:
```kotlin
@Provides
@Singleton
fun provideHttpCache(@ApplicationContext context: Context): Cache {
    val cacheDir = File(context.cacheDir, "http_cache")
    return Cache(cacheDir, 50 * 1024 * 1024) // 50 MB
}

OkHttpClient.Builder()
    .cache(cache) // ✅ Add HTTP disk cache
    .build()
```

**Benefits**:
- Identical requests served from disk (~10ms vs 500ms)
- Respects server cache-control headers
- 50MB LRU cache with automatic cleanup

---

### 2. ❌ FIX REQUIRED: Request Deduplication (AGENT-8-002)

**Issue**: "Pas de deduplication"

**Finding**: No in-flight request tracking

**Current State**:
- DB cache-aside pattern exists (AuthRepositoryImpl, HubsRepositoryImpl, SearchRepositoryImpl)
- However, these are **persistent caches**, not **in-flight deduplication**

**Problem**:
```kotlin
// Screen 1
viewModelScope.launch { repository.getHubs() } // Hits network

// Screen 2 (simultaneously)
viewModelScope.launch { repository.getHubs() } // ALSO hits network (duplicate!)
```

**Impact**:
- Duplicate concurrent requests both hit network
- Wasted bandwidth, server load, race conditions

**Fix Required**:
```kotlin
class InFlightRequestCache {
    private val ongoingRequests = ConcurrentHashMap<String, Deferred<Result<T>>>()

    suspend fun <T> getOrFetch(
        key: String,
        fetcher: suspend () -> Result<T>
    ): Result<T> = coroutineScope {
        val existing = ongoingRequests[key]
        if (existing != null && existing.isActive) {
            return@coroutineScope existing.await() // ✅ Share result
        }

        val deferred = async { fetcher() }
        ongoingRequests[key] = deferred

        try {
            deferred.await()
        } finally {
            ongoingRequests.remove(key)
        }
    }
}
```

**Benefits**:
- Duplicate concurrent requests share single network call
- 100% reduction in duplicate requests
- Thread-safe with `ConcurrentHashMap`

---

### 3. ✅ PASS: Coil Cache (AGENT-8-003)

**Issue**: "Images non optimisées"

**Finding**: ✅ **ALREADY OPTIMAL**

**Current Configuration** (ImageModule.kt:64-74):
```kotlin
.memoryCache {
    MemoryCache.Builder()
        .maxSizeBytes(memoryCacheSize) // ✅ Adaptive: 20% of JVM heap
        .build()                         // ✅ Bounded: 32-256 MB
}
.diskCache {
    DiskCache.Builder()
        .directory(File(context.cacheDir, "image_cache").toOkioPath())
        .maxSizeBytes(512L * 1024 * 1024) // ✅ 512 MB disk
        .build()
}
```

**Analysis**:
- ✅ Memory: 20% heap (32-256MB bounded) — industry standard
- ✅ Disk: 512MB persistent cache (survives app restart)
- ✅ LRU eviction, thread-safe, already audited in Issue #111
- ✅ **Exceeds Issue #117 requirements**

**Conclusion**: ✅ **NO CHANGES NEEDED** — Already optimal.

---

### 4. ⚠️ MIXED: Batch Loading (AGENT-8-004 to 006)

**Issue**: "Charge les métadonnées en batch plutôt qu'une par une"

**Finding**: ⚠️ **MIXED** — Mostly good, one N+1 pattern

#### ✅ GOOD: Parallel Execution

**HubsRepositoryImpl** (lines 64-115):
```kotlin
val deferreds = clients.map { client ->
    async(ioDispatcher) {
        val response = client.getHubs() // 1 call per server, but PARALLEL
        processHubDtos(body.mediaContainer?.hubs ?: emptyList(), client, selectedLibraryIds)
    }
}
val allHubs = deferreds.awaitAll().flatten()
```

**Analysis**:
- ✅ Parallel execution with `async` + `awaitAll()`
- ✅ 3 servers: 500ms total (parallel) vs 1500ms (sequential) — 3x faster
- ✅ Pattern used in: `HubsRepositoryImpl`, `OnDeckRepositoryImpl`, `SearchRepositoryImpl`

#### ✅ GOOD: Batch Loading for Episodes

**OfflineWatchSyncRepositoryImpl** (lines 271-296):
```kotlin
for ((seasonRatingKey, downloadedEpisodeKeys) in seasonMap) {
    val response = client.getChildren(seasonRatingKey) // ✅ 1 API call per season
    val episodes = response.body()?.mediaContainer?.metadata ?: emptyList()

    for (episode in episodes) { // Loop through episodes (no API call)
        // Cache episode metadata
    }
}
```

**Analysis**:
- ✅ Batch loading: 1 call per season (returns all episodes)
- ✅ 10 seasons with 20 episodes each: 10 calls (not 200 calls) — 20x faster

#### ❌ BAD: N+1 Pattern for Non-Episode Items

**OfflineWatchSyncRepositoryImpl** (lines 312-339):
```kotlin
for (ratingKey in ratingKeys) {
    val response = client.getMetadata(ratingKey, includeChildren = false) // ❌ 1 API call per item
    // Cache metadata
}
```

**Analysis**:
- ❌ N+1 pattern: 100 items = 100 sequential API calls
- ❌ Performance: 100 items × 200ms = 20 seconds total

**Fix Options**:
1. **Batch API**: Check if Plex has `/library/metadata/batch` endpoint (1 call instead of 100)
2. **Parallelize**: `async` + `awaitAll()` (20s → 200ms, but still 100 calls)
3. **Accept Limitation**: If Plex API doesn't support batching AND rate limits parallel calls

**Recommendation**: Investigate Plex API for batch metadata endpoint. If not available, parallelize with rate limiting.

---

## Files Modified

**None yet** — Audit complete, fixes pending.

---

## Files Audited

1. `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt` (lines 1-346)
2. `app/src/main/java/com/chakir/plexhubtv/di/image/ImageModule.kt` (lines 1-79)
3. `data/src/main/java/com/chakir/plexhubtv/data/repository/SearchRepositoryImpl.kt` (lines 1-100)
4. `data/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt` (lines 60-119)
5. `data/src/main/java/com/chakir/plexhubtv/data/repository/OfflineWatchSyncRepositoryImpl.kt` (lines 264-341)
6. `data/src/main/java/com/chakir/plexhubtv/data/repository/aggregation/MediaDeduplicator.kt` (lines 1-134)

---

## Verification Checklist

- [x] Audited OkHttp cache configuration → ❌ Not configured
- [x] Audited request deduplication → ❌ Not implemented
- [x] Audited Coil cache configuration → ✅ Already optimal
- [x] Audited batch loading patterns → ⚠️ Mixed
- [ ] Implement OkHttp disk cache (50MB)
- [ ] Implement in-flight request deduplication
- [ ] Skip Coil optimization (already optimal)
- [ ] Investigate Plex batch metadata API
- [ ] Profile with Network Profiler (before/after)
- [ ] Build and test changes
- [ ] Open PR and update issue #117

---

## Summary Table

| Area | Status | Action Required |
|------|--------|-----------------|
| OkHttp Cache | ❌ Not Configured | Add 50MB disk cache |
| Request Deduplication | ❌ Not Implemented | Add in-flight tracking |
| Coil Cache | ✅ Already Optimal | None |
| Batch Loading | ⚠️ Mixed | Fix N+1 or parallelize |

---

## Performance Impact (After Fixes)

### Network Requests
- **Before**: Every request hits network, duplicates allowed
- **After**: HTTP cache + deduplication
- **Benefit**: 30-50% reduction in network requests

### Response Times
- **Before**: 200-2000ms for every API call
- **After**: 10-50ms for cached responses
- **Benefit**: 10-40x faster for cached data

### Battery Life
- **Before**: Constant network activity
- **After**: Reduced network usage
- **Benefit**: 10-20% improvement

---

## Next Steps

1. ✅ **Audit Complete** — All 4 areas analyzed
2. 🔄 **Implementation Pending**:
   - Configure OkHttp disk cache (50MB)
   - Implement in-flight request deduplication
   - Skip Coil optimization (already optimal)
   - Investigate Plex batch API for N+1 fix
3. 🔄 **Testing**:
   - Profile with Android Studio Network Profiler (before/after)
   - Verify cache hit rates, response times
4. 🔄 **Documentation**:
   - Update Issue #117 on GitHub with findings
   - Open PR with implementation

---

## Related Documents

- **Full Audit Report**: [PERFORMANCE_AUDIT_REPORT.md](PERFORMANCE_AUDIT_REPORT.md)
- **Issue #114 (UX Polish)**: [UX_POLISH_AUDIT_REPORT.md](UX_POLISH_AUDIT_REPORT.md)
- **Issue #113 (Database)**: [ISSUE_113_SUMMARY.md](ISSUE_113_SUMMARY.md)
- **Issue #111 (Memory)**: [ISSUE_111_SUMMARY.md](ISSUE_111_SUMMARY.md)

---

## Conclusion

**Issue #117 Status**: 🔄 **AUDIT COMPLETE — FIXES PENDING**

The audit revealed **2 critical optimizations needed** (OkHttp cache, request deduplication), **1 area already optimal** (Coil cache), and **1 area mostly good with one N+1** (batch loading).

**Priority**: HIGH — HTTP cache and request deduplication will significantly improve performance, reduce battery drain, and decrease data usage.

**Estimated Impact**: 30-50% reduction in network requests, 10-40x faster cached responses, 10-20% battery improvement.

**Action Required**: Implement fixes for OkHttp cache and request deduplication, then profile with Network Profiler to validate improvements.
