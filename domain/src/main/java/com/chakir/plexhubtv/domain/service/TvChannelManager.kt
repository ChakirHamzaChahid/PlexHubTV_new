package com.chakir.plexhubtv.domain.service

/**
 * Manages Android TV Channels (Continue Watching).
 * Implementation lives in :data (Android TV Provider APIs).
 */
interface TvChannelManager {
    suspend fun createChannelIfNeeded(): Long?
    suspend fun updateContinueWatching()
    suspend fun deleteChannel()
}
