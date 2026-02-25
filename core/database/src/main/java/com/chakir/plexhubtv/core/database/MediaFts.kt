package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table linked to [MediaEntity] for full-text search.
 * Room auto-generates content-sync triggers to keep this in sync with the media table.
 */
@Fts4(contentEntity = MediaEntity::class)
@Entity(tableName = "media_fts")
data class MediaFts(
    val title: String,
    val summary: String?,
    val genres: String?,
)
