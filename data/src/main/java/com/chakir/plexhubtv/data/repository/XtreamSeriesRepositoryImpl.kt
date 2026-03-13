package com.chakir.plexhubtv.data.repository

import androidx.room.withTransaction
import com.chakir.plexhubtv.core.database.IdBridgeDao
import com.chakir.plexhubtv.core.database.IdBridgeEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.XtreamCategory
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.network.xtream.XtreamApiClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.mapper.XtreamMediaMapper
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import com.chakir.plexhubtv.domain.repository.XtreamSeriesRepository
import com.chakir.plexhubtv.domain.usecase.MediaDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamSeriesRepositoryImpl @Inject constructor(
    private val apiClient: XtreamApiClient,
    private val accountRepo: XtreamAccountRepository,
    private val mediaDao: MediaDao,
    private val idBridgeDao: IdBridgeDao,
    private val database: PlexDatabase,
    private val xtreamMapper: XtreamMediaMapper,
    private val mediaMapper: MediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : XtreamSeriesRepository {

    override suspend fun getCategories(accountId: String): Result<List<XtreamCategory>> =
        safeApiCall("XtreamSeriesRepository.getCategories") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val dtos = service.getSeriesCategories(username, password)
                dtos.mapNotNull { xtreamMapper.mapCategoryDto(it) }
            }
        }

    override suspend fun syncSeries(accountId: String, categoryId: Int?): Result<Int> =
        safeApiCall("XtreamSeriesRepository.syncSeries") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val dtos = service.getSeries(username, password, categoryId = categoryId)

                val entities = dtos.mapNotNull { dto ->
                    if (dto.seriesId == null) return@mapNotNull null
                    xtreamMapper.mapSeriesToEntity(dto, accountId)
                }

                val serverId = "xtream_$accountId"
                val syncedRatingKeys = entities.map { it.ratingKey }.toSet()

                if (entities.isNotEmpty()) {
                    database.withTransaction {
                        mediaDao.upsertMedia(entities)
                        val bridgeEntries = entities.mapNotNull { entity ->
                            val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
                            val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
                            if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
                        }
                        if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
                        // Solution C: compute groupKey post-bridge
                        mediaDao.updateGroupKeys(serverId, entities.map { it.ratingKey })
                    }
                }

                // Differential cleanup: only for full sync (no category filter)
                if (categoryId == null && syncedRatingKeys.isNotEmpty()) {
                    val existingKeys = mediaDao.getRatingKeysByServerAndType(serverId, "show")
                    val staleKeys = existingKeys.filter { it !in syncedRatingKeys }
                    if (staleKeys.isNotEmpty()) {
                        database.withTransaction {
                            staleKeys.chunked(500).forEach { chunk ->
                                mediaDao.deleteMediaByKeys(serverId, chunk)
                            }
                        }
                        Timber.i("XTREAM [Series] Cleanup: Removed ${staleKeys.size} stale series for account $accountId")
                    }
                }

                Timber.i("XTREAM [Series] Synced ${entities.size} series for account $accountId")
                entities.size
            }
        }

    override fun getSeries(accountId: String, categoryId: Int?): Flow<List<MediaItem>> =
        flow {
            val serverId = "xtream_$accountId"
            val filter = categoryId?.toString() ?: "all"

            val entities = mediaDao.getMediaByServerTypeFilter(serverId, "show", filter)
            emit(entities.map { mediaMapper.mapEntityToDomain(it) })
        }.flowOn(ioDispatcher)

    override suspend fun getSeriesDetail(accountId: String, seriesId: Int): Result<MediaDetail> =
        safeApiCall("XtreamSeriesRepository.getSeriesDetail") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val response = service.getSeriesInfo(username, password, seriesId = seriesId)
                // Patch seriesId: the "info" object from get_series_info doesn't include series_id,
                // but we need it for building consistent ratingKeys (season_<seriesId>_<num>, etc.)
                val seriesDto = (response.info
                    ?: throw IllegalStateException("Series info not found for seriesId=$seriesId"))
                    .copy(seriesId = seriesId)

                // Map the show entity
                val showEntity = xtreamMapper.mapSeriesToEntity(seriesDto, accountId)
                val showItem = mediaMapper.mapEntityToDomain(showEntity)

                // Map seasons as children
                val seasonItems = response.seasons?.mapNotNull { season ->
                    val seasonNum = season.seasonNumber ?: return@mapNotNull null
                    val serverId = "xtream_$accountId"
                    MediaItem(
                        id = "${serverId}_season_${seriesId}_$seasonNum",
                        ratingKey = "season_${seriesId}_$seasonNum",
                        serverId = serverId,
                        title = season.name ?: "Season $seasonNum",
                        type = com.chakir.plexhubtv.core.model.MediaType.Season,
                        thumbUrl = season.cover,
                        parentRatingKey = "series_$seriesId",
                        parentTitle = seriesDto.name,
                        seasonIndex = seasonNum,
                    )
                } ?: emptyList()

                // Also persist episodes to the DB for later playback
                val episodeEntities = response.episodes?.flatMap { (seasonKey, episodes) ->
                    val seasonNum = seasonKey.toIntOrNull() ?: return@flatMap emptyList()
                    episodes.map { ep ->
                        xtreamMapper.mapEpisodeToEntity(ep, seriesDto, seasonNum, accountId)
                    }
                } ?: emptyList()

                if (episodeEntities.isNotEmpty()) {
                    database.withTransaction {
                        mediaDao.upsertMedia(episodeEntities)
                        val bridgeEntries = episodeEntities.mapNotNull { entity ->
                            val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
                            val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
                            if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
                        }
                        if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
                    }
                }

                MediaDetail(
                    item = showItem,
                    children = seasonItems,
                )
            }
        }

    override suspend fun buildEpisodeUrl(accountId: String, episodeId: String, extension: String): String {
        val account = accountRepo.getAccount(accountId)
            ?: throw IllegalStateException("Account $accountId not found")
        val password = accountRepo.getDecryptedPassword(accountId)
            ?: throw IllegalStateException("Password not found for account $accountId")
        return apiClient.buildEpisodeUrl(account, account.username, password, episodeId, extension)
    }

    private suspend fun getServiceCredentials(accountId: String): Triple<com.chakir.plexhubtv.core.network.xtream.XtreamApiService, String, String> {
        val account = accountRepo.getAccount(accountId)
            ?: throw IllegalArgumentException("Account $accountId not found")
        val password = accountRepo.getDecryptedPassword(accountId)
            ?: throw IllegalStateException("Password not found for account $accountId")
        val service = apiClient.getService(account.baseUrl, account.port)
        return Triple(service, account.username, password)
    }
}
