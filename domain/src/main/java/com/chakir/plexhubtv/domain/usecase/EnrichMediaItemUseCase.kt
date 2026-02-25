package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
        private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
    ) {
        // In-memory cache: keyed by "ratingKey:serverId" → enriched MediaItem
        private val cache = ConcurrentHashMap<String, MediaItem>()

        suspend operator fun invoke(item: MediaItem): MediaItem {
            val opId = "enrich_${item.ratingKey}"
            performanceTracker.startOperation(
                opId,
                com.chakir.plexhubtv.core.common.PerfCategory.ENRICHMENT,
                "Enrich Media for Multi-Server Sources",
                mapOf("title" to item.title, "type" to item.type.name, "ratingKey" to item.ratingKey)
            )

            val cacheKey = "${item.ratingKey}:${item.serverId}"
            cache[cacheKey]?.let { cached ->
                performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("cacheHit" to true, "sources" to cached.remoteSources.size))
                Timber.d("Enrich: Cache hit for ${item.title} ($cacheKey)")
                return cached
            }

            return try {
                enrich(item, opId).also {
                    cache[cacheKey] = it
                    performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("cacheHit" to false, "sources" to it.remoteSources.size))
                }
            } catch (e: Exception) {
                performanceTracker.endOperation(opId, success = false, errorMessage = e.message)
                throw e
            }
        }

        private suspend fun enrich(item: MediaItem, opId: String): MediaItem {
            val allServers = authRepository.getServers().getOrNull() ?: return item

            // Filter out excluded/deselected servers from settings
            val excludedIds = settingsRepository.excludedServerIds.first()
            val enabledServers = allServers.filter { it.clientIdentifier !in excludedIds }

            val serverMap = enabledServers.associate { it.clientIdentifier to it.name }
            val currentSource = buildMediaSource(item, serverMap[item.serverId] ?: "Unknown")

            performanceTracker.addCheckpoint(opId, "Server List Loaded", mapOf("total" to allServers.size, "enabled" to enabledServers.size, "excluded" to excludedIds.size))

            // Single server shortcut
            if (enabledServers.size <= 1) {
                performanceTracker.addCheckpoint(opId, "Single Server - No Enrichment Needed")
                return item.copy(remoteSources = listOf(currentSource))
            }

            // === ROOM-FIRST: query local DB (~5ms) ===
            // For episodes: matches by showTitle + seasonTitle + episodeIndex (unificationId unreliable)
            // For movies/shows: matches by unificationId
            val canQueryRoom = item.type == MediaType.Episode ||
                !item.unificationId.isNullOrBlank()
            if (canQueryRoom) {
                val roomQueryStart = System.currentTimeMillis()
                val localMatches = mediaDetailRepository.findRemoteSources(item)
                val roomQueryDuration = System.currentTimeMillis() - roomQueryStart

                if (localMatches.isNotEmpty()) {
                    performanceTracker.addCheckpoint(
                        opId,
                        "Room Query (Hit)",
                        mapOf("duration" to roomQueryDuration, "matches" to localMatches.size)
                    )
                    Timber.d("Enrich: Room-first found ${localMatches.size} remote source(s) for '${item.title}'")

                    // Fetch full details for matches that lack mediaParts or streams (needed for resolution, codecs, languages)
                    val remoteSources = coroutineScope {
                        localMatches.map { match ->
                            async {
                                val needsFullDetails = match.mediaParts.isEmpty() ||
                                                      match.mediaParts.first().streams.isEmpty()

                                val enrichedMatch = if (needsFullDetails) {
                                    // Room cache doesn't have mediaParts/streams → fetch full details
                                    try {
                                        val detailResult = mediaRepository.getMediaDetail(match.ratingKey, match.serverId)
                                        val fullDetail = detailResult.getOrNull() ?: match

                                        // Persist mediaParts to Room for future sessions (progressive cache)
                                        if (fullDetail.mediaParts.isNotEmpty() && fullDetail.mediaParts.first().streams.isNotEmpty()) {
                                            mediaDetailRepository.updateMediaParts(fullDetail)
                                        }

                                        fullDetail
                                    } catch (e: Exception) {
                                        Timber.w(e, "Enrich: Failed to fetch details for ${match.ratingKey} on ${match.serverId}")
                                        match
                                    }
                                } else {
                                    match
                                }
                                buildMediaSource(enrichedMatch, serverMap[match.serverId] ?: match.serverId)
                            }
                        }.awaitAll()
                    }

                    // Extract alternative thumb URLs for image fallback
                    val alternativeThumbUrls = remoteSources
                        .mapNotNull { it.thumbUrl }
                        .filter { it.isNotBlank() }

                    return item.copy(
                        remoteSources = listOf(currentSource) + remoteSources,
                        alternativeThumbUrls = alternativeThumbUrls
                    )
                } else {
                    performanceTracker.addCheckpoint(
                        opId,
                        "Room Query (Miss)",
                        mapOf("duration" to roomQueryDuration)
                    )
                }
            } else {
                performanceTracker.addCheckpoint(opId, "Room Query Skipped (no unificationId)", mapOf("type" to item.type.name))
            }

            // === NETWORK FALLBACK: for media not yet synced or without unificationId ===
            performanceTracker.addCheckpoint(opId, "Network Fallback Started")
            Timber.d("Enrich: Network fallback for '${item.title}' (unificationId=${item.unificationId})")
            return enrichViaNetwork(item, currentSource, enabledServers, serverMap, opId)
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
            opId: String,
        ): MediaItem =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                coroutineScope {
                    val networkStart = System.currentTimeMillis()
                    val matchesDeferred =
                        allServers
                            .filter { it.clientIdentifier != item.serverId }
                            .map { server ->
                                async {
                                    val serverSearchStart = System.currentTimeMillis()
                                    try {
                                        // ⚡ TIMEOUT: Max 3s per server to prevent slow/offline servers from blocking everything
                                        val searchRes = kotlinx.coroutines.withTimeoutOrNull(3000) {
                                            searchRepository.searchOnServer(server, item.title)
                                        }
                                        val results = searchRes?.getOrNull() ?: emptyList()
                                        val serverSearchDuration = System.currentTimeMillis() - serverSearchStart
                                        performanceTracker.addCheckpoint(
                                            opId,
                                            if (searchRes == null) "Network Search TIMEOUT: ${server.name}" else "Network Search: ${server.name}",
                                            mapOf("duration" to serverSearchDuration, "results" to results.size)
                                        )

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
                                        val serverSearchDuration = System.currentTimeMillis() - serverSearchStart
                                        performanceTracker.addCheckpoint(
                                            opId,
                                            "Network Search FAILED: ${server.name}",
                                            mapOf("duration" to serverSearchDuration, "error" to (e.message ?: "unknown"))
                                        )
                                        Timber.w(e, "Enrich: search failed on ${server.name} for '${item.title}'")
                                        null
                                    }
                                }
                            }

                    val matches = matchesDeferred.awaitAll().filterNotNull()
                    val networkTotalDuration = System.currentTimeMillis() - networkStart
                    performanceTracker.addCheckpoint(
                        opId,
                        "Network Fallback Complete",
                        mapOf("totalDuration" to networkTotalDuration, "totalMatches" to matches.size)
                    )

                    // Extract alternative thumb URLs for image fallback
                    val alternativeThumbUrls = matches
                        .mapNotNull { it.thumbUrl }
                        .filter { it.isNotBlank() }

                    item.copy(
                        remoteSources = listOf(currentSource) + matches,
                        alternativeThumbUrls = alternativeThumbUrls
                    )
                }
            }
    }
