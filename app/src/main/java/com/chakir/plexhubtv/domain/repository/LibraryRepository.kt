package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.LibrarySection
import kotlinx.coroutines.flow.Flow

/**
 * Interface d'accès au contenu des bibliothèques (Movies, Shows).
 *
 * Gère la pagination, le filtrage et le tri côté serveur.
 */
interface LibraryRepository {
    
    /** Récupère la liste des sections (bibliothèques) disponibles sur un serveur. */
    suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> 
    
    /**
     * Récupère le contenu paginé d'une bibliothèque.
     *
     * @param serverId ID du serveur cible.
     * @param libraryKey Clé de la section (ex: "1").
     * @param mediaType Type de média (Movie, Show).
     * @param filter Filtre API Plex (ex: "unwatched=1").
     * @param sort Critère de tri (ex: "addedAt:desc").
     * @param genre Liste de genres pour filtrer localement ou via API.
     * @param query Requête de recherche textuelle.
     * @return Un Flow de [PagingData] prêt pour l'UI Compose.
     */
    fun getLibraryContent(
        serverId: String, 
        libraryKey: String, 
        mediaType: com.chakir.plexhubtv.domain.model.MediaType,
        filter: String? = null, 
        sort: String? = null,
        isDescending: Boolean = false,
        genre: List<String>?,
        selectedServerId: String? = null,
        excludedServerIds: List<String> = emptyList(),
        initialKey: Int? = null,
        query: String? = null
    ): Flow<androidx.paging.PagingData<MediaItem>>
    
    /**
     * Calcule l'index du premier élément correspondant à une lettre (Fast Scroller).
     * Utilisé pour la navigation alphabétique rapide sur TV.
     */
    suspend fun getIndexOfFirstItem(
        type: com.chakir.plexhubtv.domain.model.MediaType,
        letter: String,
        filter: String?,
        sort: String?,
        genre: List<String>?,
        serverId: String?,
        selectedServerId: String? = null,
        excludedServerIds: List<String> = emptyList(),
        libraryKey: String?, // Optional, for context
        query: String?
    ): Int
}
