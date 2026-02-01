package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour accéder aux favoris stockés localement.
 */
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE ratingKey = :ratingKey AND serverId = :serverId)")
    fun isFavorite(ratingKey: String, serverId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE ratingKey = :ratingKey AND serverId = :serverId")
    suspend fun deleteFavorite(ratingKey: String, serverId: String)
}
