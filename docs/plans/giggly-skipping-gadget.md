# Plan : Logical Split Commits

## Context

All performance optimizations (Steps 1-3) + other accumulated changes are implemented and verified.
Emulator traces confirmed: Shows 284ms->226ms (-20%), Movies 489ms->357ms (-27%), no regressions.
User requested "Splits logiques" — multiple commits by domain.

55 modified files + 27 untracked + 4 staged deletions need to be organized into logical commits.

---

## Commit 1: `perf(sql): Eliminate PRINTF and add composite indexes for unified query`

Eliminates ~43k PRINTF calls per library load by replacing with arithmetic.
Adds 3 composite indexes via migration v38->v39.

**Files (4 modified + 1 untracked):**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilder.kt`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt`
- `core/database/schemas/com.chakir.plexhubtv.core.database.PlexDatabase/39.json` (untracked)

---

## Commit 2: `perf(player): Cache scrobble progress in memory to avoid PagingSource invalidation`

Replaces per-30s Room writes with in-memory ConcurrentHashMap, flushed once at playback stop.
Defers count observer after loadMetadata() to avoid 1 wasted unified query at startup.

**Files (4 modified):**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt`
- `domain/src/main/java/com/chakir/plexhubtv/domain/repository/PlaybackRepository.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerScrobbler.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt`

---

## Commit 3: `perf(player): Add stats observation toggle and conditional MpvPlayer updates`

Adds setStatsObserving() to MpvPlayer interface + MpvPlayerWrapper conditional updates.
PlaybackStatsViewModel controls the toggle. Reduces JNI/CPU overhead when debug overlay is hidden.

**Files (3 modified):**
- `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayer.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/player/PlaybackStatsViewModel.kt`

---

## Commit 4: `ref(sync): Wrap upsert+bridge in transactions and add differential cleanup`

Wraps upsert+populateIdBridge in Room transactions for atomicity.
Adds differential cleanup (delete stale ratingKeys not in latest sync).
Applies to Plex sync, backend sync, and Xtream sync repos.

**Files (5 modified):**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/XtreamSeriesRepositoryImpl.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/XtreamVodRepositoryImpl.kt`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt`

---

## Commit 5: `fix(enrichment): Improve match strategy and skip network fallback for non-Plex items`

EnrichMediaItemUseCase: skip network fallback for backend_/xtream_ items (Room miss = no match).
Stricter match: when external IDs exist, only match by ID (prevents false title+year unification).
FavoritesRepositoryImpl: group favorites by serverId for indexed batch queries.
MediaDeduplicator: minor improvement.

**Files (4 modified):**
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/EnrichMediaItemUseCase.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/FavoritesRepositoryImpl.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/aggregation/MediaDeduplicator.kt`
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/Profile.kt`

---

## Commit 6: `feat(settings): Add backend health info, cache purge worker and sync controls`

New settings UI for backend health, collection/rating sync toggles.
CachePurgeWorker for periodic cleanup. Worker improvements (CollectionSync, RatingSync).
Backend health endpoint (`getHealthInfo`), expanded BackendConnectionInfo model.

**Files (9 modified + 1 untracked):**
- `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt`
- `app/src/main/java/com/chakir/plexhubtv/work/CollectionSyncWorker.kt`
- `app/src/main/java/com/chakir/plexhubtv/work/RatingSyncWorker.kt`
- `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`
- `app/src/main/java/com/chakir/plexhubtv/work/CachePurgeWorker.kt` (untracked)
- `domain/src/main/java/com/chakir/plexhubtv/domain/repository/BackendRepository.kt`
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/BackendConnectionInfo.kt`
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/PlexApiService.kt`

---

## Commit 7: `chore(branding): Update app icons, banners and launcher assets`

Replace old PNG drawables with WebP mipmaps. New TV-specific icons and banners.
AndroidManifest updated to reference mipmap resources.

**Files (4 staged deletions + ~20 modified mipmaps + ~8 untracked + AndroidManifest + playstore PNGs):**
- `app/src/main/res/drawable/app_icon_your_company.png` (deleted)
- `app/src/main/res/drawable/ic_launcher_tv.png` (deleted)
- `app/src/main/res/drawable/movie.png` (deleted)
- `app/src/main/res/drawable/tv_banner.png` (deleted)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/ic_launcher-playstore.png`
- `app/src/main/ic_launcher_tv-playstore.png` (untracked)
- All `app/src/main/res/mipmap-*` files (modified + untracked)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_banner.xml` (untracked)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_tv.xml` (untracked)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_tv_round.xml` (untracked)
- `app/src/main/res/drawable/tv_banner.webp` (untracked)
- `app/src/main/res/plexhub_logo_final_large.png` (untracked)
- `app/src/main/res/values/ic_banner_background.xml` (untracked)
- `app/src/main/res/values/ic_launcher_tv_background.xml` (untracked)

---

## Commit 8: `chore: Misc improvements (search, navigation, build, strings)`

Remaining small changes: search screen, MainScreen, AppProfileViewModel,
build.gradle, libs.versions.toml, strings.xml.

**Files (~6 modified):**
- `app/src/main/java/com/chakir/plexhubtv/feature/search/NetflixSearchScreen.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/main/MainScreen.kt`
- `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileViewModel.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/res/values/strings.xml`

---

## Excluded

- `.claude/settings.local.json` — local IDE settings, not committed

---

## Execution Order

1. Unstage the 4 currently staged deletions (they belong to commit 7)
2. Create commits 1-8 in order
3. Each commit: `git add <specific files>` then `git commit`
4. Verify with `git log --oneline` at the end
