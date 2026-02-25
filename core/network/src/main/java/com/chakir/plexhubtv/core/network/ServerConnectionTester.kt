package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionResult(
    val url: String,
    val success: Boolean,
    val latencyMs: Long,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
)

/**
 * Interface pour tester la connectivité d'une URL de serveur.
 * Utilise un client OkHttp dédié avec des timeouts courts (5s) pour
 * accélérer la découverte des serveurs.
 */
interface ServerConnectionTester {
    suspend fun testConnection(
        url: String,
        token: String,
        timeoutSeconds: Int = 10,
    ): ConnectionResult
}

@Singleton
class OkHttpConnectionTester
    @Inject
    constructor(
        baseClient: OkHttpClient,
    ) : ServerConnectionTester {
        // Derive from the app's OkHttpClient to inherit SSL trust config (self-signed certs on LAN)
        private val defaultClient =
            baseClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        // Longer timeout client for relay connections (shared connection pool)
        private val relayClient =
            baseClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        override suspend fun testConnection(
            url: String,
            token: String,
            timeoutSeconds: Int,
        ): ConnectionResult {
            val client = if (timeoutSeconds > 10) relayClient else defaultClient
            return withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val request =
                    Request.Builder()
                        .url(url)
                        .addHeader("X-Plex-Token", token)
                        .addHeader("Accept", "application/json")
                        .get()
                        .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val end = System.currentTimeMillis()
                        val latency = end - start

                        val isReachabilitySuccess = response.isSuccessful || response.code == 401

                        ConnectionResult(
                            url = url,
                            success = isReachabilitySuccess,
                            latencyMs = latency,
                            errorCode = if (!isReachabilitySuccess) response.code else null,
                        )
                    }
                } catch (e: Exception) {
                    ConnectionResult(
                        url = url,
                        success = false,
                        latencyMs = Long.MAX_VALUE,
                        errorMessage = e.javaClass.simpleName,
                    )
                }
            }
        }
    }
