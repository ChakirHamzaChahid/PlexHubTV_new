package com.chakir.plexhubtv.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.network.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel principal de l'application.
 * Surveille l'état de la connexion (Online/Offline) via [ConnectionManager].
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val connectionManager: ConnectionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            observeConnectionState()
        }

        private fun observeConnectionState() {
            viewModelScope.launch {
                connectionManager.isOffline.collectLatest { offline ->
                    _uiState.update { it.copy(isOffline = offline) }
                }
            }
        }

        // Debug method to simulate network toggle
        fun toggleOfflineMode() {
            val newState = !_uiState.value.isOffline
            connectionManager.setOfflineMode(newState)
        }
    }

data class MainUiState(
    val isOffline: Boolean = false,
)
