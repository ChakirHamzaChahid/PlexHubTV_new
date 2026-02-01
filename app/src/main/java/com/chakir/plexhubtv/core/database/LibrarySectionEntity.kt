package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant une section (bibliothèque) d'un serveur Plex.
 * Ex: "Films", "Séries TV".
 */
@Entity(tableName = "library_sections")
data class LibrarySectionEntity(
    @PrimaryKey val id: String, // serverId:libraryKey
    val serverId: String,
    val libraryKey: String,
    val title: String,
    val type: String // movie, show, etc.
)
