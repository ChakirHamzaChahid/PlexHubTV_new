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
import androidx.work.workDataOf

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepositoryImpl,
    private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    private val channelId = "library_sync"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Promote to Foreground to bypass 10-min limit
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Failed to set foreground: ${e.message}")
        }

        // Set up progress callback
        syncRepository.onProgressUpdate = { current, total, libraryName ->
            updateNotification("Syncing $libraryName: $current / $total")
            
            // Report progress to UI
            setProgressAsync(
                workDataOf(
                    "progress" to (if (total > 0) (current.toFloat() / total) * 100 else 0f),
                    "message" to "Syncing $libraryName ($current/$total)"
                )
            )
        }

        try {
            val serversResult = authRepository.getServers()
            val servers = serversResult.getOrNull() ?: return@withContext Result.failure()

            var failureCount = 0
            servers.forEach { server ->
                updateNotification("Syncing ${server.name}...")
                val syncResult = syncRepository.syncServer(server)
                if (syncResult.isFailure) {
                    failureCount++
                }
            }

            // Clear callback
            syncRepository.onProgressUpdate = null

            if (failureCount == servers.size && servers.isNotEmpty()) {
                Result.retry()
            } else {
                // MARK SYNC AS COMPLETE
                settingsDataStore.saveLastSyncTime(System.currentTimeMillis())
                settingsDataStore.saveFirstSyncComplete(true)
                Result.success()
            }
        } catch (e: Exception) {
            syncRepository.onProgressUpdate = null
            Result.failure()
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
