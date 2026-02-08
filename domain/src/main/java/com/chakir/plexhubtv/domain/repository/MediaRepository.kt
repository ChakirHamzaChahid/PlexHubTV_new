package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Dépôt central pour l'accès aux métadonnées multimédias.
 *
 * C'est le point d'entrée unique pour obtenir les détails d'un film/série,
 * gérer les favoris, l'historique et l'agrégation multi-serveurs.
 */
interface MediaRepository {
    // --- Agrégation Multi-serveurs ---

    /**
     * Récupère la liste "On Deck" (En cours) unifiée depuis tous les serveurs.
     * Applique une stratégie de Cache-First puis Network-Refresh.
     */
    fun getUnifiedOnDeck(): Flow<List<MediaItem>>

    /** Récupère les "Hubs" (Carrousels) unifiés (Récents, Trending...). */
    fun getUnifiedHubs(): Flow<List<Hub>>

    /** Récupère une bibliothèque entière unifiée (ex: Tous les films de tous les serveurs). */
    suspend fun getUnifiedLibrary(mediaType: String): Result<List<MediaItem>>

    // --- Détails & Enfants ---

    /**
     * Récupère les métadonnées complètes d'un média.
     * @param ratingKey ID unique sur le serveur.
     * @param serverId ID du serveur.
     */
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

    /** Récupère les suggestions (Similaires, Autres films du réalisateur...). */
    suspend fun getSimilarMedia(
        ratingKey: String,
        serverId: String,
    ): Result<List<MediaItem>>

    /** Récupère toutes les collections associées au média (multi-serveurs). */
    fun getMediaCollections(
        ratingKey: String,
        serverId: String,
    ): Flow<List<com.chakir.plexhubtv.core.model.Collection>>

    /** Récupère une collection spécifique par son ID et serverId. */
    fun getCollection(
        collectionId: String,
        serverId: String,
    ): Flow<com.chakir.plexhubtv.core.model.Collection?>

    // --- Actions Utilisateur ---

    /** Marque un élément comme Vu ou Non Vu. */
    suspend fun toggleWatchStatus(
        media: MediaItem,
        isWatched: Boolean,
    ): Result<Unit>

    /** Sauvegarde la position de lecture (Resume Point). */
    suspend fun updatePlaybackProgress(
        media: MediaItem,
        positionMs: Long,
    ): Result<Unit>

    // --- Support Lecteur ---

    /** Trouve le média suivant (Binge Watching). */
    suspend fun getNextMedia(currentItem: MediaItem): MediaItem?

    /** Trouve le média précédent. */
    suspend fun getPreviousMedia(currentItem: MediaItem): MediaItem?

    // --- Favoris (Local DB) ---

    /** Observe la liste des favoris (Watchlist locale). */
    fun getFavorites(): Flow<List<MediaItem>>

    /** Vérifie si un élément est en favori. */
    fun isFavorite(
        ratingKey: String,
        serverId: String,
    ): Flow<Boolean>

    /** Ajoute/Retire des favoris. */
    suspend fun toggleFavorite(media: MediaItem): Result<Boolean>

    // --- Historique ---

    /** Récupère l'historique de visionnage local. */
    fun getWatchHistory(
        limit: Int = 50,
        offset: Int = 0,
    ): Flow<List<MediaItem>>

    // --- Recherche ---

    /** Recherche globale multi-serveurs. */
    suspend fun searchMedia(query: String): Result<List<MediaItem>>

    // --- Watchlist ---

    /** Synchronise la Watchlist globale Plex avec les favoris locaux. */
    suspend fun syncWatchlist(): Result<Unit>

    // --- Lecteur ---

    /** Met à jour la sélection des pistes (audio/sous-titres) sur le serveur. */
    suspend fun updateStreamSelection(
        serverId: String,
        partId: String,
        audioStreamId: String? = null,
        subtitleStreamId: String? = null,
    ): Result<Unit>
}
