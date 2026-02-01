package com.chakir.plexhubtv.core.util

import java.util.Locale

/**
 * Utilitaires pour le formatage des noms de Codecs (Audio, Vidéo, Sous-titres).
 * Convertit les codes techniques (e.g., "hevc", "dca") en noms affichables ("HEVC", "DTS").
 */
object CodecUtils {

    fun formatVideoCodec(codec: String): String {
        return when (codec.lowercase(Locale.US)) {
            "h264", "avc1", "avc" -> "H.264"
            "hevc", "h265", "hev1" -> "HEVC"
            "av1" -> "AV1"
            "vp8" -> "VP8"
            "vp9" -> "VP9"
            "mpeg2video", "mpeg2" -> "MPEG-2"
            "mpeg4" -> "MPEG-4"
            "vc1" -> "VC-1"
            else -> codec.uppercase(Locale.US)
        }
    }

    fun formatAudioCodec(codec: String): String {
        return when (codec.lowercase(Locale.US)) {
            "aac" -> "AAC"
            "ac3" -> "AC3"
            "eac3", "ec3" -> "E-AC3"
            "truehd" -> "TrueHD"
            "dts" -> "DTS"
            "dca" -> "DTS"
            "dtshd", "dts-hd" -> "DTS-HD"
            "flac" -> "FLAC"
            "mp3", "mp3float" -> "MP3"
            "opus" -> "Opus"
            "vorbis" -> "Vorbis"
            "pcm_s16le", "pcm_s24le", "pcm" -> "PCM"
            else -> codec.uppercase(Locale.US)
        }
    }

    fun formatSubtitleCodec(codec: String): String {
        return when (codec.uppercase(Locale.US)) {
            "SUBRIP" -> "SRT"
            "DVD_SUBTITLE" -> "DVD"
            "WEBVTT" -> "VTT"
            "HDMV_PGS_SUBTITLE" -> "PGS"
            "MOV_TEXT" -> "MOV"
            else -> codec.uppercase(Locale.US)
        }
    }

    fun getSubtitleExtension(codec: String?): String {
        if (codec == null) return "srt"
        return when (codec.lowercase(Locale.US)) {
            "subrip", "srt" -> "srt"
            "ass" -> "ass"
            "ssa" -> "ssa"
            "webvtt", "vtt" -> "vtt"
            "mov_text" -> "srt"
            "pgs", "hdmv_pgs_subtitle" -> "sup"
            "dvd_subtitle", "dvdsub" -> "sub"
            else -> "srt"
        }
    }
}
