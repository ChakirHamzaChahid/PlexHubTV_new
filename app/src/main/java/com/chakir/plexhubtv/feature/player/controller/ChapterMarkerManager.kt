package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.Chapter
import com.chakir.plexhubtv.core.model.Marker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Gestionnaire des chapitres et des marqueurs (intro, générique, publicités) pour la lecture vidéo.
 * Fournit des flux d'état (StateFlows) pour la synchronisation de l'interface utilisateur avec la
 * position de lecture actuelle.
 */
class ChapterMarkerManager
    @Inject
    constructor() {
        // Liste des chapitres disponibles
        private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
        val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

        // Liste des marqueurs disponibles
        private val _markers = MutableStateFlow<List<Marker>>(emptyList())
        val markers: StateFlow<List<Marker>> = _markers.asStateFlow()

        // Le chapitre en cours de lecture
        private val _currentChapter = MutableStateFlow<Chapter?>(null)
        val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()

        // Les marqueurs visibles à la position actuelle (ex: bouton "Passer l'intro")
        private val _visibleMarkers = MutableStateFlow<List<Marker>>(emptyList())
        val visibleMarkers: StateFlow<List<Marker>> = _visibleMarkers.asStateFlow()

        // Raccourci vers le marqueur d'intro s'il existe
        private val _introMarker = MutableStateFlow<Marker?>(null)
        val introMarker: StateFlow<Marker?> = _introMarker.asStateFlow()

        // Raccourci vers le marqueur de générique de fin s'il existe
        private val _creditsMarker = MutableStateFlow<Marker?>(null)
        val creditsMarker: StateFlow<Marker?> = _creditsMarker.asStateFlow()

        /** Définit la liste des chapitres pour la vidéo en cours. */
        fun setChapters(newChapters: List<Chapter>) {
            _chapters.update { newChapters }
        }

        /**
         * Définit la liste des marqueurs pour la vidéo en cours et met à jour les références rapides
         * (intro/credits).
         */
        fun setMarkers(newMarkers: List<Marker>) {
            _markers.update { newMarkers }
            updateMarkerStates(newMarkers)
        }

        private fun updateMarkerStates(newMarkers: List<Marker>) {
            _introMarker.update { newMarkers.firstOrNull { it.type == "intro" } }
            _creditsMarker.update { newMarkers.firstOrNull { it.type == "credits" } }
        }

        /**
         * Met à jour la position de lecture actuelle et synchronise l'état de l'interface utilisateur.
         * Détermine quel est le chapitre actuel et quels marqueurs sont actifs.
         *
         * @param currentPositionMs La position de lecture actuelle en millisecondes.
         */
        fun updatePlaybackPosition(currentPositionMs: Long) {
            val currentChap =
                _chapters.value.firstOrNull { chapter ->
                    currentPositionMs >= chapter.startTime && currentPositionMs < chapter.endTime
                }
            _currentChapter.update { currentChap }

            val markersList = _markers.value
            val visibleMarkersList =
                markersList.filter { marker ->
                    currentPositionMs >= marker.startTime && currentPositionMs < marker.endTime
                }
            _visibleMarkers.update { visibleMarkersList }
        }

        /** Récupère tous les chapitres triés par heure de début. */
        fun getSortedChapters(): List<Chapter> = _chapters.value.sortedBy { it.startTime }

        /**
         * Récupère le chapitre correspondant à un temps donné.
         * @param timeMs Le temps en millisecondes.
         */
        fun getChapterAt(timeMs: Long): Chapter? {
            return _chapters.value.firstOrNull { chapter ->
                timeMs >= chapter.startTime && timeMs < chapter.endTime
            }
        }

        /**
         * Récupère le marqueur correspondant à un temps donné.
         * @param timeMs Le temps en millisecondes.
         */
        fun getMarkerAt(timeMs: Long): Marker? {
            return _markers.value.firstOrNull { marker ->
                timeMs >= marker.startTime && timeMs < marker.endTime
            }
        }

        /** Vérifie si un marqueur d'intro est présent. */
        fun hasIntro(): Boolean = _introMarker.value != null

        /** Vérifie si un marqueur de générique est présent. */
        fun hasCredits(): Boolean = _creditsMarker.value != null

        /** Trouve le chapitre suivant par rapport à une position donnée (pour le bouton "Suivant"). */
        fun getNextChapter(fromPositionMs: Long): Chapter? {
            return _chapters.value.firstOrNull { it.startTime > fromPositionMs }
        }

        /**
         * Trouve le chapitre précédent par rapport à une position donnée (pour le bouton "Précédent").
         */
        fun getPreviousChapter(fromPositionMs: Long): Chapter? {
            // Idéalement on veut le chapitre dont le start est < current, le dernier de cette liste
            return _chapters.value.lastOrNull { it.startTime < fromPositionMs }
        }

        /** Vérifie si la vidéo a des chapitres. */
        fun hasChapters(): Boolean = _chapters.value.isNotEmpty()

        /** Vérifie si la vidéo a des marqueurs. */
        fun hasMarkers(): Boolean = _markers.value.isNotEmpty()

        /**
         * Efface toutes les données (à appeler lors de l'arrêt de la lecture ou changement de vidéo).
         */
        fun clear() {
            _chapters.update { emptyList() }
            _markers.update { emptyList() }
            _currentChapter.update { null }
            _visibleMarkers.update { emptyList() }
            _introMarker.update { null }
            _creditsMarker.update { null }
        }
    }
