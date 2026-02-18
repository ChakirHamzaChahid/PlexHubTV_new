package com.chakir.plexhubtv.di.image

import coil.intercept.Interceptor
import coil.request.ImageResult
import com.chakir.plexhubtv.core.common.PerformanceTracker
import com.chakir.plexhubtv.core.common.PerfCategory
import timber.log.Timber
import javax.inject.Inject

/**
 * Coil Interceptor that tracks image loading performance
 */
class PerformanceImageInterceptor @Inject constructor(
    private val performanceTracker: PerformanceTracker
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()

        // Generate unique operation ID using hashCode to avoid collisions with same token
        val urlHash = url.hashCode().toString(36) // Base36 for shorter ID
        val opId = "image_load_$urlHash"

        performanceTracker.startOperation(
            opId,
            PerfCategory.IMAGE_LOAD,
            "Coil Image Load",
            mapOf(
                "url" to url,
                "size" to "${request.sizeResolver}",
                "memoryCachePolicy" to request.memoryCachePolicy.name,
                "diskCachePolicy" to request.diskCachePolicy.name
            )
        )

        return try {
            val result = chain.proceed(request)

            // Detect cache source from result type
            val cacheSource = when (result) {
                is coil.request.SuccessResult -> {
                    when (result.dataSource) {
                        coil.decode.DataSource.MEMORY_CACHE -> "MEMORY"
                        coil.decode.DataSource.DISK -> "DISK"
                        coil.decode.DataSource.NETWORK -> "NETWORK"
                        else -> "UNKNOWN"
                    }
                }
                else -> "ERROR"
            }

            performanceTracker.endOperation(
                opId,
                success = result is coil.request.SuccessResult,
                additionalMeta = mapOf("cacheSource" to cacheSource)
            )

            result
        } catch (e: Exception) {
            performanceTracker.endOperation(opId, success = false, errorMessage = e.message)
            throw e
        }
    }
}
