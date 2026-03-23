package com.chakir.plexhubtv.core.model

/**
 * Domain model for a Jellyfin server.
 * Mirrors the Plex [Server] model but simpler (single URL, no connection racing).
 */
data class JellyfinServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val userId: String,
    val userName: String,
    val version: String = "",
    val isActive: Boolean = true,
    val lastSyncedAt: Long = 0,
) {
    /** The prefixed serverId used throughout the app (media entities, source handlers, etc.). */
    val prefixedServerId: String get() = "${SourcePrefix.JELLYFIN}$id"
}
