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
        .addMigrations(MIGRATION_11_12)
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
}
