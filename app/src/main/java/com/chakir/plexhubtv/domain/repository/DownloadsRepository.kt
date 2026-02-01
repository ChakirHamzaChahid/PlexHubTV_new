package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.DownloadStatus
import com.chakir.plexhubtv.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Interface pour le système de synchronisation hors-ligne (Downloads).
 *
 * Utilise WorkManager pour gérer les tâches de fond.
 */
interface DownloadsRepository {
    /** Observe la liste de tous les médias téléchargés ou en cours. */
    fun getAllDownloads(): Flow<List<MediaItem>>

    /** Observe l'état d'un téléchargement spécifique. */
    fun getDownloadStatus(mediaId: String): Flow<DownloadStatus>

    /** Lance le téléchargement d'un média (incluant transcodage si nécessaire). */
    suspend fun startDownload(media: MediaItem): Result<Unit>

    /** Annule un téléchargement en cours. */
    suspend fun cancelDownload(mediaId: String): Result<Unit>

    /** Supprime un média téléchargé du stockage local. */
    suspend fun deleteDownload(mediaId: String): Result<Unit>

    /** Vérifie si un item est déjà présent en base locale. */
    suspend fun getDownloadedItem(ratingKey: String): Result<com.chakir.plexhubtv.core.database.DownloadEntity?>
}
