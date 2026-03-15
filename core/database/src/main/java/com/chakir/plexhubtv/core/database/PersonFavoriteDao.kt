package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonFavoriteDao {
    @Query("SELECT * FROM person_favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<PersonFavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM person_favorites WHERE tmdbId = :tmdbId)")
    fun isFavorite(tmdbId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonFavoriteEntity)

    @Query("DELETE FROM person_favorites WHERE tmdbId = :tmdbId")
    suspend fun delete(tmdbId: Int)
}
