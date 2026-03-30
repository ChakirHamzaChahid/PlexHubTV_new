package com.chakir.plexhubtv.feature.appprofile

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.Profile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour la sélection et gestion des profils.
 */
@Immutable
data class AppProfileUiState(
    val profiles: ImmutableList<Profile> = persistentListOf(),
    val activeProfile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val profileToEdit: Profile? = null,
    val showDeleteConfirmation: Boolean = false,
    val profileToDelete: Profile? = null,
    val showPinDialog: Boolean = false,
    val pinError: String? = null,
    val pendingProfileSwitch: Profile? = null,
)

sealed interface AppProfileAction {
    data class SelectProfile(val profile: Profile) : AppProfileAction
    data object ManageProfiles : AppProfileAction
    data object CreateProfile : AppProfileAction
    data class EditProfile(val profile: Profile) : AppProfileAction
    data class DeleteProfile(val profileId: String) : AppProfileAction
    data class SubmitCreateProfile(
        val name: String,
        val avatarEmoji: String,
        val isKidsProfile: Boolean,
    ) : AppProfileAction
    data class SubmitEditProfile(
        val id: String,
        val name: String,
        val avatarEmoji: String,
        val isKidsProfile: Boolean,
    ) : AppProfileAction
    data class ConfirmDeleteProfile(val profile: Profile) : AppProfileAction
    data class VerifyPin(val pin: String) : AppProfileAction
    data object DismissPin : AppProfileAction
    data object DismissDialog : AppProfileAction
    data object Back : AppProfileAction
}

sealed interface AppProfileNavigationEvent {
    data object NavigateToHome : AppProfileNavigationEvent
    data object NavigateToManageProfiles : AppProfileNavigationEvent
    data object NavigateBack : AppProfileNavigationEvent
}
