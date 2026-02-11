package com.chakir.plexhubtv.di.image

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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .components {
                add(PlexImageKeyer())
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(200 * 1024 * 1024) // Fixed 200MB (Parity with Plezy)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024L * 1024 * 1024) // 1GB Cache for "Offline-like" failover
                    .build()
            }
            .allowHardware(true)
            .precision(coil.size.Precision.INEXACT)
            .respectCacheHeaders(false) // Force cache even if server says funky things
            .crossfade(false) // Instant pop-in, no fade (faster feel)
            .build()
    }
}
