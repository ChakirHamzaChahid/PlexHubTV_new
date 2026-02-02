package com.chakir.plexhubtv.feature.player

import com.chakir.plexhubtv.domain.model.AudioTrack
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.SubtitleTrack

/**
 * État de l'UI pour le lecteur vidéo.
 * Contient les informations de lecture (position, durée, buffering), les pistes audio/sous-titres et les métadonnées.
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val currentItem: MediaItem? = null,
    val nextItem: MediaItem? = null,
    val showAutoNextPopup: Boolean = false,
    
    // Tracks
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedAudio: AudioTrack? = null,
    val selectedSubtitle: SubtitleTrack? = null,
    
    val availableQualities: List<VideoQuality> = listOf(
        VideoQuality("Maximum", 200000),
        VideoQuality("20 Mbps (1080p)", 20000),
        VideoQuality("12 Mbps (1080p)", 12000),
        VideoQuality("8 Mbps (1080p)", 8000),
        VideoQuality("4 Mbps (720p)", 4000),
        VideoQuality("3 Mbps (720p)", 3000),
        VideoQuality("2 Mbps (480p)", 2000)
    ),
    val selectedQuality: VideoQuality = VideoQuality("Maximum", 200000),
    val showSettings: Boolean = false,
    val showAudioSelection: Boolean = false,
    val showSubtitleSelection: Boolean = false,
    val showSpeedSelection: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val audioDelay: Long = 0L,
    val subtitleDelay: Long = 0L,
    val showAudioSyncDialog: Boolean = false,
    val showSubtitleSyncDialog: Boolean = false,
    
    val showPerformanceOverlay: Boolean = false,
    val playerStats: PlayerStats? = null,

    val error: String? = null,
    val isMpvMode: Boolean = false
)

data class PlayerStats(
    val bitrate: String = "0 kbps",
    val resolution: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val droppedFrames: Long = 0,
    val fps: Double = 0.0,
    val cacheDuration: Long = 0L
)

data class VideoQuality(
    val name: String,
    val bitrate: Int
)

sealed interface PlayerAction {
    data object Play : PlayerAction
    data object Pause : PlayerAction
    data class SeekTo(val position: Long) : PlayerAction
    data object Next : PlayerAction
    data object Previous : PlayerAction
    data class SelectAudioTrack(val track: AudioTrack) : PlayerAction
    data class SelectSubtitleTrack(val track: SubtitleTrack) : PlayerAction
    data class SelectQuality(val quality: VideoQuality) : PlayerAction
    data class SetPlaybackSpeed(val speed: Float) : PlayerAction
    data class SetAudioDelay(val delayMs: Long) : PlayerAction
    data class SetSubtitleDelay(val delayMs: Long) : PlayerAction
    data object ShowAudioSelector : PlayerAction
    data object ShowSubtitleSelector : PlayerAction
    data object ShowAudioSyncSelector : PlayerAction
    data object ShowSubtitleSyncSelector : PlayerAction
    data object ToggleSpeedSelection : PlayerAction
    data object Close : PlayerAction
    data class SkipMarker(val marker: com.chakir.plexhubtv.domain.model.Marker) : PlayerAction
    data object PlayNext : PlayerAction
    data object CancelAutoNext : PlayerAction
    data object SeekToNextChapter : PlayerAction
    data object SeekToPreviousChapter : PlayerAction
    data object ToggleSettings : PlayerAction
    data object TogglePerformanceOverlay : PlayerAction
    data object DismissDialog : PlayerAction // Close any open dialog without stopping playback
}
