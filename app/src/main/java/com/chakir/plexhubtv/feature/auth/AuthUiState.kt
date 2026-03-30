package com.chakir.plexhubtv.feature.auth

import androidx.compose.runtime.Immutable
import com.chakir.plexhubtv.core.model.Server
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * États de l'interface d'authentification.
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState

    /**
     * En cours d'authentification (affichage du PIN code à saisir sur plex.tv/link).
     */
    data class Authenticating(
        val pinCode: String,
        val pinId: String,
        val progress: Float? = null,
        val authUrl: String = "https://plex.tv/link",
    ) : AuthUiState

    data class Error(val message: String) : AuthUiState

    @Immutable
    data class Success(val servers: ImmutableList<Server> = persistentListOf()) : AuthUiState
}

sealed interface AuthEvent {
    data object StartAuth : AuthEvent

    data object Retry : AuthEvent

    data object Cancel : AuthEvent

    data class OpenBrowser(val url: String) : AuthEvent

    data class SubmitToken(val token: String) : AuthEvent

    data object ScanQr : AuthEvent // Placeholder for future camera integration
}
