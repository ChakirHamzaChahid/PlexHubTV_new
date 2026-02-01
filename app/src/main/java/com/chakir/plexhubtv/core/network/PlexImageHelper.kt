package com.chakir.plexhubtv.core.network

import android.net.Uri
import com.chakir.plexhubtv.domain.model.Server
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

enum class ImageType {
    POSTER, ART, THUMB, LOGO, AVATAR
}

/**
 * Utilitaire pour la construction d'URLs d'images Plex optimisées.
 *
 * Gère le Transcodage d'images (redimensionnement côté serveur) pour économiser la bande passante
 * et améliorer les performances de chargement sur Android TV.
 */
object PlexImageHelper {
    private const val WIDTH_ROUNDING_FACTOR = 40
    private const val HEIGHT_ROUNDING_FACTOR = 60
    private const val MAX_TRANSCODED_WIDTH = 1920
    private const val MAX_TRANSCODED_HEIGHT = 1080
    private const val MIN_TRANSCODED_WIDTH = 160
    private const val MIN_TRANSCODED_HEIGHT = 240

    fun getOptimizedImageUrl(
        baseUrl: String,
        token: String,
        path: String?,
        width: Int,
        height: Int,
        imageType: ImageType = ImageType.POSTER,
        enableTranscoding: Boolean = true
    ): String {
        if (path.isNullOrBlank()) return ""

        if (path.startsWith("http")) return path

        if (!enableTranscoding || !shouldTranscode(path)) {
            return "$baseUrl$path?X-Plex-Token=$token"
        }

        // Apply rounding for cache hit rate
        val roundedWidth = roundToFactor(width, WIDTH_ROUNDING_FACTOR)
            .coerceIn(MIN_TRANSCODED_WIDTH, MAX_TRANSCODED_WIDTH)
        val roundedHeight = roundToFactor(height, HEIGHT_ROUNDING_FACTOR)
            .coerceIn(MIN_TRANSCODED_HEIGHT, MAX_TRANSCODED_HEIGHT)

        val encodedPath = Uri.encode("$path${if (path.contains("?")) "&" else "?"}X-Plex-Token=$token")
        
        val builder = Uri.parse("$baseUrl/photo/:/transcode").buildUpon()
            .appendQueryParameter("width", roundedWidth.toString())
            .appendQueryParameter("height", roundedHeight.toString())
            .appendQueryParameter("minSize", "1")
            .appendQueryParameter("upscale", "1")
            .appendQueryParameter("url", encodedPath)
            .appendQueryParameter("X-Plex-Token", token)

        return builder.build().toString()
    }

    private fun roundToFactor(value: Int, factor: Int): Int {
        return (ceil(value.toDouble() / factor) * factor).toInt()
    }

    private fun shouldTranscode(path: String): Boolean {
        return !path.contains("/photo/:/transcode")
    }
}
