package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.ServerMapper
import com.chakir.plexhubtv.domain.model.AuthPin
import com.chakir.plexhubtv.domain.model.Server
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implémentation du repository d'authentification.
 * Gère le Login (via PIN code), la persistance du Token, et la découverte des serveurs.
 *
 * Utilise une stratégie de cache à 3 niveaux pour les serveurs :
 * 1. Cache mémoire (pour la rapidité immédiate).
 * 2. Cache base de données (pour le hors-ligne et le démarrage rapide).
 * 3. Rafraîchissement API (pour la mise à jour des IPs/Tokens).
 */
class AuthRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val settingsDataStore: SettingsDataStore,
    private val serverMapper: ServerMapper,
    private val userMapper: com.chakir.plexhubtv.data.mapper.UserMapper,
    private val database: com.chakir.plexhubtv.core.database.PlexDatabase
) : AuthRepository {

    override suspend fun checkAuthentication(): Boolean {
        val currentToken = settingsDataStore.plexToken.first()
        if (currentToken.isNullOrBlank()) {
             return false
        }

        // Ensure Client ID exists
        val clientId = settingsDataStore.clientId.first()
        if (clientId.isNullOrBlank()) {
             settingsDataStore.saveClientId(java.util.UUID.randomUUID().toString())
        }
        
        return !currentToken.isNullOrBlank()
    }

    override suspend fun getPin(strong: Boolean): Result<AuthPin> {
        return try {
            var clientId = settingsDataStore.clientId.first()
            if (clientId.isNullOrBlank()) {
                clientId = java.util.UUID.randomUUID().toString()
                settingsDataStore.saveClientId(clientId)
            }

            val response = api.getPin(strong = strong, clientId = clientId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(AuthPin(id = body.id!!.toString(), code = body.code!!))
            } else {
                Result.failure(Exception("Failed to get PIN: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkPin(pinId: String): Result<Boolean> {
         return try {
            val clientId = settingsDataStore.clientId.first() ?: return Result.failure(Exception("Client ID not found"))
            val response = api.getPinStatus(id = pinId, clientId = clientId)
            val body = response.body()

            if (response.isSuccessful && body != null) {
                if (body.authToken != null) {
                    settingsDataStore.saveToken(body.authToken)
                    Result.success(true)
                } else {
                    Result.success(false) // Not yet linked
                }
            } else {
                 Result.failure(Exception("Failed to check PIN status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithToken(token: String): Result<Boolean> {
        return try {
            val clientId = settingsDataStore.clientId.first() ?: java.util.UUID.randomUUID().toString().also { settingsDataStore.saveClientId(it) }
            
            // Verify token by calling getUser or similar. 
            // For now, let's just save it and try to fetch user.
            val response = api.getUser(token = token, clientId = clientId)
            if (response.isSuccessful) {
                settingsDataStore.saveToken(token)
                Result.success(true)
            } else {
                Result.failure(Exception("Invalid token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.domain.model.PlexHomeUser>> {
        return try {
            val token = settingsDataStore.plexToken.first() ?: return Result.failure(Exception("Not authenticated"))
            val clientId = settingsDataStore.clientId.first() ?: return Result.failure(Exception("Client ID not found"))
            
            val response = api.getHomeUsers(token = token, clientId = clientId)
            val body = response.body()
            
            if (response.isSuccessful && body != null) {
                val users = body.map { userMapper.mapDtoToDomain(it) }
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to get home users: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun switchUser(user: com.chakir.plexhubtv.domain.model.PlexHomeUser, pin: String?): Result<Boolean> {
        return try {
            val currentToken = settingsDataStore.plexToken.first() ?: return Result.failure(Exception("Not authenticated"))
            val clientId = settingsDataStore.clientId.first() ?: return Result.failure(Exception("Client ID not found"))
            
            val response = api.switchUser(
                uuid = user.uuid,
                pin = pin,
                token = currentToken,
                clientId = clientId
            )
            val body = response.body()
            
            if (response.isSuccessful && body != null && body.authToken.isNotEmpty()) {
                // IMPORTANT: Before saving the new token, clear the caches
                database.clearAllTables()
                
                settingsDataStore.saveToken(body.authToken)
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to switch user: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var cachedServers: List<Server>? = null
 
    override suspend fun getServers(forceRefresh: Boolean): Result<List<Server>> {
        // 1. Memory Cache
        if (!forceRefresh && cachedServers != null) {
            return Result.success(cachedServers!!)
        }

        // 2. DB Cache
        try {
            val dbServers = database.serverDao().getAllServers().first()
            if (!forceRefresh && dbServers.isNotEmpty()) {
                val domainServers = dbServers.map { serverMapper.mapEntityToDomain(it) }
                cachedServers = domainServers
                return Result.success(domainServers)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "DB Cache failed: ${e.message}")
        }

        // 3. API Refresh
        return try {
            val token = settingsDataStore.plexToken.first()
            if (token.isNullOrBlank()) return Result.failure(Exception("Not authenticated"))

            val clientId = settingsDataStore.clientId.first()
            if (clientId.isNullOrBlank()) return Result.failure(Exception("Client ID not found"))
            
            val response = api.getResources(token = token, clientId = clientId)
            val body = response.body()
            
            if (response.isSuccessful && body != null) {
                val servers = body.mapNotNull { resource ->
                    serverMapper.mapDtoToDomain(resource)
                }
                
                // Update Cache in background
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        servers.forEach { domainServer ->
                            database.serverDao().insertServer(serverMapper.mapDomainToEntity(domainServer))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthRepository", "Failed to update DB cache: ${e.message}")
                    }
                }
                
                cachedServers = servers
                Result.success(servers)
            } else {
                android.util.Log.e("AuthRepository", "API Error: ${response.code()}")
                Result.failure(Exception("Failed to get servers: ${response.code()}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "API Exception: ${e.message}")
            Result.failure(e)
        }
    }

    override fun observeAuthState(): Flow<Boolean> {
        return settingsDataStore.plexToken.map { !it.isNullOrBlank() }
    }
}
