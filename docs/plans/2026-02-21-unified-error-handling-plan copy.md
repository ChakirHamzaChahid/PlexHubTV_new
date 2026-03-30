# Unified Error Handling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Unify error handling across PlexHubTV — standardize on `AppError`, add `safeApiCall` helper, delete `PlexException`, migrate all repositories.

**Architecture:** `AppError` (sealed class extending `Exception`) is the single error model. Repositories convert at the edge via `safeApiCall {}` helper. `PlexException` is deleted. ViewModels already consume `AppError`.

**Tech Stack:** Kotlin sealed classes, Kotlin `Result<T>`, Retrofit `HttpException`, Timber logging.

**Design doc:** `docs/plans/2026-02-21-unified-error-handling-design.md`

---

### Task 1: Make AppError extend Exception

**Files:**
- Modify: `core/model/src/main/java/com/chakir/plexhubtv/core/model/AppError.kt:7-9`

**Step 1: Edit AppError sealed class declaration**

Change line 7-9 from:
```kotlin
sealed class AppError(
    open val message: String? = null,
    open val cause: Throwable? = null
) {
```

To:
```kotlin
sealed class AppError(
    override val message: String? = null,
    override val cause: Throwable? = null
) : Exception(message, cause) {
```

**Step 2: Extend toAppError() to handle AppError pass-through and HttpException**

Replace the existing `toAppError()` function (lines 80-87) with:

```kotlin
fun Throwable.toAppError(): AppError {
    return when (this) {
        is AppError -> this
        is java.net.UnknownHostException -> AppError.Network.NoConnection(this.message)
        is java.net.SocketTimeoutException -> AppError.Network.Timeout(this.message)
        is retrofit2.HttpException -> this.toHttpAppError()
        is java.io.IOException -> AppError.Network.ServerError(this.message, this)
        else -> AppError.Unknown(this.message, this)
    }
}

fun retrofit2.HttpException.toHttpAppError(): AppError {
    return when (code()) {
        401, 403 -> AppError.Network.Unauthorized("HTTP ${code()}")
        404 -> AppError.Network.NotFound("HTTP 404")
        in 500..599 -> AppError.Network.ServerError("HTTP ${code()}", this)
        else -> AppError.Unknown("HTTP ${code()}: ${message()}", this)
    }
}
```

**Step 3: Run existing tests to verify non-breaking**

Run: `./gradlew :core:model:testDebugUnitTest --tests "com.chakir.plexhubtv.core.model.AppErrorTest"`
Expected: All 20 existing tests PASS (no behavioral change).

**Step 4: Commit**

```bash
git add core/model/src/main/java/com/chakir/plexhubtv/core/model/AppError.kt
git commit -m "refactor(ARCH): make AppError extend Exception and extend toAppError()"
```

---

### Task 2: Create safeApiCall helper

**Files:**
- Create: `core/common/src/main/java/com/chakir/plexhubtv/core/common/SafeApiCall.kt`

**Step 1: Create the safeApiCall file**

```kotlin
package com.chakir.plexhubtv.core.common

import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.core.model.toHttpAppError
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wraps a suspend block with standardized error handling.
 * Catches common exceptions and maps them to [AppError].
 *
 * For business-specific early returns (validation, auth checks),
 * use `Result.failure(AppError.Xxx(...))` BEFORE calling safeApiCall,
 * or `throw AppError.Xxx(...)` inside the block.
 */
suspend inline fun <T> safeApiCall(
    tag: String = "",
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: AppError) {
        Result.failure(e)
    } catch (e: UnknownHostException) {
        Timber.e(e, "%s: No connection", tag)
        Result.failure(AppError.Network.NoConnection(e.message))
    } catch (e: SocketTimeoutException) {
        Timber.e(e, "%s: Timeout", tag)
        Result.failure(AppError.Network.Timeout(e.message))
    } catch (e: IOException) {
        Timber.e(e, "%s: Network error", tag)
        Result.failure(AppError.Network.ServerError(e.message, e))
    } catch (e: HttpException) {
        Timber.e(e, "%s: HTTP %d", tag, e.code())
        Result.failure(e.toHttpAppError())
    } catch (e: Exception) {
        Timber.e(e, "%s: Unknown error", tag)
        Result.failure(AppError.Unknown(e.message, e))
    }
}
```

**Step 2: Commit**

```bash
git add core/common/src/main/java/com/chakir/plexhubtv/core/common/SafeApiCall.kt
git commit -m "feat(ARCH): add safeApiCall helper for unified error handling"
```

---

### Task 3: Add tests for safeApiCall and HttpException.toHttpAppError()

**Files:**
- Modify: `core/model/src/test/java/com/chakir/plexhubtv/core/model/AppErrorTest.kt`

**Step 1: Add new test cases at the end of AppErrorTest**

Append after line 211 (before closing `}`):

```kotlin
    // --- New tests: AppError extends Exception ---

    @Test
    fun `AppError is an instance of Exception`() {
        val error = AppError.Network.NoConnection("test")
        assertThat(error).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `AppError can be thrown and caught as Exception`() {
        val caught = try {
            throw AppError.Auth.InvalidToken("expired")
        } catch (e: Exception) {
            e
        }
        assertThat(caught).isInstanceOf(AppError.Auth.InvalidToken::class.java)
        assertThat(caught.message).isEqualTo("expired")
    }

    @Test
    fun `toAppError returns same instance for AppError input`() {
        val original = AppError.Media.NotFound("test")
        val result = original.toAppError()
        assertThat(result).isSameInstanceAs(original)
    }
```

**Step 2: Run tests**

Run: `./gradlew :core:model:testDebugUnitTest --tests "com.chakir.plexhubtv.core.model.AppErrorTest"`
Expected: All tests PASS (20 existing + 3 new).

**Step 3: Commit**

```bash
git add core/model/src/test/java/com/chakir/plexhubtv/core/model/AppErrorTest.kt
git commit -m "test(ARCH): add tests for AppError extends Exception and toAppError pass-through"
```

---

### Task 4: Migrate AuthRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImpl.kt`

**Step 1: Update imports**

Remove:
```kotlin
import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.NetworkException
```

Add:
```kotlin
import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
```

Remove (no longer needed — safeApiCall handles these):
```kotlin
import retrofit2.HttpException
import java.io.IOException
```

**Step 2: Migrate getPin() (lines 57-86)**

Replace with:
```kotlin
override suspend fun getPin(strong: Boolean): Result<AuthPin> {
    var clientId = settingsDataStore.clientId.first()
    if (clientId.isNullOrBlank()) {
        clientId = java.util.UUID.randomUUID().toString()
        settingsDataStore.saveClientId(clientId)
    }

    return safeApiCall("getPin") {
        val response = api.getPin(strong = strong, clientId = clientId)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            val pinId = body.id
                ?: throw AppError.Auth.PinGenerationFailed("PIN ID missing in API response")
            val pinCode = body.code
                ?: throw AppError.Auth.PinGenerationFailed("PIN code missing in API response")
            AuthPin(id = pinId.toString(), code = pinCode)
        } else {
            throw AppError.Auth.PinGenerationFailed("Failed to get PIN: ${response.code()}")
        }
    }
}
```

**Step 3: Migrate checkPin() (lines 88-115)**

Replace with:
```kotlin
override suspend fun checkPin(pinId: String): Result<Boolean> {
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("checkPin") {
        val response = api.getPinStatus(id = pinId, clientId = clientId)
        val body = response.body()

        if (response.isSuccessful && body != null) {
            val authToken = body.authToken
            if (authToken != null) {
                settingsDataStore.saveToken(authToken)
                true
            } else {
                false // Not yet linked
            }
        } else {
            throw AppError.Auth.PinGenerationFailed("Failed to check PIN status: ${response.code()}")
        }
    }
}
```

**Step 4: Migrate loginWithToken() (lines 117-140)**

Replace with:
```kotlin
override suspend fun loginWithToken(token: String): Result<Boolean> {
    val clientId = settingsDataStore.clientId.first()
        ?: java.util.UUID.randomUUID().toString().also { settingsDataStore.saveClientId(it) }

    return safeApiCall("loginWithToken") {
        val response = api.getUser(token = token, clientId = clientId)
        if (response.isSuccessful) {
            settingsDataStore.saveToken(token)
            true
        } else {
            throw AppError.Auth.InvalidToken("Invalid token: ${response.code()}")
        }
    }
}
```

**Step 5: Migrate getHomeUsers() (lines 142-166)**

Replace with:
```kotlin
override suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.core.model.PlexHomeUser>> {
    val token = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("getHomeUsers") {
        val response = api.getHomeUsers(token = token, clientId = clientId)
        val body = response.body()

        if (response.isSuccessful && body != null) {
            body.map { userMapper.mapDtoToDomain(it) }
        } else {
            throw AppError.Network.ServerError("Failed to get home users: ${response.code()}")
        }
    }
}
```

**Step 6: Migrate switchUser() (lines 168-204)**

Replace with:
```kotlin
override suspend fun switchUser(
    user: com.chakir.plexhubtv.core.model.PlexHomeUser,
    pin: String?,
): Result<Boolean> {
    val currentToken = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("switchUser") {
        val response = api.switchUser(
            uuid = user.uuid,
            pin = pin,
            token = currentToken,
            clientId = clientId,
        )
        val body = response.body()

        if (response.isSuccessful && body != null && body.authToken.isNotEmpty()) {
            database.clearAllTables()
            settingsDataStore.saveToken(body.authToken)
            true
        } else {
            throw AppError.Auth.InvalidToken("Failed to switch user: ${response.code()}")
        }
    }
}
```

**Step 7: Migrate getServers() (lines 208-293) — Pattern B (custom fallback)**

This method has 3-level caching with a special IOException fallback to DB cache. Keep custom try-catch but replace exception types:

Replace the catch blocks (lines 261-292) with:
```kotlin
} catch (e: java.io.IOException) {
    val errorMessage = when (e) {
        is java.net.UnknownHostException ->
            "Unable to connect to Plex servers. Check your internet connection."
        is java.net.SocketTimeoutException ->
            "Connection timeout. Plex servers are not responding."
        else ->
            "Network error: ${e.message ?: "Unable to reach Plex servers"}"
    }
    Timber.e(e, "Network error fetching servers")

    // Fallback to DB cache on network error
    try {
        val dbServers = database.serverDao().getAllServers().first()
        if (dbServers.isNotEmpty()) {
            val domainServers = dbServers.map { serverMapper.mapEntityToDomain(it) }
            cachedServers = domainServers
            Timber.w("Using cached servers due to network error (${dbServers.size} servers)")
            return Result.success(domainServers)
        }
    } catch (dbError: Exception) {
        Timber.e(dbError, "DB fallback also failed")
    }

    Result.failure(AppError.Network.NoConnection(errorMessage))
} catch (e: Exception) {
    Timber.e(e, "Error fetching servers")
    Result.failure(e.toAppError())
}
```

Also replace early returns inside the method:
- Line 230: `Result.failure(Exception("Not authenticated"))` → `Result.failure(AppError.Auth.InvalidToken("Not authenticated"))`
- Line 233: `Result.failure(Exception("Client ID not found"))` → `Result.failure(AppError.Auth.InvalidToken("Client ID not found"))`
- Line 259: `Result.failure(AuthException(...))` → `Result.failure(AppError.Network.ServerError("Failed to get servers: ${response.code()}"))`

**Step 8: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate AuthRepositoryImpl to AppError + safeApiCall"
```

---

### Task 5: Migrate MediaDetailRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt`

**Step 1: Update imports**

Remove:
```kotlin
import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.MediaNotFoundException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.common.exception.ServerUnavailableException
import retrofit2.HttpException
import java.io.IOException
```

Add:
```kotlin
import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate getMediaDetail() (lines 39-98)**

Replace the API fallback section (lines 69-97) with:
```kotlin
// 2. API fallback — if not in BDD (new media, not yet synced)
val client = serverClientResolver.getClient(serverId)
    ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

return safeApiCall("getMediaDetail") {
    val response = client.getMetadata(ratingKey)
    if (response.isSuccessful) {
        val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
        if (metadata != null) {
            return@safeApiCall mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
        }
    }
    throw AppError.Media.NotFound("Media $ratingKey not found on server $serverId")
}
```

**Step 3: Migrate getSeasonEpisodes() (lines 100-146)**

Replace lines 123-145 with:
```kotlin
// 2. API fallback: Only if not in cache (new season, not yet synced)
return safeApiCall("getSeasonEpisodes") {
    val client = serverClientResolver.getClient(serverId)
    if (client != null) {
        val response = client.getChildren(ratingKey)
        if (response.isSuccessful) {
            val metadata = response.body()?.mediaContainer?.metadata
            if (metadata != null) {
                return@safeApiCall metadata.map {
                    mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                }
            }
        }
    }
    throw AppError.Media.NotFound("Episodes for $ratingKey not found")
}
```

**Step 4: Migrate getSimilarMedia() (lines 200-249)**

Replace lines 212-248 with:
```kotlin
val client = serverClientResolver.getClient(serverId)
    ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

return safeApiCall("getSimilarMedia") {
    val response = client.getRelated(ratingKey)
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            val hubs = body.mediaContainer?.hubs ?: emptyList()
            Timber.d("Similar: Received ${hubs.size} hubs for $ratingKey")

            val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
            val metadata = similarHub?.metadata ?: emptyList()

            Timber.d("Similar: Found ${metadata.size} items in similar hub")

            val items = metadata.map {
                mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
            }
            similarCache[cacheKey] = System.currentTimeMillis() to items
            return@safeApiCall items
        } else {
            Timber.w("Similar: Response body is null for $ratingKey")
            return@safeApiCall emptyList()
        }
    }
    throw AppError.Network.ServerError("API Error: ${response.code()}")
}
```

Note: The `response` variable inside the else branch needs to be referenced. Since `safeApiCall` wraps the whole block, the non-successful response path should throw. But `response` is scoped within the block so we need to restructure slightly — assign the response first, then check.

Actually, let me restructure to be cleaner:

```kotlin
return safeApiCall("getSimilarMedia") {
    val response = client.getRelated(ratingKey)
    if (!response.isSuccessful) {
        throw AppError.Network.ServerError("Similar: API returned ${response.code()} for $ratingKey")
    }

    val body = response.body()
    if (body == null) {
        Timber.w("Similar: Response body is null for $ratingKey")
        return@safeApiCall emptyList<MediaItem>()
    }

    val hubs = body.mediaContainer?.hubs ?: emptyList()
    Timber.d("Similar: Received ${hubs.size} hubs for $ratingKey")

    val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
    val metadata = similarHub?.metadata ?: emptyList()
    Timber.d("Similar: Found ${metadata.size} items in similar hub")

    val items = metadata.map {
        mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
    }
    similarCache[cacheKey] = System.currentTimeMillis() to items
    items
}
```

**Step 5: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate MediaDetailRepositoryImpl to AppError + safeApiCall"
```

---

### Task 6: Migrate PlaybackRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt`

**Step 1: Update imports**

Remove:
```kotlin
import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.common.exception.ServerUnavailableException
import retrofit2.HttpException
import java.io.IOException
```

Add:
```kotlin
import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate toggleWatchStatus() (lines 40-70)**

Replace with:
```kotlin
override suspend fun toggleWatchStatus(
    media: MediaItem,
    isWatched: Boolean,
): Result<Unit> {
    val client = getClient(media.serverId)
        ?: return Result.failure(AppError.Network.ServerError("Server ${media.serverId} unavailable"))

    return safeApiCall("toggleWatchStatus") {
        val response = if (isWatched) client.scrobble(media.ratingKey) else client.unscrobble(media.ratingKey)

        if (response.isSuccessful) {
            val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
            apiCache.evict(cacheKey)
        } else {
            throw AppError.Network.ServerError("API Error: ${response.code()}")
        }
    }
}
```

**Step 3: Migrate updatePlaybackProgress() (lines 72-116)**

This method has a `finally` block for optimistic UI — keep it but use `safeApiCall` for the main body. Since `safeApiCall` is inline, we can't wrap it in try-finally directly. Instead, restructure:

```kotlin
override suspend fun updatePlaybackProgress(
    media: MediaItem,
    positionMs: Long,
): Result<Unit> {
    val client = getClient(media.serverId)
        ?: return Result.failure(AppError.Network.ServerError("Server ${media.serverId} unavailable"))

    val result = safeApiCall("updatePlaybackProgress") {
        val response = client.updateTimeline(
            ratingKey = media.ratingKey,
            state = "playing",
            timeMs = positionMs,
            durationMs = media.durationMs ?: 0L,
        )

        if (response.isSuccessful) {
            val cacheKey = "${media.serverId}:/library/metadata/${media.ratingKey}"
            apiCache.evict(cacheKey)
        } else {
            throw AppError.Network.ServerError("API Error: ${response.code()}")
        }
    }

    // Update local DB regardless of network status (Optimistic UI / Offline support)
    try {
        mediaDao.updateProgress(
            ratingKey = media.ratingKey,
            serverId = media.serverId,
            viewOffset = positionMs,
            lastViewedAt = System.currentTimeMillis(),
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to update local progress for ${media.ratingKey}")
    }

    return result
}
```

**Step 4: Migrate updateStreamSelection() (lines 176-203)**

Replace with:
```kotlin
override suspend fun updateStreamSelection(
    serverId: String,
    partId: String,
    audioStreamId: String?,
    subtitleStreamId: String?,
): Result<Unit> {
    val client = getClient(serverId)
        ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

    return safeApiCall("updateStreamSelection") {
        val url = "${client.baseUrl}library/parts/$partId"
        val response = api.putStreamSelection(
            url = url,
            audioStreamID = audioStreamId,
            subtitleStreamID = subtitleStreamId,
            token = client.server.accessToken ?: "",
        )
        if (!response.isSuccessful) {
            throw AppError.Network.ServerError("API Error: ${response.code()}")
        }
    }
}
```

**Step 5: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate PlaybackRepositoryImpl to AppError + safeApiCall"
```

---

### Task 7: Migrate AccountRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/AccountRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate getHomeUsers() (lines 23-37)**

Replace with:
```kotlin
override suspend fun getHomeUsers(): Result<List<PlexHomeUser>> {
    val token = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not logged in"))
    val clientId = settingsDataStore.clientId.first() ?: ""

    return safeApiCall("getHomeUsers") {
        val response = api.getHomeUsers(token, clientId)
        if (response.isSuccessful) {
            response.body()?.map { userMapper.mapDtoToDomain(it) } ?: emptyList()
        } else {
            throw AppError.Network.ServerError("API Error: ${response.code()}")
        }
    }
}
```

**Step 3: Migrate switchUser() (lines 39-62)**

Replace with:
```kotlin
override suspend fun switchUser(
    user: PlexHomeUser,
    pin: String?,
): Result<Boolean> {
    val currentToken = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not logged in"))
    val clientId = settingsDataStore.clientId.first() ?: ""

    return safeApiCall("switchUser") {
        val response = api.switchUser(user.uuid, pin, currentToken, clientId)
        if (response.isSuccessful) {
            val body = response.body()
                ?: throw AppError.Network.ServerError("Empty switch response")
            settingsDataStore.saveToken(body.authToken)
            settingsDataStore.saveUser(user.uuid, user.displayName)
            true
        } else {
            throw AppError.Auth.InvalidToken("PIN likely incorrect or API error: ${response.code()}")
        }
    }
}
```

**Step 4: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/AccountRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate AccountRepositoryImpl to AppError + safeApiCall"
```

---

### Task 8: Migrate WatchlistRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate getWatchlist() (lines 29-74)**

Replace with:
```kotlin
override suspend fun getWatchlist(): Result<List<MediaItem>> {
    val token = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("getWatchlist") {
        val allItems = mutableListOf<MediaItem>()
        var offset = 0
        val pageSize = 100

        do {
            val response = api.getWatchlist(
                token = token,
                clientId = clientId,
                start = offset,
                size = pageSize,
            )
            val body = response.body()

            if (!response.isSuccessful || body == null) {
                throw AppError.Network.ServerError("Failed to fetch watchlist: ${response.code()} ${response.message()}")
            }

            val container = body.mediaContainer
            val items = container?.metadata?.map {
                mediaMapper.mapDtoToDomain(it, "watchlist", "https://metadata.provider.plex.tv", token)
            } ?: emptyList()

            allItems.addAll(items)
            offset += pageSize
        } while (offset < (body.mediaContainer?.totalSize ?: 0))

        allItems.toList()
    }
}
```

**Step 3: Migrate addToWatchlist() (lines 76-100)**

Replace with:
```kotlin
override suspend fun addToWatchlist(ratingKey: String): Result<Unit> {
    val token = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("addToWatchlist") {
        val response = api.addToWatchlist(
            ratingKey = ratingKey,
            token = token,
            clientId = clientId,
        )
        if (!response.isSuccessful) {
            throw AppError.Network.ServerError("Failed to add to watchlist: ${response.code()} ${response.message()}")
        }
    }
}
```

**Step 4: Migrate removeFromWatchlist() (lines 102-126)**

Replace with:
```kotlin
override suspend fun removeFromWatchlist(ratingKey: String): Result<Unit> {
    val token = settingsDataStore.plexToken.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
    val clientId = settingsDataStore.clientId.first()
        ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

    return safeApiCall("removeFromWatchlist") {
        val response = api.removeFromWatchlist(
            ratingKey = ratingKey,
            token = token,
            clientId = clientId,
        )
        if (!response.isSuccessful) {
            throw AppError.Network.ServerError("Failed to remove from watchlist: ${response.code()} ${response.message()}")
        }
    }
}
```

**Step 5: Migrate syncWatchlist() (lines 128-170)**

Replace with:
```kotlin
override suspend fun syncWatchlist(): Result<Unit> =
    withContext(Dispatchers.IO) {
        val token = settingsDataStore.plexToken.first()
            ?: return@withContext Result.failure(AppError.Auth.InvalidToken("No token"))
        val clientId = settingsDataStore.clientId.first()
            ?: return@withContext Result.failure(AppError.Auth.InvalidToken("No client ID"))

        safeApiCall("syncWatchlist") {
            val response = api.getWatchlist(token, clientId)
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Failed to fetch watchlist: ${response.code()}")
            }

            val metadata = response.body()?.mediaContainer?.metadata ?: emptyList()
            metadata.forEach { item ->
                val guid = item.guid
                if (guid != null) {
                    val localItems = mediaDao.getAllMediaByGuid(guid)
                    localItems.forEach { local ->
                        favoriteDao.insertFavorite(
                            FavoriteEntity(
                                ratingKey = local.ratingKey,
                                serverId = local.serverId,
                                title = local.title,
                                type = local.type,
                                thumbUrl = local.thumbUrl,
                                artUrl = local.artUrl,
                                year = local.year,
                                addedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }
```

**Step 6: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate WatchlistRepositoryImpl to AppError + safeApiCall"
```

---

### Task 9: Migrate ProfileRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/ProfileRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate createProfile() (lines 51-60)**

Replace with:
```kotlin
override suspend fun createProfile(profile: Profile): Result<Profile> {
    return try {
        profileDao.insertProfile(profile.toEntity())
        Timber.i("Profile created: ${profile.name}")
        Result.success(profile)
    } catch (e: Exception) {
        Timber.e(e, "Failed to create profile: ${profile.name}")
        Result.failure(AppError.Storage.WriteError("Failed to create profile: ${profile.name}", e))
    }
}
```

**Step 3: Migrate updateProfile() (lines 62-71)**

Replace with:
```kotlin
override suspend fun updateProfile(profile: Profile): Result<Profile> {
    return try {
        profileDao.updateProfile(profile.toEntity())
        Timber.i("Profile updated: ${profile.name}")
        Result.success(profile)
    } catch (e: Exception) {
        Timber.e(e, "Failed to update profile: ${profile.name}")
        Result.failure(AppError.Storage.WriteError("Failed to update profile: ${profile.name}", e))
    }
}
```

**Step 4: Migrate deleteProfile() (lines 73-94)**

Replace with:
```kotlin
override suspend fun deleteProfile(profileId: String): Result<Unit> {
    return try {
        val activeProfile = getActiveProfile()
        if (activeProfile?.id == profileId) {
            return Result.failure(AppError.Validation("Cannot delete active profile. Switch to another profile first."))
        }

        val profileCount = getProfileCount()
        if (profileCount <= 1) {
            return Result.failure(AppError.Validation("Cannot delete the last profile."))
        }

        profileDao.deleteProfileById(profileId)
        Timber.i("Profile deleted: $profileId")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Failed to delete profile: $profileId")
        Result.failure(AppError.Storage.WriteError("Failed to delete profile: $profileId", e))
    }
}
```

**Step 5: Migrate switchProfile() (lines 96-113)**

Replace with:
```kotlin
override suspend fun switchProfile(profileId: String): Result<Profile> {
    return try {
        val profile = getProfileById(profileId)
            ?: return Result.failure(AppError.Validation("Profile not found"))

        profileDao.deactivateAllProfiles()
        profileDao.activateProfile(profileId)

        Timber.i("Switched to profile: ${profile.name}")
        Result.success(profile.copy(isActive = true, lastUsed = System.currentTimeMillis()))
    } catch (e: Exception) {
        Timber.e(e, "Failed to switch profile: $profileId")
        Result.failure(AppError.Storage.WriteError("Failed to switch profile: $profileId", e))
    }
}
```

**Step 6: Migrate ensureDefaultProfile() (lines 124-161)**

Replace catch block at line 157-160:
```kotlin
} catch (e: Exception) {
    Timber.e(e, "Failed to ensure default profile")
    throw e
}
```

This method throws (doesn't return Result), so keep as-is. No AppError wrapping needed since callers catch it directly.

**Step 7: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/ProfileRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate ProfileRepositoryImpl to AppError"
```

---

### Task 10: Migrate SyncRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
```

**Step 2: Migrate syncServer() (lines 39-79)**

Replace catch block at lines 75-78:
```kotlin
} catch (e: Exception) {
    Timber.e("Critical failure syncing server ${server.name}: ${e.message}")
    Result.failure(e.toAppError())
}
```

Replace line 45:
```kotlin
val libraries = librariesResult.getOrNull()
    ?: return@withContext Result.failure(AppError.Network.ServerError("Failed to fetch libraries for ${server.name}"))
```

**Step 3: Migrate syncLibrary() (lines 88-183)**

Replace line 95:
```kotlin
val baseUrl = connectionManager.findBestConnection(server)
    ?: return@withContext Result.failure(AppError.Network.NoConnection("No connection to ${server.name}"))
```

Replace line 173:
```kotlin
return@withContext Result.failure(AppError.Network.ServerError("Failed to fetch page: ${response.code()}"))
```

Replace catch block at lines 179-181:
```kotlin
} catch (e: Exception) {
    Timber.e("Error in syncLibrary $libraryKey: ${e.message}")
    Result.failure(e.toAppError())
}
```

**Step 4: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate SyncRepositoryImpl to AppError"
```

---

### Task 11: Migrate LibraryRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
```

**Step 2: Migrate getLibraries() (lines 34-81) — Pattern B (offline fallback)**

Replace line 47:
```kotlin
return Result.failure(AppError.Network.NoConnection("Server offline and no cache"))
```

Replace line 77:
```kotlin
return Result.failure(AppError.Network.ServerError("Failed to fetch sections"))
```

Replace catch block at lines 78-80:
```kotlin
} catch (e: Exception) {
    return Result.failure(e.toAppError())
}
```

**Step 3: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate LibraryRepositoryImpl to AppError"
```

---

### Task 12: Migrate SearchRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/SearchRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
```

**Step 2: Migrate searchAllServers() (lines 41-97) — Pattern B (CancellationException)**

Replace catch block at lines 85-96:
```kotlin
} catch (e: Exception) {
    when (e) {
        is kotlinx.coroutines.CancellationException -> {
            Timber.d("Global search cancelled (user typing)")
        }
        else -> {
            Timber.e(e, "Global search failed")
        }
    }
    Result.failure(e.toAppError())
}
```

**Step 3: Migrate searchOnServer() (lines 99-191) — Pattern B (cache fallback + CancellationException)**

Replace line 128:
```kotlin
Result.failure(AppError.Network.NoConnection("No connection and no cache"))
```

Replace line 163:
```kotlin
Result.failure(AppError.Network.ServerError("Search failed: ${response.code()}"))
```

Replace catch block at lines 165-188:
```kotlin
} catch (e: Exception) {
    when (e) {
        is kotlinx.coroutines.CancellationException -> {
            Timber.d("Search cancelled on ${server.name} (user typing)")
        }
        else -> {
            Timber.e(e, "Network error during search on ${server.name}")
        }
    }
    // Network error → fallback to cache
    if (cached != null) {
        try {
            val fallback = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
            Timber.d("Network error, serving cached results for '$query'")
            Result.success(fallback)
        } catch (parseError: Exception) {
            Result.failure(e.toAppError())
        }
    } else {
        Result.failure(e.toAppError())
    }
}
```

**Step 4: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/SearchRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate SearchRepositoryImpl to AppError"
```

---

### Task 13: Migrate FavoritesRepositoryImpl

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/FavoritesRepositoryImpl.kt`

**Step 1: Update imports**

Add:
```kotlin
import com.chakir.plexhubtv.core.model.AppError
```

**Step 2: Migrate toggleFavorite() catch block (line 154-156)**

Replace:
```kotlin
} catch (e: Exception) {
    Result.failure(e)
}
```

With:
```kotlin
} catch (e: Exception) {
    Result.failure(AppError.Storage.WriteError("Failed to toggle favorite for ${media.title}", e))
}
```

**Step 3: Verify compilation**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/repository/FavoritesRepositoryImpl.kt
git commit -m "refactor(ARCH): migrate FavoritesRepositoryImpl to AppError"
```

---

### Task 14: Delete PlexExceptions.kt

**Files:**
- Delete: `core/common/src/main/java/com/chakir/plexhubtv/core/common/exception/PlexExceptions.kt`

**Step 1: Verify no remaining imports**

Run: `grep -r "PlexException\|NetworkException\|AuthException\|MediaNotFoundException\|ServerUnavailableException" --include="*.kt" data/ core/ app/`

Expected: No matches (all imports removed in previous tasks).

**Step 2: Delete the file**

```bash
rm core/common/src/main/java/com/chakir/plexhubtv/core/common/exception/PlexExceptions.kt
```

**Step 3: Delete the exception directory if empty**

```bash
rmdir core/common/src/main/java/com/chakir/plexhubtv/core/common/exception/ 2>/dev/null || true
```

**Step 4: Verify full project compilation**

Run: `./gradlew :data:compileDebugKotlin :core:common:compileDebugKotlin :core:model:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Run all tests**

Run: `./gradlew :core:model:testDebugUnitTest`
Expected: All tests PASS

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor(ARCH): delete PlexExceptions.kt — unified on AppError"
```

---

### Task 15: Final verification and squash commit

**Step 1: Run full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All tests PASS

**Step 3: Verify no remaining raw Result.failure(Exception(...))**

Run: `grep -rn "Result.failure(Exception(" --include="*.kt" data/`
Expected: No matches

**Step 4: Verify no remaining PlexException references**

Run: `grep -rn "PlexException\|NetworkException\|AuthException\|MediaNotFoundException\|ServerUnavailableException" --include="*.kt" data/ core/ app/`
Expected: No matches (except possibly test files or comments)
