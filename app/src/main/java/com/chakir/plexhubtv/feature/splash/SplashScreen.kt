package com.chakir.plexhubtv.feature.splash

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chakir.plexhubtv.R

/**
 * Écran Splash style Netflix.
 * Affiche une vidéo d'intro (Intro.mp4), vérifie l'auth en arrière-plan.
 */
@Composable
fun SplashRoute(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToLoading: () -> Unit,
) {
    var isVideoEnded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SplashNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                SplashNavigationEvent.NavigateToLoading -> onNavigateToLoading()
            }
        }
    }

    // Notify ViewModel when video ends
    LaunchedEffect(isVideoEnded) {
        if (isVideoEnded) {
            viewModel.onVideoEnded()
        }
    }

    SplashScreen(onVideoEnded = { isVideoEnded = true })
}

@Composable
fun SplashScreen(onVideoEnded: () -> Unit = {}) {
    val context = LocalContext.current

    // Create ExoPlayer and release it when leaving composition
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // DO NOT loop - play once and trigger navigation when done
            repeatMode = Player.REPEAT_MODE_OFF
            // Load intro video from res/raw
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.intro}")
            setMediaItem(MediaItem.fromUri(videoUri))

            // Listen for playback end
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onVideoEnded()
                    }
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
    }
}
