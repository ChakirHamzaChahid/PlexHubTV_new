package com.chakir.plexhubtv.feature.player.url

import android.net.Uri
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaPart

/**
 * Strategy interface for source-specific playback URL construction.
 *
 * Each media source (Plex, Jellyfin, etc.) implements its own URL builder
 * because direct-play and transcode URL formats differ between servers.
 * Implementations are registered via Hilt `@IntoSet` multibinding.
 */
interface PlaybackUrlBuilder {

    /** Returns true if this builder handles the given serverId. */
    fun matches(serverId: String): Boolean

    /**
     * Builds the playback URL for the given media item.
     *
     * @param media The media item to play (carries baseUrl and accessToken).
     * @param part The selected media part (file/stream container).
     * @param rKey The rating key / item ID.
     * @param isDirectPlay True for direct play, false for transcoding.
     * @param bitrate Target bitrate in kbps (only relevant for transcoding).
     * @param clientId The client identifier for server-side session tracking.
     * @param audioStreamId Selected audio stream ID, or null for default.
     * @param subtitleStreamId Selected subtitle stream ID, or null for none.
     * @param audioIndex Selected audio stream index, or null.
     * @param subtitleIndex Selected subtitle stream index, or null.
     * @return The playback URI, or null if URL cannot be constructed.
     */
    fun buildUrl(
        media: MediaItem,
        part: MediaPart,
        rKey: String,
        isDirectPlay: Boolean,
        bitrate: Int,
        clientId: String,
        audioStreamId: String?,
        subtitleStreamId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): Uri?
}
