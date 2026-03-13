package com.chakir.plexhubtv.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.R
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.MediaUnifiedDao
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.core.network.ApiKeyManager
import com.chakir.plexhubtv.core.network.OmdbApiService
import com.chakir.plexhubtv.core.network.TmdbApiService
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Background worker for syncing media ratings from TMDb and OMDb APIs.
 *
 * **Configuration-Driven Strategy**:
 * - Series: Always use TMDb (primary source)
 * - Movies: User-configurable (TMDb or OMDb via [SettingsRepository.ratingSyncSource])
 * - Delay between requests: Configurable (default 250ms)
 * - Batching: Optional multi-day sync with progress tracking
 *
 * **Batching Mode**:
 * When enabled, limits daily requests and saves progress to resume next day.
 * - Daily limit: Configurable (default 900/day)
 * - Progress tracked separately for series and movies
 * - Resets progress when changing configuration or on manual reset
 */
@HiltWorker
class RatingSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val mediaDao: MediaDao,
        private val mediaUnifiedDao: MediaUnifiedDao,
        private val database: PlexDatabase,
        private val tmdbApiService: TmdbApiService,
        private val omdbApiService: OmdbApiService,
        private val apiKeyManager: ApiKeyManager,
        private val settingsRepository: SettingsRepository,
    ) : CoroutineWorker(context, params) {

        companion object {
            /** Batch size for DB updates. Each batch = 1 InvalidationTracker notification. */
            private const val UPDATE_BATCH_SIZE = 50
            private const val NOTIFICATION_ID = 2 // Different from LibrarySyncWorker (1)
            private const val CHANNEL_ID = "rating_sync"
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private data class RatingUpdate(val type: String, val id: String, val rating: Double)
        private val pendingUpdates = mutableListOf<RatingUpdate>()

        private suspend fun queueUpdate(type: String, id: String, rating: Double): Int {
            pendingUpdates.add(RatingUpdate(type, id, rating))
            return if (pendingUpdates.size >= UPDATE_BATCH_SIZE) flushUpdates() else 0
        }

        /** Flush pending updates in a single Room transaction (1 invalidation per batch). */
        private suspend fun flushUpdates(): Int {
            if (pendingUpdates.isEmpty()) return 0
            val batch = pendingUpdates.toList()
            pendingUpdates.clear()
            Timber.d("WORKER [RatingSync] Flushing batch of ${batch.size} rating updates to DB")
            var updated = 0
            database.withTransaction {
                batch.forEach { u ->
                    updated += when (u.type) {
                        "tmdb" -> mediaDao.updateRatingByTmdbId(u.id, u.rating)
                        else -> mediaDao.updateRatingByImdbId(u.id, u.rating)
                    }
                }
                // Surgical update: mirror ratings into media_unified
                batch.forEach { u ->
                    when (u.type) {
                        "tmdb" -> mediaUnifiedDao.updateRatingByTmdbId(u.id, u.rating)
                        else -> mediaUnifiedDao.updateRatingByImdbId(u.id, u.rating)
                    }
                }
            }
            Timber.d("WORKER [RatingSync] Batch flush: ${batch.size} queued → $updated rows affected in DB")
            return updated
        }
        override suspend fun doWork(): Result {
            return try {
                Timber.i("WORKER [RatingSync] ══════ STARTING rating synchronization ══════")

                // Promote to Foreground Service to bypass 10-min WorkManager limit
                try {
                    setForeground(getForegroundInfo())
                    Timber.d("WORKER [RatingSync] Foreground service set — no time limit")
                } catch (e: Exception) {
                    Timber.e(e, "WORKER [RatingSync] Failed to set foreground: ${e.message}")
                }

                // Load configuration
                val config = loadConfiguration()
                Timber.i("WORKER [RatingSync] Config: movieSource=${config.movieSource}, delay=${config.delayMs}ms, batching=${config.batchingEnabled}, dailyLimit=${config.dailyLimit}")
                Timber.i("WORKER [RatingSync] API keys: tmdb=${if (config.tmdbKey.isNotBlank()) "present (${config.tmdbKey.take(4)}...)" else "MISSING"}, omdb=${if (config.omdbKey.isNotBlank()) "present (${config.omdbKey.take(4)}...)" else "MISSING"}")

                if (!config.isValid()) {
                    Timber.e("WORKER [RatingSync] ✗ ABORTED — missing API keys (tmdb=${config.tmdbKey.isNotBlank()}, omdb=${config.omdbKey.isNotBlank()})")
                    return Result.failure()
                }

                var totalUpdated = 0

                // Check if batching enabled and if we should reset daily progress
                if (config.batchingEnabled) {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val lastRunDate = settingsRepository.ratingSyncLastRunDate.first()
                    Timber.d("WORKER [RatingSync] Batching: today=$today, lastRun=$lastRunDate")
                    if (lastRunDate != today) {
                        Timber.d("WORKER [RatingSync] New day detected, resetting daily progress")
                        settingsRepository.resetRatingSyncProgress()
                        settingsRepository.saveRatingSyncLastRunDate(today)
                    }
                }

                // Sync Series with TMDb (always)
                updateNotification("Phase 1/3: Series (TMDb)…")
                val seriesTmdb = syncSeriesWithTmdb(config)
                totalUpdated += seriesTmdb
                Timber.i("WORKER [RatingSync] Phase 1/3 done — Series via TMDb: $seriesTmdb updated")

                // Sync Series without TMDb (fallback to OMDb)
                updateNotification("Phase 2/3: Series (OMDb fallback)…")
                val seriesOmdb = syncSeriesWithOmdbFallback(config)
                totalUpdated += seriesOmdb
                Timber.i("WORKER [RatingSync] Phase 2/3 done — Series via OMDb fallback: $seriesOmdb updated")

                // Sync Movies (TMDb or OMDb based on config)
                updateNotification("Phase 3/3: Movies (${config.movieSource})…")
                val movies = syncMovies(config)
                totalUpdated += movies
                Timber.i("WORKER [RatingSync] Phase 3/3 done — Movies via ${config.movieSource}: $movies updated")

                Timber.i("WORKER [RatingSync] ══════ COMPLETED ══════ Total updated: $totalUpdated (series_tmdb=$seriesTmdb, series_omdb=$seriesOmdb, movies=$movies)")
                Result.success()
            } catch (e: CancellationException) {
                // System stopped the worker (10-min limit or REPLACE policy).
                // Return success to prevent WorkManager from rescheduling.
                Timber.w("WORKER [RatingSync] ══════ STOPPED by system ══════ (will NOT reschedule)")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "WORKER [RatingSync] ══════ FAILED ══════ ${e.javaClass.simpleName}: ${e.message}")
                Result.failure()
            }
        }

        /**
         * Sync series with TMDb IDs using TMDb API.
         */
        private suspend fun syncSeriesWithTmdb(config: RatingSyncConfig): Int {
            val allSeries = mediaDao.getAllSeriesWithTmdbId()
            val progressSeries = if (config.batchingEnabled) settingsRepository.ratingSyncProgressSeries.first() else 0
            val remainingQuota = if (config.batchingEnabled) config.dailyLimit - progressSeries else Int.MAX_VALUE

            if (config.batchingEnabled && remainingQuota <= 0) {
                Timber.d("WORKER [RatingSync] Daily quota reached for series, skipping")
                return 0
            }

            val seriesToSync = if (config.batchingEnabled) {
                allSeries.take(remainingQuota)
            } else {
                allSeries
            }

            Timber.i("WORKER [RatingSync] [Series/TMDb] Total=${allSeries.size}, toSync=${seriesToSync.size}, batchProgress=$progressSeries")

            var updated = 0
            var skipped = 0
            var errors = 0
            for ((index, tmdbId) in seriesToSync.withIndex()) {
                if (isStopped) {
                    Timber.w("WORKER [RatingSync] [Series/TMDb] Stopped at $index/${seriesToSync.size}")
                    break
                }
                try {
                    val response = tmdbApiService.getTvDetails(tmdbId, config.tmdbKey)
                    val rating = response.voteAverage ?: 0.0

                    if (rating > 0.0) {
                        updated += queueUpdate("tmdb", tmdbId, rating)
                    } else {
                        skipped++
                    }

                    if ((index + 1) % 50 == 0 || index == seriesToSync.size - 1) {
                        Timber.d("WORKER [RatingSync] [Series/TMDb] Progress: ${index + 1}/${seriesToSync.size} (updated=$updated, skipped=$skipped, errors=$errors)")
                        updateNotification("Series (TMDb): ${index + 1}/${seriesToSync.size}")
                    }

                    // Rate limiting
                    if (index < seriesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressSeries(progressSeries + index + 1)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: javax.net.ssl.SSLHandshakeException) {
                    errors++
                    Timber.e(e, "WORKER [RatingSync] [Series/TMDb] SSL Error for tmdbId=$tmdbId — check device date/time")
                } catch (e: Exception) {
                    errors++
                    Timber.w("WORKER [RatingSync] [Series/TMDb] Failed tmdbId=$tmdbId: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            updated += flushUpdates()
            Timber.i("WORKER [RatingSync] [Series/TMDb] Done: updated=$updated, skipped=$skipped, errors=$errors")

            // Reset progress if all done and not batching
            if (!config.batchingEnabled || seriesToSync.size >= allSeries.size) {
                settingsRepository.saveRatingSyncProgressSeries(0)
            }

            return updated
        }

        /**
         * Sync series without TMDb IDs using OMDb API as fallback.
         */
        private suspend fun syncSeriesWithOmdbFallback(config: RatingSyncConfig): Int {
            val allSeries = mediaDao.getAllSeriesWithImdbIdNoTmdbId()
            Timber.i("WORKER [RatingSync] [Series/OMDb] Total=${allSeries.size} series with IMDb IDs (no TMDb)")

            var updated = 0
            var skipped = 0
            var errors = 0
            for ((index, imdbId) in allSeries.withIndex()) {
                if (isStopped) {
                    Timber.w("WORKER [RatingSync] [Series/OMDb] Stopped at $index/${allSeries.size}")
                    break
                }
                try {
                    val response = omdbApiService.getRating(imdbId, config.omdbKey)
                    val ratingStr = response.imdbRating

                    if (ratingStr != null && ratingStr != "N/A") {
                        val rating = ratingStr.toDoubleOrNull() ?: 0.0
                        if (rating > 0.0) {
                            updated += queueUpdate("imdb", imdbId, rating)
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }

                    if ((index + 1) % 50 == 0 || index == allSeries.size - 1) {
                        Timber.d("WORKER [RatingSync] [Series/OMDb] Progress: ${index + 1}/${allSeries.size} (updated=$updated, skipped=$skipped, errors=$errors)")
                        updateNotification("Series OMDb: ${index + 1}/${allSeries.size}")
                    }

                    // Rate limiting
                    if (index < allSeries.size - 1) {
                        delay(config.delayMs)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: javax.net.ssl.SSLHandshakeException) {
                    errors++
                    Timber.e(e, "WORKER [RatingSync] [Series/OMDb] SSL Error for imdbId=$imdbId — check device date/time")
                } catch (e: Exception) {
                    errors++
                    Timber.w("WORKER [RatingSync] [Series/OMDb] Failed imdbId=$imdbId: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            updated += flushUpdates()
            Timber.i("WORKER [RatingSync] [Series/OMDb] Done: updated=$updated, skipped=$skipped, errors=$errors")

            return updated
        }

        /**
         * Sync movies using the configured source (TMDb or OMDb).
         */
        private suspend fun syncMovies(config: RatingSyncConfig): Int {
            return if (config.movieSource == "tmdb") {
                syncMoviesWithTmdb(config)
            } else {
                syncMoviesWithOmdb(config)
            }
        }

        /**
         * Sync movies with TMDb IDs using TMDb API.
         */
        private suspend fun syncMoviesWithTmdb(config: RatingSyncConfig): Int {
            val allMovies = mediaDao.getAllMoviesWithTmdbId()
            val progressMovies = if (config.batchingEnabled) settingsRepository.ratingSyncProgressMovies.first() else 0
            val remainingQuota = if (config.batchingEnabled) config.dailyLimit - progressMovies else Int.MAX_VALUE

            if (config.batchingEnabled && remainingQuota <= 0) {
                Timber.d("WORKER [RatingSync] Daily quota reached for movies, skipping")
                return 0
            }

            val moviesToSync = if (config.batchingEnabled) {
                allMovies.take(remainingQuota)
            } else {
                allMovies
            }

            Timber.i("WORKER [RatingSync] [Movies/TMDb] Total=${allMovies.size}, toSync=${moviesToSync.size}, batchProgress=$progressMovies")

            var updated = 0
            var skipped = 0
            var errors = 0
            for ((index, tmdbId) in moviesToSync.withIndex()) {
                if (isStopped) {
                    Timber.w("WORKER [RatingSync] [Movies/TMDb] Stopped at $index/${moviesToSync.size}")
                    break
                }
                try {
                    val response = tmdbApiService.getMovieDetails(tmdbId, config.tmdbKey)
                    val rating = response.voteAverage ?: 0.0

                    if (rating > 0.0) {
                        updated += queueUpdate("tmdb", tmdbId, rating)
                    } else {
                        skipped++
                    }

                    if ((index + 1) % 50 == 0 || index == moviesToSync.size - 1) {
                        Timber.d("WORKER [RatingSync] [Movies/TMDb] Progress: ${index + 1}/${moviesToSync.size} (updated=$updated, skipped=$skipped, errors=$errors)")
                        updateNotification("Movies (TMDb): ${index + 1}/${moviesToSync.size}")
                    }

                    // Rate limiting
                    if (index < moviesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressMovies(progressMovies + index + 1)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: javax.net.ssl.SSLHandshakeException) {
                    errors++
                    Timber.e(e, "WORKER [RatingSync] [Movies/TMDb] SSL Error for tmdbId=$tmdbId — check device date/time")
                } catch (e: Exception) {
                    errors++
                    Timber.w("WORKER [RatingSync] [Movies/TMDb] Failed tmdbId=$tmdbId: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            updated += flushUpdates()
            Timber.i("WORKER [RatingSync] [Movies/TMDb] Done: updated=$updated, skipped=$skipped, errors=$errors")

            // Reset progress if all done and not batching
            if (!config.batchingEnabled || moviesToSync.size >= allMovies.size) {
                settingsRepository.saveRatingSyncProgressMovies(0)
            }

            return updated
        }

        /**
         * Sync movies with IMDb IDs using OMDb API.
         */
        private suspend fun syncMoviesWithOmdb(config: RatingSyncConfig): Int {
            val allMovies = mediaDao.getAllMoviesWithImdbId()
            val progressMovies = if (config.batchingEnabled) settingsRepository.ratingSyncProgressMovies.first() else 0
            val remainingQuota = if (config.batchingEnabled) config.dailyLimit - progressMovies else Int.MAX_VALUE

            if (config.batchingEnabled && remainingQuota <= 0) {
                Timber.d("WORKER [RatingSync] Daily quota reached for movies, skipping")
                return 0
            }

            val moviesToSync = if (config.batchingEnabled) {
                allMovies.take(remainingQuota)
            } else {
                allMovies
            }

            Timber.i("WORKER [RatingSync] [Movies/OMDb] Total=${allMovies.size}, toSync=${moviesToSync.size}, batchProgress=$progressMovies")

            var updated = 0
            var skipped = 0
            var errors = 0
            for ((index, imdbId) in moviesToSync.withIndex()) {
                if (isStopped) {
                    Timber.w("WORKER [RatingSync] [Movies/OMDb] Stopped at $index/${moviesToSync.size}")
                    break
                }
                try {
                    val response = omdbApiService.getRating(imdbId, config.omdbKey)
                    val ratingStr = response.imdbRating

                    if (ratingStr != null && ratingStr != "N/A") {
                        val rating = ratingStr.toDoubleOrNull() ?: 0.0
                        if (rating > 0.0) {
                            updated += queueUpdate("imdb", imdbId, rating)
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }

                    if ((index + 1) % 50 == 0 || index == moviesToSync.size - 1) {
                        Timber.d("WORKER [RatingSync] [Movies/OMDb] Progress: ${index + 1}/${moviesToSync.size} (updated=$updated, skipped=$skipped, errors=$errors)")
                        updateNotification("Movies (OMDb): ${index + 1}/${moviesToSync.size}")
                    }

                    // Rate limiting
                    if (index < moviesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressMovies(progressMovies + index + 1)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: javax.net.ssl.SSLHandshakeException) {
                    errors++
                    Timber.e(e, "WORKER [RatingSync] [Movies/OMDb] SSL Error for imdbId=$imdbId — check device date/time")
                } catch (e: Exception) {
                    errors++
                    Timber.w("WORKER [RatingSync] [Movies/OMDb] Failed imdbId=$imdbId: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            updated += flushUpdates()
            Timber.i("WORKER [RatingSync] [Movies/OMDb] Done: updated=$updated, skipped=$skipped, errors=$errors")

            // Reset progress if all done and not batching
            if (!config.batchingEnabled || moviesToSync.size >= allMovies.size) {
                settingsRepository.saveRatingSyncProgressMovies(0)
            }

            return updated
        }

        /**
         * Load configuration from SettingsRepository.
         */
        private suspend fun loadConfiguration(): RatingSyncConfig {
            return RatingSyncConfig(
                movieSource = settingsRepository.ratingSyncSource.first(),
                delayMs = settingsRepository.ratingSyncDelay.first(),
                batchingEnabled = settingsRepository.ratingSyncBatchingEnabled.first(),
                dailyLimit = settingsRepository.ratingSyncDailyLimit.first(),
                tmdbKey = apiKeyManager.getTmdbApiKey() ?: "",
                omdbKey = apiKeyManager.getOmdbApiKey() ?: "",
            )
        }

        /**
         * Configuration data class.
         */
        private data class RatingSyncConfig(
            val movieSource: String, // "tmdb" or "omdb"
            val delayMs: Long,
            val batchingEnabled: Boolean,
            val dailyLimit: Int,
            val tmdbKey: String,
            val omdbKey: String,
        ) {
            fun isValid(): Boolean {
                return tmdbKey.isNotBlank() && omdbKey.isNotBlank()
            }
        }

        private fun updateNotification(text: String) {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Rating Synchronization",
                    NotificationManager.IMPORTANCE_LOW,
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(applicationContext.getString(R.string.sync_notification_title))
                .setContentText("Syncing ratings…")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }
    }
