package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_cache",
    indices = [Index(value = ["query", "serverId"], unique = true)]
)
data class SearchCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val serverId: String,
    val resultsJson: String,  // JSON sérialisé
    val resultCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMillis: Long = 3_600_000): Boolean =  // 1 heure par défaut
        System.currentTimeMillis() - lastUpdated > ttlMillis
}
