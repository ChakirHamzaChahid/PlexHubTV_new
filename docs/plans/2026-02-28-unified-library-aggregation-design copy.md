# Design: Unified Library Aggregation (Cross-Server Media Fusion)

**Date:** 2026-02-28
**Status:** Approved
**Approach:** A — Query-Time Unification

## Problem

When the same movie or series exists on multiple synced servers (Plex, Xtream/Backend), the library shows duplicates or incomplete metadata. Episodes from different servers are not merged — each server's seasons are shown independently.

## Goals

1. One item per movie/series in the library regardless of source count
2. "Available on..." with list of synced+enabled servers on the detail view
3. Cross-server season/episode merging (union of all episodes across servers)
4. Source selection dialog before playback when multiple servers have the episode
5. Backend Xtream treated identically to Plex for fusion rules

## Design Decisions

| Decision | Choice |
|----------|--------|
| Merge layer for episodes | Hybrid Room query at detail load time |
| IMDB/TMDB bridging | COALESCE in GROUP BY + id_bridge lookup table |
| Metadata priority | Completeness score with Plex bonus |
| Server selection for playback | Selection dialog (existing behavior) |
| "Synced" server definition | Present in Room AND enabled in Settings |
| Architecture | Query-time unification (no new materialized tables for media) |

---

## Section 1: id_bridge Table (IMDB↔TMDB Correspondence)

### Problem

Server A has `unificationId = "imdb://tt1375666"` (IMDB only). Server B has `unificationId = "tmdb://27205"` (TMDB only, backend couldn't find IMDB). These represent the same movie but won't group together.

### Solution

New Room table that maps IMDB ↔ TMDB IDs, populated whenever a media item has BOTH IDs.

```sql
CREATE TABLE id_bridge (
    imdbId TEXT NOT NULL PRIMARY KEY,
    tmdbId TEXT NOT NULL
);
CREATE INDEX idx_id_bridge_tmdb ON id_bridge(tmdbId);
```

**Population:** At every Room write (Plex sync, Backend sync, Xtream sync), if a media item has both `imdbId` and `tmdbId`, do `INSERT OR REPLACE INTO id_bridge`. Negligible cost (one extra insert per media).

**Usage:** LEFT JOIN in the unified library query to bridge tmdbId → imdbId for items missing IMDB.

**Migration:** DB version 35.

---

## Section 2: Improved GROUP BY for Unified Library

### Current Behavior

```sql
GROUP BY CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END
```

This fails when two sources have different unificationId formats (imdb:// vs tmdb://) for the same content. Metadata comes from an arbitrary row (whichever SQLite picks first).

### New GROUP BY

```sql
GROUP BY COALESCE(
    media.imdbId,                    -- Priority 1: IMDB (most stable)
    id_bridge.imdbId,                -- Priority 2: IMDB via bridge (tmdb→imdb)
    'tmdb_' || media.tmdbId,         -- Priority 3: TMDB direct
    CASE WHEN media.unificationId != ''
         THEN media.unificationId    -- Priority 4: existing unificationId (title+year)
         ELSE media.ratingKey || media.serverId  -- Priority 5: unique fallback
    END
)
```

### Metadata Priority Scoring

To ensure the "best" metadata wins when grouping, use a scored subquery:

```sql
SELECT ... FROM (
    SELECT media.*,
        id_bridge.imdbId as bridgedImdbId,
        -- Completeness score + source bonus
        (CASE WHEN media.summary IS NOT NULL AND media.summary != '' THEN 2 ELSE 0 END)
        + (CASE WHEN media.thumbUrl IS NOT NULL AND media.thumbUrl != '' THEN 2 ELSE 0 END)
        + (CASE WHEN media.imdbId IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN media.tmdbId IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN media.year IS NOT NULL AND media.year > 0 THEN 1 ELSE 0 END)
        + (CASE WHEN media.genres IS NOT NULL AND media.genres != '' THEN 1 ELSE 0 END)
        + (CASE WHEN media.serverId NOT LIKE 'xtream_%' AND media.serverId NOT LIKE 'backend_%' THEN 3 ELSE 0 END)  -- Plex bonus
        AS metadata_score
    FROM media
    LEFT JOIN id_bridge ON media.tmdbId = id_bridge.tmdbId AND media.imdbId IS NULL
    WHERE type = ?
    ORDER BY metadata_score DESC
) media
GROUP BY ...
```

SQLite's GROUP BY picks the first row per group. By sorting by `metadata_score DESC` in the subquery, the row with the best metadata (and Plex bonus) wins.

### Enabled Server Filtering

Add `AND serverId IN (?)` with the list of servers that are both present in Room and enabled in Settings.

---

## Section 3: Cross-Server Season/Episode Merging

### Current Behavior

`getShowSeasons(ratingKey, serverId)` queries ONE server only. Episodes from other servers are invisible.

### New Approach: `getUnifiedSeasons()`

**Step 1 — Fetch all episodes across enabled servers:**

```sql
SELECT * FROM media
WHERE type = 'episode'
  AND (
    grandparentRatingKey IN (
      SELECT ratingKey FROM media WHERE unificationId = :showUnificationId AND serverId IN (:enabledServerIds)
    )
    OR grandparentTitle = :showTitle
  )
  AND serverId IN (:enabledServerIds)
ORDER BY parentIndex ASC, `index` ASC
```

**Step 2 — Merge in memory (Kotlin):**

Episodes grouped by `(parentIndex, index)` → `Map<Pair<Int,Int>, List<MediaEntity>>`

For each group:
- Pick the "best" source (Plex > Backend > Xtream + completeness score)
- Store all available servers as `EpisodeSource` list

**Step 3 — Build unified seasons:**

Episodes grouped by `parentIndex` → `Map<Int, List<UnifiedEpisode>>`

Per season:
- Episode count = number of unique episode indices
- Season title = from best source
- Available servers = union of all servers having at least one episode

### Example: 24H (servers A and B synced+enabled)

```
Server A → Season 1 (8 episodes) + Season 2 (10 episodes)
Server B → Season 1 (10 episodes)

Result:
Season 1: 10 episodes
  Ep 1-8  → available on A and B
  Ep 9-10 → available on B only
Season 2: 10 episodes
  Ep 1-10 → available on A only
```

### Discovery vs Stream Details (Two-Phase)

The unified season/episode merge provides **server discovery** (which servers have each episode). Stream details (resolution, codecs, subtitles, audio tracks) are loaded **lazily**:

1. **At series open:** Room query → all sources per episode known (~15ms)
2. **At episode click:** If >1 sources → dialog opens
3. **In dialog:** For each Plex source, fetch `mediaParts` async (~200ms per server). Xtream/Backend: show server name only (no mediaParts).
4. **User picks server → playback starts**

This avoids fetching stream details for all episodes×servers upfront (which would be too expensive).

### Domain Models

```kotlin
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
    val sources: List<EpisodeSource>,
)

data class EpisodeSource(
    val serverId: String,
    val serverName: String,
    val ratingKey: String,
)
```

### Integration Point

New use case `GetUnifiedSeasonsUseCase` in `domain/`. Called by `MediaDetailViewModel.loadDetail()` when the media is a show, replacing the current `mediaDetailRepository.getShowSeasons(ratingKey, serverId)`.

---

## Section 4: Enabled Server List

A unified `getEnabledServerIds()` method provides the list of servers that are both synced and active:

1. **Plex servers:** `AuthRepository.getServers()` → filter by Settings (active servers)
2. **Xtream accounts:** `XtreamAccountRepository.observeAccounts()` → `"xtream_{id}"`
3. **Backend servers:** `BackendRepository.observeServers()` → `"backend_{id}"`
4. **Cross with Room:** `SELECT DISTINCT serverId FROM media`
5. **Return intersection**

Calculated once at startup, refreshed after each sync. Passed to:
- Library unified query (`WHERE serverId IN (?)`)
- `getUnifiedSeasons()`
- Source selection dialog

---

## Section 5: Schema Impact

**No changes to `MediaEntity`** — existing fields are sufficient:
- `unificationId`, `imdbId`, `tmdbId` → for GROUP BY
- `grandparentTitle`, `parentIndex`, `index` → for episode merge
- `serverId` → for server filtering

**New:** `id_bridge` table (Section 1). DB migration v35.

**New domain models:** `UnifiedSeason`, `UnifiedEpisode`, `EpisodeSource` (Section 3).

**No need for** `UnifiedMovie` or `UnifiedShow` — the improved GROUP BY handles movies/shows in SQL. Existing `MediaItem` with `remoteSources` suffices.

---

## Section 6: Complete Data Flow

```
UNIFIED LIBRARY (movies/series)
├── SQL: SELECT ... FROM (subquery ORDER BY metadata_score DESC)
│        LEFT JOIN id_bridge GROUP BY COALESCE(imdbId, bridgedImdbId, tmdb_..., ...)
│        WHERE serverId IN (enabled_servers)
├── Result: 1 item per unique content, best metadata, aggregated serverIds
└── Display: standard grid/list

OPEN SERIES (detail)
├── 1. mediaDetailRepository.getMediaDetail(ratingKey, serverId) → show metadata (~5ms Room)
├── 2. getUnifiedSeasons(unificationId, showTitle, enabledServerIds) → (~15ms Room)
│      └── SELECT episodes WHERE grandparentTitle=? AND serverId IN (?)
│          → groupBy(parentIndex, index) → merge → List<UnifiedSeason>
└── 3. Display: merged seasons, episode count = max across all servers

CLICK EPISODE
├── 1. Sources already known in UnifiedEpisode.sources (pre-loaded)
├── 2. If 1 source → direct playback
├── 3. If >1 sources → Selection dialog
│      └── For each Plex source: fetch mediaParts async (resolution, audio, subs)
│      └── Xtream/Backend: show server name only
└── 4. User picks → playback

id_bridge POPULATION
├── At every Room write (Plex sync, Backend sync, Xtream sync)
│   └── If media has imdbId AND tmdbId → INSERT OR REPLACE INTO id_bridge
└── Populates progressively, improves fusion over time
```

---

## Performance Analysis

| Step | Current | After Refactor | Delta |
|------|---------|----------------|-------|
| Open series → seasons | ~5ms | ~15ms | +10ms |
| Episode merge in memory | N/A | <1ms | +1ms |
| Display season episodes | ~5ms | instant (already in memory) | -5ms |
| Episode play (enrichment) | 5ms–10s | instant (sources pre-loaded) | **much faster** |
| Unified library query | ~50ms | ~60ms | +10ms |
| Stream details (dialog) | N/A (fetched at play) | ~200ms per Plex source (lazy) | comparable |
