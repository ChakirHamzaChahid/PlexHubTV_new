package com.chakir.plexhubtv.core.util

import android.net.Uri

/**
 * Utilitaires pour la manipulation des images Plex.
 *
 * Fournit des méthodes pour générer les URLs des endpoints de transcodage d'images de Plex.
 * Cela permet de demander au serveur de redimensionner et d'optimiser les images pour
 * réduire la consommation de données et améliorer les performances de rendu.
 */

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
    // If already a transcode URL, return as is to avoid recursion
    if (originalUrl.contains("/photo/:/transcode")) return originalUrl
    
    // Parse base URL and params
    val uri = Uri.parse(originalUrl)
    val port = uri.port
    val portSuffix = if (port != -1) ":$port" else ""
    val baseUrl = "${uri.scheme}://${uri.host}$portSuffix"
    val path = uri.path ?: return originalUrl
    val token = uri.getQueryParameter("X-Plex-Token")
    
    // If no token is provided in the URL, we cannot securely access the transcode endpoint.
    if (token.isNullOrEmpty()) return originalUrl

    // Plex Transcode Endpoint
    val encodedPath = Uri.encode(path)
    
    // Construct the new URL with aggressively optimized parameters
    // minSize=1: Always return an image
    // upscale=1: Allow upscaling if needed (prevents black borders)
    return "$baseUrl/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$encodedPath&X-Plex-Token=$token"
}
