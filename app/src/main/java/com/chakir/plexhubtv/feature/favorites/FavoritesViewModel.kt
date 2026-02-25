package com.chakir.plexhubtv.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetFavoritesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

enum class FavoritesSortOption {
    DATE_ADDED, TITLE, YEAR, RATING;

    val defaultDescending: Boolean get() = this != TITLE
}

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<MediaItem> = emptyList(),
    val sortOption: FavoritesSortOption = FavoritesSortOption.DATE_ADDED,
    val isDescending: Boolean = true,
    val error: String? = null,
)

/**
 * ViewModel pour les favoris.
 * Utilise [GetFavoritesUseCase].
 */
@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val getFavoritesUseCase: GetFavoritesUseCase,
    ) : ViewModel() {

        private val _sortOption = MutableStateFlow(FavoritesSortOption.DATE_ADDED)
        private val _isDescending = MutableStateFlow(true)

        val uiState: StateFlow<FavoritesUiState> = combine(
            getFavoritesUseCase().onStart { Timber.d("SCREEN [Favorites]: Loading start") },
            _sortOption,
            _isDescending,
        ) { items, sort, desc ->
            val sorted = sortItems(items, sort, desc)
            FavoritesUiState(
                isLoading = false,
                favorites = sorted,
                sortOption = sort,
                isDescending = desc,
            )
        }.catch { e ->
            Timber.e(e, "FavoritesViewModel: loadFavorites failed")
            emit(FavoritesUiState(isLoading = false, error = e.message ?: "Failed to load favorites"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FavoritesUiState(),
        )

        init {
            Timber.d("SCREEN [Favorites]: Opened")
        }

        fun setSortOption(option: FavoritesSortOption) {
            if (_sortOption.value == option) {
                _isDescending.value = !_isDescending.value
            } else {
                _sortOption.value = option
                _isDescending.value = option.defaultDescending
            }
        }

        private fun sortItems(
            items: List<MediaItem>,
            sort: FavoritesSortOption,
            desc: Boolean,
        ): List<MediaItem> {
            val sorted = when (sort) {
                FavoritesSortOption.DATE_ADDED -> if (desc) items else items.reversed()
                FavoritesSortOption.TITLE -> items.sortedBy { it.title.lowercase() }
                FavoritesSortOption.YEAR -> items.sortedBy { it.year ?: 0 }
                FavoritesSortOption.RATING -> items.sortedBy { it.audienceRating ?: it.rating ?: 0.0 }
            }
            return if (sort != FavoritesSortOption.DATE_ADDED && desc) sorted.reversed() else sorted
        }
    }
