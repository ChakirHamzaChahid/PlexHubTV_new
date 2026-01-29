package com.chakir.plexhubtv.core.util

import android.net.Uri

/**
 * Appends a Plex authentication token to a URL string.
 * Automatically determines whether to use '?' or '&' as the separator.
 */
fun String.withPlexToken(token: String?): String {
    if (token.isNullOrBlank()) return this
    val separator = if (contains("?")) "&" else "?"
    return "$this${separator}X-Plex-Token=$token"
}

/**
 * Appends a base URL and Plex authentication token to this path string.
 */
fun String.toPlexUrl(baseUrl: String, token: String?): String {
    return "$baseUrl$this".withPlexToken(token)
}

/**
 * Builds a full Plex URL with token as Uri
 */
fun String.toPlexUri(baseUrl: String, token: String?): Uri {
    return if (token.isNullOrBlank()) {
        Uri.parse("$baseUrl$this")
    } else {
        Uri.parse(baseUrl).buildUpon()
            .apply {
                // Append path segments
                this@toPlexUri.split("/").filter { it.isNotBlank() }.forEach { 
                    appendPath(it) 
                }
            }
            .appendQueryParameter("X-Plex-Token", token)
            .build()
    }
}
