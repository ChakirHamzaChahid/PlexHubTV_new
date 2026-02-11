package com.chakir.plexhubtv.feature.profile

import com.chakir.plexhubtv.core.model.Profile

/**
 * État de l'UI pour la sélection et gestion des profils.
 */
data class ProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val profileToEdit: Profile? = null,
)

sealed interface ProfileAction {
    data class SelectProfile(val profile: Profile) : ProfileAction
    data object ManageProfiles : ProfileAction
    data object CreateProfile : ProfileAction
    data class EditProfile(val profile: Profile) : ProfileAction
    data class DeleteProfile(val profileId: String) : ProfileAction
    data object DismissDialog : ProfileAction
    data object Back : ProfileAction
}

sealed interface ProfileNavigationEvent {
    data object NavigateToHome : ProfileNavigationEvent
    data object NavigateToManageProfiles : ProfileNavigationEvent
    data object NavigateBack : ProfileNavigationEvent
}
