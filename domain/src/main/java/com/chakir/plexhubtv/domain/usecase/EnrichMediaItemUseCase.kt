package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaSource
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.model.VideoStream
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.JellyfinServerRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import com.chakir.plexhubtv.domain.repository.MediaDetailRepository
import com.chakir.plexhubtv.domain.repository.SearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
        private val mediaDetailRepository: MediaDetailRepository,
        private val backendRepository: BackendRepository,
        private val xtreamAccountRepository: XtreamAccountRepository,
        private val jellyfinServerRepository: JellyfinServerRepository,
        private val performanceTracker: com.chakir.plexhubtv.core.common.PerformanceTracker,
        private val getEnabledServerIdsUseCase: GetEnabledServerIdsUseCase,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        // In-memory LRU cache: keyed by "ratingKey:serverId" → enriched MediaItem
        // Capped at 500 entries to prevent unbounded growth during long binge sessions
        private val cache: MutableMap<String, MediaItem> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, MediaItem>(128, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaItem>): Boolean = size > 500
            }
        )

        // In-flight deduplication: prevents duplicate enrichment when prefetch + play overlap
        private val inFlight = ConcurrentHashMap<String, Deferred<MediaItem>>()

        // Show-level source cache: grandparentTitle → (serverId → showRatingKey)
        // Avoids repeated search API calls when binge-watching episodes of the same show
        // Capped at 200 entries to prevent unbounded growth
        private val showServerCache: MutableMap<String, ConcurrentHashMap<String, String>> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, ConcurrentHashMap<String, String>>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ConcurrentHashMap<String, String>>): Boolean = size > 200
            }
        )

        /**
         * Clears cached episode enrichment results so the next call re-queries Room.
         * Call after new remote episodes are cached to Room (e.g. after prefetch).
         */
        fun invalidateEpisodeCache() {
            val removed = cache.entries.removeAll { it.value.type == MediaType.Episode }
            if (removed) Timber.d("Enrich: Invalidated episode cache entries")
        }

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

            // In-flight deduplication: if another coroutine is already enriching this item, await its result
            val deferred = CompletableDeferred<MediaItem>()
            val existing = inFlight.putIfAbsent(cacheKey, deferred)
            if (existing != null) {
                Timber.d("Enrich: In-flight dedup for ${item.title} ($cacheKey) — awaiting existing")
                performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("deduplicated" to true))
                return existing.await()
            }

            return try {
                enrich(item, opId).also {
                    cache[cacheKey] = it
                    deferred.complete(it)
                    performanceTracker.endOperation(opId, success = true, additionalMeta = mapOf("cacheHit" to false, "sources" to it.remoteSources.size))
                }
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
                performanceTracker.endOperation(opId, success = false, errorMessage = e.message)
                throw e
            } finally {
                inFlight.remove(cacheKey)
            }
        }

        private suspend fun enrich(item: MediaItem, opId: String): MediaItem {
            // Only search servers that are synced in Room AND not excluded in settings
            val enabledServerIds = getEnabledServerIdsUseCase().toSet()

            // Build server name map from ALL sources (Plex + Backend + Xtream)
            val serverMap = buildServerNameMap(enabledServerIds)
            val currentSource = buildMediaSource(item, serverMap[item.serverId] ?: "Unknown")
                .copy(displayTitle = item.title)

            // Plex servers for network fallback (search API only works with Plex)
            val allPlexServers = authRepository.getServers().getOrNull() ?: emptyList()
            val enabledPlexServers = allPlexServers.filter { it.clientIdentifier in enabledServerIds }

            performanceTracker.addCheckpoint(opId, "Server List Loaded", mapOf("total" to enabledServerIds.size, "plex" to enabledPlexServers.size))

            // Single server shortcut — check ALL enabled servers, not just Plex
            if (enabledServerIds.size <= 1) {
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
                    val roomSources = supervisorScope {
                        localMatches.map { match ->
                            async {
                                val needsFullDetails = match.mediaParts.isEmpty() ||
                                                      match.mediaParts.first().streams.isEmpty()

                                val enrichedMatch = if (needsFullDetails) {
                                    // Room cache doesn't have mediaParts/streams → fetch full details
                                    try {
                                        val detailResult = mediaDetailRepository.getMediaDetail(match.ratingKey, match.serverId)
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
                                    .copy(displayTitle = enrichedMatch.title)
                            }
                        }.awaitAll()
                    }

                    // Room may not have episodes for ALL servers (episodes aren't synced by LibrarySyncWorker).
                    // Check which Plex servers are still missing and use network fallback (tree traversal) for those.
                    val coveredServerIds = roomSources.map { it.serverId }.toSet() + item.serverId
                    val missingPlexServers = enabledPlexServers.filter { it.clientIdentifier !in coveredServerIds }

                    val networkSources = if (missingPlexServers.isNotEmpty() && item.type == MediaType.Episode) {
                        performanceTracker.addCheckpoint(opId, "Room Partial - Network Fallback",
                            mapOf("roomMatches" to roomSources.size, "missingServers" to missingPlexServers.size))
                        supervisorScope {
                            val partialResults = CopyOnWriteArrayList<MediaSource>()
                            val jobs = missingPlexServers.map { server ->
                                async {
                                    try {
                                        val result = enrichEpisodeViaTreeTraversal(item, server, opId)
                                        if (result != null) partialResults.add(result)
                                        result
                                    } catch (e: Exception) {
                                        Timber.w(e, "Enrich: tree traversal failed on ${server.name} for '${item.title}'")
                                        null
                                    }
                                }
                            }
                            val completedInTime = withTimeoutOrNull(4000) { jobs.awaitAll() }
                            if (completedInTime == null) {
                                Timber.w("Enrich: Partial network fallback timed out after 4s, returning ${partialResults.size} results")
                                jobs.forEach { if (it.isActive) it.cancel() }
                            }
                            partialResults.toList()
                        }
                    } else emptyList()

                    val allRemoteSources = roomSources + networkSources

                    // Extract alternative thumb URLs for image fallback
                    val alternativeThumbUrls = allRemoteSources
                        .mapNotNull { it.thumbUrl }
                        .filter { it.isNotBlank() }

                    return item.copy(
                        remoteSources = listOf(currentSource) + allRemoteSources,
                        alternativeThumbUrls = alternativeThumbUrls
                    )
                } else {
                    performanceTracker.addCheckpoint(
                        opId,
                        "Room Query (Miss)",
                        mapOf("duration" to roomQueryDuration)
                    )
                    // Room searched by unificationId (IMDB/TMDB/title+year) across ALL synced servers
                    // and found nothing. For non-Plex items (backend/xtream), network fallback is
                    // pointless — IPTV content is rarely duplicated on Plex servers, and if it were,
                    // Room would have found it via the shared TMDB/IMDB ID.
                    val isNonPlexItem = SourcePrefix.isNonPlex(item.serverId)
                    if (isNonPlexItem) {
                        performanceTracker.addCheckpoint(opId, "Network Fallback Skipped (non-Plex Room miss)")
                        Timber.d("Enrich: Skipping network fallback for non-Plex item '${item.title}' (Room miss with unificationId=${item.unificationId})")
                        return item.copy(remoteSources = listOf(currentSource))
                    }
                }
            } else {
                performanceTracker.addCheckpoint(opId, "Room Query Skipped (no unificationId)", mapOf("type" to item.type.name))
            }

            // === NETWORK FALLBACK: for media not yet synced or without unificationId ===
            // Only Plex servers support search API for network fallback
            performanceTracker.addCheckpoint(opId, "Network Fallback Started")
            Timber.d("Enrich: Network fallback for '${item.title}' (unificationId=${item.unificationId})")
            return enrichViaNetwork(item, currentSource, enabledPlexServers, serverMap, opId)
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
                viewOffset = item.viewOffset,
            )
        }

        /**
         * Build a server name map from ALL sources (Plex + Backend + Xtream).
         */
        private suspend fun buildServerNameMap(enabledServerIds: Set<String>): Map<String, String> {
            val map = mutableMapOf<String, String>()

            // Plex servers
            authRepository.getServers().getOrNull()?.forEach { server ->
                if (server.clientIdentifier in enabledServerIds) {
                    map[server.clientIdentifier] = server.name
                }
            }

            // Backend servers
            try {
                backendRepository.observeServers().first().forEach { backend ->
                    val serverId = "backend_${backend.id}"
                    if (serverId in enabledServerIds) {
                        map[serverId] = backend.label
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Enrich: Failed to load backend server names")
            }

            // Xtream accounts
            try {
                xtreamAccountRepository.observeAccounts().first().forEach { account ->
                    val serverId = "xtream_${account.id}"
                    if (serverId in enabledServerIds) {
                        map[serverId] = account.label
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Enrich: Failed to load xtream account names")
            }

            // Jellyfin servers
            try {
                jellyfinServerRepository.observeServers().first().forEach { jfServer ->
                    val serverId = SourcePrefix.JELLYFIN + jfServer.id
                    if (serverId in enabledServerIds) {
                        map[serverId] = jfServer.name
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Enrich: Failed to load Jellyfin server names")
            }

            return map
        }

        private suspend fun enrichViaNetwork(
            item: MediaItem,
            currentSource: MediaSource,
            allServers: List<com.chakir.plexhubtv.core.model.Server>,
            serverMap: Map<String, String>,
            opId: String,
        ): MediaItem =
            withContext(ioDispatcher) {
                supervisorScope {
                    val networkStart = System.currentTimeMillis()
                    val otherServers = allServers.filter { it.clientIdentifier != item.serverId }

                    // === Episode pre-filtering: only search servers that have the parent show ===
                    val preFilteredShowMap: Map<String, String> = if (item.type == MediaType.Episode) {
                        val parentUnificationId = item.grandparentRatingKey?.let { gpRk ->
                            mediaDetailRepository.getParentShowIds(gpRk, item.serverId)?.unificationId
                        }
                        if (!parentUnificationId.isNullOrBlank()) {
                            val serversWithShow = mediaDetailRepository.findServersWithShow(parentUnificationId, item.serverId)
                            if (serversWithShow.isNotEmpty()) {
                                performanceTracker.addCheckpoint(opId, "Pre-filtered servers",
                                    mapOf("total" to otherServers.size, "withShow" to serversWithShow.size))
                                Timber.d("Enrich: Pre-filtered ${otherServers.size} servers to ${serversWithShow.size} (show found in Room)")
                            }
                            serversWithShow
                        } else emptyMap()
                    } else emptyMap()

                    val collectedResults = CopyOnWriteArrayList<MediaSource>()

                    val jobs = otherServers.map { server ->
                        async {
                            val serverSearchStart = System.currentTimeMillis()
                            try {
                                val result = if (item.type == MediaType.Episode) {
                                    val knownShowRk = preFilteredShowMap[server.clientIdentifier]
                                    if (knownShowRk != null) {
                                        // Show found in Room on this server → direct traversal (skip search API)
                                        val seasonIndex = item.seasonIndex ?: item.parentIndex ?: return@async null
                                        val episodeIndex = item.episodeIndex ?: return@async null
                                        traverseShowToEpisode(knownShowRk, server.clientIdentifier, seasonIndex, episodeIndex, server.name, opId)
                                    } else if (preFilteredShowMap.isNotEmpty()) {
                                        // Pre-filtering active but show NOT on this server → skip entirely
                                        null
                                    } else {
                                        // No pre-filtering info → full tree traversal (search API fallback)
                                        enrichEpisodeViaTreeTraversal(item, server, opId)
                                    }
                                } else {
                                    enrichMovieOrShowViaSearch(item, server, serverSearchStart, opId)
                                }
                                if (result != null) collectedResults.add(result)
                                result
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

                    // Wait up to 4s — slow/dead servers won't block the entire enrichment
                    val completedInTime = withTimeoutOrNull(4000) { jobs.awaitAll() }
                    if (completedInTime == null) {
                        Timber.w("Enrich: Network fallback timed out after 4s, returning ${collectedResults.size} partial results")
                        jobs.forEach { if (it.isActive) it.cancel() }
                    }

                    val matches = collectedResults.toList()
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

        /**
         * Tree traversal strategy for episodes: find show → season by index → episode by index.
         *
         * Three strategies to find the show on the remote server (in order):
         * 1) Room-show lookup: if parent show is already in Room on this server (via unificationId),
         *    skip search entirely and navigate by ratingKey — fastest, title-independent
         * 2) Search API + multi-strategy matching: IMDB/TMDB ID match, exact title, normalized title
         * 3) If nothing works, give up
         *
         * Once the show is found, navigation is purely by season/episode index numbers.
         */
        private suspend fun enrichEpisodeViaTreeTraversal(
            episode: MediaItem,
            server: com.chakir.plexhubtv.core.model.Server,
            opId: String,
        ): MediaSource? {
            val showTitle = episode.grandparentTitle ?: return null
            val seasonIndex = episode.seasonIndex ?: episode.parentIndex ?: return null
            val episodeIndex = episode.episodeIndex ?: return null

            // === Strategy 0: Show-level source cache (instant for episode 2+) ===
            val cachedShowRk = showServerCache[showTitle]?.get(server.clientIdentifier)
            if (cachedShowRk != null) {
                val result = traverseShowToEpisode(cachedShowRk, server.clientIdentifier, seasonIndex, episodeIndex, server.name, opId)
                if (result != null) {
                    performanceTracker.addCheckpoint(opId, "Show Cache Hit: ${server.name}", mapOf("showRk" to cachedShowRk))
                    return result
                }
                // Cache stale (show removed?) — remove entry and fall through
                showServerCache[showTitle]?.remove(server.clientIdentifier)
            }

            // Look up parent show info from Room once (used by both strategies)
            val parentInfo = episode.grandparentRatingKey?.let { gpRk ->
                mediaDetailRepository.getParentShowIds(gpRk, episode.serverId)
            }

            // === Strategy 1: Room-show lookup (no search needed, title-independent) ===
            // If the parent show is synced in Room on this server (via LibrarySyncWorker),
            // we can jump directly to its ratingKey and traverse season→episode by index.
            val parentUnificationId = parentInfo?.unificationId
            if (!parentUnificationId.isNullOrBlank()) {
                val remoteShow = mediaDetailRepository.findRemoteShowByUnificationId(
                    parentUnificationId, server.clientIdentifier
                )
                if (remoteShow != null) {
                    val result = traverseShowToEpisode(remoteShow.ratingKey, server.clientIdentifier, seasonIndex, episodeIndex, server.name, opId)
                    if (result != null) {
                        performanceTracker.addCheckpoint(opId, "Room-Show Shortcut: ${server.name}", mapOf("showRk" to remoteShow.ratingKey))
                        // Cache for subsequent episodes
                        showServerCache.getOrPut(showTitle) { ConcurrentHashMap() }[server.clientIdentifier] = remoteShow.ratingKey
                        return result
                    }
                }
            }

            // === Strategy 2: Search API + multi-strategy matching ===
            val searchRes = withTimeoutOrNull(3000) {
                searchRepository.searchOnServer(server, showTitle)
            }
            val candidates = searchRes?.getOrNull() ?: emptyList()
            val shows = candidates.filter { it.type == MediaType.Show }

            // Match by: 1) IMDB/TMDB ID, 2) exact title, 3) normalized title
            val showMatch = if (parentInfo != null) {
                shows.find { candidate ->
                    (parentInfo.imdbId != null && parentInfo.imdbId == candidate.imdbId) ||
                        (parentInfo.tmdbId != null && parentInfo.tmdbId == candidate.tmdbId)
                }
            } else null
                ?: shows.find { it.title.equals(showTitle, ignoreCase = true) }
                ?: shows.find { normalizeTitle(it.title).equals(normalizeTitle(showTitle), ignoreCase = true) }

            if (showMatch == null) {
                val showTitles = shows.take(3).joinToString { "'${it.title}'" }
                performanceTracker.addCheckpoint(opId, "Tree Traversal: Show not found on ${server.name}",
                    mapOf("searched" to showTitle, "candidates" to showTitles))
                Timber.d("Enrich: Tree traversal miss on ${server.name} — searched '$showTitle', found: $showTitles")
                return null
            }

            // Cache for subsequent episodes
            showServerCache.getOrPut(showTitle) { ConcurrentHashMap() }[server.clientIdentifier] = showMatch.ratingKey

            return traverseShowToEpisode(showMatch.ratingKey, server.clientIdentifier, seasonIndex, episodeIndex, server.name, opId)
        }

        /**
         * Given a show's ratingKey on a specific server, traverse season→episode by index numbers.
         * Returns a MediaSource for the matched episode, or null if not found.
         */
        private suspend fun traverseShowToEpisode(
            showRatingKey: String,
            serverId: String,
            seasonIndex: Int,
            episodeIndex: Int,
            serverName: String,
            opId: String,
        ): MediaSource? {
            // Get seasons → find matching season by index
            val seasons = mediaDetailRepository.getShowSeasons(showRatingKey, serverId)
                .getOrNull() ?: return null
            val seasonMatch = seasons.find { it.seasonIndex == seasonIndex } ?: return null

            // Get episodes → find matching episode by index
            val episodes = mediaDetailRepository.getSeasonEpisodes(seasonMatch.ratingKey, serverId)
                .getOrNull() ?: return null
            val episodeMatch = episodes.find { it.episodeIndex == episodeIndex } ?: return null

            // Fetch full episode details to get mediaParts/streams
            val fullDetail = try {
                val detailResult = mediaDetailRepository.getMediaDetail(episodeMatch.ratingKey, serverId)
                detailResult.getOrNull() ?: episodeMatch
            } catch (e: Exception) {
                Timber.w(e, "Enrich: Failed to fetch episode details for ${episodeMatch.ratingKey}")
                episodeMatch
            }

            performanceTracker.addCheckpoint(opId, "Tree Traversal Match: $serverName", mapOf("episode" to fullDetail.title))
            return buildMediaSource(fullDetail, serverName)
        }

        /**
         * Standard search strategy for movies/shows: search by title, match by GUID or title+year.
         */
        private suspend fun enrichMovieOrShowViaSearch(
            item: MediaItem,
            server: com.chakir.plexhubtv.core.model.Server,
            serverSearchStart: Long,
            opId: String,
        ): MediaSource? {
            val searchRes = withTimeoutOrNull(3000) {
                searchRepository.searchOnServer(server, item.title)
            }
            val results = searchRes?.getOrNull() ?: emptyList()
            val serverSearchDuration = System.currentTimeMillis() - serverSearchStart
            performanceTracker.addCheckpoint(
                opId,
                if (searchRes == null) "Network Search TIMEOUT: ${server.name}" else "Network Search: ${server.name}",
                mapOf("duration" to serverSearchDuration, "results" to results.size)
            )

            // Match strategy depends on whether the item has external IDs:
            // - With imdbId/tmdbId: ONLY match by ID (prevents false unification of
            //   different movies with same title+year, e.g. "300" parody vs real "300")
            // - Without any ID: fall back to title+year+type matching
            val hasExternalId = !item.imdbId.isNullOrBlank() || !item.tmdbId.isNullOrBlank()

            return results.find { candidate ->
                val idMatch =
                    (item.imdbId != null && item.imdbId == candidate.imdbId) ||
                        (item.tmdbId != null && item.tmdbId == candidate.tmdbId)
                if (hasExternalId) {
                    idMatch
                } else {
                    val titleMatch = candidate.title.equals(item.title, ignoreCase = true)
                    val yearMatch = (item.year == null || candidate.year == null || item.year == candidate.year)
                    val typeMatch = item.type == candidate.type
                    titleMatch && yearMatch && typeMatch
                }
            }?.let { match ->
                try {
                    val detailRes = mediaDetailRepository.getMediaDetail(match.ratingKey, server.clientIdentifier)
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
        }

        /**
         * Normalize a title for fuzzy matching: lowercase, strip common articles,
         * collapse whitespace, remove punctuation that often differs across servers.
         * e.g. "The Office (US)" → "office us", "L'Attaque des Titans" → "attaque des titans"
         */
        private fun normalizeTitle(title: String): String {
            val articles = listOf("the ", "a ", "an ", "le ", "la ", "les ", "l'", "un ", "une ", "des ")
            var normalized = title.lowercase().trim()
            // Remove leading articles
            for (article in articles) {
                if (normalized.startsWith(article)) {
                    normalized = normalized.removePrefix(article).trim()
                    break
                }
            }
            // Remove punctuation and collapse whitespace
            return normalized
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
