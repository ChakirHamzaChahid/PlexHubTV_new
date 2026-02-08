package com.chakir.plexhubtv.feature.player.mpv

import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.chakir.plexhubtv.feature.player.PlayerStats
import kotlinx.coroutines.flow.StateFlow

interface MpvPlayer {
    val isPlaying: StateFlow<Boolean>
    val isBuffering: StateFlow<Boolean>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val error: StateFlow<String?>

    fun initialize(viewGroup: ViewGroup)

    fun play(url: String)

    fun resume()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setVolume(volume: Float)

    fun setSpeed(speed: Double)

    fun setAudioId(aid: String)

    fun setSubtitleId(sid: String)

    fun setAudioDelay(delayMs: Long)

    fun setSubtitleDelay(delayMs: Long)

    fun attach(lifecycleOwner: LifecycleOwner)

    fun release()

    fun getStats(): PlayerStats
}
