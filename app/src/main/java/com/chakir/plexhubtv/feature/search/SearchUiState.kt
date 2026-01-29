package com.chakir.plexhubtv.feature.search

import com.chakir.plexhubtv.domain.model.MediaItem

enum class SearchState {
    Idle, Searching, Results, NoResults, Error
}

data class SearchUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val results: List<MediaItem> = emptyList(),
    val error: String? = null
)

sealed interface SearchAction {
    data class QueryChange(val query: String) : SearchAction
    data object ClearQuery : SearchAction
    data class OpenMedia(val media: MediaItem) : SearchAction
}
