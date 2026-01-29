package com.chakir.plexhubtv.feature.player.mpv

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MpvPlayerWrapper(
    private val context: Context,
    private val scope: CoroutineScope
) : SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "MpvPlayerWrapper"
    }

    private var surfaceView: SurfaceView? = null
    var isInitialized = false
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun initialize(viewGroup: ViewGroup) {
        if (isInitialized) return

        try {
            MPVLib.create(context)
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec")
            
            // Enable ASS subtitles
            MPVLib.setOptionString("sub-ass", "yes")
            MPVLib.setOptionString("sub-font-size", "55")

            MPVLib.init()

            MPVLib.addObserver(this)
            MPVLib.addLogObserver(this)

            surfaceView = SurfaceView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, 
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                holder.addCallback(this@MpvPlayerWrapper)
                holder.setFormat(PixelFormat.TRANSLUCENT) // Important for GPU output
            }
            viewGroup.addView(surfaceView)

            // Copy fonts for ASS if needed (can implement later)
            // copyAssets()

            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            _error.update { e.message }
        }
    }

    fun play(url: String) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("loadfile", url))
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun resume() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun pause() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean("pause", true)
    }

    fun seekTo(positionMs: Long) {
        if (!isInitialized) return
        val position = positionMs / 1000.0
        MPVLib.command(arrayOf("seek", position.toString(), "absolute"))
    }
    
    fun setVolume(volume: Float) {
         if (!isInitialized) return
         MPVLib.setPropertyDouble("volume", (volume * 100).toDouble())
    }

    fun attach(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    fun release() {
        if (!isInitialized) return
        MPVLib.destroy()
        surfaceView = null
        isInitialized = false
    }

    // SurfaceHolder.Callback
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isInitialized) {
            MPVLib.attachSurface(holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // N/A
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (isInitialized) {
            MPVLib.detachSurface()
        }
    }

    // MPVLib.EventObserver
    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Long) {
        if (property == "time-pos") {
             // MPV usually returns double for time-pos
        }
    }
    
    override fun eventProperty(property: String, value: Double) {
         if (property == "time-pos") {
             _position.update { (value * 1000).toLong() }
         } else if (property == "duration") {
             _duration.update { (value * 1000).toLong() }
         }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            _isPlaying.update { !value }
        } else if (property == "paused-for-cache") {
            _isBuffering.update { value }
        }
    }

    override fun eventProperty(property: String, value: String) {}

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                // Video started
                 _isBuffering.update { false }
            }
            MPVLib.MPV_EVENT_END_FILE -> {
                // Finished
            }
        }
    }

    // MPVLib.LogObserver
    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level == MPVLib.MPV_LOG_LEVEL_ERROR || level == MPVLib.MPV_LOG_LEVEL_FATAL) {
            Log.e(TAG, "[$prefix] $text")
        }
    }
}
