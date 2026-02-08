package com.chakir.plexhubtv.core.model

/**
 * Représente un marqueur temporel spécial dans une vidéo.
 *
 * Utilisé principalement pour les fonctionnalités "Skip Intro" et "Skip Credits".
 *
 * @property title Titre ou label (ex: "Intro", "Credits").
 * @property type Type technique (ex: "intro", "credits", "commercial").
 * @property startTime Début du segment en millisecondes.
 * @property endTime Fin du segment en millisecondes.
 */
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Marker(
    val title: String = "Marker",
    val type: String, // 'intro' or 'credits'
    val startTime: Long,
    val endTime: Long,
) : Parcelable
