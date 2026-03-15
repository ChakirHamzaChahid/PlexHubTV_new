package com.chakir.plexhubtv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.auth.AuthEvent
import com.chakir.plexhubtv.core.network.auth.AuthEventBus
import com.chakir.plexhubtv.core.update.ApkInstaller
import com.chakir.plexhubtv.core.update.UpdateChecker
import com.chakir.plexhubtv.core.update.UpdateInfo
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
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
    private val connectionManager: ConnectionManager,
    private val updateChecker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val _showSessionExpiredDialog = MutableStateFlow(false)
    val showSessionExpiredDialog: StateFlow<Boolean> = _showSessionExpiredDialog.asStateFlow()

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    val availableUpdate: StateFlow<UpdateInfo?> = _availableUpdate.asStateFlow()

    init {
        apkInstaller.cleanupOldApks()
        checkForUpdate()
        authEventBus.events.safeCollectIn(
            scope = viewModelScope,
            onError = { e ->
                Timber.e(e, "MainViewModel: auth events collection failed")
            }
        ) { event ->
            when (event) {
                AuthEvent.TokenInvalid -> handleTokenInvalid()
            }
        }

        // Re-discover server connections after network change so images load with fresh URLs
        connectionManager.connectionRefreshNeeded.safeCollectIn(
            scope = viewModelScope,
            onError = { e ->
                Timber.e(e, "MainViewModel: connection refresh collection failed")
            }
        ) {
            refreshServerConnections()
        }
    }

    private fun refreshServerConnections() {
        viewModelScope.launch {
            try {
                val servers = authRepository.getServers(forceRefresh = false).getOrNull()
                if (servers.isNullOrEmpty()) return@launch

                Timber.i("MainViewModel: Re-discovering connections for ${servers.size} servers after network change")
                servers.map { server ->
                    async { connectionManager.findBestConnection(server) }
                }.awaitAll()
                Timber.i("MainViewModel: Connection re-discovery complete")
            } catch (e: Exception) {
                Timber.w(e, "MainViewModel: Connection re-discovery failed")
            }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val autoCheck = settingsRepository.autoCheckUpdates.firstOrNull() ?: true
            if (!autoCheck) return@launch

            val currentVersion = BuildConfig.VERSION_NAME
            val update = updateChecker.checkForUpdate(currentVersion)
            _availableUpdate.value = update
        }
    }

    fun dismissUpdate() {
        _availableUpdate.value = null
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
