package com.chakir.plexhubtv.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val showHeroSection: Flow<Boolean>
    val episodePosterMode: Flow<String>
    val appTheme: Flow<String>
    
    suspend fun setShowHeroSection(show: Boolean)
    suspend fun setEpisodePosterMode(mode: String)
    suspend fun setAppTheme(theme: String)
    
    fun getVideoQuality(): Flow<String>
    suspend fun setVideoQuality(quality: String)
    
    val isCacheEnabled: Flow<Boolean>
    suspend fun setCacheEnabled(enabled: Boolean)
    
    val defaultServer: Flow<String>
    suspend fun setDefaultServer(server: String)

    val playerEngine: Flow<String>
    suspend fun setPlayerEngine(engine: String)

    val clientId: Flow<String?>
    
    suspend fun clearSession()
    
    suspend fun getCacheSize(): Long
    suspend fun clearCache()
}
