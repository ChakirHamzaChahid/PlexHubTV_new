package com.chakir.plexhubtv.core.model

/**
 * Représente une bibliothèque (ou "Section") sur un serveur Plex.
 *
 * Une bibliothèque correspond à un dossier racine géré par Plex (ex: "Films", "Séries TV", "Documentaires").
 *
 * @property key Identifiant unique ou ID de la section (ex: "1", "4").
 * @property title Nom de la bibliothèque défini par l'utilisateur (ex: "Films 4K").
 * @property type Type de contenu (movie, show, artist, photo).
 */
data class LibrarySection(
    val key: String,
    val title: String,
    val type: String? = null,
)
