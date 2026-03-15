package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for providing API keys from Settings DataStore.
 * Used by RatingSyncWorker to get user-configured API keys.
 */
@Singleton
class ApiKeyManager
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProvider,
    ) {
        /**
     * Get TMDb API key.
     * Priority: BuildConfig (local.properties) > DataStore (Settings UI)
     * Returns null if not configured in either source.
     */
    suspend fun getTmdbApiKey(): String? {
        // First check BuildConfig (from local.properties)
        val buildConfigKey = BuildConfig.TMDB_API_KEY
        if (!buildConfigKey.isNullOrBlank()) {
            return buildConfigKey
        }
        
        // Fallback to DataStore (Settings UI)
        return apiKeyProvider.tmdbApiKey.first()
    }

    /**
     * Get OMDb API key.
     * Priority: BuildConfig (local.properties) > DataStore (Settings UI)
     * Returns null if not configured in either source.
     */
    suspend fun getOmdbApiKey(): String? {
        // First check BuildConfig (from local.properties)
        val buildConfigKey = BuildConfig.OMDB_API_KEY
        if (!buildConfigKey.isNullOrBlank()) {
            return buildConfigKey
        }

        // Fallback to DataStore (Settings UI)
        return apiKeyProvider.omdbApiKey.first()
    }

    /**
     * Get OpenSubtitles API key.
     * Priority: BuildConfig (local.properties) > DataStore (Settings UI)
     * Returns null if not configured in either source.
     */
    suspend fun getOpenSubtitlesApiKey(): String? {
        val buildConfigKey = BuildConfig.OPENSUBTITLES_API_KEY
        if (!buildConfigKey.isNullOrBlank()) {
            return buildConfigKey
        }
        return apiKeyProvider.openSubtitlesApiKey.first()
    }

    /**
     * Check if both API keys are configured (from either source).
     */
    suspend fun areKeysConfigured(): Boolean {
        val tmdbKey = getTmdbApiKey()
        val omdbKey = getOmdbApiKey()
        return !tmdbKey.isNullOrBlank() && !omdbKey.isNullOrBlank()
    }
}
