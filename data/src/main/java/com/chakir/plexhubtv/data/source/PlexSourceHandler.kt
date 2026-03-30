package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.ServerClientResolver
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plex Media Server source handler.
 * Room-first with API fallback, URL resolution via server connection.
 */
@Singleton
class PlexSourceHandler @Inject constructor(
    private val serverClientResolver: ServerClientResolver,
    private val mediaDao: MediaDao,
    private val mapper: MediaMapper,
    private val mediaUrlResolver: MediaUrlResolver,
) : MediaSourceHandler {

    private val similarCache = ConcurrentHashMap<String, Pair<Long, List<MediaItem>>>()
    private val similarCacheTtlMs = 10 * 60 * 1000L

    override fun matches(serverId: String): Boolean = !SourcePrefix.isNonPlex(serverId)

    override suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        val localEntity = mediaDao.getMedia(ratingKey, serverId)

        // Episodes: API-first because Room never stores chapters/markers
        // (episodes are cached from /children which doesn't include them).
        // Without markers, skip intro/credits buttons won't appear.
        val isEpisode = localEntity?.type == "episode"

        if (!isEpisode && localEntity != null) {
            // Non-episode Room cache (movies/shows synced via LibrarySyncWorker)
            val hasStreams = localEntity.mediaParts.isNotEmpty() &&
                localEntity.mediaParts.first().streams.isNotEmpty()
            if (hasStreams) {
                val client = serverClientResolver.getClient(serverId)
                val baseUrl = client?.baseUrl
                val token = client?.server?.accessToken
                val domain = mapper.mapEntityToDomain(localEntity)
                val resolved = if (baseUrl != null && token != null) {
                    mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                        baseUrl = baseUrl, accessToken = token
                    )
                } else domain
                return Result.success(resolved)
            }
        }

        // API fetch (includes chapters & markers via includeMarkers=1)
        val client = serverClientResolver.getClient(serverId)
            ?: if (localEntity != null) {
                // Server offline but we have local data — return it
                val domain = mapper.mapEntityToDomain(localEntity)
                return Result.success(domain)
            } else {
                return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))
            }

        val result = safeApiCall("PlexSourceHandler.getDetail") {
            val response = client.getMetadata(ratingKey)
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                if (metadata != null) {
                    return@safeApiCall mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
                }
            }
            // API failed — fall back to Room if available
            if (localEntity != null) {
                Timber.w("PlexSourceHandler: API failed for $ratingKey, falling back to Room cache")
                val domain = mapper.mapEntityToDomain(localEntity)
                val baseUrl = client.baseUrl
                val token = client.server.accessToken
                return@safeApiCall mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                    baseUrl = baseUrl, accessToken = token
                )
            }
            throw AppError.Media.NotFound("Media $ratingKey not found on server $serverId")
        }

        // On timeout/connection error: invalidate stale cached connection and retry once
        val error = result.exceptionOrNull()
        val isConnectionError = error is AppError.Network.Timeout ||
            error is AppError.Network.NoConnection ||
            (error is AppError.Network.ServerError && error.cause is java.io.IOException)
        if (isConnectionError) {
            Timber.w("PlexSourceHandler: Connection failed for $serverId, invalidating cache and retrying")
            serverClientResolver.invalidateConnection(serverId)
            val retryClient = serverClientResolver.getClient(serverId)
            if (retryClient != null) {
                val retryResult = safeApiCall("PlexSourceHandler.getDetail(retry)") {
                    val response = retryClient.getMetadata(ratingKey)
                    if (response.isSuccessful) {
                        val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                        if (metadata != null) {
                            return@safeApiCall mapper.mapDtoToDomain(metadata, serverId, retryClient.baseUrl, retryClient.server.accessToken)
                        }
                    }
                    if (localEntity != null) {
                        val domain = mapper.mapEntityToDomain(localEntity)
                        return@safeApiCall mediaUrlResolver.resolveUrls(domain, retryClient.baseUrl, retryClient.server.accessToken).copy(
                            baseUrl = retryClient.baseUrl, accessToken = retryClient.server.accessToken
                        )
                    }
                    throw AppError.Media.NotFound("Media $ratingKey not found on server $serverId")
                }
                if (retryResult.isSuccess) return retryResult
            }
            // Retry also failed — fall back to Room if available
            if (localEntity != null) {
                Timber.w("PlexSourceHandler: Retry also failed for $ratingKey, falling back to Room cache")
                return Result.success(mapper.mapEntityToDomain(localEntity))
            }
        }

        return result.map { mergeOverrides(it, ratingKey, serverId) }
    }

    /**
     * Merges TMDB overrides from Room into a domain item loaded via API.
     * ~1ms Room lookup by primary key. No-op if no overrides exist.
     */
    private suspend fun mergeOverrides(item: MediaItem, ratingKey: String, serverId: String): MediaItem {
        val entity = mediaDao.getMedia(ratingKey, serverId)
        if (entity == null) {
            Timber.d("MERGE_OVERRIDE: No entity in Room for rk=$ratingKey sid=$serverId — skip")
            return item
        }
        val hasOverrides = entity.overriddenSummary != null || entity.overriddenThumbUrl != null
        val scraped = entity.scrapedRating
        val hasRatingOverride = scraped != null && scraped > 0.0
        if (!hasOverrides && !hasRatingOverride) {
            Timber.d("MERGE_OVERRIDE: No overrides for '${item.title}' rk=$ratingKey — skip")
            return item
        }
        Timber.d("MERGE_OVERRIDE: Applying overrides for '${item.title}' rk=$ratingKey — summary=${entity.overriddenSummary?.take(50)}... thumb=${entity.overriddenThumbUrl?.take(60)}... rating=$scraped")
        return item.copy(
            summary = entity.overriddenSummary ?: item.summary,
            thumbUrl = entity.overriddenThumbUrl ?: item.thumbUrl,
            rating = if (hasRatingOverride) scraped else item.rating,
        )
    }

    override suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        // Plex uses getChildren for both seasons of show and episodes of season
        return getEpisodes(ratingKey, serverId)
    }

    override suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>> {
        // Room first
        val localEntities = mediaDao.getChildren(seasonRatingKey, serverId)
        if (localEntities.isNotEmpty()) {
            val client = serverClientResolver.getClient(serverId)
            val baseUrl = client?.baseUrl
            val token = client?.server?.accessToken
            val items = localEntities.map {
                mapper.mapEntityToDomain(it).let { domain ->
                    if (baseUrl != null && token != null) {
                        mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                            baseUrl = baseUrl, accessToken = token
                        )
                    } else domain
                }
            }
            return Result.success(items)
        }

        // API fallback
        return safeApiCall("PlexSourceHandler.getEpisodes") {
            val client = serverClientResolver.getClient(serverId)
            if (client != null) {
                val response = client.getChildren(seasonRatingKey)
                if (response.isSuccessful) {
                    val metadata = response.body()?.mediaContainer?.metadata
                    if (metadata != null) {
                        val entities = metadata.mapIndexed { index, dto ->
                            mapper.mapDtoToEntity(dto, serverId, "", isOwned = client.server.isOwned).copy(pageOffset = index)
                        }
                        mediaDao.upsertMedia(entities)
                        Timber.d("Cached ${entities.size} Plex episodes to Room for $seasonRatingKey")
                        return@safeApiCall metadata.map {
                            mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                        }
                    }
                }
            }
            throw AppError.Media.NotFound("Episodes for $seasonRatingKey not found")
        }
    }

    override suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        val cacheKey = "$ratingKey:$serverId"
        val cached = similarCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < similarCacheTtlMs) {
            Timber.d("Similar: Cache hit for $ratingKey (${cached.second.size} items)")
            return Result.success(cached.second)
        }

        val client = serverClientResolver.getClient(serverId)
            ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

        return safeApiCall("PlexSourceHandler.getSimilarMedia") {
            val response = client.getRelated(ratingKey)
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Similar: API returned ${response.code()} for $ratingKey")
            }
            val body = response.body()
            if (body == null) {
                Timber.w("Similar: Response body is null for $ratingKey")
                return@safeApiCall emptyList<MediaItem>()
            }
            val hubs = body.mediaContainer?.hubs ?: emptyList()
            val similarHub = hubs.find { it.hubIdentifier == "similar" } ?: hubs.firstOrNull()
            val metadata = similarHub?.metadata ?: emptyList()
            val items = metadata.map {
                mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
            }
            similarCache[cacheKey] = System.currentTimeMillis() to items
            items
        }
    }

    override fun needsEnrichment(): Boolean = true
    override fun needsUrlResolution(): Boolean = true
    override fun metadataScoreBonus(): Int = 3
}
