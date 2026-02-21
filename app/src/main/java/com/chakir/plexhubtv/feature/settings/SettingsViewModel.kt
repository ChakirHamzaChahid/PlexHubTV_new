package com.chakir.plexhubtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.work.LibrarySyncWorker
import com.chakir.plexhubtv.work.RatingSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour l'écran des paramètres.
 * Gère le chargement et la modification des préférences utilisateur via [SettingsRepository].
 * Gère également les actions de synchronisation manuelle et de déconnexion.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val authRepository: AuthRepository,
        private val workManager: WorkManager,
        private val syncWatchlistUseCase: com.chakir.plexhubtv.domain.usecase.SyncWatchlistUseCase,
    ) : ViewModel() {
        @Inject
        lateinit var tvChannelManager: com.chakir.plexhubtv.domain.service.TvChannelManager
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<SettingsNavigationEvent>()
        val navigationEvents = _navigationEvents.receiveAsFlow()

        val isTvChannelsEnabled: StateFlow<Boolean> = settingsRepository.isTvChannelsEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

        private var settingsJob: Job? = null

        init {
            Timber.d("SCREEN [Settings]: Opened")
            loadOneTimeData()
            observeSettings()
        }

        fun onAction(action: SettingsAction) {
            Timber.d("ACTION [Settings] Action=${action.javaClass.simpleName}")
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
                is SettingsAction.ForceSync -> {
                    _uiState.update { it.copy(isSyncing = true, syncMessage = null, syncError = null) }

                    val constraints =
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                    val syncRequest =
                        OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                            .setConstraints(constraints)
                            .build()

                    workManager.enqueueUniqueWork(
                        "LibrarySync_Manual",
                        ExistingWorkPolicy.REPLACE,
                        syncRequest,
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
                            _uiState.update {
                                it.copy(
                                    isSyncing = false,
                                    syncMessage = "Synced $count watchlist items successfully",
                                    syncError = null,
                                )
                            }
                            Timber.d("Synced $count watchlist items")
                        } else {
                            _uiState.update {
                                it.copy(
                                    isSyncing = false,
                                    syncMessage = null,
                                    syncError = "Failed to sync watchlist: ${result.exceptionOrNull()?.message}",
                                )
                            }
                            Timber.e("Failed to sync watchlist", result.exceptionOrNull())
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
                is SettingsAction.SaveTmdbApiKey -> {
                    _uiState.update { it.copy(tmdbApiKey = action.key) }
                    viewModelScope.launch { settingsRepository.saveTmdbApiKey(action.key) }
                }
                is SettingsAction.SaveOmdbApiKey -> {
                    _uiState.update { it.copy(omdbApiKey = action.key) }
                    viewModelScope.launch { settingsRepository.saveOmdbApiKey(action.key) }
                }
                is SettingsAction.SaveIptvPlaylistUrl -> {
                    _uiState.update { it.copy(iptvPlaylistUrl = action.url) }
                    viewModelScope.launch { settingsRepository.saveIptvPlaylistUrl(action.url) }
                }
                is SettingsAction.SyncRatings -> {
                    _uiState.update { it.copy(isSyncingRatings = true, ratingSyncMessage = null) }

                    val constraints =
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                    val syncRequest =
                        OneTimeWorkRequestBuilder<RatingSyncWorker>()
                            .setConstraints(constraints)
                            .build()

                    workManager.enqueueUniqueWork(
                        "RatingSync_Manual",
                        ExistingWorkPolicy.KEEP, // Prevent duplicate syncs
                        syncRequest,
                    )

                    // Show immediate feedback
                    _uiState.update { it.copy(isSyncingRatings = false, ratingSyncMessage = "Rating sync started in background...") }

                    // Clear message after 3 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _uiState.update { it.copy(ratingSyncMessage = null) }
                    }
                }
                // Rating Sync Configuration Actions
                is SettingsAction.ChangeRatingSyncSource -> {
                    _uiState.update { it.copy(ratingSyncSource = action.source) }
                    viewModelScope.launch { settingsRepository.saveRatingSyncSource(action.source) }
                }
                is SettingsAction.ChangeRatingSyncDelay -> {
                    _uiState.update { it.copy(ratingSyncDelay = action.delayMs) }
                    viewModelScope.launch { settingsRepository.saveRatingSyncDelay(action.delayMs) }
                }
                is SettingsAction.ToggleRatingSyncBatching -> {
                    _uiState.update { it.copy(ratingSyncBatchingEnabled = action.enabled) }
                    viewModelScope.launch { settingsRepository.saveRatingSyncBatchingEnabled(action.enabled) }
                }
                is SettingsAction.ChangeRatingSyncDailyLimit -> {
                    _uiState.update { it.copy(ratingSyncDailyLimit = action.limit) }
                    viewModelScope.launch { settingsRepository.saveRatingSyncDailyLimit(action.limit) }
                }
                is SettingsAction.ResetRatingSyncProgress -> {
                    viewModelScope.launch {
                        settingsRepository.resetRatingSyncProgress()
                        Timber.d("Rating sync progress reset")
                    }
                }
            }
        }

        fun setTvChannelsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setTvChannelsEnabled(enabled)

                // Trigger immediate action based on state
                if (!enabled) {
                    tvChannelManager.deleteChannel()
                } else {
                    tvChannelManager.createChannelIfNeeded()
                    tvChannelManager.updateContinueWatching()
                }
            }
        }

        private fun loadOneTimeData() {
            viewModelScope.launch {
                val size = settingsRepository.getCacheSize()
                _uiState.update { it.copy(cacheSize = formatFileSize(size)) }
            }
            viewModelScope.launch {
                val serverStart = System.currentTimeMillis()
                val serversResult = authRepository.getServers()
                val duration = System.currentTimeMillis() - serverStart
                serversResult.getOrNull()?.let { servers ->
                    Timber.i("SCREEN [Settings] SUCCESS: Servers loaded in ${duration}ms | Count=${servers.size}")
                    val serverNames = servers.map { it.name }
                    val serverMap = servers.associate { it.name to it.clientIdentifier }
                    _uiState.update { it.copy(availableServers = serverNames, availableServersMap = serverMap) }
                }
            }
        }

        private fun observeSettings() {
            settingsJob?.cancel()

            // Group 1: Core settings (5 flows)
            val core = combine(
                settingsRepository.appTheme,
                settingsRepository.getVideoQuality(),
                settingsRepository.isCacheEnabled,
                settingsRepository.defaultServer,
                settingsRepository.playerEngine,
            ) { theme, quality, cacheEnabled, server, engine ->
                val themeEnum = try { AppTheme.valueOf(theme) } catch (_: Exception) { AppTheme.Plex }
                { s: SettingsUiState ->
                    s.copy(
                        theme = themeEnum,
                        videoQuality = quality,
                        isCacheEnabled = cacheEnabled,
                        defaultServer = server,
                        playerEngine = engine,
                    )
                }
            }

            // Group 2: Language & exclusion preferences (3 flows)
            val prefs = combine(
                settingsRepository.preferredAudioLanguage,
                settingsRepository.preferredSubtitleLanguage,
                settingsRepository.excludedServerIds,
            ) { audio, subtitle, excluded ->
                { s: SettingsUiState ->
                    s.copy(
                        preferredAudioLanguage = audio,
                        preferredSubtitleLanguage = subtitle,
                        excludedServerIds = excluded,
                    )
                }
            }

            // Group 3: External API keys (3 flows)
            val apiKeys = combine(
                settingsRepository.getTmdbApiKey(),
                settingsRepository.getOmdbApiKey(),
                settingsRepository.iptvPlaylistUrl,
            ) { tmdb, omdb, iptv ->
                { s: SettingsUiState ->
                    s.copy(
                        tmdbApiKey = tmdb ?: "",
                        omdbApiKey = omdb ?: "",
                        iptvPlaylistUrl = iptv ?: "",
                    )
                }
            }

            // Group 4: Rating sync configuration (4 flows)
            val ratingSyncConfig = combine(
                settingsRepository.ratingSyncSource,
                settingsRepository.ratingSyncDelay,
                settingsRepository.ratingSyncBatchingEnabled,
                settingsRepository.ratingSyncDailyLimit,
            ) { source, delay, batching, limit ->
                { s: SettingsUiState ->
                    s.copy(
                        ratingSyncSource = source,
                        ratingSyncDelay = delay,
                        ratingSyncBatchingEnabled = batching,
                        ratingSyncDailyLimit = limit,
                    )
                }
            }

            // Group 5: Rating sync progress (2 flows)
            val ratingSyncProgress = combine(
                settingsRepository.ratingSyncProgressSeries,
                settingsRepository.ratingSyncProgressMovies,
            ) { series, movies ->
                { s: SettingsUiState ->
                    s.copy(ratingSyncProgressSeries = series, ratingSyncProgressMovies = movies)
                }
            }

            // Single combined collector — applies all groups to the current state
            settingsJob = combine(core, prefs, apiKeys, ratingSyncConfig, ratingSyncProgress) { c, p, a, rc, rp ->
                { state: SettingsUiState -> rp(rc(a(p(c(state))))) }
            }
                .onEach { updater -> _uiState.update { current -> updater(current) } }
                .catch { e -> Timber.e(e, "SettingsViewModel: settings observation failed") }
                .launchIn(viewModelScope)
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
