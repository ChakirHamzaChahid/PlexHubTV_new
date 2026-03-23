package com.chakir.plexhubtv.di.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.size.Precision
import com.chakir.plexhubtv.BuildConfig
import com.chakir.plexhubtv.core.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        performanceImageInterceptor: PerformanceImageInterceptor,
        jellyfinImageInterceptor: JellyfinImageInterceptor,
    ): ImageLoader {
        // Dedicated OkHttpClient for images:
        // - Remove Plex AuthInterceptor (tokens already embedded in Plex URLs)
        // - Add JellyfinImageInterceptor (adds Authorization header for Jellyfin URLs)
        // - Shorter timeouts to prevent blocking on slow/offline servers
        val builder = okHttpClient.newBuilder()
        builder.interceptors().removeAll { it is AuthInterceptor }
        val imageOkHttpClient = builder
            .addInterceptor(jellyfinImageInterceptor)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)  // 5s: allows local Jellyfin servers time to respond
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)    // 10s instead of 30s
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)   // 10s instead of 30s
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)    // 15s total max (prevents 60s hangs)
            .retryOnConnectionFailure(false) // Don't retry — FallbackAsyncImage handles URL-level fallback
            .connectionPool(okhttp3.ConnectionPool(4, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build()

        // ✅ ADAPTIVE CACHE: Use JVM heap limit (not system RAM) to avoid exceeding heap on low-RAM devices.
        // System RAM can be 2GB while heap limit is only 192MB — sizing cache as % of system RAM
        // could configure a 200MB cache that exceeds the heap, leading to OOM under load.
        val maxHeap = Runtime.getRuntime().maxMemory()
        val memoryCacheSize = (maxHeap * 0.20).toLong() // 20% of heap
            .coerceIn(32 * 1024 * 1024L, 256 * 1024 * 1024L) // Min 32 MB, Max 256 MB

        Timber.i(
            "ImageCache: Heap limit = %.0f MB, Cache = %.1f MB (%.0f%%)",
            maxHeap / (1024.0 * 1024.0),
            memoryCacheSize / (1024.0 * 1024.0),
            (memoryCacheSize.toDouble() / maxHeap) * 100
        )

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                add(PlexImageKeyer())
                if (BuildConfig.DEBUG) {
                    add(performanceImageInterceptor)
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(memoryCacheSize) // ✅ Adaptive cache size
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB disk (3.2% of 8GB Mi Box S storage)
                    .build()
            }
            .precision(Precision.INEXACT)
            .build()
    }
}
