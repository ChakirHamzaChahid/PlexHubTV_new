package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_watch_progress")
data class OfflineWatchProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val serverId: String,
    val ratingKey: String,
    val globalKey: String, // serverId:ratingKey
    val actionType: String, // "progress", "watched", "unwatched"
    val viewOffset: Long? = null,
    val duration: Long? = null,
    val shouldMarkWatched: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncAttempts: Int = 0,
    val lastError: String? = null
)
