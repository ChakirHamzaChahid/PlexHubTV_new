package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Server

/**
 * Interface pour la synchronisation globale des données serveur (Data Hydration).
 *
 * Différent de OfflineSync, ceci sert à récupérer les métadonnées pour la navigation.
 */
interface SyncRepository {
    /** Synchronise les métadonnées de base d'un serveur (sections, infos). */
    suspend fun syncServer(server: Server): Result<Unit>

    /** Synchronise potentiellement le contenu d'une bibliothèque (si nécessaire). */
    suspend fun syncLibrary(
        server: Server,
        libraryKey: String,
    ): Result<Unit>
}
