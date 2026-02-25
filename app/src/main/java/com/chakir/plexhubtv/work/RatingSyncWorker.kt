package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.network.ApiKeyManager
import com.chakir.plexhubtv.core.network.OmdbApiService
import com.chakir.plexhubtv.core.network.TmdbApiService
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        private val tmdbApiService: TmdbApiService,
        private val omdbApiService: OmdbApiService,
        private val apiKeyManager: ApiKeyManager,
        private val settingsRepository: SettingsRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                Timber.i("WORKER [RatingSync] Starting rating synchronization")

                // Load configuration
                val config = loadConfiguration()
                if (!config.isValid()) {
                    Timber.e("WORKER [RatingSync] Invalid configuration (missing API keys)")
                    return Result.failure()
                }

                Timber.d("WORKER [RatingSync] Configuration: source=${config.movieSource}, delay=${config.delayMs}ms, batching=${config.batchingEnabled}, dailyLimit=${config.dailyLimit}")

                var totalUpdated = 0

                // Check if batching enabled and if we should reset daily progress
                if (config.batchingEnabled) {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val lastRunDate = settingsRepository.ratingSyncLastRunDate.first()
                    if (lastRunDate != today) {
                        Timber.d("WORKER [RatingSync] New day detected, resetting daily progress")
                        settingsRepository.resetRatingSyncProgress()
                        settingsRepository.saveRatingSyncLastRunDate(today)
                    }
                }

                // Sync Series with TMDb (always)
                totalUpdated += syncSeriesWithTmdb(config)

                // Sync Series without TMDb (fallback to OMDb)
                totalUpdated += syncSeriesWithOmdbFallback(config)

                // Sync Movies (TMDb or OMDb based on config)
                totalUpdated += syncMovies(config)

                Timber.i("WORKER [RatingSync] Completed successfully. Total updated: $totalUpdated")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "WORKER [RatingSync] Failed with exception")
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

            Timber.d("WORKER [RatingSync] Found ${allSeries.size} unique series with TMDb IDs, syncing ${seriesToSync.size} (progress: $progressSeries)")

            var updated = 0
            seriesToSync.forEachIndexed { index, tmdbId ->
                try {
                    val response = tmdbApiService.getTvDetails(tmdbId, config.tmdbKey)
                    val rating = response.voteAverage ?: 0.0

                    if (rating > 0.0) {
                        val rowsUpdated = mediaDao.updateRatingByTmdbId(tmdbId, rating)
                        updated += rowsUpdated
                        Timber.d("WORKER [RatingSync] Updated $rowsUpdated series with TMDb ID $tmdbId to rating $rating")
                    }

                    // Rate limiting
                    if (index < seriesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressSeries(progressSeries + index + 1)
                    }
                } catch (e: javax.net.ssl.SSLHandshakeException) {
                    Timber.e(e, "WORKER [RatingSync] SSL Error for TMDb ID $tmdbId. Tip: Check if your device's date/time is correct.")
                } catch (e: Exception) {
                    Timber.e(e, "WORKER [RatingSync] Failed to fetch TMDb rating for $tmdbId")
                }
            }

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
            Timber.d("WORKER [RatingSync] Found ${allSeries.size} unique series with IMDb IDs (no TMDb)")

            var updated = 0
            allSeries.forEachIndexed { index, imdbId ->
                try {
                    val response = omdbApiService.getRating(imdbId, config.omdbKey)
                    val ratingStr = response.imdbRating

                    if (ratingStr != null && ratingStr != "N/A") {
                        val rating = ratingStr.toDoubleOrNull() ?: 0.0
                        if (rating > 0.0) {
                            val rowsUpdated = mediaDao.updateRatingByImdbId(imdbId, rating)
                            updated += rowsUpdated
                            Timber.d("WORKER [RatingSync] Updated $rowsUpdated series with IMDb ID $imdbId to rating $rating")
                        }
                    }

                    // Rate limiting
                    if (index < allSeries.size - 1) {
                        delay(config.delayMs)
                    }
                } catch (e: javax.net.ssl.SSLHandshakeException) {
                    Timber.e(e, "WORKER [RatingSync] SSL Error for IMDb $imdbId. Tip: Check if your device's date/time is correct.")
                } catch (e: Exception) {
                    Timber.e(e, "WORKER [RatingSync] Failed to fetch OMDb rating for $imdbId")
                }
            }

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

            Timber.d("WORKER [RatingSync] Found ${allMovies.size} unique movies with TMDb IDs, syncing ${moviesToSync.size} (progress: $progressMovies)")

            var updated = 0
            moviesToSync.forEachIndexed { index, tmdbId ->
                try {
                    val response = tmdbApiService.getMovieDetails(tmdbId, config.tmdbKey)
                    val rating = response.voteAverage ?: 0.0

                    if (rating > 0.0) {
                        val rowsUpdated = mediaDao.updateRatingByTmdbId(tmdbId, rating)
                        updated += rowsUpdated
                        Timber.d("WORKER [RatingSync] Updated $rowsUpdated movies with TMDb ID $tmdbId to rating $rating")
                    }

                    // Rate limiting
                    if (index < moviesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressMovies(progressMovies + index + 1)
                    }
                } catch (e: javax.net.ssl.SSLHandshakeException) {
                    Timber.e(e, "WORKER [RatingSync] SSL Error for TMDb ID $tmdbId. Tip: Check if your device's date/time is correct.")
                } catch (e: Exception) {
                    Timber.e(e, "WORKER [RatingSync] Failed to fetch TMDb rating for $tmdbId")
                }
            }

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

            Timber.d("WORKER [RatingSync] Found ${allMovies.size} unique movies with IMDb IDs, syncing ${moviesToSync.size} (progress: $progressMovies)")

            var updated = 0
            moviesToSync.forEachIndexed { index, imdbId ->
                try {
                    val response = omdbApiService.getRating(imdbId, config.omdbKey)
                    val ratingStr = response.imdbRating

                    if (ratingStr != null && ratingStr != "N/A") {
                        val rating = ratingStr.toDoubleOrNull() ?: 0.0
                        if (rating > 0.0) {
                            val rowsUpdated = mediaDao.updateRatingByImdbId(imdbId, rating)
                            updated += rowsUpdated
                            Timber.d("WORKER [RatingSync] Updated $rowsUpdated movies with IMDb ID $imdbId to rating $rating")
                        }
                    }

                    // Rate limiting
                    if (index < moviesToSync.size - 1) {
                        delay(config.delayMs)
                    }

                    // Update progress if batching enabled
                    if (config.batchingEnabled) {
                        settingsRepository.saveRatingSyncProgressMovies(progressMovies + index + 1)
                    }
                } catch (e: javax.net.ssl.SSLHandshakeException) {
                    Timber.e(e, "WORKER [RatingSync] SSL Error for Movie $imdbId. Tip: Check if your device's date/time is correct.")
                } catch (e: Exception) {
                    Timber.e(e, "WORKER [RatingSync] Failed to fetch OMDb rating for $imdbId")
                }
            }

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
    }
