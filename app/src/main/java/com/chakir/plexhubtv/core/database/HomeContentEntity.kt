package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité de liaison pour l'écran d'accueil.
 * Ne stocke pas les données du média, mais une référence (ServerID + RatingKey)
 * qui permet de faire une jointure avec la table `media`.
 */
@Entity(tableName = "home_content", primaryKeys = ["type", "hubIdentifier", "itemServerId", "itemRatingKey"])
data class HomeContentEntity(
    val type: String, // "onDeck" or "hub"
    val hubIdentifier: String, // "onDeck" or hub.identifier ("recentlyAdded", etc.)
    val title: String, // Hub title, e.g. "Recently Added Movies"
    
    // Reference to the Media Item
    val itemServerId: String,
    val itemRatingKey: String,
    
    val orderIndex: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
