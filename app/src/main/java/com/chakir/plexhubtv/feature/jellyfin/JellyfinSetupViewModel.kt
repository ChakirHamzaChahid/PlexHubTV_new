package com.chakir.plexhubtv.feature.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chakir.plexhubtv.core.model.JellyfinServer
import com.chakir.plexhubtv.domain.repository.JellyfinServerRepository
import com.chakir.plexhubtv.work.LibrarySyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class JellyfinSetupUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isTesting: Boolean = false,
    val testResult: JellyfinTestResult? = null,
    val servers: List<JellyfinServer> = emptyList(),
    val showRemoveDialog: String? = null,
)

sealed interface JellyfinTestResult {
    data class Success(val serverName: String, val version: String) : JellyfinTestResult
    data class Error(val message: String) : JellyfinTestResult
}

sealed interface JellyfinSetupAction {
    data class UpdateBaseUrl(val value: String) : JellyfinSetupAction
    data class UpdateUsername(val value: String) : JellyfinSetupAction
    data class UpdatePassword(val value: String) : JellyfinSetupAction
    data object TestAndAdd : JellyfinSetupAction
    data class ConfirmRemove(val serverId: String) : JellyfinSetupAction
    data class DismissRemoveDialog(val confirmed: Boolean) : JellyfinSetupAction
    data object ClearForm : JellyfinSetupAction
}

@HiltViewModel
class JellyfinSetupViewModel @Inject constructor(
    private val jellyfinServerRepository: JellyfinServerRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinSetupUiState())
    val uiState: StateFlow<JellyfinSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jellyfinServerRepository.observeServers().collect { servers ->
                _uiState.update { it.copy(servers = servers) }
            }
        }
    }

    fun onAction(action: JellyfinSetupAction) {
        when (action) {
            is JellyfinSetupAction.UpdateBaseUrl -> _uiState.update { it.copy(baseUrl = action.value) }
            is JellyfinSetupAction.UpdateUsername -> _uiState.update { it.copy(username = action.value) }
            is JellyfinSetupAction.UpdatePassword -> _uiState.update { it.copy(password = action.value) }
            is JellyfinSetupAction.TestAndAdd -> testAndAdd()
            is JellyfinSetupAction.ConfirmRemove -> _uiState.update { it.copy(showRemoveDialog = action.serverId) }
            is JellyfinSetupAction.DismissRemoveDialog -> handleRemoveDialog(action.confirmed)
            is JellyfinSetupAction.ClearForm -> clearForm()
        }
    }

    private fun testAndAdd() {
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) return

        _uiState.update { it.copy(isTesting = true, testResult = null) }
        viewModelScope.launch {
            val result = jellyfinServerRepository.addServer(
                baseUrl = state.baseUrl.trim(),
                username = state.username.trim(),
                password = state.password.trim(),
            )
            result.fold(
                onSuccess = { server ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = JellyfinTestResult.Success(
                                serverName = server.name,
                                version = server.version,
                            ),
                            baseUrl = "",
                            username = "",
                            password = "",
                        )
                    }
                    // Trigger library sync to import Jellyfin content + rebuild unified table
                    triggerLibrarySync()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = JellyfinTestResult.Error(
                                error.message ?: "Connection failed",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun handleRemoveDialog(confirmed: Boolean) {
        val serverId = _uiState.value.showRemoveDialog
        _uiState.update { it.copy(showRemoveDialog = null) }
        if (confirmed && serverId != null) {
            viewModelScope.launch {
                jellyfinServerRepository.removeServer(serverId)
            }
        }
    }

    private fun triggerLibrarySync() {
        val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("LibrarySync_JellyfinSetup", ExistingWorkPolicy.KEEP, syncRequest)
        Timber.d("Triggered library sync after adding Jellyfin server")
    }

    private fun clearForm() {
        _uiState.update {
            it.copy(
                baseUrl = "",
                username = "",
                password = "",
                isTesting = false,
                testResult = null,
            )
        }
    }
}
