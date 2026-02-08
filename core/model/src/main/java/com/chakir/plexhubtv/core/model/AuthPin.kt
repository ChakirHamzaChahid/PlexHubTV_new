package com.chakir.plexhubtv.core.model

/**
 * Représente un code PIN pour l'authentification OAuth (Plex.tv/link).
 *
 * Ce modèle est utilisé lors du processus de connexion TV :
 * 1. L'application demande un PIN.
 * 2. L'API renvoie un [code] et un [id].
 * 3. L'utilisateur va sur [url] et entre le [code].
 * 4. L'application poll l'API avec [id] pour obtenir le token.
 *
 * @property id Identifiant unique de la demande de connexion.
 * @property code Le code court à 4 caractères affiché à l'écran.
 * @property url L'URL où l'utilisateur doit se rendre (ex: plex.tv/link).
 */
data class AuthPin(
    val id: String,
    val code: String,
    val url: String = "https://plex.tv/link",
)
