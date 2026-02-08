package com.chakir.plexhubtv.core.model

/**
 * Préférences utilisateur Plex (Profil).
 * Récupéré depuis https://clients.plex.tv/api/v2/user
 *
 * Contient les préférences de langue audio/sous-titres et les réglages d'accessibilité.
 * Utilisé par `TrackSelectionUseCase` pour la sélection automatique des pistes.
 *
 * @property autoSelectAudio Active la sélection automatique des pistes audio.
 * @property autoSelectSubtitle Logique de sélection des sous-titres : 0=Manuel, 1=Avec audio étranger, 2=Toujours.
 * @property defaultSubtitleForced Préférence pour les sous-titres forcés (1 = oui).
 */
data class UserProfile(
    val id: String,
    val title: String,
    val thumb: String? = null,
    val protected: Boolean = false,
    val admin: Boolean = false,
    // Préférences Audio
    val autoSelectAudio: Boolean = true,
    val defaultAudioAccessibility: Int = 0,
    val defaultAudioLanguage: String? = null,
    val defaultAudioLanguages: List<String>? = null,
    // Préférences Sous-titres
    val defaultSubtitleLanguage: String? = null,
    val defaultSubtitleLanguages: List<String>? = null,
    val autoSelectSubtitle: Int = 0, // 0=Manuel, 1=Audio étranger, 2=Toujours
    val defaultSubtitleAccessibility: Int = 0, // Préférence SDH (Sourds/Malentendants)
    val defaultSubtitleForced: Int = 1, // Préférence sous-titres forcés
    // Préférences Affichage
    val watchedIndicator: Int = 1,
    val mediaReviewsVisibility: Int = 0,
    val mediaReviewsLanguages: List<String>? = null,
) {
    /**
     * Retourne vrai si les sous-titres doivent être sélectionnés automatiquement.
     */
    val shouldAutoSelectSubtitle: Boolean
        get() = autoSelectSubtitle > 0

    /**
     * Retourne vrai si les sous-titres forcés doivent être préférés.
     */
    val preferForcedSubtitles: Boolean
        get() = defaultSubtitleForced == 1
}
