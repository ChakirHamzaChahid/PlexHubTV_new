package com.chakir.plexhubtv.core.database

import androidx.room.Entity

/**
 * Entité technique pour la librairie Paging 3 (RemoteMediator).
 * Stocke les clés de pagination (page suivante/précédente) pour chaque requête API,
 * permettant de savoir où on s'est arrêté lors du chargement incrémental.
 */
@Entity(
    tableName = "remote_keys",
    primaryKeys = ["libraryKey", "filter", "sortOrder", "offset"]
)
data class RemoteKey(
    val libraryKey: String,
    val filter: String,
    val sortOrder: String,
    val offset: Int,
    val prevKey: Int?,
    val nextKey: Int?
)
