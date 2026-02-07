package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionResult(
    val url: String,
    val success: Boolean,
    val latencyMs: Long,
    val errorCode: Int? = null
)

/**
 * Interface pour tester la connectivité d'une URL de serveur.
 * Utilise un client OkHttp dédié avec des timeouts courts (5s) pour
 * accélérer la découverte des serveurs.
 */
interface ServerConnectionTester {
    suspend fun testConnection(url: String, token: String): ConnectionResult
}

@Singleton
class OkHttpConnectionTester @Inject constructor() : ServerConnectionTester {

    // Dedicated client for testing: medium timeouts (was 5s, now 15s for reliability)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS) 
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun testConnection(url: String, token: String): ConnectionResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Plex-Token", token)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val end = System.currentTimeMillis()
                    val latency = end - start
                    
                    // 200 OK or 401 Unauthorized (implies server is reachable but token might be issue, still a network success)
                    // Plex often returns 401 if token is bad, but 200 if good. Both mean server is there.
                    // For finding "Best Connection", we care about reachability.
                    val isReachabilitySuccess = response.isSuccessful || response.code == 401

                    ConnectionResult(
                        url = url,
                        success = isReachabilitySuccess,
                        latencyMs = latency,
                        errorCode = if (!isReachabilitySuccess) response.code else null
                    )
                }
            } catch (e: Exception) {
                // e.g. Timeout, Unreachable
                ConnectionResult(
                    url = url,
                    success = false,
                    latencyMs = Long.MAX_VALUE
                )
            }
        }
    }
}
