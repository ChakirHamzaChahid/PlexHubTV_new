package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPreferenceDao {
    @Query("SELECT * FROM track_preferences WHERE ratingKey = :ratingKey AND serverId = :serverId")
    fun getPreference(ratingKey: String, serverId: String): Flow<TrackPreferenceEntity?>

    @Query("SELECT * FROM track_preferences WHERE ratingKey = :ratingKey AND serverId = :serverId")
    suspend fun getPreferenceSync(ratingKey: String, serverId: String): TrackPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: TrackPreferenceEntity)
    
    @Query("DELETE FROM track_preferences WHERE ratingKey = :ratingKey AND serverId = :serverId")
    suspend fun deletePreference(ratingKey: String, serverId: String)
}
