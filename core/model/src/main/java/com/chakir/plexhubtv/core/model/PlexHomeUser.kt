package com.chakir.plexhubtv.core.model

/**
 * Utilisateur (Managed User) au sein d'une "Plex Home".
 *
 * @property id ID numérique de l'utilisateur.
 * @property uuid Identifiant global unique (UUID).
 * @property title Titre ou nom d'utilisateur.
 * @property username Nom de connexion (si compte complet Plex.tv).
 * @property email Email associé (optionnel).
 * @property friendlyName Nom d'affichage convivial.
 * @property thumb URL de l'avatar.
 * @property hasPassword Indique si l'utilisateur est protégé par mot de passe/PIN.
 * @property restricted Si vrai, l'utilisateur a des restrictions de contenu.
 * @property protected Si vrai, nécessite un PIN pour entrer.
 * @property admin Si vrai, c'est l'administrateur du serveur.
 * @property guest Si vrai, c'est le compte invité.
 */
data class PlexHomeUser(
    val id: Int,
    val uuid: String,
    val title: String,
    val username: String?,
    val email: String? = null,
    val friendlyName: String?,
    val thumb: String?,
    val hasPassword: Boolean,
    val restricted: Boolean = false,
    val protected: Boolean,
    val admin: Boolean,
    val guest: Boolean,
) {
    /** Nom à afficher dans l'UI (privilégie friendlyName). */
    val displayName: String get() = friendlyName ?: title
}
