package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "person_favorites")
data class PersonFavoriteEntity(
    @PrimaryKey val tmdbId: Int,
    val name: String,
    val profilePath: String? = null,
    val knownFor: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
