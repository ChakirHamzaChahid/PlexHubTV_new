package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.database.ApiCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Daily worker that purges expired entries from the API cache table.
 * Prevents progressive bloat of the api_cache table on low-storage devices.
 */
@HiltWorker
class CachePurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiCacheDao: ApiCacheDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            apiCacheDao.purgeExpired(System.currentTimeMillis())
            Timber.d("CachePurge: expired entries removed")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "CachePurge: failed")
            Result.retry()
        }
    }
}
