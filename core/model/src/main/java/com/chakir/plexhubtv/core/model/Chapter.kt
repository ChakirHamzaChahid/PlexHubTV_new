package com.chakir.plexhubtv.core.model

/**
 * Représente un chapitre dans un flux vidéo.
 *
 * Utilisé pour la navigation rapide et l'affichage des marqueurs dans la barre de progression.
 *
 * @property title Titre du chapitre (ex: "Introduction", "Scene 1").
 * @property startTime Temps de début en millisecondes.
 * @property endTime Temps de fin en millisecondes.
 * @property thumbUrl URL de l'image d'aperçu du chapitre (optionnel).
 */


data class Chapter(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val thumbUrl: String? = null,
)
