package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    /** Change le statut de visionnage d'un média (Vidé / Non vidé). */
    suspend fun toggleWatchStatus(
        media: MediaItem,
        isWatched: Boolean,
    ): Result<Unit>

    /** Met à jour la progression de lecture sur Plex. */
    suspend fun updatePlaybackProgress(
        media: MediaItem,
        positionMs: Long,
    ): Result<Unit>

    /** Récupère le média suivant dans la file d'attente/saison. */
    suspend fun getNextMedia(currentItem: MediaItem): MediaItem?

    /** Récupère le média précédent. */
    suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem?

    /** Récupère l'historique de visionnage. */
    fun getWatchHistory(
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<MediaItem>>

    fun getWatchHistoryPaged(): Flow<androidx.paging.PagingData<MediaItem>>

    /** Envoie un timeline "stopped" au serveur Plex (appelé à la fermeture du player). */
    suspend fun sendStoppedTimeline(
        media: MediaItem,
        positionMs: Long,
    ): Result<Unit>

    /** Écrit le cache de progression local en BDD (appelé au stop du player). */
    suspend fun flushLocalProgress()

    /** Met à jour la sélection de flux (audio/sous-titres) pour un média. */
    suspend fun updateStreamSelection(
        serverId: String,
        partId: String,
        audioStreamId: String? = null,
        subtitleStreamId: String? = null,
    ): Result<Unit>
}
