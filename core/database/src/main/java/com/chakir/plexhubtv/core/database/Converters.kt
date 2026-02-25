package com.chakir.plexhubtv.core.database

import androidx.room.TypeConverter
import com.chakir.plexhubtv.core.model.MediaPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Convertisseurs de types pour Room.
 * Gère la sérialisation/désérialisation des objets complexes (Listes, Streams) en JSON
 * via kotlinx-serialization pour pouvoir les stocker dans les colonnes TEXT de SQLite.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromMediaPartList(value: List<MediaPart>?): String {
        if (value == null) return "[]"
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toMediaPartList(value: String?): List<MediaPart> {
        if (value.isNullOrEmpty() || value == "null") return emptyList()
        return json.decodeFromString<List<MediaPart>>(value)
    }
}
