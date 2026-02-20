package com.chakir.plexhubtv.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface de gestion des préférences utilisateur (stockées localement via DataStore).
 */
interface SettingsRepository {
    // --- UI Preferences ---
    val showHeroSection: Flow<Boolean>
    val episodePosterMode: Flow<String> // "poster" vs "thumb"
    val appTheme: Flow<String> // "dark", "oled", "light"

    suspend fun setShowHeroSection(show: Boolean)

    suspend fun setEpisodePosterMode(mode: String)

    suspend fun setAppTheme(theme: String)

    // --- Player Preferences ---
    fun getVideoQuality(): Flow<String>

    suspend fun setVideoQuality(quality: String)

    val playerEngine: Flow<String> // "exo" vs "mpv"

    suspend fun setPlayerEngine(engine: String)

    // --- Audio/Subtitle Preferences ---
    val preferredAudioLanguage: Flow<String?> // "fr", "en", etc.

    suspend fun setPreferredAudioLanguage(lang: String?)

    val preferredSubtitleLanguage: Flow<String?> // "fr", "en", or "forced", "none"

    suspend fun setPreferredSubtitleLanguage(lang: String?)

    // --- System ---
    val isCacheEnabled: Flow<Boolean>

    suspend fun setCacheEnabled(enabled: Boolean)

    val defaultServer: Flow<String> // ID du serveur préféré

    suspend fun setDefaultServer(server: String)

    val clientId: Flow<String?> // Identifiant unique local de l'application

    suspend fun clearSession() // Reset complet au logout

    suspend fun getCacheSize(): Long

    suspend fun clearCache()

    suspend fun clearDatabase()

    val excludedServerIds: Flow<Set<String>>

    suspend fun toggleServerExclusion(serverId: String)

    val iptvPlaylistUrl: Flow<String?>

    suspend fun saveIptvPlaylistUrl(url: String)

    // --- External API Keys ---
    fun getTmdbApiKey(): Flow<String?>

    suspend fun saveTmdbApiKey(key: String)

    fun getOmdbApiKey(): Flow<String?>

    suspend fun saveOmdbApiKey(key: String)

    // --- Rating Sync Configuration ---
    val ratingSyncSource: Flow<String> // "tmdb" or "omdb"

    val ratingSyncDelay: Flow<Long> // delay in ms between API requests

    val ratingSyncBatchingEnabled: Flow<Boolean>

    val ratingSyncDailyLimit: Flow<Int> // max requests per day when batching

    val ratingSyncProgressSeries: Flow<Int> // current progress for series

    val ratingSyncProgressMovies: Flow<Int> // current progress for movies

    val ratingSyncLastRunDate: Flow<String?> // last run date (YYYY-MM-DD)

    suspend fun saveRatingSyncSource(source: String)

    suspend fun saveRatingSyncDelay(delayMs: Long)

    suspend fun saveRatingSyncBatchingEnabled(enabled: Boolean)

    suspend fun saveRatingSyncDailyLimit(limit: Int)

    suspend fun saveRatingSyncProgressSeries(progress: Int)

    suspend fun saveRatingSyncProgressMovies(progress: Int)

    suspend fun saveRatingSyncLastRunDate(date: String)

    suspend fun resetRatingSyncProgress()

    // --- TV Channels ---
    val isTvChannelsEnabled: Flow<Boolean>

    suspend fun setTvChannelsEnabled(enabled: Boolean)
}
