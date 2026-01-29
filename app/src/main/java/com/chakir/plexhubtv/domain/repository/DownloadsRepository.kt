package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.DownloadStatus
import com.chakir.plexhubtv.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface DownloadsRepository {
    fun getAllDownloads(): Flow<List<MediaItem>>
    fun getDownloadStatus(mediaId: String): Flow<DownloadStatus>
    suspend fun startDownload(media: MediaItem): Result<Unit>
    suspend fun cancelDownload(mediaId: String): Result<Unit>
    suspend fun deleteDownload(mediaId: String): Result<Unit>
    suspend fun getDownloadedItem(ratingKey: String): Result<com.chakir.plexhubtv.core.database.DownloadEntity?>
}
