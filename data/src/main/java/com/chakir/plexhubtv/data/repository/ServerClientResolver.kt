package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerClientResolver @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val api: PlexApiService,
) {
    /**
     * Resolves the best connection for a [Server] and returns a ready-to-use [PlexClient].
     * Use when you already have a Server object (e.g. Sync, Search).
     */
    suspend fun resolveClient(server: Server): PlexClient? {
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return PlexClient(server, api, baseUrl)
    }

    /**
     * Returns connected clients for all (or a subset of) servers, resolving in parallel.
     * @param selectedServerIds if non-empty, only returns clients for these server IDs.
     */
    suspend fun getActiveClients(selectedServerIds: Set<String> = emptySet()): List<PlexClient> = coroutineScope {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull()
            ?: return@coroutineScope emptyList()
        val filtered = if (selectedServerIds.isNotEmpty()) {
            servers.filter { it.clientIdentifier in selectedServerIds }
        } else {
            servers
        }
        filtered.map { server ->
            async {
                val baseUrl = connectionManager.findBestConnection(server)
                if (baseUrl != null) PlexClient(server, api, baseUrl) else null
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Returns a [PlexClient] for the given serverId.
     * Supports "default_server" and "all" aliases (resolves to first available server).
     */
    suspend fun getClient(serverId: String): PlexClient? {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
        val server = if (serverId == "default_server" || serverId == "all") {
            servers.firstOrNull()
        } else {
            servers.find { it.clientIdentifier == serverId }
        } ?: return null
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return PlexClient(server, api, baseUrl)
    }

    suspend fun getServers(): List<Server> {
        return authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
    }

    /**
     * Invalidates the cached connection for a server so the next
     * [getClient] re-tests all connection candidates.
     */
    fun invalidateConnection(serverId: String) {
        connectionManager.invalidateConnection(serverId)
    }
}
