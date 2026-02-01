package com.chakir.plexhubtv.core.database

import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.chakir.plexhubtv.domain.model.MediaPart
import com.chakir.plexhubtv.domain.model.MediaStream

/**
 * Convertisseurs de types pour Room.
 * Gère la sérialisation/désérialisation des objets complexes (Listes, Streams) en JSON via Gson
 * pour pouvoir les stocker dans les colonnes TEXT de SQLite.
 */
class Converters {
    private val gson = GsonBuilder()
        .registerTypeAdapter(MediaStream::class.java, MediaStreamAdapter())
        .create()

    @TypeConverter
    fun fromMediaPartList(value: List<MediaPart>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toMediaPartList(value: String?): List<MediaPart> {
        val listType = object : TypeToken<List<MediaPart>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}
