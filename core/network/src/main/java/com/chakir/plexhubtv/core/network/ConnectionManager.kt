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
         * Strategy: Race direct candidates first (10s timeout), then fallback to relay (30s timeout).
         * This ensures fast servers respond quickly while relay-only servers still get connected.
         */
        suspend fun findBestConnection(server: Server): String? {
            if (_isOffline.value) return null

            // Check cache first
            _activeConnections.value[server.clientIdentifier]?.let { return it }

            val directCandidates = server.connectionCandidates.filter { !it.relay }.map { it.uri }.distinct()
            val relayCandidates = server.connectionCandidates.filter { it.relay }.map { it.uri }.distinct()

            // 1. Race direct candidates (local + public) with standard timeout
            if (directCandidates.isNotEmpty()) {
                val validUrl = raceUrls(directCandidates, server.accessToken ?: "")
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    return validUrl
                }
            }

            // 2. Fallback: try relay candidates with longer timeout
            if (relayCandidates.isNotEmpty()) {
                Timber.d("ConnectionManager: Trying relay candidates for ${server.name} (${relayCandidates.size})")
                val validUrl = raceUrls(relayCandidates, server.accessToken ?: "", timeoutSeconds = 30)
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    return validUrl
                }
            }

            // 3. Server is relay-capable (server.relay=true) but no candidate has relay flag
            //    Retry all candidates with longer timeout
            if (server.relay && relayCandidates.isEmpty()) {
                Timber.d("ConnectionManager: Retrying all candidates with relay timeout for ${server.name}")
                val allUrls = server.connectionCandidates.map { it.uri }.distinct()
                val validUrl = raceUrls(allUrls, server.accessToken ?: "", timeoutSeconds = 30)
                if (validUrl != null) {
                    cacheConnection(server.clientIdentifier, validUrl)
                    return validUrl
                }
            }

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
                var completedCount = 0

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
                                completedCount++
                                if (completedCount == urls.size && !winner.isCompleted) {
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
