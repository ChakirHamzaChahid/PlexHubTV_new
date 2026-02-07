package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.data.repository.SyncRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.work.workDataOf

/**
 * Worker de synchronisation en arrière-plan des bibliothèques Plex.
 * 
 * Fonctionnalités :
 * - Récupère toutes les bibliothèques de tous les serveurs connectés.
 * - Stocke les métadonnées dans la base de données locale (Room).
 * - Synchronise également la Watchlist Plex (Cloud) vers les Favoris locaux.
 * - S'exécute en mode Foreground Service pour éviter les limitations Android (10 min).
 * - Notifie l'utilisateur de la progression via des notifications.
 * 
 * Planification :
 * - Synchronisation initiale : Au premier démarrage de l'app.
 * - Synchronisation périodique : Toutes les 6 heures (configuré dans PlexHubApplication).
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepositoryImpl,
    private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    private val syncWatchlistUseCase: com.chakir.plexhubtv.domain.usecase.SyncWatchlistUseCase
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    private val channelId = "library_sync"

    /**
     * Logique principale du Worker.
     * 
     * Étapes :
     * 1. Attend l'authentification (token + clientId).
     * 2. Passe en mode Foreground pour éviter les timeouts.
     * 3. Récupère la liste des serveurs.
     * 4. Synchronise chaque serveur (appel à SyncRepository).
     * 5. Synchronise la Watchlist Plex vers Favoris.
     * 6. Marque la synchronisation comme complète.
     * 
     * Gestion des erreurs : Retourne toujours Success pour éviter de bloquer l'app.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        android.util.Log.d("SyncWorker", "→ doWork() STARTED")
        
        try {
            // 1. Wait for valid token and client ID (Avoid race condition with checkAuthentication)
            android.util.Log.d("SyncWorker", "→ Waiting for authentication...")
            val token = withTimeoutOrNull(15000) { // Wait up to 15s
                settingsDataStore.plexToken.first { !it.isNullOrBlank() }
            }
            val clientId = withTimeoutOrNull(5000) {
                settingsDataStore.clientId.first { !it.isNullOrBlank() }
            }

            if (token == null || clientId == null) {
                android.util.Log.w("SyncWorker", "✗ Authentication timeout - Token: ${token != null}, ClientId: ${clientId != null}")
                // If we timeout, we might be on a fresh install waiting for bypass. 
                // Don't mark as complete yet, just return success and let periodic sync try later
                // OR return failure and let WorkManager retry
                return@withContext Result.success() 
            }
            
            android.util.Log.d("SyncWorker", "✓ Authenticated, proceeding with sync")

            // Promote to Foreground to bypass 10-min limit
            try {
                setForeground(getForegroundInfo())
                android.util.Log.d("SyncWorker", "✓ Foreground service set")
            } catch (e: Exception) {
                android.util.Log.e("SyncWorker", "✗ Failed to set foreground: ${e.message}", e)
            }

            // Set up progress callback
            var lastNotificationTime = 0L
            syncRepository.onProgressUpdate = { current, total, libraryName ->
                val now = System.currentTimeMillis()
                // Throttle: Update only if 1 second has passed OR if finished
                if (now - lastNotificationTime >= 1000 || current == total) {
                    lastNotificationTime = now
                    try {
                        updateNotification("Syncing $libraryName: $current / $total")
                    } catch (e: Exception) {
                        android.util.Log.e("SyncWorker", "Failed to update notification: ${e.message}")
                    }
                    
                    // Report progress to UI
                    try {
                        setProgressAsync(
                            workDataOf(
                                "progress" to (if (total > 0) (current.toFloat() / total) * 100 else 0f),
                                "message" to "Syncing $libraryName ($current/$total)"
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("SyncWorker", "Failed to set progress: ${e.message}")
                    }
                }
            }

            android.util.Log.d("SyncWorker", "→ Fetching servers...")
            val serversResult = authRepository.getServers()
            val servers = serversResult.getOrNull()
            
            if (servers == null || servers.isEmpty()) {
                android.util.Log.w("SyncWorker", "✗ No servers found")
                syncRepository.onProgressUpdate = null
                settingsDataStore.saveFirstSyncComplete(true)
                return@withContext Result.success()
            }
            
            android.util.Log.d("SyncWorker", "→ Syncing ${servers.size} server(s)")

            var failureCount = 0
            servers.forEach { server ->
                try {
                    updateNotification("Syncing ${server.name}...")
                    val syncResult = syncRepository.syncServer(server)
                    if (syncResult.isFailure) {
                        android.util.Log.w("SyncWorker", "✗ Failed to sync ${server.name}")
                        failureCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "✗ Exception syncing ${server.name}: ${e.message}")
                    failureCount++
                }
            }
            
            // AUTOMATE WATCHLIST SYNC
            try {
                updateNotification("Syncing Watchlist...")
                syncWatchlistUseCase()
            } catch (e: Exception) {
                android.util.Log.e("SyncWorker", "✗ Watchlist sync failed: ${e.message}")
            }

            // Clear callback
            syncRepository.onProgressUpdate = null

            if (failureCount == servers.size && servers.isNotEmpty()) {
                android.util.Log.w("SyncWorker", "All servers failed, marking complete anyway")
                settingsDataStore.saveFirstSyncComplete(true)
                Result.success()
            } else {
                // MARK SYNC AS COMPLETE
                settingsDataStore.saveLastSyncTime(System.currentTimeMillis())
                settingsDataStore.saveFirstSyncComplete(true)
                android.util.Log.i("SyncWorker", "✓ Sync complete! Failures: $failureCount/${servers.size}")
                
                // TRIGGER COLLECTION SYNC NOW THAT WE HAVE DATA
                try {
                    android.util.Log.d("SyncWorker", "→ Triggering Collection Sync after successful library sync")
                    val collectionSyncRequest = androidx.work.OneTimeWorkRequestBuilder<CollectionSyncWorker>()
                        .build()
                    androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "CollectionSync_Initial",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        collectionSyncRequest
                    )
                } catch (e: Exception) {
                    android.util.Log.e("SyncWorker", "Failed to trigger collection sync: ${e.message}")
                }
                
                Result.success()
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "═══════════════════════════════")
            android.util.Log.e("SyncWorker", "SYNC FAILED - Exception Details:")
            android.util.Log.e("SyncWorker", "Type: ${e.javaClass.name}")
            android.util.Log.e("SyncWorker", "Message: ${e.message}")
            android.util.Log.e("SyncWorker", "Stack trace:", e)
            android.util.Log.e("SyncWorker", "═══════════════════════════════")
            
            syncRepository.onProgressUpdate = null
            
            // Mark as complete to avoid blocking the app
            try {
                settingsDataStore.saveFirstSyncComplete(true)
                android.util.Log.w("SyncWorker", "Marked sync as complete despite failure to unblock app")
            } catch (ex: Exception) {
                android.util.Log.e("SyncWorker", "Failed to mark sync complete: ${ex.message}")
            }
            
            Result.success() // Changed from failure to success to avoid blocking
        }
    }

    private fun updateNotification(text: String) {
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("PlexHubTV")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress
            .build()
        notificationManager.notify(1, notification)
    }

    /**
     * Crée les informations pour le service Foreground.
     * Affiche une notification permanente pendant la synchronisation.
     */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Synchronisation de la Bibliothèque",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("PlexHubTV")
            .setContentText("Synchronisation des médias en cours...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(1, notification)
        }
    }
}
