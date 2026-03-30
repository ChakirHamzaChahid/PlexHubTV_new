package com.chakir.plexhubtv.core.database

import androidx.room.Entity

/**
 * Entité représentant un item de la Watchlist cloud Plex.
 * Stocke TOUS les items (matchés localement ou non) pour affichage complet.
 */
@Entity(tableName = "watchlist", primaryKeys = ["cloudRatingKey"])
data class WatchlistEntity(
    val cloudRatingKey: String,
    val guid: String?,
    val title: String,
    val type: String,
    val year: Int? = null,
    val thumbUrl: String? = null,
    val artUrl: String? = null,
    val summary: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val localRatingKey: String? = null,
    val localServerId: String? = null,
    val orderIndex: Int = 0,
)
