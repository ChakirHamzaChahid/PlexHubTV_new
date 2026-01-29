package com.chakir.plexhubtv.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing offline watch progress synchronization.
 * 
 * Handles queuing watch actions (progress updates, mark watched/unwatched)
 * when offline and syncing them to Plex servers when connectivity is restored.
 */
interface OfflineWatchSyncRepository {
    
    /**
     * Watch threshold - mark as watched when progress exceeds this percentage
     */
    val WATCHED_THRESHOLD: Double
        get() = 0.90
    
    /**
     * Maximum sync attempts before giving up on an item
     */
    val MAX_SYNC_ATTEMPTS: Int
        get() = 5
    
    /**
     * Queue a progress update for later sync
     */
    suspend fun queueProgressUpdate(
        serverId: String,
        ratingKey: String,
        viewOffset: Long,
        duration: Long
    )
    
    /**
     * Queue a manual "mark as watched" action
     */
    suspend fun queueMarkWatched(serverId: String, ratingKey: String)
    
    /**
     * Queue a manual "mark as unwatched" action
     */
    suspend fun queueMarkUnwatched(serverId: String, ratingKey: String)
    
    /**
     * Get the local watch status for a media item
     * Returns: true if watched, false if unwatched, null if no local action exists
     */
    suspend fun getLocalWatchStatus(globalKey: String): Boolean?
    
    /**
     * Get local watch statuses for multiple items in a batch
     */
    suspend fun getLocalWatchStatusesBatched(globalKeys: Set<String>): Map<String, Boolean?>
    
    /**
     * Get the local view offset (resume position) for a media item
     */
    suspend fun getLocalViewOffset(globalKey: String): Long?
    
    /**
     * Get count of pending sync items
     */
    suspend fun getPendingSyncCount(): Int
    
    /**
     * Observe pending sync count changes
     */
    fun observePendingSyncCount(): Flow<Int>
    
    /**
     * Sync all pending items to their respective servers
     */
    suspend fun syncPendingItems(): Result<Int>
    
    /**
     * Fetch latest watch states from server and update local cache
     */
    suspend fun syncWatchStatesFromServer(): Result<Int>
    
    /**
     * Perform bidirectional sync: push local changes, then pull server states
     */
    suspend fun performBidirectionalSync(force: Boolean = false): Result<Unit>
    
    /**
     * Clear all pending watch actions
     */
    suspend fun clearAll()
    
    /**
     * Check if an item should be considered watched based on progress
     */
    fun isWatchedByProgress(viewOffset: Long, duration: Long): Boolean =
        if (duration == 0L) false else (viewOffset.toDouble() / duration) >= WATCHED_THRESHOLD
}
