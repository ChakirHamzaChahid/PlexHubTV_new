package com.chakir.plexhubtv.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val users: List<com.chakir.plexhubtv.domain.model.UserProfile> = emptyList(),
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ProfileEvent {
    data class SwitchToUser(val user: com.chakir.plexhubtv.domain.model.UserProfile) : ProfileEvent
    data object RefreshProfiles : ProfileEvent
}

/**
 * ViewModel gérant la liste des profils utilisateurs (Plex Home Users).
 * Permet de lister les utilisateurs et de basculer de session active via [AccountRepository].
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            accountRepository.getHomeUsers().onSuccess { users ->
                val currentUser = accountRepository.getCurrentUser()
                _uiState.update { it.copy(
                    users = users.map { user -> 
                        com.chakir.plexhubtv.domain.model.UserProfile(
                            id = user.uuid,
                            title = user.title,
                            thumb = user.thumb,
                            protected = user.protected,
                            admin = user.admin
                        )
                    },
                    currentUserId = currentUser?.uuid,
                    isLoading = false 
                ) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.localizedMessage, isLoading = false) }
            }
        }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.SwitchToUser -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true) }
                    // Convert domain model back or fetch from list?
                    val user = accountRepository.getHomeUsers().getOrNull()?.find { it.uuid == event.user.id }
                    if (user != null) {
                        accountRepository.switchUser(user, null) // In real app, prompt for PIN if needed
                        _uiState.update { it.copy(currentUserId = user.uuid, isLoading = false) }
                    }
                }
            }
            ProfileEvent.RefreshProfiles -> loadProfiles()
        }
    }
}
