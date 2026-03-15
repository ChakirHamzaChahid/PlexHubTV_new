package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY title ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId AND serverId = :serverId")
    suspend fun getPlaylist(playlistId: String, serverId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId AND serverId = :serverId ORDER BY orderIndex ASC")
    suspend fun getPlaylistItems(playlistId: String, serverId: String): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND serverId = :serverId")
    suspend fun clearPlaylistItems(playlistId: String, serverId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND serverId = :serverId")
    suspend fun deletePlaylist(playlistId: String, serverId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND serverId = :serverId AND itemRatingKey = :itemRatingKey")
    suspend fun removePlaylistItem(playlistId: String, serverId: String, itemRatingKey: String)

    @Query("DELETE FROM playlists")
    suspend fun clearAll()

    @Query("DELETE FROM playlist_items")
    suspend fun clearAllItems()

    @Transaction
    suspend fun replacePlaylistItems(playlistId: String, serverId: String, items: List<PlaylistItemEntity>) {
        clearPlaylistItems(playlistId, serverId)
        upsertPlaylistItems(items)
    }
}
