package com.chakir.plexhubtv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.work.LibrarySyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val _appReady = MutableStateFlow(false)
    val appReady: StateFlow<Boolean> = _appReady.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Launch parallel initialization
        initializeAppInParallel()

        setupBackgroundSync()
    }

    /**
     * Initialize app services in parallel for faster cold start.
     * Inspired by Plezy's Future.wait() pattern.
     */
    private fun initializeAppInParallel() {
        appScope.launch {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting parallel initialization...")

            try {
                val jobs =
                    listOf(
                        // Job 1: Warm up Settings DataStore
                        async(Dispatchers.IO) {
                            try {
                                Timber.d("Init: Loading settings...")
                                settingsDataStore.isFirstSyncComplete.first()
                                Timber.d("Init: Settings loaded")
                            } catch (e: Exception) {
                                Timber.e(e, "Init: Settings failed")
                            }
                        },
                        // Job 2: Pre-warm ImageLoader (triggers Coil initialization)
                        async(Dispatchers.Default) {
                            try {
                                Timber.d("Init: Warming image cache...")
                                // Access imageLoader to trigger Coil setup
                                imageLoader.memoryCache
                                imageLoader.diskCache
                                Timber.d("Init: Image cache ready")
                            } catch (e: Exception) {
                                Timber.e(e, "Init: Image cache failed")
                            }
                        },
                        // Job 3: Pre-warm WorkManager (ensure ready for sync)
                        async(Dispatchers.Default) {
                            try {
                                Timber.d("Init: Warming WorkManager...")
                                // Access workerFactory to ensure DI ready
                                workerFactory
                                delay(50) // Small delay to ensure init complete
                                Timber.d("Init: WorkManager ready")
                            } catch (e: Exception) {
                                Timber.e(e, "Init: WorkManager failed")
                            }
                        },
                        // Job 4: Pre-warm OkHttpClient connection pool
                        async(Dispatchers.IO) {
                            try {
                                Timber.d("Init: Warming network stack...")
                                // Access okHttpClient to trigger connection pool setup
                                okHttpClient.connectionPool
                                Timber.d("Init: Network ready")
                            } catch (e: Exception) {
                                Timber.e(e, "Init: Network failed")
                            }
                        },
                    )

                // Wait for ALL jobs to complete
                jobs.awaitAll()

                val duration = System.currentTimeMillis() - startTime
                Timber.i("✅ Parallel init complete in ${duration}ms")

                // Signal that app is ready
                _appReady.value = true
            } catch (e: Exception) {
                Timber.e(e, "Parallel init failed")
                // Set ready anyway to not block app
                _appReady.value = true
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
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
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        // 1. Trigger Immediate Sync ONLY if never completed (Critical for Fresh Install)
        appScope.launch(ioDispatcher) {
            val isFirstSyncComplete = settingsDataStore.isFirstSyncComplete.first()
            if (!isFirstSyncComplete) {
                val immediateSyncRequest =
                    androidx.work.OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                        .setConstraints(constraints)
                        .build()

                WorkManager.getInstance(this@PlexHubApplication).enqueueUniqueWork(
                    "LibrarySync_Initial",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    immediateSyncRequest,
                )
            }
        }

        // 2. Schedule Periodic Sync (Every 6 hours)
        // Add delay to prevent conflict with Initial Sync on startup
        val syncRequest =
            PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LibrarySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )

        // 3. Schedule Collection Sync (Every 6 hours)
        val collectionSyncRequest =
            PeriodicWorkRequestBuilder<com.chakir.plexhubtv.work.CollectionSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CollectionSync",
            ExistingPeriodicWorkPolicy.KEEP,
            collectionSyncRequest,
        )
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader
    }
}
