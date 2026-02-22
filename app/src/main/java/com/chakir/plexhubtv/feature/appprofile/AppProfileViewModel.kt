package com.chakir.plexhubtv.feature.appprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour la gestion des profils utilisateurs.
 */
@HiltViewModel
class AppProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppProfileUiState())
    val uiState: StateFlow<AppProfileUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<AppProfileNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    init {
        loadProfiles()
        observeActiveProfile()
    }

    fun onAction(action: AppProfileAction) {
        when (action) {
            is AppProfileAction.SelectProfile -> selectProfile(action.profile)
            is AppProfileAction.ManageProfiles -> navigateToManageProfiles()
            is AppProfileAction.CreateProfile -> showCreateDialog()
            is AppProfileAction.EditProfile -> showEditDialog(action.profile)
            is AppProfileAction.DeleteProfile -> deleteProfile(action.profileId)
            is AppProfileAction.DismissDialog -> dismissDialog()
            is AppProfileAction.Back -> navigateBack()
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // Ensure default profile exists
                profileRepository.ensureDefaultProfile()

                // Observe all profiles
                profileRepository.getAllProfiles().safeCollectIn(
                    scope = viewModelScope,
                    onError = { e ->
                        Timber.e(e, "AppProfileViewModel: loadProfiles failed")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to load profiles"
                            )
                        }
                    }
                ) { profiles ->
                    _uiState.update {
                        it.copy(
                            profiles = profiles,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load profiles")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load profiles"
                    )
                }
            }
        }
    }

    private fun observeActiveProfile() {
        profileRepository.getActiveProfileFlow().safeCollectIn(
            scope = viewModelScope,
            onError = { e ->
                Timber.e(e, "AppProfileViewModel: observeActiveProfile failed")
            }
        ) { profile ->
            _uiState.update { it.copy(activeProfile = profile) }
        }
    }

    private fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                val result = profileRepository.switchProfile(profile.id)
                if (result.isSuccess) {
                    Timber.i("Profile switched to: ${profile.name}")
                    _navigationEvents.send(AppProfileNavigationEvent.NavigateToHome)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to switch profile"
                    _uiState.update { it.copy(error = error) }
                    Timber.e("Failed to switch profile: $error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error switching profile")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun navigateToManageProfiles() {
        viewModelScope.launch {
            _navigationEvents.send(AppProfileNavigationEvent.NavigateToManageProfiles)
        }
    }

    private fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    private fun showEditDialog(profile: Profile) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                profileToEdit = profile
            )
        }
    }

    private fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            try {
                val result = profileRepository.deleteProfile(profileId)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()?.message ?: "Failed to delete profile"
                    _uiState.update { it.copy(error = error) }
                    Timber.e("Failed to delete profile: $error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting profile")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun dismissDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                showEditDialog = false,
                profileToEdit = null
            )
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _navigationEvents.send(AppProfileNavigationEvent.NavigateBack)
        }
    }
}
