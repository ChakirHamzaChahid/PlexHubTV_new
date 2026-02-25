package com.chakir.plexhubtv.feature.appprofile

import com.chakir.plexhubtv.core.model.Profile

/**
 * État de l'UI pour la sélection et gestion des profils.
 */
data class AppProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val profileToEdit: Profile? = null,
)

sealed interface AppProfileAction {
    data class SelectProfile(val profile: Profile) : AppProfileAction
    data object ManageProfiles : AppProfileAction
    data object CreateProfile : AppProfileAction
    data class EditProfile(val profile: Profile) : AppProfileAction
    data class DeleteProfile(val profileId: String) : AppProfileAction
    data object DismissDialog : AppProfileAction
    data object Back : AppProfileAction
}

sealed interface AppProfileNavigationEvent {
    data object NavigateToHome : AppProfileNavigationEvent
    data object NavigateToManageProfiles : AppProfileNavigationEvent
    data object NavigateBack : AppProfileNavigationEvent
}
