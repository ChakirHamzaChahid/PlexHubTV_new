package com.chakir.plexhubtv.feature.favorites

import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.usecase.GetFavoriteActorsUseCase
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

enum class FavoritesCategory { MEDIA, ACTORS }

enum class FavoritesSortOption {
    DATE_ADDED, TITLE, YEAR, RATING;

    val defaultDescending: Boolean get() = this != TITLE
}

enum class ActorSortOption {
    DATE_ADDED, NAME;

    val defaultDescending: Boolean get() = this != NAME
}

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val category: FavoritesCategory = FavoritesCategory.MEDIA,
    // Media favorites
    val favorites: List<MediaItem> = emptyList(),
    val sortOption: FavoritesSortOption = FavoritesSortOption.DATE_ADDED,
    val isDescending: Boolean = true,
    // Actor favorites
    val favoriteActors: List<FavoriteActor> = emptyList(),
    val actorSortOption: ActorSortOption = ActorSortOption.DATE_ADDED,
    val isActorSortDescending: Boolean = true,
    val error: String? = null,
)

/**
 * ViewModel pour les favoris.
 * Utilise [GetFavoritesUseCase] et [GetFavoriteActorsUseCase].
 */
@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val getFavoritesUseCase: GetFavoritesUseCase,
        private val getFavoriteActorsUseCase: GetFavoriteActorsUseCase,
    ) : BaseViewModel() {

        private val _sortOption = MutableStateFlow(FavoritesSortOption.DATE_ADDED)
        private val _isDescending = MutableStateFlow(true)
        private val _category = MutableStateFlow(FavoritesCategory.MEDIA)
        private val _actorSortOption = MutableStateFlow(ActorSortOption.DATE_ADDED)
        private val _isActorSortDescending = MutableStateFlow(true)

        val uiState: StateFlow<FavoritesUiState> = combine(
            combine(
                getFavoritesUseCase().onStart { Timber.d("SCREEN [Favorites]: Loading media") },
                _sortOption,
                _isDescending,
            ) { items, sort, desc -> Triple(items, sort, desc) },
            combine(
                getFavoriteActorsUseCase().onStart { Timber.d("SCREEN [Favorites]: Loading actors") },
                _actorSortOption,
                _isActorSortDescending,
            ) { actors, sort, desc -> Triple(actors, sort, desc) },
            _category,
        ) { (mediaItems, mediaSortOption, mediaDesc), (actors, actorSort, actorDesc), category ->
            FavoritesUiState(
                isLoading = false,
                category = category,
                favorites = sortItems(mediaItems, mediaSortOption, mediaDesc),
                sortOption = mediaSortOption,
                isDescending = mediaDesc,
                favoriteActors = sortActors(actors, actorSort, actorDesc),
                actorSortOption = actorSort,
                isActorSortDescending = actorDesc,
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

        fun setCategory(category: FavoritesCategory) {
            _category.value = category
        }

        fun setSortOption(option: FavoritesSortOption) {
            if (_sortOption.value == option) {
                _isDescending.value = !_isDescending.value
            } else {
                _sortOption.value = option
                _isDescending.value = option.defaultDescending
            }
        }

        fun setActorSortOption(option: ActorSortOption) {
            if (_actorSortOption.value == option) {
                _isActorSortDescending.value = !_isActorSortDescending.value
            } else {
                _actorSortOption.value = option
                _isActorSortDescending.value = option.defaultDescending
            }
        }

        private fun sortItems(
            items: List<MediaItem>,
            sort: FavoritesSortOption,
            desc: Boolean,
        ): List<MediaItem> {
            val sorted = when (sort) {
                FavoritesSortOption.DATE_ADDED -> items.sortedWith(
                    compareBy<MediaItem> { it.addedAt ?: 0L }
                        .thenBy { it.title.lowercase() },
                )
                FavoritesSortOption.TITLE -> items.sortedBy { it.title.lowercase() }
                FavoritesSortOption.YEAR -> items.sortedWith(
                    compareBy<MediaItem> { it.year ?: 0 }
                        .thenBy { it.title.lowercase() },
                )
                FavoritesSortOption.RATING -> items.sortedWith(
                    compareBy<MediaItem> { it.audienceRating ?: it.rating ?: 0.0 }
                        .thenBy { it.title.lowercase() },
                )
            }
            return if (desc) sorted.reversed() else sorted
        }

        private fun sortActors(
            actors: List<FavoriteActor>,
            sort: ActorSortOption,
            desc: Boolean,
        ): List<FavoriteActor> {
            val sorted = when (sort) {
                ActorSortOption.DATE_ADDED -> actors.sortedWith(
                    compareBy<FavoriteActor> { it.addedAt }
                        .thenBy { it.name.lowercase() },
                )
                ActorSortOption.NAME -> actors.sortedBy { it.name.lowercase() }
            }
            return if (desc) sorted.reversed() else sorted
        }
    }
