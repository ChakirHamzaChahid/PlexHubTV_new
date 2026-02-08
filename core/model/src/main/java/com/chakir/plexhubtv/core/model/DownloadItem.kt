package com.chakir.plexhubtv.core.model

data class DownloadItem(
    val serverId: String,
    val ratingKey: String,
    val status: DownloadStatus,
    val progress: Int,
    val filePath: String?,
    val isCompleted: Boolean,
)
