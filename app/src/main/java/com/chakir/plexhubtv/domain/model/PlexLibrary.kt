package com.chakir.plexhubtv.domain.model

/**
 * Modèle riche d'une bibliothèque Plex incluant le contexte du serveur.
 *
 * Contrairement à [LibrarySection] qui est une simple structure de réponse API,
 * [PlexLibrary] enrichit la donnée avec l'ID du serveur et des flags UI.
 *
 * @property key ID local de la section (ex: "3").
 * @property title Nom (ex: "Films").
 * @property type Type (movie, show).
 * @property serverId "Machine Identifier" du serveur hébergeant cette bibliothèque.
 * @property serverName Nom du serveur (ex: "NAS Maison").
 * @property agent Agent de métadonnées utilisé (Legacy vs Plex Movie).
 * @property scanner Scanner de fichiers utilisé.
 * @property language Langue par défaut de la bibliothèque.
 * @property isVirtual Si vrai, c'est une bibliothèque virtuelle (agrégée côté client).
 * @property globalKey Clé unique globale combinant serveur et ID ("serverId:key").
 */
data class PlexLibrary(
    val key: String,
    val title: String,
    val type: String,
    val serverId: String,
    val serverName: String? = null,
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val uuid: String? = null,
    val updatedAt: Long? = null,
    val createdAt: Long? = null,
    val hidden: Boolean = false,
    val isVirtual: Boolean = false // For client-side aggregations
) {
    /** Clé unique utilisable dans les listes diffables (DiffUtil). */
    val globalKey: String get() = "$serverId:$key"
}
