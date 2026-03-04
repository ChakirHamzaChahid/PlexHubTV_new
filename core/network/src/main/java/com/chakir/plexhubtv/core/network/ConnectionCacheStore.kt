package com.chakir.plexhubtv.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Persists validated server connection URLs across cold starts.
 * Abstraction over the concrete DataStore implementation to avoid
 * core:network depending on core:datastore.
 */
interface ConnectionCacheStore {
    val cachedConnections: Flow<Map<String, String>>
    suspend fun saveCachedConnections(connections: Map<String, String>)
}
