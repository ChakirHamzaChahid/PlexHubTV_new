package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cas d'utilisation pour enrichir un MediaItem avec des sources externes (autres serveurs).
 *
 * Stratégie Room-first :
 * 1. Si unificationId non-vide → query Room via mediaDetailRepository.findRemoteSources (~5ms)
 * 2. Si Room vide ET (guid/imdb/tmdb sont null) → fallback réseau (ancien comportement)
 * 3. Cache in-memory pour cohérence intra-session
 */
@Singleton
class EnrichMediaItemUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val searchRepository: SearchRepository,
        private val mediaRepository: com.chakir.plexhubtv.domain.repository.MediaRepository,
        private val mediaDetailRepository: MediaDetailRepository,
    ) {
        // In-memory cache: keyed by "ratingKey:serverId" → enriched MediaItem
        private val cache = ConcurrentHashMap<String, MediaItem>()

        suspend operator fun invoke(item: MediaItem): MediaItem {
            val cacheKey = "${item.ratingKey}:${item.serverId}"
            cache[cacheKey]?.let { cached ->
                Timber.d("Enrich: Cache hit for ${item.title} ($cacheKey)")
                return cached
            }
            return enrich(item).also { cache[cacheKey] = it }
        }

        private suspend fun enrich(item: MediaItem): MediaItem {
            val allServers = authRepository.getServers().getOrNull() ?: return item
            val serverMap = allServers.associate { it.clientIdentifier to it.name }
            val currentSource = buildMediaSource(item, serverMap[item.serverId] ?: "Unknown")

            // Single server shortcut
            if (allServers.size <= 1) {
                return item.copy(remoteSources = listOf(currentSource))
            }

            // === ROOM-FIRST: query local DB (~5ms) ===
            // For episodes: matches by showTitle + seasonTitle + episodeIndex (unificationId unreliable)
            // For movies/shows: matches by unificationId
            val canQueryRoom = item.type == MediaType.Episode ||
                !item.unificationId.isNullOrBlank()
            if (canQueryRoom) {
                val localMatches = mediaDetailRepository.findRemoteSources(item)
                if (localMatches.isNotEmpty()) {
                    Timber.d("Enrich: Room-first found ${localMatches.size} remote source(s) for '${item.title}'")
                    val remoteSources = localMatches.map { match ->
                        buildMediaSource(match, serverMap[match.serverId] ?: match.serverId)
                    }
                    return item.copy(remoteSources = listOf(currentSource) + remoteSources)
                }
            }

            // === NETWORK FALLBACK: for media not yet synced or without unificationId ===
            Timber.d("Enrich: Network fallback for '${item.title}' (unificationId=${item.unificationId})")
            return enrichViaNetwork(item, currentSource, allServers, serverMap)
        }

        private fun buildMediaSource(
            item: MediaItem,
            serverName: String,
        ): MediaSource {
            val bestMedia = item.mediaParts.firstOrNull()
            val videoStream = bestMedia?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
            val audioStream = bestMedia?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()

            return MediaSource(
                serverId = item.serverId,
                ratingKey = item.ratingKey,
                serverName = serverName,
                resolution = videoStream?.let { "${it.height}p" },
                container = bestMedia?.container,
                videoCodec = videoStream?.codec,
                audioCodec = audioStream?.codec,
                audioChannels = audioStream?.channels,
                fileSize = bestMedia?.size,
                bitrate = videoStream?.bitrate,
                hasHDR = videoStream?.hasHDR ?: false,
                languages = bestMedia?.streams?.filterIsInstance<AudioStream>()
                    ?.mapNotNull { it.language }?.distinct() ?: emptyList(),
                thumbUrl = item.thumbUrl,
                artUrl = item.artUrl,
            )
        }

        private suspend fun enrichViaNetwork(
            item: MediaItem,
            currentSource: MediaSource,
            allServers: List<com.chakir.plexhubtv.core.model.Server>,
            serverMap: Map<String, String>,
        ): MediaItem =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                coroutineScope {
                    val matchesDeferred =
                        allServers
                            .filter { it.clientIdentifier != item.serverId }
                            .map { server ->
                                async {
                                    try {
                                        val searchRes = searchRepository.searchOnServer(server, item.title)
                                        val results = searchRes.getOrNull() ?: emptyList()

                                        results.find { candidate ->
                                            // 1. GUID Match (Best)
                                            val idMatch =
                                                (item.imdbId != null && item.imdbId == candidate.imdbId) ||
                                                    (item.tmdbId != null && item.tmdbId == candidate.tmdbId)

                                            // 2. Title + Year Match (Fallback)
                                            val titleMatch = candidate.title.equals(item.title, ignoreCase = true)
                                            val yearMatch = (item.year == null || candidate.year == null || item.year == candidate.year)

                                            // 3. Hierarchy Match
                                            val typeMatch = item.type == candidate.type
                                            val episodeMatch =
                                                if (item.type == MediaType.Episode && candidate.type == MediaType.Episode) {
                                                    // parentIndex may be null when episode comes from Room cache
                                                    val seasonMatch = item.parentIndex == null || candidate.parentIndex == null || item.parentIndex == candidate.parentIndex
                                                    seasonMatch && (item.episodeIndex == candidate.episodeIndex)
                                                } else {
                                                    true
                                                }

                                            (idMatch) || (titleMatch && yearMatch && typeMatch && episodeMatch)
                                        }?.let { match ->
                                            try {
                                                val detailRes = mediaRepository.getMediaDetail(match.ratingKey, server.clientIdentifier)
                                                val fullDetail = detailRes.getOrNull()
                                                val safeMatch = fullDetail ?: match
                                                buildMediaSource(safeMatch, server.name)
                                            } catch (e: Exception) {
                                                Timber.w(e, "Enrich: detail fetch failed for ${match.ratingKey} on ${server.name}")
                                                MediaSource(
                                                    serverId = server.clientIdentifier,
                                                    ratingKey = match.ratingKey,
                                                    serverName = server.name,
                                                    resolution = null,
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "Enrich: search failed on ${server.name} for '${item.title}'")
                                        null
                                    }
                                }
                            }

                    val matches = matchesDeferred.awaitAll().filterNotNull()
                    item.copy(remoteSources = listOf(currentSource) + matches)
                }
            }
    }
