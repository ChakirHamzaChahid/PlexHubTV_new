package com.chakir.plexhubtv.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.chakir.plexhubtv.domain.usecase.FilterContentByAgeUseCase
import com.chakir.plexhubtv.domain.usecase.SearchAcrossServersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.chakir.plexhubtv.domain.service.AnalyticsService

/**
 * ViewModel pour la Recherche Globale.
 * Exécute les recherches via [SearchAcrossServersUseCase] avec un mécanisme de "debounce" pour éviter les appels excessifs.
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchAcrossServersUseCase: SearchAcrossServersUseCase,
        private val profileRepository: ProfileRepository,
        private val filterContentByAgeUseCase: FilterContentByAgeUseCase,
        private val analyticsService: AnalyticsService,
        private val savedStateHandle: SavedStateHandle,
    ) : BaseViewModel() {
        private val _uiState = MutableStateFlow(
            SearchUiState(query = savedStateHandle.get<String>("search_query") ?: "")
        )
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<SearchNavigationEvent>(Channel.BUFFERED)
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private var searchJob: Job? = null

        init {
            Timber.d("SCREEN [Search]: Opened")

            // ISSUE #114 FIX: Add automatic search with 400ms debounce
            // Collect query changes, debounce for 400ms, then trigger search automatically
            viewModelScope.launch {
                uiState
                    .map { it.query }
                    .distinctUntilChanged() // Only trigger if query actually changed
                    .debounce(400) // Wait 400ms after last keystroke
                    .filter { it.length >= 2 } // Only search if at least 2 characters
                    .collect { query ->
                        Timber.d("SEARCH [Debounce]: Auto-triggering search for query='$query'")
                        performSearch(query)
                    }
            }
        }

        fun onAction(action: SearchAction) {
            when (action) {
                is SearchAction.QueryChange -> {
                    _uiState.update { it.copy(query = action.query) }
                    savedStateHandle["search_query"] = action.query
                    if (action.query.isBlank()) {
                        _uiState.update { it.copy(searchState = SearchState.Idle, results = emptyList()) }
                        searchJob?.cancel()
                    }
                }
                is SearchAction.ClearQuery -> {
                    _uiState.update { it.copy(query = "", searchState = SearchState.Idle, results = emptyList()) }
                    savedStateHandle["search_query"] = ""
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

                    analyticsService.logEvent("search", mapOf("search_term" to query.take(100)))

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
                                emitError(appError)
                            }
                            _uiState.update {
                                it.copy(searchState = SearchState.Error)
                            }
                        }
                    ) { result ->
                        val duration = System.currentTimeMillis() - startTime
                        result.fold(
                            onSuccess = { items ->
                                val activeProfile = profileRepository.getActiveProfile()
                                val filtered = if (activeProfile != null) {
                                    filterContentByAgeUseCase(items, activeProfile)
                                } else {
                                    items
                                }
                                Timber.i("SCREEN [Search] SUCCESS: query='$query' Load Duration=${duration}ms | Results=${filtered.size}")
                                _uiState.update {
                                    it.copy(
                                        results = filtered,
                                        searchState = if (filtered.isNotEmpty()) SearchState.Results else SearchState.NoResults,
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
                                    emitError(appError)
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
