package com.chakir.plexhubtv.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import timber.log.Timber

/**
 * AsyncImage with automatic fallback to alternative URLs if primary fails.
 *
 * Tries URLs in order:
 * 1. Primary URL
 * 2. alternativeUrls[0] (if timeout/error)
 * 3. alternativeUrls[1] (if timeout/error)
 * ...
 *
 * Stops at first successful load or shows placeholder if all fail.
 */
@Composable
fun FallbackAsyncImage(
    primaryUrl: String?,
    alternativeUrls: List<String> = emptyList(),
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    imageWidth: Int = 420,
    imageHeight: Int = 630,
) {
    // Track which URL index we're currently trying (0 = primary, 1+ = alternatives)
    var currentUrlIndex by remember(primaryUrl, alternativeUrls) { mutableIntStateOf(0) }

    // Build list of all URLs to try: [primary, alt1, alt2, ...]
    val allUrls = remember(primaryUrl, alternativeUrls) {
        listOfNotNull(primaryUrl) + alternativeUrls.filter { it.isNotBlank() }
    }

    // Current URL being loaded
    val currentUrl = allUrls.getOrNull(currentUrlIndex)

    if (currentUrl == null) {
        // No URLs available → show placeholder (handled by Coil's placeholder/error)
        AsyncImage(
            model = null,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(currentUrl)
            .crossfade(false)
            .size(imageWidth, imageHeight)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .listener(
                onError = { _, result ->
                    val nextIndex = currentUrlIndex + 1
                    if (nextIndex < allUrls.size) {
                        Timber.w("Image load failed for URL $currentUrl (${result.throwable.message}), trying fallback ${nextIndex + 1}/${allUrls.size}")
                        currentUrlIndex = nextIndex // Try next URL
                    } else {
                        Timber.e("All ${allUrls.size} image URLs failed for $primaryUrl")
                    }
                },
                onSuccess = { _, _ ->
                    if (currentUrlIndex > 0) {
                        Timber.i("Image loaded successfully from fallback URL #${currentUrlIndex + 1}: $currentUrl")
                    }
                }
            )
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
