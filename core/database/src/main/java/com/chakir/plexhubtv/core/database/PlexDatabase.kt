package com.chakir.plexhubtv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Définition principale de la base de données Room.
 * Version 40 : Solution C — media_unified materialized table + groupKey column on media
 */
@TypeConverters(Converters::class)
@Database(
    entities = [

        MediaEntity::class,
        ServerEntity::class,
        DownloadEntity::class,
        ApiCacheEntity::class,
        OfflineWatchProgressEntity::class,
        HomeContentEntity::class,
        FavoriteEntity::class,
        RemoteKey::class,
        LibrarySectionEntity::class,
        TrackPreferenceEntity::class,
        CollectionEntity::class,
        MediaCollectionCrossRef::class,
        ProfileEntity::class,
        SearchCacheEntity::class,
        MediaFts::class,
        XtreamAccountEntity::class,
        BackendServerEntity::class,
        IdBridgeEntity::class,
        MediaUnifiedEntity::class,
        PersonFavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 44,
    exportSchema = true,
)
abstract class PlexDatabase : RoomDatabase() {
    // ... DAOs ...

    abstract fun mediaDao(): MediaDao

    abstract fun serverDao(): ServerDao

    abstract fun downloadDao(): DownloadDao

    abstract fun apiCacheDao(): ApiCacheDao

    abstract fun offlineWatchProgressDao(): OfflineWatchProgressDao

    abstract fun homeContentDao(): HomeContentDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun remoteKeysDao(): RemoteKeysDao

    abstract fun librarySectionDao(): LibrarySectionDao

    abstract fun trackPreferenceDao(): TrackPreferenceDao

    abstract fun collectionDao(): CollectionDao

    abstract fun profileDao(): ProfileDao

    abstract fun searchCacheDao(): SearchCacheDao

    abstract fun xtreamAccountDao(): XtreamAccountDao

    abstract fun backendServerDao(): BackendServerDao

    abstract fun idBridgeDao(): IdBridgeDao

    abstract fun mediaUnifiedDao(): MediaUnifiedDao

    abstract fun personFavoriteDao(): PersonFavoriteDao

    abstract fun playlistDao(): PlaylistDao
}
