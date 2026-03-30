package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.data.repository.AggregationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Full rebuild of the `media_unified` table from the `media` table.
 * Chained after CollectionSyncWorker + RatingSyncWorker so the media table
 * is in its final state when the rebuild runs.
 *
 * Typically completes in 2-5 seconds for ~69K media rows → ~36K unified rows.
 */
@HiltWorker
class UnifiedRebuildWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val aggregationService: AggregationService,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.w("JELLYFIN_TRACE [UnifiedRebuildWorker] Starting full rebuild...")
            aggregationService.rebuildAll()
            Timber.w("JELLYFIN_TRACE [UnifiedRebuildWorker] Complete")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "JELLYFIN_TRACE [UnifiedRebuildWorker] FAILED: ${e.message}")
            Result.retry()
        }
    }
}
