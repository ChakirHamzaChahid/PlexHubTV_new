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

    private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            // PERFORMANCE FIX: Add missing indexes (Issue #2 & #3)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_guid` ON `media` (`guid`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_type_addedAt` ON `media` (`type`, `addedAt`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_imdbId` ON `media` (`imdbId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_tmdbId` ON `media` (`tmdbId`)")
        }
    }

    private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
             database.execSQL("CREATE TABLE IF NOT EXISTS `track_preferences` (`ratingKey` TEXT NOT NULL, `serverId` TEXT NOT NULL, `audioStreamId` TEXT, `subtitleStreamId` TEXT, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`ratingKey`, `serverId`))")
        }
    }

    private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
             // 1. Create new table with composite PK (id, serverId)
             database.execSQL("""
                CREATE TABLE IF NOT EXISTS `collections_new` (
                    `id` TEXT NOT NULL, 
                    `serverId` TEXT NOT NULL, 
                    `title` TEXT NOT NULL, 
                    `summary` TEXT, 
                    `thumbUrl` TEXT, 
                    `lastSync` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`, `serverId`)
                )
             """)

             // 2. Copy data from old table
             // Note: If serverId column didn't exist in v18, this would fail. 
             // We assume it existed but wasn't part of PK.
             database.execSQL("""
                INSERT INTO `collections_new` (`id`, `serverId`, `title`, `summary`, `thumbUrl`, `lastSync`)
                SELECT `id`, `serverId`, `title`, `summary`, `thumbUrl`, `lastSync` FROM `collections`
             """)

             // 3. Drop old table
             database.execSQL("DROP TABLE `collections`")

             // 4. Rename new table
             database.execSQL("ALTER TABLE `collections_new` RENAME TO `collections`")
        }
    }

    @Provides
    @Singleton
    fun providePlexDatabase(
        @ApplicationContext context: Context
    ): PlexDatabase {
        return Room.databaseBuilder(
            context,
            PlexDatabase::class.java,
            "plex_hub_db"
        )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryCallback({ sqlQuery, bindArgs ->
            // Useful for debugging performance-heavy queries if needed
            // android.util.Log.d("SQL_QUERY", "Query: $sqlQuery Args: $bindArgs")
        }, { /* executor */ })
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA synchronous = NORMAL")
                db.execSQL("PRAGMA cache_size = -8000") // 8MB cache
            }
        })
        .addMigrations(MIGRATION_11_12, MIGRATION_15_16, MIGRATION_18_19)
        .fallbackToDestructiveMigration()
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
}
