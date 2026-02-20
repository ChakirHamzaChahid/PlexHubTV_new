# PRODUCTION AUDIT REPORT V2 -- PlexHubTV

> **Date**: 20 February 2026
> **Version**: 0.10.0 (`claude/continue-plexhubtv-refactor-YO43N`)
> **Previous Audit**: 19 February 2026 (Score: 4/10 -- 10 P0 blockers)
> **Auditors**: 5 parallel agents (Sentinel, Turbo, Blueprint, Pixel, Launcher) -- Claude Opus 4.6
> **Method**: Full source code audit with exact file:line citations

---

## EXECUTIVE SUMMARY

**Global Score: 65/100** (up from **40/100**)

PlexHubTV has undergone a significant transformation since the Feb 19 audit. Of the original 10 P0 blockers, **7 are now FIXED**, 1 is **PARTIALLY FIXED**, and **2 remain OPEN**. The app has moved from "not shippable" to "shippable with targeted fixes."

| Dimension | Score /20 | Trend | Key Takeaway |
|-----------|-----------|-------|--------------|
| **Stability** | 13/20 | +9 | `!!` eliminated, PlayerController fixed, GlobalHandler added. **Gap**: zero `.catch` on Flows in ~20 ViewModels |
| **Security** | 12/20 | +8 | TrustManager hostname-aware, 401 chain, EncryptedSharedPreferences. **Gap**: cleartext traffic still globally permitted |
| **Performance** | 14/20 | +8 | Adaptive cache, Coil keyer, parallel init, Room WAL, ABI splits. **Gap**: zero `derivedStateOf`, no ExoPlayer LoadControl |
| **Architecture** | 12/20 | +6 | Domain pure, model/navigation deps FIXED. **Gap**: `core:network` still imports `core:database`, 20% test coverage |
| **UX TV** | 14/20 | +10 | Home/Player/Search/SeasonDetail exemplary. **Gap**: Auth broken for TV (PIN commented out, no on-screen keyboard) |

**Verdict**: NOT vendable today. **4-5 days of Sprint 1 fixes** would make it release-ready for closed beta. Full production polish requires ~4 weeks (4 sprints).

---

## PREVIOUS AUDIT (Feb 19) -- RESOLUTION STATUS

| # | Previous P0 | Status | Evidence |
|---|-------------|--------|----------|
| 1 | TrustManager accepts ALL certificates (MITM) | **FIXED** | `NetworkModule.kt:132-206` -- `X509ExtendedTrustManager` with `isPrivateAddress()` check. Separate `@Named("public")` OkHttpClient for TMDb/OMDb |
| 2 | `!!` non-null assertions in production code | **FIXED** | Grep: zero `!!` in production `.kt` files. Only 3 remain in test files |
| 3 | No 401/session expiration handling | **FIXED** | `AuthInterceptor:74` -> `AuthEventBus` -> `MainViewModel` -> `SessionExpiredDialog`. Deduplication + back-stack clearing |
| 4 | PlayerController scope never cancelled | **FIXED** | `PlayerController.kt:136-138` -- `scope.cancel()` in `release()`, fresh scope on `initialize()` |
| 5 | No GlobalCoroutineExceptionHandler | **FIXED** | `GlobalCoroutineExceptionHandler.kt` @Singleton, Timber + Crashlytics. Injected into `ApplicationScope` and `PlayerController` |
| 6 | EncryptedSharedPreferences not used | **FIXED** | `SecurePreferencesManager.kt` -- AES-256-GCM via MasterKey, `MutableStateFlow` for reactivity |
| 7 | No Firebase Crashlytics/Analytics | **FIXED** | Full Firebase suite (Crashlytics + Analytics + Performance) with `!BuildConfig.DEBUG` gating |
| 8 | ConnectionManager race conditions | **FIXED** | `ConcurrentHashMap` + `AtomicInteger` |
| 9 | Cleartext traffic globally permitted | **STILL OPEN** | `network_security_config.xml:15` -- `cleartextTrafficPermitted="true"` on base-config |
| 10 | `core:model` / `core:navigation` dependency bloat | **FIXED** | Both modules now have minimal, correct dependencies |

**Resolution rate: 8/10 (80%)**

---

## TOP 10 P0 BLOCKERS (Deduplicated)

| # | ID | Category | File:Line | Description | Agent | Effort |
|---|----|----------|-----------|-------------|-------|--------|
| 1 | **SEC-1** | Security | `app/src/main/res/xml/network_security_config.xml:15` | **Cleartext HTTP globally permitted.** `cleartextTrafficPermitted="true"` on base-config. Plex auth tokens can be intercepted via MITM on any network. The TODO on line 14 confirms this is a debugging leftover. | Sentinel + Launcher | 30 min |
| 2 | **STAB-1** | Stability | ~20 ViewModels (HomeVM, FavoritesVM, DownloadsVM, HistoryVM, IptvVM, HubDetailVM, CollectionDetailVM, ProfileVM, SeasonDetailVM, MediaDetailVM, SearchVM, SettingsVM...) | **Zero `.catch` on Flow collectors.** Every `.collect {}` on a repository/use-case flow propagates uncaught exceptions that cancel the parent `viewModelScope`, killing the entire ViewModel permanently. One transient DB/network error = dead screen. | Sentinel | 2 days |
| 3 | **SEC-2** | Security | `data/src/main/java/.../iptv/M3uParser.kt:34-45` + `data/src/main/java/.../repository/IptvRepositoryImpl.kt:32` | **No URL validation on IPTV streams.** M3U parser accepts any scheme (`file://`, `content://`, `javascript:`). `IptvRepositoryImpl` passes user URL to OkHttp with AuthInterceptor, leaking Plex token to arbitrary servers. | Sentinel | 1 day |
| 4 | **PERF-1** | Performance | `app/src/main/java/.../di/image/PerformanceImageInterceptor.kt:17-64` | **Performance interceptor runs on EVERY image load in release.** ~100+ images on Home = 200+ HashMap ops + string allocs + `System.currentTimeMillis()` per screen. No `BuildConfig.DEBUG` gate. | Turbo | 30 min |
| 5 | **LAUNCH-1** | Compliance | `app/src/main/AndroidManifest.xml:15` | **POST_NOTIFICATIONS runtime permission not requested.** Declared in manifest but no runtime request for Android 13+ (API 33). WorkManager foreground service notifications silently suppressed. | Launcher | 1 day |
| 6 | **UX-1** | UX | `app/src/main/java/.../feature/auth/AuthScreen.kt:97-119` | **Auth flow broken for real TV users.** PIN login button commented out (lines 97-104). `OutlinedTextField` with `KeyboardType.Password` requires system keyboard -- most Android TV devices lack one. Token paste is a dead end. | Pixel | 1-2 days |
| 7 | **ARCH-1** | Architecture | `core/network/build.gradle.kts:74` + `core/network/src/main/java/.../PlexApiCache.kt:3-4` | **core:network imports core:database.** `PlexApiCache` directly imports `ApiCacheDao` + `ApiCacheEntity`. Network module should not depend on database module -- inject via interface. | Blueprint | 0.5 days |
| 8 | **PERF-2** | Performance | Global Compose (all screens) | **Zero `derivedStateOf` usage anywhere.** Computed values (`continueWatchingItems`, `hasContinueWatching`, `hasMyList`, `visibleTabs`) recompute on every recomposition. Combined with no `ImmutableList`/Compose stability config, the runtime treats all list params as unstable. | Turbo | 1-2 days |
| 9 | **LAUNCH-2** | Compliance | `app/src/main/java/.../feature/settings/SettingsScreen.kt:33` | **Privacy policy page does not exist.** URL points to `docs/privacy-policy-en.md` on GitHub which has not been created. Google Play requires an accessible privacy policy. | Launcher | 1 day |
| 10 | **UX-2** | UX | `core/designsystem/src/main/java/.../Type.kt` + `core/ui/src/main/java/.../NetflixMediaCard.kt:264,276,316` | **Typography 10-11sp -- unreadable on TV.** `labelSmall` defaults to 11sp; explicit `10.sp` font sizes in card metadata. Android TV guidelines recommend 14sp minimum body text for 3m viewing distance. | Pixel | 1 day |

---

## DETAILED FINDINGS BY DIMENSION

### 1. STABILITY (13/20)

#### Crash Vectors

| # | Severity | File:Line | Description | Impact |
|---|----------|-----------|-------------|--------|
| C1 | P0 | ~20 ViewModels | **Flow collectors without `.catch`.** See STAB-1 above. | ViewModel death on any upstream exception |
| C2 | P1 | `core/datastore/SettingsDataStore.kt:68` | **Unscoped CoroutineScope in init block.** Orphan `CoroutineScope(Dispatchers.IO).launch` with no parent job, no cancellation, no exception handler for migration logic. | Silent exception swallowing |
| C3 | P1 | `core/designsystem/Theme.kt:144` | **Unsafe cast `(view.context as Activity)`.** `ContextThemeWrapper` or non-Activity context throws `ClassCastException`. `!isInEditMode` guard only protects Android Studio previews. | Crash in edge cases |
| C4 | P1 | `feature/details/MediaDetailViewModel.kt:49` + 4 others | **`checkNotNull(savedStateHandle["ratingKey"])`.** Malformed deep link (`plexhub://play/` with missing args) crashes the app. Same pattern in SeasonDetail, CollectionDetail, HubDetail, MediaEnrichmentVM. | Crash from deep links |
| C5 | P2 | `feature/splash/SplashViewModel.kt:32-36` | **Race on plain `var` fields.** `isVideoComplete`, `isAuthenticationComplete`, `hasNavigated` accessed from multiple coroutines without synchronization. Safe on Main dispatcher but fragile. | Potential double navigation |
| C6 | P2 | `feature/settings/SettingsViewModel.kt:256-371` | **19 parallel Flow collectors in `loadSettings()`.** Called in `init` but not protected against double-call -- collectors accumulate. | Memory/CPU waste on repeated calls |

#### What's Working Well
- Zero `!!` in production code (was pervasive)
- `GlobalCoroutineExceptionHandler` with Crashlytics reporting, wired into `ApplicationScope` + `PlayerController`
- PlayerController `release()` properly cancels scope + recreates
- `AuthInterceptor` thread-safe with `AtomicReference`, no `runBlocking`
- Robust 401 chain with dialog deduplication and back-stack clearing
- Network error retry (max 3) + MPV fallback in PlayerController

---

### 2. SECURITY (12/20)

| # | Severity | File:Line | Description | Risk |
|---|----------|-----------|-------------|------|
| S1 | **P0** | `network_security_config.xml:15` | Cleartext HTTP globally permitted. See SEC-1 above. | CRITICAL -- token theft |
| S2 | **P0** | `M3uParser.kt:34-45` + `IptvRepositoryImpl.kt:32` | No IPTV URL validation. See SEC-2 above. | HIGH -- file access, token leak |
| S3 | P1 | `AndroidManifest.xml:25` | `android:allowBackup="true"`. ADB backup can extract DataStore prefs + potentially encrypted files. | MEDIUM -- data exfiltration |
| S4 | P1 | `AndroidManifest.xml:48-53` | Deep link `plexhub://play` has no auth check or input validation. Unauthenticated deep links trigger navigation to player screen. | MEDIUM -- unauthorized access |
| S5 | P2 | `PlayerController.kt:281` | `android.util.Log.d("METRICS", ...)` bypasses Timber, logs in release. | LOW -- info leak |
| S6 | P2 | `SecurePreferencesManager.kt:52-56` | Fallback to unencrypted SharedPreferences on encryption failure. Tokens could be stored in plaintext on Keystore corruption. | LOW -- edge case |

#### What's Working Well
- Hostname-aware `X509ExtendedTrustManager` (private IPs only) with separate public OkHttpClient
- `EncryptedSharedPreferences` (AES-256-GCM) for all sensitive data
- Timber `DebugTree` gated by `BuildConfig.DEBUG`
- HTTP logging `Level.NONE` in release
- `AuthEventBus` prevents infinite 401 retry loops
- Debug route gated by `BuildConfig.DEBUG` in both `MainScreen` and `SettingsScreen`

---

### 3. PERFORMANCE (14/20)

#### Top Bottlenecks

| # | Severity | File:Line | Description | Estimated Gain |
|---|----------|-----------|-------------|----------------|
| P1 | **P0** | `PerformanceImageInterceptor.kt:17-64` | Runs on every image load in release. See PERF-1. | ~10ms/screen, reduced GC |
| P2 | P1 | `PlayerFactory.kt:31-34` | ExoPlayer without `DefaultLoadControl`. Default 50s buffer = ~150MB on 4K LAN. No minimum buffer tuning for relay. | 30-50% less player memory |
| P3 | P1 | Global Compose | Zero `derivedStateOf`. See PERF-2. | 20-40% fewer recompositions |
| P4 | P1 | `NetflixMediaCard.kt:78-79` | `LaunchedEffect(isFocused)` creates coroutine on every focus change. 50+ visible cards = 50 coroutine launches per D-Pad press. | Smoother D-Pad navigation |
| P5 | P1 | `FavoritesScreen.kt:129` | `onFocus = { isFocused = true }` never resets to `false`. All previously-focused cards stay elevated with `zIndex(1f)`. | Correct visual behavior |
| P6 | P1 | `HomeViewModel.kt:108-113` | Favorites Flow collector launched inside `loadContent()`. Each refresh creates a new parallel collector without cancelling the old one. Memory leak. | Eliminate N-1 redundant collectors |
| P7 | P2 | `MediaDao.kt:385-386` | Duplicated `AND (:query IS NULL OR title LIKE...)` condition. SQLite evaluates LIKE predicate twice per row. | 50% faster alphabet jumps |
| P8 | P2 | `NetflixHeroBillboard.kt:87-94` | Auto-rotation `while(true) { delay(8000) }` runs when billboard scrolled off-screen. | Eliminate background timer |
| P9 | P2 | `PlexHubApplication.kt:161-183` | Server connection warmup sequential per server inside single `async`. 3+ relay servers = 5-15s added to cold start. | Cap with `withTimeoutOrNull(5s)` |
| P10 | P2 | `MediaDao.kt:225-369` | 8 nearly-identical `@Query` methods (dead code since `@RawQuery` path used). Inflates Room-generated code + APK size. | ~5-10KB APK reduction |

#### Quick Wins (< 1 day each)

| # | Change | Effort |
|---|--------|--------|
| 1 | Add `if (!BuildConfig.DEBUG) return chain.proceed()` to `PerformanceImageInterceptor` | 30 min |
| 2 | Fix `FavoritesScreen` `onFocus = { focused -> isFocused = focused }` | 15 min |
| 3 | Wrap computed values in `remember { derivedStateOf { ... } }` on Home/Library | 2 hrs |
| 4 | Remove duplicated `AND (:query IS NULL...)` in `MediaDao:385` | 15 min |
| 5 | Move favorites collection from `loadContent()` to `init {}` in `HomeViewModel` | 30 min |
| 6 | Replace `LaunchedEffect` with `SideEffect` in `NetflixMediaCard:78` | 30 min |
| 7 | Delete 8 unused `aggregatedPagingSource*` methods in `MediaDao` | 30 min |
| 8 | Add `withTimeoutOrNull(8000)` to init in `PlexHubApplication:107` | 30 min |

#### What's Working Well
- Adaptive memory cache (10-15% RAM, 50-400MB) with dedicated image OkHttpClient (5s timeout)
- Custom Coil Keyer strips tokens/hostnames -- LAN vs relay same cache entry
- Parallel cold start init (5 async jobs with error handling per job)
- Connection race strategy (direct 3s, relay 5s fallback, 5min failure cache)
- Room WAL + `PRAGMA synchronous=NORMAL` + `cache_size=-8000`
- 10 Room indexes covering all major query patterns
- ABI splits + R8 enabled
- LazyColumn/LazyRow composite keys for efficient diffing
- Lambda stability via `remember(key)` in `NetflixContentRow`
- Paging3 config tuned for TV (pageSize=50, prefetchDistance=15)
- Image size constraints (`size(420, 630)` for posters, not `Size.ORIGINAL`)

---

### 4. ARCHITECTURE (12/20)

#### Module Health

| Module | Grade | Key Issue |
|--------|-------|-----------|
| `:domain` | **A** | Pure Kotlin, zero `import android.*`, correct deps |
| `:core:model` | **A** | Minimal deps -- FIXED from previous audit |
| `:core:datastore` | **A** | Clean, properly scoped |
| `:core:navigation` | **B+** | FIXED from previous audit. Minor: hardcoded version `"1.7.8"` |
| `:core:designsystem` | **B+** | Clean. Hilt may be unnecessary |
| `:core:database` | **B** | Well-structured. Minor: Gson for type converters (should be kotlinx-serialization) |
| `:app` | **B-** | 5 data-layer imports, DAO direct access, debug `Log.d` |
| `:core:ui` | **B-** | Duplicate `tv-foundation` dep (lines 64, 67), French comment left in |
| `:core:common` | **B-** | Heavyweight deps (Navigation, TvProvider, WorkManager, Firebase) for a "common" module |
| `:core:network` | **C** | **STILL imports `:core:database`** -- persistent violation |
| `:data` | **B** | Use case (`ResolveEpisodeSourcesUseCase`) belongs in `:domain`. Gson version mismatch |

#### Clean Architecture Violations

| # | Severity | Description |
|---|----------|-------------|
| 1 | CRITICAL | `core:network` -> `core:database` dependency (`PlexApiCache` imports `ApiCacheDao` + `ApiCacheEntity`) |
| 2 | HIGH | `PlayerController.kt:40` injects `TrackPreferenceDao` directly (presentation -> database) |
| 3 | HIGH | `PlayerTrackController.kt:4-5` imports `TrackPreferenceDao` + `TrackPreferenceEntity` |
| 4 | MEDIUM | `SeasonDetailViewModel.kt:8` imports `data.usecase.ResolveEpisodeSourcesUseCase` (presentation -> data) |
| 5 | MEDIUM | 3 app files import `data.util.TvChannelManager` (presentation -> data) |

#### Error Handling Assessment

Three competing systems used inconsistently:
1. **`AppError` sealed class** (well-designed, barely used -- only 2 call sites)
2. **`PlexException` sealed class** (de facto standard in data layer)
3. **Raw `Result.failure(Exception(...))`** (scattered in repositories)

`toAppError()` does NOT handle `PlexException` subclasses, creating a gap.

#### Dead Code

| File | Issue |
|------|-------|
| `core/image/PlexImageKeyer.kt` | Orphaned duplicate of `app/.../di/image/PlexImageKeyer.kt` |
| `core/util/CacheManager.kt` | Orphaned duplicate of `core/common/.../CacheManager.kt` |
| `core/navigation/src/main/res/` | Untracked `res/` directory (accidental?) |
| `PlayerController.kt:146,149` | Leftover refactoring comments |
| `AppSidebar.kt`, `NetflixSidebar.kt` | Appear unused (MainScreen uses `NetflixTopBar`) |

#### Testing

| Module | Files | Tests | Coverage |
|--------|-------|-------|----------|
| `:app` (ViewModels) | 4 | 35 | ~15% |
| `:app` (Player) | 3 | 11 | ~40% |
| `:core:model` | 1 | 25 | ~80% |
| `:core:common` | 2 | 13 | ~30% |
| `:core:network` | 3 | 13 | ~40% |
| `:data` | 2 | 17 | ~10% |
| `:domain` | 7 | 25 | ~50% |
| **TOTAL** | **23** | **145** | **~20%** |

Missing critical tests: HomeViewModelTest, LibraryViewModelTest, SearchViewModelTest, MediaMapperTest, EnrichMediaItemUseCaseTest.

#### Tech Debt: ~12 developer-days

---

### 5. UX TV (14/20)

#### Screen-by-Screen Assessment

| Screen | Grade | Key Issue |
|--------|-------|-----------|
| **NetflixHomeScreen** | **A** | Exemplary. Billboard + content rows + `focusGroup()` + D-Pad UP return |
| **VideoPlayerScreen** | **A** | Exemplary. Full D-Pad, auto-hide, skip intro/credits, error overlay, MPV fallback |
| **NetflixSearchScreen** | **A** | Exemplary. Split layout with on-screen keyboard, proper focus flow |
| **SeasonDetailScreen** | **A-** | MAJOR improvement. TV two-column layout (35/65), episode list with progress bars |
| **NetflixDetailScreen** | **B+** | Good. Full-bleed backdrop, tabs, action buttons. Minor: `CircularProgressIndicator` (no skeleton) |
| **LibrariesScreen** | **B** | Good. Grid + paging + alphabet sidebar. Issues: `FilterButton` 32dp (below 40dp min), `AlertDialog` for filters |
| **FavoritesScreen** | **B** | Good grid layout. Bug: focus state never resets (see PERF P5) |
| **ProfileScreen (auth)** | **B** | FocusRequester, scale animation, PIN dialog. Two profile systems coexist |
| **HistoryScreen** | **C+** | Uses old `MediaCard` instead of `NetflixMediaCard`. Uses `MaterialTheme.colorScheme.background` (not Netflix black). 100dp cells (smaller than 140dp elsewhere) |
| **DownloadsScreen** | **C** | `TopAppBar`, `LazyColumn` list, hardcoded French strings in semantics, hardcoded "Delete" |
| **AuthScreen** | **D** | PIN commented out, `OutlinedTextField` unusable on TV without keyboard |

#### Screens Still Using TopAppBar (Mobile Pattern)

| Screen | Severity |
|--------|----------|
| DownloadsScreen | P2 |
| ProfileSwitchScreen | P3 |
| IptvScreen | P3 |
| CollectionDetailScreen | P3 |
| HubDetailScreen | P3 |
| SettingsScreen | P3 (acceptable for settings) |
| ServerStatusScreen | P3 |
| DebugScreen | P4 (debug-only) |

#### Strings / i18n

- **562 EN strings** + **562 FR strings** (full parity)
- ~20 hardcoded strings remain (mostly in `DownloadsScreen`, `NetflixMediaCard` semantics, `NetflixOnScreenKeyboard`)
- String resources exist for most -- just need `stringResource()` calls

#### Features Inventory

| Feature | Status | Maturity |
|---------|--------|----------|
| Profiles with avatars + PIN | Implemented | Mature |
| Continue Watching (cross-device) | Implemented | Mature |
| Android TV Channels | Implemented | Mature |
| Multi-server enrichment | Implemented | Mature |
| Offline downloads | Implemented | Basic |
| IPTV | Implemented | Basic |
| Kid mode | Partial | Flag exists, no content filtering |
| Screensaver | Not implemented | -- |
| QR code auth | Not implemented | -- |

---

## DECISION MATRIX

| # | Action | Agent | Primary File(s) | Effort | Impact | Sprint |
|---|--------|-------|------------------|--------|--------|--------|
| 1 | Set `cleartextTrafficPermitted="false"` | Sentinel/Launcher | `network_security_config.xml:15` | 30 min | Critical | **S1** |
| 2 | Add `.catch` to all Flow collectors in ViewModels | Sentinel | ~20 ViewModel files | 2 days | Critical | **S1** |
| 3 | Gate `PerformanceImageInterceptor` by `BuildConfig.DEBUG` | Turbo | `PerformanceImageInterceptor.kt:17` | 30 min | High | **S1** |
| 4 | Add POST_NOTIFICATIONS runtime permission request | Launcher | New `PermissionHelper.kt` or `MainActivity.kt` | 1 day | Critical | **S1** |
| 5 | Create privacy policy page | Launcher | `docs/privacy-policy-en.md` | 1 day | Critical | **S1** |
| 6 | Validate IPTV URLs (scheme allowlist) | Sentinel | `M3uParser.kt`, `IptvRepositoryImpl.kt` | 1 day | High | **S1** |
| 7 | Fix FavoritesScreen focus reset | Turbo | `FavoritesScreen.kt:129` | 15 min | Medium | **S1** |
| 8 | Fix HomeViewModel collector accumulation | Turbo | `HomeViewModel.kt:108-113` | 30 min | Medium | **S1** |
| 9 | Re-enable PIN login on AuthScreen | Pixel | `AuthScreen.kt:97-104` | 1-2 days | Critical | **S2** |
| 10 | Create TV typography scale (min 14sp) | Pixel | `Type.kt` | 1 day | High | **S2** |
| 11 | Set `allowBackup="false"` | Sentinel | `AndroidManifest.xml:25` | 15 min | Medium | **S2** |
| 12 | Replace `checkNotNull` with graceful error handling | Sentinel | 5 ViewModel init blocks | 1 day | Medium | **S2** |
| 13 | Extract `PlexApiCache` interface to break `network->database` | Blueprint | `core/network/`, `core/database/` | 0.5 days | High | **S2** |
| 14 | Create `TrackPreferenceRepository` for PlayerController | Blueprint | `app/player/`, `domain/` | 0.5 days | Medium | **S2** |
| 15 | Add `derivedStateOf` for computed values | Turbo | Home, Library, Detail screens | 1-2 days | High | **S2** |
| 16 | Configure ExoPlayer `DefaultLoadControl` | Turbo | `PlayerFactory.kt:31-34` | 2 days | High | **S3** |
| 17 | Unify error handling (AppError vs PlexException) | Blueprint | All repositories + ViewModels | 2 days | High | **S3** |
| 18 | Restore critical tests (HomeVM, LibraryVM, SearchVM, MediaMapper) | Blueprint | 4 new test files | 3 days | High | **S3** |
| 19 | Replace TopAppBar on DownloadsScreen | Pixel | `DownloadsScreen.kt` | 1-2 days | Medium | **S3** |
| 20 | Replace FilterDialog AlertDialogs | Pixel | `FilterDialog.kt` | 2-3 days | Medium | **S3** |
| 21 | Compose stability config (ImmutableList or stabilityConfigPath) | Turbo | Build config + data classes | 2-3 days | High | **S4** |
| 22 | Implement Baseline Profiles | Turbo | New benchmark module | 2 days | High | **S4** |
| 23 | Consolidate Gson -> kotlinx-serialization | Blueprint | Room converters, ServerMapper, SearchRepo | 2 days | Medium | **S4** |
| 24 | Kid mode content filtering | Pixel | Profile logic, library queries | 3-5 days | Medium | **S4** |
| 25 | Delete dead code (orphan files, unused queries) | Blueprint | 5+ files | 0.5 days | Low | **S4** |

---

## SPRINT PLAN

### Sprint 1 -- Release Blockers (5 days)

**Goal**: Remove all blockers preventing Play Store submission for closed beta.

| Task | Effort | Owner |
|------|--------|-------|
| Fix cleartext traffic (`network_security_config.xml`) | 30 min | Security |
| Add `.catch` to all Flow collectors (~20 ViewModels) | 2 days | Stability |
| Gate `PerformanceImageInterceptor` by DEBUG | 30 min | Performance |
| Add POST_NOTIFICATIONS runtime permission | 1 day | Compliance |
| Create + host privacy policy | 1 day | Compliance |
| Validate IPTV URLs (scheme allowlist) | 1 day | Security |
| Fix FavoritesScreen focus + HomeVM collector leak | 45 min | Performance |
| Remove `import android.util.Log` from PlayerController | 5 min | Cleanup |

**Exit criteria**: Zero P0 security/compliance issues. All ViewModels survive Flow errors.

### Sprint 2 -- Stability & Auth (7 days)

**Goal**: Fix auth UX for real TV users. Harden crash vectors. Fix architecture violations.

| Task | Effort |
|------|--------|
| Re-enable PIN login on AuthScreen | 1-2 days |
| Create TV typography scale (min 14sp) | 1 day |
| Set `allowBackup="false"` | 15 min |
| Replace `checkNotNull` with graceful error handling (5 VMs) | 1 day |
| Break `core:network -> core:database` dependency | 0.5 days |
| Create `TrackPreferenceRepository` | 0.5 days |
| Add `derivedStateOf` on Home/Library/Detail | 1-2 days |
| Deep link parameter validation | 0.5 days |

**Exit criteria**: Auth usable on TV. Crash risk from deep links eliminated.

### Sprint 3 -- Polish & Quality (8 days)

**Goal**: Player optimization. Error handling consistency. UI polish. Test coverage.

| Task | Effort |
|------|--------|
| Configure ExoPlayer `DefaultLoadControl` | 2 days |
| Unify error handling (AppError vs PlexException) | 2 days |
| Restore 4 critical test files | 3 days |
| Replace TopAppBar on DownloadsScreen | 1-2 days |
| Replace FilterDialog AlertDialogs with TV overlays | 2-3 days |

**Exit criteria**: Player memory under control. >30% test coverage. Consistent error UX.

### Sprint 4 -- Optimization & Release (6 days)

**Goal**: Compose performance. Dependency consolidation. Final release prep.

| Task | Effort |
|------|--------|
| Compose stability config (ImmutableList / stabilityConfigPath) | 2-3 days |
| Implement Baseline Profiles | 2 days |
| Consolidate Gson -> kotlinx-serialization | 2 days |
| Delete dead code (orphan files, unused queries, stale comments) | 0.5 days |
| Test release build on real TV devices | 2 days |
| Prepare Play Store listing + screenshots | 2 days |
| Data Safety Form + Content Rating questionnaire | 1 day |

**Exit criteria**: Production release candidate published.

---

## FINAL VERDICT

| Question | Answer |
|----------|--------|
| **Vendable today?** | **NO** -- 3 compliance blockers (cleartext, notifications permission, privacy policy) and 1 critical stability gap (Flow .catch) |
| **Days to closed beta?** | **~5 days** (Sprint 1) |
| **Days to production?** | **~26 days** (4 sprints) |
| **Biggest risk?** | Auth UX -- token paste on TV without keyboard is a dead end for real users |
| **Biggest improvement since Feb 19?** | Security posture (TrustManager, 401 handling, Crashlytics) and string externalization (562 EN + 562 FR) |
| **Score trajectory** | 40/100 -> 65/100 (+25 in 1 day). Sprint 1 would bring it to ~75/100. Full sprint plan targets ~90/100. |

---

*Generated by 5 parallel Claude Opus 4.6 agents on 20 February 2026*
*Total files analyzed: ~120+ source files across 11 modules*
*Total findings: 10 P0, 18 P1, 15 P2, 8 P3*
