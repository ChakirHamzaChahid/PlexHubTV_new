package com.chakir.plexhubtv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetLibraryContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.SavedStateHandle
import androidx.paging.cachedIn
import com.chakir.plexhubtv.domain.model.MediaType
import com.chakir.plexhubtv.domain.model.Server
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy

@HiltViewModel
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
class LibraryViewModel @Inject constructor(
    private val getLibraryContentUseCase: GetLibraryContentUseCase,
    private val getRecommendedContentUseCase: com.chakir.plexhubtv.domain.usecase.GetRecommendedContentUseCase,
    private val authRepository: com.chakir.plexhubtv.domain.repository.AuthRepository,
    private val libraryRepository: com.chakir.plexhubtv.domain.repository.LibraryRepository,
    private val mediaRepository: com.chakir.plexhubtv.domain.repository.MediaRepository,
    private val mediaDao: com.chakir.plexhubtv.core.database.MediaDao,
    private val syncRepository: com.chakir.plexhubtv.domain.repository.SyncRepository,
    private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
    private val connectionManager: com.chakir.plexhubtv.core.network.ConnectionManager,
    private val workManager: WorkManager,
    private val getLibraryIndexUseCase: com.chakir.plexhubtv.domain.usecase.GetLibraryIndexUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // État de l'UI exposé de manière immuable (StateFlow)
    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // Canal pour les événements de navigation (Effets uniques, ex: Toast, Navigation)
    private val _navigationEvents = Channel<LibraryNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()


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
    val pagedItems: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<MediaItem>> = _uiState
        .map { state -> 
            val serverFilterId = state.availableServersMap[state.selectedServerFilter]
            
            // Map UI Genre to database genre keyword list
            val genreQuery = if (state.selectedGenre != null && state.selectedGenre != "All") {
                com.chakir.plexhubtv.domain.model.GenreGrouping.GROUPS[state.selectedGenre] ?: listOf(state.selectedGenre)
            } else null

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
                query = state.searchQuery.ifBlank { null }
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
                 query = params.query
            ).also {
                 android.util.Log.d("METRICS", "DATA [Library] Loading content: Library=${params.libraryId} Type=${params.mediaType} Filter=${params.filter} Sort=${params.sort} Server=${params.serverId}")
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
        val query: String? = null
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
                lastFocusedId = restoredFocusId
            ) 
        }
        
        // Load metadata (servers, genres, counts) - separate from Paging data
        viewModelScope.launch {
            loadMetadata(initialMediaType)
            
            // Collect excluded servers
            launch {
                settingsRepository.excludedServerIds.collect { excluded ->
                    _uiState.update { it.copy(excludedServerIds = excluded) }
                }
            }

            // Apply Default Server Preference after metadata (to ensure map is ready or concurrently)
            val defaultServer = settingsRepository.defaultServer.firstOrNull()
            if (!defaultServer.isNullOrEmpty() && defaultServer != "all") {
                 _uiState.update { 
                     // Only apply if we haven't manually selected one (though init implies fresh)
                     it.copy(selectedServerFilter = defaultServer) 
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
        android.util.Log.d("METRICS", "SCREEN [Library]: Loading metadata start for $mediaType")
        try {
            val typeFilter = if (mediaType == MediaType.Movie) "movie" else "show"
            
            // Récupérations des serveurs depuis le repo Auth
            val servers = authRepository.getServers().getOrNull() ?: emptyList()
            val serverNames = servers.map { it.name }
            val serverMap = servers.associate { it.name to it.clientIdentifier }
            
            // Utilisation des groupes de genres définis statiquement (UI_LABELS)
            val allGenres = com.chakir.plexhubtv.domain.model.GenreGrouping.UI_LABELS
            
            // Récupération des compteurs depuis la BDD Room
            val totalCount = mediaDao.getUniqueCountByType(typeFilter)
            val rawCount = mediaDao.getRawCountByType(typeFilter)
            
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.i("METRICS", "SCREEN [Library] SUCCESS: Load Duration=${duration}ms | UniqueCount=$totalCount | RawCount=$rawCount")

            if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                android.util.Log.d("LibraryVM", "Loaded metadata: ${serverNames.size} servers, ${allGenres.size} genre groups")
                android.util.Log.i("SyncStats", "STATS for $typeFilter: Raw Items=$rawCount | Unique Items=$totalCount | Deduplication Ratio=${String.format("%.2f", rawCount.toFloat() / totalCount.toFloat())}x")
            }
            
            _uiState.update { 
                it.copy(
                    availableServers = serverNames,
                    availableServersMap = serverMap,
                    availableGenres = allGenres,
                    totalItems = totalCount,
                    isLoading = false
                )
            }

            // Si la bibliothèque semble vide ou corrompue, déclencher une synchro arrière-plan
            if (totalCount < 100 && _uiState.value.selectedServerId == "all") {
                android.util.Log.i("LibraryVM", "Low item count ($totalCount), triggering background sync...")
                triggerBackgroundSync()
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("METRICS", "SCREEN [Library] FAILED: duration=${duration}ms error=${e.message}")
            if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                android.util.Log.e("LibraryVM", "Error loading metadata: ${e.message}")
            }
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /**
     * Lance une synchronisation complète via WorkManager.
     * Utilise une contrainte réseau (CONNECTED).
     */
    private fun triggerBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<com.chakir.plexhubtv.work.LibrarySyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "LibrarySync_Initial",
            ExistingWorkPolicy.REPLACE, // Force new sync if stuck
            syncRequest
        )
    }

    /**
     * Traite les actions de l'interface utilisateur (MVI-like).
     */
    fun onAction(action: LibraryAction) {
        android.util.Log.d("METRICS", "ACTION [Library] Action=${action.javaClass.simpleName}")
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
                    if (!newVisible) it.copy(isSearchVisible = false, searchQuery = "")
                    else it.copy(isSearchVisible = true)
                }
            }
            is LibraryAction.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = action.query) }
            }
            is LibraryAction.SelectGenre -> {
                _uiState.update { it.copy(selectedGenre = action.genre) }
            }
            is LibraryAction.SelectServerFilter -> {
                _uiState.update { it.copy(selectedServerFilter = action.serverId) }
            }
            is LibraryAction.OnItemFocused -> {
                savedStateHandle["lastFocusedId"] = action.item.ratingKey
                _uiState.update { it.copy(lastFocusedId = action.item.ratingKey) }
            }
            is LibraryAction.JumpToLetter -> {
                viewModelScope.launch {
                    val state = _uiState.value
                    android.util.Log.d("LibraryViewModel", "JumpToLetter: ${action.letter}")
                    
                    // Calculate filter params similarly to pagedItems
                    val serverFilterId = state.availableServersMap[state.selectedServerFilter]
                    val genreQuery = if (state.selectedGenre != null && state.selectedGenre != "All") {
                        com.chakir.plexhubtv.domain.model.GenreGrouping.GROUPS[state.selectedGenre] ?: listOf(state.selectedGenre)
                    } else null
                    
                    val idx = getLibraryIndexUseCase(
                        type = state.mediaType,
                        letter = action.letter,
                        filter = state.currentFilter,
                        sort = state.currentSort,
                        genre = genreQuery,
                        serverId = state.selectedServerId,
                        selectedServerId = serverFilterId,
                        excludedServerIds = state.excludedServerIds.toList(),
                        libraryKey = state.selectedLibraryId,
                        query = state.searchQuery.ifBlank { null }
                    )
                    
                    android.util.Log.d("LibraryViewModel", "Calculated index for ${action.letter}: $idx")
                    
                    if (idx >= 0) {
                        // Force reload with new initial Key
                        savedStateHandle["initialScrollIndex"] = idx
                        _uiState.update { it.copy(initialScrollIndex = idx) }
                         android.util.Log.d("LibraryViewModel", "Updated initialScrollIndex to $idx. Triggering reload and scroll.")
                         _navigationEvents.send(LibraryNavigationEvent.ScrollToItem(idx))
                    } else {
                         android.util.Log.w("LibraryViewModel", "Index < 0, ignoring scroll")
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
