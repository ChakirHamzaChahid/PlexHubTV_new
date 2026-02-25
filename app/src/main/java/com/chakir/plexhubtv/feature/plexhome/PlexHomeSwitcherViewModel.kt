package com.chakir.plexhubtv.feature.plexhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour la gestion des profils (Plex Home).
 * Gère le chargement des utilisateurs et le basculement (Switch User) avec ou sans PIN.
 */
@HiltViewModel
class PlexHomeSwitcherViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PlexHomeSwitcherUiState())
        val uiState: StateFlow<PlexHomeSwitcherUiState> = _uiState.asStateFlow()

        init {
            Timber.d("SCREEN [PlexHomeSwitch]: Opened")
            onAction(PlexHomeSwitcherAction.LoadUsers)
        }

        fun onAction(action: PlexHomeSwitcherAction) {
            when (action) {
                PlexHomeSwitcherAction.LoadUsers -> loadUsers()
                is PlexHomeSwitcherAction.SelectUser -> {
                    // Vérifier si le profil est protégé par PIN
                    if (action.user.protected || action.user.hasPassword) {
                        _uiState.update { it.copy(showPinDialog = true, selectedUser = action.user, pinValue = "") }
                    } else {
                        switchUser(action.user)
                    }
                }
                PlexHomeSwitcherAction.CancelPin -> {
                    _uiState.update { it.copy(showPinDialog = false, selectedUser = null, pinValue = "") }
                }
                is PlexHomeSwitcherAction.EnterPinDigit -> {
                    if (_uiState.value.pinValue.length < 4) {
                        _uiState.update { it.copy(pinValue = it.pinValue + action.digit) }
                        if (_uiState.value.pinValue.length == 4) {
                            onAction(PlexHomeSwitcherAction.SubmitPin)
                        }
                    }
                }
                PlexHomeSwitcherAction.ClearPin -> {
                    _uiState.update { it.copy(pinValue = "") }
                }
                PlexHomeSwitcherAction.SubmitPin -> {
                    val user = _uiState.value.selectedUser ?: return
                    val pin = _uiState.value.pinValue
                    switchUser(user, pin)
                }
            }
        }

        private fun loadUsers() {
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                Timber.d("SCREEN [PlexHomeSwitch]: Loading users start")
                _uiState.update { it.copy(isLoading = true, error = null) }
                authRepository.getHomeUsers()
                    .onSuccess { users ->
                        val duration = System.currentTimeMillis() - startTime
                        Timber.i("SCREEN [PlexHomeSwitch] SUCCESS: duration=${duration}ms | users=${users.size}")
                        _uiState.update { it.copy(isLoading = false, users = users) }
                    }
                    .onFailure { error ->
                        val duration = System.currentTimeMillis() - startTime
                        Timber.e("SCREEN [PlexHomeSwitch] FAILED: duration=${duration}ms error=${error.message}")
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                    }
            }
        }

        private fun switchUser(
            user: com.chakir.plexhubtv.core.model.PlexHomeUser,
            pin: String? = null,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSwitching = true, error = null) }
                authRepository.switchUser(user, pin)
                    .onSuccess {
                        _uiState.update { it.copy(isSwitching = false, switchSuccess = true, showPinDialog = false) }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isSwitching = false,
                                error = "Échec du changement d'utilisateur: ${error.message}",
                                pinValue = "",
                            )
                        }
                    }
            }
        }
    }
