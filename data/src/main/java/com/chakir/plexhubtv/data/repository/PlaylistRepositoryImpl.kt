package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.PlaylistDao
import com.chakir.plexhubtv.core.database.PlaylistEntity
import com.chakir.plexhubtv.core.database.PlaylistItemEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.Playlist
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.domain.repository.PlaylistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val serverClientResolver: ServerClientResolver,
    private val mediaMapper: MediaMapper,
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshPlaylists() {
        try {
            val clients = serverClientResolver.getActiveClients()
            coroutineScope {
                clients.map { client ->
                    async { fetchPlaylistsFromServer(client) }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: refreshPlaylists failed")
        }
    }

    private suspend fun fetchPlaylistsFromServer(client: PlexClient) {
        try {
            val response = client.getPlaylists()
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata ?: return
                val serverId = client.server.clientIdentifier
                val entities = metadata.map { dto ->
                    PlaylistEntity(
                        id = dto.ratingKey,
                        serverId = serverId,
                        title = dto.title,
                        summary = dto.summary,
                        thumbUrl = client.getThumbnailUrl(dto.thumb),
                        playlistType = "video",
                        itemCount = dto.leafCount ?: 0,
                        durationMs = dto.duration ?: 0,
                        lastSync = System.currentTimeMillis(),
                    )
                }
                playlistDao.upsertPlaylists(entities)
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: fetchPlaylistsFromServer failed for ${client.server.name}")
        }
    }

    override suspend fun getPlaylistDetail(playlistId: String, serverId: String): Playlist? {
        val client = serverClientResolver.getClient(serverId) ?: return null
        return try {
            val response = client.getPlaylistItems(playlistId)
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata ?: emptyList()
                val items = metadata.mapIndexed { index, dto ->
                    mediaMapper.mapDtoToDomain(dto, serverId, client.baseUrl, client.server.accessToken)
                }
                // Also cache playlist items locally
                val itemEntities = metadata.mapIndexed { index, dto ->
                    PlaylistItemEntity(
                        playlistId = playlistId,
                        serverId = serverId,
                        itemRatingKey = dto.ratingKey,
                        orderIndex = index,
                        playlistItemId = dto.ratingKey,
                    )
                }
                playlistDao.replacePlaylistItems(playlistId, serverId, itemEntities)

                val playlistEntity = playlistDao.getPlaylist(playlistId, serverId)
                Playlist(
                    id = playlistId,
                    serverId = serverId,
                    title = playlistEntity?.title ?: "",
                    summary = playlistEntity?.summary,
                    thumbUrl = playlistEntity?.thumbUrl,
                    itemCount = items.size,
                    durationMs = playlistEntity?.durationMs ?: 0,
                    items = items,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: getPlaylistDetail failed")
            null
        }
    }

    override suspend fun createPlaylist(title: String, ratingKey: String, serverId: String): Result<Playlist> {
        val client = serverClientResolver.getClient(serverId) ?: return Result.failure(Exception("Server not found"))
        return try {
            val response = client.createPlaylist(title, ratingKey)
            if (response.isSuccessful) {
                val dto = response.body()?.mediaContainer?.metadata?.firstOrNull()
                    ?: return Result.failure(Exception("No playlist returned"))
                val entity = PlaylistEntity(
                    id = dto.ratingKey,
                    serverId = serverId,
                    title = dto.title,
                    summary = dto.summary,
                    thumbUrl = client.getThumbnailUrl(dto.thumb),
                    itemCount = dto.leafCount ?: 1,
                    durationMs = dto.duration ?: 0,
                    lastSync = System.currentTimeMillis(),
                )
                playlistDao.upsertPlaylist(entity)
                Result.success(entity.toDomain())
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: createPlaylist failed")
            Result.failure(e)
        }
    }

    override suspend fun addToPlaylist(playlistId: String, ratingKey: String, serverId: String): Result<Unit> {
        val client = serverClientResolver.getClient(serverId) ?: return Result.failure(Exception("Server not found"))
        return try {
            val response = client.addToPlaylist(playlistId, ratingKey)
            if (response.isSuccessful) {
                refreshPlaylists()
                Result.success(Unit)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: addToPlaylist failed")
            Result.failure(e)
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, playlistItemId: String, serverId: String): Result<Unit> {
        val client = serverClientResolver.getClient(serverId) ?: return Result.failure(Exception("Server not found"))
        return try {
            val response = client.removeFromPlaylist(playlistId, playlistItemId)
            if (response.isSuccessful) {
                playlistDao.removePlaylistItem(playlistId, serverId, playlistItemId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: removeFromPlaylist failed")
            Result.failure(e)
        }
    }

    override suspend fun deletePlaylist(playlistId: String, serverId: String): Result<Unit> {
        val client = serverClientResolver.getClient(serverId) ?: return Result.failure(Exception("Server not found"))
        return try {
            val response = client.deletePlaylist(playlistId)
            if (response.isSuccessful) {
                playlistDao.clearPlaylistItems(playlistId, serverId)
                playlistDao.deletePlaylist(playlistId, serverId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "PlaylistRepository: deletePlaylist failed")
            Result.failure(e)
        }
    }

    private fun PlaylistEntity.toDomain() = Playlist(
        id = id,
        serverId = serverId,
        title = title,
        summary = summary,
        thumbUrl = thumbUrl,
        itemCount = itemCount,
        durationMs = durationMs,
    )
}
