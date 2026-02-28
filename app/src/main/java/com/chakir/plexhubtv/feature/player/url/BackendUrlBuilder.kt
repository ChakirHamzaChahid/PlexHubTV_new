package com.chakir.plexhubtv.feature.player.url

import com.chakir.plexhubtv.domain.repository.BackendRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds stream URLs for backend-sourced content by delegating to the backend API.
 * The backend resolves the actual Xtream stream URL server-side.
 */
@Singleton
class BackendUrlBuilder @Inject constructor(
    private val backendRepository: BackendRepository,
) {
    suspend fun buildUrl(ratingKey: String, serverId: String): String? {
        return backendRepository.getStreamUrl(ratingKey, serverId).getOrNull()
    }
}
