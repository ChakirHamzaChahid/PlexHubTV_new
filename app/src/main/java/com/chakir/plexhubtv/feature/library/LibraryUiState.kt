package com.chakir.plexhubtv.feature.library

import com.chakir.plexhubtv.core.model.LibrarySection
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType

enum class LibraryTab(val title: String) {
    Recommended("Recommended"),
    Browse("Browse"),
    Collections("Collections"),
    Playlists("Playlists"),
}

enum class LibraryViewMode {
    Grid,
    Compact,
    List,
}

data class LibraryDisplayState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val hubs: List<com.chakir.plexhubtv.core.model.Hub> = emptyList(),
    val totalItems: Int = 0,
    val filteredItems: Int? = null,
    val mediaType: MediaType = MediaType.Movie,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    val selectedTab: LibraryTab = LibraryTab.Browse,
)

data class LibraryFilterState(
    val currentSort: String = "Title",
    val isSortDescending: Boolean = false,
    val currentFilter: String = "All",
    val selectedGenre: String? = null,
    val selectedServerFilter: String? = null,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val excludedServerIds: Set<String> = emptySet(),
    val availableGenres: List<String> = emptyList(),
    val availableServers: List<String> = emptyList(),
    val availableServersMap: Map<String, String> = emptyMap(),
)

data class LibrarySelectionState(
    val selectedServerId: String? = null,
    val selectedLibraryId: String? = null,
    val availableLibraries: List<LibrarySection> = emptyList(),
)

data class LibraryDialogState(
    val isSortDialogOpen: Boolean = false,
    val isServerFilterOpen: Boolean = false,
    val isGenreFilterOpen: Boolean = false,
)

data class LibraryScrollState(
    val rawOffset: Int = 0,
    val offset: Int = 0,
    val endOfReached: Boolean = false,
    val initialScrollIndex: Int? = null,
    val lastFocusedId: String? = null,
)

/**
 * État UI de la bibliothèque, décomposé en sous-états sémantiques.
 * Note: La liste des médias est gérée séparément via PagingData.
 * Les erreurs sont émises via errorEvents channel pour une gestion centralisée.
 */
data class LibraryUiState(
    val display: LibraryDisplayState = LibraryDisplayState(),
    val filter: LibraryFilterState = LibraryFilterState(),
    val selection: LibrarySelectionState = LibrarySelectionState(),
    val dialog: LibraryDialogState = LibraryDialogState(),
    val scroll: LibraryScrollState = LibraryScrollState(),
)

sealed interface LibraryAction {
    data class SelectTab(val tab: LibraryTab) : LibraryAction

    data class ChangeViewMode(val mode: LibraryViewMode) : LibraryAction

    object LoadNextPage : LibraryAction

    object Refresh : LibraryAction

    data class OpenMedia(val media: MediaItem) : LibraryAction

    data class SelectLibrary(val libraryId: String) : LibraryAction

    // Dialogs
    object OpenServerFilter : LibraryAction

    object CloseServerFilter : LibraryAction

    object OpenGenreFilter : LibraryAction

    object CloseGenreFilter : LibraryAction

    data class ApplyFilter(val filter: String) : LibraryAction

    data class SelectGenre(val genre: String?) : LibraryAction

    data class SelectServerFilter(val serverId: String?) : LibraryAction

    object OpenSortDialog : LibraryAction

    object CloseSortDialog : LibraryAction

    data class ApplySort(val sort: String, val isDescending: Boolean) : LibraryAction

    // Search
    object ToggleSearch : LibraryAction

    data class UpdateSearchQuery(val query: String) : LibraryAction

    // Focus
    data class OnItemFocused(val item: MediaItem) : LibraryAction

    data class JumpToLetter(val letter: String) : LibraryAction
}
