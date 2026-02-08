package com.chakir.plexhubtv.core.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Transforms a direct Plex image URL into a transcoded/optimized version.
 */
fun getOptimizedImageUrl(
    originalUrl: String?,
    width: Int = 300,
    height: Int = 450,
): String? {
    if (originalUrl.isNullOrEmpty()) return null
    if (originalUrl.contains("/photo/:/transcode")) return originalUrl

    try {
        val uri = URI(originalUrl)
        val scheme = uri.scheme
        val host = uri.host
        val port = uri.port
        val portSuffix = if (port != -1) ":$port" else ""
        val baseUrl = "$scheme://$host$portSuffix"
        val path = uri.path

        // Parse query for token
        val query = uri.query
        val token = query?.split("&")?.find { it.startsWith("X-Plex-Token=") }?.substringAfter("=")

        if (token.isNullOrEmpty()) return originalUrl

        val encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

        return "$baseUrl/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$encodedPath&X-Plex-Token=$token"
    } catch (e: Exception) {
        // Fallback if URI parsing fails
        return originalUrl
    }
}
