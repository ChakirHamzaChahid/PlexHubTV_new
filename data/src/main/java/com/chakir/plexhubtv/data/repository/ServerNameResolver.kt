package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.JellyfinServerRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for mapping serverId → human-readable server name.
 * Combines Plex servers, Backend servers, Xtream accounts, and Jellyfin servers.
 */
@Singleton
class ServerNameResolver @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendRepository: BackendRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
    private val jellyfinServerRepository: JellyfinServerRepository,
) {
    /**
     * Builds a combined map of all known server IDs to their display names.
     * - Plex: clientIdentifier → name
     * - Backend: "backend_{id}" → label
     * - Xtream: "xtream_{id}" → label
     * - Jellyfin: "jellyfin_{id}" → name
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
        } catch (e: Exception) {
            Timber.w(e, "ServerNameResolver: Failed to load backend servers")
        }

        // Xtream accounts
        try {
            xtreamAccountRepository.observeAccounts().firstOrNull()?.forEach { account ->
                map["xtream_${account.id}"] = account.label
            }
        } catch (e: Exception) {
            Timber.w(e, "ServerNameResolver: Failed to load Xtream accounts")
        }

        // Jellyfin servers
        try {
            jellyfinServerRepository.getServers().forEach { server ->
                map[server.prefixedServerId] = server.name
            }
        } catch (e: Exception) {
            Timber.w(e, "ServerNameResolver: Failed to load Jellyfin servers")
        }

        return map
    }
}
