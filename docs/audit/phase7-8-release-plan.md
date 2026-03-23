# Phase 7+8: Release Readiness & Action Plan

> **Date**: 22 March 2026
> **Branch**: `claude/continue-plexhubtv-refactor-YO43N`
> **App Version**: 1.0.15 (versionCode: 1)

---

## Executive Summary

PlexHubTV is in a **late-stage development** state with strong fundamentals but several blockers preventing a confident production release. The build system is properly configured with minification, signing, and ABI splits. Firebase Crashlytics, Analytics, and Performance are integrated with proper DEBUG gating. Localization covers English and French with 653/583 string entries respectively (70 missing in French). The primary concerns are: (1) **no Timber release tree** -- production logging goes to `/dev/null` and critical errors are not forwarded to Crashlytics, (2) **versionCode stuck at 1** -- must be incremented for every Play Store upload, (3) **73% of test files were deleted** during refactoring leaving only 38 test files, and (4) **~40+ hardcoded English strings** in composables that bypass the localization system. There are no critical security vulnerabilities; the keystore is properly gitignored, secrets use EncryptedSharedPreferences, and clear-text traffic is intentionally allowed for IPTV/LAN use cases.

**Overall Readiness**: 72/100 -- **Almost Ready** (1-2 sprints of focused work to achieve minimum viable release).

---

## Phase 7: Production Readiness Checklist

### 7.1 Build & Release Configuration

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|
| `isMinifyEnabled` | PASS | `true` in release build type | None |
| `isShrinkResources` | PASS | `true` in release build type | None |
| ProGuard rules | PASS | Comprehensive rules for Retrofit, Gson, Room, Hilt, Media3, MPV, FFmpeg, Coil, Firebase, Kotlinx Serialization, Coroutines, DataStore, AndroidX TV | None |
| Consumer rules | PASS | `data/consumer-rules.pro` and `core/network/consumer-rules.pro` propagate keep rules to app module | None |
| Release signing | PASS | External keystore via `keystore/keystore.properties`; fallback to debug signing if not found | None |
| Keystore in git | PASS | `.gitignore` contains `keystore/`, `*.jks`, `*.keystore`, `keystore.properties` | None |
| `google-services.json` in git | PASS | `.gitignore` contains `app/google-services.json` | None |
| ABI splits | PASS | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` + universal APK | None |
| versionCode | FAIL | Stuck at `1`; must increment for every Play Store upload | **Automate versionCode from CI or manual bump** |
| versionName | PASS | `1.0.15` -- reasonable semantic versioning | None |
| `compileSdk` | PASS | 36 (latest) | None |
| `targetSdk` | WARN | 35 -- acceptable, but Google Play will require 36 by August 2026 | Plan upgrade to 36 |
| `BuildConfig.DEBUG` guards | PASS | 18 occurrences across 10 files -- Firebase, Timber, logging, debug UI all properly gated | None |
| Timber `DebugTree` | PASS | Only planted when `BuildConfig.DEBUG` is true | None |
| Timber release tree | FAIL | **No release tree exists.** In production, Timber logs to nothing. `Timber.e()` and `Timber.w()` are used 410 times across 92 files but **none of these reach Crashlytics** in release builds. | **Create `CrashReportingTree` that forwards Timber.e/w to `FirebaseCrashlytics.recordException()`** |
| LeakCanary | PASS | `debugImplementation` only -- not included in release APK | None |
| Detekt + ktlint | WARN | `ignoreFailures = true` on ktlint -- linting errors don't fail the build | Consider setting `false` for CI |

### 7.2 Google Play Compliance

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|
| `INTERNET` | PASS | Required for Plex API communication | None |
| `FOREGROUND_SERVICE` | PASS | Used by WorkManager for library sync | None |
| `FOREGROUND_SERVICE_DATA_SYNC` | PASS | Correct foregroundServiceType declared in manifest | None |
| `POST_NOTIFICATIONS` | PASS | Required for sync progress notifications (SDK 33+) | None |
| `REQUEST_INSTALL_PACKAGES` | WARN | Used for self-update from GitHub APK. **Google Play may flag this.** Must declare this in Play Console's "Permissions Declaration Form" | **Add justification in Play Store listing; ensure APK update flow is documented** |
| `WRITE_EXTERNAL_STORAGE` | PASS | Not declared -- no unnecessary legacy permissions | None |
| Leanback launcher | PASS | `LEANBACK_LAUNCHER` category in intent-filter; `android.software.leanback` declared as optional | None |
| Touchscreen | PASS | `android.hardware.touchscreen` declared `required="false"` | None |
| Privacy policy | PASS | URL exists: `https://chakir-elarram.github.io/PlexHubTV/privacy-policy-en.html`; accessible from Settings > Legal | None |
| `supportsRtl` | PASS | `android:supportsRtl="true"` in manifest | None |
| `allowBackup` | PASS | `android:allowBackup="false"` -- correct for security (tokens in EncryptedSharedPreferences) | None |
| Data extraction rules | PASS | `@xml/data_extraction_rules` configured | None |
| Network security config | WARN | `cleartextTrafficPermitted="true"` as base config. Justified for IPTV/LAN, but Plex Cloud domains enforce HTTPS. **Google Play review may question this.** | Document rationale in Play Store listing |
| `largeHeap` | WARN | `android:largeHeap="true"` -- acceptable for TV media app but impacts other apps on device | Consider profiling memory to see if removable |

### 7.3 Crash Reporting

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|
| Firebase Crashlytics plugin | PASS | `firebase.crashlytics` plugin applied in both root and app `build.gradle.kts` | None |
| Firebase Performance plugin | PASS | `firebase.perf` plugin applied | None |
| Crashlytics SDK | PASS | `libs.firebase.crashlytics` dependency declared | None |
| DEBUG gating | PASS | `setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)` in `PlexHubApplication.initializeFirebase()` | None |
| Custom keys | PASS | `app_version` and `build_type` set as Crashlytics custom keys | None |
| Analytics gating | PASS | `setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)` | None |
| Performance gating | PASS | `isPerformanceCollectionEnabled = !BuildConfig.DEBUG` | None |
| DI integration | PASS | `FirebaseModule` provides `FirebaseCrashlytics` singleton via Hilt | None |
| Exception recording | WARN | Only `GlobalCoroutineExceptionHandler` calls `recordException()`. Most `Timber.e()` calls (410 across 92 files) do NOT forward to Crashlytics in release. | **Create `CrashReportingTree` (see 7.1)** |
| Player crash context | PASS | `PlayerController` sets `player_engine` and media metadata as Crashlytics custom keys | None |
| ProGuard rules | PASS | Firebase Crashlytics, Analytics, and Performance keep rules in `proguard-rules.pro` | None |
| Stack traces | PASS | `SourceFile` and `LineNumberTable` attributes kept for readable crash reports | None |

### 7.4 Accessibility

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|
| `contentDescription` coverage | GOOD | 132 occurrences across 30 composable files | None |
| `contentDescription = null` | WARN | 73 occurrences across 33 files -- decorative images/icons correctly null, but some may be interactive elements missing descriptions | **Review null descriptions on interactive elements (IconButton, clickable Image)** |
| `semantics` blocks | GOOD | 103 occurrences across 20 files -- good semantic tree annotations | None |
| String resource descriptions | PASS | Dedicated `_description` string keys exist for all major screens: auth, profile, loading, library, search, player, settings, IPTV, favorites, history, collection | None |
| D-Pad navigation | PASS | `FocusRequester` and focus management used throughout (TV-first app) | None |
| Focus indicators | PASS | TV Material Design focus ring theming applied | None |
| Screen reader support | WARN | No explicit TalkBack testing documented. Android TV accessibility is less common but growing. | Consider basic TalkBack pass |

### 7.5 Localization

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|
| English (default) | PASS | 653 string entries -- comprehensive coverage | None |
| French (values-fr) | WARN | 583 string entries -- **70 strings missing** (mostly newer features: Jellyfin setup, Xtream backend trigger, screensaver, updates, some settings sections, home layout settings, parental controls partially) | **Translate 70 missing French strings** |
| Other languages | INFO | Only EN and FR. No Arabic, Spanish, German, etc. | Consider adding popular languages post-release |
| `supportsRtl` | PASS | Enabled in manifest | None |
| Hardcoded strings | FAIL | **~40+ hardcoded English strings** found in composables: `XtreamSetupScreen` (6), `UpdateDialog` (4), `AppProfileSwitchScreen` (2), `DownloadsScreen` (1), `DebugScreen` (5), `MediaDetailScreen` (2), `NetflixDetailScreen` (6), `ServerStatusScreen` (1), `IptvScreen` (6), `JellyfinSetupScreen` (3), `DiscoverScreen/Components` (2), `ProfileFormDialog` (1) | **Extract all hardcoded strings to `strings.xml`** |
| Worker notifications | FAIL | `RatingSyncWorker` has hardcoded `"Syncing ratings..."` in notification | **Use string resource** |
| Plurals | WARN | No `<plurals>` resources used. Format strings like `"%1$d Titles"` don't handle singular/plural correctly in all languages | Consider adding plurals for counts |

### 7.6 Testing

| Item | Status | Details | Action Required |
|------|--------|---------|-----------------|

#### Current Test Files (38 files)

**App Layer (17 files)**:

| File | Location |
|------|----------|
| `ChapterMarkerManagerTest.kt` | `app/.../player/controller/` |
| `PlayerStatsTrackerTest.kt` | `app/.../player/controller/` |
| `PlayerTrackControllerTest.kt` | `app/.../player/controller/` |
| `PlayerScrobblerTest.kt` | `app/.../player/controller/` |
| `PlayerControlViewModelTest.kt` | `app/.../player/` |
| `TrackSelectionViewModelTest.kt` | `app/.../player/` |
| `PlaybackStatsViewModelTest.kt` | `app/.../player/` |
| `MediaDetailViewModelTest.kt` | `app/.../details/` |
| `HomeViewModelTest.kt` | `app/.../home/` |
| `LibraryViewModelTest.kt` | `app/.../library/` |
| `SearchViewModelTest.kt` | `app/.../search/` |
| `MainViewModelTest.kt` | `app/...` |
| `AppProfileViewModelTest.kt` | `app/.../appprofile/` |
| `PlexHomeSwitcherViewModelTest.kt` | `app/.../plexhome/` |
| `PlaylistDetailViewModelTest.kt` | `app/.../playlist/` |
| `PlaylistListViewModelTest.kt` | `app/.../playlist/` |
| `GlobalCoroutineExceptionHandlerTest.kt` | `app/.../handler/` |
| `SyncStatusModelTest.kt` | `app/.../loading/` |

**Core Layer (6 files)**:

| File | Location |
|------|----------|
| `AppErrorTest.kt` | `core/model/` |
| `UnificationIdTest.kt` | `core/model/` |
| `StringNormalizerTest.kt` | `core/common/` |
| `NetworkSecurityTest.kt` | `core/network/` |
| `AuthInterceptorTest.kt` | `core/network/` |
| `ConnectionManagerTest.kt` | `core/network/` |
| `AuthEventBusTest.kt` | `core/network/` |

**Data Layer (3 files)**:

| File | Location |
|------|----------|
| `MediaUrlResolverTest.kt` | `data/.../util/` |
| `ProfileRepositoryImplTest.kt` | `data/.../repository/` |
| `MediaMapperTest.kt` | `data/.../mapper/` |
| `MediaLibraryQueryBuilderTest.kt` | `data/.../repository/` |

**Domain Layer (8 files)**:

| File | Location |
|------|----------|
| `GetMediaDetailUseCaseTest.kt` | `domain/.../usecase/` |
| `GetUnifiedHomeContentUseCaseTest.kt` | `domain/.../usecase/` |
| `PrefetchNextEpisodeUseCaseTest.kt` | `domain/.../usecase/` |
| `SearchAcrossServersUseCaseTest.kt` | `domain/.../usecase/` |
| `SyncWatchlistUseCaseTest.kt` | `domain/.../usecase/` |
| `ToggleFavoriteUseCaseTest.kt` | `domain/.../usecase/` |
| `EnrichMediaItemUseCaseTest.kt` | `domain/.../usecase/` |
| `FilterContentByAgeUseCaseTest.kt` | `domain/.../usecase/` |
| `PlaybackManagerTest.kt` | `domain/.../service/` |

#### Testing Summary

| Metric | Value |
|--------|-------|
| Total test files | 38 |
| Files on `main` | 18 |
| Files recovered/added on branch | +20 |
| Missing critical tests | ~5 (see below) |
| Maestro E2E flows | 18 flows + 2 subflows + 1 config |
| Test stack | JUnit, MockK, Truth, Turbine, Robolectric, Coroutines Test |

#### Still Missing Tests (from MISSING_TESTS.md reconciliation)

| Test | Priority | Status |
|------|----------|--------|
| `MediaDetailRepositoryImplTest.kt` | P1 | Missing -- critical for offline-first verification |
| `PlaybackRepositoryImplTest.kt` | P1 | Missing -- URL resolution and transcoding |
| `ContentRatingHelperTest.kt` | P2 | Missing -- rating transformation |
| `MediaDeduplicatorTest.kt` | P2 | Missing -- multi-server deduplication |
| `JellyfinSourceHandlerTest.kt` | P1 | Missing -- new Jellyfin integration completely untested |

#### Maestro E2E Test Coverage

18 Maestro flows covering: auth login, profile selection, home screen, D-pad movie browsing, movie detail, playback, player controls, audio/subtitle tracks, search, favorites, TV shows, settings, multi-server source selection, downloads/offline, history, IPTV, AI visual checks, and full regression.

---

## Phase 8: Prioritized Sprint Plan

### Sprint 1: Critical Release Blockers (1 week)

| # | Task | Files to Modify | Effort | Dependency |
|---|------|-----------------|--------|------------|
| 1.1 | Create `CrashReportingTree` that forwards `Timber.e/w` to Crashlytics `recordException` | New: `app/.../handler/CrashReportingTree.kt`; Modify: `PlexHubApplication.kt` | 2h | None |
| 1.2 | Increment `versionCode` and automate via git commit count or CI | `app/build.gradle.kts` | 1h | None |
| 1.3 | Extract ~40 hardcoded strings from composables to `strings.xml` | `XtreamSetupScreen.kt`, `UpdateDialog.kt`, `DebugScreen.kt`, `NetflixDetailScreen.kt`, `IptvScreen.kt`, `JellyfinSetupScreen.kt`, `MediaDetailScreen.kt`, `DiscoverScreen.kt`, `AppProfileSwitchScreen.kt`, `DownloadsScreen.kt`, `ProfileFormDialog.kt`, `ServerStatusScreen.kt`, `RatingSyncWorker.kt` | 4h | None |
| 1.4 | Add French translations for 70 missing strings | `app/src/main/res/values-fr/strings.xml` | 3h | 1.3 |
| 1.5 | Review `contentDescription = null` on interactive elements | All 33 files with null descriptions | 2h | None |
| 1.6 | Set up release build smoke test (build + install on device) | CI or manual script | 2h | 1.1, 1.2 |

**Sprint 1 Total: ~14 hours**

### Sprint 2: Test Recovery & Data Layer Hardening (1 week)

| # | Task | Files to Modify | Effort | Dependency |
|---|------|-----------------|--------|------------|
| 2.1 | Write `MediaDetailRepositoryImplTest.kt` (offline-first cache strategy, enrichment, error handling) | New: `data/.../repository/MediaDetailRepositoryImplTest.kt` | 4h | None |
| 2.2 | Write `PlaybackRepositoryImplTest.kt` (URL resolution, transcoding, scrobbling) | New: `data/.../repository/PlaybackRepositoryImplTest.kt` | 4h | None |
| 2.3 | Write `JellyfinSourceHandlerTest.kt` (Jellyfin sync integration) | New: `data/.../source/JellyfinSourceHandlerTest.kt` | 3h | None |
| 2.4 | Write `JellyfinMapperTest.kt` (DTO to domain mapping for Jellyfin) | New: `data/.../mapper/JellyfinMapperTest.kt` | 2h | None |
| 2.5 | Run full Maestro E2E suite on release build and fix regressions | `.maestro/` flows | 4h | Sprint 1 |
| 2.6 | Set ktlint `ignoreFailures = false` and fix violations | `build.gradle.kts`, various `.kt` files | 3h | None |

**Sprint 2 Total: ~20 hours**

### Sprint 3: Production Polish (1 week)

| # | Task | Files to Modify | Effort | Dependency |
|---|------|-----------------|--------|------------|
| 3.1 | Bump `targetSdk` to 36 and resolve any behavioral changes | `app/build.gradle.kts`, potential manifest/code updates | 4h | None |
| 3.2 | Add `<plurals>` resources for count strings (titles, episodes, items) | `values/strings.xml`, `values-fr/strings.xml`, composable files | 3h | None |
| 3.3 | Audit and document `REQUEST_INSTALL_PACKAGES` rationale for Play Console | Documentation, Play Console declaration | 1h | None |
| 3.4 | Write `ContentRatingHelperTest.kt` and `MediaDeduplicatorTest.kt` (P2 tests) | New test files in `data/.../` | 3h | None |
| 3.5 | Profile memory usage and evaluate removing `largeHeap="true"` | Profiling, potential `AndroidManifest.xml` change | 3h | None |
| 3.6 | Add ProGuard mapping file upload to Firebase for symbolicated crash reports | `app/build.gradle.kts` (Firebase Crashlytics plugin handles this automatically if configured) | 1h | None |
| 3.7 | Create Play Store listing assets (screenshots, feature graphic, descriptions) | External/Play Console | 4h | None |

**Sprint 3 Total: ~19 hours**

### Sprint 4: Jellyfin Integration Stabilization (1 week)

| # | Task | Files to Modify | Effort | Dependency |
|---|------|-----------------|--------|------------|
| 4.1 | Verify Jellyfin `pageOffset` uniqueness to prevent `INSERT OR REPLACE` data loss | `JellyfinSourceHandler.kt`, `JellyfinMapper.kt` | 3h | None |
| 4.2 | Write integration test for Jellyfin full sync flow (setup -> connect -> sync -> play) | New test files | 4h | 2.3 |
| 4.3 | Test JellyfinPlaybackReporter with real server | Manual testing + fix bugs | 3h | None |
| 4.4 | Verify JellyfinUrlBuilder produces correct stream URLs for direct play and transcode | `JellyfinUrlBuilder.kt`, test file | 3h | None |
| 4.5 | Test Jellyfin image loading via `JellyfinImageInterceptor` | `JellyfinImageInterceptor.kt`, manual testing | 2h | None |
| 4.6 | Add Jellyfin-specific ProGuard testing (build release APK and test Jellyfin flow) | Manual testing | 3h | Sprint 1 |

**Sprint 4 Total: ~18 hours**

### Sprint 5: Final QA & Release (1 week)

| # | Task | Files to Modify | Effort | Dependency |
|---|------|-----------------|--------|------------|
| 5.1 | Full regression test on 3+ Android TV devices (Shield, Chromecast, Fire TV) | Manual testing | 6h | All sprints |
| 5.2 | Run full Maestro suite on release build | `.maestro/` | 2h | 5.1 |
| 5.3 | Verify Firebase Crashlytics receives test crashes in release build | `DebugViewModel.kt` test crash flow | 1h | 1.1 |
| 5.4 | Verify CrashReportingTree forwards Timber errors to Crashlytics | Manual verification | 1h | 1.1 |
| 5.5 | Performance testing: cold start time, memory footprint, scroll performance | Profiling tools | 3h | None |
| 5.6 | Final version bump, changelog, and Play Store submission | `app/build.gradle.kts`, Play Console | 2h | All |
| 5.7 | Create staged rollout plan (5% -> 20% -> 100%) | Play Console | 1h | 5.6 |
| 5.8 | Set up Firebase Crashlytics alert thresholds for crash-free rate | Firebase Console | 1h | 5.3 |

**Sprint 5 Total: ~17 hours**

---

## Readiness Score

| Category | Score (0-10) | Weight | Weighted | Blocking Issues |
|----------|-------------|--------|----------|-----------------|
| Build & Release | 8/10 | 20% | 1.6 | versionCode=1; no release Timber tree |
| Google Play Compliance | 8/10 | 15% | 1.2 | REQUEST_INSTALL_PACKAGES needs justification |
| Crash Reporting | 7/10 | 15% | 1.05 | Timber errors not forwarded to Crashlytics |
| Accessibility | 7/10 | 10% | 0.7 | 73 null contentDescriptions (some on interactive elements) |
| Localization | 6/10 | 15% | 0.9 | 70 missing FR strings; ~40 hardcoded EN strings |
| Testing | 7/10 | 25% | 1.75 | 38 test files (up from 18), but 5 critical tests still missing; 18 Maestro flows exist |
| **TOTAL** | | **100%** | **7.2/10** | **3 blocking, 4 non-blocking** |

---

## Summary

- **Ready for release**: Almost -- 1-2 sprints of focused work
- **Blocking items**: 3
  1. No Timber `CrashReportingTree` -- production errors invisible (Sprint 1, 2h fix)
  2. `versionCode = 1` -- must increment for Play Store (Sprint 1, 1h fix)
  3. ~40 hardcoded English strings bypass localization (Sprint 1, 4h fix)
- **Non-blocking but important**: 4
  1. 70 missing French translations
  2. `REQUEST_INSTALL_PACKAGES` needs Play Console justification
  3. 5 critical test files still missing
  4. Jellyfin integration is new and largely untested
- **Total sprint effort**: 5 sprints / ~88 hours / 5 weeks
- **Minimum viable release**: After Sprint 1 + Sprint 2 (2 weeks) with Plex-only scope (deferring Jellyfin to post-launch)
- **Full confidence release**: After Sprint 5 (5 weeks)

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| ProGuard strips critical class in release | Medium | High | Sprint 2.5 (Maestro on release build) |
| Jellyfin integration crashes in production | High | Medium | Sprint 4 (dedicated Jellyfin stabilization) |
| Play Store rejects due to REQUEST_INSTALL_PACKAGES | Low | Medium | Sprint 3.3 (documented justification) |
| French users encounter untranslated strings | Medium | Low | Sprint 1.3-1.4 (extract and translate) |
| Memory pressure on low-end TV devices (largeHeap) | Low | Medium | Sprint 3.5 (memory profiling) |
