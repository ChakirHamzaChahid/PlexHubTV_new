package com.chakir.plexhubtv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.auth.AuthEvent
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application-level ViewModel for global auth coordination.
 *
 * Lives in MainActivity scope, survives navigation. Collects auth events
 * from AuthEventBus and coordinates dialog display and token clearing.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authEventBus: AuthEventBus,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _showSessionExpiredDialog = MutableStateFlow(false)
    val showSessionExpiredDialog: StateFlow<Boolean> = _showSessionExpiredDialog.asStateFlow()

    init {
        viewModelScope.launch {
            authEventBus.events.collect { event ->
                when (event) {
                    AuthEvent.TokenInvalid -> handleTokenInvalid()
                }
            }
        }
    }

    private suspend fun handleTokenInvalid() {
        // Prevent dialog spam from multiple simultaneous 401s
        if (!_showSessionExpiredDialog.value) {
            Timber.w("Token invalidated (401 detected), clearing token and showing dialog")

            try {
                authRepository.clearToken()
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear token, but continuing to show dialog")
            }

            _showSessionExpiredDialog.value = true

            // Track session expiration for monitoring (non-critical)
            try {
                Firebase.analytics.logEvent("session_expired") {
                    param("source", "401_interceptor")
                }
            } catch (e: Exception) {
                Timber.d(e, "Firebase analytics unavailable, skipping event")
            }
        }
    }

    /**
     * Called when user dismisses the session expired dialog.
     * Hides dialog and invokes navigation callback.
     */
    fun onSessionExpiredDialogDismissed(navigateToAuth: () -> Unit) {
        _showSessionExpiredDialog.value = false
        navigateToAuth()
    }
}
