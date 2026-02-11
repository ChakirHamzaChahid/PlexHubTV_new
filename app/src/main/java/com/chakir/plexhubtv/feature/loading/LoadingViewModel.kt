package com.chakir.plexhubtv.feature.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chakir.plexhubtv.di.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoadingViewModel
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        private val workManager: WorkManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LoadingUiState>(LoadingUiState.Loading("Initialisation..."))
        val uiState: StateFlow<LoadingUiState> = _uiState.asStateFlow()

        private val _navigationEvent = MutableSharedFlow<LoadingNavigationEvent>()
        val navigationEvent: SharedFlow<LoadingNavigationEvent> = _navigationEvent.asSharedFlow()

        init {
            checkSyncStatus()
        }

        private fun checkSyncStatus() {
            viewModelScope.launch {
                // 1. Check if sync is already complete
                if (settingsDataStore.isFirstSyncComplete.first()) {
                    _navigationEvent.emit(LoadingNavigationEvent.NavigateToMain)
                    return@launch
                }

                // 2. Observe WorkManager for "LibrarySync_Initial" or "LibrarySync"
                // We verify both unique work names just in case
                val workFlow = workManager.getWorkInfosForUniqueWorkFlow("LibrarySync_Initial")

                workFlow.collectLatest { workInfos ->
                    val syncWork = workInfos.firstOrNull()

                    if (syncWork == null) {
                        _uiState.value = LoadingUiState.Loading("Démarrage de la synchronisation...")
                    } else {
                        when (syncWork.state) {
                            androidx.work.WorkInfo.State.SUCCEEDED -> {
                                _uiState.value = LoadingUiState.Completed
                                _navigationEvent.emit(LoadingNavigationEvent.NavigateToMain)
                            }
                            androidx.work.WorkInfo.State.FAILED -> {
                                _uiState.value = LoadingUiState.Error("La synchronisation a échoué.")
                                // Optionally navigate anyway after a delay or allow retry
                            }
                            androidx.work.WorkInfo.State.RUNNING -> {
                                val progress = syncWork.progress.getFloat("progress", 0f)
                                val message = syncWork.progress.getString("message") ?: "Synchronisation en cours..."
                                _uiState.value = LoadingUiState.Loading(message, progress)
                            }
                            else -> {
                                _uiState.value = LoadingUiState.Loading("Préparation...")
                            }
                        }
                    }
                }
            }
        }

        // Fallback: If WorkManager fails to report success, check DataStore periodically
        fun onRetry() {
            checkSyncStatus()
        }
    }

sealed class LoadingUiState {
    data class Loading(val message: String, val progress: Float = 0f) : LoadingUiState()

    data object Completed : LoadingUiState()

    data class Error(val message: String) : LoadingUiState()
}

sealed class LoadingNavigationEvent {
    data object NavigateToMain : LoadingNavigationEvent()
}
