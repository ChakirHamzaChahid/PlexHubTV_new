package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.core.model.UserProfile
import javax.inject.Inject

enum class TrackSelectionPriority {
    NAVIGATION, // User's manual selection from previous episode
    PLEX_SELECTED, // Plex's selected track
    PER_MEDIA, // Per-media language preference
    PROFILE, // User profile preferences
    DEFAULT_TRACK, // Default or first track
    OFF, // Subtitles off (subtitle only)
}

data class TrackSelectionResult<T>(
    val track: T,
    val priority: TrackSelectionPriority,
)

/**
 * Cas d'utilisation pour la sélection intelligente des pistes (Audio / Sous-titres).
 *
 * Implémente l'algorithme "Smart Select" de Plezy avec 6 niveaux de priorité :
 * 1. **Navigation** : Reprend le choix manuel de l'épisode précédent.
 * 2. **Plex Selected** : Respecte la piste marquée comme "Selected" par le serveur.
 * 3. **Per-Media** : Préférence spécifique forcée pour ce média (si disponible).
 * 4. **Profile** : Préférences globales de l'utilisateur (Langue préférée).
 * 5. **Default** : Piste marquée "Default" ou la première piste.
 * 6. **Off** : Pas de sous-titres (si aucune condition n'est remplie).
 */
class TrackSelectionUseCase
    @Inject
    constructor() {
        /** Sélectionne la meilleure piste audio selon les priorités. */
        fun selectAudioTrack(
            availableTracks: List<AudioTrack>,
            preferredTrack: AudioTrack? = null,
            plexSelectedIndex: Int? = null,
            mediaLanguagePreference: String? = null,
            userProfile: UserProfile? = null,
        ): TrackSelectionResult<AudioTrack>? {
            if (availableTracks.isEmpty()) return null

            val realTracks = availableTracks.filter { it.id != "auto" && it.id != "no" }
            if (realTracks.isEmpty()) return null

            // Priority 1: Navigation preference
            preferredTrack?.let { preferred ->
                findBestAudioMatch(realTracks, preferred)?.let {
                    return TrackSelectionResult(it, TrackSelectionPriority.NAVIGATION)
                }
            }

            // Priority 2: Plex selected
            plexSelectedIndex?.let { index ->
                if (index >= 0 && index < realTracks.size) {
                    return TrackSelectionResult(realTracks[index], TrackSelectionPriority.PLEX_SELECTED)
                }
            }

            // Priority 3: Per-media language
            mediaLanguagePreference?.let { lang ->
                realTracks.firstOrNull { languageMatches(it.language, lang) }?.let {
                    return TrackSelectionResult(it, TrackSelectionPriority.PER_MEDIA)
                }
            }

            // Priority 4: User profile
            userProfile?.let { profile ->
                if (profile.autoSelectAudio) {
                    val preferredLanguages = buildPreferredLanguages(profile, isAudio = true)
                    for (prefLang in preferredLanguages) {
                        realTracks.firstOrNull { languageMatches(it.language, prefLang) }?.let {
                            return TrackSelectionResult(it, TrackSelectionPriority.PROFILE)
                        }
                    }
                }
            }

            // Priority 5: Default or first
            val defaultOrFirst = realTracks.firstOrNull { it.isDefault }
                ?: realTracks.firstOrNull()
                ?: return null
            return TrackSelectionResult(defaultOrFirst, TrackSelectionPriority.DEFAULT_TRACK)
        }

        /**
         * Select the best subtitle track
         */
        fun selectSubtitleTrack(
            availableTracks: List<SubtitleTrack>,
            preferredTrack: SubtitleTrack? = null,
            plexSelectedIndex: Int? = null,
            mediaLanguagePreference: String? = null,
            userProfile: UserProfile? = null,
            selectedAudioTrack: AudioTrack? = null,
        ): TrackSelectionResult<SubtitleTrack> {
            val realTracks = availableTracks.filter { it.id != "auto" && it.id != "no" }

            // Priority 1: Navigation preference
            preferredTrack?.let { preferred ->
                if (preferred.id == "no") {
                    return TrackSelectionResult(SubtitleTrack.OFF, TrackSelectionPriority.NAVIGATION)
                }
                if (realTracks.isNotEmpty()) {
                    findBestSubtitleMatch(realTracks, preferred)?.let {
                        return TrackSelectionResult(it, TrackSelectionPriority.NAVIGATION)
                    }
                }
            }

            // Priority 2: Plex selected
            plexSelectedIndex?.let { index ->
                if (realTracks.isNotEmpty() && index >= 0 && index < realTracks.size) {
                    return TrackSelectionResult(realTracks[index], TrackSelectionPriority.PLEX_SELECTED)
                }
            }

            // Priority 3: Per-media language
            mediaLanguagePreference?.let { lang ->
                if (lang == "none" || lang.isEmpty()) {
                    return TrackSelectionResult(SubtitleTrack.OFF, TrackSelectionPriority.PER_MEDIA)
                }
                if (realTracks.isNotEmpty()) {
                    realTracks.firstOrNull { languageMatches(it.language, lang) }?.let {
                        return TrackSelectionResult(it, TrackSelectionPriority.PER_MEDIA)
                    }
                }
            }

            // Priority 4: User profile
            userProfile?.let { profile ->
                if (realTracks.isNotEmpty()) {
                    // Mode 0: Manually selected - OFF
                    if (profile.autoSelectSubtitle == 0) {
                        return TrackSelectionResult(SubtitleTrack.OFF, TrackSelectionPriority.PROFILE)
                    }

                    // Mode 1: Show with foreign audio only
                    if (profile.autoSelectSubtitle == 1) {
                        val defaultSubLang = profile.defaultSubtitleLanguage
                        if (selectedAudioTrack != null && defaultSubLang != null) {
                            val audioLang = selectedAudioTrack.language?.lowercase()
                            val prefLang = defaultSubLang.lowercase()

                            // If audio matches preferred language, no subtitles needed
                            if (audioLang != null && languageMatches(audioLang, prefLang)) {
                                return TrackSelectionResult(SubtitleTrack.OFF, TrackSelectionPriority.PROFILE)
                            }
                        }
                    }

                    // Mode 1 or 2: Find matching subtitle
                    val preferredLanguages = buildPreferredLanguages(profile, isAudio = false)
                    var candidateTracks = realTracks

                    // Apply SDH and Forced filtering
                    candidateTracks = filterSubtitlesBySDH(candidateTracks, profile.defaultSubtitleAccessibility)
                    candidateTracks = filterSubtitlesByForced(candidateTracks, profile.defaultSubtitleForced)

                    // Fallback to original if filters removed everything
                    if (candidateTracks.isEmpty()) candidateTracks = realTracks

                    for (prefLang in preferredLanguages) {
                        candidateTracks.firstOrNull { languageMatches(it.language, prefLang) }?.let {
                            return TrackSelectionResult(it, TrackSelectionPriority.PROFILE)
                        }
                    }
                }
            }

            // Priority 5: Default track
            if (realTracks.isNotEmpty()) {
                realTracks.firstOrNull { it.isDefault }?.let {
                    return TrackSelectionResult(it, TrackSelectionPriority.DEFAULT_TRACK)
                }
            }

            // Priority 6: OFF
            return TrackSelectionResult(SubtitleTrack.OFF, TrackSelectionPriority.OFF)
        }

        private fun findBestAudioMatch(
            tracks: List<AudioTrack>,
            preferred: AudioTrack,
        ): AudioTrack? {
            // Exact match (id + title + language)
            tracks.firstOrNull {
                it.id == preferred.id && it.title == preferred.title && it.language == preferred.language
            }?.let { return it }

            // Partial match (title + language)
            tracks.firstOrNull {
                it.title == preferred.title && it.language == preferred.language
            }?.let { return it }

            // Language only
            tracks.firstOrNull {
                it.language == preferred.language
            }?.let { return it }

            return null
        }

        private fun findBestSubtitleMatch(
            tracks: List<SubtitleTrack>,
            preferred: SubtitleTrack,
        ): SubtitleTrack? {
            // Exact match
            tracks.firstOrNull {
                it.id == preferred.id && it.title == preferred.title && it.language == preferred.language
            }?.let { return it }

            // Partial match
            tracks.firstOrNull {
                it.title == preferred.title && it.language == preferred.language
            }?.let { return it }

            // Language only
            tracks.firstOrNull {
                it.language == preferred.language
            }?.let { return it }

            return null
        }

        private fun buildPreferredLanguages(
            profile: UserProfile,
            isAudio: Boolean,
        ): List<String> {
            val result = mutableListOf<String>()

            val primary = if (isAudio) profile.defaultAudioLanguage else profile.defaultSubtitleLanguage
            val list = if (isAudio) profile.defaultAudioLanguages else profile.defaultSubtitleLanguages

            primary?.let { if (it.isNotBlank()) result.add(it) }
            list?.forEach { if (it.isNotBlank()) result.add(it) }

            return result.distinct()
        }

        private fun languageMatches(
            trackLanguage: String?,
            preferredLanguage: String?,
        ): Boolean {
            if (trackLanguage == null || preferredLanguage == null) return false

            val track = trackLanguage.lowercase()
            val preferred = preferredLanguage.lowercase()

            // Direct match
            if (track == preferred) return true

            // Base code match (handle region codes like "en-US")
            val trackBase = track.split("-").first()
            val preferredBase = preferred.split("-").first()

            return trackBase == preferredBase
        }

        private fun filterSubtitlesBySDH(
            tracks: List<SubtitleTrack>,
            preference: Int,
        ): List<SubtitleTrack> {
            return when (preference) {
                0, 1 -> {
                    val preferSDH = preference == 1
                    val filtered = tracks.filter { isSDH(it) == preferSDH }
                    filtered.ifEmpty { tracks }
                }
                2 -> tracks.filter { isSDH(it) }
                3 -> tracks.filter { !isSDH(it) }
                else -> tracks
            }
        }

        private fun filterSubtitlesByForced(
            tracks: List<SubtitleTrack>,
            preference: Int,
        ): List<SubtitleTrack> {
            return when (preference) {
                0, 1 -> {
                    val preferForced = preference == 1
                    val filtered = tracks.filter { isForced(it) == preferForced }
                    filtered.ifEmpty { tracks }
                }
                2 -> tracks.filter { isForced(it) }
                3 -> tracks.filter { !isForced(it) }
                else -> tracks
            }
        }

        private fun isSDH(track: SubtitleTrack): Boolean {
            val title = track.title?.lowercase() ?: ""
            return title.contains("sdh") ||
                title.contains("cc") ||
                title.contains("hearing impaired") ||
                title.contains("deaf")
        }

        private fun isForced(track: SubtitleTrack): Boolean {
            val title = track.title?.lowercase() ?: ""
            return title.contains("forced")
        }
    }
