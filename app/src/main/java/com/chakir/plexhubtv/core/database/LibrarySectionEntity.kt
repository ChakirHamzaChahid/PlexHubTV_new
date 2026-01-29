package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_sections")
data class LibrarySectionEntity(
    @PrimaryKey val id: String, // serverId:libraryKey
    val serverId: String,
    val libraryKey: String,
    val title: String,
    val type: String // movie, show, etc.
)
