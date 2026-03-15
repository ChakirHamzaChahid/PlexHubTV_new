package com.chakir.plexhubtv.feature.player.profile

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceProfile(
    val videoCodecs: Set<String>,
    val audioCodecs: Set<String>,
    val maxWidth: Int,
    val maxHeight: Int,
    val supportsHDR: Boolean,
    val maxBitDepth: Int,
)

@Singleton
class DeviceProfileService @Inject constructor() {

    val profile: DeviceProfile by lazy { detectProfile() }

    fun canDirectPlayVideo(codec: String?): Boolean {
        if (codec == null) return true
        return normalizeVideoCodec(codec) in profile.videoCodecs
    }

    fun canDirectPlayAudio(codec: String?): Boolean {
        if (codec == null) return true
        return normalizeAudioCodec(codec) in profile.audioCodecs
    }

    private fun detectProfile(): DeviceProfile {
        val codecList = try {
            MediaCodecList(MediaCodecList.ALL_CODECS)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate codecs")
            return fallbackProfile()
        }

        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        var maxWidth = 1920
        var maxHeight = 1080
        var supportsHDR = false
        var maxBitDepth = 8

        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            val isHardware = !info.name.contains("google", ignoreCase = true) &&
                !info.name.contains("sw", ignoreCase = true) &&
                !info.name.contains("c2.android", ignoreCase = true)

            for (type in info.supportedTypes) {
                val mime = type.lowercase()
                when {
                    mime.startsWith("video/") && isHardware -> {
                        mimeToVideoCodec(mime)?.let { videoCodecs.add(it) }
                        try {
                            val caps = info.getCapabilitiesForType(type)
                            val videoCaps = caps.videoCapabilities
                            if (videoCaps != null) {
                                val w = videoCaps.supportedWidths.upper
                                val h = videoCaps.supportedHeights.upper
                                if (w > maxWidth) maxWidth = w
                                if (h > maxHeight) maxHeight = h
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val profileLevels = caps.profileLevels
                                for (pl in profileLevels) {
                                    if (isHDRProfile(mime, pl.profile)) {
                                        supportsHDR = true
                                        maxBitDepth = maxOf(maxBitDepth, 10)
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    mime.startsWith("audio/") -> {
                        mimeToAudioCodec(mime)?.let { audioCodecs.add(it) }
                    }
                }
            }
        }

        val profile = DeviceProfile(
            videoCodecs = videoCodecs,
            audioCodecs = audioCodecs,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            supportsHDR = supportsHDR,
            maxBitDepth = maxBitDepth,
        )
        Timber.i(
            "DeviceProfile: video=%s, audio=%s, maxRes=%dx%d, HDR=%s, bitDepth=%d",
            videoCodecs, audioCodecs, maxWidth, maxHeight, supportsHDR, maxBitDepth,
        )
        return profile
    }

    private fun isHDRProfile(mime: String, profile: Int): Boolean {
        if (mime == "video/hevc" && profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) return true
        if (mime == "video/x-vnd.on2.vp9" && profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mime == "video/av01" && profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10) return true
        }
        return false
    }

    private fun mimeToVideoCodec(mime: String): String? = when (mime) {
        "video/avc" -> "h264"
        "video/hevc" -> "hevc"
        "video/x-vnd.on2.vp9" -> "vp9"
        "video/av01" -> "av1"
        "video/mp4v-es" -> "mpeg4"
        "video/x-vnd.on2.vp8" -> "vp8"
        else -> null
    }

    private fun mimeToAudioCodec(mime: String): String? = when (mime) {
        "audio/mp4a-latm" -> "aac"
        "audio/ac3" -> "ac3"
        "audio/eac3" -> "eac3"
        "audio/vnd.dts" -> "dts"
        "audio/vnd.dts.hd" -> "dts-hd"
        "audio/true-hd" -> "truehd"
        "audio/opus" -> "opus"
        "audio/flac" -> "flac"
        "audio/vorbis" -> "vorbis"
        "audio/mpeg" -> "mp3"
        "audio/raw" -> "pcm"
        else -> null
    }

    private fun normalizeVideoCodec(codec: String): String = when (codec.lowercase()) {
        "h264", "avc", "avc1" -> "h264"
        "hevc", "h265" -> "hevc"
        "vp9" -> "vp9"
        "av1" -> "av1"
        "mpeg4", "mp4v" -> "mpeg4"
        "vp8" -> "vp8"
        else -> codec.lowercase()
    }

    private fun normalizeAudioCodec(codec: String): String = when (codec.lowercase()) {
        "aac", "mp4a" -> "aac"
        "ac3" -> "ac3"
        "eac3", "eac-3" -> "eac3"
        "dts" -> "dts"
        "dts-hd", "dts-hd ma", "dtshd" -> "dts-hd"
        "truehd" -> "truehd"
        "opus" -> "opus"
        "flac" -> "flac"
        "vorbis" -> "vorbis"
        "mp3" -> "mp3"
        else -> codec.lowercase()
    }

    private fun fallbackProfile(): DeviceProfile = DeviceProfile(
        videoCodecs = setOf("h264"),
        audioCodecs = setOf("aac", "mp3"),
        maxWidth = 1920,
        maxHeight = 1080,
        supportsHDR = false,
        maxBitDepth = 8,
    )
}
