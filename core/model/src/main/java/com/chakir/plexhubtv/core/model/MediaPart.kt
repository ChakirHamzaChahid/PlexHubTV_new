package com.chakir.plexhubtv.core.model

/**
 * Représente un fichier physique ou une partie d'un média.
 *
 * Un film peut être séparé en plusieurs fichiers (parties) ou avoir plusieurs versions.
 *
 * @property id ID unique de la partie (Part ID).
 * @property key Clé API pour l'accès direct.
 * @property duration Durée spécifique de cette partie.
 * @property file Chemin du fichier sur le disque du serveur.
 * @property size Taille du fichier en octets.
 * @property container Format conteneur (ex: "mkv", "mp4").
 * @property streams Liste des flux audio/vidéo/sous-titres contenus dans ce fichier.
 */


data class MediaPart(
    val id: String,
    val key: String,
    val duration: Long?,
    val file: String?,
    val size: Long?,
    val container: String?,
    val streams: List<MediaStream> = emptyList(),
)
