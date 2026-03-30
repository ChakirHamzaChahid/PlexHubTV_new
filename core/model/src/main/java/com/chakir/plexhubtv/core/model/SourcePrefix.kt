package com.chakir.plexhubtv.core.model

/**
 * Canonical server-ID prefixes for each media source type.
 *
 * Plex servers use raw `clientIdentifier` (no prefix) for backward compatibility.
 * All other sources prefix their IDs so [MediaSourceHandler.matches] can dispatch correctly.
 */
object SourcePrefix {
    const val XTREAM = "xtream_"
    const val BACKEND = "backend_"
    const val JELLYFIN = "jellyfin_"

    /** Returns true when the serverId belongs to a non-Plex source. */
    fun isNonPlex(serverId: String): Boolean =
        serverId.startsWith(XTREAM) || serverId.startsWith(BACKEND) || serverId.startsWith(JELLYFIN)
}
