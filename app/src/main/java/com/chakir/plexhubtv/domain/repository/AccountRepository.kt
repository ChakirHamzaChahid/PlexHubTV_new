package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.PlexHomeUser
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    suspend fun getHomeUsers(): Result<List<PlexHomeUser>>
    suspend fun switchUser(user: PlexHomeUser, pin: String? = null): Result<Boolean>
    fun observeCurrentUser(): Flow<PlexHomeUser?>
    suspend fun getCurrentUser(): PlexHomeUser?
    suspend fun logout()
    
    suspend fun refreshProfile()
}
