package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getFavorites(): Flow<List<MediaItem>>

    fun isFavorite(
        ratingKey: String,
        serverId: String,
    ): Flow<Boolean>

    fun isFavoriteAny(ratingKeys: List<String>): Flow<Boolean>

    suspend fun toggleFavorite(media: MediaItem): Result<Boolean>
}
