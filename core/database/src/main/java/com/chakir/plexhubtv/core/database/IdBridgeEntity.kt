package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "id_bridge",
    indices = [Index(value = ["tmdbId"])]
)
data class IdBridgeEntity(
    @PrimaryKey val imdbId: String,
    val tmdbId: String,
)
