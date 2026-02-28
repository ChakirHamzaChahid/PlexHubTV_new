package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.BackendConnectionInfo
import com.chakir.plexhubtv.core.model.BackendServer
import com.chakir.plexhubtv.core.model.CategoryConfig
import com.chakir.plexhubtv.core.model.CategorySelection
import com.chakir.plexhubtv.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface BackendRepository {
    fun observeServers(): Flow<List<BackendServer>>

    suspend fun addServer(label: String, baseUrl: String): Result<BackendServer>

    suspend fun removeServer(id: String)

    suspend fun testConnection(baseUrl: String): Result<BackendConnectionInfo>

    suspend fun syncMedia(backendId: String): Result<Int>

    suspend fun getStreamUrl(ratingKey: String, backendServerId: String): Result<String>

    suspend fun getEpisodes(parentRatingKey: String, backendServerId: String): Result<List<MediaItem>>

    suspend fun getMediaDetail(ratingKey: String, backendServerId: String): Result<MediaItem>

    suspend fun createXtreamAccount(
        backendId: String,
        label: String,
        baseUrl: String,
        port: Int,
        username: String,
        password: String,
    ): Result<Unit>

    suspend fun deleteXtreamAccount(backendId: String, accountId: String): Result<Unit>

    suspend fun testXtreamAccount(backendId: String, accountId: String): Result<Unit>

    suspend fun syncAll(backendId: String): Result<String>

    suspend fun triggerAccountSync(backendId: String, accountId: String): Result<String>

    suspend fun getCategories(backendId: String, accountId: String): Result<CategoryConfig>

    suspend fun updateCategories(
        backendId: String,
        accountId: String,
        filterMode: String,
        categories: List<CategorySelection>,
    ): Result<Unit>

    suspend fun refreshCategories(backendId: String, accountId: String): Result<Unit>
}
