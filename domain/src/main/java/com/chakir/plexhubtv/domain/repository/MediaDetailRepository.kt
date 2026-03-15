package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Collection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.UnifiedSeason
import kotlinx.coroutines.flow.Flow

/** Lightweight container for parent show metadata used during episode enrichment. */
data class ParentShowInfo(val imdbId: String?, val tmdbId: String?, val unificationId: String?)

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

    /** Returns the IMDB, TMDB IDs and unificationId of a show from Room cache. */
    suspend fun getParentShowIds(ratingKey: String, serverId: String): ParentShowInfo?

    /** Finds a show on a specific server by unificationId (Room-only, no network). */
    suspend fun findRemoteShowByUnificationId(unificationId: String, serverId: String): MediaItem?

    /** Returns all servers (serverId → showRatingKey) that have a show with the given unificationId. */
    suspend fun findServersWithShow(unificationId: String, excludeServerId: String): Map<String, String>

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

    /**
     * Soft-hide : masque le média du hub localement (toutes les instances cross-serveur via unificationId).
     * Ne supprime PAS du serveur Plex — voir MediaDetailRepositoryImpl pour le code de suppression serveur
     * conservé en commentaire.
     */
    suspend fun deleteMedia(ratingKey: String, serverId: String): Result<Unit>

    /** Checks whether the given server is owned by the current user. */
    suspend fun isServerOwned(serverId: String): Boolean

    /** Refreshes metadata (rating, synopsis, poster) from TMDB/OMDB for the given media. */
    suspend fun refreshMetadataFromTmdb(media: MediaItem): Result<Unit>
}
