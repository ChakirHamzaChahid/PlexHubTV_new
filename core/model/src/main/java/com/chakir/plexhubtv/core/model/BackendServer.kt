package com.chakir.plexhubtv.core.model

data class BackendServer(
    val id: String,
    val label: String,
    val baseUrl: String,
    val isActive: Boolean,
    val lastSyncedAt: Long,
)
