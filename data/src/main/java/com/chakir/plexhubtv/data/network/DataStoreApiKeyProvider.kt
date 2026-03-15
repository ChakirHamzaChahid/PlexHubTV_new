package com.chakir.plexhubtv.data.network

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.network.ApiKeyProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreApiKeyProvider
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
    ) : ApiKeyProvider {
        override val tmdbApiKey: Flow<String?> = settingsDataStore.tmdbApiKey
        override val omdbApiKey: Flow<String?> = settingsDataStore.omdbApiKey
        override val openSubtitlesApiKey: Flow<String?> = settingsDataStore.openSubtitlesApiKey
    }
