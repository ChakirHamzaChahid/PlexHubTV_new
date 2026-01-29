package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val cacheManager: com.chakir.plexhubtv.core.util.CacheManager
) : SettingsRepository {

    override val showHeroSection: Flow<Boolean> = settingsDataStore.showHeroSection
    override val episodePosterMode: Flow<String> = settingsDataStore.episodePosterMode
    override val appTheme: Flow<String> = settingsDataStore.appTheme
    override val isCacheEnabled: Flow<Boolean> = settingsDataStore.isCacheEnabled
    override val defaultServer: Flow<String> = settingsDataStore.defaultServer
    override val playerEngine: Flow<String> = settingsDataStore.playerEngine
    override val clientId: Flow<String?> = settingsDataStore.clientId

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

    override suspend fun clearSession() {
        settingsDataStore.clearAll()
    }

    override suspend fun getCacheSize(): Long {
         return cacheManager.getCacheSize()
    }

    override suspend fun clearCache() {
         cacheManager.clearCache()
    }
}
