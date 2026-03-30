package com.chakir.plexhubtv.domain.service

import com.chakir.plexhubtv.core.model.MediaItem

/**
 * Strategy interface for source-specific playback reporting.
 *
 * Each media source (Plex, Jellyfin, etc.) implements its own reporter to handle
 * progress updates, scrobble events, and stream selection using its native API.
 * Implementations are registered via Hilt `@IntoSet` multibinding and dispatched
 * by [PlaybackRepository] based on [matches].
 */
interface PlaybackReporter {

    /** Returns true if this reporter handles the given serverId. */
    fun matches(serverId: String): Boolean

    /** Reports playback progress to the media server. */
    suspend fun reportProgress(item: MediaItem, positionMs: Long): Result<Unit>

    /** Notifies the media server that playback has stopped. */
    suspend fun reportStopped(item: MediaItem, positionMs: Long): Result<Unit>

    /** Marks the item as watched or unwatched on the media server. */
    suspend fun toggleWatchStatus(item: MediaItem, isWatched: Boolean): Result<Unit>

    /**
     * Updates the selected audio/subtitle streams on the media server.
     * Returns [Result.success] with Unit if the source doesn't support stream selection.
     */
    suspend fun updateStreamSelection(
        serverId: String,
        partId: String,
        audioStreamId: String?,
        subtitleStreamId: String?,
    ): Result<Unit>
}
