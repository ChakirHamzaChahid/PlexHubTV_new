package com.chakir.plexhubtv.feature.search

import com.chakir.plexhubtv.core.model.MediaItem

enum class SearchState {
    Idle,
    Searching,
    Results,
    NoResults,
    Error,
}

/**
 * État de l'interface de Recherche.
 * Gère la requête en cours, l'état de la recherche (Idle, Searching, Results, etc.) et les résultats.
 * Les erreurs sont maintenant émises via errorEvents channel pour une gestion centralisée.
 */
data class SearchUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val results: List<MediaItem> = emptyList(),
)

sealed interface SearchAction {
    data class QueryChange(val query: String) : SearchAction

    data object ClearQuery : SearchAction

    data class OpenMedia(val media: MediaItem) : SearchAction
}
