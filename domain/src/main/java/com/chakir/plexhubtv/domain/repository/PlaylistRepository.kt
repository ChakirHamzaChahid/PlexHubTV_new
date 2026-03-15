package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>

    suspend fun refreshPlaylists()

    suspend fun getPlaylistDetail(playlistId: String, serverId: String): Playlist?

    suspend fun createPlaylist(title: String, ratingKey: String, serverId: String): Result<Playlist>

    suspend fun addToPlaylist(playlistId: String, ratingKey: String, serverId: String): Result<Unit>

    suspend fun removeFromPlaylist(playlistId: String, playlistItemId: String, serverId: String): Result<Unit>

    suspend fun deletePlaylist(playlistId: String, serverId: String): Result<Unit>
}
