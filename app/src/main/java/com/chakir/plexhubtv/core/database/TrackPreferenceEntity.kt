package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_preferences", primaryKeys = ["ratingKey", "serverId"])
data class TrackPreferenceEntity(
    val ratingKey: String,
    val serverId: String,
    val audioStreamId: String?,
    val subtitleStreamId: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
