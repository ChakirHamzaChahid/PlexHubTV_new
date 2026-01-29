package com.chakir.plexhubtv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchAcrossServersUseCase: SearchAcrossServersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<SearchNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private var searchJob: Job? = null

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
        searchJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update { it.copy(searchState = SearchState.Searching, error = null) }
            delay(500L) // Debounce 500ms
            
            searchAcrossServersUseCase(query).collect { result ->
                val duration = System.currentTimeMillis() - startTime - 500L // Approx duration after delay
                result.fold(
                    onSuccess = { items ->
                        android.util.Log.i("METRICS", "SCREEN [Search] SUCCESS: query='$query' Load Duration=${duration}ms | Results=${items.size}")
                        _uiState.update { 
                            it.copy(
                                results = items,
                                searchState = if (items.isNotEmpty()) SearchState.Results else SearchState.NoResults
                            ) 
                        }
                    },
                    onFailure = { error ->
                        android.util.Log.e("METRICS", "SCREEN [Search] FAILED: query='$query' duration=${duration}ms error=${error.message}")
                        _uiState.update { 
                            it.copy(
                                searchState = SearchState.Error, 
                                error = error.message
                            ) 
                        }
                    }
                )
            }
        }
    }
}

sealed interface SearchNavigationEvent {
    data class NavigateToDetail(val ratingKey: String, val serverId: String) : SearchNavigationEvent
}
