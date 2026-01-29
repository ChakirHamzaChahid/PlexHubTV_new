package com.chakir.plexhubtv.feature.auth

import com.chakir.plexhubtv.domain.model.Server

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data class Authenticating(
        val pinCode: String,
        val pinId: String,
        val progress: Float? = null,
        val authUrl: String = "https://plex.tv/link"
    ) : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class Success(val servers: List<Server>) : AuthUiState
}

sealed interface AuthEvent {
    data object StartAuth : AuthEvent
    data object Retry : AuthEvent
    data object Cancel : AuthEvent
    data class OpenBrowser(val url: String) : AuthEvent
    data class SubmitToken(val token: String) : AuthEvent 
    data object ScanQr : AuthEvent // Placeholder for future camera integration
}
