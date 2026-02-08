package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chakir.plexhubtv.core.model.DownloadStatus

/**
 * Entité représentant un média téléchargé ou en cours de téléchargement.
 * Contient le chemin du fichier local et l'état de la tâche.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val globalKey: String, // serverId:ratingKey
    val serverId: String,
    val ratingKey: String,
    val type: String,
    val parentRatingKey: String? = null,
    val grandparentRatingKey: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0, // 0-100
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val videoFilePath: String? = null,
    val thumbPath: String? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val downloadedAt: Long? = null,
) {
    val isCompleted: Boolean get() = status == DownloadStatus.COMPLETED
    val filePath: String get() = videoFilePath ?: ""
}
