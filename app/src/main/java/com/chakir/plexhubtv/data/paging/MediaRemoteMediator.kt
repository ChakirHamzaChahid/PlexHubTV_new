package com.chakir.plexhubtv.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.database.PlexDatabase
import com.chakir.plexhubtv.core.database.RemoteKey
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.MediaMapper
import retrofit2.HttpException
import timber.log.Timber

/**
 * Médiateur Paging 3 pour la synchronisation Réseau -> DB.
 *
 * Responsabilités :
 * - Détermine si les données locales sont périmées (via [initialize]).
 * - Charge les pages depuis l'API Plex (/library/sections/all).
 * - Insère les données dans la DB et gère les clés de pagination ([RemoteKey]).
 * - Enregistre des métriques de performance sur les temps de réponse API vs DB.
 */
@OptIn(ExperimentalPagingApi::class)
class MediaRemoteMediator(
    private val libraryKey: String, // Likely the section ID
    private val filter: String,
    private val sortOrder: String,
    private val api: PlexApiService,
    private val database: PlexDatabase,
    private val serverId: String,
    private val serverUrl: String, // Need Base URL to construct full path
    private val token: String,
    private val mapper: MediaMapper,
) : RemoteMediator<Int, MediaEntity>() {
    private val remoteKeysDao = database.remoteKeysDao()
    private val mediaDao = database.mediaDao()

    override suspend fun initialize(): InitializeAction {
        // Check if we have cached data for this specific filter/sort combination
        val cachedKey = remoteKeysDao.getFirstKey(libraryKey, filter, sortOrder)
        return if (cachedKey == null) {
            if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                Timber.d("INITIALIZE: No cache for lib=$libraryKey filter=$filter sort=$sortOrder → REFRESH")
            }
            // No cache, force refresh
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            if (com.chakir.plexhubtv.BuildConfig.DEBUG) {
                Timber.d(
                    "INITIALIZE: Cache found for lib=$libraryKey filter=$filter sort=$sortOrder offset=${cachedKey.offset} → SKIP_REFRESH",
                )
            }
            // Have cache, show it immediately and refresh in background
            InitializeAction.SKIP_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MediaEntity>,
    ): MediatorResult {
        return try {
            val offset =
                when (loadType) {
                    LoadType.REFRESH -> {
                        // Support jumping to a specific position via initialKey
                        val anchor = state.anchorPosition
                        if (anchor != null) {
                            Timber.d("REFRESH with anchor=$anchor")
                            anchor
                        } else {
                            0
                        }
                    }
                    LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                    LoadType.APPEND -> {
                        val lastItem = state.lastItemOrNull() ?: return MediatorResult.Success(endOfPaginationReached = true)

                        // One Key Per Page Strategy:
                        // Find the key associated with the page that produced 'lastItem'.
                        // Queries for key with offset <= lastItem.pageOffset
                        val key =
                            remoteKeysDao.getClosestKey(
                                libraryKey = libraryKey,
                                filter = filter,
                                sortOrder = sortOrder,
                                offset = lastItem.pageOffset,
                            )

                        key?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = key != null)
                    }
                }

            val limit = if (loadType == LoadType.REFRESH) state.config.initialLoadSize else state.config.pageSize

            // Construct URL: /library/sections/{id}/all
            val url = "$serverUrl/library/sections/$libraryKey/all"

            // Prepare API parameters (filter and sortOrder are already normalized upstream)
            val typeParam = if (filter == "all") null else filter
            val sortParam = if (sortOrder == "default") null else sortOrder

            val startTime = System.currentTimeMillis()
            Timber.d("--------------------------------------------------")
            Timber.d("PAGING [Load Request]: type=$loadType offset=$offset limit=$limit for lib=$libraryKey")

            // 1. API Fetch
            val apiStartTime = System.currentTimeMillis()
            val response =
                api.getLibraryContents(
                    url = url,
                    start = offset,
                    size = limit,
                    type = typeParam,
                    sort = sortParam,
                )
            val apiDuration = System.currentTimeMillis() - apiStartTime

            if (!response.isSuccessful) {
                Timber.e("PAGING FAILED: lib=$libraryKey code=${response.code()}")
                return MediatorResult.Error(retrofit2.HttpException(response))
            }

            val body = response.body()
            val items = body?.mediaContainer?.metadata ?: emptyList()
            val endOfPaginationReached = items.isEmpty()

            // 2. DB Transaction
            val dbStartTime = System.currentTimeMillis()
            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    mediaDao.clearByLibraryFilterSort(libraryKey, filter, sortOrder)
                    remoteKeysDao.clearByLibraryFilterSort(libraryKey, filter, sortOrder)
                }

                val prevKey = if (offset == 0) null else offset - limit
                val nextKey = if (endOfPaginationReached) null else offset + items.size

                val remoteKey =
                    RemoteKey(
                        libraryKey = libraryKey,
                        filter = filter,
                        sortOrder = sortOrder,
                        offset = offset,
                        prevKey = prevKey,
                        nextKey = nextKey,
                    )

                remoteKeysDao.insert(remoteKey)

                val entities =
                    items.mapIndexed { index, dto ->
                        mapper.mapDtoToEntity(dto, serverId, libraryKey)
                            .copy(
                                librarySectionId = libraryKey,
                                filter = filter,
                                sortOrder = sortOrder,
                                pageOffset = offset + index,
                            )
                    }

                mediaDao.upsertMedia(entities)
            }
            val dbDuration = System.currentTimeMillis() - dbStartTime
            val totalLoadDuration = System.currentTimeMillis() - startTime

            Timber.i("PAGING SUCCESS: lib=$libraryKey items=${items.size}")
            Timber.i(" -> API Latency: ${apiDuration}ms")
            Timber.i(" -> DB Transaction: ${dbDuration}ms")
            Timber.i(" -> Total Load Process: ${totalLoadDuration}ms")
            Timber.d("--------------------------------------------------")

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            Timber.e("PAGING ERROR: ${e.message}")
            MediatorResult.Error(e)
        }
    }
}
