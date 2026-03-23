package com.chakir.plexhubtv.core.network.jellyfin

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests connectivity to a Jellyfin server by pinging the unauthenticated
 * `/System/Info/Public` endpoint.
 *
 * Unlike Plex (which races multiple connection candidates via ConnectionManager),
 * a Jellyfin server has a single URL. This tester is intentionally simple.
 */
@Singleton
class JellyfinConnectionTester @Inject constructor(
    private val api: JellyfinApiService,
) {
    /**
     * Pings the server and returns public info on success, or null on failure.
     * Useful for setup flow validation and health checks.
     */
    suspend fun test(baseUrl: String): JellyfinPublicInfo? {
        return try {
            val cleanUrl = baseUrl.trimEnd('/')
            val response = api.getPublicInfo("$cleanUrl/System/Info/Public")
            if (response.isSuccessful) {
                val info = response.body()
                Timber.d("JellyfinConnectionTester: OK → ${info?.serverName} v${info?.version}")
                info
            } else {
                Timber.w("JellyfinConnectionTester: HTTP ${response.code()} from $baseUrl")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "JellyfinConnectionTester: Failed to reach $baseUrl")
            null
        }
    }
}
