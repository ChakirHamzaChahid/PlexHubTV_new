package com.chakir.plexhubtv.core.database

import androidx.room.Entity

/**
 * Entité représentant un favori (Watchlist locale).
 * Stocké localement pour un accès rapide sans réseau.
 */
@Entity(tableName = "favorites", primaryKeys = ["ratingKey", "serverId"])
data class FavoriteEntity(
    val ratingKey: String,
    val serverId: String,
    val title: String,
    val type: String, // movie, show, etc.
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val year: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
