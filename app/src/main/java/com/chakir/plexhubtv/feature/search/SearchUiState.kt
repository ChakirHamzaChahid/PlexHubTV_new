package com.chakir.plexhubtv.feature.search

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
@Immutable
data class SearchUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val results: ImmutableList<MediaItem> = persistentListOf(),
)

sealed interface SearchAction {
    data class QueryChange(val query: String) : SearchAction

    data object ClearQuery : SearchAction

    data object ExecuteSearch : SearchAction

    data class OpenMedia(val media: MediaItem) : SearchAction
}
