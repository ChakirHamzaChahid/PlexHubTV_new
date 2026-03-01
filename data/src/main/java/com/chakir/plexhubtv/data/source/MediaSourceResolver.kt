package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a serverId to the appropriate [MediaSourceHandler].
 * Uses Hilt's `@IntoSet` multibinding to discover all registered handlers.
 */
@Singleton
class MediaSourceResolver @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards MediaSourceHandler>,
) {
    fun resolve(serverId: String): MediaSourceHandler =
        handlers.find { it.matches(serverId) }
            ?: throw IllegalArgumentException("No MediaSourceHandler found for serverId: $serverId")
}
