package com.chakir.plexhubtv.data.network

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.network.ConnectionCacheStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreConnectionCacheStore
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
    ) : ConnectionCacheStore {
        override val cachedConnections: Flow<Map<String, String>> =
            settingsDataStore.cachedConnections

        override suspend fun saveCachedConnections(connections: Map<String, String>) {
            settingsDataStore.saveCachedConnections(connections)
        }
    }
