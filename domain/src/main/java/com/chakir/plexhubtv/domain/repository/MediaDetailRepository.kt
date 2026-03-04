package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.UnifiedSeason
import kotlinx.coroutines.flow.Flow

interface MediaDetailRepository {
    /** Récupère les métadonnées complètes d'un média avec gestion de fallback. */
    suspend fun getMediaDetail(
        ratingKey: String,
        serverId: String,
    ): Result<MediaItem>

    /** Récupère les épisodes d'une saison. */
    suspend fun getSeasonEpisodes(
        ratingKey: String,
        serverId: String,
    ): Result<List<MediaItem>>

    /** Récupère les saisons d'une série. */
    suspend fun getShowSeasons(
        ratingKey: String,
        serverId: String,
    ): Result<List<MediaItem>>

    /** Récupère les suggestions (Similaires, etc.). */
    suspend fun getSimilarMedia(
        ratingKey: String,
        serverId: String,
    ): Result<List<MediaItem>>

    /** Fetches all episodes for a show across enabled servers and merges into unified seasons. */
    suspend fun getUnifiedSeasons(
        showUnificationId: String,
        enabledServerIds: List<String>,
    ): List<UnifiedSeason>

    /** Trouve les instances du même média sur d'autres serveurs (via unificationId Room). */
    suspend fun findRemoteSources(item: MediaItem): List<MediaItem>

    /** Met à jour les mediaParts d'un média en cache Room (pour persistence entre sessions). */
    suspend fun updateMediaParts(item: MediaItem)

    /** Récupère toutes les collections associées au média. */
    fun getMediaCollections(
        ratingKey: String,
        serverId: String,
    ): Flow<List<Collection>>

    /** Récupère une collection spécifique. */
    fun getCollection(
        collectionId: String,
        serverId: String,
    ): Flow<Collection?>
}
