package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.FavoriteActor
import com.chakir.plexhubtv.core.model.Person
import kotlinx.coroutines.flow.Flow

interface PersonFavoriteRepository {
    fun isFavorite(tmdbId: Int): Flow<Boolean>
    suspend fun toggleFavorite(person: Person)
    fun getAllFavorites(): Flow<List<FavoriteActor>>
}
