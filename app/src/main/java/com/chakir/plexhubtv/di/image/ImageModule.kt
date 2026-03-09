package com.chakir.plexhubtv.di.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.size.Precision
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import com.chakir.plexhubtv.BuildConfig
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
    ): ImageLoader {
        // Dedicated OkHttpClient for images with shorter timeouts to prevent blocking on slow/offline servers
        val imageOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)  // 5s instead of 10s
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)    // 10s instead of 30s
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)   // 10s instead of 30s
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)    // 15s total max (prevents 60s hangs)
            .retryOnConnectionFailure(false) // Don't retry — FallbackAsyncImage handles URL-level fallback
            .connectionPool(okhttp3.ConnectionPool(8, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build()

        // ✅ ADAPTIVE CACHE: Use JVM heap limit (not system RAM) to avoid exceeding heap on low-RAM devices.
        // System RAM can be 2GB while heap limit is only 192MB — sizing cache as % of system RAM
        // could configure a 200MB cache that exceeds the heap, leading to OOM under load.
        val maxHeap = Runtime.getRuntime().maxMemory()
        val memoryCacheSize = (maxHeap * 0.25).toLong() // 25% of heap — Coil's default heuristic
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
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB disk (reduced from 1GB)
                    .build()
            }
            .precision(Precision.INEXACT)
            .build()
    }
}
