package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for mapping serverId → human-readable server name.
 * Combines Plex servers, Backend servers, and Xtream accounts.
 */
@Singleton
class ServerNameResolver @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendRepository: BackendRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
) {
    /**
     * Builds a combined map of all known server IDs to their display names.
     * - Plex: clientIdentifier → name
     * - Backend: "backend_{id}" → label
     * - Xtream: "xtream_{id}" → label
     */
    suspend fun getServerNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        // Plex servers
        authRepository.getServers(forceRefresh = false).getOrNull()?.forEach { server ->
            map[server.clientIdentifier] = server.name
        }

        // Backend servers
        try {
            backendRepository.observeServers().firstOrNull()
                ?.filter { it.isActive }
                ?.forEach { backend ->
                    map["backend_${backend.id}"] = backend.label
                }
        } catch (_: Exception) { }

        // Xtream accounts
        try {
            xtreamAccountRepository.observeAccounts().firstOrNull()?.forEach { account ->
                map["xtream_${account.id}"] = account.label
            }
        } catch (_: Exception) { }

        return map
    }
}
