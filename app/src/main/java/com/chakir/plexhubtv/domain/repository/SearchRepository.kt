package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.MediaItem

interface SearchRepository {
    suspend fun searchAllServers(query: String): Result<List<MediaItem>>
    suspend fun searchOnServer(server: com.chakir.plexhubtv.domain.model.Server, query: String): Result<List<MediaItem>>
}
