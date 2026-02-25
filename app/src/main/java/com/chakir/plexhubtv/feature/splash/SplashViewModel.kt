package com.chakir.plexhubtv.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent
    data object NavigateToLoading : SplashNavigationEvent
    data object NavigateToLibrarySelection : SplashNavigationEvent
}

/**
 * ViewModel pour l'écran Splash (Netflix-style).
 * Vérifie l'authentification ET attend la fin de la vidéo d'intro avant de naviguer.
 */
@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _navigationEvent = Channel<SplashNavigationEvent>()
        val navigationEvent = _navigationEvent.receiveAsFlow()

        private data class TransitionState(
            val isVideoStarted: Boolean = false,
            val isVideoComplete: Boolean = false,
            val isAuthenticationComplete: Boolean = false,
            val authenticationResult: SplashNavigationEvent? = null,
            val hasNavigated: Boolean = false,
        ) {
            val readyToNavigate: Boolean
                get() = isVideoComplete && isAuthenticationComplete &&
                    authenticationResult != null && !hasNavigated
        }

        private val transitionState = MutableStateFlow(TransitionState())

        companion object {
            /**
             * UX22: Timeout to prevent infinite black screen if video fails to load.
             * After 5s, if video hasn't started, we force navigation to ensure
             * user never gets stuck on splash screen.
             */
            private const val SPLASH_TIMEOUT_MS = 5000L
        }

        init {
            checkAuthAndNavigate()
            startTimeoutTimer()
            observeTransitionState()
        }

        fun onVideoStarted() {
            Timber.d("SPLASH: Video playback started")
            transitionState.update { it.copy(isVideoStarted = true) }
        }

        fun onVideoEnded() {
            Timber.d("SPLASH: Video playback ended")
            transitionState.update { it.copy(isVideoComplete = true) }
        }

        fun onVideoError() {
            Timber.e("SPLASH: Video playback error, forcing navigation fallback")
            transitionState.update { it.copy(isVideoComplete = true) }
        }

        /**
         * UX21: Allow user to skip the splash video
         */
        fun onSkipRequested() {
            Timber.d("SPLASH: User requested skip, forcing navigation")
            transitionState.update { it.copy(isVideoComplete = true) }
        }

        private fun startTimeoutTimer() {
            viewModelScope.launch {
                delay(SPLASH_TIMEOUT_MS)
                val state = transitionState.value
                if (!state.isVideoStarted && !state.hasNavigated) {
                    Timber.w("SPLASH: Video did not start within timeout, forcing fallback navigation")
                    transitionState.update { it.copy(isVideoComplete = true) }
                }
            }
        }

        private fun checkAuthAndNavigate() {
            viewModelScope.launch {
                val result =
                    try {
                        val isAuthenticated = authRepository.checkAuthentication()
                        Timber.d("SPLASH: Authentication check result = $isAuthenticated")
                        if (isAuthenticated) {
                            val isLibrarySelectionDone = settingsDataStore.isLibrarySelectionComplete.first()
                            if (isLibrarySelectionDone) {
                                SplashNavigationEvent.NavigateToLoading
                            } else {
                                SplashNavigationEvent.NavigateToLibrarySelection
                            }
                        } else {
                            SplashNavigationEvent.NavigateToLogin
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "SPLASH: Error checking authentication")
                        SplashNavigationEvent.NavigateToLogin
                    }

                transitionState.update {
                    it.copy(isAuthenticationComplete = true, authenticationResult = result)
                }
            }
        }

        /**
         * Reactively waits for both video and auth to complete, then navigates exactly once.
         * Uses [MutableStateFlow.filter] + [first] to guarantee single-shot navigation
         * without manual tryNavigate() calls.
         */
        private fun observeTransitionState() {
            viewModelScope.launch {
                val state =
                    transitionState
                        .filter { it.readyToNavigate }
                        .first()

                Timber.d("SPLASH: Both video and auth complete, navigating...")
                transitionState.update { it.copy(hasNavigated = true) }
                _navigationEvent.send(state.authenticationResult!!)
            }
        }
    }
