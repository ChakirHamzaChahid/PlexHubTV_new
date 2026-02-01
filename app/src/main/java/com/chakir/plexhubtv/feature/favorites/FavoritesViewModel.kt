package com.chakir.plexhubtv.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<MediaItem> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel pour les favoris.
 * Utilise [GetFavoritesUseCase].
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            getFavoritesUseCase()
                .collect { items ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            favorites = items
                        )
                    }
                }
        }
    }
}
