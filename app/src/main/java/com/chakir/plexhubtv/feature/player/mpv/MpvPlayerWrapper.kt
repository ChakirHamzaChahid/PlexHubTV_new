package com.chakir.plexhubtv.feature.player.mpv

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class MpvPlayerWrapper(
    private val context: Context,
    private val scope: CoroutineScope,
) : MpvPlayer, SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver, DefaultLifecycleObserver {
    companion object {
    }

    private var surfaceView: SurfaceView? = null
    var isInitialized = false
        private set

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    // Stats Flows
    private val _videoBitrate = MutableStateFlow(0.0)
    val videoBitrate: StateFlow<Double> = _videoBitrate.asStateFlow()

    private val _fps = MutableStateFlow(0.0)
    val fps: StateFlow<Double> = _fps.asStateFlow()

    private val _droppedFrames = MutableStateFlow(0L)
    val droppedFrames: StateFlow<Long> = _droppedFrames.asStateFlow()

    private val _videoCodec = MutableStateFlow("Unknown")
    val videoCodec: StateFlow<String> = _videoCodec.asStateFlow()

    private val _audioCodec = MutableStateFlow("Unknown")
    val audioCodec: StateFlow<String> = _audioCodec.asStateFlow()

    private val _videoWidth = MutableStateFlow(0L)
    val videoWidth: StateFlow<Long> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0L)
    val videoHeight: StateFlow<Long> = _videoHeight.asStateFlow()

    private val _cacheDuration = MutableStateFlow(0.0)
    val cacheDuration: StateFlow<Double> = _cacheDuration.asStateFlow()

    private var pendingUrl: String? = null
    private var pendingPosition: Long? = null

    override fun initialize(viewGroup: ViewGroup) {
        if (isInitialized) return
        Timber.d("Initializing MPV...")

        try {
            MPVLib.create(context)
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("hwdec", "mediacodec")

            // Subtitles: use Android system fonts (fontconfig unavailable on Android)
            MPVLib.setOptionString("sub-ass", "yes")
            MPVLib.setOptionString("sub-font-provider", "none")
            MPVLib.setOptionString("sub-fonts-dir", "/system/fonts")
            MPVLib.setOptionString("sub-font", "sans-serif")
            MPVLib.setOptionString("sub-font-size", "55")
            MPVLib.setOptionString("sub-color", "#FFFFFFFF")
            MPVLib.setOptionString("sub-border-size", "3")

            MPVLib.init()

            MPVLib.addObserver(this)
            MPVLib.addLogObserver(this)

            MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)

            // Stats
            MPVLib.observeProperty("video-bitrate", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("estimated-vf-fps", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("drop-frame-count", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("video-format", MPVLib.MPV_FORMAT_STRING) // Codec
            MPVLib.observeProperty("audio-codec-name", MPVLib.MPV_FORMAT_STRING)
            MPVLib.observeProperty("demuxer-cache-duration", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("video-w", MPVLib.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("video-h", MPVLib.MPV_FORMAT_DOUBLE)

            surfaceView =
                SurfaceView(context).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    holder.addCallback(this@MpvPlayerWrapper)
                    holder.setFormat(PixelFormat.TRANSLUCENT) // Important for GPU output
                }
            viewGroup.addView(surfaceView)

            isInitialized = true
            Timber.d("MPV Initialized successfully")

            // Play pending URL if any
            pendingUrl?.let { url ->
                Timber.d("Playing pending URL: $url")
                play(url)
                pendingUrl = null
            }

            // Apply pending seek if any
            pendingPosition?.let { pos ->
                Timber.d("Applying pending seek: $pos ms")
                seekTo(pos)
                pendingPosition = null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MPV")
            _error.update { e.message }
        }
    }

    override fun play(url: String) {
        Timber.d("play() called with URL: $url (isInitialized=$isInitialized)")
        if (!isInitialized) {
            Timber.w("Player not initialized, queuing URL: $url")
            pendingUrl = url
            return
        }
        MPVLib.command(arrayOf("loadfile", url))
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun resume() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun seekTo(positionMs: Long) {
        if (!isInitialized) {
            Timber.w("Player not initialized, queuing seek: $positionMs ms")
            pendingPosition = positionMs
            return
        }
        val position = positionMs / 1000.0
        MPVLib.command(arrayOf("seek", position.toString(), "absolute"))
    }

    override fun setVolume(volume: Float) {
        if (!isInitialized) return
        MPVLib.setPropertyDouble("volume", (volume * 100).toDouble())
    }

    override fun setSpeed(speed: Double) {
        if (!isInitialized) return
        MPVLib.setPropertyDouble("speed", speed)
    }

    override fun setAudioId(aid: String) {
        if (!isInitialized) return
        Timber.d("Setting Audio ID: $aid")
        MPVLib.setPropertyString("aid", aid)
    }

    override fun setSubtitleId(sid: String) {
        if (!isInitialized) return
        Timber.d("Setting Subtitle ID: $sid")
        val result = MPVLib.setPropertyString("sid", sid)
        Timber.d("MPV setPropertyString('sid', $sid) returned: $result")
    }

    override fun setAudioDelay(delayMs: Long) {
        if (!isInitialized) return
        // MPV audio-delay is in seconds
        MPVLib.setPropertyDouble("audio-delay", delayMs / 1000.0)
    }

    override fun setSubtitleDelay(delayMs: Long) {
        if (!isInitialized) return
        // MPV sub-delay is in seconds
        MPVLib.setPropertyDouble("sub-delay", delayMs / 1000.0)
    }

    override fun attach(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    override fun release() {
        if (!isInitialized) return
        MPVLib.destroy()
        surfaceView = null
        isInitialized = false
    }

    override fun getStats(): com.chakir.plexhubtv.feature.player.PlayerStats {
        return com.chakir.plexhubtv.feature.player.PlayerStats(
            bitrate = "${_videoBitrate.value.toInt()} kbps",
            videoCodec = _videoCodec.value,
            audioCodec = _audioCodec.value,
            resolution = "${_videoWidth.value}x${_videoHeight.value}",
            fps = _fps.value,
            cacheDuration = (_cacheDuration.value * 1000).toLong(),
        )
    }

    // SurfaceHolder.Callback
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isInitialized) {
            MPVLib.attachSurface(holder.surface)
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        // N/A
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (isInitialized) {
            MPVLib.detachSurface()
        }
    }

    // MPVLib.EventObserver
    override fun eventProperty(property: String) {}

    override fun eventProperty(
        property: String,
        value: Long,
    ) {
        if (property == "time-pos") {
            // MPV usually returns double for time-pos
        }
    }

    override fun eventProperty(
        property: String,
        value: Double,
    ) {
        if (property == "time-pos") {
            _position.update { (value * 1000).toLong() }
        } else if (property == "duration") {
            _duration.update { (value * 1000).toLong() }
        } else if (property == "video-bitrate") {
            _videoBitrate.update { value }
        } else if (property == "estimated-vf-fps") {
            _fps.update { value }
        } else if (property == "demuxer-cache-duration") {
            _cacheDuration.update { value }
        } else if (property == "drop-frame-count") {
            _droppedFrames.update { value.toLong() }
        } else if (property == "video-w") {
            _videoWidth.update { value.toLong() }
        } else if (property == "video-h") {
            _videoHeight.update { value.toLong() }
        }
    }

    override fun eventProperty(
        property: String,
        value: String,
    ) {
        if (property == "video-format") {
            _videoCodec.update { value }
        } else if (property == "audio-codec-name") {
            _audioCodec.update { value }
        }
    }

    override fun eventProperty(
        property: String,
        value: Boolean,
    ) {
        if (property == "pause") {
            _isPlaying.update { !value }
        } else if (property == "paused-for-cache") {
            _isBuffering.update { value }
        }
    }

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
    override fun logMessage(
        prefix: String,
        level: Int,
        text: String,
    ) {
        if (level == MPVLib.MPV_LOG_LEVEL_ERROR || level == MPVLib.MPV_LOG_LEVEL_FATAL) {
            Timber.e("[$prefix] $text")
        }
    }
}
