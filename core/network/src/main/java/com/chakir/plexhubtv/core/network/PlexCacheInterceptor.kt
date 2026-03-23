package com.chakir.plexhubtv.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Network interceptor that overrides Plex server Cache-Control headers for stable endpoints.
 *
 * Plex servers send `no-cache` or `no-store` on most responses, preventing OkHttp's disk cache
 * from working. This interceptor adds a `max-age` directive for endpoints whose data is stable
 * enough to tolerate short caching windows, significantly reducing redundant network calls.
 *
 * Must be added as a **network interceptor** (not application interceptor) so it modifies
 * the actual response stored in OkHttp's disk cache.
 */
class PlexCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val path = request.url.encodedPath
        val maxAge = when {
            // Library listings — stable for 5 minutes
            path.matches(Regex("/library/sections/\\d+/all.*")) -> 300
            // Individual metadata — stable for 5 minutes
            path.matches(Regex("/library/metadata/\\d+$")) -> 300
            // Library sections list — stable for 10 minutes
            path == "/library/sections" -> 600
            // Not a cacheable endpoint
            else -> return response
        }

        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAge")
            .removeHeader("Pragma")
            .build()
    }
}
