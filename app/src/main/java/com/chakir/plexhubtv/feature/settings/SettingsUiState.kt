package com.chakir.plexhubtv.feature.settings

/**
 * État UI des paramètres.
 * Contient les préférences utilisateur (Thème, Qualité Vidéo, Moteur de lecture) et l'état de la synchronisation.
 */
data class SettingsUiState(
    val theme: AppTheme = AppTheme.MonoDark,
    val videoQuality: String = "Original",
    val isCacheEnabled: Boolean = true,
    val cacheSize: String = "0 MB",
    val defaultServer: String = "MyServer",
    val availableServers: List<String> = listOf("MyServer"),
    val availableServersMap: Map<String, String> = emptyMap(),
    val playerEngine: String = "ExoPlayer",
    val appVersion: String = "1.0.0",
    val isSyncing: Boolean = false,
    val isSyncingWatchlist: Boolean = false,
    val syncMessage: String? = null,
    val syncError: String? = null,
    val excludedServerIds: Set<String> = emptySet(),
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val tmdbApiKey: String = "",
    val omdbApiKey: String = "",
    val isSyncingRatings: Boolean = false,
    val ratingSyncMessage: String? = null,
    val iptvPlaylistUrl: String = "",
    val showYearOnCards: Boolean = false,
    val gridColumnsCount: Int = 6,
    // Xtream sync
    val isSyncingXtream: Boolean = false,
    val xtreamSyncMessage: String? = null,
    val xtreamAccounts: List<com.chakir.plexhubtv.core.model.XtreamAccount> = emptyList(),
    // Backend
    val backendServers: List<com.chakir.plexhubtv.core.model.BackendServer> = emptyList(),
    val isTestingBackend: Boolean = false,
    val backendConfigMessage: String? = null,
    val isSyncingBackend: Boolean = false,
    val backendSyncMessage: String? = null,
    // Rating Sync Configuration
    val ratingSyncSource: String = "tmdb", // "tmdb" or "omdb"
    val ratingSyncDelay: Long = 250L, // delay in ms
    val ratingSyncBatchingEnabled: Boolean = false,
    val ratingSyncDailyLimit: Int = 900,
    val ratingSyncProgressSeries: Int = 0,
    val ratingSyncProgressMovies: Int = 0,
)

enum class AppTheme {
    Plex,
    MonoDark,
    MonoLight,
    Morocco,
}

sealed interface SettingsAction {
    data class ChangeTheme(val theme: AppTheme) : SettingsAction

    data class ChangeVideoQuality(val quality: String) : SettingsAction

    data object ClearCache : SettingsAction

    data class SelectDefaultServer(val serverName: String) : SettingsAction

    data class ChangePlayerEngine(val engine: String) : SettingsAction

    data object Back : SettingsAction

    data object Logout : SettingsAction

    data object CheckServerStatus : SettingsAction

    data object ForceSync : SettingsAction

    data object SyncWatchlist : SettingsAction

    data class ChangePreferredAudioLanguage(val language: String?) : SettingsAction

    data class ChangePreferredSubtitleLanguage(val language: String?) : SettingsAction

    data class ToggleServerExclusion(val serverId: String) : SettingsAction

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

    data object ManageXtreamAccounts : SettingsAction

    data class ManageXtreamCategories(val accountId: String) : SettingsAction

    data object SyncXtream : SettingsAction

    data class AddBackendServer(val label: String, val url: String) : SettingsAction

    data class RemoveBackendServer(val id: String) : SettingsAction

    data class TestBackendConnection(val url: String) : SettingsAction

    data object SyncBackend : SettingsAction
}
