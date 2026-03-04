package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
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

    override fun matches(serverId: String): Boolean =
        !serverId.startsWith("xtream_") && !serverId.startsWith("backend_")

    override suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        // Room first
        val localEntity = mediaDao.getMedia(ratingKey, serverId)
        if (localEntity != null) {
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
            Timber.d("PlexSourceHandler: Room cache missing streams for $ratingKey, fetching from network")
        }

        // API fallback
        val client = serverClientResolver.getClient(serverId)
            ?: return Result.failure(AppError.Network.ServerError("Server $serverId unavailable"))

        return safeApiCall("PlexSourceHandler.getDetail") {
            val response = client.getMetadata(ratingKey)
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata?.firstOrNull()
                if (metadata != null) {
                    return@safeApiCall mapper.mapDtoToDomain(metadata, serverId, client.baseUrl, client.server.accessToken)
                }
            }
            throw AppError.Media.NotFound("Media $ratingKey not found on server $serverId")
        }
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
                        val entities = metadata.map { mapper.mapDtoToEntity(it, serverId, "") }
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
