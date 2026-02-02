package com.chakir.plexhubtv.domain.model

/**
 * Représentation UI d'une piste audio sélectionnable dans le lecteur.
 */
data class AudioTrack(
    val id: String,
    val title: String?,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val index: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isSelected: Boolean = false,
    val streamId: String? = null
) {
    val displayName: String get() = title ?: language ?: "Track $id"
}

/**
 * Représentation UI d'une piste de sous-titres sélectionnable.
 *
 * @property isExternal Si vrai, c'est un fichier externe (SRT) et non incrusté dans le conteneur.
 */
data class SubtitleTrack(
    val id: String,
    val title: String?,
    val language: String?,
    val codec: String?,
    val index: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val isSelected: Boolean = false,
    val streamId: String? = null
) {
    val displayName: String get() = title ?: language ?: if (isExternal) "External" else "Track $id"
    
    companion object {
        /** Objet spécial pour désactiver les sous-titres. */
        val OFF = SubtitleTrack(id = "no", title = "Off", language = null, codec = null, index = -1)
    }
}
