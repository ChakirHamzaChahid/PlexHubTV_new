package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.PlexHomeUser
import kotlinx.coroutines.flow.Flow

/**
 * Interface de gestion des comptes utilisateurs (Plex Home).
 *
 * Responsabilités :
 * - Lister les utilisateurs gérés (Managed Users).
 * - Basculer d'utilisateur (User Switching) avec ou sans PIN.
 * - Gérer l'état de l'utilisateur courant.
 */
interface AccountRepository {
    /** Récupère la liste de tous les utilisateurs du Plex Home courant. */
    suspend fun getHomeUsers(): Result<List<PlexHomeUser>>

    /**
     * Bascule vers un nouvel utilisateur.
     * @param user L'utilisateur cible.
     * @param pin Le code PIN (si l'utilisateur est protégé).
     * @return Resultat du switch (succès/échec).
     */
    suspend fun switchUser(
        user: PlexHomeUser,
        pin: String? = null,
    ): Result<Boolean>

    /** Observe les changements de l'utilisateur actif en temps réel. */
    fun observeCurrentUser(): Flow<PlexHomeUser?>

    /** Récupère l'utilisateur actif de manière synchrone (snapshot). */
    suspend fun getCurrentUser(): PlexHomeUser?

    /** Déconnecte l'utilisateur courant et retourne au profil principal ou écran de login. */
    suspend fun logout()

    /** Rafraîchit les données du profil courant depuis l'API. */
    suspend fun refreshProfile()
}
