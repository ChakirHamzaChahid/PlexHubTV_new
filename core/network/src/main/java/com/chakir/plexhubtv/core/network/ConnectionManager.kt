package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire central des connexions aux serveurs Plex.
 *
 * Responsabilités :
 * - Détermine la meilleure URL de connexion pour chaque serveur (Local vs Distant).
 * - Utilise une logique de "Course" (Race) pour tester les connexions candidates en parallèle.
 * - Gère le mode Hors-ligne global de l'application.
 * - Cache les URLs valides pour éviter de re-tester à chaque appel.
 * - Persiste les URLs validées dans DataStore pour survie au redémarrage.
 */
@Singleton
class ConnectionManager
    @Inject
    constructor(
        private val connectionTester: ServerConnectionTester,
        private val settingsDataStore: SettingsDataStore,
        @com.chakir.plexhubtv.core.di.ApplicationScope private val scope: CoroutineScope,
    ) {
        // Map of Server MachineID -> Active (Best) Base URL
        private val _activeConnections = MutableStateFlow<Map<String, String>>(emptyMap())
        val activeConnections: StateFlow<Map<String, String>> = _activeConnections.asStateFlow()

        // Global Offline State
        private val _isOffline = MutableStateFlow(false)
        val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

        // Failed servers cache (serverId -> timestamp when it failed)
        // Skip retrying failed servers for 5 minutes to prevent UI blocking
        // Thread-safe for concurrent access from multiple coroutines
        private val failedServers = ConcurrentHashMap<String, Long>()
        private val FAILED_SERVER_SKIP_DURATION_MS = 5 * 60 * 1000L // 5 minutes

        init {
            // Restore persisted connections on cold start
            scope.launch {
                try {
                    val persisted = settingsDataStore.cachedConnections.first()
                    if (persisted.isNotEmpty()) {
                        _activeConnections.update { it + persisted }
                        Timber.d("ConnectionManager: Restored ${persisted.size} cached connections from DataStore")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "ConnectionManager: Failed to restore cached connections")
                }
            }
        }

        /**
         * Finds the best connection for a server.
         * Strategy: Race direct candidates first (3s timeout), then fallback to relay (5s timeout).
         * This ensures fast servers respond quickly while relay-only servers still get connected.
         * Failed servers are skipped for 5 minutes to prevent UI blocking.
         */
        suspend fun findBestConnection(server: Server): String? {
            if (_isOffline.value) return null

            // Check cache first
            _activeConnections.value[server.clientIdentifier]?.let { return it }

            // Skip if server recently failed (fail-fast to prevent UI blocking)
            val now = System.currentTimeMillis()
            failedServers[server.clientIdentifier]?.let { failedTime ->
                if (now - failedTime < FAILED_SERVER_SKIP_DURATION_MS) {
                    Timber.d("ConnectionManager: Skipping recently failed server ${server.name} (will retry in ${(FAILED_SERVER_SKIP_DURATION_MS - (now - failedTime)) / 1000}s)")
                    return null
                } else {
                    // Timeout expired, remove from failed list and retry
                    failedServers.remove(server.clientIdentifier)
                }
            }

            val directCandidates = server.connectionCandidates.filter { !it.relay }.map { it.uri }.distinct()
            val relayCandidates = server.connectionCandidates.filter { it.relay }.map { it.uri }.distinct()

            // 1. Race direct candidates (local + public) with aggressive timeout (3s)
            if (directCandidates.isNotEmpty()) {
                val validUrl = raceUrls(directCandidates, server.accessToken ?: "", timeoutSeconds = 3)
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    failedServers.remove(server.clientIdentifier) // Clear failed status
                    return validUrl
                }
            }

            // 2. Fallback: try relay candidates with short timeout (5s instead of 30s)
            if (relayCandidates.isNotEmpty()) {
                Timber.d("ConnectionManager: Trying relay candidates for ${server.name} (${relayCandidates.size})")
                val validUrl = raceUrls(relayCandidates, server.accessToken ?: "", timeoutSeconds = 5)
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    failedServers.remove(server.clientIdentifier) // Clear failed status
                    return validUrl
                }
            }

            // 3. Server is relay-capable (server.relay=true) but no candidate has relay flag
            //    Retry all candidates with short timeout (5s instead of 30s)
            if (server.relay && relayCandidates.isEmpty()) {
                Timber.d("ConnectionManager: Retrying all candidates with relay timeout for ${server.name}")
                val allUrls = server.connectionCandidates.map { it.uri }.distinct()
                val validUrl = raceUrls(allUrls, server.accessToken ?: "", timeoutSeconds = 5)
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    failedServers.remove(server.clientIdentifier) // Clear failed status
                    return validUrl
                }
            }

            // All attempts failed - mark server as failed to skip for 5 minutes
            failedServers[server.clientIdentifier] = now
            Timber.w("ConnectionManager: Server ${server.name} failed all connection attempts, will skip for 5 minutes")

            return null
        }

        private suspend fun raceUrls(
            urls: List<String>,
            token: String,
            timeoutSeconds: Int = 10,
        ): String? =
            coroutineScope {
                if (urls.isEmpty()) return@coroutineScope null

                val winner = kotlinx.coroutines.CompletableDeferred<String?>()
                val completedCount = AtomicInteger(0)

                val jobs =
                    urls.map { url ->
                        launch {
                            try {
                                val result = connectionTester.testConnection(url, token, timeoutSeconds)
                                if (result.success) {
                                    winner.complete(url)
                                }
                            } catch (e: Exception) {
                                // Ignore individual test failures
                            } finally {
                                // Thread-safe increment and check
                                if (completedCount.incrementAndGet() == urls.size && !winner.isCompleted) {
                                    winner.complete(null)
                                }
                            }
                        }
                    }

                // Wait for winner or all to fail
                val firstUrl =
                    try {
                        winner.await()
                    } catch (e: Exception) {
                        null
                    }

                jobs.forEach { it.cancel() } // Stop other tests
                firstUrl
            }

        fun cacheConnection(
            serverId: String,
            url: String,
        ) {
            _activeConnections.update { it + (serverId to url) }
            // Persist to DataStore for cold start recovery
            scope.launch {
                try {
                    settingsDataStore.saveCachedConnections(_activeConnections.value)
                } catch (e: Exception) {
                    Timber.w(e, "ConnectionManager: Failed to persist connection cache")
                }
            }
        }

        fun getCachedUrl(serverId: String): String? = _activeConnections.value[serverId]

        fun setOfflineMode(isOffline: Boolean) {
            _isOffline.value = isOffline
        }

        /**
         * Clears the failed servers cache, allowing immediate retry of all servers.
         * Useful for manual "Refresh" actions.
         */
        fun clearFailedServers() {
            failedServers.clear()
            Timber.d("ConnectionManager: Cleared failed servers cache")
        }

        suspend fun checkConnectionStatus(server: Server): ConnectionResult {
            val candidates = server.connectionCandidates
            if (candidates.isEmpty()) {
                return ConnectionResult("No URLs", false, 0, 404)
            }

            // Test all candidates in parallel with appropriate timeouts per type
            // Use relay timeout if candidate is flagged relay OR server is relay-capable with no explicit relay candidates
            val hasExplicitRelayCandidates = candidates.any { it.relay }
            val results =
                candidates.distinctBy { it.uri }.map { candidate ->
                    val timeout = if (candidate.relay || (server.relay && !hasExplicitRelayCandidates)) 30 else 10
                    scope.async { connectionTester.testConnection(candidate.uri, server.accessToken ?: "", timeout) }
                }.awaitAll()

            val winner =
                results
                    .filter { it.success }
                    .minByOrNull { it.latencyMs }

            return winner ?: results.firstOrNull() ?: ConnectionResult("Unknown", false, 0, 500)
        }
    }
