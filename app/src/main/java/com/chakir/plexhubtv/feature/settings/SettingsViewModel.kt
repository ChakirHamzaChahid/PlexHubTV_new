package com.chakir.plexhubtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.flow.first
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
        private val tvChannelManager: com.chakir.plexhubtv.domain.service.TvChannelManager,
        private val syncXtreamLibraryUseCase: com.chakir.plexhubtv.domain.usecase.SyncXtreamLibraryUseCase,
        private val xtreamAccountRepository: com.chakir.plexhubtv.domain.repository.XtreamAccountRepository,
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
        private val backendRepository: com.chakir.plexhubtv.domain.repository.BackendRepository,
        private val jellyfinServerRepository: com.chakir.plexhubtv.domain.repository.JellyfinServerRepository,
        private val syncJellyfinLibraryUseCase: com.chakir.plexhubtv.domain.usecase.SyncJellyfinLibraryUseCase,
        private val updateChecker: com.chakir.plexhubtv.core.update.UpdateChecker,
    ) : ViewModel() {
        companion object {
            const val ALL_SERVERS_SENTINEL = "all"
        }

        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private val _navigationEvents = Channel<SettingsNavigationEvent>(Channel.BUFFERED)
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
            observeBackendServers()
            observeXtreamAccounts()
            observeJellyfinServers()
            _uiState.update { it.copy(hasParentalPin = settingsRepository.hasParentalPin()) }
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
                is SettingsAction.ChangeDeinterlaceMode -> {
                    _uiState.update { it.copy(deinterlaceMode = action.mode) }
                    viewModelScope.launch { settingsRepository.setDeinterlaceMode(action.mode) }
                }
                is SettingsAction.ToggleAutoPlayNext -> {
                    _uiState.update { it.copy(autoPlayNextEnabled = action.enabled) }
                    viewModelScope.launch { settingsRepository.setAutoPlayNext(action.enabled) }
                }
                is SettingsAction.ChangeSkipIntroMode -> {
                    _uiState.update { it.copy(skipIntroMode = action.mode) }
                    viewModelScope.launch { settingsRepository.setSkipIntroMode(action.mode) }
                }
                is SettingsAction.ChangeSkipCreditsMode -> {
                    _uiState.update { it.copy(skipCreditsMode = action.mode) }
                    viewModelScope.launch { settingsRepository.setSkipCreditsMode(action.mode) }
                }
                is SettingsAction.ToggleThemeSong -> {
                    _uiState.update { it.copy(themeSongEnabled = action.enabled) }
                    viewModelScope.launch { settingsRepository.setThemeSongEnabled(action.enabled) }
                }
                is SettingsAction.ToggleScreensaver -> {
                    _uiState.update { it.copy(screensaverEnabled = action.enabled) }
                    viewModelScope.launch { settingsRepository.setScreensaverEnabled(action.enabled) }
                }
                is SettingsAction.ChangeScreensaverInterval -> {
                    _uiState.update { it.copy(screensaverIntervalSeconds = action.seconds) }
                    viewModelScope.launch { settingsRepository.setScreensaverIntervalSeconds(action.seconds) }
                }
                is SettingsAction.ToggleScreensaverClock -> {
                    _uiState.update { it.copy(screensaverShowClock = action.enabled) }
                    viewModelScope.launch { settingsRepository.setScreensaverShowClock(action.enabled) }
                }
                is SettingsAction.ToggleShowContinueWatching -> {
                    _uiState.update { it.copy(showContinueWatching = action.enabled) }
                    viewModelScope.launch { settingsRepository.setShowContinueWatching(action.enabled) }
                }
                is SettingsAction.ToggleShowMyList -> {
                    _uiState.update { it.copy(showMyList = action.enabled) }
                    viewModelScope.launch { settingsRepository.setShowMyList(action.enabled) }
                }
                is SettingsAction.ToggleShowSuggestions -> {
                    _uiState.update { it.copy(showSuggestions = action.enabled) }
                    viewModelScope.launch { settingsRepository.setShowSuggestions(action.enabled) }
                }
                is SettingsAction.MoveHomeRowUp -> {
                    val current = _uiState.value.homeRowOrder.toMutableList()
                    val idx = current.indexOf(action.rowId)
                    if (idx > 0) {
                        current[idx] = current[idx - 1].also { current[idx - 1] = current[idx] }
                        _uiState.update { it.copy(homeRowOrder = current) }
                        viewModelScope.launch { settingsRepository.saveHomeRowOrder(current) }
                    }
                }
                is SettingsAction.MoveHomeRowDown -> {
                    val current = _uiState.value.homeRowOrder.toMutableList()
                    val idx = current.indexOf(action.rowId)
                    if (idx >= 0 && idx < current.size - 1) {
                        current[idx] = current[idx + 1].also { current[idx + 1] = current[idx] }
                        _uiState.update { it.copy(homeRowOrder = current) }
                        viewModelScope.launch { settingsRepository.saveHomeRowOrder(current) }
                    }
                }
                is SettingsAction.ToggleShowYearOnCards -> {
                    _uiState.update { it.copy(showYearOnCards = action.enabled) }
                    viewModelScope.launch { settingsRepository.setShowYearOnCards(action.enabled) }
                }
                is SettingsAction.ChangeGridColumnsCount -> {
                    _uiState.update { it.copy(gridColumnsCount = action.count) }
                    viewModelScope.launch { settingsRepository.setGridColumnsCount(action.count) }
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
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                            .build()

                    workManager.enqueueUniqueWork(
                        "LibrarySync_Manual",
                        ExistingWorkPolicy.REPLACE,
                        syncRequest,
                    )

                    // Observe WorkManager to know when sync finishes
                    viewModelScope.launch {
                        workManager.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
                            when (workInfo?.state) {
                                WorkInfo.State.SUCCEEDED -> {
                                    _uiState.update { it.copy(isSyncing = false, syncMessage = "Library sync completed") }
                                    kotlinx.coroutines.delay(3000)
                                    _uiState.update { it.copy(syncMessage = null) }
                                    return@collect
                                }
                                WorkInfo.State.FAILED -> {
                                    _uiState.update { it.copy(isSyncing = false, syncError = "Library sync failed") }
                                    kotlinx.coroutines.delay(5000)
                                    _uiState.update { it.copy(syncError = null) }
                                    return@collect
                                }
                                WorkInfo.State.CANCELLED -> {
                                    _uiState.update { it.copy(isSyncing = false) }
                                    return@collect
                                }
                                else -> { /* ENQUEUED, RUNNING, BLOCKED — keep spinner */ }
                            }
                        }
                    }
                }
                is SettingsAction.SyncWatchlist -> {
                    _uiState.update { it.copy(isSyncingWatchlist = true, syncMessage = null, syncError = null) }

                    viewModelScope.launch {
                        val result = syncWatchlistUseCase()
                        if (result.isSuccess) {
                            val count = result.getOrNull() ?: 0
                            _uiState.update {
                                it.copy(
                                    isSyncingWatchlist = false,
                                    syncMessage = "Synced $count watchlist items successfully",
                                    syncError = null,
                                )
                            }
                            Timber.d("Synced $count watchlist items")
                        } else {
                            _uiState.update {
                                it.copy(
                                    isSyncingWatchlist = false,
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
                is SettingsAction.ChangeMetadataLanguage -> {
                    _uiState.update { it.copy(metadataLanguage = action.language) }
                    viewModelScope.launch { settingsRepository.setMetadataLanguage(action.language) }
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
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                            .build()

                    workManager.enqueueUniqueWork(
                        "RatingSync_Manual",
                        ExistingWorkPolicy.KEEP, // Prevent duplicate syncs
                        syncRequest,
                    )

                    // Observe WorkManager to know when sync finishes
                    viewModelScope.launch {
                        workManager.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
                            when (workInfo?.state) {
                                WorkInfo.State.SUCCEEDED -> {
                                    _uiState.update { it.copy(isSyncingRatings = false, ratingSyncMessage = "Rating sync completed") }
                                    kotlinx.coroutines.delay(3000)
                                    _uiState.update { it.copy(ratingSyncMessage = null) }
                                    return@collect
                                }
                                WorkInfo.State.FAILED -> {
                                    _uiState.update { it.copy(isSyncingRatings = false, ratingSyncMessage = "Rating sync failed") }
                                    kotlinx.coroutines.delay(5000)
                                    _uiState.update { it.copy(ratingSyncMessage = null) }
                                    return@collect
                                }
                                WorkInfo.State.CANCELLED -> {
                                    _uiState.update { it.copy(isSyncingRatings = false) }
                                    return@collect
                                }
                                else -> { /* ENQUEUED, RUNNING, BLOCKED — keep spinner */ }
                            }
                        }
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
                is SettingsAction.SwitchPlexUser -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToPlexHomeSwitch)
                    }
                }
                is SettingsAction.ManageAppProfiles -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToAppProfiles)
                    }
                }
                is SettingsAction.ManageLibrarySelection -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToLibrarySelection)
                    }
                }
                is SettingsAction.ManageJellyfinServers -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToJellyfinSetup)
                    }
                }
                is SettingsAction.SyncJellyfin -> {
                    _uiState.update { it.copy(isSyncingJellyfin = true, jellyfinSyncMessage = null) }
                    viewModelScope.launch {
                        try {
                            val result = syncJellyfinLibraryUseCase()
                            val msg = if (result.isSuccess) {
                                "Jellyfin sync completed (${result.getOrDefault(0)} items)"
                            } else {
                                "Jellyfin sync failed: ${result.exceptionOrNull()?.message}"
                            }
                            _uiState.update { it.copy(isSyncingJellyfin = false, jellyfinSyncMessage = msg) }
                        } catch (e: Exception) {
                            Timber.e(e, "Jellyfin sync failed")
                            _uiState.update {
                                it.copy(isSyncingJellyfin = false, jellyfinSyncMessage = "Sync failed: ${e.message}")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(jellyfinSyncMessage = null) }
                    }
                }
                is SettingsAction.ManageXtreamAccounts -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToXtreamSetup)
                    }
                }
                is SettingsAction.SyncXtream -> {
                    _uiState.update { it.copy(isSyncingXtream = true, xtreamSyncMessage = null) }

                    viewModelScope.launch {
                        try {
                            val accounts = xtreamAccountRepository.observeAccounts().first()
                            if (accounts.isEmpty()) {
                                _uiState.update {
                                    it.copy(isSyncingXtream = false, xtreamSyncMessage = "No Xtream accounts configured")
                                }
                            } else {
                                val selectedCatIds = settingsDataStore.selectedXtreamCategoryIds.first()
                                var errors = 0
                                accounts.forEach { account ->
                                    val result = syncXtreamLibraryUseCase(account.id, selectedCatIds)
                                    if (result.isFailure) {
                                        errors++
                                        Timber.w("Xtream sync failed for ${account.label}: ${result.exceptionOrNull()?.message}")
                                    }
                                }
                                val msg = if (errors == 0) {
                                    "Xtream sync completed (${accounts.size} account(s))"
                                } else {
                                    "Xtream sync: ${accounts.size - errors}/${accounts.size} accounts OK"
                                }
                                _uiState.update { it.copy(isSyncingXtream = false, xtreamSyncMessage = msg) }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Xtream sync failed")
                            _uiState.update {
                                it.copy(isSyncingXtream = false, xtreamSyncMessage = "Sync failed: ${e.message}")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(xtreamSyncMessage = null) }
                    }
                }
                is SettingsAction.ManageXtreamCategories -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToXtreamCategorySelection(action.accountId))
                    }
                }
                is SettingsAction.AddBackendServer -> {
                    _uiState.update { it.copy(isTestingBackend = true, backendConfigMessage = null) }
                    viewModelScope.launch {
                        val result = backendRepository.addServer(action.label, action.url)
                        if (result.isSuccess) {
                            _uiState.update { it.copy(isTestingBackend = false, backendConfigMessage = "Server added") }
                        } else {
                            _uiState.update {
                                it.copy(isTestingBackend = false, backendConfigMessage = "Failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                        kotlinx.coroutines.delay(3000)
                        _uiState.update { it.copy(backendConfigMessage = null) }
                    }
                }
                is SettingsAction.RemoveBackendServer -> {
                    viewModelScope.launch {
                        backendRepository.removeServer(action.id)
                    }
                }
                is SettingsAction.TestBackendConnection -> {
                    _uiState.update { it.copy(isTestingBackend = true, backendConfigMessage = null) }
                    viewModelScope.launch {
                        val result = backendRepository.testConnection(action.url)
                        if (result.isSuccess) {
                            val info = result.getOrThrow()
                            _uiState.update {
                                it.copy(isTestingBackend = false, backendConfigMessage = "Connected (${info.totalMedia} media, ${info.enrichedMedia} enriched)")
                            }
                        } else {
                            _uiState.update {
                                it.copy(isTestingBackend = false, backendConfigMessage = "Unreachable")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(backendConfigMessage = null) }
                    }
                }
                is SettingsAction.SetParentalPin -> {
                    settingsRepository.setParentalPin(action.pin)
                    _uiState.update { it.copy(hasParentalPin = true) }
                }
                is SettingsAction.ClearParentalPin -> {
                    settingsRepository.setParentalPin(null)
                    _uiState.update { it.copy(hasParentalPin = false) }
                }
                is SettingsAction.ToggleAutoCheckUpdates -> {
                    _uiState.update { it.copy(autoCheckUpdates = action.enabled) }
                    viewModelScope.launch { settingsRepository.setAutoCheckUpdates(action.enabled) }
                }
                is SettingsAction.CheckForUpdates -> {
                    _uiState.update { it.copy(isCheckingForUpdate = true, updateCheckMessage = null) }
                    viewModelScope.launch {
                        val update = updateChecker.checkForUpdate(com.chakir.plexhubtv.BuildConfig.VERSION_NAME)
                        if (update != null) {
                            _uiState.update {
                                it.copy(isCheckingForUpdate = false, updateCheckMessage = "Update available: v${update.versionName}")
                            }
                        } else {
                            _uiState.update {
                                it.copy(isCheckingForUpdate = false, updateCheckMessage = "You're up to date!")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(updateCheckMessage = null) }
                    }
                }
                is SettingsAction.NavigateToSubtitleStyle -> {
                    viewModelScope.launch {
                        _navigationEvents.send(SettingsNavigationEvent.NavigateToSubtitleStyle)
                    }
                }
                is SettingsAction.SyncBackend -> {
                    _uiState.update { it.copy(isSyncingBackend = true, backendSyncMessage = null) }
                    viewModelScope.launch {
                        try {
                            val servers = backendRepository.observeServers().first()
                            if (servers.isEmpty()) {
                                _uiState.update { it.copy(isSyncingBackend = false, backendSyncMessage = "No backend servers configured") }
                            } else {
                                var totalSynced = 0
                                servers.filter { it.isActive }.forEach { server ->
                                    val result = backendRepository.syncMedia(server.id)
                                    totalSynced += result.getOrDefault(0)
                                }
                                _uiState.update {
                                    it.copy(isSyncingBackend = false, backendSyncMessage = "Synced $totalSynced items from backend")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Backend sync failed")
                            _uiState.update {
                                it.copy(isSyncingBackend = false, backendSyncMessage = "Sync failed: ${e.message}")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(backendSyncMessage = null) }
                    }
                }
                is SettingsAction.TriggerBackendXtreamSync -> {
                    _uiState.update { it.copy(isTriggeringBackendSync = true, backendTriggerSyncMessage = null) }
                    viewModelScope.launch {
                        try {
                            val servers = backendRepository.observeServers().first()
                            if (servers.isEmpty()) {
                                _uiState.update { it.copy(isTriggeringBackendSync = false, backendTriggerSyncMessage = "No backend servers configured") }
                            } else {
                                val jobIds = mutableListOf<String>()
                                servers.filter { it.isActive }.forEach { server ->
                                    val result = backendRepository.syncAll(server.id)
                                    result.getOrNull()?.let { jobIds.add(it) }
                                }
                                val msg = if (jobIds.isNotEmpty()) {
                                    "Sync triggered (${jobIds.size} server(s))"
                                } else {
                                    "Failed to trigger sync"
                                }
                                _uiState.update { it.copy(isTriggeringBackendSync = false, backendTriggerSyncMessage = msg) }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Backend trigger sync failed")
                            _uiState.update {
                                it.copy(isTriggeringBackendSync = false, backendTriggerSyncMessage = "Failed: ${e.message}")
                            }
                        }
                        kotlinx.coroutines.delay(5000)
                        _uiState.update { it.copy(backendTriggerSyncMessage = null) }
                    }
                }
                is SettingsAction.CheckBackendHealth -> {
                    _uiState.update { it.copy(isCheckingBackendHealth = true, backendHealthMessage = null) }
                    viewModelScope.launch {
                        try {
                            val servers = backendRepository.observeServers().first()
                            if (servers.isEmpty()) {
                                _uiState.update { it.copy(isCheckingBackendHealth = false, backendHealthMessage = "No backend servers configured") }
                            } else {
                                val reports = mutableListOf<String>()
                                servers.filter { it.isActive }.forEach { server ->
                                    val result = backendRepository.getHealthInfo(server.id)
                                    if (result.isSuccess) {
                                        val info = result.getOrThrow()
                                        val lastSync = info.lastSyncAt?.let {
                                            val mins = (System.currentTimeMillis() - it) / 60_000
                                            if (mins < 60) "${mins}m ago" else "${mins / 60}h ago"
                                        } ?: "never"
                                        reports.add(
                                            "${server.label}: ${info.status} | v${info.version} | " +
                                            "${info.accounts} accounts | ${info.totalMedia} media | " +
                                            "${info.enrichedMedia} enriched | ${info.brokenStreams} broken | " +
                                            "last sync: $lastSync"
                                        )
                                    } else {
                                        reports.add("${server.label}: unreachable")
                                    }
                                }
                                _uiState.update {
                                    it.copy(isCheckingBackendHealth = false, backendHealthMessage = reports.joinToString("\n"))
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Backend health check failed")
                            _uiState.update {
                                it.copy(isCheckingBackendHealth = false, backendHealthMessage = "Failed: ${e.message}")
                            }
                        }
                        kotlinx.coroutines.delay(10000)
                        _uiState.update { it.copy(backendHealthMessage = null) }
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

        private fun observeBackendServers() {
            backendRepository.observeServers()
                .onEach { servers -> _uiState.update { it.copy(backendServers = servers) } }
                .catch { e -> Timber.e(e, "Failed to observe backend servers") }
                .launchIn(viewModelScope)
        }

        private fun observeXtreamAccounts() {
            xtreamAccountRepository.observeAccounts()
                .onEach { accounts -> _uiState.update { it.copy(xtreamAccounts = accounts) } }
                .catch { e -> Timber.e(e, "Failed to observe Xtream accounts") }
                .launchIn(viewModelScope)
        }

        private fun observeJellyfinServers() {
            jellyfinServerRepository.observeServers()
                .onEach { servers -> _uiState.update { it.copy(jellyfinServers = servers) } }
                .catch { e -> Timber.e(e, "Failed to observe Jellyfin servers") }
                .launchIn(viewModelScope)
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
                    val serverNames = listOf(ALL_SERVERS_SENTINEL) + servers.map { it.name }
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

            // Group 2: Language & exclusion preferences (4 flows)
            val prefs = combine(
                settingsRepository.preferredAudioLanguage,
                settingsRepository.preferredSubtitleLanguage,
                settingsRepository.excludedServerIds,
                settingsRepository.metadataLanguage,
            ) { audio, subtitle, excluded, metaLang ->
                { s: SettingsUiState ->
                    s.copy(
                        preferredAudioLanguage = audio,
                        preferredSubtitleLanguage = subtitle,
                        excludedServerIds = excluded,
                        metadataLanguage = metaLang,
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

            // Collect home row visibility preferences
            viewModelScope.launch {
                settingsRepository.showContinueWatching.collect { show ->
                    _uiState.update { it.copy(showContinueWatching = show) }
                }
            }
            viewModelScope.launch {
                settingsRepository.showMyList.collect { show ->
                    _uiState.update { it.copy(showMyList = show) }
                }
            }
            viewModelScope.launch {
                settingsRepository.showSuggestions.collect { show ->
                    _uiState.update { it.copy(showSuggestions = show) }
                }
            }
            viewModelScope.launch {
                settingsRepository.homeRowOrder.collect { order ->
                    _uiState.update { it.copy(homeRowOrder = order) }
                }
            }

            // Collect showYearOnCards separately (single flow, simpler than creating a group)
            viewModelScope.launch {
                settingsRepository.showYearOnCards.collect { showYear ->
                    _uiState.update { it.copy(showYearOnCards = showYear) }
                }
            }

            // Collect gridColumnsCount separately
            viewModelScope.launch {
                settingsRepository.gridColumnsCount.collect { columnsCount ->
                    _uiState.update { it.copy(gridColumnsCount = columnsCount) }
                }
            }

            // Collect deinterlaceMode separately
            viewModelScope.launch {
                settingsRepository.deinterlaceMode.collect { mode ->
                    _uiState.update { it.copy(deinterlaceMode = mode) }
                }
            }

            // Collect autoPlayNextEnabled separately
            viewModelScope.launch {
                settingsRepository.autoPlayNextEnabled.collect { enabled ->
                    _uiState.update { it.copy(autoPlayNextEnabled = enabled) }
                }
            }

            // Collect skip intro/credits modes separately
            viewModelScope.launch {
                settingsRepository.skipIntroMode.collect { mode ->
                    _uiState.update { it.copy(skipIntroMode = mode) }
                }
            }
            viewModelScope.launch {
                settingsRepository.skipCreditsMode.collect { mode ->
                    _uiState.update { it.copy(skipCreditsMode = mode) }
                }
            }
            viewModelScope.launch {
                settingsRepository.themeSongEnabled.collect { enabled ->
                    _uiState.update { it.copy(themeSongEnabled = enabled) }
                }
            }

            // Collect screensaver settings separately
            viewModelScope.launch {
                settingsRepository.screensaverEnabled.collect { enabled ->
                    _uiState.update { it.copy(screensaverEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                settingsRepository.screensaverIntervalSeconds.collect { seconds ->
                    _uiState.update { it.copy(screensaverIntervalSeconds = seconds) }
                }
            }
            viewModelScope.launch {
                settingsRepository.screensaverShowClock.collect { show ->
                    _uiState.update { it.copy(screensaverShowClock = show) }
                }
            }

            // Collect autoCheckUpdates separately
            viewModelScope.launch {
                settingsRepository.autoCheckUpdates.collect { enabled ->
                    _uiState.update { it.copy(autoCheckUpdates = enabled) }
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

    data object NavigateToPlexHomeSwitch : SettingsNavigationEvent

    data object NavigateToAppProfiles : SettingsNavigationEvent

    data object NavigateToLibrarySelection : SettingsNavigationEvent

    data object NavigateToJellyfinSetup : SettingsNavigationEvent

    data object NavigateToXtreamSetup : SettingsNavigationEvent

    data class NavigateToXtreamCategorySelection(val accountId: String) : SettingsNavigationEvent

    data object NavigateToSubtitleStyle : SettingsNavigationEvent
}
