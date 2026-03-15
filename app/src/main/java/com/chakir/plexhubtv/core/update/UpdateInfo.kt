package com.chakir.plexhubtv.core.update

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val apkSize: Long = 0L,
    val htmlUrl: String = "",
)
