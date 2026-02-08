package com.chakir.plexhubtv.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.usecase.GetCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel
    @Inject
    constructor(
        private val getCollectionUseCase: GetCollectionUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val collectionId: String = checkNotNull(savedStateHandle["collectionId"])
        private val serverId: String = checkNotNull(savedStateHandle["serverId"])

        private val _uiState = MutableStateFlow(CollectionDetailUiState(isLoading = true))
        val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

        init {
            loadCollection()
        }

        private fun loadCollection() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                getCollectionUseCase(collectionId, serverId).collect { collection ->
                    if (collection != null) {
                        _uiState.update { it.copy(isLoading = false, collection = collection) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Collection not found") }
                    }
                }
            }
        }
    }
