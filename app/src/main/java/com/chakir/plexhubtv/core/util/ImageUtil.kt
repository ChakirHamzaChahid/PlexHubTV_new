package com.chakir.plexhubtv.core.util

import android.net.Uri

/**
 * Transforms a direct Plex image URL into a transcoded/optimized version.
 *
 * @param originalUrl The full URL to the original image (e.g., http://1.2.3.4:32400/library/metadata/123/thumb?X-Plex-Token=...)
 * @param width The desired width (e.g. 300)
 * @param height The desired height (e.g. 450)
 * @return A URL pointing to the /photo/:/transcode endpoint
 */
fun getOptimizedImageUrl(originalUrl: String?, width: Int = 300, height: Int = 450): String? {
    if (originalUrl.isNullOrEmpty()) return null
    if (!originalUrl.contains("/library/metadata/")) return originalUrl // Return original if not a library item (e.g. user avatar) or already transcoded

    // Parse base URL and params
    val uri = Uri.parse(originalUrl)
    val port = uri.port
    val portSuffix = if (port != -1) ":$port" else ""
    val baseUrl = "${uri.scheme}://${uri.host}$portSuffix"
    val path = uri.path ?: return originalUrl
    val token = uri.getQueryParameter("X-Plex-Token")
    
    // If no token is provided in the URL, we cannot securely access the transcode endpoint.
    // Fallback to original URL (app might supply header).
    if (token.isNullOrEmpty()) return originalUrl

    // Plex Transcode Endpoint
    // Path: /photo/:/transcode
    // Params:
    // width=xxx
    // height=xxx
    // minSize=1
    // url=encoded_original_path
    // X-Plex-Token=xxx
    
    val encodedPath = Uri.encode(path) // We only need to encode the path part, not query params yet for the 'url' param? 
                                     // Actually Plex expects the 'url' param to be the path relative to server root, or absolute?
                                     // Usually relative path like `/library/metadata/123/thumb/123456789` works.
    
    // Construct the new URL
    return "$baseUrl/photo/:/transcode?width=$width&height=$height&minSize=1&url=$encodedPath&X-Plex-Token=$token"
}
