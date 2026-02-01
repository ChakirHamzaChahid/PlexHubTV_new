package com.chakir.plexhubtv.feature.player

import android.app.Activity
import android.util.Log
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.chakir.plexhubtv.domain.model.MediaItem
import com.chakir.plexhubtv.domain.model.MediaType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import android.view.KeyEvent as NativeKeyEvent

/**
 * Route principale pour l'écran de lecture vidéo.
 * Initialise le ViewModel, les gestionnaires de cycle de vie et le BackHandler.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerRoute(
    viewModel: PlayerViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Collecte des Chapitres et Marqueurs
    val chapters by viewModel.chapterMarkerManager.chapters.collectAsState()
    val markers by viewModel.chapterMarkerManager.markers.collectAsState()
    val visibleMarkers by viewModel.chapterMarkerManager.visibleMarkers.collectAsState()
    
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Attache le joueur MPV au cycle de vie (Resume/Pause)
    LaunchedEffect(viewModel.mpvPlayer) {
        viewModel.mpvPlayer?.attach(lifecycleOwner)
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
        exoPlayer = viewModel.getExoPlayer(),
        mpvPlayer = viewModel.mpvPlayer,
        chapters = chapters,
        markers = markers,
        visibleMarkers = visibleMarkers,
        onAction = { action ->
            if (action is PlayerAction.Close) onClose()
            else viewModel.onAction(action)
        }
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
    mpvPlayer: com.chakir.plexhubtv.feature.player.mpv.MpvPlayerWrapper?,
    chapters: List<com.chakir.plexhubtv.domain.model.Chapter> = emptyList(),
    markers: List<com.chakir.plexhubtv.domain.model.Marker> = emptyList(),
    visibleMarkers: List<com.chakir.plexhubtv.domain.model.Marker> = emptyList(),
    onAction: (PlayerAction) -> Unit
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
    val isDialogVisible = uiState.showSettings || uiState.showAudioSelection || uiState.showSubtitleSelection || uiState.showAutoNextPopup
    
    BackHandler(enabled = true) {
        if (isDialogVisible) {
            // Close any open dialog without stopping playback
            onAction(PlayerAction.DismissDialog)
        } else if (controlsVisible) {
            controlsVisible = false
        } else {
            onAction(PlayerAction.Close)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        NativeKeyEvent.KEYCODE_ENTER -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                true
                            } else false
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
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { 
                controlsVisible = !controlsVisible 
                if (controlsVisible) lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        // Hybrid Player Rendering
        if (uiState.isMpvMode && mpvPlayer != null) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        mpvPlayer.initialize(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (exoPlayer != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = exoPlayer
                        useController = false // Use our custom controls
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay Controls (Plezy Style)
        AnimatedVisibility(
            visible = controlsVisible || !uiState.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            com.chakir.plexhubtv.feature.player.ui.PlezyPlayerControls(
                uiState = uiState,
                onAction = onAction,
                title = uiState.currentItem?.title ?: "",
                chapters = chapters,
                markers = markers,
                visibleMarkers = visibleMarkers,
                playPauseFocusRequester = focusRequester
            )
        }
        
        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        
        if (uiState.error != null) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
             }
        }
        
        // Auto-Next Popup
        AnimatedVisibility(
            visible = uiState.showAutoNextPopup && uiState.nextItem != null,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 140.dp, end = 32.dp)
        ) {
            uiState.nextItem?.let { nextItem ->
                AutoNextPopup(
                    item = nextItem,
                    onPlayNow = { onAction(PlayerAction.PlayNext) },
                    onCancel = { onAction(PlayerAction.CancelAutoNext) }
                )
            }
        }

        // Settings Dialog (Rendered only if generic settings are shown)
        if (uiState.showSettings) {
            com.chakir.plexhubtv.feature.player.ui.components.PlayerSettingsDialog(
                uiState = uiState,
                onSelectQuality = { onAction(PlayerAction.SelectQuality(it)) },
                onDismiss = { onAction(PlayerAction.ToggleSettings) }
            )
        }

        // Audio Selection Dialog
        if (uiState.showAudioSelection) {
            com.chakir.plexhubtv.feature.player.ui.components.AudioSelectionDialog(
                tracks = uiState.audioTracks,
                selectedTrack = uiState.selectedAudio,
                onSelect = { onAction(PlayerAction.SelectAudioTrack(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) }
            )
        }

        // Subtitle Selection Dialog
        if (uiState.showSubtitleSelection) {
            com.chakir.plexhubtv.feature.player.ui.components.SubtitleSelectionDialog(
                tracks = uiState.subtitleTracks,
                selectedTrack = uiState.selectedSubtitle,
                onSelect = { onAction(PlayerAction.SelectSubtitleTrack(it)) },
                onDismiss = { onAction(PlayerAction.DismissDialog) }
            )
        }
    }
}

@Composable
fun AutoNextPopup(
    item: MediaItem,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.width(300.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Thumbnail
            coil.compose.AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(width = 80.dp, height = 45.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next Episode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val playInteractionSource = remember { MutableInteractionSource() }
                    val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
                    
                    Button(
                        onClick = onPlayNow,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .scale(if (isPlayFocused) 1.1f else 1f),
                        interactionSource = playInteractionSource,
                        colors = if (isPlayFocused) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) 
                                 else ButtonDefaults.buttonColors()
                    ) {
                        Text("Play Now", style = MaterialTheme.typography.labelSmall)
                    }

                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    val isCancelFocused by cancelInteractionSource.collectIsFocusedAsState()

                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .scale(if (isCancelFocused) 1.1f else 1f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isCancelFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)
                        ),
                        interactionSource = cancelInteractionSource
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall, color = if (isCancelFocused) MaterialTheme.colorScheme.primary else Color.White)
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
    val movie = MediaItem(
        id = "1", ratingKey = "1", serverId = "s1", title = "Interstellar", type = MediaType.Movie, year = 2014, durationMs = 10140000
    )
    MaterialTheme {
        VideoPlayerScreen(
            uiState = PlayerUiState(
                isPlaying = true,
                currentItem = movie,
                duration = 10140000,
                currentPosition = 3600000
            ),
            exoPlayer = null,
            mpvPlayer = null,
            onAction = {}
        )
    }
}
