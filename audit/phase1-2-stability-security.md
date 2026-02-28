# PlexHubTV Production Audit Report
## Phase 1: Stability & Crash-Proofing | Phase 2: Security & Data Protection

**Date**: 2026-02-25
**Auditor**: Claude Opus 4.6 (automated)
**Scope**: Full codebase — ViewModels, Repositories, Player subsystem, Network layer, Security components, Workers, Navigation
**Files Audited**: 35+ source files across app/, core/, data/, domain/ modules

---

## Severity Legend

| Severity | Meaning | Action Required |
|----------|---------|-----------------|
| **P0** | Critical — crash, data loss, or security breach in production | Must fix before release |
| **P1** | High — degraded UX, potential crash under edge conditions, security weakness | Fix in next sprint |
| **P2** | Medium — code smell, minor risk, defense-in-depth improvement | Schedule for cleanup |

---

# PHASE 1: STABILITY & CRASH-PROOFING

---

## 1.1 Null Safety & Force-Unwrap Violations

### FINDING S-01: Force-unwrap on authentication result (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/splash/SplashViewModel.kt:143`
**Probability**: Low (guarded by `readyToNavigate` check) | **Impact**: App crash (fatal)

```kotlin
_navigationEvent.send(state.authenticationResult!!)
```

**Problem**: `!!` will throw `KotlinNullPointerException` if `authenticationResult` is null. The `readyToNavigate` property guards this, but a race condition or future refactor could break the invariant.

**Fix**:
```kotlin
state.authenticationResult?.let { result ->
    _navigationEvent.send(result)
} ?: Timber.e("SplashViewModel: authenticationResult null when readyToNavigate=true")
```

---

### FINDING S-02: Force-unwrap on filtered items list (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt:235`
**Probability**: Low (null check on line 232) | **Impact**: App crash (fatal)

```kotlin
state.filteredItems!!
```

**Problem**: Guarded by `if (state.filteredItems != null)` three lines above, but the `!!` is fragile — a refactor moving the line outside the `if` block would crash.

**Fix**:
```kotlin
state.filteredItems?.let { items ->
    // use items safely
}
```

---

## 1.2 Lifecycle & Resource Leaks

### FINDING S-03: Orphaned CoroutineScope in PlayerScrobbler.stop() (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerScrobbler.kt:102`
**Probability**: High (called every playback stop) | **Impact**: Resource leak, orphaned network call

```kotlin
fun stop() {
    // ...
    CoroutineScope(Dispatchers.IO).launch {
        tvChannelManager.updateContinueWatching()
    }
}
```

**Problem**: Creates an unmanaged `CoroutineScope` that is never cancelled. If `stop()` is called rapidly (e.g., user switching episodes), multiple fire-and-forget coroutines accumulate. The scope has no parent job, so exceptions are silently swallowed and the coroutine cannot be cancelled.

**Fix**: Use the parent `PlayerController.scope` or `applicationScope` instead:
```kotlin
fun stop(scope: CoroutineScope) {
    // ...
    scope.launch(Dispatchers.IO) {
        tvChannelManager.updateContinueWatching()
    }
}
```

---

### FINDING S-04: PlayerController @Singleton with manual CoroutineScope lifecycle (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:51,125-146`
**Probability**: Medium | **Impact**: Resource leak, stale state after scope recreation

```kotlin
@Singleton
class PlayerController @Inject constructor(...) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + globalHandler)
    // ...
    fun release() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + globalHandler)
    }
}
```

**Problem**: As a `@Singleton`, `PlayerController` lives for the entire app lifecycle. The manual scope cancel-and-recreate pattern means:
1. Any coroutine launched on the old scope between `cancel()` and recreation is lost
2. `StateFlow` collectors on the old scope will silently stop
3. No structured concurrency — the scope has no parent

**Fix**: Either:
- Use `applicationScope` from Hilt for long-lived work
- Or wrap scope recreation in a mutex to prevent race conditions between cancel and new launches

---

### FINDING S-05: MpvPlayerWrapper lacks error recovery on init failure (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt`
**Probability**: Medium (MPV is fallback path) | **Impact**: Silent failure, player appears stuck

**Problem**: `MPVLib.create(context)` and `MPVLib.init()` can throw native exceptions (e.g., missing libmpv.so on certain architectures). There is no try-catch around initialization, so a failure crashes the fallback player path without recovering to ExoPlayer.

**Fix**:
```kotlin
try {
    MPVLib.create(context)
    MPVLib.init()
} catch (e: Exception) {
    Timber.e(e, "MPV initialization failed, falling back")
    onError?.invoke(e)
    return
}
```

---

### FINDING S-06: Duplicate property observation in MpvPlayerWrapper (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:101`
**Probability**: Low | **Impact**: Minor performance overhead, potential double-callbacks

**Problem**: MPV property observation may be registered twice if `init()` is called multiple times without proper cleanup, leading to duplicate event firing.

**Fix**: Track observation state and guard against re-registration, or unregister in `release()`.

---

## 1.3 Race Conditions & Concurrency

### FINDING S-07: Unbuffered Channels may drop navigation events (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt:38,41`
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt:42,45`
**Probability**: Medium (depends on collector timing) | **Impact**: Lost navigation events, user action ignored

```kotlin
// HomeViewModel
private val _navigationEvents = Channel<HomeNavigationEvent>()
private val _errorEvents = Channel<AppError>()

// SearchViewModel
private val _navigationEvents = Channel<SearchNavigationEvent>()
private val _errorEvents = Channel<AppError>()
```

**Problem**: `Channel()` defaults to `RENDEZVOUS` capacity (0). If the UI is not actively collecting when an event is sent (e.g., during config change or between compositions), the `send()` call suspends indefinitely or the event is lost. This is a known pattern bug in Compose navigation.

**Fix**: Use `Channel(Channel.BUFFERED)` or `Channel(1, BufferOverflow.DROP_OLDEST)`:
```kotlin
private val _navigationEvents = Channel<HomeNavigationEvent>(Channel.BUFFERED)
private val _errorEvents = Channel<AppError>(Channel.BUFFERED)
```

---

### FINDING S-08: AuthViewModel polling uses wrong isActive check (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthViewModel.kt:110`
**Probability**: Low | **Impact**: Potential polling after ViewModel cleared

```kotlin
while (viewModelScope.isActive) {
    delay(pollingInterval)
    // ...
}
```

**Problem**: `viewModelScope.isActive` checks the scope's job, but within a launched coroutine, you should check `isActive` (the coroutine's own property) or use `currentCoroutineContext().isActive`. While functionally similar in most cases, checking the outer scope can mask cancellation of the specific coroutine.

**Fix**:
```kotlin
while (isActive) {  // checks current coroutine's job
    delay(pollingInterval)
    // ...
}
```

---

## 1.4 Process Death Recovery

### FINDING S-09: No SavedStateHandle in HomeViewModel or SearchViewModel (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt`
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt`
**Probability**: Medium (Android TV less prone to process death) | **Impact**: State loss on process recreation

**Problem**: `HomeViewModel` and `SearchViewModel` do not use `SavedStateHandle` to persist UI state. On process death and recreation (common on low-memory Android TV devices), the current scroll position, search query, active filters, and loaded data are all lost. The user returns to the default state.

**Fix**: Save critical UI state (search query, scroll position, active section) to `SavedStateHandle`. Other ViewModels (LibraryViewModel, MediaDetailViewModel, PlayerControlViewModel) correctly use `SavedStateHandle`.

---

## 1.5 Error Handling

### FINDING S-10: LibrarySyncWorker always returns Result.success() (P1)

**File**: `data/src/main/java/com/chakir/plexhubtv/data/worker/LibrarySyncWorker.kt:210`
**Probability**: High (sync failures happen on unstable networks) | **Impact**: WorkManager believes sync succeeded, skips retry

```kotlin
return Result.success()  // Always, even if sync partially failed
```

**Problem**: The worker catches all exceptions and always returns `Result.success()`. This means:
1. WorkManager will not retry failed syncs
2. The UI shows stale data without indication
3. Periodic sync schedule continues as if nothing failed

**Fix**: Return `Result.retry()` for transient errors (network) and `Result.failure()` for permanent errors:
```kotlin
return if (hadTransientError) Result.retry() else Result.success()
```

---

### FINDING S-11: android.util.Log usage bypasses Timber/Crashlytics (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:312`
**Probability**: Always (every playback) | **Impact**: Log leaks to logcat in production, bypasses log stripping

```kotlin
Log.d("METRICS", "Playback started in ${elapsed}ms (buffer: ${bufferMs}ms)")
```

**Problem**: Direct `android.util.Log` call bypasses Timber's planted tree, meaning:
1. In release builds, this line will still log to logcat (Timber.DebugTree is not planted in release)
2. ProGuard rules strip `Timber.*` calls but NOT `Log.*` calls (unless explicitly configured)
3. Performance metrics may contain timing information useful to attackers

**Fix**: Replace with `Timber.d("Playback started in %dms (buffer: %dms)", elapsed, bufferMs)`

---

## 1.6 Player Robustness

### FINDING S-12: PlayerTrackController subtitle selection lacks null safety (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt`
**Probability**: Low | **Impact**: Subtitle track fails to load silently

**Problem**: When building subtitle track URIs, the code constructs URLs using string interpolation with values from the Plex API. If `stream.key` is null or the server connection is stale, the resulting URI is malformed. No validation of the constructed URI occurs before passing it to the player.

**Fix**: Validate constructed URIs with `Uri.parse()` and check for null scheme/host before use.

---

### FINDING S-13: PlayerController token embedded in subtitle URL (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:619`
**Probability**: Always (when subtitles are loaded) | **Impact**: Token exposure in logs, crash reports, and ExoPlayer internals

```kotlin
Uri.parse("$baseUrl${stream.key}?X-Plex-Token=$token")
```

**Problem**: The Plex auth token is embedded directly in the subtitle URL. ExoPlayer may log this URL in its internal debug logs, error reports, and it will appear in any stack trace or crash report involving subtitle loading. This is also a cross-reference with Security finding SEC-02.

**Fix**: Use ExoPlayer's `DataSource.Factory` with a custom `DefaultHttpDataSource` that injects the token as a header instead of a query parameter, or use a `ResolvingDataSource` to inject the token at request time.

---

## 1.7 Memory Management

### FINDING S-14: SettingsViewModel field injection of TvChannelManager (P2)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt:47-48`
**Probability**: Low | **Impact**: Potential initialization order issue

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(...) {
    @Inject lateinit var tvChannelManager: TvChannelManager
```

**Problem**: Field injection (`@Inject lateinit var`) in a `@HiltViewModel` is unusual and fragile. Hilt injects constructor parameters first, then fields. If any `init {}` block or constructor logic accesses `tvChannelManager` before field injection completes, it throws `UninitializedPropertyAccessException`.

**Fix**: Move `tvChannelManager` to constructor injection:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    // existing params...
    private val tvChannelManager: TvChannelManager,
) : ViewModel() {
```

---

# PHASE 2: SECURITY & DATA PROTECTION

---

## 2.1 Network Security

### FINDING SEC-01: Cleartext traffic globally permitted (CONTRADICTS COMMENTS) (P0)

**File**: `app/src/main/res/xml/network_security_config.xml:14-15`
**Probability**: Always | **Impact**: All HTTP traffic sent in cleartext, tokens/credentials exposed on network

```xml
<!-- TODO: Re-enable cleartext blocking after debugging network issues -->
<base-config cleartextTrafficPermitted="true">
```

**Problem**: The comment on lines 3-6 states "Cleartext (HTTP) traffic is BLOCKED by default" but the actual `base-config` on line 15 sets `cleartextTrafficPermitted="true"`. This is a **critical contradiction**. The TODO on line 14 confirms this was a temporary debug change that was never reverted. This means:
1. ALL HTTP traffic is allowed to ALL domains
2. Auth tokens sent over HTTP can be intercepted on public networks
3. Plex API calls to `plex.tv` could be downgraded to HTTP
4. The security documentation is actively misleading

**Fix**: Revert to the intended configuration:
```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors>
        <certificates src="system" />
    </trust-anchors>
</base-config>
```

---

### FINDING SEC-02: No certificate pinning for plex.tv (P1)

**File**: `app/src/main/res/xml/network_security_config.xml`
**File**: `core/network/src/main/java/com/chakir/plexhubtv/core/network/di/NetworkModule.kt`
**Probability**: Low (requires active MITM) | **Impact**: Token theft via compromised CA

**Problem**: No certificate pinning is configured for `plex.tv` (the authentication endpoint). A compromised or rogue Certificate Authority could issue a valid certificate for `plex.tv`, enabling man-in-the-middle attacks on authentication flows, exposing the user's Plex auth token.

**Fix**: Add certificate pinning in `network_security_config.xml`:
```xml
<domain-config>
    <domain includeSubdomains="true">plex.tv</domain>
    <pin-set expiration="2027-01-01">
        <pin digest="SHA-256">BASE64_ENCODED_HASH</pin>
        <!-- backup pin -->
        <pin digest="SHA-256">BACKUP_BASE64_ENCODED_HASH</pin>
    </pin-set>
</domain-config>
```

---

### FINDING SEC-03: Custom TrustManager accepts self-signed certs for private IPs (P2)

**File**: `core/network/src/main/java/com/chakir/plexhubtv/core/network/di/NetworkModule.kt:132-206`
**Probability**: Always (for LAN servers) | **Impact**: Accepted risk for LAN, but widens attack surface

```kotlin
// Custom X509ExtendedTrustManager that accepts self-signed for private IPs
// isPrivateAddress() checks 10.x.x.x, 172.16-31.x.x, 192.168.x.x
```

**Problem**: The custom TrustManager bypasses certificate validation for any server on a private IP range. While necessary for self-hosted Plex servers with self-signed certs, this means:
1. Any device on the local network can impersonate a Plex server
2. Corporate networks with private IPs are included in the bypass
3. VPN connections that use private IP ranges bypass cert validation

**Mitigation**: This is an accepted trade-off for self-hosted Plex support. Consider:
- Prompting the user to trust specific certificates (TOFU model)
- Logging when self-signed cert is accepted

---

### FINDING SEC-04: HTTP logging includes full request/response bodies in debug (P2)

**File**: `core/network/src/main/java/com/chakir/plexhubtv/core/network/di/NetworkModule.kt:71`
**Probability**: Only in debug builds | **Impact**: Token/credential exposure in debug logcat

```kotlin
if (BuildConfig.DEBUG) {
    addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
}
```

**Problem**: `Level.BODY` logs complete request and response bodies, including:
- Auth tokens in headers and URL parameters
- User data in responses
- PIN codes during authentication

While this is debug-only, shared debug APKs or connected logcat sessions can expose this data.

**Fix**: Use `Level.HEADERS` instead, or redact sensitive headers:
```kotlin
level = HttpLoggingInterceptor.Level.HEADERS
// Or use a custom logger that redacts X-Plex-Token
```

---

## 2.2 Sensitive Data Storage

### FINDING SEC-05: SecurePreferencesManager falls back to unencrypted storage (P0)

**File**: `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SecurePreferencesManager.kt:52-56`
**Probability**: Medium (EncryptedSharedPreferences can fail on first boot, ROM issues, Keystore corruption) | **Impact**: Auth tokens stored in plaintext

```kotlin
} catch (e: Exception) {
    Timber.e(e, "EncryptedSharedPreferences failed, falling back to plain SharedPreferences")
    context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
}
```

**Problem**: When `EncryptedSharedPreferences` fails (Keystore corruption, first-boot race conditions on some Android TV OEMs), the fallback stores auth tokens, API keys, and server URLs in **plain-text SharedPreferences**. This data is:
1. Readable via `adb backup` (since `allowBackup="true"` -- see SEC-07)
2. Accessible on rooted devices
3. Persisted without the user's knowledge that encryption failed
4. **Never re-encrypted** when the Keystore recovers

**Fix**: Do NOT fall back to plain storage. Instead:
```kotlin
} catch (e: Exception) {
    Timber.e(e, "EncryptedSharedPreferences failed")
    // Clear corrupted master key and retry once
    try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(context, PREFS_FILE, masterKey, ...)
    } catch (e2: Exception) {
        // Force re-authentication rather than storing tokens in plaintext
        throw SecurityException("Cannot create secure storage", e2)
    }
}
```

---

### FINDING SEC-06: IPTV playlist URL stored unencrypted in DataStore (P2)

**File**: `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`
**Probability**: Always (when IPTV is configured) | **Impact**: Playlist URL (potentially with embedded credentials) readable

**Problem**: The IPTV M3U playlist URL is stored in plain DataStore Preferences. Many IPTV providers embed authentication tokens or usernames in their M3U URLs (e.g., `http://provider.com/get.php?username=X&password=Y`). This URL is:
1. Readable via `adb backup`
2. Not encrypted
3. Potentially contains IPTV service credentials

**Fix**: Store IPTV playlist URL in `SecurePreferencesManager` instead of DataStore.

---

### FINDING SEC-07: allowBackup="true" enables data extraction (P1)

**File**: `app/src/main/AndroidManifest.xml:26`
**Probability**: Requires physical access or ADB | **Impact**: Full app data extraction including tokens

```xml
android:allowBackup="true"
```

**Problem**: With `allowBackup="true"`, an attacker with ADB access can extract the entire app data directory via `adb backup`, including:
1. SharedPreferences (potentially unencrypted -- see SEC-05)
2. Room database with user library, history, favorites
3. DataStore with all settings and IPTV URLs
4. Any cached files

Combined with SEC-05 (plaintext fallback), this is a token extraction vector.

**Fix**:
```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
```
Create `data_extraction_rules.xml` to explicitly exclude sensitive paths for Android 12+ cloud backup.

---

## 2.3 Token Management

### FINDING SEC-08: Token embedded in URLs (subtitle, thumbnail, stream) (P1)

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:619`
**File**: Various thumbnail URL constructions throughout ViewModels
**Probability**: Always | **Impact**: Token in logs, crash reports, URL history

**Problem**: The `X-Plex-Token` is appended as a query parameter in multiple URL constructions:
- Subtitle URLs (PlayerController:619)
- Direct play URLs
- Thumbnail/art URLs

This means the token appears in:
1. ExoPlayer internal logs and error messages
2. Firebase Crashlytics crash reports (URL in stack traces)
3. OkHttp connection pool logs
4. Android system URL caches

**Fix**: For ExoPlayer media sources, use header-based authentication via a custom `DataSource.Factory`:
```kotlin
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setDefaultRequestProperties(mapOf("X-Plex-Token" to token))
```
For thumbnails (Coil/Glide), configure the image loader with an interceptor that adds the token header.

---

### FINDING SEC-09: AuthInterceptor caches token in AtomicReference (P2)

**File**: `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt:31-32`
**Probability**: Low | **Impact**: Stale token after logout if not cleared

**Problem**: The `AtomicReference<String?>` caches the auth token for non-blocking reads. If the user logs out and the token is not explicitly cleared from this cache, subsequent requests may still carry the old token until the next token refresh cycle.

**Fix**: Ensure logout flow calls `authInterceptor.clearCachedToken()` and verify this path exists and is used.

---

## 2.4 API Key Management

### FINDING SEC-10: API keys in BuildConfig (P2)

**File**: `data/src/main/java/com/chakir/plexhubtv/data/ApiKeyManager.kt`
**Probability**: Always | **Impact**: API keys extractable from APK

```kotlin
// Priority: BuildConfig > DataStore
val tmdbKey = BuildConfig.TMDB_API_KEY
val omdbKey = BuildConfig.OMDB_API_KEY
```

**Problem**: `BuildConfig` constants are compiled into the APK as plain strings. Even with R8/ProGuard obfuscation, these strings are trivially extractable via `apktool` or `strings` on the DEX file. For TMDb/OMDb, the risk is:
1. Quota abuse by extracting and reusing the keys
2. Key revocation affecting all users if abused

**Fix**: For low-sensitivity keys (TMDb/OMDb are free-tier), this is an accepted risk. For higher security:
- Use a backend proxy that holds the actual API key
- Or use Google Play Integrity API to validate requests

---

## 2.5 ProGuard/R8 Configuration

### FINDING SEC-11: ProGuard rules are comprehensive but lack Log stripping (P2)

**File**: `app/proguard-rules.pro`
**File**: `core/network/proguard-rules.pro`
**Probability**: Always in release | **Impact**: Debug information in release APK

**Problem**: The ProGuard configuration correctly keeps Retrofit, Gson, Room, Media3, MPV, and Firebase classes. However, there is no rule to strip `android.util.Log` calls in release builds. The `Log.d("METRICS", ...)` call in PlayerController (Finding S-11) will remain in the release APK.

**Fix**: Add to `proguard-rules.pro`:
```
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
```

---

## 2.6 Content Validation

### FINDING SEC-12: M3uParser URL scheme validation is correct (POSITIVE)

**File**: `data/src/main/java/com/chakir/plexhubtv/data/iptv/M3uParser.kt:13,63-69`

```kotlin
private val ALLOWED_STREAM_SCHEMES = setOf("http", "https", "rtsp", "rtp")

private fun isAllowedStreamUrl(url: String): Boolean {
    val scheme = Uri.parse(url).scheme?.lowercase()
    // ...
}
```

**Assessment**: The M3U parser correctly validates URL schemes against an allowlist, blocking `file://`, `content://`, and `javascript:` URIs. This prevents local file exfiltration and code injection through malicious M3U playlists.

---

### FINDING SEC-13: Deep link scheme plexhub://play lacks input validation (P1)

**File**: `app/src/main/AndroidManifest.xml:52`
**Probability**: Low (requires user to click malicious link) | **Impact**: Potential navigation hijacking

```xml
<data android:scheme="plexhub" android:host="play" />
```

**Problem**: The `plexhub://play` deep link scheme is registered but the handler needs to validate:
1. That `ratingKey` and `serverId` parameters are sanitized
2. That the user is authenticated before processing
3. That the intent data does not contain injection payloads

An attacker could craft `plexhub://play?ratingKey=../../malicious&serverId=attacker-server` to potentially navigate the user to unexpected content or trigger unintended API calls.

**Fix**: Validate deep link parameters in the Activity's `onCreate`/`onNewIntent`:
```kotlin
val ratingKey = intent.data?.getQueryParameter("ratingKey")
    ?.takeIf { it.matches(Regex("^[0-9]+$")) }
    ?: return // reject invalid ratingKey
```

---

## 2.7 Permissions

### FINDING SEC-14: Permissions are minimal and appropriate (POSITIVE)

**File**: `app/src/main/AndroidManifest.xml`

**Assessment**: The manifest requests only:
- `INTERNET` — required for Plex API
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — required for WorkManager foreground
- `POST_NOTIFICATIONS` — runtime permission (Android 13+)
- `RECEIVE_BOOT_COMPLETED` — for periodic sync scheduling

No dangerous permissions (camera, microphone, location, contacts, storage) are requested. This is a well-scoped permission set for the app's functionality.

---

## 2.8 Encryption

### FINDING SEC-15: Conscrypt provider installation is correct (POSITIVE)

**File**: `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`

**Assessment**: The app installs Conscrypt as the default security provider at startup, ensuring modern TLS versions and cipher suites are available even on older Android TV devices with outdated system providers. GMS Security Provider is also installed as a backup. This is best practice.

---

# SUMMARY TABLE

| ID | Severity | Category | File | Description |
|----|----------|----------|------|-------------|
| SEC-01 | **P0** | Network | network_security_config.xml:15 | Cleartext traffic globally permitted (contradicts comments) |
| SEC-05 | **P0** | Storage | SecurePreferencesManager.kt:52-56 | Fallback to unencrypted SharedPreferences |
| S-03 | **P1** | Lifecycle | PlayerScrobbler.kt:102 | Orphaned CoroutineScope on every stop() |
| S-04 | **P1** | Lifecycle | PlayerController.kt:51,125 | Manual scope lifecycle in @Singleton |
| S-05 | **P1** | Player | MpvPlayerWrapper.kt | No error recovery on native init failure |
| S-07 | **P1** | Concurrency | HomeViewModel.kt:38,41 / SearchViewModel.kt:42,45 | Unbuffered Channels drop navigation events |
| S-10 | **P1** | Error Handling | LibrarySyncWorker.kt:210 | Always returns Result.success() |
| SEC-02 | **P1** | Network | network_security_config.xml | No certificate pinning for plex.tv |
| SEC-07 | **P1** | Storage | AndroidManifest.xml:26 | allowBackup="true" enables data extraction |
| SEC-08 | **P1** | Token | PlayerController.kt:619 | Token embedded in URLs (logs, crash reports) |
| SEC-13 | **P1** | Validation | AndroidManifest.xml:52 | Deep link input not validated |
| S-01 | **P2** | Null Safety | SplashViewModel.kt:143 | Force-unwrap on auth result |
| S-02 | **P2** | Null Safety | LibrariesScreen.kt:235 | Force-unwrap on filtered items |
| S-06 | **P2** | Player | MpvPlayerWrapper.kt:101 | Duplicate property observation |
| S-08 | **P2** | Concurrency | AuthViewModel.kt:110 | Wrong isActive scope check |
| S-09 | **P2** | Process Death | HomeViewModel / SearchViewModel | No SavedStateHandle for state restoration |
| S-11 | **P2** | Logging | PlayerController.kt:312 | android.util.Log bypasses Timber |
| S-14 | **P2** | DI | SettingsViewModel.kt:47-48 | Field injection in @HiltViewModel |
| S-12 | **P2** | Player | PlayerTrackController.kt | Subtitle URI not validated |
| SEC-03 | **P2** | Network | NetworkModule.kt:132-206 | Self-signed cert bypass on private IPs |
| SEC-04 | **P2** | Network | NetworkModule.kt:71 | Full body logging in debug |
| SEC-06 | **P2** | Storage | SettingsDataStore.kt | IPTV URL (may contain creds) unencrypted |
| SEC-09 | **P2** | Token | AuthInterceptor.kt:31-32 | Token cache not cleared on logout |
| SEC-10 | **P2** | API Keys | ApiKeyManager.kt | BuildConfig keys extractable from APK |
| SEC-11 | **P2** | ProGuard | proguard-rules.pro | No Log.* stripping rule |
| SEC-12 | PASS | Validation | M3uParser.kt:13 | URL scheme validation correct |
| SEC-14 | PASS | Permissions | AndroidManifest.xml | Minimal, appropriate permissions |
| SEC-15 | PASS | Encryption | PlexHubApplication.kt | Conscrypt + GMS provider correct |

---

# PRIORITY FIX ORDER

## Immediate (P0) — Must fix before any release
1. **SEC-01**: Set `cleartextTrafficPermitted="false"` in `network_security_config.xml` base-config
2. **SEC-05**: Remove plaintext SharedPreferences fallback in SecurePreferencesManager

## Next Sprint (P1) — Fix within 1-2 weeks
3. **S-03**: Replace orphaned CoroutineScope in PlayerScrobbler with parent scope
4. **S-07**: Add `Channel.BUFFERED` to navigation/error channels in HomeViewModel and SearchViewModel
5. **S-10**: Return `Result.retry()` for transient errors in LibrarySyncWorker
6. **SEC-07**: Set `allowBackup="false"` in AndroidManifest
7. **SEC-08**: Move token from URL query params to request headers for media sources
8. **SEC-13**: Validate deep link parameters with regex patterns
9. **S-04**: Consider applicationScope pattern for PlayerController
10. **S-05**: Add try-catch around MpvPlayerWrapper native initialization

## Scheduled Cleanup (P2) — Within 1-2 months
11. **S-01/S-02**: Replace `!!` with safe calls
12. **S-11/SEC-11**: Replace Log.d with Timber and add ProGuard stripping rule
13. **S-14**: Move to constructor injection for SettingsViewModel
14. **SEC-02**: Add certificate pinning for plex.tv
15. **SEC-04**: Reduce HTTP log level to HEADERS in debug
16. Remaining P2 items

---

*Report generated by automated audit. All findings include specific file paths and line numbers for verification. Code snippets are from the actual codebase as of 2026-02-25.*
