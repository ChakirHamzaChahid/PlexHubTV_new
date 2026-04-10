package com.chakir.plexhubtv.core.model

/**
 * High-level media source types used for UI filtering.
 * Maps to [SourcePrefix]-based serverId conventions.
 */
enum class SourceType(val label: String) {
    PLEX("Plex"),
    JELLYFIN("Jellyfin"),
    XTREAM("Xtream"),
    BACKEND("Backend");

    companion object {
        fun fromServerId(serverId: String): SourceType = when {
            serverId.startsWith(SourcePrefix.JELLYFIN) -> JELLYFIN
            serverId.startsWith(SourcePrefix.XTREAM) -> XTREAM
            serverId.startsWith(SourcePrefix.BACKEND) -> BACKEND
            else -> PLEX
        }
    }
}
