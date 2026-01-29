package com.chakir.plexhubtv.domain.model

data class AudioTrack(
    val id: String,
    val title: String?,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isSelected: Boolean = false
) {
    val displayName: String get() = title ?: language ?: "Track $id"
}

data class SubtitleTrack(
    val id: String,
    val title: String?,
    val language: String?,
    val codec: String?,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val isSelected: Boolean = false
) {
    val displayName: String get() = title ?: language ?: if (isExternal) "External" else "Track $id"
    
    companion object {
        val OFF = SubtitleTrack(id = "no", title = "Off", language = null, codec = null)
    }
}
