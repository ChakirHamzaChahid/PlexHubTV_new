package com.chakir.plexhubtv.feature.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.core.model.*
import com.chakir.plexhubtv.domain.repository.*
import com.chakir.plexhubtv.feature.player.controller.*
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.media3.common.Player as ExoPlayerConstants

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel
    @Inject
    constructor(
        application: Application,
        private val savedStateHandle: SavedStateHandle,
        // New Controllers
        private val mediaLoader: PlayerMediaLoader,
        private val actionHandler: PlayerActionHandler,
        private val positionTracker: PlayerPositionTracker,
        private val playerInitializer: PlayerInitializer,
        // Existing Controllers
        val chapterMarkerManager: ChapterMarkerManager,
        private val playerScrobbler: PlayerScrobbler,
        val playerStatsTracker: PlayerStatsTracker,
        private val playerTrackController: PlayerTrackController,
    ) : AndroidViewModel(application) {
        private val ratingKey: String? = savedStateHandle["ratingKey"]
        private val serverId: String? = savedStateHandle["serverId"]
        private val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

        private val _uiState = MutableStateFlow(PlayerUiState())
        val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

        var player: ExoPlayer? = null
            private set
        var mpvPlayer: MpvPlayer? = null
            private set

        private var isMpvMode = false
        private var isDirectPlay = false

        init {
            initializePlayer(application)

            playerScrobbler.start(
                scope = viewModelScope,
                currentItemProvider = { _uiState.value.currentItem },
                isPlayingProvider = { _uiState.value.isPlaying },
                currentPositionProvider = { _uiState.value.currentPosition },
                durationProvider = { _uiState.value.duration },
            )

            playerStatsTracker.startTracking(
                scope = viewModelScope,
                isMpvMode = { isMpvMode },
                exoMetadata = {
                    player?.let { p ->
                        ExoStreamMetadata(p.videoFormat, p.videoSize)
                    }
                },
                exoPosition = { player?.currentPosition ?: 0L },
                exoBuffered = { player?.bufferedPosition ?: 0L },
                mpvStats = { mpvPlayer?.getStats() },
            )

            viewModelScope.launch {
                playerScrobbler.showAutoNextPopup.collect { show ->
                    _uiState.update { it.copy(showAutoNextPopup = show) }
                }
            }

            ratingKey?.let { rKey ->
                serverId?.let { sId ->
                    loadMedia(rKey, sId)
                }
            }

            startPositionTracking()
        }

        private fun initializePlayer(application: Application) {
            player =
                playerInitializer.createExoPlayer(
                    context = application,
                    onPlayingChanged = { isPlaying -> _uiState.update { it.copy(isPlaying = isPlaying) } },
                    onStateChanged = { state -> _uiState.update { it.copy(isBuffering = state == ExoPlayerConstants.STATE_BUFFERING) } },
                    onTracksChanged = { /* Handled in controller */ },
                    onError = { e -> _uiState.update { it.copy(error = e.message) } },
                )

            mpvPlayer = playerInitializer.createMpvPlayer(application, viewModelScope)
        }

        private fun loadMedia(
            rKey: String,
            sId: String,
            bitrateOverride: Int? = null,
        ) {
            viewModelScope.launch {
                val result =
                    mediaLoader.loadMedia(
                        rKey = rKey,
                        sId = sId,
                        isMpvMode = isMpvMode,
                        bitrateOverride = bitrateOverride,
                        onMpvSwitchRequired = { switchToMpv() },
                    )

                result.onSuccess { res ->
                    if (res.needsMpvSwitch) return@onSuccess

                    this@PlayerViewModel.isDirectPlay = res.isDirectPlay

                    _uiState.update {
                        it.copy(
                            currentItem = res.item,
                            audioTracks = res.audioTracks,
                            subtitleTracks = res.subtitleTracks,
                            selectedAudio = res.selectedAudio,
                            selectedSubtitle = res.selectedSubtitle,
                            error = null,
                        )
                    }

                    if (isMpvMode) {
                        mpvPlayer?.play(res.streamUri!!)
                        val resumePos =
                            if (_uiState.value.currentPosition > 0) {
                                _uiState.value.currentPosition
                            } else if (startOffset > 0) {
                                startOffset
                            } else {
                                res.item.viewOffset
                            }
                        mpvPlayer?.seekTo(resumePos)
                    } else {
                        val mediaItem = androidx.media3.common.MediaItem.fromUri(res.streamUri!!)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        val resumePos =
                            if (_uiState.value.currentPosition > 0) {
                                _uiState.value.currentPosition
                            } else if (startOffset > 0) {
                                startOffset
                            } else {
                                res.item.viewOffset
                            }
                        player?.seekTo(resumePos)
                        player?.playWhenReady = true
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }

        fun onAction(action: PlayerAction) {
            actionHandler.handleAction(
                action = action,
                player = player,
                mpvPlayer = mpvPlayer,
                isMpvMode = isMpvMode,
                currentState = _uiState.value,
                onStateUpdate = { updateFn -> _uiState.update { it.updateFn() } },
                onLoadMediaRequested = { rKey, sId -> loadMedia(rKey, sId) },
                onToggleMpvRequested = {
                    if (isMpvMode) switchToExo() else switchToMpv()
                },
                scope = viewModelScope,
                exoPlayer = player,
            )
        }

        private fun startPositionTracking() {
            positionTracker.startTracking(
                scope = viewModelScope,
                isMpvMode = { isMpvMode },
                mpvPlayer = { mpvPlayer },
                exoPlayer = { player },
                onUpdate = { updateFn -> _uiState.update { it.updateFn() } },
                hasNextItem = { _uiState.value.nextItem != null },
                isPopupShown = { _uiState.value.showAutoNextPopup },
            )
        }

        private fun switchToMpv() {
            isMpvMode = true
            player?.pause()
            _uiState.update { it.copy(currentPosition = player?.currentPosition ?: it.currentPosition) }
            ratingKey?.let { rKey -> serverId?.let { sId -> loadMedia(rKey, sId) } }
        }

        private fun switchToExo() {
            isMpvMode = false
            mpvPlayer?.pause()
            _uiState.update { it.copy(currentPosition = mpvPlayer?.position?.value ?: it.currentPosition) }
            ratingKey?.let { rKey -> serverId?.let { sId -> loadMedia(rKey, sId) } }
        }

        override fun onCleared() {
            super.onCleared()
            player?.release()
            mpvPlayer?.release()
            positionTracker.stopTracking()
        }
    }
