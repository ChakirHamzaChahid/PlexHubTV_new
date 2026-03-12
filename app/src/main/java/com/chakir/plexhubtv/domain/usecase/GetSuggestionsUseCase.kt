package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.ServerClientResolver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates intelligent suggestions by combining 3 strategies:
 * - Genre-match (40%): unwatched media matching recently watched genres
 * - Random (30%): random unwatched media for discovery
 * - Fresh (30%): recently added unwatched media
 *
 * Deduplicates by unificationId to avoid showing the same title from multiple servers.
 */
@Singleton
class GetSuggestionsUseCase @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaMapper: MediaMapper,
    private val mediaUrlResolver: MediaUrlResolver,
    private val serverClientResolver: ServerClientResolver,
) {
    suspend operator fun invoke(limit: Int = 20): List<MediaItem> {
        return try {
            val genreCount = (limit * 0.4).toInt().coerceAtLeast(1)
            val randomCount = (limit * 0.3).toInt().coerceAtLeast(1)
            val freshCount = limit - genreCount - randomCount

            // 1. Extract genres from watch history
            val watchedGenreStrings = mediaDao.getRecentWatchedGenres()
            val genres: List<String> = watchedGenreStrings
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .groupingBy { g: String -> g }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

            // 2. Genre-match suggestions
            val genreMatches = if (genres.isNotEmpty()) {
                genres.flatMap { genre ->
                    mediaDao.getUnwatchedByGenre(genre, genreCount)
                }.distinctBy { it.unificationId.ifBlank { "${it.ratingKey}_${it.serverId}" } }
                    .take(genreCount)
            } else {
                emptyList()
            }

            // 3. Random unwatched
            val seenIds = genreMatches
                .map { it.unificationId.ifBlank { "${it.ratingKey}_${it.serverId}" } }
                .toSet()
            val randomMatches = mediaDao.getRandomUnwatched(randomCount + 10)
                .filter { entity ->
                    val id = entity.unificationId.ifBlank { "${entity.ratingKey}_${entity.serverId}" }
                    id !in seenIds
                }
                .take(randomCount)

            // 4. Fresh additions (last 30 days)
            val allSeenIds = seenIds + randomMatches
                .map { it.unificationId.ifBlank { "${it.ratingKey}_${it.serverId}" } }
            val thirtyDaysAgo = System.currentTimeMillis() / 1000 - (30 * 24 * 60 * 60)
            val freshMatches = mediaDao.getFreshUnwatched(thirtyDaysAgo, freshCount + 10)
                .filter { entity ->
                    val id = entity.unificationId.ifBlank { "${entity.ratingKey}_${entity.serverId}" }
                    id !in allSeenIds
                }
                .take(freshCount)

            // Pre-resolve server connections (group by serverId to avoid redundant lookups)
            val combined = (genreMatches + randomMatches + freshMatches)
                .distinctBy { it.unificationId.ifBlank { "${it.ratingKey}_${it.serverId}" } }
                .take(limit)

            val serverIds = combined.map { it.serverId }.distinct()
            val serverUrls = serverIds.associateWith { serverId ->
                val client = serverClientResolver.getClient(serverId)
                if (client != null) client.baseUrl to client.server.accessToken else null
            }

            // Combine, map to domain, and resolve URLs
            val result = combined.map { entity ->
                val domain = mediaMapper.mapEntityToDomain(entity)
                val connection = serverUrls[entity.serverId]
                if (connection != null) {
                    val (baseUrl, token) = connection
                    mediaUrlResolver.resolveUrls(domain, baseUrl, token)
                } else {
                    domain
                }
            }

            Timber.d("[Suggestions] Generated ${result.size} suggestions (genre=${genreMatches.size}, random=${randomMatches.size}, fresh=${freshMatches.size})")
            result
        } catch (e: Exception) {
            Timber.e(e, "[Suggestions] Failed to generate suggestions")
            emptyList()
        }
    }
}
