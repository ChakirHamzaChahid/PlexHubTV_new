package com.chakir.plexhubtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chakir.plexhubtv.work.LibrarySyncWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour l'écran des paramètres.
 * Gère le chargement et la modification des préférences utilisateur via [SettingsRepository].
 * Gère également les actions de synchronisation manuelle et de déconnexion.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val workManager: WorkManager,
    private val syncWatchlistUseCase: com.chakir.plexhubtv.domain.usecase.SyncWatchlistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvents = Channel<SettingsNavigationEvent>()
    val navigationEvents = _navigationEvents.receiveAsFlow()

    init {
        android.util.Log.d("METRICS", "SCREEN [Settings]: Opened")
        loadSettings()
    }

    fun onAction(action: SettingsAction) {
        android.util.Log.d("METRICS", "ACTION [Settings] Action=${action.javaClass.simpleName}")
        when (action) {
            is SettingsAction.ChangeTheme -> {
                _uiState.update { it.copy(theme = action.theme) }
                viewModelScope.launch {
                    settingsRepository.setAppTheme(action.theme.name)
                }
            }
            is SettingsAction.ChangeVideoQuality -> {
                _uiState.update { it.copy(videoQuality = action.quality) }
                viewModelScope.launch {
                    settingsRepository.setVideoQuality(action.quality)
                }
            }
            is SettingsAction.ClearCache -> {
                 viewModelScope.launch {
                    settingsRepository.clearCache()
                    settingsRepository.clearDatabase()
                    _uiState.update { it.copy(cacheSize = formatFileSize(0)) }
                 }
            }
            is SettingsAction.SelectDefaultServer -> {
                _uiState.update { it.copy(defaultServer = action.serverName) }
                 viewModelScope.launch {
                    settingsRepository.setDefaultServer(action.serverName)
                }
            }
            is SettingsAction.ChangePlayerEngine -> {
                _uiState.update { it.copy(playerEngine = action.engine) }
                viewModelScope.launch { settingsRepository.setPlayerEngine(action.engine) }
            }
             is SettingsAction.Logout -> {
                viewModelScope.launch {
                    settingsRepository.clearSession()
                    _navigationEvents.send(SettingsNavigationEvent.NavigateToLogin)
                }
            }
            is SettingsAction.Back -> {
                viewModelScope.launch { _navigationEvents.send(SettingsNavigationEvent.NavigateBack) }
            }
            is SettingsAction.CheckServerStatus -> {
                viewModelScope.launch { _navigationEvents.send(SettingsNavigationEvent.NavigateToServerStatus) }
            }
            is SettingsAction.CheckServerStatus -> {
                viewModelScope.launch { _navigationEvents.send(SettingsNavigationEvent.NavigateToServerStatus) }
            }
            is SettingsAction.ForceSync -> {
                _uiState.update { it.copy(isSyncing = true, syncMessage = null, syncError = null) }
                
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                
                val syncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                    .setConstraints(constraints)
                    .build()
                
                workManager.enqueueUniqueWork(
                    "LibrarySync_Manual",
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
                
                // Show immediate feedback
                _uiState.update { it.copy(isSyncing = false, syncMessage = "Library sync started...") }
                
                // Clear message after 3 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(syncMessage = null) }
                }
            }
            is SettingsAction.SyncWatchlist -> {
                _uiState.update { it.copy(isSyncing = true, syncMessage = null, syncError = null) }
                
                viewModelScope.launch {
                    val result = syncWatchlistUseCase()
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        _uiState.update { it.copy(
                            isSyncing = false,
                            syncMessage = "Synced $count watchlist items successfully",
                            syncError = null
                        ) }
                        android.util.Log.d("SettingsViewModel", "Synced $count watchlist items")
                    } else {
                        _uiState.update { it.copy(
                            isSyncing = false,
                            syncMessage = null,
                            syncError = "Failed to sync watchlist: ${result.exceptionOrNull()?.message}"
                        ) }
                        android.util.Log.e("SettingsViewModel", "Failed to sync watchlist", result.exceptionOrNull())
                    }
                    
                    // Clear message after 5 seconds
                    kotlinx.coroutines.delay(5000)
                    _uiState.update { it.copy(syncMessage = null, syncError = null) }
                }
            }
            is SettingsAction.ChangePreferredAudioLanguage -> {
                _uiState.update { it.copy(preferredAudioLanguage = action.language) }
                viewModelScope.launch { settingsRepository.setPreferredAudioLanguage(action.language) }
            }
            is SettingsAction.ChangePreferredSubtitleLanguage -> {
                _uiState.update { it.copy(preferredSubtitleLanguage = action.language) }
                viewModelScope.launch { settingsRepository.setPreferredSubtitleLanguage(action.language) }
            }
            is SettingsAction.ToggleServerExclusion -> {
                viewModelScope.launch { settingsRepository.toggleServerExclusion(action.serverId) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Calculate cache size
            launch {
                val size = settingsRepository.getCacheSize()
                _uiState.update { it.copy(cacheSize = formatFileSize(size)) }
            }

            // Load settings
            launch {
                settingsRepository.appTheme.collect { themeName ->
                    val themeEnum = try {
                        AppTheme.valueOf(themeName)
                    } catch (e: Exception) {
                        AppTheme.Plex
                    }
                    _uiState.update { it.copy(theme = themeEnum) }
                }
            }
             launch {
                 settingsRepository.getVideoQuality().collect { quality: String ->
                     _uiState.update { it.copy(videoQuality = quality) }
                 }
             }
             launch {
                 settingsRepository.isCacheEnabled.collect { enabled ->
                     _uiState.update { it.copy(isCacheEnabled = enabled) }
                 }
             }
             launch {
                 settingsRepository.defaultServer.collect { server ->
                     _uiState.update { it.copy(defaultServer = server) }
                 }
             }
             launch {
                 settingsRepository.playerEngine.collect { engine ->
                     _uiState.update { it.copy(playerEngine = engine) }
                 }
             }
             
             // Fetch Servers
             launch {
                 val serverStart = System.currentTimeMillis()
                 val serversResult = authRepository.getServers()
                 val duration = System.currentTimeMillis() - serverStart
                 
                 serversResult.getOrNull()?.let { servers ->
                     android.util.Log.i("METRICS", "SCREEN [Settings] SUCCESS: Servers loaded in ${duration}ms | Count=${servers.size}")
                     val serverNames = servers.map { it.name }
                     val serverMap = servers.associate { it.name to it.clientIdentifier }
                     _uiState.update { it.copy(availableServers = serverNames, availableServersMap = serverMap) }
                 }
             }

             launch {
                 settingsRepository.preferredAudioLanguage.collect { lang ->
                     _uiState.update { it.copy(preferredAudioLanguage = lang) }
                 }
             }
             launch {
                 settingsRepository.preferredSubtitleLanguage.collect { lang ->
                     _uiState.update { it.copy(preferredSubtitleLanguage = lang) }
                 }
             }
             launch {
                 settingsRepository.excludedServerIds.collect { excluded ->
                     _uiState.update { it.copy(excludedServerIds = excluded) }
                 }
             }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

sealed interface SettingsNavigationEvent {
    data object NavigateBack : SettingsNavigationEvent
    data object NavigateToLogin : SettingsNavigationEvent
    data object NavigateToServerStatus : SettingsNavigationEvent
}
