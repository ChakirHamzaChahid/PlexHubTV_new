package com.chakir.plexhubtv.feature.settings.serverstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.ConnectionResult
import com.chakir.plexhubtv.domain.model.Server
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerStatusUiState(
    val isLoading: Boolean = false,
    val servers: List<ServerStatusUiModel> = emptyList(),
    val error: String? = null
)

data class ServerStatusUiModel(
    val name: String,
    val identifier: String,
    val isOnline: Boolean,
    val latencyMs: Long,
    val address: String,
    val details: String,
    val isLoading: Boolean = false
)

/**
 * ViewModel pour l'écran d'état des serveurs.
 * Vérifie la connectivité de chaque serveur en parallèle via [ConnectionManager].
 */
@HiltViewModel
class ServerStatusViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerStatusUiState())
    val uiState: StateFlow<ServerStatusUiState> = _uiState.asStateFlow()

    init {
        loadServers()
    }

    fun refresh() {
        loadServers()
    }

    private fun loadServers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = authRepository.getServers()
            
            result.onSuccess { servers ->
                // Initial list populate (as loading)
                val initialUiModels = servers.map { server ->
                    ServerStatusUiModel(
                        name = server.name,
                        identifier = server.clientIdentifier,
                        isOnline = false, // Unknown yet
                        latencyMs = 0,
                        address = "Scanning...",
                        details = "Checking availability...",
                        isLoading = true
                    )
                }
                _uiState.update { it.copy(servers = initialUiModels) }
                
                // Check status for each server in parallel (but update UI individually)
                checkAllServers(servers)
                
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Available to load servers") }
            }
        }
    }

    private suspend fun checkAllServers(servers: List<Server>) {
        // We trigger check for all, assuming ConnectionManager handles concurrency or we launch separate coroutines here.
        // To be safe and update UI incrementally, we iterate.
        // Actually, we can launch parallel jobs here.
        
        servers.forEach { server ->
            viewModelScope.launch {
                val status = connectionManager.checkConnectionStatus(server)
                updateServerStatus(server.clientIdentifier, status)
            }
        }
        
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun updateServerStatus(identifier: String, status: ConnectionResult) {
        _uiState.update { currentState ->
            val updatedList = currentState.servers.map { item ->
                if (item.identifier == identifier) {
                    item.copy(
                        isOnline = status.success,
                        latencyMs = status.latencyMs,
                        address = status.url,
                        details = if (status.success) "Online (${status.latencyMs}ms)" else "Offline (Error ${status.errorCode})",
                        isLoading = false
                    )
                } else {
                    item
                }
            }
            currentState.copy(servers = updatedList)
        }
    }
}
