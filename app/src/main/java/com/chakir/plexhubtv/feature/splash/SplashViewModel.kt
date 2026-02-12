package com.chakir.plexhubtv.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SplashNavigationEvent {
    data object NavigateToLogin : SplashNavigationEvent
    data object NavigateToLoading : SplashNavigationEvent
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
    ) : ViewModel() {
        private val _navigationEvent = Channel<SplashNavigationEvent>()
        val navigationEvent = _navigationEvent.receiveAsFlow()

        private var isAuthenticationComplete = false
        private var authenticationResult: SplashNavigationEvent? = null
        private var isVideoComplete = false

        init {
            checkAuthAndNavigate()
        }

        fun onVideoEnded() {
            Timber.d("SPLASH: Video playback ended")
            isVideoComplete = true
            tryNavigate()
        }

        private fun checkAuthAndNavigate() {
            viewModelScope.launch {
                try {
                    val isAuthenticated = authRepository.checkAuthentication()
                    Timber.d("SPLASH: Authentication check result = $isAuthenticated")

                    authenticationResult = if (isAuthenticated) {
                        SplashNavigationEvent.NavigateToLoading
                    } else {
                        SplashNavigationEvent.NavigateToLogin
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SPLASH: Error checking authentication")
                    authenticationResult = SplashNavigationEvent.NavigateToLogin
                }

                isAuthenticationComplete = true
                tryNavigate()
            }
        }

        private fun tryNavigate() {
            viewModelScope.launch {
                // Only navigate when BOTH video ended AND authentication is complete
                if (isVideoComplete && isAuthenticationComplete && authenticationResult != null) {
                    Timber.d("SPLASH: Both video and auth complete, navigating...")
                    _navigationEvent.send(authenticationResult!!)
                }
            }
        }
    }
