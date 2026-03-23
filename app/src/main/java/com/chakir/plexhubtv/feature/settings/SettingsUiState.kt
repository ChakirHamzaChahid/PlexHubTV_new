package com.chakir.plexhubtv.feature.settings

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * État UI des paramètres.
 * Contient les préférences utilisateur (Thème, Qualité Vidéo, Moteur de lecture) et l'état de la synchronisation.
 */
@Immutable
data class SettingsUiState(
    val theme: AppTheme = AppTheme.MonoDark,
    val videoQuality: String = "Original",
    val isCacheEnabled: Boolean = true,
    val cacheSize: String = "0 MB",
    val defaultServer: String = "MyServer",
    val availableServers: ImmutableList<String> = persistentListOf("MyServer"),
    val availableServersMap: ImmutableMap<String, String> = persistentMapOf(),
    val playerEngine: String = "ExoPlayer",
    val deinterlaceMode: String = "auto",
    val autoPlayNextEnabled: Boolean = true,
    val skipIntroMode: String = "ask", // "auto", "ask", "off"
    val skipCreditsMode: String = "ask", // "auto", "ask", "off"
    val themeSongEnabled: Boolean = false,
    val appVersion: String = "1.0.0",
    val isSyncing: Boolean = false,
    val isSyncingWatchlist: Boolean = false,
    val syncMessage: String? = null,
    val syncError: String? = null,
    val excludedServerIds: Set<String> = emptySet(),
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val metadataLanguage: String = "fr",
    val tmdbApiKey: String = "",
    val omdbApiKey: String = "",
    val isSyncingRatings: Boolean = false,
    val ratingSyncMessage: String? = null,
    val iptvPlaylistUrl: String = "",
    // Home Row Visibility & Order
    val showContinueWatching: Boolean = true,
    val showMyList: Boolean = true,
    val showSuggestions: Boolean = true,
    val homeRowOrder: ImmutableList<String> = persistentListOf("continue_watching", "my_list", "suggestions"),
    val showYearOnCards: Boolean = false,
    val gridColumnsCount: Int = 6,
    // Xtream sync
    val isSyncingXtream: Boolean = false,
    val xtreamSyncMessage: String? = null,
    val xtreamAccounts: ImmutableList<com.chakir.plexhubtv.core.model.XtreamAccount> = persistentListOf(),
    // Jellyfin
    val jellyfinServers: ImmutableList<com.chakir.plexhubtv.core.model.JellyfinServer> = persistentListOf(),
    val isSyncingJellyfin: Boolean = false,
    val jellyfinSyncMessage: String? = null,
    // Backend
    val backendServers: ImmutableList<com.chakir.plexhubtv.core.model.BackendServer> = persistentListOf(),
    val isTestingBackend: Boolean = false,
    val backendConfigMessage: String? = null,
    val isSyncingBackend: Boolean = false,
    val backendSyncMessage: String? = null,
    val isTriggeringBackendSync: Boolean = false,
    val backendTriggerSyncMessage: String? = null,
    val isCheckingBackendHealth: Boolean = false,
    val backendHealthMessage: String? = null,
    // Rating Sync Configuration
    val ratingSyncSource: String = "tmdb", // "tmdb" or "omdb"
    val ratingSyncDelay: Long = 250L, // delay in ms
    val ratingSyncBatchingEnabled: Boolean = false,
    val ratingSyncDailyLimit: Int = 900,
    val ratingSyncProgressSeries: Int = 0,
    val ratingSyncProgressMovies: Int = 0,
    // Screensaver
    val screensaverEnabled: Boolean = true,
    val screensaverIntervalSeconds: Int = 15,
    val screensaverShowClock: Boolean = true,
    // Parental PIN
    val hasParentalPin: Boolean = false,
    // Auto-Update
    val autoCheckUpdates: Boolean = true,
    val isCheckingForUpdate: Boolean = false,
    val updateCheckMessage: String? = null,
)

enum class AppTheme {
    Plex,
    MonoDark,
    MonoLight,
    Morocco,
    OLEDBlack,
}

sealed interface SettingsAction {
    data class ChangeTheme(val theme: AppTheme) : SettingsAction

    data class ChangeVideoQuality(val quality: String) : SettingsAction

    data object ClearCache : SettingsAction

    data class SelectDefaultServer(val serverName: String) : SettingsAction

    data class ChangePlayerEngine(val engine: String) : SettingsAction

    data class ChangeDeinterlaceMode(val mode: String) : SettingsAction

    data class ToggleAutoPlayNext(val enabled: Boolean) : SettingsAction

    data class ChangeSkipIntroMode(val mode: String) : SettingsAction

    data class ChangeSkipCreditsMode(val mode: String) : SettingsAction

    data object Back : SettingsAction

    data object Logout : SettingsAction

    data object CheckServerStatus : SettingsAction

    data object ForceSync : SettingsAction

    data object SyncWatchlist : SettingsAction

    data class ChangePreferredAudioLanguage(val language: String?) : SettingsAction

    data class ChangePreferredSubtitleLanguage(val language: String?) : SettingsAction

    data class ChangeMetadataLanguage(val language: String) : SettingsAction

    data class ToggleServerExclusion(val serverId: String) : SettingsAction

    data class ToggleShowContinueWatching(val enabled: Boolean) : SettingsAction

    data class ToggleShowMyList(val enabled: Boolean) : SettingsAction

    data class ToggleShowSuggestions(val enabled: Boolean) : SettingsAction

    data class MoveHomeRowUp(val rowId: String) : SettingsAction

    data class MoveHomeRowDown(val rowId: String) : SettingsAction

    data class ToggleShowYearOnCards(val enabled: Boolean) : SettingsAction

    data class ChangeGridColumnsCount(val count: Int) : SettingsAction

    data class SaveTmdbApiKey(val key: String) : SettingsAction

    data class SaveOmdbApiKey(val key: String) : SettingsAction

    data object SyncRatings : SettingsAction

    data class SaveIptvPlaylistUrl(val url: String) : SettingsAction

    // Rating Sync Configuration Actions
    data class ChangeRatingSyncSource(val source: String) : SettingsAction

    data class ChangeRatingSyncDelay(val delayMs: Long) : SettingsAction

    data class ToggleRatingSyncBatching(val enabled: Boolean) : SettingsAction

    data class ChangeRatingSyncDailyLimit(val limit: Int) : SettingsAction

    data object ResetRatingSyncProgress : SettingsAction

    data object SwitchPlexUser : SettingsAction

    data object ManageAppProfiles : SettingsAction

    data object ManageLibrarySelection : SettingsAction

    data object ManageJellyfinServers : SettingsAction

    data object SyncJellyfin : SettingsAction

    data object ManageXtreamAccounts : SettingsAction

    data class ManageXtreamCategories(val accountId: String) : SettingsAction

    data object SyncXtream : SettingsAction

    data class AddBackendServer(val label: String, val url: String) : SettingsAction

    data class RemoveBackendServer(val id: String) : SettingsAction

    data class TestBackendConnection(val url: String) : SettingsAction

    data object SyncBackend : SettingsAction

    data object TriggerBackendXtreamSync : SettingsAction

    data object CheckBackendHealth : SettingsAction

    data class SetParentalPin(val pin: String) : SettingsAction

    data object ClearParentalPin : SettingsAction

    data object NavigateToSubtitleStyle : SettingsAction

    data class ToggleThemeSong(val enabled: Boolean) : SettingsAction

    data class ToggleScreensaver(val enabled: Boolean) : SettingsAction

    data class ChangeScreensaverInterval(val seconds: Int) : SettingsAction

    data class ToggleScreensaverClock(val enabled: Boolean) : SettingsAction

    data class ToggleAutoCheckUpdates(val enabled: Boolean) : SettingsAction

    data object CheckForUpdates : SettingsAction
}
