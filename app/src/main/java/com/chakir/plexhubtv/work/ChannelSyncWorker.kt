package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.data.util.TvChannelManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Worker for periodic TV Channel synchronization.
 *
 * Runs every 3 hours to refresh the Continue Watching channel
 * with latest On Deck content. Complements immediate updates
 * after playback sessions.
 */
@HiltWorker
class ChannelSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tvChannelManager: TvChannelManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("TV Channel: Periodic sync started")

        // Check if feature is enabled
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Sync skipped (disabled in settings)")
            return Result.success()
        }

        return try {
            tvChannelManager.updateContinueWatching()
            Timber.i("TV Channel: Periodic sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Periodic sync failed")
            // Return success to avoid WorkManager retry spam
            Result.success()
        }
    }
}
