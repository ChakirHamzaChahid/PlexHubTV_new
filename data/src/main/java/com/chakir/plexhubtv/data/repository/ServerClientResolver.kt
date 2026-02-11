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
    suspend fun getActiveClients(): List<PlexClient> = coroutineScope {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull()
            ?: return@coroutineScope emptyList()
        servers.map { server ->
            async {
                val baseUrl = connectionManager.findBestConnection(server)
                if (baseUrl != null) PlexClient(server, api, baseUrl) else null
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun getClient(serverId: String): PlexClient? {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
        val server = servers.find { it.clientIdentifier == serverId } ?: return null
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return PlexClient(server, api, baseUrl)
    }

    suspend fun getServers(): List<Server> {
        return authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
    }
}
