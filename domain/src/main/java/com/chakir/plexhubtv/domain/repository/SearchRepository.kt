package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem

/**
 * Interface pour la recherche unifiée.
 */
interface SearchRepository {
    /**
     * Recherche un terme sur TOUS les serveurs connectés en parallèle.
     * @return Une liste agrégée et dédoublonnée de résultats.
     */
    suspend fun searchAllServers(query: String): Result<List<MediaItem>>

    /** Recherche sur un serveur spécifique. */
    suspend fun searchOnServer(
        server: com.chakir.plexhubtv.core.model.Server,
        query: String,
    ): Result<List<MediaItem>>
}
