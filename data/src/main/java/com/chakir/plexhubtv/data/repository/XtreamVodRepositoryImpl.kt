package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.IdBridgeDao
import com.chakir.plexhubtv.core.database.IdBridgeEntity
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.XtreamCategory
import com.chakir.plexhubtv.core.network.util.safeApiCall
import com.chakir.plexhubtv.core.network.xtream.XtreamApiClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.mapper.XtreamMediaMapper
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import com.chakir.plexhubtv.domain.repository.XtreamVodRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamVodRepositoryImpl @Inject constructor(
    private val apiClient: XtreamApiClient,
    private val accountRepo: XtreamAccountRepository,
    private val mediaDao: MediaDao,
    private val idBridgeDao: IdBridgeDao,
    private val xtreamMapper: XtreamMediaMapper,
    private val mediaMapper: MediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : XtreamVodRepository {

    override suspend fun getCategories(accountId: String): Result<List<XtreamCategory>> =
        safeApiCall("XtreamVodRepository.getCategories") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val dtos = service.getVodCategories(username, password)
                dtos.mapNotNull { xtreamMapper.mapCategoryDto(it) }
            }
        }

    override suspend fun syncMovies(accountId: String, categoryId: Int?): Result<Int> =
        safeApiCall("XtreamVodRepository.syncMovies") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val dtos = service.getVodStreams(username, password, categoryId = categoryId)

                val entities = dtos.mapNotNull { dto ->
                    if (dto.streamId == null) return@mapNotNull null
                    xtreamMapper.mapVodToEntity(dto, accountId)
                }

                if (entities.isNotEmpty()) {
                    mediaDao.upsertMedia(entities)
                    val bridgeEntries = entities.mapNotNull { entity ->
                        val imdb = entity.imdbId?.takeIf { it.isNotBlank() }
                        val tmdb = entity.tmdbId?.takeIf { it.isNotBlank() }
                        if (imdb != null && tmdb != null) IdBridgeEntity(imdb, tmdb) else null
                    }
                    if (bridgeEntries.isNotEmpty()) idBridgeDao.upsertAll(bridgeEntries)
                }

                Timber.i("XTREAM [VOD] Synced ${entities.size} movies for account $accountId")
                entities.size
            }
        }

    override fun getMovies(accountId: String, categoryId: Int?): Flow<List<MediaItem>> =
        flow {
            val serverId = "xtream_$accountId"
            val filter = categoryId?.toString() ?: "all"

            // Query from Room — Xtream movies have type="movie" and serverId="xtream_xxx"
            val entity = mediaDao.getMediaByServerTypeFilter(serverId, "movie", filter)
            emit(entity.map { mediaMapper.mapEntityToDomain(it) })
        }.flowOn(ioDispatcher)

    override suspend fun enrichMovieDetail(accountId: String, vodId: Int, ratingKey: String): Result<Unit> =
        safeApiCall("XtreamVodRepository.enrichMovieDetail") {
            withContext(ioDispatcher) {
                val (service, username, password) = getServiceCredentials(accountId)
                val response = service.getVodInfo(username, password, vodId = vodId)
                val info = response.info ?: return@withContext Unit

                val serverId = "xtream_$accountId"
                val existing = mediaDao.getMedia(ratingKey, serverId) ?: return@withContext Unit

                val updated = existing.copy(
                    summary = info.plot ?: info.description ?: existing.summary,
                    genres = info.genre ?: existing.genres,
                    tmdbId = info.tmdbId?.toString() ?: existing.tmdbId,
                    artUrl = info.backdropPath?.firstOrNull()?.takeIf { it.isNotBlank() } ?: existing.artUrl,
                    duration = info.durationSecs?.takeIf { it > 0 }?.toLong()?.times(1000) ?: existing.duration,
                )
                mediaDao.insertMedia(updated)
                Timber.d("XTREAM [VOD] Enriched detail for ${existing.title} (vod_id=$vodId)")
            }
        }

    override suspend fun buildStreamUrl(accountId: String, streamId: Int, extension: String): String {
        val account = accountRepo.getAccount(accountId)
            ?: throw IllegalStateException("Account $accountId not found")
        val password = accountRepo.getDecryptedPassword(accountId)
            ?: throw IllegalStateException("Password not found for account $accountId")
        return apiClient.buildMovieUrl(account, account.username, password, streamId, extension)
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
