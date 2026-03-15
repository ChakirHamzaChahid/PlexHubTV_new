package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Provides user-configured API keys for external rating services.
 * Abstraction over the concrete DataStore implementation to avoid
 * core:network depending on core:datastore.
 */
interface ApiKeyProvider {
    val tmdbApiKey: Flow<String?>
    val omdbApiKey: Flow<String?>
    val openSubtitlesApiKey: Flow<String?>
}
