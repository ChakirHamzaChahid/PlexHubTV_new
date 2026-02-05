package com.chakir.plexhubtv.core.image

import android.net.Uri
import coil.key.Keyer
import coil.request.Options

/**
 * Custom Coil Keyer for Plex URLs.
 * 
 * Objectives:
 * 1. Ignore "X-Plex-Token" in cache keys to prevent re-downloads when session/token changes.
 * 2. Ignore timestamps or transient params if present.
 * 3. Preserve transformation params (width, height, upscale) effectively.
 * 
 * Logic:
 * - If URL contains "X-Plex-Token", rebuild the URL without it.
 * - Use that sanitized URL as the cache key.
 */
class PlexImageKeyer : Keyer<Uri> {

    override fun key(data: Uri, options: Options): String? {
        // Only handle http/https URLs that likely come from Plex
        if (data.scheme != "http" && data.scheme != "https") return null

        // Check if it has a token
        if (data.getQueryParameter("X-Plex-Token") == null && !data.toString().contains("X-Plex-Token=")) {
            return null // Standard URL, let Coil handle it default
        }

        // Build a stable key by removing volatile parameters
        val builder = data.buildUpon().clearQuery()
        
        val paramNames = data.queryParameterNames
        val stableParams = paramNames.filter { key ->
            key != "X-Plex-Token" && 
            key != "X-Plex-Client-Identifier" &&
            key != "X-Plex-Product" &&
            key != "X-Plex-Version" &&
            key != "_" // jQuery timestamp often used in webs
        }
        
        // Sort params to ensure deterministic key order (width=300&height=400 vs height=400&width=300)
        stableParams.sorted().forEach { key ->
            val values = data.getQueryParameters(key)
            values.forEach { value ->
                builder.appendQueryParameter(key, value)
            }
        }

        return builder.build().toString()
    }
}
