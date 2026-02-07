package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Represents a Plex Collection (e.g., "Star Wars Collection").
 */
@Entity(
    tableName = "collections",
    primaryKeys = ["id", "serverId"]
)
data class CollectionEntity(
    val id: String, // ratingKey or GUID from Plex
    val serverId: String,
    val title: String,
    val summary: String? = null,
    val thumbUrl: String? = null,
    val lastSync: Long
)

/**
 * Cross-reference table to link Media items to Collections.
 * Many-to-Many relationship (A media can belong to multiple collections, a collection has many media).
 */
@Entity(
    tableName = "media_collection_cross_ref",
    primaryKeys = ["mediaRatingKey", "collectionId", "serverId"],
    indices = [
        Index(value = ["mediaRatingKey", "serverId"]), 
        Index(value = ["collectionId", "serverId"])
    ]
)
data class MediaCollectionCrossRef(
    val mediaRatingKey: String,
    val collectionId: String,
    val serverId: String
)
