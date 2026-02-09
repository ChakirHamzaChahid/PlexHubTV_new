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
         * Finds the best connection for a server using "Race" logic.
         * Returns the first WORKING URL immediately, then updates to faster one if found later?
         * For simplicity, this initial implementation waits for the 'race' winner.
         */
        suspend fun findBestConnection(server: Server): String? {
            if (_isOffline.value) return null

            // Check cache first
            _activeConnections.value[server.clientIdentifier]?.let { return it }

            // Prioritize: HTTPS & plex.direct > HTTPS > HTTP
            // But for the RACE, we might want to test them all or a subset.
            val urlsToTest = server.connectionCandidates.map { it.uri }.distinct()

            val validUrl = raceConnections(urlsToTest, server.accessToken ?: "")
            if (validUrl != null) {
                cacheConnection(server.clientIdentifier, validUrl)
            }
            return validUrl
        }

        private suspend fun raceConnections(
            urls: List<String>,
            token: String,
        ): String? =
            coroutineScope {
                if (urls.isEmpty()) return@coroutineScope null

                // Performance Optimization: Parallel Test
                raceUrls(urls, token)
            }

        private suspend fun raceUrls(
            urls: List<String>,
            token: String,
        ): String? =
            coroutineScope {
                val winner = kotlinx.coroutines.CompletableDeferred<String?>()
                var completedCount = 0

                val jobs =
                    urls.map { url ->
                        launch {
                            try {
                                val result = connectionTester.testConnection(url, token)
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
            val urlsToTest = server.connectionCandidates.map { it.uri }.distinct()
            if (urlsToTest.isEmpty()) {
                return ConnectionResult("No URLs", false, 0, 404)
            }

            // Use the same race logic but return the result directly
            // We want the BEST result (lowest latency success), or the "best" failure if all fail.
            val results =
                urlsToTest.map { url ->
                    scope.async { connectionTester.testConnection(url, server.accessToken ?: "") }
                }.awaitAll()

            val winner =
                results
                    .filter { it.success }
                    .minByOrNull { it.latencyMs }

            return winner ?: results.firstOrNull() ?: ConnectionResult("Unknown", false, 0, 500)
        }
    }
