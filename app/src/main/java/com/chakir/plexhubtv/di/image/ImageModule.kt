package com.chakir.plexhubtv.di.image

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import timber.log.Timber
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
            .retryOnConnectionFailure(false) // Don't retry failed connections for images
            .build()

        // ✅ ADAPTIVE CACHE: Calculate optimal cache size based on available RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val memoryCacheSize = when {
            totalRam < 2_000_000_000 -> (totalRam * 0.10).toLong() // 1-2 GB RAM: 10%
            totalRam < 4_000_000_000 -> (totalRam * 0.12).toLong() // 2-4 GB RAM: 12%
            else -> (totalRam * 0.15).toLong()                      // 4+ GB RAM: 15%
        }.coerceIn(50 * 1024 * 1024L, 400 * 1024 * 1024L) // Min 50 MB, Max 400 MB

        Timber.i(
            "ImageCache: Total RAM = %.2f GB, Cache = %.1f MB (%.1f%%)",
            totalRam / (1024.0 * 1024.0 * 1024.0),
            memoryCacheSize / (1024.0 * 1024.0),
            (memoryCacheSize.toDouble() / totalRam) * 100
        )

        return ImageLoader.Builder(context)
            .okHttpClient(imageOkHttpClient) // Use dedicated image client with shorter timeouts
            .components {
                add(PlexImageKeyer())
                add(performanceImageInterceptor)
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memoryCacheSize.toInt()) // ✅ Adaptive cache size
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB disk (reduced from 1GB)
                    .build()
            }
            .allowHardware(true)
            .precision(coil.size.Precision.INEXACT)
            .respectCacheHeaders(false) // Force cache even if server says funky things
            .crossfade(false) // Instant pop-in, no fade (faster feel)
            .build()
    }
}
