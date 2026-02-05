package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.util.Resource
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Cas d'utilisation (UseCase) pour récupérer le contenu d'une bibliothèque Plex.
 *
 * Responsabilité :
 * - Faire le pont entre le ViewModel et le LibraryRepository.
 * - Fournir un flux paginé (PagingData) d'éléments multimédias.
 * - Gérer les filtres, le tri et la recherche.
 *
 * Dépendances :
 * - @see LibraryRepository : Pour l'accès aux données.
 */
class GetLibraryContentUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    /**
     * Exécute la requête de récupération du contenu.
     *
     * @param serverId Identifiant unique du serveur Plex cible.
     * @param libraryKey Clé ou ID de la bibliothèque (section) sur le serveur.
     * @param mediaType Type de média attendu (Movie, Show, etc.).
     * @param filter Filtre API Plex (ex: "unwatched", "newest"). Optionnel.
     * @param sort Critère de tri (ex: "titleSort", "addedAt"). Optionnel.
     * @param isDescending Si vrai, inverse l'ordre du tri.
     * @param genre Liste des genres pour filtrer localement ou via API. Optionnel.
     * @param selectedServerId ID d'un serveur spécifique si on filtre dans une vue unifiée.
     * @param initialKey Index partiel pour le saut (scroll) alphabétique.
     * @param query Chaîne de recherche pour filtrer les résultats.
     *
     * @return Flow<PagingData<MediaItem>> : Un flux de données paginées prêt pour l'UI Compose.
     */
    operator fun invoke(
        serverId: String, 
        libraryKey: String,
        mediaType: com.chakir.plexhubtv.domain.model.MediaType,
        filter: String? = null,
        sort: String? = null,
        isDescending: Boolean = false,
        genre: List<String>? = null,
        selectedServerId: String? = null,
        excludedServerIds: List<String> = emptyList(),
        initialKey: Int? = null,
        query: String? = null
    ): Flow<androidx.paging.PagingData<MediaItem>> = libraryRepository.getLibraryContent(
        serverId, libraryKey, mediaType, filter, sort, isDescending, genre, selectedServerId, excludedServerIds, initialKey, query
    )
}
