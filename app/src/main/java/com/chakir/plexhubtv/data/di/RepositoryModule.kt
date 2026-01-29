package com.chakir.plexhubtv.data.di

import com.chakir.plexhubtv.data.repository.AuthRepositoryImpl
import com.chakir.plexhubtv.data.repository.DownloadsRepositoryImpl
import com.chakir.plexhubtv.data.repository.LibraryRepositoryImpl
import com.chakir.plexhubtv.data.repository.MediaRepositoryImpl
import com.chakir.plexhubtv.data.repository.SearchRepositoryImpl
import com.chakir.plexhubtv.data.repository.SettingsRepositoryImpl
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.DownloadsRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import com.chakir.plexhubtv.domain.repository.MediaRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindDownloadsRepository(
        impl: DownloadsRepositoryImpl
    ): DownloadsRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: com.chakir.plexhubtv.data.repository.AccountRepositoryImpl
    ): com.chakir.plexhubtv.domain.repository.AccountRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        impl: com.chakir.plexhubtv.data.repository.SyncRepositoryImpl
    ): com.chakir.plexhubtv.domain.repository.SyncRepository
}
