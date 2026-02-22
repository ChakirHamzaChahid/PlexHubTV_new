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
    List,
}

/**
 * État UI de la bibliothèque.
 * Contient tout l'état nécessaire : filtres, tri, mode d'affichage, search query, etc.
 * Note: La liste des médias est gérée séparément via PagingData.
 * Les erreurs sont maintenant émises via errorEvents channel pour une gestion centralisée.
 */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val hubs: List<com.chakir.plexhubtv.core.model.Hub> = emptyList(),
    val totalItems: Int = 0,
    val filteredItems: Int? = null, // null = not yet computed; differs from totalItems when filters are active
    val mediaType: MediaType = MediaType.Movie,
    val offset: Int = 0,
    val endOfReached: Boolean = false,
    // Selection State
    val selectedServerId: String? = null,
    val availableLibraries: List<LibrarySection> = emptyList(),
    val selectedLibraryId: String? = null,
    val selectedTab: LibraryTab = LibraryTab.Browse,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    // Sort & Filter
    val isSortDialogOpen: Boolean = false,
    val currentSort: String = "Title", // Date Added, Title, Year
    val isSortDescending: Boolean = false, // Default for Title (ascending A-Z)
    val isServerFilterOpen: Boolean = false,
    val isGenreFilterOpen: Boolean = false,
    val currentFilter: String = "All", // Deprecated? Or just "Unwatched" etc.
    // New Filters
    val availableGenres: List<String> = emptyList(),
    val availableServers: List<String> = emptyList(), // Names
    val availableServersMap: Map<String, String> = emptyMap(), // Name -> ID
    val selectedGenre: String? = null,
    val selectedServerFilter: String? = null,
    // Search
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    // Internal Pagination State
    val rawOffset: Int = 0,
    val initialScrollIndex: Int? = null,
    // Focus Restoration
    val lastFocusedId: String? = null,
    val excludedServerIds: Set<String> = emptySet(),
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
