package com.chakir.plexhubtv.feature.libraryselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SelectableLibrary(
    val key: String,
    val title: String,
    val type: String,
    val isSelected: Boolean = true,
)

data class ServerWithLibraries(
    val serverId: String,
    val serverName: String,
    val libraries: List<SelectableLibrary>,
)

data class LibrarySelectionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val servers: List<ServerWithLibraries> = emptyList(),
    val isConfirming: Boolean = false,
)

sealed interface LibrarySelectionAction {
    data class ToggleLibrary(val serverId: String, val libraryKey: String) : LibrarySelectionAction
    data class ToggleServer(val serverId: String) : LibrarySelectionAction
    data object Confirm : LibrarySelectionAction
    data object Retry : LibrarySelectionAction
}

sealed class LibrarySelectionNavigationEvent {
    data object NavigateToLoading : LibrarySelectionNavigationEvent()
}

@HiltViewModel
class LibrarySelectionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val mediaDao: MediaDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibrarySelectionUiState())
    val uiState: StateFlow<LibrarySelectionUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<LibrarySelectionNavigationEvent>()
    val navigationEvent: SharedFlow<LibrarySelectionNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        loadServersAndLibraries()
    }

    fun onAction(action: LibrarySelectionAction) {
        when (action) {
            is LibrarySelectionAction.ToggleLibrary -> toggleLibrary(action.serverId, action.libraryKey)
            is LibrarySelectionAction.ToggleServer -> toggleServer(action.serverId)
            is LibrarySelectionAction.Confirm -> confirm()
            is LibrarySelectionAction.Retry -> loadServersAndLibraries()
        }
    }

    private fun loadServersAndLibraries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val serversResult = authRepository.getServers(forceRefresh = true)
                val servers = serversResult.getOrNull()

                if (servers.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Aucun serveur trouvé. Vérifiez votre connexion.")
                    }
                    return@launch
                }

                // Load previously selected IDs (for reconfiguration from Settings)
                val previouslySelected = settingsDataStore.selectedLibraryIds.first()

                // Fetch libraries for each server in parallel
                val serverWithLibraries = servers.map { server ->
                    async {
                        val librariesResult = libraryRepository.getLibraries(server.clientIdentifier)
                        val libraries = librariesResult.getOrNull() ?: emptyList()

                        val syncableLibraries = libraries
                            .filter { it.type == "movie" || it.type == "show" }
                            .map { lib ->
                                val compositeId = "${server.clientIdentifier}:${lib.key}"
                                SelectableLibrary(
                                    key = lib.key,
                                    title = lib.title,
                                    type = lib.type ?: "movie",
                                    // Pre-select: if user already configured, use their selection; otherwise select all
                                    isSelected = if (previouslySelected.isEmpty()) true
                                    else previouslySelected.contains(compositeId),
                                )
                            }

                        ServerWithLibraries(
                            serverId = server.clientIdentifier,
                            serverName = server.name,
                            libraries = syncableLibraries,
                        )
                    }
                }.awaitAll()

                // Filter out servers with no syncable libraries
                val validServers = serverWithLibraries.filter { it.libraries.isNotEmpty() }

                if (validServers.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Aucune bibliothèque compatible trouvée.")
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(isLoading = false, servers = validServers)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load servers and libraries")
                _uiState.update {
                    it.copy(isLoading = false, error = "Erreur de chargement : ${e.message}")
                }
            }
        }
    }

    private fun toggleLibrary(serverId: String, libraryKey: String) {
        _uiState.update { state ->
            state.copy(
                servers = state.servers.map { server ->
                    if (server.serverId == serverId) {
                        server.copy(
                            libraries = server.libraries.map { lib ->
                                if (lib.key == libraryKey) lib.copy(isSelected = !lib.isSelected)
                                else lib
                            },
                        )
                    } else {
                        server
                    }
                },
            )
        }
    }

    private fun toggleServer(serverId: String) {
        _uiState.update { state ->
            state.copy(
                servers = state.servers.map { server ->
                    if (server.serverId == serverId) {
                        val allSelected = server.libraries.all { it.isSelected }
                        server.copy(
                            libraries = server.libraries.map { it.copy(isSelected = !allSelected) },
                        )
                    } else {
                        server
                    }
                },
            )
        }
    }

    private fun confirm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true) }

            try {
                val currentState = _uiState.value

                // Build selected IDs set
                val selectedIds = currentState.servers.flatMap { server ->
                    server.libraries
                        .filter { it.isSelected }
                        .map { "${server.serverId}:${it.key}" }
                }.toSet()

                // Check if this is a reconfiguration (user already had a selection)
                val isReconfiguration = settingsDataStore.isFirstSyncComplete.first()
                if (isReconfiguration) {
                    val previousIds = settingsDataStore.selectedLibraryIds.first()
                    val removedIds = previousIds - selectedIds

                    // Purge media from deselected libraries
                    for (compositeId in removedIds) {
                        val parts = compositeId.split(":", limit = 2)
                        if (parts.size == 2) {
                            Timber.d("Purging media for deselected library: $compositeId")
                            mediaDao.deleteMediaByLibrary(parts[0], parts[1])
                        }
                    }

                    // Reset sync flag to trigger re-sync
                    settingsDataStore.saveFirstSyncComplete(false)
                }

                // Save selection
                settingsDataStore.saveSelectedLibraryIds(selectedIds)
                settingsDataStore.saveLibrarySelectionComplete(true)

                Timber.i("Library selection saved: ${selectedIds.size} libraries selected")

                _navigationEvent.emit(LibrarySelectionNavigationEvent.NavigateToLoading)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save library selection")
                _uiState.update {
                    it.copy(isConfirming = false, error = "Erreur de sauvegarde : ${e.message}")
                }
            }
        }
    }
}
