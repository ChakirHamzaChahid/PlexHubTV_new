package com.chakir.plexhubtv.feature.splash

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chakir.plexhubtv.R
import timber.log.Timber

/**
 * Écran Splash style Netflix.
 * Affiche une vidéo d'intro (Intro.mp4), vérifie l'auth en arrière-plan.
 */
@Composable
fun SplashRoute(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToLoading: () -> Unit,
    onNavigateToLibrarySelection: () -> Unit = {},
) {
    var isVideoEnded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SplashNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                SplashNavigationEvent.NavigateToLoading -> onNavigateToLoading()
                SplashNavigationEvent.NavigateToLibrarySelection -> onNavigateToLibrarySelection()
            }
        }
    }

    // Notify ViewModel when video ends
    LaunchedEffect(isVideoEnded) {
        if (isVideoEnded) {
            viewModel.onVideoEnded()
        }
    }

    SplashScreen(
        viewModel = viewModel,
        onVideoEnded = { isVideoEnded = true }
    )
}

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onVideoEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val skipButtonFocusRequester = remember { FocusRequester() }

    // UX21: Auto-focus the skip button on initial display
    LaunchedEffect(Unit) {
        try {
            skipButtonFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus might fail if not yet attached, that's okay
        }
    }

    // Create ExoPlayer and release it when leaving composition
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // DO NOT loop - play once and trigger navigation when done
            repeatMode = Player.REPEAT_MODE_OFF
            // Load intro video from res/raw
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")
            setMediaItem(MediaItem.fromUri(videoUri))

            // Listen for playback events
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onVideoEnded()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        viewModel.onVideoStarted()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.e(error, "Splash video playback error, falling back")
                    viewModel.onVideoError()
                }
            })

            prepare()
            playWhenReady = true
            // Mute the video (optional - remove this line if you want sound)
            //volume = 0f
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_splash")
            .semantics { contentDescription = "Écran de démarrage" }
            .background(Color.Black), // Netflix black background
        contentAlignment = Alignment.Center
    ) {
        // Video player view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide playback controls
                    // Make video fit screen (scale to fill)
                    // resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // UX21: Skip button in top-right corner
        val skipInteractionSource = remember { MutableInteractionSource() }
        val isSkipFocused by skipInteractionSource.collectIsFocusedAsState()

        Button(
            onClick = { viewModel.onSkipRequested() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
                .focusRequester(skipButtonFocusRequester)
                .scale(if (isSkipFocused) 1.1f else 1f)
                .testTag("splash_skip_button"),
            interactionSource = skipInteractionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSkipFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                contentColor = if (isSkipFocused) Color.White else Color.Black
            )
        ) {
            Text(
                text = stringResource(R.string.action_skip),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
