package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.network.ApiKeyManager
import com.chakir.plexhubtv.core.network.OmdbApiService
import com.chakir.plexhubtv.core.network.TmdbApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Background worker for syncing media ratings from TMDb and OMDb APIs.
 * 
 * Strategy:
 * - Series: Fetch TMDb ratings (primary), fallback to OMDb if no TMDb ID
 * - Movies: Fetch OMDb ratings (IMDb ratings)
 * - Uses DISTINCT queries to prevent duplicate API calls
 * - Batch processing with delays to respect rate limits
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
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                Timber.i("WORKER [RatingSync] Starting rating synchronization")

                // Check if API keys are configured
                val tmdbKey = apiKeyManager.getTmdbApiKey()
                val omdbKey = apiKeyManager.getOmdbApiKey()

                if (tmdbKey.isNullOrBlank() || omdbKey.isNullOrBlank()) {
                    Timber.e("WORKER [RatingSync] API keys not configured")
                    return Result.failure()
                }

                var totalUpdated = 0

                // Sync Series with TMDb
                val seriesWithTmdb = mediaDao.getAllSeriesWithTmdbId()
                Timber.d("WORKER [RatingSync] Found ${seriesWithTmdb.size} unique series with TMDb IDs")

                seriesWithTmdb.forEachIndexed { index, tmdbId ->
                    try {
                        val response = tmdbApiService.getTvDetails(tmdbId, tmdbKey)
                        val rating = response.voteAverage ?: 0.0

                        if (rating > 0.0) {
                            val updated = mediaDao.updateRatingByTmdbId(tmdbId, rating)
                            totalUpdated += updated
                            Timber.d("WORKER [RatingSync] Updated $updated series with TMDb ID $tmdbId to SCRAPED rating $rating")
                        }

                        // Rate limiting: delay between requests
                        if (index < seriesWithTmdb.size - 1) {
                            delay(250) // 4 requests per second
                        }
                    } catch (e: javax.net.ssl.SSLHandshakeException) {
                        Timber.e(e, "WORKER [RatingSync] SSL Error for $tmdbId. Tip: Check if your device's date/time is correct.")
                    } catch (e: Exception) {
                        Timber.e(e, "WORKER [RatingSync] Failed to fetch TMDb rating for $tmdbId")
                    }
                }

                // Sync Series without TMDb (fallback to OMDb)
                val seriesWithImdbOnly = mediaDao.getAllSeriesWithImdbIdNoTmdbId()
                Timber.d("WORKER [RatingSync] Found ${seriesWithImdbOnly.size} unique series with IMDb IDs (no TMDb)")

                seriesWithImdbOnly.forEachIndexed { index, imdbId ->
                    try {
                        val response = omdbApiService.getRating(imdbId, omdbKey)
                        val ratingStr = response.imdbRating

                        if (ratingStr != null && ratingStr != "N/A") {
                            val rating = ratingStr.toDoubleOrNull() ?: 0.0
                            if (rating > 0.0) {
                                val updated = mediaDao.updateRatingByImdbId(imdbId, rating)
                                totalUpdated += updated
                                Timber.d("WORKER [RatingSync] Updated $updated series with IMDb ID $imdbId to SCRAPED rating $rating")
                            }
                        }

                        // Rate limiting
                        if (index < seriesWithImdbOnly.size - 1) {
                            delay(250)
                        }
                    } catch (e: javax.net.ssl.SSLHandshakeException) {
                        Timber.e(e, "WORKER [RatingSync] SSL Error for IMDb $imdbId. Tip: Check if your device's date/time is correct.")
                    } catch (e: Exception) {
                        Timber.e(e, "WORKER [RatingSync] Failed to fetch OMDb rating for $imdbId")
                    }
                }

                // Sync Movies with OMDb
                val moviesWithImdb = mediaDao.getAllMoviesWithImdbId()
                Timber.d("WORKER [RatingSync] Found ${moviesWithImdb.size} unique movies with IMDb IDs")

                moviesWithImdb.forEachIndexed { index, imdbId ->
                    try {
                        val response = omdbApiService.getRating(imdbId, omdbKey)
                        val ratingStr = response.imdbRating

                        if (ratingStr != null && ratingStr != "N/A") {
                            val rating = ratingStr.toDoubleOrNull() ?: 0.0
                            if (rating > 0.0) {
                                val updated = mediaDao.updateRatingByImdbId(imdbId, rating)
                                totalUpdated += updated
                                Timber.d("WORKER [RatingSync] Updated $updated movies with IMDb ID $imdbId to SCRAPED rating $rating")
                            }
                        }

                        // Rate limiting
                        if (index < moviesWithImdb.size - 1) {
                            delay(250)
                        }
                    } catch (e: javax.net.ssl.SSLHandshakeException) {
                        Timber.e(e, "WORKER [RatingSync] SSL Error for Movie $imdbId. Tip: Check if your device's date/time is correct.")
                    } catch (e: Exception) {
                        Timber.e(e, "WORKER [RatingSync] Failed to fetch OMDb rating for $imdbId")
                    }
                }

                Timber.i("WORKER [RatingSync] Completed successfully. Total updated: $totalUpdated")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "WORKER [RatingSync] Failed with exception")
                Result.failure()
            }
        }
    }
