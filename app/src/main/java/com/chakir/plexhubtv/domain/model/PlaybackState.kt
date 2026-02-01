package com.chakir.plexhubtv.domain.model

/**
 * État actuel du lecteur vidéo (UI State).
 *
 * @property isPlaying Indique si la lecture est en cours (vrai) ou en pause (faux).
 * @property isBuffering Indique si le lecteur est en train de charger des données.
 * @property currentPosition Position actuelle de la tête de lecture en ms.
 * @property totalDuration Durée totale du média en ms.
 * @property currentMedia Le média en cours de lecture (pour info métadonnées).
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val currentMedia: MediaItem? = null
)
