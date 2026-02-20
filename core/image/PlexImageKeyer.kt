package com.chakir.plexhubtv.core.image

import android.net.Uri
import coil3.key.Keyer
import coil3.request.Options
import java.net.URLDecoder

/**
 * Custom Coil Keyer for Plex URLs.
 *
 * Produces cache keys that are INDEPENDENT of:
 * 1. Server hostname/IP (so LAN vs relay URLs hit the same cache)
 * 2. X-Plex-Token (so token changes don't invalidate cache)
 * 3. Other volatile params (client ID, timestamps)
 *
 * For transcode URLs: key = "plex-transcode://{relative_path}?w={width}&h={height}"
 * For direct URLs: key = "plex-direct://{path}"
 */
class PlexImageKeyer : Keyer<Uri> {
    override fun key(
        data: Uri,
        options: Options,
    ): String? {
        // Only handle http/https URLs
        if (data.scheme != "http" && data.scheme != "https") return null

        // Only handle Plex URLs (must have token or be a transcode URL)
        val isPlexUrl = data.getQueryParameter("X-Plex-Token") != null ||
            data.toString().contains("X-Plex-Token=") ||
            data.path?.contains("/photo/:/transcode") == true
        if (!isPlexUrl) return null

        // Case 1: Transcode URL - extract the relative image path and dimensions
        if (data.path?.contains("/photo/:/transcode") == true) {
            return buildTranscodeKey(data)
        }

        // Case 2: Direct image URL - extract just the path
        return buildDirectKey(data)
    }

    private fun buildTranscodeKey(data: Uri): String {
        val width = data.getQueryParameter("width") ?: "300"
        val height = data.getQueryParameter("height") ?: "450"

        // Extract the encoded 'url' parameter which contains the relative image path
        val encodedUrl = data.getQueryParameter("url")
        if (encodedUrl != null) {
            // Decode the URL parameter to get the relative path
            val decodedPath = try {
                URLDecoder.decode(encodedUrl, "UTF-8")
            } catch (e: Exception) {
                encodedUrl
            }
            // Strip any token from the decoded path
            val cleanPath = decodedPath
                .replace(Regex("[?&]X-Plex-Token=[^&]*"), "")
                .trimEnd('?', '&')

            return "plex-transcode://$cleanPath?w=$width&h=$height"
        }

        // Fallback: use the path without hostname
        val path = data.path ?: return data.toString()
        return "plex-transcode://$path?w=$width&h=$height"
    }

    private fun buildDirectKey(data: Uri): String {
        val path = data.path ?: return data.toString()

        // Strip token and volatile params, keep only the path
        return "plex-direct://$path"
    }
}
