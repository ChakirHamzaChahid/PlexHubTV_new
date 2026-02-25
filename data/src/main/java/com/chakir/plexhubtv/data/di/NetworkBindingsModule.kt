package com.chakir.plexhubtv.data.di

import com.chakir.plexhubtv.core.network.ApiKeyProvider
import com.chakir.plexhubtv.core.network.ConnectionCacheStore
import com.chakir.plexhubtv.core.network.auth.AuthTokenProvider
import com.chakir.plexhubtv.data.network.DataStoreApiKeyProvider
import com.chakir.plexhubtv.data.network.DataStoreAuthTokenProvider
import com.chakir.plexhubtv.data.network.DataStoreConnectionCacheStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds DataStore-backed implementations to core:network interfaces.
 * This inverts the dependency: core:network defines interfaces,
 * :data provides implementations backed by core:datastore.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(
        impl: DataStoreAuthTokenProvider,
    ): AuthTokenProvider

    @Binds
    @Singleton
    abstract fun bindConnectionCacheStore(
        impl: DataStoreConnectionCacheStore,
    ): ConnectionCacheStore

    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(
        impl: DataStoreApiKeyProvider,
    ): ApiKeyProvider
}
