package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.AuthPin
import com.chakir.plexhubtv.core.model.Server
import kotlinx.coroutines.flow.Flow

/**
 * Interface de gestion de l'authentification (OAuth Device Flow).
 *
 * Responsabilités :
 * - Gérer le flux "Link" (plex.tv/link).
 * - Récupérer et valider le token d'accès.
 * - Lister les serveurs disponibles pour ce compte.
 */
interface AuthRepository {
    /** Vérifie si un token valide existe déjà localement. */
    suspend fun checkAuthentication(): Boolean

    /**
     * Initie le flow de connexion.
     * @param strong Si vrai, demande un code PIN sécurisé (recommandé).
     * @return Un objet [AuthPin] contenant le code à afficher à l'écran.
     */
    suspend fun getPin(strong: Boolean = true): Result<AuthPin>

    /**
     * Vérifie si l'utilisateur a saisi le code sur le web.
     * @param pinId L'ID de la demande de PIN reçu via [getPin].
     * @return Vrai si l'authentification est réussie (token reçu et stocké).
     */
    suspend fun checkPin(pinId: String): Result<Boolean>

    /** Connecte directement avec un token connu (ex: switch user). */
    suspend fun loginWithToken(token: String): Result<Boolean>

    /**
     * Récupère la liste des serveurs connectés au compte.
     * @param forceRefresh Si vrai, force un appel réseau vers plex.tv/api/resources.
     */
    suspend fun getServers(forceRefresh: Boolean = false): Result<List<Server>>

    /** Récupère les utilisateurs Plex Home (alias vers AccountRepository). */
    suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.core.model.PlexHomeUser>>

    /** Bascule d'utilisateur (alias vers AccountRepository). */
    suspend fun switchUser(
        user: com.chakir.plexhubtv.core.model.PlexHomeUser,
        pin: String? = null,
    ): Result<Boolean>

    /** Flux observant l'état global d'authentification (connecté/déconnecté). */
    fun observeAuthState(): Flow<Boolean>

    /** Clears only the authentication token from secure storage. */
    suspend fun clearToken()

    /**
     * Clears token + user data + optionally database.
     * @param clearDatabase If true, wipes all cached media data.
     */
    suspend fun clearAllAuthData(clearDatabase: Boolean = true)
}
