package com.chakir.plexhubtv.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.chakir.plexhubtv.core.model.AgeRating
import com.chakir.plexhubtv.core.common.safeCollectIn
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.chakir.plexhubtv.domain.usecase.GetLibraryContentUseCase
import com.chakir.plexhubtv.feature.common.BaseViewModel
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
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
        private val profileRepository: ProfileRepository,
        private val authRepository: com.chakir.plexhubtv.domain.repository.AuthRepository,
        private val libraryRepository: com.chakir.plexhubtv.domain.repository.LibraryRepository,
        private val mediaDao: com.chakir.plexhubtv.core.database.MediaDao,
        private val syncRepository: com.chakir.plexhubtv.domain.repository.SyncRepository,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
        private val connectionManager: com.chakir.plexhubtv.core.network.ConnectionManager,
        private val workManager: WorkManager,
        private val getLibraryIndexUseCase: com.chakir.plexhubtv.domain.usecase.GetLibraryIndexUseCase,
        private val xtreamAccountRepository: com.chakir.plexhubtv.domain.repository.XtreamAccountRepository,
        private val backendRepository: com.chakir.plexhubtv.domain.repository.BackendRepository,
        private val jellyfinServerRepository: com.chakir.plexhubtv.domain.repository.JellyfinServerRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : BaseViewModel() {
        // État de l'UI exposé de manière immuable (StateFlow)
        private val _uiState = MutableStateFlow(LibraryUiState(display = LibraryDisplayState(isLoading = true)))
        val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

        // Canal pour les événements de navigation (Effets uniques, ex: Toast, Navigation)
        private val _navigationEvents = Channel<LibraryNavigationEvent>(Channel.BUFFERED)
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
        val pagedItems: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<MediaItem>> =
            _uiState
                .map { state ->
                    val serverFilterId = state.filter.availableServersMap[state.filter.selectedServerFilter]

                    // Map UI Genre to database genre keyword list
                    val genreQuery =
                        if (state.filter.selectedGenre != null && state.filter.selectedGenre != "All") {
                            com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.filter.selectedGenre] ?: listOf(state.filter.selectedGenre)
                        } else {
                            null
                        }

                    FilterParams(
                        filter = state.filter.currentFilter,
                        sort = state.filter.currentSort,
                        isDescending = state.filter.isSortDescending,
                        libraryId = state.selection.selectedLibraryId ?: "default",
                        mediaType = state.display.mediaType,
                        genre = genreQuery,
                        serverId = state.selection.selectedServerId ?: "all",
                        serverFilterId = serverFilterId,
                        excludedServerIds = state.filter.excludedServerIds.toList(),
                        initialScrollIndex = state.scroll.initialScrollIndex,
                        query = state.filter.searchQuery.ifBlank { null },
                    )
                }
                .distinctUntilChanged { old, new ->
                    // Ignore initialScrollIndex: it's a navigation param, not a filter param.
                    // Changing scroll position should NOT invalidate the PagingData flow.
                    old.copy(initialScrollIndex = null) == new.copy(initialScrollIndex = null)
                }
                // Debounce removed: refresh is now explicitly triggered in UI via LaunchedEffect
                .flatMapLatest { params ->
                    Timber.d(
                        "DATA [Library] Loading content: Library=${params.libraryId} Type=${params.mediaType} Filter=${params.filter} Sort=${params.sort} Server=${params.serverId}",
                    )
                    // Compute maxAge for SQL-level content filtering (avoids iterating pagingData.filter{} per page)
                    // Only filter when: kids profile (capped at 7) or explicit PARENTAL_* age rating.
                    // GENERAL and ADULT both mean "no filtering" — GENERAL was the old broken default
                    // (never user-configurable since the dialog doesn't expose age rating selection).
                    val profile = profileRepository.getActiveProfile()
                    val maxAge = if (profile != null) {
                        when {
                            profile.isKidsProfile -> 7
                            profile.ageRating == AgeRating.PARENTAL_7 -> 7
                            profile.ageRating == AgeRating.PARENTAL_13 -> 13
                            profile.ageRating == AgeRating.PARENTAL_16 -> 16
                            else -> null // GENERAL or ADULT → no filtering
                        }
                    } else null

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
                        maxAgeRating = maxAge,
                    )
                }
                .cachedIn(viewModelScope)
                .also {
                    if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                        var emissionCount = 0
                        viewModelScope.launch {
                            it.onEach {
                                emissionCount++
                                if (emissionCount > 1) {
                                    Timber.w("PERF [Library] PagingData re-emission #$emissionCount (possible background media table write)")
                                }
                            }.collect {} // Side-effect only collector for debug logging
                        }
                    }
                }

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
            val restoredPendingScroll = savedStateHandle.get<Int>("pendingScrollRestore")

            _uiState.update {
                it.copy(
                    display = it.display.copy(mediaType = initialMediaType, isLoading = true),
                    selection = it.selection.copy(selectedLibraryId = restoredLibraryId),
                    scroll = it.scroll.copy(
                        initialScrollIndex = restoredItemIndex,
                        lastFocusedId = restoredFocusId,
                        pendingScrollRestore = restoredPendingScroll,
                    ),
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
                    _uiState.update { it.copy(filter = it.filter.copy(excludedServerIds = excluded.toImmutableSet())) }
                }

                // Restore persisted filter preferences
                val savedSort = settingsRepository.librarySort.firstOrNull() ?: "Title"
                val savedSortDesc = settingsRepository.librarySortDescending.firstOrNull() ?: false
                val savedGenre = settingsRepository.libraryGenre.firstOrNull()
                val savedServerFilter = settingsRepository.libraryServerFilter.firstOrNull()
                val showYear = settingsRepository.showYearOnCards.firstOrNull() ?: false
                val gridColumns = settingsRepository.gridColumnsCount.firstOrNull() ?: 6

                // Use saved server filter, fallback to default server preference
                val serverFilter = savedServerFilter
                    ?: settingsRepository.defaultServer.firstOrNull()?.takeIf { it.isNotEmpty() && it != "all" }

                _uiState.update {
                    it.copy(
                        filter = it.filter.copy(
                            currentSort = savedSort,
                            isSortDescending = savedSortDesc,
                            selectedGenre = savedGenre,
                            selectedServerFilter = serverFilter,
                        ),
                        display = it.display.copy(
                            showYearOnCards = showYear,
                            gridColumnsCount = gridColumns,
                        ),
                    )
                }

                // Launch count observer AFTER metadata + DataStore reads so the first count
                // already uses the correct selectedServerId (set by single-server fast path).
                launchFilteredCountObserver()
            }
        }

        /**
         * Observe les changements de filtre pour calculer le nombre d'éléments filtrés.
         * Lancé après loadMetadata() pour éviter un premier count unifié inutile.
         */
        private fun launchFilteredCountObserver() {
            viewModelScope.launch {
                _uiState
                    .map { state ->
                        val serverFilterId = state.filter.availableServersMap[state.filter.selectedServerFilter]
                        val genreQuery =
                            if (state.filter.selectedGenre != null && state.filter.selectedGenre != "All") {
                                com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.filter.selectedGenre] ?: listOf(state.filter.selectedGenre)
                            } else {
                                null
                            }
                        CountParams(
                            filter = state.filter.currentFilter,
                            sort = state.filter.currentSort,
                            isDescending = state.filter.isSortDescending,
                            libraryId = state.selection.selectedLibraryId ?: "default",
                            mediaType = state.display.mediaType,
                            genre = genreQuery,
                            serverId = state.selection.selectedServerId ?: "all",
                            serverFilterId = serverFilterId,
                            excludedServerIds = state.filter.excludedServerIds.toList(),
                            query = state.filter.searchQuery.ifBlank { null },
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
                            _uiState.update { it.copy(display = it.display.copy(filteredItems = count)) }
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

                // Récupérations des serveurs depuis le repo Auth + Xtream accounts
                val servers = authRepository.getServers().getOrNull() ?: emptyList()
                val serverNames = servers.map { it.name }.toMutableList()
                val serverMap = servers.associate { it.name to it.clientIdentifier }.toMutableMap()

                // Include Xtream accounts as virtual servers
                try {
                    val xtreamAccounts = xtreamAccountRepository.observeAccounts().firstOrNull() ?: emptyList()
                    xtreamAccounts.forEach { account ->
                        serverNames.add(account.label)
                        serverMap[account.label] = "xtream_${account.id}"
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to load Xtream accounts for filter: ${e.message}")
                }

                // Include Backend servers as virtual servers
                try {
                    val backendServers = backendRepository.observeServers().firstOrNull() ?: emptyList()
                    backendServers.forEach { backend ->
                        serverNames.add(backend.label)
                        serverMap[backend.label] = "backend_${backend.id}"
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to load Backend servers for filter: ${e.message}")
                }

                // Include Jellyfin servers
                try {
                    val jellyfinServers = jellyfinServerRepository.getServers()
                    Timber.w("JELLYFIN_TRACE [loadMetadata] jellyfinServers: ${jellyfinServers.size}, details=${jellyfinServers.map { "${it.name}(id=${it.id}, prefixed=${it.prefixedServerId}, active=${it.isActive})" }}")
                    jellyfinServers.forEach { jfServer ->
                        serverNames.add(jfServer.name)
                        serverMap[jfServer.name] = jfServer.prefixedServerId
                    }
                } catch (e: Exception) {
                    Timber.e("JELLYFIN_TRACE [loadMetadata] Failed to load Jellyfin servers: ${e.message}", e)
                }

                Timber.w("JELLYFIN_TRACE [loadMetadata] plexServers=${servers.size}, totalServerNames=${serverNames.size}, serverNames=$serverNames, serverMap=$serverMap")

                // Single-server fast path: skip unified query when only one source exists
                if (servers.size == 1 && serverNames.size == 1) {
                    Timber.w("JELLYFIN_TRACE [loadMetadata] SINGLE-SERVER FAST PATH activated: serverId=${servers[0].clientIdentifier}")
                    _uiState.update { it.copy(
                        selection = it.selection.copy(
                            selectedServerId = servers[0].clientIdentifier
                        )
                    ) }
                } else {
                    Timber.w("JELLYFIN_TRACE [loadMetadata] UNIFIED PATH: plexServers=${servers.size}, serverNames=${serverNames.size} → serverId will be 'all'")
                }

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
                        display = it.display.copy(totalItems = totalCount, isLoading = false),
                        filter = it.filter.copy(availableServers = serverNames.toImmutableList(), availableServersMap = serverMap.toImmutableMap(), availableGenres = allGenres.toImmutableList()),
                    )
                }

                // Si la bibliothèque semble vide ou corrompue, déclencher une synchro arrière-plan
                val currentServerId = _uiState.value.selection.selectedServerId
                if (totalCount < 100 && (currentServerId == "all" || currentServerId == null)) {
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
                    emitError(e.toAppError())
                }

                _uiState.update { it.copy(display = it.display.copy(isLoading = false)) }
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
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
                    _uiState.update { it.copy(display = it.display.copy(selectedTab = action.tab)) }
                }
                is LibraryAction.ChangeViewMode -> {
                    _uiState.update { it.copy(display = it.display.copy(viewMode = action.mode)) }
                }
                is LibraryAction.LoadNextPage -> {
                    // Handled by Paging 3 automatically
                }
                is LibraryAction.Refresh -> {
                    if (_uiState.value.display.isRefreshing) return
                    _uiState.update { it.copy(display = it.display.copy(isRefreshing = true)) }
                    triggerBackgroundSync()
                    viewModelScope.launch {
                        delay(10_000) // 10s cooldown to prevent flooding
                        _uiState.update { it.copy(display = it.display.copy(isRefreshing = false)) }
                    }
                }
                is LibraryAction.OpenMedia -> {
                    // Save focus + scroll position for restoration on back-navigation
                    _uiState.update {
                        it.copy(scroll = it.scroll.copy(
                            lastFocusedId = action.media.ratingKey,
                            pendingScrollRestore = action.firstVisibleItemIndex,
                        ))
                    }
                    savedStateHandle["lastFocusedId"] = action.media.ratingKey
                    savedStateHandle["initialScrollIndex"] = action.firstVisibleItemIndex
                    savedStateHandle["pendingScrollRestore"] = action.firstVisibleItemIndex
                    viewModelScope.launch {
                        _navigationEvents.send(LibraryNavigationEvent.NavigateToDetail(action.media.ratingKey, action.media.serverId))
                    }
                }
                is LibraryAction.SelectLibrary -> {
                    savedStateHandle.remove<Int>("pendingScrollRestore")
                    savedStateHandle["selectedLibraryId"] = action.libraryId
                    _uiState.update {
                        it.copy(
                            selection = it.selection.copy(selectedLibraryId = action.libraryId),
                            scroll = it.scroll.copy(pendingScrollRestore = null),
                        )
                    }
                }
                is LibraryAction.ApplyFilter -> {
                    _uiState.update { it.copy(filter = it.filter.copy(currentFilter = action.filter)) }
                }
                is LibraryAction.ApplySort -> {
                    savedStateHandle.remove<Int>("pendingScrollRestore")
                    _uiState.update {
                        it.copy(
                            filter = it.filter.copy(currentSort = action.sort, isSortDescending = action.isDescending),
                            dialog = it.dialog.copy(isSortDialogOpen = false),
                            scroll = it.scroll.copy(pendingScrollRestore = null),
                        )
                    }
                    viewModelScope.launch { settingsRepository.saveLibrarySort(action.sort, action.isDescending) }
                }
                is LibraryAction.OpenServerFilter -> _uiState.update { it.copy(dialog = it.dialog.copy(isServerFilterOpen = true)) }
                is LibraryAction.CloseServerFilter -> _uiState.update { it.copy(dialog = it.dialog.copy(isServerFilterOpen = false)) }
                is LibraryAction.OpenGenreFilter -> _uiState.update { it.copy(dialog = it.dialog.copy(isGenreFilterOpen = true)) }
                is LibraryAction.CloseGenreFilter -> _uiState.update { it.copy(dialog = it.dialog.copy(isGenreFilterOpen = false)) }
                is LibraryAction.OpenSortDialog -> _uiState.update { it.copy(dialog = it.dialog.copy(isSortDialogOpen = true)) }
                is LibraryAction.CloseSortDialog -> _uiState.update { it.copy(dialog = it.dialog.copy(isSortDialogOpen = false)) }
                is LibraryAction.ToggleSearch -> {
                    _uiState.update {
                        val newVisible = !it.filter.isSearchVisible
                        if (!newVisible) {
                            it.copy(filter = it.filter.copy(isSearchVisible = false, searchQuery = ""))
                        } else {
                            it.copy(filter = it.filter.copy(isSearchVisible = true))
                        }
                    }
                }
                is LibraryAction.UpdateSearchQuery -> {
                    _uiState.update { it.copy(filter = it.filter.copy(searchQuery = action.query)) }
                }
                is LibraryAction.SelectGenre -> {
                    savedStateHandle.remove<Int>("pendingScrollRestore")
                    _uiState.update { it.copy(filter = it.filter.copy(selectedGenre = action.genre), scroll = it.scroll.copy(pendingScrollRestore = null)) }
                    viewModelScope.launch { settingsRepository.saveLibraryGenre(action.genre) }
                }
                is LibraryAction.SelectServerFilter -> {
                    savedStateHandle.remove<Int>("pendingScrollRestore")
                    _uiState.update { it.copy(filter = it.filter.copy(selectedServerFilter = action.serverId), scroll = it.scroll.copy(pendingScrollRestore = null)) }
                    viewModelScope.launch { settingsRepository.saveLibraryServerFilter(action.serverId) }
                }
                is LibraryAction.OnItemFocused -> {
                    // PERFORMANCE FIX: Only update SavedStateHandle, DO NOT update _uiState.
                    // Updating _uiState triggers a full screen recomposition on every D-pad move.
                    savedStateHandle["lastFocusedId"] = action.item.ratingKey
                    // _uiState.update { it.copy(lastFocusedId = action.item.ratingKey) }
                }
                is LibraryAction.JumpToLetter -> {
                    savedStateHandle.remove<Int>("pendingScrollRestore")
                    _uiState.update { it.copy(scroll = it.scroll.copy(pendingScrollRestore = null)) }
                    viewModelScope.launch {
                        val state = _uiState.value
                        Timber.d("JumpToLetter: ${action.letter}")

                        // Calculate filter params similarly to pagedItems
                        val serverFilterId = state.filter.availableServersMap[state.filter.selectedServerFilter]
                        val genreQuery =
                            if (state.filter.selectedGenre != null && state.filter.selectedGenre != "All") {
                                com.chakir.plexhubtv.core.model.GenreGrouping.GROUPS[state.filter.selectedGenre] ?: listOf(state.filter.selectedGenre)
                            } else {
                                null
                            }

                        val idx =
                            getLibraryIndexUseCase(
                                type = state.display.mediaType,
                                letter = action.letter,
                                filter = state.filter.currentFilter,
                                sort = state.filter.currentSort,
                                genre = genreQuery,
                                serverId = state.selection.selectedServerId,
                                selectedServerId = serverFilterId,
                                excludedServerIds = state.filter.excludedServerIds.toList(),
                                libraryKey = state.selection.selectedLibraryId,
                                query = state.filter.searchQuery.ifBlank { null },
                            )

                        Timber.d("Calculated index for ${action.letter}: $idx")

                        if (idx >= 0) {
                            // Force reload with new initial Key
                            savedStateHandle["initialScrollIndex"] = idx
                            _uiState.update { it.copy(scroll = it.scroll.copy(initialScrollIndex = idx)) }
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
