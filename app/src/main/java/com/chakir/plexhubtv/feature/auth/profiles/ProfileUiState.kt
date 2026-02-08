package com.chakir.plexhubtv.feature.auth.profiles

import com.chakir.plexhubtv.core.model.PlexHomeUser

/**
 * État de l'UI pour l'écran de changement de profil.
 */
data class ProfileUiState(
    val isLoading: Boolean = false,
    val users: List<PlexHomeUser> = emptyList(),
    val error: String? = null,
    val showPinDialog: Boolean = false,
    val selectedUser: PlexHomeUser? = null,
    val pinValue: String = "",
    val isSwitching: Boolean = false,
    val switchSuccess: Boolean = false,
)

sealed interface ProfileAction {
    data object LoadUsers : ProfileAction

    data class SelectUser(val user: PlexHomeUser) : ProfileAction

    data object CancelPin : ProfileAction

    data class EnterPinDigit(val digit: String) : ProfileAction

    data object ClearPin : ProfileAction

    data object SubmitPin : ProfileAction
}
