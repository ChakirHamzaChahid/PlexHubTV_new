package com.chakir.plexhubtv.feature.plexhome

import com.chakir.plexhubtv.core.model.PlexHomeUser

/**
 * État de l'UI pour l'écran de changement de profil.
 */
data class PlexHomeSwitcherUiState(
    val isLoading: Boolean = false,
    val users: List<PlexHomeUser> = emptyList(),
    val error: String? = null,
    val showPinDialog: Boolean = false,
    val selectedUser: PlexHomeUser? = null,
    val pinValue: String = "",
    val isSwitching: Boolean = false,
    val switchSuccess: Boolean = false,
)

sealed interface PlexHomeSwitcherAction {
    data object LoadUsers : PlexHomeSwitcherAction

    data class SelectUser(val user: PlexHomeUser) : PlexHomeSwitcherAction

    data object CancelPin : PlexHomeSwitcherAction

    data class EnterPinDigit(val digit: String) : PlexHomeSwitcherAction

    data object ClearPin : PlexHomeSwitcherAction

    data object SubmitPin : PlexHomeSwitcherAction
}
