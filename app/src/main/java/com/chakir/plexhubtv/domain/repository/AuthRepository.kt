package com.chakir.plexhubtv.domain.repository

import com.chakir.plexhubtv.domain.model.AuthPin
import com.chakir.plexhubtv.domain.model.Server
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun checkAuthentication(): Boolean
    suspend fun getPin(strong: Boolean = true): Result<AuthPin> // Returns PIN code/ID
    suspend fun checkPin(pinId: String): Result<Boolean> // Returns true if linked
    suspend fun loginWithToken(token: String): Result<Boolean>
    suspend fun getServers(forceRefresh: Boolean = false): Result<List<Server>>
    suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.domain.model.PlexHomeUser>>
    suspend fun switchUser(user: com.chakir.plexhubtv.domain.model.PlexHomeUser, pin: String? = null): Result<Boolean>
    fun observeAuthState(): Flow<Boolean>
}
