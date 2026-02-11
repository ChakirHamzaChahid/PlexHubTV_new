package com.chakir.plexhubtv.di.image

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import com.chakir.plexhubtv.di.network.PlexImageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager responsible for prefetching images to improve scroll performance.
 * Use this to load images for items that are about to come into view.
 */
@Singleton
class ImagePrefetchManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) {
        /**
         * Prefetches a list of image URLs.
         *
         * @param urls List of direct image URLs to prefetch.
         */
        fun prefetchImages(urls: List<String>) {
            urls.forEach { url ->
                if (url.isNotEmpty()) {
                    val request =
                        ImageRequest.Builder(context)
                            .data(url)
                            // We don't need to decode the image into a Bitmap, just load it into disk/memory cache
                            // However, Coil's preload() will decode it into memory cache if we don't specify otherwise.
                            // For smooth scrolling, having it in memory is best.
                            .build()
                    imageLoader.enqueue(request)
                }
            }
        }

        /**
         * Prefetches images for Plex items, automatically optimizing URLs.
         *
         * @param items List of objects containing image paths (e.g., thumbUrl, artUrl).
         * @param width Target width for optimization.
         * @param height Target height for optimization.
         * @param baseUrl Plex server base URL.
         * @param token Plex authentication token.
         */
        fun prefetchPlexItems(
            items: List<PlexItemImage>,
            width: Int,
            height: Int,
            baseUrl: String,
            token: String,
        ) {
            val urls =
                items.mapNotNull { item ->
                    // Prioritize thumb, fallback to art if thumb is missing (depending on UI logic, usually thumb for lists)
                    val path = item.thumbPath
                    if (!path.isNullOrEmpty()) {
                        PlexImageHelper.getOptimizedImageUrl(
                            baseUrl = baseUrl,
                            token = token,
                            path = path,
                            width = width,
                            height = height,
                        )
                    } else {
                        null
                    }
                }
            prefetchImages(urls)
        }
    }

/**
 * Interface to extract image paths from domain models genericly.
 */
data class PlexItemImage(
    val thumbPath: String?,
)
