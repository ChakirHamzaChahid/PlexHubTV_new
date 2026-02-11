package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Repository pour la gestion des profils utilisateurs.
 */
interface ProfileRepository {

    /**
     * Récupère tous les profils.
     */
    fun getAllProfiles(): Flow<List<Profile>>

    /**
     * Récupère un profil par son ID.
     */
    suspend fun getProfileById(profileId: String): Profile?

    /**
     * Récupère le profil actuellement actif.
     */
    suspend fun getActiveProfile(): Profile?

    /**
     * Récupère le profil actif en Flow.
     */
    fun getActiveProfileFlow(): Flow<Profile?>

    /**
     * Crée un nouveau profil.
     */
    suspend fun createProfile(profile: Profile): Result<Profile>

    /**
     * Met à jour un profil existant.
     */
    suspend fun updateProfile(profile: Profile): Result<Profile>

    /**
     * Supprime un profil.
     */
    suspend fun deleteProfile(profileId: String): Result<Unit>

    /**
     * Active un profil spécifique.
     * Désactive automatiquement tous les autres profils.
     */
    suspend fun switchProfile(profileId: String): Result<Profile>

    /**
     * Compte le nombre de profils.
     */
    suspend fun getProfileCount(): Int

    /**
     * Crée un profil par défaut si aucun profil n'existe.
     */
    suspend fun ensureDefaultProfile(): Profile
}
