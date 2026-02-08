package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.DownloadDao
import com.chakir.plexhubtv.core.model.DownloadStatus
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.DownloadsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implémentation du repository de téléchargements.
 * Gère le statut et l'accès aux médias téléchargés localement (Feature future/partielle).
 */
class DownloadsRepositoryImpl
    @Inject
    constructor(
        private val downloadDao: DownloadDao,
    ) : DownloadsRepository {
        override fun getAllDownloads(): Flow<List<MediaItem>> =
            flow {
                emit(emptyList())
            }

        override fun getDownloadStatus(mediaId: String): Flow<DownloadStatus> =
            flow {
                emit(DownloadStatus.NOT_DOWNLOADED)
            }

        override suspend fun startDownload(media: MediaItem): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun cancelDownload(mediaId: String): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun deleteDownload(mediaId: String): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun getDownloadedItem(ratingKey: String): Result<com.chakir.plexhubtv.core.model.DownloadItem?> {
            return try {
                val entity = downloadDao.getDownload(ratingKey)
                Result.success(
                    entity?.let {
                        com.chakir.plexhubtv.core.model.DownloadItem(
                            serverId = it.serverId,
                            ratingKey = it.ratingKey,
                            status = it.status,
                            progress = it.progress,
                            filePath = it.filePath,
                            isCompleted = it.isCompleted,
                        )
                    },
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
