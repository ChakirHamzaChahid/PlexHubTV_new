package com.chakir.plexhubtv.data.di

import com.chakir.plexhubtv.core.network.ApiCache
import com.chakir.plexhubtv.data.cache.RoomApiCache
import com.chakir.plexhubtv.data.repository.AuthRepositoryImpl
import com.chakir.plexhubtv.data.repository.DownloadsRepositoryImpl
import com.chakir.plexhubtv.data.repository.LibraryRepositoryImpl
import com.chakir.plexhubtv.data.repository.MediaRepositoryImpl
import com.chakir.plexhubtv.data.repository.OfflineWatchSyncRepositoryImpl
import com.chakir.plexhubtv.data.repository.ProfileRepositoryImpl
import com.chakir.plexhubtv.data.repository.SearchRepositoryImpl
import com.chakir.plexhubtv.data.repository.SettingsRepositoryImpl
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.DownloadsRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.OfflineWatchSyncRepository
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module Dagger Hilt pour l'injection des Repositories.
 * Lie les interfaces du domaine (ex: [AuthRepository]) à leurs implémentations concrètes (ex: [AuthRepositoryImpl]).
 * Scope: Singleton (Une seule instance pour toute l'application).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindDownloadsRepository(impl: DownloadsRepositoryImpl): DownloadsRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: com.chakir.plexhubtv.data.repository.AccountRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.AccountRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        impl: com.chakir.plexhubtv.data.repository.SyncRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.SyncRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(
        impl: com.chakir.plexhubtv.data.repository.WatchlistRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.WatchlistRepository

    @Binds
    @Singleton
    abstract fun bindIptvRepository(
        impl: com.chakir.plexhubtv.data.repository.IptvRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.IptvRepository

    @Binds
    @Singleton
    abstract fun bindMediaUrlResolver(
        impl: com.chakir.plexhubtv.core.util.DefaultMediaUrlResolver,
    ): com.chakir.plexhubtv.core.util.MediaUrlResolver

    @Binds
    @Singleton
    abstract fun bindMediaDeduplicator(
        impl: com.chakir.plexhubtv.data.repository.aggregation.DefaultMediaDeduplicator,
    ): com.chakir.plexhubtv.data.repository.aggregation.MediaDeduplicator

    @Binds
    @Singleton
    abstract fun bindOnDeckRepository(
        impl: com.chakir.plexhubtv.data.repository.OnDeckRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.OnDeckRepository

    @Binds
    @Singleton
    abstract fun bindHubsRepository(
        impl: com.chakir.plexhubtv.data.repository.HubsRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.HubsRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: com.chakir.plexhubtv.data.repository.FavoritesRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindMediaDetailRepository(
        impl: com.chakir.plexhubtv.data.repository.MediaDetailRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.MediaDetailRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        impl: com.chakir.plexhubtv.data.repository.PlaybackRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindOfflineWatchSyncRepository(impl: OfflineWatchSyncRepositoryImpl): OfflineWatchSyncRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindApiCache(impl: RoomApiCache): ApiCache

    @Binds
    @Singleton
    abstract fun bindTrackPreferenceRepository(
        impl: com.chakir.plexhubtv.data.repository.TrackPreferenceRepositoryImpl,
    ): com.chakir.plexhubtv.domain.repository.TrackPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindResolveEpisodeSourcesUseCase(
        impl: com.chakir.plexhubtv.data.usecase.ResolveEpisodeSourcesUseCaseImpl,
    ): com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase
}
