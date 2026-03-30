# Fix: Images fail to load when device is on a different network than server

## Context

Images (posters, art) fail to load with 5s timeouts when the device is on a different network than the Plex server. The root cause is **stale connection URLs** in the `ConnectionManager` cache.

**Flow causing the bug:**
1. `LibrarySyncWorker` syncs on home WiFi → `ConnectionManager` caches `192.168.1.2:32400` (local IP)
2. Cache persisted to DataStore via `ConnectionCacheStore`
3. Device moves to mobile data or app restarts on different WiFi
4. `ConnectionManager` restores stale `192.168.1.2:32400` from DataStore
5. `LibraryRepositoryImpl.clientMap` calls `getCachedUrl()` → stale local IP
6. `MediaUrlResolver.resolveUrls()` builds image URLs with stale base URL
7. Coil tries to load → 5s timeout → image not displayed
8. `alternativeThumbUrls` (from DB `resolvedThumbUrl`) are ALSO stale → all fallbacks fail

**Key files involved:**
- `core/network/.../ConnectionManager.kt` — caches server URLs, persists to DataStore, no network awareness
- `data/.../LibraryRepositoryImpl.kt:178-184` — `clientMap` uses `getCachedUrl()` + `firstOrNull()` fallback
- `core/ui/.../FallbackAsyncImage.kt` — tries primary URL then alternatives (both stale)
- `di/image/ImageModule.kt:34` — image connect timeout = 5s (too long for stale URLs)

---

## Fix Plan

### Step 1: Add network change monitoring to `ConnectionManager`

**File:** `core/network/.../ConnectionManager.kt`

- Inject `@ApplicationContext context: Context` into constructor
- Register `ConnectivityManager.NetworkCallback` in `init` block
- On `onAvailable()`: invalidate all cached connections + clear failed servers
- Add `invalidateAllConnections()` method
- Emit a `_connectionRefreshNeeded` `MutableSharedFlow<Unit>` so observers can react

```kotlin
// New fields:
private val _connectionRefreshNeeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val connectionRefreshNeeded: SharedFlow<Unit> = _connectionRefreshNeeded.asSharedFlow()

// In init:
val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        invalidateAllConnections()
    }
})

fun invalidateAllConnections() {
    _activeConnections.update { emptyMap() }
    failedServers.clear()
    scope.launch { connectionCacheStore.saveCachedConnections(emptyMap()) }
    _connectionRefreshNeeded.tryEmit(Unit)
}
```

### Step 2: Improve `clientMap` fallback in `LibraryRepositoryImpl`

**File:** `data/.../LibraryRepositoryImpl.kt:178-184`

When `getCachedUrl()` returns null (after network invalidation), the current fallback picks `server.connectionCandidates.firstOrNull()?.uri` which is often the local IP (Plex orders local first).

**Fix:** Prefer non-local candidates in fallback:

```kotlin
val clientMap = allServers.associate { server ->
    server.clientIdentifier to (
        connectionManager.getCachedUrl(server.clientIdentifier)
            ?: server.connectionCandidates
                .filter { !it.relay }
                .firstOrNull { !isPrivateIp(it.uri) }?.uri
            ?: server.connectionCandidates.firstOrNull()?.uri
    )
}
```

Add a `isPrivateIp()` helper that detects RFC1918 addresses in the hostname.

### Step 3: Reduce image connect timeout

**File:** `app/.../di/image/ImageModule.kt:34`

Reduce from 5s to 2s. If the server is reachable, 2s is ample. If not, fail fast and try fallback sooner.

```kotlin
.connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS) // 2s instead of 5s
```

### Step 4: Proactive connection refresh after network change

**File:** `app/.../PlexHubApplication.kt` or `MainViewModel.kt`

After `connectionRefreshNeeded` fires, proactively re-discover connections for all servers:

- Observe `connectionManager.connectionRefreshNeeded` in `MainViewModel` (already app-scoped)
- On emit: fetch servers via `authRepository.getServers()`, call `connectionManager.findBestConnection()` for each in parallel
- This repopulates the cache with fresh URLs before the next image request

---

## Files impacted

| Action | File |
|--------|------|
| Modified | `core/network/.../ConnectionManager.kt` — network callback + invalidateAll |
| Modified | `data/.../LibraryRepositoryImpl.kt` — smarter clientMap fallback |
| Modified | `app/.../di/image/ImageModule.kt` — reduce connect timeout |
| Modified | `app/.../MainViewModel.kt` — proactive re-discovery on network change |

---

## Verification

1. **Cold start on different network**: Install app, sync on WiFi A, kill app, switch to WiFi B or mobile data, reopen → images should load (via public/relay URLs, not stale local IP)
2. **Network switch while running**: Open app on WiFi A (images load), switch to mobile data → images should reload after brief invalidation
3. **Local server access**: On the same network as server, images load via local IP (fastest path)
4. **Timeout improvement**: Any remaining stale URLs fail in ≤2s instead of 5s
5. **Build**: `./gradlew assembleDebug` passes
