package com.chakir.plexhubtv.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import com.chakir.plexhubtv.domain.service.AnalyticsService

/**
 * ViewModel gérant le flux d'authentification.
 * Responsabilités :
 * - Initialiser le Login via PIN (Polling).
 * - Login via Token (Dev/Test).
 * - Vérifier l'état d'authentification au démarrage.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val analyticsService: AnalyticsService,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        private var pollingJob: Job? = null
        private var loginJob: Job? = null

        init {
            viewModelScope.launch {
                if (authRepository.checkAuthentication()) {
                    fetchServers()
                } else if (com.chakir.plexhubtv.BuildConfig.PLEX_TOKEN.isNotBlank()) {
                    doLoginWithToken(com.chakir.plexhubtv.BuildConfig.PLEX_TOKEN)
                }
            }
        }

        fun onEvent(event: AuthEvent) {
            when (event) {
                AuthEvent.StartAuth -> startAuthFlow()
                AuthEvent.Retry -> startAuthFlow()
                AuthEvent.Cancel -> cancelAuth()
                is AuthEvent.OpenBrowser -> { /* Handled in UI, or trigger intent here */ }
                is AuthEvent.SubmitToken -> loginWithToken(event.token)
                AuthEvent.ScanQr -> { /* QR code scanning not implemented */ }
            }
        }

        private fun loginWithToken(token: String) {
            loginJob?.cancel()
            loginJob = viewModelScope.launch {
                doLoginWithToken(token)
            }
        }

        private suspend fun doLoginWithToken(token: String) {
            _uiState.value = AuthUiState.Authenticating(pinCode = "Verifying Token...", pinId = "")
            try {
                withTimeout(20_000) {
                    authRepository.loginWithToken(token.trim())
                        .onSuccess {
                            fetchServers()
                        }
                        .onFailure { e ->
                            analyticsService.logEvent("auth_failed", mapOf("method" to "token"))
                            _uiState.value = AuthUiState.Error("Token verification failed: ${e.message}")
                        }
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.value = AuthUiState.Error("Token verification timed out. Check your network connection.")
            }
        }

        private fun startAuthFlow() {
            cancelAuth() // Cancel any existing flow
            pollingJob =
                viewModelScope.launch {
                    _uiState.value =
                        AuthUiState.Authenticating(
                            pinCode = "Loading...",
                            pinId = "",
                            progress = 0f,
                        )

                    authRepository.getPin(strong = false)
                        .onSuccess { authPin ->
                            _uiState.update {
                                AuthUiState.Authenticating(
                                    pinCode = authPin.code,
                                    pinId = authPin.id,
                                    progress = 0f,
                                    authUrl = authPin.url,
                                )
                            }
                            startPolling(authPin.id)
                        }
                        .onFailure { e ->
                            _uiState.value = AuthUiState.Error("Failed to get PIN: ${e.message}")
                        }
                }
        }

        private suspend fun startPolling(pinId: String) {
            val timeoutMs = 120_000L // 2 minutes
            val startTime = System.currentTimeMillis()

            while (currentCoroutineContext().isActive && System.currentTimeMillis() - startTime < timeoutMs) {
                delay(2000) // Poll every 2 seconds

                val progress = (System.currentTimeMillis() - startTime).toFloat() / timeoutMs
                _uiState.update { state ->
                    if (state is AuthUiState.Authenticating) state.copy(progress = progress) else state
                }

                val result = authRepository.checkPin(pinId)
                if (result.getOrNull() == true) {
                    // Linked! Fetch servers
                    fetchServers()
                    return
                }
            }
            analyticsService.logEvent("auth_timeout")
            _uiState.value = AuthUiState.Error("Authentication timed out")
        }

        private suspend fun fetchServers() {
            authRepository.getServers()
                .onSuccess { servers ->
                    analyticsService.logEvent("auth_success", mapOf("server_count" to servers.size.toLong()))
                    _uiState.value = AuthUiState.Success(servers)
                }
                .onFailure { e ->
                    _uiState.value = AuthUiState.Error("Linked, but failed to load servers: ${e.message}")
                }
        }

        private fun cancelAuth() {
            pollingJob?.cancel()
            loginJob?.cancel()
            _uiState.value = AuthUiState.Idle
        }
    }
