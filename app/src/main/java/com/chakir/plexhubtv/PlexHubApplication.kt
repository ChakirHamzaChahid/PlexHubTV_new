package com.chakir.plexhubtv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import android.util.Log

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
    @Inject lateinit var imageLoader: ImageLoader

    // AJOUTER CES LIGNES :
    private val _appReady = MutableStateFlow(false)
    val appReady: StateFlow<Boolean> = _appReady.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        
        // Launch parallel initialization
        initializeAppInParallel()
        
        setupBackgroundSync()
    }

    /**
     * Initialize app services in parallel for faster cold start.
     * Inspired by Plezy's Future.wait() pattern.
     */
    private fun initializeAppInParallel() {
        CoroutineScope(Dispatchers.Default).launch {
            val startTime = System.currentTimeMillis()
            Log.d("PlexHubApp", "Starting parallel initialization...")
            
            try {
                val jobs = listOf(
                    // Job 1: Warm up Settings DataStore
                    async(Dispatchers.IO) {
                        try {
                            Log.d("PlexHubApp", "Init: Loading settings...")
                            settingsDataStore.isFirstSyncComplete.first()
                            Log.d("PlexHubApp", "Init: Settings loaded")
                        } catch (e: Exception) {
                            Log.e("PlexHubApp", "Init: Settings failed - ${e.message}")
                        }
                    },
                    
                    // Job 2: Pre-warm ImageLoader (triggers Coil initialization)
                    async(Dispatchers.Default) {
                        try {
                            Log.d("PlexHubApp", "Init: Warming image cache...")
                            // Access imageLoader to trigger Coil setup
                            imageLoader.memoryCache
                            imageLoader.diskCache
                            Log.d("PlexHubApp", "Init: Image cache ready")
                        } catch (e: Exception) {
                            Log.e("PlexHubApp", "Init: Image cache failed - ${e.message}")
                        }
                    },
                    
                    // Job 3: Pre-warm WorkManager (ensure ready for sync)
                    async(Dispatchers.Default) {
                        try {
                            Log.d("PlexHubApp", "Init: Warming WorkManager...")
                            // Access workerFactory to ensure DI ready
                            workerFactory
                            delay(50)  // Small delay to ensure init complete
                            Log.d("PlexHubApp", "Init: WorkManager ready")
                        } catch (e: Exception) {
                            Log.e("PlexHubApp", "Init: WorkManager failed - ${e.message}")
                        }
                    },
                    
                    // Job 4: Pre-warm OkHttpClient connection pool
                    async(Dispatchers.IO) {
                        try {
                            Log.d("PlexHubApp", "Init: Warming network stack...")
                            // Access okHttpClient to trigger connection pool setup
                            okHttpClient.connectionPool
                            Log.d("PlexHubApp", "Init: Network ready")
                        } catch (e: Exception) {
                            Log.e("PlexHubApp", "Init: Network failed - ${e.message}")
                        }
                    }
                )
                
                // Wait for ALL jobs to complete
                jobs.awaitAll()
                
                val duration = System.currentTimeMillis() - startTime
                Log.i("PlexHubApp", "✅ Parallel init complete in ${duration}ms")
                
                // Signal that app is ready
                _appReady.value = true
                
            } catch (e: Exception) {
                Log.e("PlexHubApp", "Parallel init failed: ${e.message}")
                // Set ready anyway to not block app
                _appReady.value = true
            }
        }
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
        // Add delay to prevent conflict with Initial Sync on startup
        val syncRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(20, TimeUnit.MINUTES)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LibrarySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // 3. Schedule Collection Sync (Every 6 hours)
        val collectionSyncRequest = PeriodicWorkRequestBuilder<com.chakir.plexhubtv.work.CollectionSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CollectionSync",
            ExistingPeriodicWorkPolicy.KEEP,
            collectionSyncRequest
        )
    }
    
    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}
