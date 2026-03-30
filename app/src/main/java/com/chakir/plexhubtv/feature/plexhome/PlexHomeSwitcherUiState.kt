package com.chakir.plexhubtv.feature.plexhome

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.PlexHomeUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * État de l'UI pour l'écran de changement de profil.
 */
@Immutable
data class PlexHomeSwitcherUiState(
    val isLoading: Boolean = false,
    val users: ImmutableList<PlexHomeUser> = persistentListOf(),
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
