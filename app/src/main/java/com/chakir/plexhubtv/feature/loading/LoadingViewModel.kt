package com.chakir.plexhubtv.feature.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import com.chakir.plexhubtv.work.LibrarySyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoadingViewModel
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        private val workManager: WorkManager,
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LoadingUiState>(LoadingUiState.Loading("Initialisation..."))
        val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

        private val _navigationEvent = MutableSharedFlow<LoadingNavigationEvent>()
        val navigationEvent: SharedFlow<LoadingNavigationEvent> = _navigationEvent.asSharedFlow()

        private val json = Json { ignoreUnknownKeys = true }

        init {
            checkSyncStatus()
        }

        private fun checkSyncStatus() {
            viewModelScope.launch {
                // 0. Safety check: ensure library selection was completed
                if (!settingsDataStore.isLibrarySelectionComplete.first()) {
                    _navigationEvent.emit(LoadingNavigationEvent.NavigateToLibrarySelection)
                    return@launch
                }

                // 1. Check if sync is already complete
                if (settingsDataStore.isFirstSyncComplete.first()) {
                    navigateAfterSync()
                    return@launch
                }

                // 2. Enqueue initial sync if not yet done
                if (!settingsDataStore.isFirstSyncComplete.first()) {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()
                    workManager.enqueueUniqueWork(
                        "LibrarySync_Initial",
                        ExistingWorkPolicy.KEEP,
                        syncRequest,
                    )
                }

                // 3. Observe WorkManager for "LibrarySync_Initial"
                val workFlow = workManager.getWorkInfosForUniqueWorkFlow("LibrarySync_Initial")

                workFlow.collectLatest { workInfos ->
                    val syncWork = workInfos.firstOrNull()

                    if (syncWork == null) {
                        _uiState.value = LoadingUiState.Loading("Démarrage de la synchronisation...")
                    } else {
                        when (syncWork.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                _uiState.value = LoadingUiState.Completed
                                navigateAfterSync()
                            }
                            WorkInfo.State.FAILED -> {
                                _uiState.value = LoadingUiState.Error("La synchronisation a échoué.")
                            }
                            WorkInfo.State.RUNNING -> {
                                val progress = syncWork.progress.getFloat("progress", 0f)
                                val phase = syncWork.progress.getString("phase") ?: "discovering"
                                val serverStatesJson = syncWork.progress.getString("serverStates")
                                val currentServerIdx = syncWork.progress.getInt("currentServerIdx", -1)

                                // Deserialize enriched server state if available
                                val syncGlobalState = if (!serverStatesJson.isNullOrBlank()) {
                                    try {
                                        val servers = json.decodeFromString<List<SyncServerState>>(serverStatesJson)
                                        SyncGlobalState(
                                            phase = when (phase) {
                                                "discovering" -> SyncPhase.Discovering
                                                "library_sync" -> SyncPhase.LibrarySync
                                                "extras" -> SyncPhase.Extras
                                                "finalizing" -> SyncPhase.Finalizing
                                                else -> SyncPhase.LibrarySync
                                            },
                                            servers = servers,
                                            currentServerIndex = currentServerIdx,
                                            globalProgress = progress,
                                        )
                                    } catch (e: Exception) {
                                        Timber.e("Failed to parse serverStates: ${e.message}")
                                        null
                                    }
                                } else null

                                val message = buildSyncMessage(phase, syncGlobalState, syncWork)
                                _uiState.value = LoadingUiState.Loading(message, progress, syncGlobalState)
                            }
                            else -> {
                                _uiState.value = LoadingUiState.Loading("Préparation...")
                            }
                        }
                    }
                }
            }
        }

        private fun buildSyncMessage(
            phase: String,
            state: SyncGlobalState?,
            workInfo: WorkInfo,
        ): String = when (phase) {
            "discovering" -> "Discovering servers..."
            "library_sync" -> {
                val server = state?.currentServer
                val lib = state?.currentLibrary
                when {
                    server != null && lib != null ->
                        "${server.serverName} - ${lib.name} (${lib.itemsSynced}/${lib.itemsTotal})"
                    server != null -> "Syncing ${server.serverName}..."
                    else -> workInfo.progress.getString("message") ?: "Syncing libraries..."
                }
            }
            "extras" -> "Syncing extras..."
            "finalizing" -> "Finalizing..."
            else -> workInfo.progress.getString("message") ?: "Synchronisation en cours..."
        }

        private suspend fun navigateAfterSync() {
            profileRepository.ensureDefaultProfile()
            val profileCount = profileRepository.getProfileCount()
            if (profileCount > 1) {
                _navigationEvent.emit(LoadingNavigationEvent.NavigateToProfileSelection)
            } else {
                _navigationEvent.emit(LoadingNavigationEvent.NavigateToMain)
            }
        }

        fun onRetry() {
            checkSyncStatus()
        }

        fun onExit() {
            viewModelScope.launch {
                _navigationEvent.emit(LoadingNavigationEvent.NavigateToAuth)
            }
        }
    }

sealed class LoadingUiState {
    data class Loading(
        val message: String,
        val progress: Float = 0f,
        val syncState: SyncGlobalState? = null,
    ) : LoadingUiState()

    data object Completed : LoadingUiState()

    data class Error(val message: String) : LoadingUiState()
}

sealed class LoadingNavigationEvent {
    data object NavigateToMain : LoadingNavigationEvent()
    data object NavigateToAuth : LoadingNavigationEvent()
    data object NavigateToLibrarySelection : LoadingNavigationEvent()
    data object NavigateToProfileSelection : LoadingNavigationEvent()
}
