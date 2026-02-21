package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.AuthPin
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.model.toAppError
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.ServerMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
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
class AuthRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val settingsDataStore: SettingsDataStore,
        private val serverMapper: ServerMapper,
        private val userMapper: com.chakir.plexhubtv.data.mapper.UserMapper,
        private val database: com.chakir.plexhubtv.core.database.PlexDatabase,
        @ApplicationScope private val applicationScope: CoroutineScope,
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
            var clientId = settingsDataStore.clientId.first()
            if (clientId.isNullOrBlank()) {
                clientId = java.util.UUID.randomUUID().toString()
                settingsDataStore.saveClientId(clientId)
            }

            return safeApiCall("getPin") {
                val response = api.getPin(strong = strong, clientId = clientId)
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    throw AppError.Auth.PinGenerationFailed("Failed to get PIN: ${response.code()}")
                }

                val pinId = body.id
                    ?: throw AppError.Auth.PinGenerationFailed("PIN ID missing in API response")
                val pinCode = body.code
                    ?: throw AppError.Auth.PinGenerationFailed("PIN code missing in API response")

                AuthPin(id = pinId.toString(), code = pinCode)
            }
        }

        override suspend fun checkPin(pinId: String): Result<Boolean> {
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("checkPin") {
                val response = api.getPinStatus(id = pinId, clientId = clientId)
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    throw AppError.Auth.PinGenerationFailed("Failed to check PIN status: ${response.code()}")
                }

                val authToken = body.authToken
                if (authToken != null) {
                    settingsDataStore.saveToken(authToken)
                    true
                } else {
                    false // Not yet linked
                }
            }
        }

        override suspend fun loginWithToken(token: String): Result<Boolean> {
            val clientId = settingsDataStore.clientId.first()
                ?: java.util.UUID.randomUUID().toString().also { settingsDataStore.saveClientId(it) }

            return safeApiCall("loginWithToken") {
                val response = api.getUser(token = token, clientId = clientId)
                if (!response.isSuccessful) {
                    throw AppError.Auth.InvalidToken("Invalid token: ${response.code()}")
                }

                settingsDataStore.saveToken(token)
                true
            }
        }

        override suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.core.model.PlexHomeUser>> {
            val token = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("getHomeUsers") {
                val response = api.getHomeUsers(token = token, clientId = clientId)
                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    throw AppError.Network.ServerError("Failed to get home users: ${response.code()}")
                }

                body.map { userMapper.mapDtoToDomain(it) }
            }
        }

        override suspend fun switchUser(
            user: com.chakir.plexhubtv.core.model.PlexHomeUser,
            pin: String?,
        ): Result<Boolean> {
            val currentToken = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))
            val clientId = settingsDataStore.clientId.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

            return safeApiCall("switchUser") {
                val response =
                    api.switchUser(
                        uuid = user.uuid,
                        pin = pin,
                        token = currentToken,
                        clientId = clientId,
                    )
                val body = response.body()

                if (!response.isSuccessful || body == null || body.authToken.isEmpty()) {
                    throw AppError.Auth.InvalidToken("Failed to switch user: ${response.code()}")
                }

                // IMPORTANT: Before saving the new token, clear the caches
                database.clearAllTables()

                settingsDataStore.saveToken(body.authToken)
                true
            }
        }

        private var cachedServers: List<Server>? = null

        override suspend fun getServers(forceRefresh: Boolean): Result<List<Server>> {
            // 1. Memory Cache
            val cached = cachedServers
            if (!forceRefresh && cached != null) {
                return Result.success(cached)
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
                Timber.e(e, "DB Cache failed")
            }

            // 3. API Refresh
            return try {
                val token = settingsDataStore.plexToken.first()
                if (token.isNullOrBlank()) return Result.failure(AppError.Auth.InvalidToken("Not authenticated"))

                val clientId = settingsDataStore.clientId.first()
                if (clientId.isNullOrBlank()) return Result.failure(AppError.Auth.InvalidToken("Client ID not found"))

                val response = api.getResources(token = token, clientId = clientId)
                val body = response.body()

                if (response.isSuccessful && body != null) {
                    val servers =
                        body.mapNotNull { resource ->
                            serverMapper.mapDtoToDomain(resource)
                        }

                    // Update Cache in background using ApplicationScope
                    applicationScope.launch(Dispatchers.IO) {
                        try {
                            servers.forEach { domainServer ->
                                database.serverDao().insertServer(serverMapper.mapDomainToEntity(domainServer))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update DB cache")
                        }
                    }

                    cachedServers = servers
                    Result.success(servers)
                } else {
                    Timber.e("API Error: ${response.code()}")
                    Result.failure(AppError.Network.ServerError("Failed to get servers: ${response.code()}"))
                }
            } catch (e: IOException) {
                val errorMessage = when (e) {
                    is java.net.UnknownHostException ->
                        "Unable to connect to Plex servers. Check your internet connection."
                    is java.net.SocketTimeoutException ->
                        "Connection timeout. Plex servers are not responding."
                    else ->
                        "Network error: ${e.message ?: "Unable to reach Plex servers"}"
                }
                Timber.e(e, "Network error fetching servers")

                // Fallback to DB cache on network error
                try {
                    val dbServers = database.serverDao().getAllServers().first()
                    if (dbServers.isNotEmpty()) {
                        val domainServers = dbServers.map { serverMapper.mapEntityToDomain(it) }
                        cachedServers = domainServers
                        Timber.w("Using cached servers due to network error (${dbServers.size} servers)")
                        return Result.success(domainServers)
                    }
                } catch (dbError: Exception) {
                    Timber.e(dbError, "DB fallback also failed")
                }

                Result.failure(AppError.Network.NoConnection(errorMessage))
            } catch (e: Exception) {
                Timber.e(e, "Error fetching servers")
                Result.failure(e.toAppError())
            }
        }

        override fun observeAuthState(): Flow<Boolean> {
            return settingsDataStore.plexToken.map { !it.isNullOrBlank() }
        }

        override suspend fun clearToken() {
            settingsDataStore.clearToken()
        }

        override suspend fun clearAllAuthData(clearDatabase: Boolean) {
            settingsDataStore.clearToken()
            settingsDataStore.clearUser()
            if (clearDatabase) {
                database.clearAllTables()
            }
        }
    }
