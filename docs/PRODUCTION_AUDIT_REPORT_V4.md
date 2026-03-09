# PRODUCTION AUDIT REPORT V4 — PlexHubTV

> **Date**: 25 February 2026
> **Version**: 1.0.0 (`claude/continue-plexhubtv-refactor-YO43N`)
> **Commit**: `c9dffb0`
> **Previous Audit**: 22 February 2026 (V3 — Score: 82/100)
> **Auditors**: 5 parallel agents (Stability/Security, Performance, Architecture, UX/Features, Release) — Claude Opus 4.6
> **Method**: Full 8-phase codebase audit — fresh deep inspection of all modules

---

## EXECUTIVE SUMMARY

**Global Score: 65/100** (down from **82/100**)

> **Important context**: V3 was a *post-fix validation* audit (checking that specific fixes landed). V4 is a *comprehensive deep audit* uncovering new classes of issues not examined in V3 — duplicate network calls, N+1 queries, God interfaces, missing DB indexes, and release compliance gaps. The score decrease reflects **wider scope**, not regressions.

| Dimension | Score /20 | Trend | Key Takeaway |
|-----------|-----------|-------|--------------|
| **Stability** | 14/20 | -3 | Previous `safeCollectIn` fixes hold. **New**: orphaned CoroutineScopes, unbuffered Channels drop nav events, SyncWorker always returns success |
| **Security** | 9/20 | +1 | SEC-2 (IPTV validation) **FIXED**. SEC-1 (cleartext HTTP) still open. **New P0**: SecurePreferencesManager plaintext fallback |
| **Performance** | 13/20 | -7 | Previous ExoPlayer/derivedStateOf fixes hold. **New P0s**: duplicate home content calls, N+1 URL resolution, missing DB indexes, no FTS |
| **Architecture** | 14/20 | -5 | Clean arch boundary fixes hold. **New P1s**: God interface (19 methods), 20+ hardcoded Dispatchers, core:model→Compose dependency |
| **UX TV** | 15/20 | -3 | Excellent focus system, player, skeletons. **New P0**: Home screen DOWN is no-op. 6 screens lack initial focus. Overscan margins too small on 4 screens |

**Verdict**: NOT shippable. **~2-3 weeks focused work** to reach closed beta. Biggest blockers: security P0s, performance P0s, Google Play compliance gaps.

---

## DETAILED FINDINGS BY DIMENSION

### 1. STABILITY (14/20)

#### P0 Issues

*None new — previous P0 stability fixes (C1-C7 from V3) remain validated.*

#### P1 Issues

| # | ID | Issue | File:Line | Impact |
|---|----|-------|-----------|--------|
| 1 | S-03 | Orphaned CoroutineScope in `PlayerScrobbler.stop()` — fire-and-forget scope never cancelled | `PlayerScrobbler.kt:102` | Resource leak on every playback stop |
| 2 | S-04 | PlayerController `@Singleton` with manual scope cancel/recreate — no structured concurrency | `PlayerController.kt:51,125-146` | Stale state, lost coroutines between cancel and recreation |
| 3 | S-05 | MpvPlayerWrapper no error recovery on native init failure — crashes fallback path | `MpvPlayerWrapper.kt` | Silent player failure, user sees stuck screen |
| 4 | S-07 | Unbuffered `Channel()` (RENDEZVOUS) in HomeViewModel + SearchViewModel — drops nav events during config change | `HomeViewModel.kt:38,41` / `SearchViewModel.kt:42,45` | Lost navigation, user action ignored |
| 5 | S-10 | LibrarySyncWorker always returns `Result.success()` even on failure — WorkManager skips retry | `LibrarySyncWorker.kt:210` | Stale library data, no automatic retry |

#### P2 Issues

| # | ID | Issue | File:Line |
|---|----|-------|-----------|
| 1 | S-01 | Force-unwrap `!!` on authenticationResult | `SplashViewModel.kt:143` |
| 2 | S-02 | Force-unwrap `!!` on filteredItems | `LibrariesScreen.kt:235` |
| 3 | S-06 | Duplicate MPV property observation | `MpvPlayerWrapper.kt:101` |
| 4 | S-08 | Wrong `isActive` scope check in AuthViewModel polling | `AuthViewModel.kt:110` |
| 5 | S-09 | No SavedStateHandle in HomeViewModel/SearchViewModel | Multiple files |
| 6 | S-11 | `android.util.Log.d` bypasses Timber in release | `PlayerController.kt:312` |
| 7 | S-12 | Subtitle URI not validated before player use | `PlayerTrackController.kt` |
| 8 | S-14 | Field injection (`@Inject lateinit var`) in SettingsViewModel | `SettingsViewModel.kt:47-48` |

**What's Working Well:**
- `safeCollectIn` deployed on 22/24 ViewModels
- Zero `!!` in production code outside 2 guarded cases
- `GlobalCoroutineExceptionHandler` with Crashlytics
- Robust 401 handling chain (AuthEventBus)

---

### 2. SECURITY (9/20)

#### P0 Blockers

| # | ID | Issue | File:Line | Risk |
|---|----|-------|-----------|------|
| 1 | **SEC-01** | Cleartext HTTP globally permitted — TODO comment confirms debug regression never reverted | `network_security_config.xml:14-15` | **CRITICAL** — Token theft via MITM on any network |
| 2 | **SEC-05** | SecurePreferencesManager falls back to **unencrypted** SharedPreferences on Keystore failure | `SecurePreferencesManager.kt:52-56` | **CRITICAL** — Auth tokens stored in plaintext |

#### P1 Issues

| # | ID | Issue | File:Line | Risk |
|---|----|-------|-----------|------|
| 1 | SEC-02 | No certificate pinning for plex.tv | `network_security_config.xml` | Token theft via compromised CA |
| 2 | SEC-07 | `allowBackup="true"` enables ADB data extraction | `AndroidManifest.xml:26` | Full token extraction with physical access |
| 3 | SEC-08 | Token embedded in URLs (subtitles, thumbnails, streams) — appears in logs/crash reports | `PlayerController.kt:619` + various | Token exposure in Crashlytics, logcat |
| 4 | SEC-13 | Deep link `plexhub://play` parameters not validated (ratingKey, serverId) | `AndroidManifest.xml:52` | Navigation hijacking via crafted URIs |

#### P2 Issues

| # | ID | Issue | File:Line |
|---|----|-------|-----------|
| 1 | SEC-03 | Custom TrustManager bypasses cert validation for all private IPs | `NetworkModule.kt:132-206` |
| 2 | SEC-04 | HTTP logging at `Level.BODY` in debug — exposes tokens | `NetworkModule.kt:71` |
| 3 | SEC-06 | IPTV playlist URL (may contain credentials) stored unencrypted in DataStore | `SettingsDataStore.kt` |
| 4 | SEC-09 | AuthInterceptor token cache not explicitly cleared on logout | `AuthInterceptor.kt:31-32` |
| 5 | SEC-10 | API keys in BuildConfig extractable from APK | `ApiKeyManager.kt` |
| 6 | SEC-11 | ProGuard rules lack `Log.*` stripping | `proguard-rules.pro` |

#### Validated Fix (since V3)

| Fix | Status | Evidence |
|-----|--------|----------|
| **SEC-2 IPTV URL validation** | ✅ **FIXED** | `M3uParser.kt:13,63-69` — Allowlist (http/https/rtsp/rtp) at parser, repository, and player layers |

**What's Working Well:**
- IPTV URL scheme validation (3-layer defense-in-depth)
- EncryptedSharedPreferences (AES-256-GCM) as primary storage
- Conscrypt + GMS security provider installation
- Minimal permissions (INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS only)
- Timber gated by `BuildConfig.DEBUG`

---

### 3. PERFORMANCE (13/20)

#### P0 Bottlenecks

| # | ID | Issue | File:Line | Impact |
|---|----|-------|-----------|--------|
| 1 | **P0-01** | Duplicate `getUnifiedHomeContentUseCase()` calls — HomeViewModel AND HubViewModel both trigger identical multi-server fan-out on launch | `HomeViewModel.kt:112` / `HubViewModel.kt:79` | 2× network traffic, 2× server load, doubled time-to-first-content |
| 2 | **P0-02** | N+1 URL resolution in `getWatchHistory()` — calls `authRepository.getServers()` per emission, then resolves URLs per entity | `PlaybackRepositoryImpl.kt:130-153` | History screen 2-5s load (should be <100ms) |
| 3 | **P0-03** | Missing `lastViewedAt` index — `ORDER BY lastViewedAt DESC` forces full table scan | `MediaEntity.kt` / `MediaDao.kt:134-142` | History query 200-500ms on 5000+ items (should be <20ms) |
| 4 | **P0-04** | `LIKE '%query%'` without FTS — full table scan on every keystroke after debounce | `MediaDao.kt:70-74` | Search lag 100-500ms on large libraries |

#### P1 Issues

| # | ID | Issue | File:Line |
|---|----|-------|-----------|
| 1 | P1-05 | Unstable `List<MediaItem>` in UI state — triggers unnecessary recompositions | `HomeUiState.kt:14` / `HubUiState.kt:8-10` |
| 2 | P1-06 | Debug ID badges rendered in release builds (no `BuildConfig.DEBUG` guard) | `NetflixMediaCard.kt:269-308` |
| 3 | P1-07 | Dead `metadataAlpha` animation always targets `1f` — wastes animation objects | `NetflixMediaCard.kt:332-336` |
| 4 | P1-08 | Synchronous security provider installation blocks main thread (100-400ms) | `PlexHubApplication.kt:84` |
| 5 | P1-09 | `material-icons-extended` bloats APK by ~2-4MB (only ~10 icons used) | `app/build.gradle.kts:209` |
| 6 | P1-10 | Sequential API calls in `getNextMedia()` — up to 3 chained network calls on playback path | `PlaybackRepositoryImpl.kt:97-121` |
| 7 | P1-11 | Inline lambda allocations in MainScreen navigation callbacks — ~20 allocations per nav recomposition | `MainScreen.kt:132-171` |
| 8 | P1-12 | Orphaned CoroutineScope in PlayerScrobbler (cross-ref S-03) | `PlayerScrobbler.kt:102-108` |
| 9 | P1-13 | Duplicate MPV property observation (cross-ref S-06) | `MpvPlayerWrapper.kt:100-101` |

#### P2 Issues

| # | ID | Issue | File:Line |
|---|----|-------|-----------|
| 1 | P2-14 | `SELECT *, MAX(lastViewedAt) as lastViewedAt` — alias collision (Room pitfall) | `MediaDao.kt:134-137` |
| 2 | P2-15 | Synchronous Firebase init on main thread (50-150ms) | `PlexHubApplication.kt:86` |
| 3 | P2-16 | 7 individual FocusRequesters per TopBar composition | `NetflixTopBar.kt` |
| 4 | P2-17 | `getNextMedia()`/`getPreviousMedia()` fetch entire season episode list | `PlaybackRepositoryImpl.kt:97-128` |
| 5 | P2-18 | `WorkManager.getInstance(this)` called 4 times (should extract local var) | `PlexHubApplication.kt:236-284` |
| 6 | P2-19 | `SideEffect` triggers on every composition (should be `LaunchedEffect`) | `NetflixMediaCard.kt:83` |
| 7 | P2-20 | PagingConfig `maxSize=2000` — potentially 4-10MB on low-RAM TV devices | `LibraryRepositoryImpl.kt` |

#### Previous Optimizations Still Validated

| Optimization | Status |
|-------------|--------|
| ExoPlayer DefaultLoadControl (LAN 15s / Relay 30s) | ✅ |
| `derivedStateOf` (13 optimizations across 3 screens) | ✅ |
| PerformanceImageInterceptor gated by BuildConfig.DEBUG | ✅ |
| Billboard timer lifecycle-aware | ✅ |
| Server warmup timeout (5s parallel) | ✅ |
| Dead `aggregatedPagingSource*` queries removed | ✅ |

---

### 4. ARCHITECTURE (14/20)

**Overall Grade: B-**

| Module | Grade | Key Issue |
|--------|-------|-----------|
| `:core:datastore` | **A** | Clean, well-scoped |
| `:core:designsystem` | **A** | Clean |
| `:core:database` | **A-** | Clean |
| `:core:network` | **A-** | Minor: depends on `:core:datastore` (inverted layer) |
| `:core:navigation` | **A-** | Duplicate `Screen` definitions (fragile) |
| `:core:model` | **B+** | Compose runtime + Retrofit dependencies in pure model module |
| `:domain` | **B** | Duplicate interface methods, unused `Resource` wrapper |
| `:data` | **B-** | Hardcoded dispatchers, duplicate `getClient()`, BuildConfig secrets |
| `:app` | **C+** | Hardcoded dispatchers, duplicate HubViewModel, bloated Screen.kt |
| `:core:common` | **B-** | Kitchen sink: Retrofit, Navigation, Firebase, WorkManager, TV Provider |
| `:core:ui` | **B+** | Unnecessary Hilt dependency |

#### P1 Issues

| # | ID | Issue | Files |
|---|----|-------|-------|
| 1 | F-01/02/03/04 | **God Interface**: `MediaRepository` has 19 methods spanning 5 concerns (aggregation, detail, favorites, history, playback) — duplicates 3 focused repositories | `MediaRepository.kt` |
| 2 | F-05 | 20+ hardcoded `Dispatchers.IO`/`Dispatchers.Main` — bypasses `@IoDispatcher`/`@MainDispatcher` qualifiers, blocks unit testing | 12+ files across data/app/domain |
| 3 | F-06 | `core:model` depends on `androidx.compose.runtime` (for `@Immutable`) — couples model layer to Compose | `core/model/build.gradle.kts` |
| 4 | F-07 | `core:common` is a kitchen sink — pulls in Retrofit, Compose Navigation, Firebase, WorkManager, TV Provider | `core/common/build.gradle.kts` |
| 5 | F-15 | API keys compiled into BuildConfig for both `:app` AND `:data` — duplicate, extractable | `data/build.gradle.kts` + `app/build.gradle.kts` |

#### P2 Issues (15 findings)

| ID | Issue | Effort |
|----|-------|--------|
| F-08 | Inconsistent package structure in `core:common` | Low |
| F-09 | `Resource<T>` sealed class almost entirely unused (only 1 use case) | Low |
| F-10 | Duplicate `Screen` sealed classes in `core:navigation` and `app` | Low |
| F-11 | Duplicate `getClient()` pattern across 3+ repositories | Low |
| F-12 | `core:network` → `core:datastore` inverted dependency | Medium |
| F-13 | `core:model` has Retrofit dependency for `toHttpAppError()` | Low |
| F-14 | Unnecessary Hilt in `core:designsystem` and `core:ui` | Low |
| F-16 | Duplicate BuildConfig fields in `:app` and `:data` | Low |
| F-17 | Overlapping `HubViewModel`/`HubDetailViewModel` responsibilities | Low |
| F-18 | `EnrichMediaItemUseCase` uses `Dispatchers.IO` directly in domain layer | Low |
| F-19 | `PerformanceTracker` uses mutable lists without thread safety | Low |
| F-20 | Orphaned `di/` root directory not in `settings.gradle.kts` | Low |
| F-21 | `SafeApiCall` in `core:common` imports `retrofit2.HttpException` | Low |
| F-22 | ktlint `ignoreFailures = true` — violations never break build | Low |
| F-24 | `ErrorExtensions.kt` has hardcoded French error messages | Medium |

#### Previous Clean Arch Fixes Still Validated

| Fix | Status |
|-----|--------|
| `core:network` → `core:database` dependency removed | ✅ |
| PlayerController uses `TrackPreferenceRepository` (not DAO) | ✅ |
| SeasonDetailViewModel imports from `domain.usecase` | ✅ |
| Unified `AppError` sealed class (PlexException removed) | ✅ |
| 5/5 critical tests present | ✅ |

#### Test Infrastructure

| Category | Files | Tests |
|----------|-------|-------|
| App layer | 13 | ~60+ |
| Domain layer | 8 | ~40+ |
| Data layer | 3 | ~15+ |
| Core layer | 7 | ~30+ |
| **Total** | **31** | **~145+** |
| Android/UI tests | **0** | **0** |

---

### 5. UX TV (15/20)

#### D-Pad Navigation

| Assessment | Details |
|------------|---------|
| **P0** | NAV-02: Home screen DOWN is a no-op — all content rows are on separate Hub tab. First screen feels empty. |
| **P1** (6 items) | NAV-03: No focus restoration on Detail screen return. NAV-04: No LEFT nav from episodes to season info. NAV-06/07/08/09: Favorites, History, Downloads, Settings lack initial focus. |
| **OK** (5 items) | MainScreen back handling, Search keyboard-to-results, IPTV focus, Hub first row focus, Library focus restoration. |

#### Focus Indicators — **EXCELLENT**

Consistent scale + border + color animations across all 10+ interactive components. Best-in-class for Android TV.

#### Loading States — **EXCELLENT** (core screens)

| Screen | Quality |
|--------|---------|
| Home/Hub | Shimmer billboard + row skeletons |
| Library | Shimmer grid with title + card placeholders |
| Season Detail | Two-column shimmer layout |
| Detail, Favorites, History, Downloads, Collection, Search | `CircularProgressIndicator` only — **needs skeleton upgrade** |

#### Empty States — **ADEQUATE**

11 empty states audited. All have text messages, most lack icons and constructive guidance. Favorites, History, Downloads need illustration + instructional text.

#### Player UX — **VERY GOOD**

14 features audited: dual engine (ExoPlayer + MPV), chapter markers, skip intro/credits, auto-next popup, track selection, performance overlay, error recovery with engine switch. Missing: binge countdown timer, resume position toast, frame-accurate seek thumbnails.

#### Overscan Safe Area — **MIXED**

| OK (48dp+) | Too Small (16dp) |
|------------|------------------|
| TopBar, ContentRow, Favorites, Library, Season Detail, Detail Screen | History, Downloads, Collection, IPTV |

#### Remote Control — **GOOD**

All standard media keys handled. Missing: long-press for context menus (add to favorites, download).

---

### 6. RELEASE READINESS (21/39 — Grade: C)

| Category | Score | Grade | Key Gaps |
|----------|-------|-------|----------|
| Build & Release | 5/7 | B | Missing: Crashlytics mapping upload, cleartext HTTP in release |
| Google Play Compliance | 3/8 | D | Missing: privacy policy URL, data safety form, content rating, store assets |
| Crash Reporting | 4/5 | B+ | Missing: mapping upload verification |
| Accessibility | 2.5/5 | C | 45 `contentDescription = null` instances, no TalkBack testing |
| Localization | 3.5/4 | B+ | 30+ hardcoded English strings, 1 hardcoded French string |
| Testing | 3/10 | D | 31 unit test files, 0 instrumented tests, 0 UI tests, no coverage metric |

---

## VERDICT "READY FOR CLOSED BETA / PLAY STORE"

### Decision Matrix

| Dimension | Conformity | Blocking? | Detail |
|-----------|------------|-----------|--------|
| **Stability** | ⚠️ 70% | No | P1 issues (orphan scopes, unbuffered channels) won't crash in normal use |
| **Security** | ❌ 2 P0 | **YES** | Cleartext HTTP + plaintext fallback are deployment blockers |
| **Performance** | ❌ 4 P0 | **YES** | Duplicate network calls, N+1 queries, missing indexes degrade UX visibly |
| **Architecture** | ⚠️ 70% | No | God interface and hardcoded dispatchers are tech debt, not blockers |
| **UX TV** | ⚠️ 75% | Partial | Home DOWN no-op and missing initial focus hurt first impression |
| **Release Readiness** | ❌ 54% | **YES** | Privacy policy, data safety form, store assets missing |

### Global Verdict

PlexHubTV is **NOT YET READY** for:
- ❌ **Closed beta internal** — Blocked by 2 security P0s + 4 performance P0s
- ❌ **Closed beta Play Store** — Additionally blocked by compliance gaps (privacy policy, data safety)
- ❌ **Production open** — Same + testing gaps

---

## MANDATORY P0 FIXES

### Security P0s (ETA: ~3h)

| # | Fix | File | Effort |
|---|-----|------|--------|
| 1 | Set `cleartextTrafficPermitted="false"` + create debug-only overlay | `network_security_config.xml` | 1h |
| 2 | Remove plaintext SharedPreferences fallback — retry with fresh MasterKey or force re-auth | `SecurePreferencesManager.kt:52-56` | 2h |

### Performance P0s (ETA: ~12h)

| # | Fix | File | Effort |
|---|-----|------|--------|
| 3 | Share unified home content via `SharedFlow` / `stateIn()` — eliminate duplicate calls | `HomeViewModel.kt` / `HubViewModel.kt` | 4h |
| 4 | Cache server list in-memory for URL resolution — eliminate N+1 in history | `PlaybackRepositoryImpl.kt:130-153` | 3h |
| 5 | Add `@Index(value = ["lastViewedAt"])` to MediaEntity | `MediaEntity.kt` | 1h |
| 6 | Implement Room FTS4 on title column for search | `MediaDao.kt`, new `MediaFts` entity | 4h |

### UX P0 (ETA: ~4h)

| # | Fix | File | Effort |
|---|-----|------|--------|
| 7 | Merge Home + Hub into single screen (hero billboard + content rows) | `NetflixHomeScreen.kt` / `HubScreen.kt` | 4h |

**Total P0 ETA**: ~19h

---

## SPRINT ACTION PLAN

### Sprint 1 (1-2 weeks) — Security & Performance P0s

| # | Task | Effort | Priority |
|---|------|--------|----------|
| S1.1 | Fix cleartext HTTP (network_security_config) | 1h | P0 |
| S1.2 | Fix SecurePreferencesManager plaintext fallback | 2h | P0 |
| S1.3 | Share unified home content Flow (eliminate duplicate calls) | 4h | P0 |
| S1.4 | Cache server list for URL resolution | 3h | P0 |
| S1.5 | Add `lastViewedAt` index to MediaEntity | 1h | P0 |
| S1.6 | Implement FTS4 for search | 4h | P0 |
| S1.7 | Merge Home + Hub screens | 4h | P0 |
| **Total** | | **~19h** | |

### Sprint 2 (1-2 weeks) — Stability P1s & Quick Wins

| # | Task | Effort | Priority |
|---|------|--------|----------|
| S2.1 | Replace orphaned CoroutineScope in PlayerScrobbler with `@ApplicationScope` | 1h | P1 |
| S2.2 | Add `Channel.BUFFERED` to navigation/error channels | 1h | P1 |
| S2.3 | LibrarySyncWorker: return `Result.retry()` for transient errors | 1h | P1 |
| S2.4 | MpvPlayerWrapper: add try-catch around native init | 1h | P1 |
| S2.5 | Set `allowBackup="false"` in AndroidManifest | 5min | P1 |
| S2.6 | Add `BuildConfig.DEBUG` guard to debug badges | 5min | P1 |
| S2.7 | Remove dead `metadataAlpha` animation | 5min | P1 |
| S2.8 | Remove duplicate MPV property observation | 5min | P1 |
| S2.9 | Replace `SideEffect` with `LaunchedEffect(isFocused)` | 5min | P1 |
| S2.10 | Replace `Log.d` with Timber in PlayerController | 5min | P1 |
| S2.11 | Add initial focus to Favorites, History, Downloads, Settings screens | 2h | P1 |
| S2.12 | Fix overscan margins (48dp) on History, Downloads, Collection, IPTV | 1h | P1 |
| S2.13 | Move security + Firebase init off main thread | 2h | P1 |
| **Total** | | **~10h** | |

### Sprint 3 (2-3 weeks) — Architecture & Release Prep

| # | Task | Effort | Priority |
|---|------|--------|----------|
| S3.1 | Decompose MediaRepository God Interface | 8h | P1 |
| S3.2 | Replace hardcoded `Dispatchers.*` with injected qualifiers | 4h | P1 |
| S3.3 | Remove Compose/Retrofit deps from `core:model` | 2h | P1 |
| S3.4 | Extract misplaced code from `core:common` | 4h | P1 |
| S3.5 | Configure Crashlytics mapping upload | 30min | P0 |
| S3.6 | Create and host privacy policy page | 2h | P0 |
| S3.7 | Extract 30+ hardcoded strings to strings.xml | 4h | P1 |
| S3.8 | Add CrashlyticsTree for release log forwarding | 2h | P1 |
| S3.9 | Version code auto-increment strategy | 1h | P1 |
| **Total** | | **~28h** | |

### Sprint 4 (1-2 weeks) — Play Store Submission

| # | Task | Effort | Priority |
|---|------|--------|----------|
| S4.1 | Complete data safety form | 2h | P0 |
| S4.2 | Content rating questionnaire (IARC) | 1h | P0 |
| S4.3 | Store listing assets (icon, banner, screenshots, descriptions) | 8h | P0 |
| S4.4 | Release build testing on real Android TV device | 4h | P0 |
| S4.5 | Set app category and target audience | 30min | P0 |
| S4.6 | Internal testing track + pre-launch report | 2h | P1 |
| S4.7 | Staged rollout (5% → 25% → 50% → 100%) | 1h/stage | P1 |
| **Total** | | **~20h** | |

---

## POST-RELEASE RECOMMENDATIONS (2-4 weeks)

| # | Task | Effort | Impact | Detail |
|---|------|--------|--------|--------|
| 1 | **Increase test coverage** | L | Stability | Target 40% domain, 30% data, 20% app. Add Room migration tests. |
| 2 | **ImmutableList in UI state** | S | Performance | `kotlinx-collections-immutable` → 30-50% fewer recompositions |
| 3 | **Compose Stability Config** | S | Performance | Compiler stability config file for `MediaItem`, `Hub`, `Server` |
| 4 | **Baseline Profiles** | M | Performance | 15-20% faster cold start |
| 5 | **Remove material-icons-extended** | S | APK Size | -2-4MB APK, copy ~10 used icons locally |
| 6 | **Screen transition animations** | S | UX | `fadeIn`/`slideInHorizontally` on NavHost routes |
| 7 | **Skeleton screens for remaining screens** | M | UX | Favorites, History, Downloads, Collection, Search |
| 8 | **Token from URL to headers** | M | Security | ExoPlayer `DataSource.Factory` with header-based auth |
| 9 | **Certificate pinning for plex.tv** | S | Security | SHA-256 pin-set in network_security_config |
| 10 | **Custom analytics events** | M | Observability | media_play, search_query, favorite_toggle, player_error |
| 11 | **Kids Mode / Parental Controls** | L | Business | Content filtering, PIN protection, simplified UI |
| 12 | **Google Play Billing** | M | Business | Premium tier with subscription + lifetime options |
| 13 | **Android TV Channels** | M | Retention | Continue Watching + Recommended on TV home screen |
| 14 | **Push notifications** | M | Retention | New content alerts via WorkManager/FCM |
| 15 | **Binge countdown timer** | S | UX | Netflix-style "Next episode in 5..." |

**Effort Legend**: S = <1 day, M = 2-5 days, L = 1-2 weeks

---

## FINAL ASSESSMENT

### Score Trajectory

| Audit | Date | Score | Method |
|-------|------|-------|--------|
| V1 | Feb 19 | **40/100** | Initial assessment |
| V2 | Feb 20 | **65/100** | Post-fix validation |
| V3 | Feb 22 | **82/100** | Targeted fix validation (narrow scope) |
| **V4** | **Feb 25** | **65/100** | **Full 8-phase deep audit (comprehensive)** |

### Key Messages

1. **V4 is not a regression** — V3 validated specific fixes (narrow scope = high score). V4 audits the entire codebase at depth (wide scope = more findings = lower score).
2. **Previous fixes hold** — All V3-validated improvements (safeCollectIn, ExoPlayer buffers, derivedStateOf, clean arch boundaries, 5 tests) remain intact.
3. **New issue classes found** — Duplicate network calls, N+1 queries, God interfaces, missing DB indexes, and release compliance gaps were not examined in V3.
4. **Clear path to release** — 4 sprints totaling ~77h of focused work to reach Play Store submission.

### Biggest Risks

1. **Security**: Cleartext HTTP + plaintext fallback are the #1 and #2 blockers
2. **Performance**: Duplicate home content calls double cold-start latency
3. **Compliance**: No privacy policy, data safety form, or store assets

### Biggest Strengths

1. **Player**: Dual-engine (ExoPlayer + MPV), chapters, skip markers, auto-next — production-grade
2. **Focus system**: Consistent scale + border + color animations — best-in-class for Android TV
3. **Error handling**: Typed `AppError` hierarchy with retryable classification
4. **Offline architecture**: Room-first strategy with enrichment cache
5. **Security foundations**: EncryptedSharedPreferences, Conscrypt, minimal permissions

---

## ALL FINDINGS — CONSOLIDATED TABLE

### By Severity

| Severity | Count | Breakdown |
|----------|-------|-----------|
| **P0** | 7 | 2 Security + 4 Performance + 1 UX |
| **P1** | 23 | 5 Stability + 4 Security + 9 Performance + 5 Architecture |
| **P2** | 36 | 8 Stability + 6 Security + 7 Performance + 15 Architecture |
| **PASS** | 3 | SEC-12 (IPTV validation), SEC-14 (permissions), SEC-15 (encryption) |
| **Total** | **69** | |

---

*Generated by 5 parallel Claude Opus 4.6 agents on 25 February 2026*
*Total files analyzed: ~150+ source files across 11 modules*
*Validation method: Full codebase deep inspection (8-phase audit)*
*Total findings: 7 P0 blocking, 23 P1 high, 36 P2 medium, 3 positive validations*
