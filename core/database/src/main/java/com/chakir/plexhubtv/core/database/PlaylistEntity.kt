package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlists",
    primaryKeys = ["id", "serverId"],
)
data class PlaylistEntity(
    val id: String,
    val serverId: String,
    val title: String,
    val summary: String? = null,
    val thumbUrl: String? = null,
    val playlistType: String = "video",
    val itemCount: Int = 0,
    val durationMs: Long = 0,
    val lastSync: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "serverId", "itemRatingKey"],
    indices = [Index(value = ["playlistId", "serverId"])],
)
data class PlaylistItemEntity(
    val playlistId: String,
    val serverId: String,
    val itemRatingKey: String,
    val orderIndex: Int,
    val playlistItemId: String,
)
