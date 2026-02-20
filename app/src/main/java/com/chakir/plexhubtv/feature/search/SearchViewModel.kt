package com.chakir.plexhubtv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

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
                    // Only update the query text, don't trigger search automatically
                    _uiState.update { it.copy(query = action.query) }
                    if (action.query.isBlank()) {
                        _uiState.update { it.copy(searchState = SearchState.Idle, results = emptyList()) }
                        searchJob?.cancel()
                    }
                }
                is SearchAction.ClearQuery -> {
                    _uiState.update { it.copy(query = "", searchState = SearchState.Idle, results = emptyList()) }
                    searchJob?.cancel()
                }
                is SearchAction.ExecuteSearch -> {
                    // Trigger search only when user explicitly submits
                    val query = _uiState.value.query
                    if (query.isNotBlank()) {
                        performSearch(query)
                    }
                }
                is SearchAction.OpenMedia -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SearchNavigationEvent.NavigateToDetail(action.media.ratingKey, action.media.serverId))
                    }
                }
            }
        }

        private fun performSearch(query: String) {
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    val startTime = System.currentTimeMillis()
                    _uiState.update { it.copy(searchState = SearchState.Searching) }

                    Firebase.analytics.logEvent("search") {
                        param(FirebaseAnalytics.Param.SEARCH_TERM, query.take(100))
                    }

                    searchAcrossServersUseCase(query).safeCollectIn(
                        scope = viewModelScope,
                        onError = { error ->
                            val duration = System.currentTimeMillis() - startTime
                            Timber.e(error, "SearchViewModel: searchAcrossServersUseCase failed")
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
                        }
                    ) { result ->
                        val duration = System.currentTimeMillis() - startTime
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
