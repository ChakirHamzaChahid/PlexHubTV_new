package com.chakir.plexhubtv.data.network

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.network.auth.AuthTokenProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreAuthTokenProvider
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
    ) : AuthTokenProvider {
        override val plexToken: Flow<String?> = settingsDataStore.plexToken
        override val clientId: Flow<String?> = settingsDataStore.clientId
    }
