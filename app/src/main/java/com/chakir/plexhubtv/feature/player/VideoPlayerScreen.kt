package com.chakir.plexhubtv.feature.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.chakir.plexhubtv.R
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import kotlinx.coroutines.delay
import android.view.KeyEvent as NativeKeyEvent

/**
 * Route principale pour l'écran de lecture vidéo.
 * Initialise le ViewModel, les gestionnaires de cycle de vie et le BackHandler.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerRoute(
    controlViewModel: PlayerControlViewModel = hiltViewModel(),
    trackViewModel: TrackSelectionViewModel = hiltViewModel(),
    statsViewModel: PlaybackStatsViewModel = hiltViewModel(),
    onClose: () -> Unit,
) {
    val uiState by controlViewModel.uiState.collectAsState()

    // Collecte des Chapitres et Marqueurs
    val chapters by controlViewModel.chapterMarkerManager.chapters.collectAsState()
    val markers by controlViewModel.chapterMarkerManager.markers.collectAsState()
    val visibleMarkers by controlViewModel.chapterMarkerManager.visibleMarkers.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Attache le joueur MPV au cycle de vie (Resume/Pause)
    LaunchedEffect(controlViewModel.mpvPlayer) {
        controlViewModel.mpvPlayer?.attach(lifecycleOwner)
    }

    // Gestion du PIP ou de l'orientation ici si nécessaire
    DisposableEffect(Unit) {
        onDispose {
            // Nettoyage sortie écran plein
        }
    }

    BackHandler {
        onClose()
    }

    VideoPlayerScreen(
        uiState = uiState,
        exoPlayer = controlViewModel.player,
        mpvPlayer = controlViewModel.mpvPlayer,
        chapters = chapters,
        markers = markers,
        visibleMarkers = visibleMarkers,
        onAction = { action ->
            if (action is PlayerAction.Close) {
                onClose()
            } else {
                // Route actions to appropriate VM
                when (action) {
                    is PlayerAction.SelectAudioTrack, 
                    is PlayerAction.SelectSubtitleTrack,
                    is PlayerAction.ShowAudioSelector,
                    is PlayerAction.ShowSubtitleSelector -> trackViewModel.onAction(action)
                    
                    is PlayerAction.TogglePerformanceOverlay -> statsViewModel.onAction(action)
                    
                    else -> controlViewModel.onAction(action)
                }
            }
        },
    )
}

/**
 * Écran principal du lecteur vidéo.
 *
 * Caractéristiques :
 * - Mode Hybride : Supporte ExoPlayer (Media3) ET MPV (pour les codecs exotiques).
 * - UI Overlay : Contrôles personnalisés (PlezyPlayerControls) avec auto-hide.
 * - Gestion Focus : Support complet du D-Pad (Android TV).
 * - Clavier/Télécommande : Interception des KeyEvents (Play, Pause, Seek).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    uiState: PlayerUiState,
    exoPlayer: androidx.media3.common.Player?,
    mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayer?,
    chapters: List<com.chakir.plexhubtv.core.model.Chapter> = emptyList(),
    markers: List<com.chakir.plexhubtv.core.model.Marker> = emptyList(),
    visibleMarkers: List<com.chakir.plexhubtv.core.model.Marker> = emptyList(),
    onAction: (PlayerAction) -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var lastInteractionTime by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, uiState.isPlaying, lastInteractionTime) {
        if (controlsVisible && uiState.isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Manage Focus when controls become visible
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            lastInteractionTime = System.currentTimeMillis() // Reset on show
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request might fail if not yet attached
            }
        }
    }

    // Unified Back Handler
    val isDialogVisible = uiState.showSettings || uiState.showAudioSelection || uiState.showSubtitleSelection || uiState.showSpeedSelection || uiState.showAudioSyncDialog || uiState.showSubtitleSyncDialog || uiState.showAutoNextPopup

    BackHandler(enabled = true) {
        if (isDialogVisible) {
            // Close any open dialog without stopping playback
            onAction(PlayerAction.DismissDialog)
        } else if (controlsVisible) {
            controlsVisible = false
        } else if (!uiState.isPlaying && !uiState.isBuffering && !uiState.error.isNullOrBlank().not()) {
            // If Paused (and not buffering/error), Back should Resume
            onAction(PlayerAction.Play)
        } else {
            onAction(PlayerAction.Close)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("screen_player")
                .semantics { contentDescription = "Écran de lecture" }
                .background(Color.Black)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        lastInteractionTime = System.currentTimeMillis() // Reset timer on input
                        when (event.nativeKeyEvent.keyCode) {
                            NativeKeyEvent.KEYCODE_DPAD_CENTER,
                            NativeKeyEvent.KEYCODE_DPAD_UP,
                            NativeKeyEvent.KEYCODE_DPAD_DOWN,
                            NativeKeyEvent.KEYCODE_DPAD_LEFT,
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
                            NativeKeyEvent.KEYCODE_ENTER,
                            -> {
                                if (!controlsVisible) {
                                    controlsVisible = true
                                    true
                                } else {
                                    false
                                }
                            }
                            // Media Controls
                            NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (uiState.isPlaying) onAction(PlayerAction.Pause) else onAction(PlayerAction.Play)
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_PLAY -> {
                                onAction(PlayerAction.Play)
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                onAction(PlayerAction.Pause)
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                onAction(PlayerAction.SeekTo(uiState.currentPosition + 30000))
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                onAction(PlayerAction.SeekTo(uiState.currentPosition - 10000))
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_NEXT -> {
                                onAction(PlayerAction.Next)
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                onAction(PlayerAction.Previous)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    controlsVisible = !controlsVisible
                    if (controlsVisible) lastInteractionTime = System.currentTimeMillis()
                },
    ) {
        // Hybrid Player Rendering
        if (uiState.isMpvMode && mpvPlayer != null) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        keepScreenOn = true // Prevent sleep mode during MPV playback
                        mpvPlayer.initialize(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (exoPlayer != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = exoPlayer
                        useController = false // Use our custom controls
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Overlay Controls (Plezy Style)
        // Show controls if manually toggled OR if paused (but NOT if just buffering/loading)
        val shouldShowControls = controlsVisible || (!uiState.isPlaying && !uiState.isBuffering)

        AnimatedVisibility(
            visible = shouldShowControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            com.chakir.plexhubtv.feature.player.components.NetflixPlayerControls(
                media = uiState.currentItem,
                isPlaying = uiState.isPlaying,
                currentTimeMs = uiState.currentPosition,
                durationMs = uiState.duration,
                onPlayPauseToggle = {
                    if (uiState.isPlaying) onAction(PlayerAction.Pause) else onAction(PlayerAction.Play)
                },
                onSeek = { onAction(PlayerAction.SeekTo(it)) },
                onSkipForward = { onAction(PlayerAction.SeekTo(uiState.currentPosition + 30000)) },
                onSkipBackward = { onAction(PlayerAction.SeekTo(uiState.currentPosition - 10000)) },
                onNext = { onAction(PlayerAction.Next) },
                onStop = { onAction(PlayerAction.Close) },
                isVisible = shouldShowControls,
                chapters = chapters,
                markers = markers,
                visibleMarkers = visibleMarkers,
                onSkipMarker = { onAction(PlayerAction.SkipMarker(it)) },
                onShowSubtitles = { onAction(PlayerAction.ShowSubtitleSelector) },
                onShowAudio = { onAction(PlayerAction.ShowAudioSelector) },
                onShowSettings = { onAction(PlayerAction.ToggleSettings) },
                modifier = Modifier,
                playPauseFocusRequester = focusRequester
            )
        }

        if (uiState.isBuffering) {
            val loadingDesc = stringResource(R.string.player_loading_description)
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("player_loading")
                    .semantics { contentDescription = loadingDesc }
            )
        }

        // Performance Overlay
        val playerStats = uiState.playerStats
        if (uiState.showPerformanceOverlay && playerStats != null) {
            com.chakir.plexhubtv.feature.player.ui.components.PerformanceOverlay(
                stats = playerStats,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp),
            )
        }

        // Error Overlay (replaces simple error display)
        if (uiState.error != null) {
            com.chakir.plexhubtv.feature.player.ui.components.PlayerErrorOverlay(
                errorMessage = uiState.error,
                errorType = uiState.errorType,
                retryCount = uiState.networkRetryCount,
                isMpvMode = uiState.isMpvMode,
                onRetry = { onAction(PlayerAction.RetryPlayback) },
                onSwitchToMpv = { onAction(PlayerAction.SwitchToMpv) },
                onClose = { onAction(PlayerAction.Close) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Auto-Next Popup
        AnimatedVisibility(
            visible = uiState.showAutoNextPopup && uiState.nextItem != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { -it },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 32.dp),
        ) {
            uiState.nextItem?.let { nextItem ->
                AutoNextPopup(
                    item = nextItem,
                    onPlayNow = { onAction(PlayerAction.PlayNext) },
                    onCancel = { onAction(PlayerAction.CancelAutoNext) },
                    modifier = Modifier.testTag("player_auto_next_popup")
                )
            }
        }

        // Settings Dialog (Rendered only if generic settings are shown)
        if (uiState.showSettings) {
            com.chakir.plexhubtv.feature.player.ui.components.PlayerSettingsDialog(
                uiState = uiState,
                onSelectQuality = { onAction(PlayerAction.SelectQuality(it)) },
                onToggleStats = { onAction(PlayerAction.TogglePerformanceOverlay) },
                onDismiss = { onAction(PlayerAction.ToggleSettings) },
            )
        }

        // Audio Selection Dialog
        if (uiState.showAudioSelection) {
            com.chakir.plexhubtv.feature.player.ui.components.AudioSelectionDialog(
                tracks = uiState.audioTracks,
                selectedTrack = uiState.selectedAudio,
                onSelect = { onAction(PlayerAction.SelectAudioTrack(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) },
            )
        }

        // Subtitle Selection Dialog
        if (uiState.showSubtitleSelection) {
            com.chakir.plexhubtv.feature.player.ui.components.SubtitleSelectionDialog(
                tracks = uiState.subtitleTracks,
                selectedTrack = uiState.selectedSubtitle,
                onSelect = { onAction(PlayerAction.SelectSubtitleTrack(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) },
            )
        }

        if (uiState.showSpeedSelection) {
            com.chakir.plexhubtv.feature.player.ui.components.SpeedSelectionDialog(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { onAction(PlayerAction.SetPlaybackSpeed(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) },
            )
        }

        // Audio Sync Dialog
        if (uiState.showAudioSyncDialog) {
            com.chakir.plexhubtv.feature.player.ui.components.SyncSettingsDialog(
                title = "Audio Sync",
                currentDelayMs = uiState.audioDelay,
                onDelayChanged = { onAction(PlayerAction.SetAudioDelay(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) },
            )
        }

        // Subtitle Sync Dialog
        if (uiState.showSubtitleSyncDialog) {
            com.chakir.plexhubtv.feature.player.ui.components.SyncSettingsDialog(
                title = "Subtitle Sync",
                currentDelayMs = uiState.subtitleDelay,
                onDelayChanged = { onAction(PlayerAction.SetSubtitleDelay(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) },
            )
        }
    }
}

@Composable
fun AutoNextPopup(
    item: MediaItem,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocusRequester = remember { FocusRequester() }
    val nextEpisodeDesc = stringResource(R.string.player_next_episode_title, item.title)
    val nextEpisodeLabel = stringResource(R.string.player_next_episode_label)

    // Auto-focus the "Play Now" button when popup appears
    LaunchedEffect(Unit) {
        try {
            playFocusRequester.requestFocus()
        } catch (_: Exception) { }
    }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = modifier
            .width(300.dp)
            .semantics { contentDescription = nextEpisodeDesc },
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail
            coil.compose.AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier =
                    Modifier
                        .size(width = 80.dp, height = 45.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nextEpisodeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayFocused by playInteractionSource.collectIsFocusedAsState()

                    Button(
                        onClick = onPlayNow,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier =
                            Modifier
                                .height(32.dp)
                                .testTag("auto_next_play_button")
                                .scale(if (isPlayFocused) 1.1f else 1f)
                                .focusRequester(playFocusRequester),
                        interactionSource = playInteractionSource,
                        colors =
                            if (isPlayFocused) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                    ) {
                        Text(stringResource(R.string.player_play_now), style = MaterialTheme.typography.labelSmall)
                    }

                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    val isCancelFocused by cancelInteractionSource.collectIsFocusedAsState()

                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier =
                            Modifier
                                .height(32.dp)
                                .testTag("auto_next_cancel_button")
                                .scale(if (isCancelFocused) 1.1f else 1f),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCancelFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                            ),
                        interactionSource = cancelInteractionSource,
                    ) {
                        Text(
                            stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCancelFocused) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                }
            }
        }
    }
}

// Local PlayerControls removed in favor of com.chakir.plexhubtv.feature.player.PlayerControls

@Preview(showBackground = true)
@Composable
fun PreviewVideoPlayerScreen() {
    val movie =
        MediaItem(
            id = "1",
            ratingKey = "1",
            serverId = "s1",
            title = "Interstellar",
            type = MediaType.Movie,
            year = 2014,
            durationMs = 10140000,
        )
    MaterialTheme {
        VideoPlayerScreen(
            uiState =
                PlayerUiState(
                    isPlaying = true,
                    currentItem = movie,
                    duration = 10140000,
                    currentPosition = 3600000,
                ),
            exoPlayer = null,
            mpvPlayer = null,
            onAction = {},
        )
    }
}
