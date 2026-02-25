package com.chakir.plexhubtv.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les profils utilisateurs.
 */
@Dao
interface ProfileDao {

    /**
     * Récupère tous les profils.
     */
    @Query("SELECT * FROM profiles ORDER BY lastUsed DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    /**
     * Récupère un profil par son ID.
     */
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: String): ProfileEntity?

    /**
     * Récupère le profil actif.
     */
    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ProfileEntity?

    /**
     * Récupère le profil actif en Flow.
     */
    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfileFlow(): Flow<ProfileEntity?>

    /**
     * Insère ou met à jour un profil.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    /**
     * Met à jour un profil.
     */
    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    /**
     * Supprime un profil.
     */
    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    /**
     * Supprime un profil par son ID.
     */
    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)

    /**
     * Désactive tous les profils (pour la sélection d'un nouveau profil actif).
     */
    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    /**
     * Active un profil spécifique.
     */
    @Query("UPDATE profiles SET isActive = 1, lastUsed = :timestamp WHERE id = :profileId")
    suspend fun activateProfile(profileId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Compte le nombre de profils.
     */
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    /**
     * Met à jour le dernier temps d'utilisation d'un profil.
     */
    @Query("UPDATE profiles SET lastUsed = :timestamp WHERE id = :profileId")
    suspend fun updateLastUsed(profileId: String, timestamp: Long = System.currentTimeMillis())
}
