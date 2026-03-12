package com.chakir.plexhubtv.core.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton service for playing TV show theme songs on detail screens.
 * Uses a lightweight ExoPlayer instance with fade-in/fade-out.
 */
@Singleton
class ThemeSongService @Inject constructor(
    private val application: Application,
) {
    private var player: ExoPlayer? = null
    private var fadeJob: Job? = null
    private var currentUrl: String? = null

    @OptIn(UnstableApi::class)
    fun play(url: String, volume: Float = 0.3f, scope: CoroutineScope) {
        if (url == currentUrl && player?.isPlaying == true) return

        stop()
        currentUrl = url

        try {
            val exo = ExoPlayer.Builder(application).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                repeatMode = Player.REPEAT_MODE_ONE
                this.volume = 0f
                prepare()
                playWhenReady = true
            }
            player = exo

            // Fade in
            fadeJob?.cancel()
            fadeJob = scope.launch {
                val targetVolume = volume.coerceIn(0f, 1f)
                val steps = 20
                val stepDelay = 50L // 1 second total fade
                for (i in 1..steps) {
                    val v = targetVolume * (i.toFloat() / steps)
                    try { exo.volume = v } catch (_: Exception) { break }
                    delay(stepDelay)
                }
            }

            Timber.d("ThemeSong: Playing $url at volume $volume")
        } catch (e: Exception) {
            Timber.e(e, "ThemeSong: Failed to play")
            stop()
        }
    }

    fun stop(scope: CoroutineScope? = null) {
        fadeJob?.cancel()
        fadeJob = null

        val exo = player
        if (exo != null && scope != null) {
            // Fade out then release
            fadeJob = scope.launch {
                val currentVol = exo.volume
                val steps = 10
                val stepDelay = 30L // 300ms total fade
                for (i in 1..steps) {
                    val v = currentVol * (1f - i.toFloat() / steps)
                    try { exo.volume = v } catch (_: Exception) { break }
                    delay(stepDelay)
                }
                releasePlayer()
            }
        } else {
            releasePlayer()
        }

        currentUrl = null
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (e: Exception) {
            Timber.w(e, "ThemeSong: Release failed")
        }
        player = null
    }
}
