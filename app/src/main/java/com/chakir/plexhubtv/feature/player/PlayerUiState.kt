package com.chakir.plexhubtv.feature.player

import androidx.media3.ui.AspectRatioFrameLayout
import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SubtitleTrack

/**
 * État de l'UI pour le lecteur vidéo.
 * Contient les informations de lecture (position, durée, buffering), les pistes audio/sous-titres et les métadonnées.
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = false,
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
    val availableQualities: List<VideoQuality> =
        listOf(
            VideoQuality("Maximum", 200000),
            VideoQuality("20 Mbps (1080p)", 20000),
            VideoQuality("12 Mbps (1080p)", 12000),
            VideoQuality("8 Mbps (1080p)", 8000),
            VideoQuality("4 Mbps (720p)", 4000),
            VideoQuality("3 Mbps (720p)", 3000),
            VideoQuality("2 Mbps (480p)", 2000),
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
    val isMpvMode: Boolean = false,
    // Network error handling
    val errorType: PlayerErrorType = PlayerErrorType.None,
    val networkRetryCount: Int = 0,
    // PLY-19: Resume playback indicator
    val resumeMessage: String? = null,
    // Subtitle download
    val showSubtitleDownload: Boolean = false,
    // Audio equalizer
    val showEqualizer: Boolean = false,
    // More menu
    val showMoreMenu: Boolean = false,
    // Chapter overlay
    val showChapterOverlay: Boolean = false,
    // Queue overlay
    val showQueueOverlay: Boolean = false,
    val playQueue: List<MediaItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    // Aspect ratio mode
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
)

data class PlayerStats(
    val bitrate: String = "0 kbps",
    val resolution: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val droppedFrames: Long = 0,
    val fps: Double = 0.0,
    val cacheDuration: Long = 0L,
    // Enhanced metrics (from Wholphin comparison)
    val decoderType: String = "Unknown",
    val peakBitrateKbps: Long = 0,
    val avgBitrateKbps: Long = 0,
    val bufferBytes: Long = 0,
    val playerBackend: String = "ExoPlayer",
    val audioChannels: String = "Unknown",
    val audioBitrate: String = "N/A",
)

data class VideoQuality(
    val name: String,
    val bitrate: Int,
)

/**
 * Type d'erreur du player pour gérer les retry et fallback
 */
enum class PlayerErrorType {
    None,           // Pas d'erreur
    Network,        // Erreur réseau (timeout, host unreachable, etc.)
    Codec,          // Erreur de codec/décodage
    Generic         // Autre erreur
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
enum class AspectRatioMode(val label: String, val exoResizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): AspectRatioMode = entries[(ordinal + 1) % entries.size]
}

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

    data class SkipMarker(val marker: com.chakir.plexhubtv.core.model.Marker) : PlayerAction

    data object PlayNext : PlayerAction

    data object CancelAutoNext : PlayerAction

    data object SeekToNextChapter : PlayerAction

    data object SeekToPreviousChapter : PlayerAction

    data object ToggleSettings : PlayerAction

    data object TogglePerformanceOverlay : PlayerAction

    data object DismissDialog : PlayerAction // Close any open dialog without stopping playback

    data object DismissCurrentOverlay : PlayerAction // Close only the topmost overlay (layered back)

    data object RetryPlayback : PlayerAction // Retry playback after network error

    data object SwitchToMpv : PlayerAction // Manually switch to MPV player

    data object ClearResumeMessage : PlayerAction // PLY-19: Dismiss resume indicator

    data object ShowSubtitleDownload : PlayerAction // Open subtitle download dialog

    data class ApplyDownloadedSubtitle(val filePath: String) : PlayerAction // Apply downloaded subtitle

    data object ShowEqualizer : PlayerAction // Open audio equalizer dialog

    data class SelectEqualizerPreset(val presetIndex: Int) : PlayerAction

    data class SetEqualizerBand(val bandIndex: Int, val level: Int) : PlayerAction

    data class SetEqualizerEnabled(val enabled: Boolean) : PlayerAction

    data object ToggleMoreMenu : PlayerAction

    data object ShowChapterOverlay : PlayerAction

    data object ShowQueueOverlay : PlayerAction

    data class SeekToChapter(val chapter: com.chakir.plexhubtv.core.model.Chapter) : PlayerAction

    data class PlayQueueItem(val index: Int) : PlayerAction

    data object CycleAspectRatio : PlayerAction
}
