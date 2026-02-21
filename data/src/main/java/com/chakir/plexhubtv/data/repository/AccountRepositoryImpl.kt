package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.common.safeApiCall
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.AppError
import com.chakir.plexhubtv.core.model.PlexHomeUser
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Implémentation du repository pour la gestion des comptes utilisateurs (Plex Home).
 * Gère le changement d'utilisateur (User Switch) et la récupération des membres de la maison.
 */
class AccountRepositoryImpl
    @Inject
    constructor(
        private val api: PlexApiService,
        private val settingsDataStore: SettingsDataStore,
        private val userMapper: com.chakir.plexhubtv.data.mapper.UserMapper,
    ) : AccountRepository {
        override suspend fun getHomeUsers(): Result<List<PlexHomeUser>> {
            val token = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not logged in"))

            return safeApiCall("getHomeUsers") {
                val clientId = settingsDataStore.clientId.first() ?: ""
                val response = api.getHomeUsers(token, clientId)

                if (!response.isSuccessful) {
                    throw AppError.Network.ServerError("API Error: ${response.code()}")
                }

                response.body()?.map { userMapper.mapDtoToDomain(it) } ?: emptyList()
            }
        }

        override suspend fun switchUser(
            user: PlexHomeUser,
            pin: String?,
        ): Result<Boolean> {
            val currentToken = settingsDataStore.plexToken.first()
                ?: return Result.failure(AppError.Auth.InvalidToken("Not logged in"))

            return safeApiCall("switchUser") {
                val clientId = settingsDataStore.clientId.first() ?: ""
                val response = api.switchUser(user.uuid, pin, currentToken, clientId)

                if (!response.isSuccessful) {
                    throw AppError.Auth.InvalidToken("PIN likely incorrect or API error: ${response.code()}")
                }

                val body = response.body()
                    ?: throw AppError.Network.ServerError("Empty switch response")

                settingsDataStore.saveToken(body.authToken)
                settingsDataStore.saveUser(user.uuid, user.displayName)
                true
            }
        }

        override fun observeCurrentUser(): Flow<PlexHomeUser?> {
            return combine(
                settingsDataStore.currentUserUuid,
                settingsDataStore.currentUserName,
            ) { uuid, name ->
                if (uuid != null && name != null) {
                    PlexHomeUser(
                        id = 0, // Not needed for identification
                        uuid = uuid,
                        title = name,
                        username = null,
                        email = null,
                        friendlyName = name,
                        thumb = "", // UI handles fallback
                        hasPassword = false,
                        restricted = false,
                        admin = false,
                        guest = false,
                        protected = false,
                    )
                } else {
                    null
                }
            }
        }

        override suspend fun getCurrentUser(): PlexHomeUser? {
            return observeCurrentUser().first()
        }

        override suspend fun logout() {
            settingsDataStore.clearToken()
            settingsDataStore.clearUser()
        }

        override suspend fun refreshProfile() {
            // Fetch fresh user data and update store
        }
    }
