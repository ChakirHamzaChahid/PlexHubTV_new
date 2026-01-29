package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.Server
import com.chakir.plexhubtv.core.util.Resource
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun syncServer(server: Server): Result<Unit>
    suspend fun syncLibrary(server: Server, libraryKey: String): Result<Unit>
}
