# Unified Library Aggregation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deduplicate media across Plex/Xtream/Backend servers with cross-server season/episode merging, IMDB↔TMDB bridging, and metadata priority scoring.

**Architecture:** Query-time unification — no new materialized media tables. A small `id_bridge` table maps IMDB↔TMDB IDs. The library GROUP BY uses COALESCE with a metadata completeness score. Series detail loads unified seasons/episodes via a single Room query across all enabled servers.

**Tech Stack:** Room (SQLite), Kotlin Coroutines, Hilt DI, Jetpack Compose for TV, @RawQuery

**Design doc:** `docs/plans/2026-02-28-unified-library-aggregation-design.md`

---

### Task 1: id_bridge Entity + DAO + Migration

Create the IMDB↔TMDB correspondence table.

**Files:**
- Create: `core/database/src/main/java/com/chakir/plexhubtv/core/database/IdBridgeEntity.kt`
- Create: `core/database/src/main/java/com/chakir/plexhubtv/core/database/IdBridgeDao.kt`
- Modify: `core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt:28-33` (add entity + DAO)
- Modify: `core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt:313-374` (add migration 34→35 + provide DAO)

**Step 1: Create IdBridgeEntity**

```kotlin
// core/database/.../IdBridgeEntity.kt
package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "id_bridge",
    indices = [Index(value = ["tmdbId"])]
)
data class IdBridgeEntity(
    @PrimaryKey val imdbId: String,
    val tmdbId: String,
)
```

**Step 2: Create IdBridgeDao**

```kotlin
// core/database/.../IdBridgeDao.kt
package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdBridgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: IdBridgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IdBridgeEntity>)

    @Query("SELECT imdbId FROM id_bridge WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getImdbIdByTmdb(tmdbId: String): String?
}
```

**Step 3: Register in PlexDatabase**

In `PlexDatabase.kt:28` add `IdBridgeEntity::class` to the entities array.
Bump version to 35 at line 33.
Add `abstract fun idBridgeDao(): IdBridgeDao` after line 67.

**Step 4: Add migration 34→35 in DatabaseModule**

After `MIGRATION_33_34` (line 329), add:

```kotlin
private val MIGRATION_34_35 =
    object : androidx.room.migration.Migration(34, 35) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `id_bridge` (
                    `imdbId` TEXT NOT NULL PRIMARY KEY,
                    `tmdbId` TEXT NOT NULL
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_id_bridge_tmdbId` ON `id_bridge` (`tmdbId`)")
        }
    }
```

Add `MIGRATION_34_35` to the `addMigrations()` call at line 373.
Add `@Provides fun provideIdBridgeDao(database: PlexDatabase): IdBridgeDao = database.idBridgeDao()` after line 440.

**Step 5: Build and verify**

Run: `./gradlew :core:database:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Export schema and commit**

Run: `./gradlew :core:database:kspDebugKotlin` (generates schema JSON for version 35)

```bash
git add core/database/
git commit -m "feat(UNIFY-01): add id_bridge table for IMDB↔TMDB correspondence"
```

---

### Task 2: Populate id_bridge at Write Time

Inject `IdBridgeDao` into repositories that write media to Room. After each media upsert, if both `imdbId` and `tmdbId` are present, insert into `id_bridge`.

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt` (constructor + syncMedia)
- Modify: `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt` (Plex sync path)
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/XtreamVodRepositoryImpl.kt` (syncMovies)
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/XtreamSeriesRepositoryImpl.kt` (syncSeries)

**Step 1: Add IdBridgeDao to BackendRepositoryImpl constructor**

In `BackendRepositoryImpl`, add `private val idBridgeDao: IdBridgeDao` as a constructor parameter.

In the `syncMedia()` method, after `mediaDao.insertMedia(entity)` (or the batch insert), add:

```kotlin
// Populate id_bridge for IMDB↔TMDB correspondence
val bridgeEntries = entities.mapNotNull { entity ->
    val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
    val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
    if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
}
if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
```

**Step 2: Add IdBridgeDao to LibrarySyncWorker**

`LibrarySyncWorker` handles Plex sync. It calls `mediaDao.insertMedia()` for Plex content. After each batch of Plex media is written, extract bridge entries similarly.

**Note:** Check how Plex media is written. The sync worker calls `libraryRepository.syncLibrary()` which writes via `mediaDao`. The bridge population should happen where the mapper produces entities with both IDs.

If the Plex sync writes entities in bulk, add the bridge population after the bulk insert.

**Step 3: Add IdBridgeDao to XtreamVodRepositoryImpl and XtreamSeriesRepositoryImpl**

Same pattern — after `mediaDao.insertMedia()`, check for both IDs and upsert bridge entries.

**Note:** Xtream content from direct sync rarely has both IDs (no TMDB enrichment without backend). But when it does (e.g., after RatingSyncWorker scrapes), the bridge should capture it. This is defensive/future-proof.

**Step 4: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add data/ app/
git commit -m "feat(UNIFY-01): populate id_bridge at media write time"
```

---

### Task 3: Enabled Server IDs Utility

Create a reusable method to compute the list of servers that are both synced (present in Room) and enabled (not excluded in Settings).

**Files:**
- Create: `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/GetEnabledServerIdsUseCase.kt`
- Modify: `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt` (add query for distinct serverIds)

**Step 1: Add DAO query for synced serverIds**

In `MediaDao.kt`, add:

```kotlin
@Query("SELECT DISTINCT serverId FROM media")
suspend fun getDistinctServerIds(): List<String>
```

**Step 2: Create GetEnabledServerIdsUseCase**

```kotlin
// domain/.../usecase/GetEnabledServerIdsUseCase.kt
package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEnabledServerIdsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
    private val backendRepository: BackendRepository,
    private val mediaDao: MediaDao,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend operator fun invoke(): List<String> {
        // 1. All server IDs present in Room (synced at least once)
        val syncedIds = mediaDao.getDistinctServerIds().toSet()

        // 2. Excluded server IDs from Settings
        val excludedIds = settingsDataStore.excludedServerIds.first()

        // 3. Return synced AND not excluded
        return syncedIds.filter { it !in excludedIds }
    }
}
```

**Step 3: Build and verify**

Run: `./gradlew :domain:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add domain/ core/database/
git commit -m "feat(UNIFY-02): add GetEnabledServerIdsUseCase"
```

---

### Task 4: Unified Season/Episode Domain Models

**Files:**
- Create: `core/model/src/main/java/com/chakir/plexhubtv/core/model/UnifiedSeason.kt`

**Step 1: Create models**

```kotlin
// core/model/.../UnifiedSeason.kt
package com.chakir.plexhubtv.core.model

data class UnifiedSeason(
    val seasonIndex: Int,
    val title: String,
    val thumbUrl: String?,
    val episodes: List<UnifiedEpisode>,
    val availableServerIds: Set<String>,
)

data class UnifiedEpisode(
    val episodeIndex: Int,
    val title: String,
    val duration: Long?,
    val thumbUrl: String?,
    val summary: String?,
    val bestRatingKey: String,
    val bestServerId: String,
    val sources: List<EpisodeSource>,
)

data class EpisodeSource(
    val serverId: String,
    val serverName: String,
    val ratingKey: String,
)
```

**Step 2: Build and commit**

```bash
git add core/model/
git commit -m "feat(UNIFY-03): add UnifiedSeason, UnifiedEpisode, EpisodeSource models"
```

---

### Task 5: Cross-Server Episode DAO Query

Add a Room query that fetches all episodes for a show across all enabled servers.

**Files:**
- Modify: `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt`

**Step 1: Add query**

In `MediaDao.kt`, add:

```kotlin
@Query("""
    SELECT * FROM media
    WHERE type = 'episode'
    AND grandparentTitle = :showTitle
    AND serverId IN (:enabledServerIds)
    ORDER BY parentIndex ASC, `index` ASC
""")
suspend fun getUnifiedEpisodes(showTitle: String, enabledServerIds: List<String>): List<MediaEntity>
```

**Note:** Room supports `IN (:list)` for `List<String>` parameters.

**Step 2: Build and commit**

```bash
git add core/database/
git commit -m "feat(UNIFY-03): add getUnifiedEpisodes DAO query for cross-server episode fetch"
```

---

### Task 6: GetUnifiedSeasonsUseCase

The core merging logic. Fetches all episodes across servers, groups by (season, episode), picks best metadata, builds `List<UnifiedSeason>`.

**Files:**
- Create: `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/GetUnifiedSeasonsUseCase.kt`

**Step 1: Create the use case**

```kotlin
package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.EpisodeSource
import com.chakir.plexhubtv.core.model.UnifiedEpisode
import com.chakir.plexhubtv.core.model.UnifiedSeason
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetUnifiedSeasonsUseCase @Inject constructor(
    private val mediaDao: MediaDao,
    private val authRepository: AuthRepository,
    private val getEnabledServerIdsUseCase: GetEnabledServerIdsUseCase,
    @com.chakir.plexhubtv.core.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * @param showTitle grandparentTitle of the show (used to match episodes across servers)
     * @param fallbackServerId the server from which the show was opened (used as single-server fallback)
     * @param fallbackRatingKey the ratingKey of the show on that server
     */
    suspend operator fun invoke(
        showTitle: String,
        fallbackServerId: String,
        fallbackRatingKey: String,
    ): Result<List<UnifiedSeason>> = withContext(ioDispatcher) {
        runCatching {
            val enabledServerIds = getEnabledServerIdsUseCase()
            if (enabledServerIds.isEmpty()) return@runCatching emptyList()

            // Fetch all episodes of this show across enabled servers
            val allEpisodes = mediaDao.getUnifiedEpisodes(showTitle, enabledServerIds)
            if (allEpisodes.isEmpty()) return@runCatching emptyList()

            // Build server name lookup
            val serverNames = buildServerNameMap()

            // Group by (seasonIndex, episodeIndex) → merge sources
            val episodeGroups = allEpisodes
                .filter { it.parentIndex != null && it.index != null }
                .groupBy { Pair(it.parentIndex!!, it.index!!) }

            val unifiedEpisodes = episodeGroups.map { (key, entities) ->
                val (seasonIdx, epIdx) = key
                val best = pickBestEntity(entities)
                UnifiedEpisode(
                    episodeIndex = epIdx,
                    title = best.title,
                    duration = best.duration,
                    thumbUrl = best.thumbUrl ?: best.parentThumb,
                    summary = best.summary,
                    bestRatingKey = best.ratingKey,
                    bestServerId = best.serverId,
                    sources = entities.map { entity ->
                        EpisodeSource(
                            serverId = entity.serverId,
                            serverName = serverNames[entity.serverId] ?: entity.serverId,
                            ratingKey = entity.ratingKey,
                        )
                    },
                )
            }

            // Group into seasons
            unifiedEpisodes
                .groupBy { ep ->
                    // Find the seasonIndex from the episode's key
                    episodeGroups.keys.first { it.second == ep.episodeIndex &&
                        episodeGroups[it]?.any { e -> pickBestEntity(listOf(e)).ratingKey == ep.bestRatingKey } == true
                    }.first
                }

            // Simpler approach: re-group from the flat episode list using allEpisodes
            val seasonMap = mutableMapOf<Int, MutableList<UnifiedEpisode>>()
            for ((key, _) in episodeGroups) {
                val (seasonIdx, _) = key
                val ue = unifiedEpisodes.first { it.episodeIndex == key.second &&
                    it.bestRatingKey == pickBestEntity(episodeGroups[key]!!).ratingKey }
                seasonMap.getOrPut(seasonIdx) { mutableListOf() }.add(ue)
            }

            seasonMap.toSortedMap().map { (seasonIdx, episodes) ->
                val sortedEps = episodes.sortedBy { it.episodeIndex }
                val allServerIds = sortedEps.flatMap { ep -> ep.sources.map { it.serverId } }.toSet()
                // Pick best season metadata from the entities
                val seasonEntities = allEpisodes.filter { it.parentIndex == seasonIdx }
                val bestSeasonEntity = seasonEntities.maxByOrNull { metadataScore(it) }
                UnifiedSeason(
                    seasonIndex = seasonIdx,
                    title = bestSeasonEntity?.parentTitle ?: "Season $seasonIdx",
                    thumbUrl = bestSeasonEntity?.parentThumb,
                    episodes = sortedEps,
                    availableServerIds = allServerIds,
                )
            }
        }
    }

    private fun pickBestEntity(entities: List<MediaEntity>): MediaEntity {
        return entities.maxByOrNull { metadataScore(it) } ?: entities.first()
    }

    private fun metadataScore(entity: MediaEntity): Int {
        var score = 0
        if (!entity.summary.isNullOrBlank()) score += 2
        if (!entity.thumbUrl.isNullOrBlank()) score += 2
        if (!entity.imdbId.isNullOrBlank()) score += 1
        if (!entity.tmdbId.isNullOrBlank()) score += 1
        if (entity.year != null && entity.year > 0) score += 1
        if (!entity.genres.isNullOrBlank()) score += 1
        // Plex bonus: not xtream_ and not backend_
        if (!entity.serverId.startsWith("xtream_") && !entity.serverId.startsWith("backend_")) score += 3
        return score
    }

    private suspend fun buildServerNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        authRepository.getServers().getOrNull()?.forEach { server ->
            map[server.clientIdentifier] = server.name
        }
        // Xtream/Backend servers: use serverId as name fallback (will be resolved by caller if needed)
        return map
    }
}
```

**IMPORTANT NOTE:** The above is a starting point. The grouping logic between episodeGroups and seasonMap is intentionally simplified. During implementation, refactor to a cleaner single-pass approach:

```kotlin
// Cleaner approach: group all episodes by (season, episode), then reorganize
val bySeasonAndEp = allEpisodes
    .filter { it.parentIndex != null && it.index != null }
    .groupBy { it.parentIndex!! }  // group by season first

val seasons = bySeasonAndEp.toSortedMap().map { (seasonIdx, seasonEpisodes) ->
    val byEpIndex = seasonEpisodes.groupBy { it.index!! }
    val unifiedEps = byEpIndex.toSortedMap().map { (epIdx, entities) ->
        val best = pickBestEntity(entities)
        UnifiedEpisode(
            episodeIndex = epIdx,
            title = best.title,
            // ... same as above
            sources = entities.map { ... }
        )
    }
    UnifiedSeason(
        seasonIndex = seasonIdx,
        title = seasonEpisodes.firstOrNull()?.parentTitle ?: "Season $seasonIdx",
        // ...
    )
}
```

**Step 2: Build and verify**

Run: `./gradlew :domain:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add domain/
git commit -m "feat(UNIFY-03): add GetUnifiedSeasonsUseCase for cross-server episode merging"
```

---

### Task 7: Improved GROUP BY in LibraryRepositoryImpl

Replace the current GROUP BY with COALESCE-based grouping and metadata scoring.

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt:156-225`

**Step 1: Replace the unified SQL query**

The current unified query starts at line 161 with:
```kotlin
sqlBuilder.append("""SELECT media.ratingKey, media.serverId, ...
```

Replace the entire unified SQL block (lines 161-225) with the new scored subquery approach.

**Key changes:**

1. Wrap the existing SELECT in a subquery that adds `metadata_score` and LEFT JOINs `id_bridge`
2. Replace the GROUP BY (line 225) with the COALESCE-based version
3. Add `WHERE serverId IN (?)` for enabled server filtering

**The new SQL structure:**

```sql
SELECT <explicit columns>,
    GROUP_CONCAT(ratingKey) as ratingKeys,
    GROUP_CONCAT(serverId) as serverIds,
    GROUP_CONCAT(...) as alternativeThumbUrls
FROM (
    SELECT media.*,
        id_bridge.imdbId as bridgedImdbId,
        (CASE WHEN summary IS NOT NULL AND summary != '' THEN 2 ELSE 0 END)
        + (CASE WHEN thumbUrl IS NOT NULL AND thumbUrl != '' THEN 2 ELSE 0 END)
        + (CASE WHEN media.imdbId IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN media.tmdbId IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN year IS NOT NULL AND year > 0 THEN 1 ELSE 0 END)
        + (CASE WHEN genres IS NOT NULL AND genres != '' THEN 1 ELSE 0 END)
        + (CASE WHEN serverId NOT LIKE 'xtream_%' AND serverId NOT LIKE 'backend_%' THEN 3 ELSE 0 END)
        AS metadata_score
    FROM media
    LEFT JOIN id_bridge ON media.tmdbId = id_bridge.tmdbId AND media.imdbId IS NULL
    WHERE type = ?
    AND serverId IN (?)  -- enabled servers only
    ORDER BY metadata_score DESC
) media
GROUP BY COALESCE(
    media.imdbId,
    media.bridgedImdbId,
    'tmdb_' || media.tmdbId,
    CASE WHEN media.unificationId != '' THEN media.unificationId
         ELSE media.ratingKey || media.serverId
    END
)
```

**CRITICAL:** The explicit column list (lines 162-177) must remain — this is required to avoid the Room @RawQuery cursor collision pitfall (see MEMORY.md). Do NOT use `SELECT *` in the outer query.

**Step 2: Pass enabledServerIds as bind args**

The `getLibraryContent()` method needs the enabled server IDs. Add `GetEnabledServerIdsUseCase` as a dependency (or pass the list from the ViewModel). The `IN (?)` clause with dynamic list size needs to be built dynamically:

```kotlin
val enabledIds = getEnabledServerIdsUseCase()
val placeholders = enabledIds.joinToString(",") { "?" }
// Insert into WHERE clause: AND serverId IN ($placeholders)
bindArgs.addAll(enabledIds)
```

**Step 3: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add data/
git commit -m "feat(UNIFY-02): improve library GROUP BY with COALESCE and metadata scoring"
```

---

### Task 8: MediaDetailViewModel — Integrate Unified Seasons

Replace the single-server `getShowSeasons()` call with the cross-server `GetUnifiedSeasonsUseCase`.

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt:330-399`

**Step 1: Add GetUnifiedSeasonsUseCase dependency**

Add `private val getUnifiedSeasonsUseCase: GetUnifiedSeasonsUseCase` to the ViewModel constructor.

**Step 2: Replace season loading in loadDetail()**

In `loadDetail()` around line 348, the current code loads seasons from a single server:
```kotlin
val detail = mediaDetailRepository.getMediaDetail(ratingKey, serverId)
```

After detail loads successfully, instead of using `detail.children` directly for shows, call `getUnifiedSeasonsUseCase`:

```kotlin
// For shows: load unified seasons across all servers
val seasons = if (detail.item.type == MediaType.Show) {
    val showTitle = detail.item.title
    val unified = getUnifiedSeasonsUseCase(showTitle, serverId, ratingKey)
    unified.getOrNull()
} else null
```

Store the `List<UnifiedSeason>` in the UI state (may need a new field or adapt existing `seasons` field).

**Step 3: Adapt enrichment skip for backend content**

Currently line 354-358:
```kotlin
if (!detail.item.serverId.startsWith("xtream_")) {
    loadAvailableServers(detail.item)
} else {
    _uiState.update { it.copy(isEnriching = false) }
}
```

For shows with unified seasons, enrichment for the show-level item should also consider backend:
```kotlin
if (!detail.item.serverId.startsWith("xtream_") && !detail.item.serverId.startsWith("backend_")) {
    loadAvailableServers(detail.item)
} else {
    _uiState.update { it.copy(isEnriching = false) }
}
```

**Step 4: Update tests**

Modify `app/src/test/java/.../MediaDetailViewModelTest.kt` (if it exists) to mock the new `getUnifiedSeasonsUseCase` dependency.

**Step 5: Build and verify**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/
git commit -m "feat(UNIFY-04): integrate unified seasons in MediaDetailViewModel"
```

---

### Task 9: SeasonDetailViewModel — Use Pre-Loaded Sources

When an episode is played from the unified season view, the sources are already known from `UnifiedEpisode.sources`. Skip the expensive enrichment call.

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/details/SeasonDetailViewModel.kt:125-211`

**Step 1: Update PlayEpisode handling**

In the `PlayEpisode` event handler (line 125+), the current code calls `enrichMediaItemUseCase(event.episode)` to find sources. If the episode already has pre-loaded sources (from unified season data), skip enrichment:

```kotlin
is SeasonDetailEvent.PlayEpisode -> {
    viewModelScope.launch {
        _uiState.update { it.copy(isResolvingSources = true) }
        try {
            val enrichedEpisode = if (event.episode.remoteSources.size > 1) {
                // Sources already known from unified season view — skip enrichment
                event.episode
            } else {
                // Fallback: enrich if sources not pre-loaded
                enrichMediaItemUseCase(event.episode)
            }
            // ... rest unchanged (queue, source selection dialog, etc.)
```

**Note:** This requires that when navigating from the unified season view to the season detail, the `remoteSources` are pre-populated on each `MediaItem` episode. This connection is made in Task 8 when converting `UnifiedEpisode` to `MediaItem`.

**Step 2: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/
git commit -m "feat(UNIFY-04): skip enrichment for episodes with pre-loaded sources"
```

---

### Task 10: Source Selection Dialog — Lazy MediaParts Fetch

When the source selection dialog opens for a Plex source, fetch stream details (resolution, audio, subtitles) lazily.

**Files:**
- Modify: The source selection dialog composable (find the existing dialog in the details feature)
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/details/SeasonDetailViewModel.kt` (add lazy fetch)

**Step 1: Identify the existing source selection dialog**

Search for `showSourceSelection` or `SourceSelectionDialog` in the details feature. The dialog likely already exists since `SeasonDetailViewModel` has `showSourceSelection` state.

**Step 2: Add lazy stream detail loading**

When the dialog opens with multiple sources, for each Plex source (not xtream/backend), launch an async fetch of `mediaDetailRepository.getMediaDetail(ratingKey, serverId)` to get mediaParts. Update the dialog state progressively as results arrive.

For Xtream/Backend sources: show server name only (no stream details available).

**Step 3: Build and verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/
git commit -m "feat(UNIFY-05): add lazy stream detail loading in source selection dialog"
```

---

### Task 11: Build Verification and Integration Test

**Files:**
- All modified files

**Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Unit tests**

Run: `./gradlew :app:testDebugUnitTest :domain:testDebugUnitTest`
Expected: All tests pass (or document pre-existing failures)

**Step 3: Commit any fixes**

```bash
git commit -m "fix(UNIFY-05): fix compilation issues from unified library integration"
```

---

### Task 12: Final Review and Cleanup

**Step 1: Review all changes**

Run: `git diff main...HEAD --stat` to see all files changed.

Verify:
- No dead code left behind
- No unused imports
- Logging added for key operations (Timber)
- No performance regressions (Room queries have proper indexes)

**Step 2: Verify indexes**

The `id_bridge` table has index on `tmdbId`. The existing `media` table already has indexes on `imdbId`, `tmdbId`, `unificationId`, `grandparentTitle` (via `parentRatingKey` index). Verify these are sufficient for the new queries.

**Step 3: Cleanup and final commit**

```bash
git commit -m "chore(UNIFY-05): cleanup dead code from unified library refactor"
```
