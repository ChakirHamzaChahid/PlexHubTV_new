package com.chakir.plexhubtv.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.chakir.plexhubtv.core.common.safeCollectIn
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.usecase.GetLibraryContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel principal pour l'écran de bibliothèque (Library).
 *
 * Responsabilités :
 * - Gérer l'état de l'interface (LibraryUiState).
 * - Charger les métadonnées (serveurs, genres).
 * - Exposer le flux de données paginées (pagedItems).
 * - Gérer les événements utilisateur (Filtres, Tri, Navigation).
 *
 * Pattern : MVVM avec StateFlow pour l'état et Channel pour les événements uniques.
 */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val getLibraryContentUseCase: GetLibraryContentUseCase,
        private val getRecommendedContentUseCase: com.chakir.plexhubtv.domain.usecase.GetRecommendedContentUseCase,
        private val authRepository: com.chakir.plexhubtv.domain.repository.AuthRepository,
        private val libraryRepository: com.chakir.plexhubtv.domain.repository.LibraryRepository,
        private val mediaDao: com.chakir.plexhubtv.core.database.MediaDao,
        private val syncRepository: com.chakir.plexhubtv.domain.repository.SyncRepository,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
        private val connectionManager: com.chakir.plexhubtv.core.network.ConnectionManager,
        private val workManager: WorkManager,
        private val getLibraryIndexUseCase: com.chakir.plexhubtv.domain.usecase.GetLibraryIndexUseCase,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // État de l'UI exposé de manière immuable (StateFlow)
        private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
        val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

        // Canal pour les événements de navigation (Effets uniques, ex: Toast, Navigation)
        private val _navigationEvents = Channel<LibraryNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        private val _errorEvents = Channel<AppError>()
        val errorEvents = _errorEvents.receiveAsFlow()

        /**
         * Flux réactif PAGINÉ des médias.
         *
         * Logique :
         * 1. Observe les changements de _uiState (filtres, tri, bibliothèque).
         * 2. Transforme l'état UI en paramètres de requête (FilterParams).
         * 3. Utilise `distinctUntilChanged` pour éviter de recharger si les paramètres clés n'ont pas changé.
         * 4. `flatMapLatest` annule la requête précédente et lance la nouvelle via le UseCase.
         * 5. `cachedIn` maintient le cache Paging dans le scope du ViewModel pour survivre aux changements de config.
         */
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val pagedItems: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<MediaItem>> =
            _uiState
                .map { state ->
                    val serverFilterId = state.availableServersMap[state.selectedServerFilter]

                    // Map UI Genre to database genre keyword list
                    val genreQuery =
                        if (state.selectedGenre != null && state.selectedGenre != "All") {
                            com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.selectedGenre] ?: listOf(state.selectedGenre)
                        } else {
                            null
                        }

                    FilterParams(
                        filter = state.currentFilter,
                        sort = state.currentSort,
                        isDescending = state.isSortDescending,
                        libraryId = state.selectedLibraryId ?: "default",
                        mediaType = state.mediaType,
                        genre = genreQuery,
                        serverId = state.selectedServerId ?: "all",
                        serverFilterId = serverFilterId,
                        excludedServerIds = state.excludedServerIds.toList(),
                        initialScrollIndex = state.initialScrollIndex,
                        query = state.searchQuery.ifBlank { null },
                    )
                }
                .distinctUntilChanged { old, new ->
                    // Custom equals check
                    old == new
                }
                // Debounce removed: refresh is now explicitly triggered in UI via LaunchedEffect
                .flatMapLatest { params ->
                    getLibraryContentUseCase(
                        serverId = params.serverId,
                        libraryKey = params.libraryId,
                        mediaType = params.mediaType,
                        filter = params.filter,
                        sort = params.sort,
                        isDescending = params.isDescending,
                        genre = params.genre,
                        selectedServerId = params.serverFilterId,
                        excludedServerIds = params.excludedServerIds,
                        initialKey = params.initialScrollIndex,
                        query = params.query,
                    ).also {
                        Timber.d(
                            "DATA [Library] Loading content: Library=${params.libraryId} Type=${params.mediaType} Filter=${params.filter} Sort=${params.sort} Server=${params.serverId}",
                        )
                    }
                }
                .cachedIn(viewModelScope)

        data class FilterParams(
            val filter: String,
            val sort: String,
            val isDescending: Boolean,
            val libraryId: String,
            val mediaType: MediaType,
            val genre: List<String>?,
            val serverId: String,
            val serverFilterId: String?,
            val excludedServerIds: List<String> = emptyList(),
            val initialScrollIndex: Int? = null,
            val query: String? = null,
        )

        /** Params for filtered count (excludes initialScrollIndex to avoid unnecessary recomputation). */
        private data class CountParams(
            val filter: String,
            val sort: String,
            val isDescending: Boolean,
            val libraryId: String,
            val mediaType: MediaType,
            val genre: List<String>?,
            val serverId: String,
            val serverFilterId: String?,
            val excludedServerIds: List<String>,
            val query: String?,
        )

        init {
            val typeArg = savedStateHandle.get<String>("mediaType") ?: "movie"
            val initialMediaType = MediaType.values().find { it.name.equals(typeArg, ignoreCase = true) } ?: MediaType.Movie

            // RESTORE STATE
            val restoredLibraryId = savedStateHandle.get<String>("selectedLibraryId")
            val restoredItemIndex = savedStateHandle.get<Int>("initialScrollIndex")
            val restoredFocusId = savedStateHandle.get<String>("lastFocusedId")

            _uiState.update {
                it.copy(
                    mediaType = initialMediaType,
                    isLoading = true,
                    selectedLibraryId = restoredLibraryId,
                    initialScrollIndex = restoredItemIndex,
                    lastFocusedId = restoredFocusId,
                )
            }

            // Load metadata (servers, genres, counts) - separate from Paging data
            viewModelScope.launch {
                loadMetadata(initialMediaType)

                // Collect excluded servers
                settingsRepository.excludedServerIds.safeCollectIn(
                    scope = viewModelScope,
                    onError = { e ->
                        Timber.e(e, "LibraryViewModel: excludedServerIds collection failed")
                    }
                ) { excluded ->
                    _uiState.update { it.copy(excludedServerIds = excluded) }
                }

                // Restore persisted filter preferences
                val savedSort = settingsRepository.librarySort.firstOrNull() ?: "Title"
                val savedSortDesc = settingsRepository.librarySortDescending.firstOrNull() ?: false
                val savedGenre = settingsRepository.libraryGenre.firstOrNull()
                val savedServerFilter = settingsRepository.libraryServerFilter.firstOrNull()

                // Use saved server filter, fallback to default server preference
                val serverFilter = savedServerFilter
                    ?: settingsRepository.defaultServer.firstOrNull()?.takeIf { it.isNotEmpty() && it != "all" }

                _uiState.update {
                    it.copy(
                        currentSort = savedSort,
                        isSortDescending = savedSortDesc,
                        selectedGenre = savedGenre,
                        selectedServerFilter = serverFilter,
                    )
                }
            }

            // Observe filter changes to compute filtered item count
            viewModelScope.launch {
                _uiState
                    .map { state ->
                        val serverFilterId = state.availableServersMap[state.selectedServerFilter]
                        val genreQuery =
                            if (state.selectedGenre != null && state.selectedGenre != "All") {
                                com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.selectedGenre] ?: listOf(state.selectedGenre)
                            } else {
                                null
                            }
                        CountParams(
                            filter = state.currentFilter,
                            sort = state.currentSort,
                            isDescending = state.isSortDescending,
                            libraryId = state.selectedLibraryId ?: "default",
                            mediaType = state.mediaType,
                            genre = genreQuery,
                            serverId = state.selectedServerId ?: "all",
                            serverFilterId = serverFilterId,
                            excludedServerIds = state.excludedServerIds.toList(),
                            query = state.searchQuery.ifBlank { null },
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest { params ->
                        try {
                            val count = libraryRepository.getFilteredCount(
                                type = params.mediaType,
                                filter = params.filter,
                                sort = params.sort,
                                isDescending = params.isDescending,
                                genre = params.genre,
                                serverId = params.serverId,
                                selectedServerId = params.serverFilterId,
                                excludedServerIds = params.excludedServerIds,
                                libraryKey = params.libraryId,
                                query = params.query,
                            )
                            _uiState.update { it.copy(filteredItems = count) }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to compute filtered count")
                        }
                    }
            }
        }

        /**
         * Charge les métadonnées globales (non paginées) :
         * - Liste des serveurs
         * - Genres disponibles
         * - Statistiques (nombre total d'éléments)
         *
         * @param mediaType Type de média (Film/Série) pour adapter les requêtes.
         */
        private suspend fun loadMetadata(mediaType: MediaType) {
            val startTime = System.currentTimeMillis()
            Timber.d("SCREEN [Library]: Loading metadata start for $mediaType")
            try {
                val typeFilter = if (mediaType == MediaType.Movie) "movie" else "show"

                // Récupérations des serveurs depuis le repo Auth
                val servers = authRepository.getServers().getOrNull() ?: emptyList()
                val serverNames = servers.map { it.name }
                val serverMap = servers.associate { it.name to it.clientIdentifier }

                // Utilisation des groupes de genres définis statiquement (UI_LABELS)
                val allGenres = com.chakir.plexhubtv.core.model.GenreGrouping.UI_LABELS

                // Récupération des compteurs depuis la BDD Room
                val totalCount = mediaDao.getUniqueCountByType(typeFilter)
                val rawCount = mediaDao.getRawCountByType(typeFilter)

                val duration = System.currentTimeMillis() - startTime
                Timber.i("SCREEN [Library] SUCCESS: Load Duration=${duration}ms | UniqueCount=$totalCount | RawCount=$rawCount")

                if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                    Timber.d("Loaded metadata: ${serverNames.size} servers, ${allGenres.size} genre groups")
                    Timber.i(
                        "STATS for $typeFilter: Raw Items=$rawCount | Unique Items=$totalCount | Deduplication Ratio=${String.format(
                            "%.2f",
                            rawCount.toFloat() / totalCount.toFloat(),
                        )}x",
                    )
                }

                _uiState.update {
                    it.copy(
                        availableServers = serverNames,
                        availableServersMap = serverMap,
                        availableGenres = allGenres,
                        totalItems = totalCount,
                        isLoading = false,
                    )
                }

                // Si la bibliothèque semble vide ou corrompue, déclencher une synchro arrière-plan
                if (totalCount < 100 && _uiState.value.selectedServerId == "all") {
                    Timber.i("Low item count ($totalCount), triggering background sync...")
                    triggerBackgroundSync()
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Timber.e("SCREEN [Library] FAILED: duration=${duration}ms error=${e.message}")
                if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                    Timber.e("Error loading metadata: ${e.message}")
                }

                // Emit error via channel for snackbar display
                viewModelScope.launch {
                    _errorEvents.send(e.toAppError())
                }

                _uiState.update { it.copy(isLoading = false) }
            }
        }

        /**
         * Lance une synchronisation complète via WorkManager.
         * Utilise une contrainte réseau (CONNECTED).
         */
        private fun triggerBackgroundSync() {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val syncRequest =
                OneTimeWorkRequestBuilder<com.chakir.plexhubtv.work.LibrarySyncWorker>()
                    .setConstraints(constraints)
                    .build()

            workManager.enqueueUniqueWork(
                "LibrarySync_Initial",
                ExistingWorkPolicy.KEEP, // Don't restart if already running
                syncRequest,
            )
        }

        /**
         * Traite les actions de l'interface utilisateur (MVI-like).
         */
        fun onAction(action: LibraryAction) {
            Timber.d("ACTION [Library] Action=${action.javaClass.simpleName}")
            when (action) {
                is LibraryAction.SelectTab -> {
                    _uiState.update { it.copy(selectedTab = action.tab) }
                }
                is LibraryAction.ChangeViewMode -> {
                    _uiState.update { it.copy(viewMode = action.mode) }
                }
                is LibraryAction.LoadNextPage -> {
                    // Handled by Paging 3 automatically
                }
                is LibraryAction.Refresh -> {
                    // Handled by PagingAdapter.refresh() in UI
                }
                is LibraryAction.OpenMedia -> {
                    // Sync lastFocusedId to UiState before navigation so focus can be restored on back
                    _uiState.update { it.copy(lastFocusedId = action.media.ratingKey) }
                    viewModelScope.launch {
                        _navigationEvents.send(LibraryNavigationEvent.NavigateToDetail(action.media.ratingKey, action.media.serverId))
                    }
                }
                is LibraryAction.SelectLibrary -> {
                    savedStateHandle["selectedLibraryId"] = action.libraryId
                    _uiState.update { it.copy(selectedLibraryId = action.libraryId) }
                }
                is LibraryAction.ApplyFilter -> {
                    _uiState.update { it.copy(currentFilter = action.filter) }
                }
                is LibraryAction.ApplySort -> {
                    _uiState.update { it.copy(currentSort = action.sort, isSortDescending = action.isDescending, isSortDialogOpen = false) }
                    viewModelScope.launch { settingsRepository.saveLibrarySort(action.sort, action.isDescending) }
                }
                is LibraryAction.OpenServerFilter -> _uiState.update { it.copy(isServerFilterOpen = true) }
                is LibraryAction.CloseServerFilter -> _uiState.update { it.copy(isServerFilterOpen = false) }
                is LibraryAction.OpenGenreFilter -> _uiState.update { it.copy(isGenreFilterOpen = true) }
                is LibraryAction.CloseGenreFilter -> _uiState.update { it.copy(isGenreFilterOpen = false) }
                is LibraryAction.OpenSortDialog -> _uiState.update { it.copy(isSortDialogOpen = true) }
                is LibraryAction.CloseSortDialog -> _uiState.update { it.copy(isSortDialogOpen = false) }
                is LibraryAction.ToggleSearch -> {
                    _uiState.update {
                        val newVisible = !it.isSearchVisible
                        if (!newVisible) {
                            it.copy(isSearchVisible = false, searchQuery = "")
                        } else {
                            it.copy(isSearchVisible = true)
                        }
                    }
                }
                is LibraryAction.UpdateSearchQuery -> {
                    _uiState.update { it.copy(searchQuery = action.query) }
                }
                is LibraryAction.SelectGenre -> {
                    _uiState.update { it.copy(selectedGenre = action.genre) }
                    viewModelScope.launch { settingsRepository.saveLibraryGenre(action.genre) }
                }
                is LibraryAction.SelectServerFilter -> {
                    _uiState.update { it.copy(selectedServerFilter = action.serverId) }
                    viewModelScope.launch { settingsRepository.saveLibraryServerFilter(action.serverId) }
                }
                is LibraryAction.OnItemFocused -> {
                    // PERFORMANCE FIX: Only update SavedStateHandle, DO NOT update _uiState.
                    // Updating _uiState triggers a full screen recomposition on every D-pad move.
                    savedStateHandle["lastFocusedId"] = action.item.ratingKey
                    // _uiState.update { it.copy(lastFocusedId = action.item.ratingKey) } 
                }
                is LibraryAction.JumpToLetter -> {
                    viewModelScope.launch {
                        val state = _uiState.value
                        Timber.d("JumpToLetter: ${action.letter}")

                        // Calculate filter params similarly to pagedItems
                        val serverFilterId = state.availableServersMap[state.selectedServerFilter]
                        val genreQuery =
                            if (state.selectedGenre != null && state.selectedGenre != "All") {
                                com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.selectedGenre] ?: listOf(state.selectedGenre)
                            } else {
                                null
                            }

                        val idx =
                            getLibraryIndexUseCase(
                                type = state.mediaType,
                                letter = action.letter,
                                filter = state.currentFilter,
                                sort = state.currentSort,
                                genre = genreQuery,
                                serverId = state.selectedServerId,
                                selectedServerId = serverFilterId,
                                excludedServerIds = state.excludedServerIds.toList(),
                                libraryKey = state.selectedLibraryId,
                                query = state.searchQuery.ifBlank { null },
                            )

                        Timber.d("Calculated index for ${action.letter}: $idx")

                        if (idx >= 0) {
                            // Force reload with new initial Key
                            savedStateHandle["initialScrollIndex"] = idx
                            _uiState.update { it.copy(initialScrollIndex = idx) }
                            Timber.d("Updated initialScrollIndex to $idx. Triggering reload and scroll.")
                            _navigationEvents.send(LibraryNavigationEvent.ScrollToItem(idx))
                        } else {
                            Timber.w("Index < 0, ignoring scroll")
                        }
                    }
                }
            }
        }

        // loadContent removed as Paging handles data loading
    }

sealed interface LibraryNavigationEvent {
    data class NavigateToDetail(val ratingKey: String, val serverId: String) : LibraryNavigationEvent

    data class ScrollToItem(val index: Int) : LibraryNavigationEvent
}
