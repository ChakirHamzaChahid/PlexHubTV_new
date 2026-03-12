package com.chakir.plexhubtv.core.model

data class BackendConnectionInfo(
    val status: String = "ok",
    val totalMedia: Int,
    val enrichedMedia: Int,
    val brokenStreams: Int = 0,
    val accounts: Int = 0,
    val version: String,
    val lastSyncAt: Long? = null,
)
