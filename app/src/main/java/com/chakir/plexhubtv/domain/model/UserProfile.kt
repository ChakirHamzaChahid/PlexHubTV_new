package com.chakir.plexhubtv.domain.model

/**
 * Plex user profile preferences
 * Fetched from https://clients.plex.tv/api/v2/user
 * 
 * Contains audio/subtitle language preferences and accessibility settings
 * used by TrackSelectionUseCase for automatic track selection
 */
data class UserProfile(
    val id: String,
    val title: String,
    val thumb: String? = null,
    val protected: Boolean = false,
    val admin: Boolean = false,
    
    // Audio preferences
    val autoSelectAudio: Boolean = true,
    val defaultAudioAccessibility: Int = 0,
    val defaultAudioLanguage: String? = null,
    val defaultAudioLanguages: List<String>? = null,
    
    // Subtitle preferences
    val defaultSubtitleLanguage: String? = null,
    val defaultSubtitleLanguages: List<String>? = null,
    val autoSelectSubtitle: Int = 0, // 0=manual, 1=foreign audio, 2=always
    val defaultSubtitleAccessibility: Int = 0, // SDH preference
    val defaultSubtitleForced: Int = 1, // Forced subtitle preference
    
    // Display preferences
    val watchedIndicator: Int = 1,
    val mediaReviewsVisibility: Int = 0,
    val mediaReviewsLanguages: List<String>? = null
) {
    /**
     * Returns true if subtitles should be automatically selected
     */
    val shouldAutoSelectSubtitle: Boolean
        get() = autoSelectSubtitle > 0
    
    /**
     * Returns true if forced subtitles should be preferred
     */
    val preferForcedSubtitles: Boolean
        get() = defaultSubtitleForced == 1
}
