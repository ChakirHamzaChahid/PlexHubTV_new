package com.chakir.plexhubtv.core.common.util

import java.util.Locale

/**
 * Utilitaires pour la construction des libellés de pistes Audio et Sous-titres.
 * Combine les informations techniques (Codec, Canaux, Langue) en une chaîne lisible par l'utilisateur.
 * Exemple : "Français · AAC · 5.1ch"
 */
object TrackUtils {
    /**
     * Build a label for an audio track.
     * Combines title, language, codec, and channel count.
     */
    fun buildAudioLabel(
        title: String?,
        language: String?,
        codec: String?,
        channels: Int?,
        index: Int,
    ): String {
        val parts = mutableListOf<String>()

        if (!title.isNullOrBlank()) {
            parts.add(title)
        }

        if (!language.isNullOrBlank()) {
            // Convert ISO code to display name if possible, otherwise use uppercase code
            val displayName =
                Locale(
                    language,
                ).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            parts.add(if (displayName.isNotBlank() && displayName != language) displayName else language.uppercase(Locale.US))
        }

        if (!codec.isNullOrBlank()) {
            parts.add(CodecUtils.formatAudioCodec(codec))
        }

        if (channels != null && channels > 0) {
            parts.add("${channels}ch")
        }

        return if (parts.isEmpty()) "Audio Track ${index + 1}" else parts.joinToString(" · ")
    }

    /**
     * Build a label for a subtitle track.
     */
    fun buildSubtitleLabel(
        title: String?,
        language: String?,
        codec: String?,
        index: Int,
    ): String {
        val parts = mutableListOf<String>()

        if (!title.isNullOrBlank()) {
            parts.add(title)
        }

        if (!language.isNullOrBlank()) {
            val displayName =
                Locale(
                    language,
                ).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            parts.add(if (displayName.isNotBlank() && displayName != language) displayName else language.uppercase(Locale.US))
        }

        if (!codec.isNullOrBlank()) {
            parts.add(CodecUtils.formatSubtitleCodec(codec))
        }

        return if (parts.isEmpty()) "Subtitle Track ${index + 1}" else parts.joinToString(" · ")
    }
}
