package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implémentation du repository des paramètres.
 * Agit comme une façade sur le DataStore [SettingsDataStore] et le [CacheManager].
 */
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        private val cacheManager: com.chakir.plexhubtv.core.util.CacheManager,
        private val database: com.chakir.plexhubtv.core.database.PlexDatabase,
    ) : SettingsRepository {
        override val showHeroSection: Flow<Boolean> = settingsDataStore.showHeroSection
        override val episodePosterMode: Flow<String> = settingsDataStore.episodePosterMode
        override val appTheme: Flow<String> = settingsDataStore.appTheme
        override val isCacheEnabled: Flow<Boolean> = settingsDataStore.isCacheEnabled
        override val defaultServer: Flow<String> = settingsDataStore.defaultServer
        override val playerEngine: Flow<String> = settingsDataStore.playerEngine
        override val clientId: Flow<String?> = settingsDataStore.clientId

        override val preferredAudioLanguage: Flow<String?> = settingsDataStore.preferredAudioLanguage
        override val preferredSubtitleLanguage: Flow<String?> = settingsDataStore.preferredSubtitleLanguage

        override suspend fun setShowHeroSection(show: Boolean) {
            settingsDataStore.saveShowHeroSection(show)
        }

        override suspend fun setEpisodePosterMode(mode: String) {
            settingsDataStore.saveEpisodePosterMode(mode)
        }

        override suspend fun setAppTheme(theme: String) {
            settingsDataStore.saveAppTheme(theme)
        }

        override fun getVideoQuality(): Flow<String> = settingsDataStore.videoQuality

        override suspend fun setVideoQuality(quality: String) {
            settingsDataStore.saveVideoQuality(quality)
        }

        override suspend fun setCacheEnabled(enabled: Boolean) {
            settingsDataStore.saveCacheEnabled(enabled)
        }

        override suspend fun setDefaultServer(server: String) {
            settingsDataStore.saveDefaultServer(server)
        }

        override suspend fun setPlayerEngine(engine: String) {
            settingsDataStore.savePlayerEngine(engine)
        }

        override suspend fun setPreferredAudioLanguage(lang: String?) {
            settingsDataStore.savePreferredAudioLanguage(lang)
        }

        override suspend fun setPreferredSubtitleLanguage(lang: String?) {
            settingsDataStore.savePreferredSubtitleLanguage(lang)
        }

        override suspend fun clearSession() {
            settingsDataStore.clearAll()
        }

        override suspend fun getCacheSize(): Long {
            return cacheManager.getCacheSize()
        }

        override suspend fun clearCache() {
            cacheManager.clearCache()
        }

        override suspend fun clearDatabase() {
            // Run in transaction or IO dispatcher if needed, but Room handles threading for queries usually.
            // clearAllTables is a suspension function or blocking? usually blocking in RoomDatabase but we can wrap it?
            // Actually RoomDatabase.clearAllTables() is not suspend. We should wrap it in withContext ideally or just call it.
            // It prevents access from main thread.
            try {
                database.clearAllTables()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override val excludedServerIds: Flow<Set<String>> = settingsDataStore.excludedServerIds

        override suspend fun toggleServerExclusion(serverId: String) {
            settingsDataStore.toggleServerExclusion(serverId)
        }

        override val iptvPlaylistUrl: Flow<String?> = settingsDataStore.iptvPlaylistUrl

        override suspend fun saveIptvPlaylistUrl(url: String) {
            settingsDataStore.saveIptvPlaylistUrl(url)
        }

        override fun getTmdbApiKey(): Flow<String?> = settingsDataStore.tmdbApiKey

        override suspend fun saveTmdbApiKey(key: String) {
            settingsDataStore.saveTmdbApiKey(key)
        }

        override fun getOmdbApiKey(): Flow<String?> = settingsDataStore.omdbApiKey

        override suspend fun saveOmdbApiKey(key: String) {
            settingsDataStore.saveOmdbApiKey(key)
        }

        // Rating Sync Configuration
        override val ratingSyncSource: Flow<String> = settingsDataStore.ratingSyncSource
        override val ratingSyncDelay: Flow<Long> = settingsDataStore.ratingSyncDelay
        override val ratingSyncBatchingEnabled: Flow<Boolean> = settingsDataStore.ratingSyncBatchingEnabled
        override val ratingSyncDailyLimit: Flow<Int> = settingsDataStore.ratingSyncDailyLimit
        override val ratingSyncProgressSeries: Flow<Int> = settingsDataStore.ratingSyncProgressSeries
        override val ratingSyncProgressMovies: Flow<Int> = settingsDataStore.ratingSyncProgressMovies
        override val ratingSyncLastRunDate: Flow<String?> = settingsDataStore.ratingSyncLastRunDate

        override suspend fun saveRatingSyncSource(source: String) {
            settingsDataStore.saveRatingSyncSource(source)
        }

        override suspend fun saveRatingSyncDelay(delayMs: Long) {
            settingsDataStore.saveRatingSyncDelay(delayMs)
        }

        override suspend fun saveRatingSyncBatchingEnabled(enabled: Boolean) {
            settingsDataStore.saveRatingSyncBatchingEnabled(enabled)
        }

        override suspend fun saveRatingSyncDailyLimit(limit: Int) {
            settingsDataStore.saveRatingSyncDailyLimit(limit)
        }

        override suspend fun saveRatingSyncProgressSeries(progress: Int) {
            settingsDataStore.saveRatingSyncProgressSeries(progress)
        }

        override suspend fun saveRatingSyncProgressMovies(progress: Int) {
            settingsDataStore.saveRatingSyncProgressMovies(progress)
        }

        override suspend fun saveRatingSyncLastRunDate(date: String) {
            settingsDataStore.saveRatingSyncLastRunDate(date)
        }

        override suspend fun resetRatingSyncProgress() {
            settingsDataStore.resetRatingSyncProgress()
        }

        // TV Channels
        override val isTvChannelsEnabled: Flow<Boolean> = settingsDataStore.isTvChannelsEnabled

        override suspend fun setTvChannelsEnabled(enabled: Boolean) {
            settingsDataStore.setTvChannelsEnabled(enabled)
        }

        // Library Filter Preferences
        override val librarySort: Flow<String> = settingsDataStore.librarySort
        override val librarySortDescending: Flow<Boolean> = settingsDataStore.librarySortDescending
        override val libraryGenre: Flow<String?> = settingsDataStore.libraryGenre
        override val libraryServerFilter: Flow<String?> = settingsDataStore.libraryServerFilter

        override suspend fun saveLibrarySort(sort: String, isDescending: Boolean) {
            settingsDataStore.saveLibrarySort(sort, isDescending)
        }

        override suspend fun saveLibraryGenre(genre: String?) {
            settingsDataStore.saveLibraryGenre(genre)
        }

        override suspend fun saveLibraryServerFilter(serverName: String?) {
            settingsDataStore.saveLibraryServerFilter(serverName)
        }
    }
