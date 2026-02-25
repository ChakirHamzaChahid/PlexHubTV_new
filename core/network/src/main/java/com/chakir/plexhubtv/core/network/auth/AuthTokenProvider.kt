package com.chakir.plexhubtv.core.network.auth

import kotlinx.coroutines.flow.Flow

/**
 * Provides authentication tokens for Plex API requests.
 * Abstraction over the concrete DataStore implementation to avoid
 * core:network depending on core:datastore.
 */
interface AuthTokenProvider {
    val plexToken: Flow<String?>
    val clientId: Flow<String?>
}
