package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.core.model.IptvChannel
import kotlinx.coroutines.flow.Flow

interface IptvRepository {
    fun getChannels(): Flow<List<IptvChannel>>

    suspend fun refreshChannels(url: String): Result<Unit>

    suspend fun getM3uUrl(): String?

    suspend fun saveM3uUrl(url: String)
}
