# Unified Error Handling Design — AppError + safeApiCall

**Date**: 2026-02-21
**Status**: Approved

## Problem

Three error systems coexist:
1. `AppError` sealed class (well-designed, 6 categories, 20+ subtypes) — used in only 4 ViewModels
2. `PlexException` sealed class (4 subclasses) — used in 3 repositories
3. Raw `Result.failure(Exception(...))` — 30+ instances across 8 repositories

`toAppError()` does NOT handle `PlexException` — everything falls to `AppError.Unknown`.

## Decision

- **Convert at repository edge**: Repositories catch all exceptions and wrap them as `AppError` immediately
- **Delete PlexException entirely**: Single error model across the codebase
- **Add `safeApiCall {}` helper**: Eliminates ~30 duplicate 3-layer catch blocks
- **No extra fields** (e.g. `httpCode`): Original exception preserved in `cause` for inspection if needed

## Design

### 1. AppError extends Exception

```kotlin
sealed class AppError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)
```

Non-breaking change — all existing `when` matches, `toUserMessage()`, `isCritical()`, `isRetryable()` continue unchanged.

### 2. safeApiCall helper (core:common)

```kotlin
suspend inline fun <T> safeApiCall(
    tag: String = "",
    crossinline block: suspend () -> T
): Result<T>
```

Handles:
- `UnknownHostException` → `AppError.Network.NoConnection`
- `SocketTimeoutException` → `AppError.Network.Timeout`
- `IOException` → `AppError.Network.ServerError`
- `HttpException` → status-code-aware mapping (401→Unauthorized, 404→NotFound, 5xx→ServerError)
- `AppError` → pass-through (thrown from inside block)
- `Exception` → `AppError.Unknown`

### 3. toAppError() extended

```kotlin
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is UnknownHostException -> AppError.Network.NoConnection(message)
    is SocketTimeoutException -> AppError.Network.Timeout(message)
    is HttpException -> this.toAppError()
    is IOException -> AppError.Network.ServerError(message, this)
    else -> AppError.Unknown(message, this)
}
```

### 4. Repository migration patterns

**Pattern A (80% of methods)**: Replace 3-layer catch with `safeApiCall`:
```kotlin
override suspend fun getMediaDetail(...): Result<MediaItem> {
    val client = serverClientResolver.getClient(serverId)
        ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))
    return safeApiCall("getMediaDetail") {
        val metadata = client.getMetadata(ratingKey).body()?.mediaContainer?.metadata?.firstOrNull()
            ?: throw AppError.Media.NotFound("Media $ratingKey not found")
        mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
    }
}
```

**Pattern B (~4 methods with fallback logic)**: Keep custom try-catch, use `e.toAppError()`:
- `AuthRepositoryImpl.getServers()` — 3-level cache fallback
- `SearchRepositoryImpl.searchOnServer()` — timeout + stale cache fallback
- `LibraryRepositoryImpl.getLibraries()` — offline fallback
- `SearchRepositoryImpl.searchAllServers()` — per-server timeout

### 5. Raw Exception → AppError mapping

| Raw exception message | AppError type |
|---|---|
| `"Not authenticated"` / `"Not logged in"` | `AppError.Auth.InvalidToken` |
| `"Client ID not found"` | `AppError.Auth.InvalidToken` |
| `"Server offline and no cache"` / `"No connection..."` | `AppError.Network.NoConnection` |
| `"API Error: ${code}"` / `"Failed to fetch..."` | `AppError.Network.ServerError` |
| `"Profile not found"` / `"Cannot delete..."` | `AppError.Validation` |
| `"PIN ID missing..."` / `"Failed to get PIN..."` | `AppError.Auth.PinGenerationFailed` |

## Files Impacted

### Created (1)
- `core/common/.../SafeApiCall.kt`

### Deleted (1)
- `core/common/.../exception/PlexExceptions.kt`

### Modified (12)
- `core/model/.../AppError.kt` — extends Exception, toAppError() extended
- `data/.../AuthRepositoryImpl.kt` — 6 methods
- `data/.../MediaDetailRepositoryImpl.kt` — 3 methods
- `data/.../PlaybackRepositoryImpl.kt` — 3 methods
- `data/.../SearchRepositoryImpl.kt` — 2 methods
- `data/.../AccountRepositoryImpl.kt` — 2 methods
- `data/.../LibraryRepositoryImpl.kt` — 1 method
- `data/.../ProfileRepositoryImpl.kt` — 5 methods
- `data/.../SyncRepositoryImpl.kt` — 2 methods
- `data/.../WatchlistRepositoryImpl.kt` — 4 methods
- `data/.../FavoritesRepositoryImpl.kt` — 1 method
- `core/model/.../AppErrorTest.kt` — new tests for safeApiCall, HttpException.toAppError()

### Not Modified
- `ErrorExtensions.kt` — unchanged
- All ViewModels — already consume AppError
- All Use Cases — pass Result<T> transparently
- `OnDeckRepositoryImpl.kt`, `SettingsRepositoryImpl.kt` — no Result.failure() to migrate
