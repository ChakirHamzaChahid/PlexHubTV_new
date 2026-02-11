package com.chakir.plexhubtv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.usecase.SearchAcrossServersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour la Recherche Globale.
 * Exécute les recherches via [SearchAcrossServersUseCase] avec un mécanisme de "debounce" pour éviter les appels excessifs.
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchAcrossServersUseCase: SearchAcrossServersUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<SearchNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        private var searchJob: Job? = null

        init {
            Timber.d("SCREEN [Search]: Opened")
        }

        fun onAction(action: SearchAction) {
            when (action) {
                is SearchAction.QueryChange -> {
                    _uiState.update { it.copy(query = action.query) }
                    if (action.query.isBlank()) {
                        _uiState.update { it.copy(searchState = SearchState.Idle, results = emptyList()) }
                        searchJob?.cancel()
                    } else {
                        debouncedSearch(action.query)
                    }
                }
                is SearchAction.ClearQuery -> {
                    _uiState.update { it.copy(query = "", searchState = SearchState.Idle, results = emptyList()) }
                    searchJob?.cancel()
                }
                is SearchAction.OpenMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SearchNavigationEvent.NavigateToDetail(action.media.ratingKey, action.media.serverId))
                    }
                }
            }
        }

        private fun debouncedSearch(query: String) {
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    val startTime = System.currentTimeMillis()
                    _uiState.update { it.copy(searchState = SearchState.Searching) }
                    delay(500L) // Debounce 500ms

                    searchAcrossServersUseCase(query).collect { result ->
                        val duration = System.currentTimeMillis() - startTime - 500L // Approx duration after delay
                        result.fold(
                            onSuccess = { items ->
                                Timber.i("SCREEN [Search] SUCCESS: query='$query' Load Duration=${duration}ms | Results=${items.size}")
                                _uiState.update {
                                    it.copy(
                                        results = items,
                                        searchState = if (items.isNotEmpty()) SearchState.Results else SearchState.NoResults,
                                    )
                                }
                            },
                            onFailure = { error ->
                                Timber.e("SCREEN [Search] FAILED: query='$query' duration=${duration}ms error=${error.message}")
                                val appError = if (query.length < 2) {
                                    AppError.Search.QueryTooShort(error.message)
                                } else {
                                    AppError.Search.SearchFailed(error.message, error)
                                }
                                viewModelScope.launch {
                                    _errorEvents.send(appError)
                                }
                                _uiState.update {
                                    it.copy(searchState = SearchState.Error)
                                }
                            },
                        )
                    }
                }
        }
    }

sealed interface SearchNavigationEvent {
    data class NavigateToDetail(val ratingKey: String, val serverId: String) : SearchNavigationEvent
}
