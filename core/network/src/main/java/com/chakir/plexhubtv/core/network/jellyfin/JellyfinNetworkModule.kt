package com.chakir.plexhubtv.core.network.jellyfin

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for Jellyfin network layer.
 *
 * Creates a dedicated OkHttpClient derived from the default (self-signed-aware)
 * client with extended timeouts — Jellyfin servers with large libraries (10K+ items)
 * can take 60-90s to respond to paginated item queries.
 * Auth is passed per-request via @Header, not via interceptor.
 */
@Module
@InstallIn(SingletonComponent::class)
object JellyfinNetworkModule {

    @Provides
    @Singleton
    @Named("jellyfin")
    fun provideJellyfinRetrofit(
        okHttpClient: OkHttpClient, // default: accepts self-signed certs on LAN
        gson: Gson,
    ): Retrofit {
        // Derive a new client with extended timeouts for large library syncs.
        // newBuilder() inherits SSL config, connection pool, etc.
        val jellyfinClient = okHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

        // Base URL is a placeholder — every call uses @Url with the full server URL
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(jellyfinClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinApiService(
        @Named("jellyfin") retrofit: Retrofit,
    ): JellyfinApiService {
        return retrofit.create(JellyfinApiService::class.java)
    }
}
