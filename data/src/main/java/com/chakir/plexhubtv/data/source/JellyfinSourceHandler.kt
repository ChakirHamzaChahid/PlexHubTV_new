package com.chakir.plexhubtv.data.source

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.data.mapper.JellyfinMapper
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.JellyfinClientResolver
import com.chakir.plexhubtv.domain.source.MediaSourceHandler
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jellyfin media source handler.
 *
 * Room-first with API fallback. Auth is via `Authorization` header (added by JellyfinImageInterceptor),
 * NOT embedded in image URLs. Episodes are API-first to include chapters (not stored by sync).
 */
@Singleton
class JellyfinSourceHandler @Inject constructor(
    private val clientResolver: JellyfinClientResolver,
    private val mediaDao: MediaDao,
    private val jellyfinMapper: JellyfinMapper,
    private val mediaMapper: MediaMapper,
) : MediaSourceHandler {

    private val similarCache = ConcurrentHashMap<String, Pair<Long, List<MediaItem>>>()
    private val similarCacheTtlMs = 10 * 60 * 1000L

    override fun matches(serverId: String): Boolean =
        serverId.startsWith(SourcePrefix.JELLYFIN)

    override suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem> {
        val localEntity = mediaDao.getMedia(ratingKey, serverId)
        val isEpisode = localEntity?.type == "episode"

        // Non-episode in Room → return with resolved URLs.
        // If mediaParts is empty (sync excludes MediaSources), fetch from API to get them.
        // The player needs mediaParts for stream URL building.
        if (!isEpisode && localEntity != null) {
            val client = clientResolver.getClient(serverId)

            // MediaSources missing → single-item API fetch to get streams for playback
            if (localEntity.mediaParts.isEmpty() && client != null) {
                try {
                    val response = client.getItem(ratingKey)
                    if (response.isSuccessful) {
                        val apiItem = response.body()
                        if (apiItem != null && !apiItem.mediaSources.isNullOrEmpty()) {
                            val apiDomain = jellyfinMapper.mapDtoToDomain(
                                apiItem, serverId, client.baseUrl, client.accessToken,
                            )
                            Timber.d("JellyfinSourceHandler: Enriched $ratingKey with ${apiDomain.mediaParts.size} parts from API")
                            return Result.success(apiDomain)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "JellyfinSourceHandler: API fetch for MediaSources failed, using Room entity")
                }
            }

            val domain = mediaMapper.mapEntityToDomain(localEntity)
            val resolved = if (client != null) {
                resolveJellyfinUrls(domain, client.baseUrl, client.accessToken)
            } else domain
            return Result.success(resolved)
        }

        // API fetch (episodes need chapters, or items not in Room)
        val client = clientResolver.getClient(serverId)
            ?: if (localEntity != null) {
                return Result.success(mediaMapper.mapEntityToDomain(localEntity))
            } else {
                return Result.failure(AppError.Network.ServerError("Jellyfin server $serverId unavailable"))
            }

        val result = safeApiCall("JellyfinSourceHandler.getDetail") {
            val response = client.getItem(ratingKey)
            if (response.isSuccessful) {
                val item = response.body()
                if (item != null) {
                    return@safeApiCall jellyfinMapper.mapDtoToDomain(
                        item, serverId, client.baseUrl, client.accessToken,
                    )
                }
            }
            // API failed — fall back to Room
            if (localEntity != null) {
                Timber.w("JellyfinSourceHandler: API failed for $ratingKey, falling back to Room")
                val domain = mediaMapper.mapEntityToDomain(localEntity)
                return@safeApiCall resolveJellyfinUrls(domain, client.baseUrl, client.accessToken)
            }
            throw AppError.Media.NotFound("Media $ratingKey not found on Jellyfin server $serverId")
        }

        return result
    }

    override suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        // Room first
        val localEntities = mediaDao.getChildren(ratingKey, serverId)
        if (localEntities.isNotEmpty()) {
            val client = clientResolver.getClient(serverId)
            val items = localEntities.map {
                val domain = mediaMapper.mapEntityToDomain(it)
                if (client != null) {
                    resolveJellyfinUrls(domain, client.baseUrl, client.accessToken)
                } else domain
            }
            return Result.success(items)
        }

        // API fallback — use generic getItems(parentId) which works for both
        // series→seasons AND season→episodes (GetMediaDetailUseCase calls this for both)
        return safeApiCall("JellyfinSourceHandler.getSeasons") {
            val client = clientResolver.getClient(serverId)
                ?: throw AppError.Network.ServerError("Jellyfin server $serverId unavailable")
            val response = client.getItems(
                parentId = ratingKey,
                sortBy = "IndexNumber",
                sortOrder = "Ascending",
            )
            if (response.isSuccessful) {
                val items = response.body()?.items
                if (items != null) {
                    // Cache to Room
                    val entities = items.mapIndexed { index, dto ->
                        jellyfinMapper.mapDtoToEntity(dto, serverId, ratingKey).copy(pageOffset = index)
                    }
                    mediaDao.upsertMedia(entities)
                    Timber.d("Cached ${entities.size} Jellyfin children to Room for $ratingKey")
                    return@safeApiCall items.map {
                        jellyfinMapper.mapDtoToDomain(it, serverId, client.baseUrl, client.accessToken)
                    }
                }
            }
            throw AppError.Media.NotFound("Children for $ratingKey not found on Jellyfin")
        }
    }

    override suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>> {
        // Room first
        val localEntities = mediaDao.getChildren(seasonRatingKey, serverId)
        if (localEntities.isNotEmpty()) {
            val client = clientResolver.getClient(serverId)
            val items = localEntities.map {
                val domain = mediaMapper.mapEntityToDomain(it)
                if (client != null) {
                    resolveJellyfinUrls(domain, client.baseUrl, client.accessToken)
                } else domain
            }
            return Result.success(items)
        }

        // API fallback — use getItems with parentId (avoids needing seriesId)
        return safeApiCall("JellyfinSourceHandler.getEpisodes") {
            val client = clientResolver.getClient(serverId)
                ?: throw AppError.Network.ServerError("Jellyfin server $serverId unavailable")
            val response = client.getItems(
                parentId = seasonRatingKey,
                includeItemTypes = "Episode",
                sortBy = "IndexNumber",
                sortOrder = "Ascending",
            )
            if (response.isSuccessful) {
                val items = response.body()?.items
                if (items != null) {
                    // Cache to Room
                    val entities = items.mapIndexed { index, dto ->
                        jellyfinMapper.mapDtoToEntity(dto, serverId, "").copy(pageOffset = index)
                    }
                    mediaDao.upsertMedia(entities)
                    Timber.d("Cached ${entities.size} Jellyfin episodes to Room for $seasonRatingKey")
                    return@safeApiCall items.map {
                        jellyfinMapper.mapDtoToDomain(it, serverId, client.baseUrl, client.accessToken)
                    }
                }
            }
            throw AppError.Media.NotFound("Episodes for $seasonRatingKey not found on Jellyfin")
        }
    }

    override suspend fun getSimilarMedia(ratingKey: String, serverId: String): Result<List<MediaItem>> {
        val cacheKey = "$ratingKey:$serverId"
        val cached = similarCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < similarCacheTtlMs) {
            return Result.success(cached.second)
        }

        val client = clientResolver.getClient(serverId)
            ?: return Result.failure(AppError.Network.ServerError("Jellyfin server $serverId unavailable"))

        return safeApiCall("JellyfinSourceHandler.getSimilarMedia") {
            val response = client.getSimilar(ratingKey)
            if (!response.isSuccessful) {
                throw AppError.Network.ServerError("Similar: API returned ${response.code()} for $ratingKey")
            }
            val items = response.body()?.items ?: emptyList()
            val mapped = items.map {
                jellyfinMapper.mapDtoToDomain(it, serverId, client.baseUrl, client.accessToken)
            }
            similarCache[cacheKey] = System.currentTimeMillis() to mapped
            mapped
        }
    }

    override fun needsEnrichment(): Boolean = true

    // Jellyfin stores RELATIVE image URLs — resolved with baseUrl, auth via Authorization header
    override fun needsUrlResolution(): Boolean = true

    // Documented hierarchy: Plex (3) > Jellyfin (2) > Xtream/Backend (0)
    override fun metadataScoreBonus(): Int = 2

    // ========================================
    // Jellyfin-specific URL resolution
    // ========================================

    /**
     * Resolves relative Jellyfin image URLs to full URLs with baseUrl.
     * Auth is NOT embedded in URLs — [JellyfinImageInterceptor] adds the
     * `Authorization: MediaBrowser Token="..."` header at request time.
     */
    private fun resolveJellyfinUrls(item: MediaItem, baseUrl: String, token: String): MediaItem =
        item.copy(
            thumbUrl = resolveIfRelative(item.thumbUrl, baseUrl),
            artUrl = resolveIfRelative(item.artUrl, baseUrl),
            parentThumb = resolveIfRelative(item.parentThumb, baseUrl),
            grandparentThumb = resolveIfRelative(item.grandparentThumb, baseUrl),
            baseUrl = baseUrl,
            accessToken = token,
        )

    private fun resolveIfRelative(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        return "$baseUrl$url"
    }
}
