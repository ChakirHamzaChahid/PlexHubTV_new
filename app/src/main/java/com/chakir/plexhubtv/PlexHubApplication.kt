package com.chakir.plexhubtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.chakir.plexhubtv.work.LibrarySyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.chakir.plexhubtv.core.datastore.SettingsDataStore

/**
 * Classe Application personnalisée pour PlexHubTV.
 * 
 * Responsabilités :
 * - Configuration Hilt (Injection de dépendances)
 * - Configuration Coil (Chargement d'images avec cache optimisé pour TV)
 * - Configuration WorkManager (Workers Hilt-compatibles)
 * - Lancement de la synchronisation périodique en arrière-plan
 */
@HiltAndroidApp
class PlexHubApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate() {
        super.onCreate()
        setupBackgroundSync()
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    /**
     * Configure la synchronisation périodique des bibliothèques.
     * 
     * Stratégie :
     * 1. Synchronisation initiale immédiate (OneTime) si jamais effectuée.
     * 2. Synchronisation périodique toutes les 6 heures (PeriodicWork).
     * 
     * Contraintes : Nécessite une connexion réseau active.
     */
    private fun setupBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 1. Trigger Immediate Sync ONLY if never completed (Critical for Fresh Install)
        CoroutineScope(Dispatchers.IO).launch {
            val isFirstSyncComplete = settingsDataStore.isFirstSyncComplete.first()
            if (!isFirstSyncComplete) {
                val immediateSyncRequest = androidx.work.OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                    .setConstraints(constraints)
                    .build()
                    
                WorkManager.getInstance(this@PlexHubApplication).enqueueUniqueWork(
                    "LibrarySync_Initial",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    immediateSyncRequest
                )
            }
        }

        // 2. Schedule Periodic Sync (Every 6 hours)
        val syncRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LibrarySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient) // Use Hilt-provided authenticated/trusting client
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Reduced from 0.25 for TV devices (2-4GB RAM)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10) // Use 10% of free disk
                    .build()
            }
            .allowHardware(true) // Critical for TV RAM
            .precision(coil.size.Precision.INEXACT) // Allow inexact scaling to avoid load failures
            .respectCacheHeaders(false) // Cache images regardless of server headers (Plex sometimes sends no-cache)
            .crossfade(true)
            .build()
    }
}
