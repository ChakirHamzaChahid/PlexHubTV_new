# Database Migration Strategy

## Overview

PlexHubTV uses **Room** (SQLite) with explicit, hand-written migrations. The database name is `plex_hub_db` and the current schema version is **35**.

All migrations live in `core/database/src/main/java/.../DatabaseModule.kt` as private `Migration` objects and are registered via `.addMigrations(...)` on the `RoomDatabase.Builder`.

## Rules

### 1. Never use `fallbackToDestructiveMigration()`

Early development (v1-v14) relied on destructive fallback, which silently wiped user data on every schema change. This was removed starting at v15. **Do not re-add it.** Every schema change must have an explicit migration.

### 2. Version bump = Migration object

Every change to `@Database(version = N)` in `PlexDatabase.kt` requires a corresponding `MIGRATION_(N-1)_N` object in `DatabaseModule.kt`. The migration chain must be contiguous from v11 to the current version.

### 3. Migration naming convention

```kotlin
private val MIGRATION_35_36 =
    object : androidx.room.migration.Migration(35, 36) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // SQL statements here
        }
    }
```

### 4. Use `IF NOT EXISTS` / `IF EXISTS` guards

All `CREATE TABLE`, `CREATE INDEX`, and `ALTER TABLE ADD COLUMN` statements should use defensive guards to prevent crashes on edge-case upgrade paths:

```sql
CREATE TABLE IF NOT EXISTS `new_table` (...)
CREATE INDEX IF NOT EXISTS `index_name` ON `table` (`column`)
```

SQLite does not support `ALTER TABLE ADD COLUMN IF NOT EXISTS`, so use a try-catch or check `PRAGMA table_info(table)` if there's a risk the column already exists.

### 5. Keep migrations idempotent when possible

A migration that runs twice should not crash. Use `IF NOT EXISTS` guards and avoid `DROP TABLE` unless replacing a table entirely.

### 6. Add new migrations to `.addMigrations()` list

After creating the `MIGRATION_(N-1)_N` object, add it to the `.addMigrations(...)` call in `providePlexDatabase()`. Order matters — Room validates the chain.

### 7. Update `@Database(version = N)` in PlexDatabase.kt

Bump the version number in the `@Database` annotation to match the new migration target version.

### 8. Update `UNIFIED_SELECT` when adding columns to `MediaEntity`

If a new column is added to `MediaEntity`, it **must** also be added to the explicit column list in `MediaLibraryQueryBuilder.UNIFIED_SELECT` and `NON_UNIFIED_SELECT`. Missing columns cause silent `LoadState.Error` in paged queries — no crash, no items displayed.

## Current migration chain

| Migration | Description |
|-----------|------------|
| 11 -> 12 | Add indexes on `guid`, `type+addedAt`, `imdbId`, `tmdbId` |
| 12 -> 13 | No-op (early dev version bump) |
| 13 -> 14 | No-op (early dev version bump) |
| 14 -> 15 | Defensive CREATE TABLE IF NOT EXISTS for all base tables |
| 15 -> 16 | Add `pageOffset` column to media table |
| 16 -> 17 | No-op (early dev version bump) |
| 17 -> 18 | No-op (early dev version bump) |
| 18 -> 19 | Add Xtream and backend tables |
| 19 -> 20 | Add `seasonIndex`, `episodeIndex` columns |
| 20 -> 21 | Add `xtreamSeriesId` column |
| 21 -> 22 | Add `displayRating` column |
| 22 -> 23 | Add collection tables |
| 23 -> 24 | Add `scrapedRating` column + rating indexes |
| 24 -> 25 | Add `alternativeThumbUrls` column |
| 25 -> 26 | Add `contentRating` column |
| 26 -> 27 | Add `xtreamEpisodeId` column |
| 27 -> 28 | Add `historyGroupKey` column |
| 28 -> 29 | Add `type+displayRating` composite index |
| 29 -> 30 | Add track_preferences + search_cache tables |
| 30 -> 31 | Add id_bridge, profile, xtream_accounts tables |
| 31 -> 32 | Add `isKidsProfile`, `ageRating`, `pin` columns to profiles |
| 32 -> 33 | Add backend_servers table |
| 33 -> 34 | Add `resolvedThumbUrl` column |
| 34 -> 35 | Add `parentalPin` column to profiles |
| 35 -> 36 | Add composite indexes for query optimization (Issue #113) |

## Checklist for adding a new migration

1. [ ] Add new column/table/index to the `MediaEntity` (or other entity) class
2. [ ] Bump `@Database(version = N+1)` in `PlexDatabase.kt`
3. [ ] Create `MIGRATION_N_(N+1)` object in `DatabaseModule.kt` with the SQL
4. [ ] Register it in `.addMigrations(...)` list
5. [ ] If touching `MediaEntity`: update `UNIFIED_SELECT` and `NON_UNIFIED_SELECT` in `MediaLibraryQueryBuilder`
6. [ ] Build and run — Room validates the schema at runtime
7. [ ] Test upgrade path from previous version on a real device or emulator

## Database configuration

- **Journal mode**: WAL (Write-Ahead Logging) — concurrent reads during writes
- **Synchronous**: NORMAL — good balance between durability and performance
- **Cache size**: 8 MB (`PRAGMA cache_size = -8000`)
- **No `allowMainThreadQueries()`** — all DAO methods are `suspend` or return `Flow`

## Threading rules

- All DAO queries must be either `suspend` functions or return `Flow<T>`
- Repository implementations must use `withContext(ioDispatcher)` or `flowOn(ioDispatcher)`
- Never use `runBlocking` to call DAO methods from non-suspend contexts
- The `@IoDispatcher` qualifier provides `Dispatchers.IO` via Hilt
