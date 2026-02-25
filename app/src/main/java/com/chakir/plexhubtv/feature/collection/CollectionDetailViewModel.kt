package com.chakir.plexhubtv.feature.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
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
        private val collectionId: String? = savedStateHandle["collectionId"]
        private val serverId: String? = savedStateHandle["serverId"]

        private val _uiState = MutableStateFlow(CollectionDetailUiState(isLoading = true))
        val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

        init {
            if (collectionId == null || serverId == null) {
                timber.log.Timber.e("CollectionDetailViewModel: missing required navigation args (collectionId=$collectionId, serverId=$serverId)")
                _uiState.update { it.copy(isLoading = false, error = "Invalid navigation arguments") }
            } else {
                loadCollection()
            }
        }

        private fun loadCollection() {
            val cid = collectionId ?: return
            val sid = serverId ?: return
            _uiState.update { it.copy(isLoading = true) }
            getCollectionUseCase(cid, sid).safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    timber.log.Timber.e(e, "CollectionDetailViewModel: loadCollection failed")
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load collection") }
                }
            ) { collection ->
                if (collection != null) {
                    _uiState.update { it.copy(isLoading = false, collection = collection) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Collection not found") }
                }
            }
        }
    }
