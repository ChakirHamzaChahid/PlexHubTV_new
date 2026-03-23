# Plan : Audit PlexHubTV — Production-Ready (Re-Audit)

## Context

A previous audit (Feb 19, 2026 — `PRODUCTION_AUDIT_REPORT.md`) scored the app **4/10** with 10 P0 blockers. Since then, significant fixes have been applied. This re-audit will:
1. Verify which P0s from the previous audit are resolved
2. Find any NEW issues introduced by the fixes
3. Produce a fresh, accurate score and action plan

### Already Fixed (verified from code review)
- TrustManager now hostname-aware (private IPs only) — `NetworkModule.kt`
- Firebase Crashlytics/Analytics/Performance integrated — `PlexHubApplication.kt`
- `!!` reduced to 4 (test files only) — was pervasive
- PlayerController scope properly cancelled in `release()` + recreated
- `GlobalCoroutineExceptionHandler` with Crashlytics reporting
- 401 detection via `AuthInterceptor` + `AuthEventBus`
- Coil upgraded 2.7 → 3.3.0
- `values-fr/strings.xml` added
- Player network error handling + retry + MPV fallback

### Still Open (preliminary)
- `cleartextTrafficPermitted="true"` globally (TODO in XML)
- 0 `.catch {}` on Flows in app/src/main
- Only 4 `@Immutable`/`@Stable` annotations
- MISSING_TESTS.md: 73% test reduction still in effect

---

## Execution Strategy

Launch **5 parallel agents** matching the audit prompt's agent structure. Each agent produces a structured report section. I then synthesize into the final `PRODUCTION_AUDIT_REPORT_V2.md`.

---

## Agent 1 — "Sentinel" (Stability & Security)

**Scope**: Phases 1 + 2 from audit prompt

**Files to analyze**:
- `core/network/src/main/java/.../AuthInterceptor.kt` — 401 handling
- `core/network/src/main/java/.../NetworkModule.kt` — TrustManager, SSL
- `core/datastore/src/main/java/.../SecurePreferencesManager.kt` — encryption
- `core/common/src/main/java/.../GlobalCoroutineExceptionHandler.kt`
- `core/model/src/main/java/.../AppError.kt` + `ErrorExtensions.kt`
- `app/src/main/java/.../PlexHubApplication.kt` — Firebase init
- `app/src/main/java/.../MainViewModel.kt` — 401 dialog handling
- `core/common/src/main/java/.../auth/AuthEventBus.kt`
- `app/src/main/res/xml/network_security_config.xml` — cleartext
- `app/proguard-rules.pro` — R8 rules
- `data/src/main/java/.../AuthRepositoryImpl.kt` — null safety
- `data/src/main/java/.../PlaybackRepositoryImpl.kt`
- `core/network/src/main/java/.../ConnectionManager.kt` — race conditions
- `app/src/main/java/.../feature/player/controller/PlayerController.kt` — lifecycle
- `app/src/main/java/.../feature/splash/SplashViewModel.kt` — race
- `domain/src/main/java/.../service/PlaybackManager.kt` — race
- `core/datastore/src/main/java/.../SettingsDataStore.kt` — scope leak
- All ViewModels — check Flow `.catch {}`, Channel patterns
- Search for: hardcoded tokens, API keys in source

**Deliverable**: Crash vectors + Security issues, classified P0/P1/P2

---

## Agent 2 — "Turbo" (Performance)

**Scope**: Phase 3 from audit prompt

**Files to analyze**:
- `app/src/main/java/.../di/image/ImageModule.kt` — Coil config (DONE: adaptive cache)
- `app/src/main/java/.../di/image/PerformanceImageInterceptor.kt` — perf gating
- `data/src/main/java/.../repository/LibraryRepositoryImpl.kt` — PagingConfig
- `data/src/main/java/.../paging/MediaRemoteMediator.kt`
- `core/database/src/main/java/.../MediaDao.kt` — Room queries, indexes
- `core/database/src/main/java/.../PlexDatabase.kt` — WAL, pragmas
- `app/src/main/java/.../feature/home/NetflixHomeScreen.kt` — Compose perf
- `core/ui/src/main/java/.../NetflixContentRow.kt` — LazyRow key param
- `core/ui/src/main/java/.../NetflixMediaCard.kt` — recomposition
- `app/src/main/java/.../PlexHubApplication.kt` — cold start init
- `app/src/main/java/.../feature/player/PlayerFactory.kt` — buffer config
- `core/network/src/main/java/.../PlexApiCache.kt` — HTTP caching
- `app/build.gradle.kts` — ABI splits, APK size
- `gradle/libs.versions.toml` — dependency versions
- All Compose screens — check `key`, `remember`, `derivedStateOf`

**Deliverable**: Top 10 bottlenecks + Quick wins + Long-term optimizations

---

## Agent 3 — "Blueprint" (Architecture & Quality)

**Scope**: Phase 4 from audit prompt

**Files to analyze**:
- All `build.gradle.kts` files — dependency violations
- `core/model/build.gradle.kts`, `core/navigation/build.gradle.kts` — previous P0 dependency bloat
- `data/src/main/java/.../di/RepositoryModule.kt` — bindings
- `domain/` — verify no Android imports
- `core/network/` — check if still imports `core:database`
- `data/src/main/java/.../mapper/MediaMapper.kt` — duplication
- `data/src/main/java/.../mapper/ServerMapper.kt`
- `app/src/main/java/.../feature/player/controller/PlayerController.kt` — direct DAO access
- `MISSING_TESTS.md` — test restoration priorities
- All existing test files — verify coverage
- Search for dead code, orphan functions, unused imports

**Deliverable**: Module scores A-F + violations + tech debt estimation

---

## Agent 4 — "Pixel" (UX TV & Features)

**Scope**: Phases 5 + 6 from audit prompt

**Files to analyze**:
- `app/src/main/java/.../feature/auth/AuthScreen.kt` — PIN flow, focus
- `app/src/main/java/.../feature/auth/profiles/ProfileScreen.kt` — focus indicators
- `app/src/main/java/.../feature/details/SeasonDetailScreen.kt` — TV layout
- `app/src/main/java/.../feature/details/NetflixDetailScreen.kt` — detail layout
- `app/src/main/java/.../feature/home/NetflixHomeScreen.kt` — hero, rows
- `app/src/main/java/.../feature/search/NetflixSearchScreen.kt` — keyboard focus
- `app/src/main/java/.../feature/library/LibrariesScreen.kt` — grid, filters
- `app/src/main/java/.../feature/player/VideoPlayerScreen.kt` — controls
- `app/src/main/java/.../feature/player/components/NetflixPlayerControls.kt`
- `app/src/main/java/.../feature/favorites/FavoritesScreen.kt` — focus bug
- `app/src/main/java/.../feature/downloads/DownloadsScreen.kt`
- `app/src/main/java/.../feature/main/MainScreen.kt` — sidebar
- `app/src/main/java/.../feature/main/AppSidebar.kt`
- `app/src/main/java/.../feature/splash/SplashScreen.kt` — skip/fallback
- `core/ui/src/main/java/.../` — all shared components
- `core/designsystem/src/main/java/.../Theme.kt` — typography sizes
- `app/src/main/res/values/strings.xml` + `values-fr/strings.xml`
- All screens — D-Pad simulation, empty states, loading states, error states

**Deliverable**: UX issues by screen + Focus dead zones + Feature ranking

---

## Agent 5 — "Launcher" (Production Readiness)

**Scope**: Phases 7 + 8 from audit prompt

**Files to analyze**:
- `app/build.gradle.kts` — signing, R8, versions, ABI splits
- `app/proguard-rules.pro` — keep rules
- `app/src/main/AndroidManifest.xml` — permissions, features
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/java/.../PlexHubApplication.kt` — Firebase, Timber
- `gradle/libs.versions.toml` — dependency versions, CVEs
- `app/src/main/res/values/strings.xml` — externalization
- `app/src/main/res/values-fr/strings.xml` — FR translation
- `MISSING_TESTS.md` — test status
- All existing test files — count, verify passing
- Search for: hardcoded strings in composables, missing contentDescription
- Check: `versionCode` strategy, mapping file upload, debug route in release

**Deliverable**: Play Store checklist + Release blockers + Sprint plan

---

## Synthesis Phase

After all 5 agents complete, I will:

1. **Cross-reference** findings — deduplicate, resolve conflicts
2. **Re-verify** key P0 claims by reading the actual code
3. **Score** each dimension (Stability, Security, Performance, Architecture, UX TV) out of 20
4. **Produce** `PRODUCTION_AUDIT_REPORT_V2.md` with:
   - Executive summary + global score /100
   - Top 10 P0 blockers (deduplicated)
   - Decision matrix (Action × Agent × File × Effort × Impact × Sprint)
   - Final verdict (vendable today? how many fixes to get there?)
   - Sprint plan (4-5 sprints)

---

## Verification

- Each agent must cite **exact file path + line number** for every finding
- Agents must verify findings against actual code (not assumptions from previous audit)
- Previous audit findings that are FIXED must be explicitly marked as resolved
- New findings must be clearly distinguished from carryover issues
- Final report will be written to `PRODUCTION_AUDIT_REPORT_V2.md` at project root
