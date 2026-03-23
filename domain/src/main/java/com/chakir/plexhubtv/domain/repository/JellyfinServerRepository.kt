package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.JellyfinServer
import kotlinx.coroutines.flow.Flow

/**
 * CRUD + auth operations for Jellyfin servers.
 * Token storage is delegated to SecurePreferencesManager (encrypted).
 */
interface JellyfinServerRepository {

    /** Reactive stream of all configured Jellyfin servers. */
    fun observeServers(): Flow<List<JellyfinServer>>

    /** One-shot fetch of all configured Jellyfin servers. */
    suspend fun getServers(): List<JellyfinServer>

    /** Get a single server by its (unprefixed) Jellyfin server ID. */
    suspend fun getServer(id: String): JellyfinServer?

    /**
     * Test connection, authenticate, and persist a new Jellyfin server.
     * @return The authenticated [JellyfinServer] on success.
     */
    suspend fun addServer(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<JellyfinServer>

    /**
     * Remove a Jellyfin server and all associated data:
     * - Delete JellyfinServerEntity from Room
     * - Delete access token from SecurePreferencesManager
     * - Delete all MediaEntity rows with serverId = "jellyfin_{id}"
     * - Trigger AggregationService.rebuildAll() to update media_unified
     */
    suspend fun removeServer(id: String)

    /** Update the last synced timestamp for a server. */
    suspend fun updateLastSyncedAt(id: String, timestamp: Long)

    /**
     * Sync all libraries for a Jellyfin server:
     * 1. Fetch library views (movies + tvshows)
     * 2. Paginate items per library (200 per page)
     * 3. Preserve hidden/scraped/overridden state from previous sync
     * 4. Batch upsert into Room
     * 5. Clean up stale items no longer on server
     * @return Total number of items synced.
     */
    suspend fun syncLibrary(serverId: String): Result<Int>
}
