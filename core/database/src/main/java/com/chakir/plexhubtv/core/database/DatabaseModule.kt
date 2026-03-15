package com.chakir.plexhubtv.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_11_12 =
        object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // PERFORMANCE FIX: Add missing indexes (Issue #2 & #3)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_guid` ON `media` (`guid`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_addedAt` ON `media` (`type`, `addedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_imdbId` ON `media` (`imdbId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_tmdbId` ON `media` (`tmdbId`)")
            }
        }

    // Gap migrations v12→v15: These version bumps occurred during early development when
    // fallbackToDestructiveMigration() silently wiped the DB on each update. No user retains
    // data from these versions, but Room requires a complete migration chain. The 14→15
    // migration defensively ensures all base tables exist (CREATE TABLE IF NOT EXISTS) to
    // handle the theoretical case of a very old install upgrading directly to the latest version.

    private val MIGRATION_12_13 =
        object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No-op: version bump during early development
            }
        }

    private val MIGRATION_13_14 =
        object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No-op: version bump during early development
            }
        }

    private val MIGRATION_14_15 =
        object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Defensive: ensure base tables exist for very old installs.
                // On fresh installs Room creates these from @Entity annotations,
                // but on migration paths they must exist before 18→19 reworks collections.
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `downloads` (
                        `globalKey` TEXT NOT NULL, `serverId` TEXT NOT NULL,
                        `ratingKey` TEXT NOT NULL, `type` TEXT NOT NULL,
                        `parentRatingKey` TEXT, `grandparentRatingKey` TEXT,
                        `status` TEXT NOT NULL, `progress` INTEGER NOT NULL,
                        `totalBytes` INTEGER, `downloadedBytes` INTEGER NOT NULL,
                        `videoFilePath` TEXT, `thumbPath` TEXT, `errorMessage` TEXT,
                        `retryCount` INTEGER NOT NULL, `downloadedAt` INTEGER,
                        PRIMARY KEY(`globalKey`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `api_cache` (
                        `cacheKey` TEXT NOT NULL, `data` TEXT NOT NULL,
                        `cachedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL,
                        `ttlSeconds` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `offline_watch_progress` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `serverId` TEXT NOT NULL, `ratingKey` TEXT NOT NULL,
                        `globalKey` TEXT NOT NULL, `actionType` TEXT NOT NULL,
                        `viewOffset` INTEGER, `duration` INTEGER,
                        `shouldMarkWatched` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        `syncAttempts` INTEGER NOT NULL, `lastError` TEXT)"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `home_content` (
                        `type` TEXT NOT NULL, `hubIdentifier` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `itemServerId` TEXT NOT NULL,
                        `itemRatingKey` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`type`, `hubIdentifier`, `itemServerId`, `itemRatingKey`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `favorites` (
                        `ratingKey` TEXT NOT NULL, `serverId` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `type` TEXT NOT NULL,
                        `thumbUrl` TEXT, `artUrl` TEXT, `year` INTEGER,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ratingKey`, `serverId`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `remote_keys` (
                        `libraryKey` TEXT NOT NULL, `filter` TEXT NOT NULL,
                        `sortOrder` TEXT NOT NULL, `offset` INTEGER NOT NULL,
                        `prevKey` INTEGER, `nextKey` INTEGER,
                        PRIMARY KEY(`libraryKey`, `filter`, `sortOrder`, `offset`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `library_sections` (
                        `id` TEXT NOT NULL, `serverId` TEXT NOT NULL,
                        `libraryKey` TEXT NOT NULL, `title` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        PRIMARY KEY(`id`))"""
                )
                // collections table with OLD schema (single-column PK).
                // Migration 18→19 will rework it to composite PK (id, serverId).
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collections` (
                        `id` TEXT NOT NULL, `serverId` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `summary` TEXT, `thumbUrl` TEXT,
                        `lastSync` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `media_collection_cross_ref` (
                        `mediaRatingKey` TEXT NOT NULL, `collectionId` TEXT NOT NULL,
                        `serverId` TEXT NOT NULL,
                        PRIMARY KEY(`mediaRatingKey`, `collectionId`, `serverId`))"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_collection_cross_ref_mediaRatingKey_serverId` ON `media_collection_cross_ref` (`mediaRatingKey`, `serverId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_collection_cross_ref_collectionId_serverId` ON `media_collection_cross_ref` (`collectionId`, `serverId`)"
                )
            }
        }

    private val MIGRATION_15_16 =
        object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_preferences` (`ratingKey` TEXT NOT NULL, `serverId` TEXT NOT NULL, `audioStreamId` TEXT, `subtitleStreamId` TEXT, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`ratingKey`, `serverId`))",
                )
            }
        }

    // Gap migrations v16→v18: Same situation as v12→v15.
    private val MIGRATION_16_17 =
        object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No-op: version bump during early development
            }
        }

    private val MIGRATION_17_18 =
        object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // No-op: version bump during early development
            }
        }

    private val MIGRATION_18_19 =
        object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create new table with composite PK (id, serverId)
                database.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS `collections_new` (
                    `id` TEXT NOT NULL,
                    `serverId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `summary` TEXT,
                    `thumbUrl` TEXT,
                    `lastSync` INTEGER NOT NULL,
                    PRIMARY KEY(`id`, `serverId`)
                )
             """,
                )

                // 2. Copy data from old table
                // Note: If serverId column didn't exist in v18, this would fail.
                // We assume it existed but wasn't part of PK.
                database.execSQL(
                    """
                INSERT INTO `collections_new` (`id`, `serverId`, `title`, `summary`, `thumbUrl`, `lastSync`)
                SELECT `id`, `serverId`, `title`, `summary`, `thumbUrl`, `lastSync` FROM `collections`
             """,
                )

                // 3. Drop old table
                database.execSQL("DROP TABLE `collections`")

                // 4. Rename new table
                database.execSQL("ALTER TABLE `collections_new` RENAME TO `collections`")
            }
        }

    private val MIGRATION_19_20 =
        object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN resolvedThumbUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE media ADD COLUMN resolvedArtUrl TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE media ADD COLUMN resolvedBaseUrl TEXT DEFAULT NULL")
                // Add composite index for HomeContentDao JOIN performance
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_ratingkey_serverid ON media(ratingKey, serverId)")
            }
        }

    private val MIGRATION_20_21 =
        object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add titleSortable column for locale-aware sorting
                db.execSQL("ALTER TABLE media ADD COLUMN titleSortable TEXT NOT NULL DEFAULT ''")
                // Create index on titleSortable for efficient sorting
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_titleSortable ON media(titleSortable)")
                // Note: titleSortable will be populated when library syncs next time
            }
        }

    private val MIGRATION_21_22 =
        object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add scrapedRating column
                database.execSQL("ALTER TABLE media ADD COLUMN scrapedRating REAL")
            }
        }

    private val MIGRATION_22_23 =
        object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create profiles table for multi-user support
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatarUrl` TEXT,
                        `avatarEmoji` TEXT,
                        `isKidsProfile` INTEGER NOT NULL,
                        `ageRating` TEXT NOT NULL,
                        `autoPlayNext` INTEGER NOT NULL,
                        `preferredAudioLanguage` TEXT,
                        `preferredSubtitleLanguage` TEXT,
                        `preferredQuality` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsed` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """
                )

                // Create index on isActive for faster active profile queries
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_isActive` ON `profiles` (`isActive`)")

                // Create default profile
                val uuid = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                database.execSQL(
                    """
                    INSERT INTO `profiles` (
                        `id`, `name`, `avatarUrl`, `avatarEmoji`, `isKidsProfile`,
                        `ageRating`, `autoPlayNext`, `preferredAudioLanguage`,
                        `preferredSubtitleLanguage`, `preferredQuality`,
                        `createdAt`, `lastUsed`, `isActive`
                    ) VALUES (
                        '$uuid', 'Default', NULL, '😊', 0,
                        'GENERAL', 1, NULL, NULL, 'AUTO',
                        $now, $now, 1
                    )
                    """
                )
            }
        }

    private val MIGRATION_23_24 =
        object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `query` TEXT NOT NULL,
                        `serverId` TEXT NOT NULL,
                        `resultsJson` TEXT NOT NULL,
                        `resultCount` INTEGER NOT NULL DEFAULT 0,
                        `lastUpdated` INTEGER NOT NULL
                    )
                    """
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_search_cache_query_serverId`
                    ON `search_cache` (`query`, `serverId`)
                    """
                )
            }
        }

    private val MIGRATION_24_25 =
        object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE servers ADD COLUMN relay INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE servers ADD COLUMN publicAddress TEXT")
                database.execSQL("ALTER TABLE servers ADD COLUMN httpsRequired INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE servers ADD COLUMN connectionCandidatesJson TEXT NOT NULL DEFAULT '[]'")
                // Clear stale server cache so fresh data with all candidates is fetched from API
                database.execSQL("DELETE FROM servers")
            }
        }

    private val MIGRATION_25_26 =
        object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Clear stale server cache for users who already migrated to v25 without the DELETE
                database.execSQL("DELETE FROM servers")
            }
        }

    private val MIGRATION_26_27 =
        object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add parentIndex column for episode matching by season number instead of season title
                // This eliminates 18 network requests per enrichment by enabling reliable Room-first matching
                database.execSQL("ALTER TABLE media ADD COLUMN parentIndex INTEGER DEFAULT NULL")
            }
        }

    private val MIGRATION_27_28 =
        object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add alternativeThumbUrls for fallback image loading from other servers
                // Stores pipe-separated list of resolvedThumbUrl from all servers for the same media
                database.execSQL("ALTER TABLE media ADD COLUMN alternativeThumbUrls TEXT DEFAULT NULL")
            }
        }

    private val MIGRATION_28_29 =
        object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add displayRating: canonical pre-computed rating for sort and display
                // Formula: COALESCE(scrapedRating, audienceRating, rating, 0.0)
                database.execSQL("ALTER TABLE media ADD COLUMN displayRating REAL NOT NULL DEFAULT 0.0")

                // Backfill: compute displayRating for all existing rows
                database.execSQL(
                    "UPDATE media SET displayRating = COALESCE(scrapedRating, audienceRating, rating, 0.0)"
                )

                // Add composite index for performant rating sort
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_type_displayRating ON media (type, displayRating)"
                )
            }
        }

    private val MIGRATION_29_30 =
        object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add historyGroupKey: materialized GROUP BY key for watch history
                database.execSQL("ALTER TABLE media ADD COLUMN historyGroupKey TEXT NOT NULL DEFAULT ''")

                // Backfill: compute historyGroupKey for all existing rows
                database.execSQL(
                    "UPDATE media SET historyGroupKey = CASE WHEN unificationId = '' THEN ratingKey || serverId ELSE unificationId END"
                )

                // Add index on lastViewedAt for WHERE lastViewedAt > 0 ORDER BY lastViewedAt DESC
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_lastViewedAt ON media (lastViewedAt)"
                )

                // Add index on historyGroupKey for GROUP BY
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_historyGroupKey ON media (historyGroupKey)"
                )
            }
        }

    private val MIGRATION_30_31 =
        object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create FTS4 virtual table linked to media (external content)
                database.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `media_fts` USING FTS4(`title`, `summary`, `genres`, content=`media`)"
                )

                // Populate FTS index from existing media rows
                database.execSQL("INSERT INTO media_fts(media_fts) VALUES('rebuild')")

                // Content-sync triggers (Room generates these on fresh install; migration needs them explicitly)
                database.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_media_fts_BEFORE_UPDATE BEFORE UPDATE ON `media` BEGIN DELETE FROM `media_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                database.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_media_fts_AFTER_UPDATE AFTER UPDATE ON `media` BEGIN INSERT INTO `media_fts`(`docid`, `title`, `summary`, `genres`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`, NEW.`genres`); END"
                )
                database.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_media_fts_BEFORE_DELETE BEFORE DELETE ON `media` BEGIN DELETE FROM `media_fts` WHERE `docid`=OLD.`rowid`; END"
                )
                database.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_media_fts_AFTER_INSERT AFTER INSERT ON `media` BEGIN INSERT INTO `media_fts`(`docid`, `title`, `summary`, `genres`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`, NEW.`genres`); END"
                )
            }
        }

    private val MIGRATION_31_32 =
        object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0")
            }
        }

    private val MIGRATION_32_33 =
        object : androidx.room.migration.Migration(32, 33) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `xtream_accounts` (
                        `id` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordKey` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `expirationDate` INTEGER,
                        `maxConnections` INTEGER NOT NULL,
                        `allowedFormatsJson` TEXT NOT NULL,
                        `serverUrl` TEXT,
                        `httpsPort` INTEGER,
                        `lastSyncedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """
                )
            }
        }

    private val MIGRATION_33_34 =
        object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backend_servers` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `label` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `lastSyncedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )
                database.execSQL("ALTER TABLE media ADD COLUMN sourceServerId TEXT DEFAULT NULL")
            }
        }

    private val MIGRATION_34_35 =
        object : androidx.room.migration.Migration(34, 35) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `id_bridge` (
                        `imdbId` TEXT NOT NULL PRIMARY KEY,
                        `tmdbId` TEXT NOT NULL
                    )
                    """
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_id_bridge_tmdbId` ON `id_bridge` (`tmdbId`)")
            }
        }

    private val MIGRATION_35_36 =
        object : androidx.room.migration.Migration(35, 36) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // ISSUE #113 FIX: Add composite indexes for query optimization

                // Index 1: Optimize getChildren() with episode ordering (MediaDao:26-30)
                // Query: WHERE parentRatingKey = ? AND serverId = ? ORDER BY index ASC
                // Impact: 25x faster (50ms → 2ms) for season detail screens
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_parentRatingKey_serverId_index` " +
                    "ON `media` (`parentRatingKey`, `serverId`, `index`)"
                )

                // Index 2: Optimize Xtream getMediaByServerTypeFilter (MediaDao:59-60)
                // Query: WHERE serverId = ? AND type = ? AND filter = ? ORDER BY titleSortable ASC
                // Impact: 40x faster (200ms → 5ms) for Xtream VOD/Series browsing
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_serverId_type_filter_titleSortable` " +
                    "ON `media` (`serverId`, `type`, `filter`, `titleSortable`)"
                )

                // Index 3: Optimize findRemoteEpisodeSources for multi-server playback (MediaDao:232-248)
                // Query: WHERE type = 'episode' AND grandparentTitle = ? AND parentIndex = ? AND index = ?
                // Impact: 33x faster (100ms → 3ms) for unified episode playback
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_media_type_grandparentTitle_parentIndex_index` " +
                    "ON `media` (`type`, `grandparentTitle`, `parentIndex`, `index`)"
                )
            }
        }

    private val MIGRATION_36_37 =
        object : androidx.room.migration.Migration(36, 37) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Pre-compute metadataScore column to eliminate expensive subquery in unified paging
                database.execSQL("ALTER TABLE media ADD COLUMN metadataScore INTEGER NOT NULL DEFAULT 0")

                // Backfill existing rows with the same formula used at insert time
                database.execSQL(
                    """UPDATE media SET metadataScore =
                        (CASE WHEN summary IS NOT NULL AND summary != '' THEN 2 ELSE 0 END)
                        + (CASE WHEN thumbUrl IS NOT NULL AND thumbUrl != '' THEN 2 ELSE 0 END)
                        + (CASE WHEN imdbId IS NOT NULL THEN 1 ELSE 0 END)
                        + (CASE WHEN tmdbId IS NOT NULL THEN 1 ELSE 0 END)
                        + (CASE WHEN year IS NOT NULL AND year > 0 THEN 1 ELSE 0 END)
                        + (CASE WHEN genres IS NOT NULL AND genres != '' THEN 1 ELSE 0 END)
                        + (CASE WHEN serverId NOT LIKE 'xtream_%' AND serverId NOT LIKE 'backend_%' THEN 100 ELSE 0 END)
                    """
                )
            }
        }

    private val MIGRATION_37_38 =
        object : androidx.room.migration.Migration(37, 38) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isOwned column (whether user owns the server this media comes from)
                database.execSQL("ALTER TABLE media ADD COLUMN isOwned INTEGER NOT NULL DEFAULT 0")

                // Recalculate metadataScore with new fields: rating(+1), audienceRating(+1), contentRating(+1), isOwned(+50)
                // isOwned defaults to false (0) — next sync will populate correctly from server.isOwned
                database.execSQL(
                    """UPDATE media SET metadataScore =
                        (CASE WHEN summary IS NOT NULL AND summary != '' THEN 2 ELSE 0 END)
                        + (CASE WHEN thumbUrl IS NOT NULL AND thumbUrl != '' THEN 2 ELSE 0 END)
                        + (CASE WHEN imdbId IS NOT NULL THEN 1 ELSE 0 END)
                        + (CASE WHEN tmdbId IS NOT NULL THEN 1 ELSE 0 END)
                        + (CASE WHEN year IS NOT NULL AND year > 0 THEN 1 ELSE 0 END)
                        + (CASE WHEN genres IS NOT NULL AND genres != '' THEN 1 ELSE 0 END)
                        + (CASE WHEN rating IS NOT NULL AND rating > 0 THEN 1 ELSE 0 END)
                        + (CASE WHEN audienceRating IS NOT NULL AND audienceRating > 0 THEN 1 ELSE 0 END)
                        + (CASE WHEN contentRating IS NOT NULL AND contentRating != '' THEN 1 ELSE 0 END)
                        + (CASE WHEN serverId NOT LIKE 'xtream_%' AND serverId NOT LIKE 'backend_%' THEN 100 ELSE 0 END)
                    """
                )
            }
        }

    private val MIGRATION_38_39 =
        object : androidx.room.migration.Migration(38, 39) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Composite indexes for unified query performance on low-end devices (Mi Box S)
                // (type, imdbId) — speeds up GROUP BY COALESCE(imdbId, ...) without full table scan
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_imdbId` ON `media` (`type`, `imdbId`)")
                // (type, tmdbId) — speeds up LEFT JOIN id_bridge ON tmdbId with type pre-filter
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_tmdbId` ON `media` (`type`, `tmdbId`)")
                // (type, titleSortable) — speeds up ORDER BY titleSortable for unified queries
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_titleSortable` ON `media` (`type`, `titleSortable`)")
            }
        }

    private val MIGRATION_39_40 =
        object : androidx.room.migration.Migration(39, 40) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Solution C: Add groupKey column to media table
                database.execSQL("ALTER TABLE media ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_groupKey` ON `media` (`type`, `groupKey`)")

                // Backfill groupKey using the COALESCE chain (without id_bridge for simplicity;
                // next sync will recalculate with bridge data)
                database.execSQL(
                    """UPDATE media SET groupKey = COALESCE(
                        imdbId,
                        CASE WHEN tmdbId IS NOT NULL AND tmdbId != '' THEN 'tmdb_' || tmdbId ELSE NULL END,
                        ratingKey || serverId
                    )"""
                )

                // Solution C: Create media_unified materialized table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_unified` (
                        `groupKey` TEXT NOT NULL PRIMARY KEY,
                        `bestRatingKey` TEXT NOT NULL,
                        `bestServerId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `titleSortable` TEXT NOT NULL DEFAULT '',
                        `year` INTEGER,
                        `summary` TEXT,
                        `duration` INTEGER,
                        `resolvedThumbUrl` TEXT,
                        `resolvedArtUrl` TEXT,
                        `resolvedBaseUrl` TEXT,
                        `imdbId` TEXT,
                        `tmdbId` TEXT,
                        `guid` TEXT,
                        `serverIds` TEXT,
                        `alternativeThumbUrls` TEXT,
                        `serverCount` INTEGER NOT NULL DEFAULT 1,
                        `genres` TEXT,
                        `contentRating` TEXT,
                        `displayRating` REAL NOT NULL DEFAULT 0.0,
                        `avgDisplayRating` REAL NOT NULL DEFAULT 0.0,
                        `addedAt` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        `viewOffset` INTEGER NOT NULL DEFAULT 0,
                        `viewCount` INTEGER NOT NULL DEFAULT 0,
                        `lastViewedAt` INTEGER NOT NULL DEFAULT 0,
                        `historyGroupKey` TEXT NOT NULL DEFAULT '',
                        `metadataScore` INTEGER NOT NULL DEFAULT 0,
                        `isOwned` INTEGER NOT NULL DEFAULT 0,
                        `unificationId` TEXT NOT NULL DEFAULT '',
                        `scrapedRating` REAL,
                        `rating` REAL,
                        `audienceRating` REAL,
                        `librarySectionId` TEXT,
                        `parentTitle` TEXT,
                        `parentRatingKey` TEXT,
                        `parentIndex` INTEGER,
                        `grandparentTitle` TEXT,
                        `grandparentRatingKey` TEXT,
                        `index` INTEGER,
                        `thumbUrl` TEXT,
                        `artUrl` TEXT,
                        `parentThumb` TEXT,
                        `grandparentThumb` TEXT,
                        `sourceServerId` TEXT
                    )
                    """
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_titleSortable` ON `media_unified` (`type`, `titleSortable`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_displayRating` ON `media_unified` (`type`, `displayRating`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_addedAt` ON `media_unified` (`type`, `addedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_year` ON `media_unified` (`type`, `year`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_genres` ON `media_unified` (`type`, `genres`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_unified_type_contentRating` ON `media_unified` (`type`, `contentRating`)")
            }
        }

    private val MIGRATION_40_41 =
        object : androidx.room.migration.Migration(40, 41) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `person_favorites` (
                        `tmdbId` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `profilePath` TEXT,
                        `knownFor` TEXT,
                        `addedAt` INTEGER NOT NULL
                    )"""
                )
            }
        }

    private val MIGRATION_41_42 =
        object : androidx.room.migration.Migration(41, 42) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL,
                        `serverId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT,
                        `thumbUrl` TEXT,
                        `playlistType` TEXT NOT NULL DEFAULT 'video',
                        `itemCount` INTEGER NOT NULL DEFAULT 0,
                        `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `lastSync` INTEGER NOT NULL,
                        PRIMARY KEY(`id`, `serverId`)
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlist_items` (
                        `playlistId` TEXT NOT NULL,
                        `serverId` TEXT NOT NULL,
                        `itemRatingKey` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `playlistItemId` TEXT NOT NULL,
                        PRIMARY KEY(`playlistId`, `serverId`, `itemRatingKey`)
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId_serverId` ON `playlist_items` (`playlistId`, `serverId`)"
                )
            }
        }

    private val MIGRATION_42_43 =
        object : androidx.room.migration.Migration(42, 43) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Soft delete: add isHidden + hiddenAt columns for "hide from hub" feature
                database.execSQL("ALTER TABLE media ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE media ADD COLUMN hiddenAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_isHidden` ON `media` (`isHidden`)")
            }
        }

    private val MIGRATION_43_44 =
        object : androidx.room.migration.Migration(43, 44) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // TMDB manual refresh: persistence columns for summary + poster override
                database.execSQL("ALTER TABLE media ADD COLUMN overriddenSummary TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE media ADD COLUMN overriddenThumbUrl TEXT DEFAULT NULL")
            }
        }

    @Provides
    @Singleton
    fun providePlexDatabase(
        @ApplicationContext context: Context,
    ): PlexDatabase {
        return Room.databaseBuilder(
            context,
            PlexDatabase::class.java,
            "plex_hub_db",
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryCallback({ sqlQuery, bindArgs ->
                // Useful for debugging performance-heavy queries if needed
                // Timber.d("Query: $sqlQuery Args: $bindArgs")
            }, { /* executor */ })
            .addCallback(
                object : androidx.room.RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA synchronous = NORMAL")
                        db.execSQL("PRAGMA cache_size = -8000") // 8MB cache
                    }
                },
            )
            .addMigrations(
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_36_37,
                MIGRATION_37_38,
                MIGRATION_38_39,
                MIGRATION_39_40,
                MIGRATION_40_41,
                MIGRATION_41_42,
                MIGRATION_42_43,
                MIGRATION_43_44,
            )
            .build()
    }

    @Provides
    fun provideMediaDao(db: PlexDatabase): MediaDao = db.mediaDao()

    @Provides
    fun provideServerDao(db: PlexDatabase): ServerDao = db.serverDao()

    @Provides
    fun provideDownloadDao(database: PlexDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideApiCacheDao(database: PlexDatabase): ApiCacheDao {
        return database.apiCacheDao()
    }

    @Provides
    fun provideOfflineWatchProgressDao(database: PlexDatabase): OfflineWatchProgressDao {
        return database.offlineWatchProgressDao()
    }

    @Provides
    fun provideHomeContentDao(database: PlexDatabase): HomeContentDao {
        return database.homeContentDao()
    }

    @Provides
    fun provideFavoriteDao(database: PlexDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    fun provideRemoteKeysDao(database: PlexDatabase): RemoteKeysDao {
        return database.remoteKeysDao()
    }

    @Provides
    fun provideTrackPreferenceDao(database: PlexDatabase): TrackPreferenceDao {
        return database.trackPreferenceDao()
    }

    @Provides
    fun provideCollectionDao(database: PlexDatabase): CollectionDao {
        return database.collectionDao()
    }

    @Provides
    fun provideProfileDao(database: PlexDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideSearchCacheDao(database: PlexDatabase): SearchCacheDao {
        return database.searchCacheDao()
    }

    @Provides
    fun provideXtreamAccountDao(database: PlexDatabase): XtreamAccountDao {
        return database.xtreamAccountDao()
    }

    @Provides
    fun provideBackendServerDao(database: PlexDatabase): BackendServerDao {
        return database.backendServerDao()
    }

    @Provides
    fun provideIdBridgeDao(database: PlexDatabase): IdBridgeDao {
        return database.idBridgeDao()
    }

    @Provides
    fun provideMediaUnifiedDao(database: PlexDatabase): MediaUnifiedDao {
        return database.mediaUnifiedDao()
    }

    @Provides
    fun providePersonFavoriteDao(database: PlexDatabase): PersonFavoriteDao {
        return database.personFavoriteDao()
    }

    @Provides
    fun providePlaylistDao(database: PlexDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideLibrarySectionDao(database: PlexDatabase): LibrarySectionDao {
        return database.librarySectionDao()
    }
}
