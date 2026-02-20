package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.exception.AuthException
import com.chakir.plexhubtv.core.common.exception.NetworkException
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.model.AuthPin
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.data.mapper.ServerMapper
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
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
            return try {
                var clientId = settingsDataStore.clientId.first()
                if (clientId.isNullOrBlank()) {
                    clientId = java.util.UUID.randomUUID().toString()
                    settingsDataStore.saveClientId(clientId)
                }

                val response = api.getPin(strong = strong, clientId = clientId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val pinId = body.id
                        ?: return Result.failure(AuthException("PIN ID missing in API response"))
                    val pinCode = body.code
                        ?: return Result.failure(AuthException("PIN code missing in API response"))
                    Result.success(AuthPin(id = pinId.toString(), code = pinCode))
                } else {
                    Result.failure(AuthException("Failed to get PIN: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting PIN")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} getting PIN")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unknown error getting PIN")
                Result.failure(e)
            }
        }

        override suspend fun checkPin(pinId: String): Result<Boolean> {
            return try {
                val clientId = settingsDataStore.clientId.first() ?: return Result.failure(AuthException("Client ID not found"))
                val response = api.getPinStatus(id = pinId, clientId = clientId)
                val body = response.body()

                if (response.isSuccessful && body != null) {
                    val authToken = body.authToken
                    if (authToken != null) {
                        settingsDataStore.saveToken(authToken)
                        Result.success(true)
                    } else {
                        Result.success(false) // Not yet linked
                    }
                } else {
                    Result.failure(AuthException("Failed to check PIN status: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error checking PIN status")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} checking PIN status")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unknown error checking PIN status")
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
                    Result.failure(AuthException("Invalid token: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error logging in with token")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} logging in with token")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unknown error logging in with token")
                Result.failure(e)
            }
        }

        override suspend fun getHomeUsers(): Result<List<com.chakir.plexhubtv.core.model.PlexHomeUser>> {
            return try {
                val token = settingsDataStore.plexToken.first() ?: return Result.failure(Exception("Not authenticated"))
                val clientId = settingsDataStore.clientId.first() ?: return Result.failure(Exception("Client ID not found"))

                val response = api.getHomeUsers(token = token, clientId = clientId)
                val body = response.body()

                if (response.isSuccessful && body != null) {
                    val users = body.map { userMapper.mapDtoToDomain(it) }
                    Result.success(users)
                } else {
                    Result.failure(AuthException("Failed to get home users: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting home users")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} getting home users")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unknown error getting home users")
                Result.failure(e)
            }
        }

        override suspend fun switchUser(
            user: com.chakir.plexhubtv.core.model.PlexHomeUser,
            pin: String?,
        ): Result<Boolean> {
            return try {
                val currentToken = settingsDataStore.plexToken.first() ?: return Result.failure(Exception("Not authenticated"))
                val clientId = settingsDataStore.clientId.first() ?: return Result.failure(Exception("Client ID not found"))

                val response =
                    api.switchUser(
                        uuid = user.uuid,
                        pin = pin,
                        token = currentToken,
                        clientId = clientId,
                    )
                val body = response.body()

                if (response.isSuccessful && body != null && body.authToken.isNotEmpty()) {
                    // IMPORTANT: Before saving the new token, clear the caches
                    database.clearAllTables()

                    settingsDataStore.saveToken(body.authToken)
                    Result.success(true)
                } else {
                    Result.failure(AuthException("Failed to switch user: ${response.code()}"))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error switching user")
                Result.failure(NetworkException("Network error", e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} switching user")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Unknown error switching user")
                Result.failure(e)
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
                if (token.isNullOrBlank()) return Result.failure(Exception("Not authenticated"))

                val clientId = settingsDataStore.clientId.first()
                if (clientId.isNullOrBlank()) return Result.failure(Exception("Client ID not found"))

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
                    Result.failure(AuthException("Failed to get servers: ${response.code()}"))
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
                Timber.e(e, "Network error fetching servers (Fix with AI)")

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

                Result.failure(NetworkException(errorMessage, e))
            } catch (e: HttpException) {
                Timber.e(e, "HTTP error ${e.code()} fetching servers")
                Result.failure(e)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching servers")
                Result.failure(e)
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
