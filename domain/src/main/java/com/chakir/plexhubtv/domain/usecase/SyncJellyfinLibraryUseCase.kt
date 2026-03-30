package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.domain.repository.JellyfinServerRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Synchronises all active Jellyfin servers into the local Room database.
 *
 * For each active server, delegates to [JellyfinServerRepository.syncLibrary]
 * which handles pagination, mapping, state preservation, and cleanup.
 */
class SyncJellyfinLibraryUseCase @Inject constructor(
    private val jellyfinRepo: JellyfinServerRepository,
) {
    /**
     * @return Total number of items synced across all servers.
     */
    suspend operator fun invoke(): Result<Int> {
        val allServers = jellyfinRepo.getServers()
        Timber.w("JELLYFIN_TRACE [SyncUseCase] all servers: ${allServers.size}, details=${allServers.map { "${it.name}(id=${it.id}, active=${it.isActive})" }}")
        val servers = allServers.filter { it.isActive }
        if (servers.isEmpty()) {
            Timber.w("JELLYFIN_TRACE [SyncUseCase] NO active servers, returning 0")
            return Result.success(0)
        }
        Timber.w("JELLYFIN_TRACE [SyncUseCase] ${servers.size} active server(s): ${servers.map { it.name }}")

        var totalSynced = 0
        var lastError: Throwable? = null

        for (server in servers) {
            try {
                val result = jellyfinRepo.syncLibrary(server.id)
                if (result.isSuccess) {
                    totalSynced += result.getOrDefault(0)
                    Timber.d("✓ [Jellyfin:${server.name}] Synced ${result.getOrDefault(0)} items")
                } else {
                    Timber.w("✗ [Jellyfin:${server.name}] Sync failed: ${result.exceptionOrNull()?.message}")
                    lastError = result.exceptionOrNull()
                }
            } catch (e: Exception) {
                Timber.e("✗ [Jellyfin:${server.name}] Exception: ${e.message}")
                lastError = e
            }
        }

        Timber.i("JELLYFIN [Sync] total: $totalSynced items across ${servers.size} server(s)")

        // If ALL servers failed, propagate the last error
        return if (totalSynced == 0 && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(totalSynced)
        }
    }
}
