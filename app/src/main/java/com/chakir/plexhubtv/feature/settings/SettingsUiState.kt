package com.chakir.plexhubtv.feature.settings

data class SettingsUiState(
    val theme: AppTheme = AppTheme.Plex,
    val videoQuality: String = "Original",
    val isCacheEnabled: Boolean = true,
    val cacheSize: String = "0 MB",
    val defaultServer: String = "MyServer",
    val availableServers: List<String> = listOf("MyServer"),
    val playerEngine: String = "ExoPlayer",
    val appVersion: String = "1.0.0"
)

enum class AppTheme {
    Plex, MonoDark, MonoLight
}

sealed interface SettingsAction {
    data class ChangeTheme(val theme: AppTheme) : SettingsAction
    data class ChangeVideoQuality(val quality: String) : SettingsAction
    data class ToggleCache(val enabled: Boolean) : SettingsAction
    data object ClearCache : SettingsAction
    data class SelectDefaultServer(val serverName: String) : SettingsAction
    data class ChangePlayerEngine(val engine: String) : SettingsAction
    data object Back : SettingsAction
    data object Logout : SettingsAction
    data object CheckServerStatus : SettingsAction
    data object SwitchProfile : SettingsAction
    data object ForceSync : SettingsAction
}
