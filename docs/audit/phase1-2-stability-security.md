# Phase 1+2: Stability & Security Audit

**Date**: 2026-03-22
**Auditor**: Claude Opus 4.6 (Stability & Security Audit Agent)
**Scope**: All `.kt` source files, XML configs, ProGuard rules, AndroidManifest

---

## Executive Summary

PlexHubTV demonstrates a mature architecture with solid patterns: `SupervisorJob` in `applicationScope`, a `GlobalCoroutineExceptionHandler` with Crashlytics integration, `EncryptedSharedPreferences` for secrets, proper `AuthInterceptor` 401 handling, and comprehensive backup exclusion rules. The main stability risks are a handful of `!!` non-null assertions in production code that can cause `KotlinNullPointerException` crashes, `collect {}` calls without `.catch {}` in `SettingsViewModel` that could silently freeze UI sections, and tokens embedded in image URLs stored in Room (inherent to the Plex protocol, but noted). No hardcoded API keys or passwords were found in source code. No `GlobalScope` usage was detected.

---

## Phase 1: Stability Findings

### P0 -- Critical (Crash Risk)

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| S-01 | `!!` on `parentIndex` -- items with `null` `parentIndex` are filtered but the `!!` occurs inside `groupBy` on already-filtered list. Safe due to the `.filter` on line 143, but if filter logic changes, this crashes. | `data/.../MediaDetailRepositoryImpl.kt` | 144 | NPE crash if filter is modified | Replace `it.parentIndex!!` with `it.parentIndex ?: return@filter` or use `filterNotNull` mapping |
| S-02 | `!!` on `index` -- same pattern as S-01, `groupBy { it.index!! }` after filter. Crash if upstream data contains nulls that bypass filter. | `data/.../MediaDetailRepositoryImpl.kt` | 147 | NPE crash on corrupted data | Use `it.index ?: 0` or safe accessor |
| S-03 | `!!` on `entity.unificationId` -- if `entity` is non-null but `unificationId` is blank/null, the `hideMediaByUnificationId` call uses `!!` after a `isNullOrBlank()` check. The check guards it, but `entity!!` on line 357 is redundant since `entity` is already proven non-null by the condition. | `data/.../MediaDetailRepositoryImpl.kt` | 357 | NPE if null-check condition is refactored | Use `entity?.unificationId?.let { ... }` |
| S-04 | `!!` on `bestSeasonRatingKey` and `bestSeasonServerId` -- these are only used inside a `.filter { ... != null }` block, but the `!!` occurs after the filter on the same chain. If the filter is removed or reordered, this crashes. | `app/.../MediaDetailViewModel.kt` | 581-582 | NPE crash if filter guard removed | Use safe `?.let { }` or `requireNotNull()` with message |

### P1 -- Important

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| S-05 | `SettingsViewModel` has ~15 `viewModelScope.launch { repo.collect { } }` blocks (lines 769-856) without `.catch {}` or try-catch. If any DataStore flow throws, the collecting coroutine dies silently and that setting stops updating. | `app/.../SettingsViewModel.kt` | 769-856 | Setting toggle stops updating after error | Use `safeCollectIn()` or add `.catch { }` before `.collect {}` |
| S-06 | `MainViewModel.checkForUpdate()` has no try-catch around `updateChecker.checkForUpdate()`. A network error would propagate to `GlobalCoroutineExceptionHandler` but in debug mode, this re-throws and crashes. | `app/.../MainViewModel.kt` | 93-101 | Crash on startup if update check fails (debug only) | Wrap in `try { } catch { Timber.w(...) }` |
| S-07 | `_uiState.value = X` direct assignment used in `AuthViewModel` (9 occurrences), `PersonDetailViewModel` (5), `LoadingViewModel` (5), `PlayerController` (1). These are safe for single-writer patterns but technically lose concurrent updates from other coroutines in the same ViewModel. | `app/.../AuthViewModel.kt` | 71-158 | Potential lost state update in race window | Prefer `_uiState.update { }` for atomic read-modify-write |
| S-08 | `CrlfFixSocket.getInputStream()` has a TOCTOU race: `if (wrappedInput == null)` check + assignment is not synchronized. Multiple threads calling `getInputStream()` could create multiple wrappers, though OkHttp typically calls this once. | `app/.../CrlfFixSocketFactory.kt` | 72-76 | Double-wrapped InputStream (unlikely) | Use `synchronized(this) { }` or `lazy { }` |
| S-09 | `PlayerController` is `@Singleton` holding `Application` context reference and mutable player state (`@Volatile var player`). The `release()` method nulls references correctly, but if a crash occurs mid-release, stale references could remain. | `app/.../PlayerController.kt` | 42-95 | Stale ExoPlayer reference after partial release | Add defensive null-check at start of `initialize()` (already done on line 147) |
| S-10 | `PlayerMediaLoader.loadMedia()` collects a flow with `.collect { }` (line 66) without `.catch {}`. If `getMediaDetailUseCase` flow throws an unexpected exception, the entire `loadMedia` call fails silently. | `app/.../PlayerMediaLoader.kt` | 66 | Media fails to load with no error feedback | Add `.catch { result = Result.failure(it) }` before `.collect` |

### P2 -- Improvement

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| S-11 | `SavedStateHandle` is used in 10+ ViewModels for reading navigation args, but none use it to persist transient UI state (e.g., scroll position, selected tab). Process death restores args but not UI state. | Multiple ViewModels | Various | Loss of UI state on process death | Use `savedStateHandle.saveable` for transient state that should survive process death |
| S-12 | `_uiState.update {}` is used 367 times across 25 files -- good pattern. But `_uiState.value =` is used 21 times across 5 files. This inconsistency creates different concurrency guarantees across the codebase. | Multiple files | Various | Inconsistent state update semantics | Standardize on `_uiState.update {}` everywhere |
| S-13 | `PlayerController.autoSkippedMarkers` is a plain `mutableSetOf()` accessed from coroutines on `mainDispatcher`. Since all accesses are on main thread, this is safe, but not self-documenting. | `app/.../PlayerController.kt` | 79 | Confusing for maintainers | Add comment documenting thread-confinement guarantee, or use `ConcurrentHashMap.newKeySet()` |
| S-14 | No `GlobalScope` usage found (good). `applicationScope` with `SupervisorJob` is properly used for fire-and-forget operations. However, `JellyfinClientResolver` init block launches with `scope.launch {}` (line 42) without try-catch. | `data/.../JellyfinClientResolver.kt` | 42 | Silent failure on token cache load | Add try-catch inside the launch |
| S-15 | `FlowExtensions.safeCollectIn()` provides a good safety net, but many newer ViewModels directly call `viewModelScope.launch { flow.collect {} }` bypassing it. No enforcement mechanism exists. | `core/.../FlowExtensions.kt` | 35-43 | Inconsistent error handling | Lint rule or code review guideline to prefer `safeCollectIn` |
| S-16 | `PlayerController.startPositionTracking()` catches `CancellationException` and re-throws (correct), plus catches generic `Exception` (line 1226). However, the main `loadMedia()` scope.launch on line 773 has no try-catch wrapper. Errors are handled per-operation inside, but a top-level guard would be safer. | `app/.../PlayerController.kt` | 773 | Unexpected error in loadMedia kills coroutine | Add top-level try-catch with state reset |

---

## Phase 2: Security Findings

### P0 -- Critical

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| (none) | No P0 security findings. | | | | |

### P1 -- Important

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| SEC-01 | Plex tokens are embedded in image URLs (`?X-Plex-Token=$token`) and stored in Room database (`thumbUrl`, `artUrl` fields). These URLs are persisted on disk in plaintext SQLite. This is inherent to the Plex API design but means tokens survive in the DB even after logout if `clearAll()` only clears `SecurePreferencesManager`. | `data/.../MediaMapper.kt`, `SyncRepositoryImpl.kt`, etc. | 34-35, 174-187 | Token persistence in Room after logout | Ensure `clearAll()` also truncates media/cache tables, or strip tokens from stored URLs and re-inject at load time |
| SEC-02 | `CollectionSyncWorker` builds URLs with inline token interpolation: `"$baseUrl/library/sections?X-Plex-Token=${server.accessToken}"`. These URLs could appear in Timber debug logs or OkHttp logging interceptor stack traces. | `app/.../CollectionSyncWorker.kt` | 85, 110, 149 | Token in log output (debug builds) | Use `@Url` with query parameter injection via Retrofit, or sanitize logs |
| SEC-03 | `network_security_config.xml` allows cleartext HTTP globally (`cleartextTrafficPermitted="true"`) with only plex.tv/plex.app domains enforcing HTTPS. While justified for IPTV/LAN, this means Plex server tokens sent to local servers over HTTP can be intercepted on the LAN. | `app/.../network_security_config.xml` | 16 | Token interception on untrusted networks | Document the risk; consider HTTPS preference for servers that support it |
| SEC-04 | `REQUEST_INSTALL_PACKAGES` permission is declared in AndroidManifest. This allows the app to install APKs (for self-update). If the update checker is compromised or the download is over HTTP, malicious APKs could be installed. | `app/.../AndroidManifest.xml` | 16 | Sideloaded malicious APK via update mechanism | Ensure update URLs are HTTPS-only; verify APK signature before install |

### P2 -- Improvement

| # | Finding | File | Line | Impact | Fix |
|---|---------|------|------|--------|-----|
| SEC-05 | `SecurePreferencesManager` stores `Context` as a constructor parameter (`@ApplicationContext private val context: Context`). This is Application context (safe for singletons, no Activity leak), properly scoped. | `core/.../SecurePreferencesManager.kt` | 29 | None (correct usage) | No action needed -- noting for completeness |
| SEC-06 | `SecurePreferencesManager.encryptedPrefs` uses `lazy {}` which is not thread-safe by default in Kotlin (it actually IS `LazyThreadSafetyMode.SYNCHRONIZED` by default). Write operations use `synchronized(this)`. Read operations like `getPlexToken()` do NOT use `synchronized`. However, `SharedPreferences.getString()` is inherently thread-safe in Android, so this is acceptable. | `core/.../SecurePreferencesManager.kt` | 51-64, 127-128 | None (acceptable) | No action needed |
| SEC-07 | `AuthInterceptor` uses `AtomicReference` for non-blocking token reads -- excellent thread-safety pattern. The 401 detection only fires for `plex.tv` hosts, preventing rogue local servers from triggering global logout. | `core/.../AuthInterceptor.kt` | 31-32, 76 | None (well-designed) | No action needed |
| SEC-08 | Backup rules properly exclude encrypted prefs, DataStore, and Room database for both cloud backup (API 23-30) and device transfer (API 31+). `android:allowBackup="false"` is set in manifest. | `app/.../backup_rules.xml`, `data_extraction_rules.xml` | All | None (properly secured) | No action needed |
| SEC-09 | `PlayerController.playDirectUrlInternal()` validates URI scheme against allowlist (`http`, `https`, `rtsp`, `rtp`) before passing to ExoPlayer. Subtitle URIs are also validated (line 1046). Good defense-in-depth. | `app/.../PlayerController.kt` | 309-314, 1046 | None (properly secured) | No action needed |
| SEC-10 | ProGuard rules are comprehensive with proper `-keep` rules for Retrofit, Gson, Room, Hilt, Media3, MPV, Firebase, and serialization. Consumer rules for `:data`, `:domain`, and `:core:network` modules properly preserve DI-injected classes. | `app/proguard-rules.pro` + consumer-rules | All | None (complete) | No action needed |
| SEC-11 | Token logging: `LibrarySyncWorker` logs `Token: ${token != null}` (boolean, not the actual token value) -- safe. `JellyfinClientResolver` logs token count, not values -- safe. No instance of actual token values being logged was found. | Various | Various | None (properly sanitized) | No action needed |
| SEC-12 | `plexhub://play` deep link is registered in AndroidManifest. The `ratingKey` parameter should be validated server-side, but a malicious deep link could trigger navigation to a specific media detail. Low risk since it only triggers navigation within the app. | `app/.../AndroidManifest.xml` | 54-57 | Unauthorized navigation trigger | Validate deep link parameters; consider requiring auth state |
| SEC-13 | `putSecret(key, value)` and `getSecret(key)` in `SecurePreferencesManager` are generic methods that accept arbitrary keys. No input validation on key names. | `core/.../SecurePreferencesManager.kt` | 212-220 | Unbounded key storage | Consider restricting to known key constants |

---

## Strengths

The following aspects of the codebase demonstrate strong engineering practices:

1. **Structured Concurrency**: `applicationScope` uses `SupervisorJob()` + `GlobalCoroutineExceptionHandler` (with Crashlytics in release, re-throw in debug). `PlayerController` creates child scopes via `SupervisorJob(applicationScope.coroutineContext[Job])` and properly cancels/recreates on release.

2. **No GlobalScope**: Zero instances of `GlobalScope` found in the entire codebase. All background work uses `applicationScope`, `viewModelScope`, or `WorkManager`.

3. **Encryption**: `SecurePreferencesManager` uses `MasterKey` with `AES256_GCM` scheme, with graceful degradation (corrupted file recovery, `isEncryptionDegraded` flow for UI feedback), and `synchronized` write blocks.

4. **Auth Handling**: `AuthInterceptor` uses `AtomicReference` for lock-free token reads, only triggers global logout for `plex.tv` 401s (not local servers), and `AuthEventBus` + `MainViewModel` coordinate dialog display with deduplication.

5. **Player Robustness**: Codec error detection auto-switches to MPV, network error retry with max 3 attempts, audio focus handling for both ExoPlayer and MPV, `CancellationException` properly re-thrown in position tracker.

6. **Backup Security**: Both `fullBackupContent` (API 23-30) and `dataExtractionRules` (API 31+) exclude encrypted prefs, DataStore, and Room database. `android:allowBackup="false"` is set.

7. **URI Validation**: Direct play URLs and subtitle URIs are validated against scheme allowlists before being passed to ExoPlayer.

8. **Safe Flow Collection**: `safeCollectIn()` extension provides `.catch {}` wrapping out of the box, used in critical paths like `MainViewModel`'s auth event collection.

9. **Error Type System**: `AppError` sealed class hierarchy provides typed error handling with `Throwable.toAppError()` conversion, and `BaseViewModel` standardizes error emission via `Channel<AppError>`.

10. **ProGuard Coverage**: Comprehensive rules covering Retrofit, Gson, Room, Hilt, Media3, MPV, Firebase, Kotlin Serialization, and Coroutines with proper `consumer-rules.pro` for library modules.

---

## Summary Statistics

| Category | Count |
|----------|-------|
| **Total Findings** | **20** |
| **P0 (Critical)** | **4** (all stability, 0 security) |
| **P1 (Important)** | **10** (6 stability, 4 security) |
| **P2 (Improvement)** | **6** (stability) + **9** (security, mostly noting good patterns) |
| `!!` assertions in production code | 6 (4 crash-risk, 1 in test, 1 in socket) |
| `GlobalScope` usage | 0 |
| Hardcoded secrets | 0 |
| ViewModels using `SavedStateHandle` | 10 (for nav args only, not transient state) |
| ViewModels with `onCleared()` | 1 (`PlayerControlViewModel`) |
| `_uiState.value =` (non-atomic) | 21 occurrences across 5 files |
| `_uiState.update {}` (atomic) | 367 occurrences across 25 files |
| `.collect {}` without `.catch {}` | ~20 instances in `SettingsViewModel` + scattered |
| `safeCollectIn()` usage | Used in critical paths (MainViewModel, HomeViewModel, etc.) |
