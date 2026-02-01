package com.chakir.plexhubtv.feature.auth.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour la gestion des profils (Plex Home).
 * Gère le chargement des utilisateurs et le basculement (Switch User) avec ou sans PIN.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        onAction(ProfileAction.LoadUsers)
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.LoadUsers -> loadUsers()
            is ProfileAction.SelectUser -> {
                if (action.user.protected || action.user.hasPassword) {
                    _uiState.update { it.copy(showPinDialog = true, selectedUser = action.user, pinValue = "") }
                } else {
                    switchUser(action.user)
                }
            }
            ProfileAction.CancelPin -> {
                _uiState.update { it.copy(showPinDialog = false, selectedUser = null, pinValue = "") }
            }
            is ProfileAction.EnterPinDigit -> {
                if (_uiState.value.pinValue.length < 4) {
                    _uiState.update { it.copy(pinValue = it.pinValue + action.digit) }
                    if (_uiState.value.pinValue.length == 4) {
                        onAction(ProfileAction.SubmitPin)
                    }
                }
            }
            ProfileAction.ClearPin -> {
                _uiState.update { it.copy(pinValue = "") }
            }
            ProfileAction.SubmitPin -> {
                val user = _uiState.value.selectedUser ?: return
                val pin = _uiState.value.pinValue
                switchUser(user, pin)
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.getHomeUsers()
                .onSuccess { users ->
                    _uiState.update { it.copy(isLoading = false, users = users) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    private fun switchUser(user: com.chakir.plexhubtv.domain.model.PlexHomeUser, pin: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSwitching = true, error = null) }
            authRepository.switchUser(user, pin)
                .onSuccess {
                    _uiState.update { it.copy(isSwitching = false, switchSuccess = true, showPinDialog = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSwitching = false, error = "Échec du changement d'utilisateur: ${error.message}", pinValue = "") }
                }
        }
    }
}
