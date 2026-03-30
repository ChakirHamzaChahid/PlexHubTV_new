package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.XtreamAccountDao
import com.chakir.plexhubtv.core.database.XtreamAccountEntity
import com.chakir.plexhubtv.core.datastore.SecurePreferencesManager
import com.chakir.plexhubtv.core.di.IoDispatcher
import com.chakir.plexhubtv.core.model.XtreamAccount
import com.chakir.plexhubtv.core.model.XtreamAccountStatus
import com.chakir.plexhubtv.core.network.xtream.XtreamApiClient
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamAccountRepositoryImpl @Inject constructor(
    private val dao: XtreamAccountDao,
    private val apiClient: XtreamApiClient,
    private val securePrefs: SecurePreferencesManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : XtreamAccountRepository {

    override fun observeAccounts(): Flow<List<XtreamAccount>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAccount(accountId: String): XtreamAccount? =
        withContext(ioDispatcher) {
            dao.getById(accountId)?.toDomain()
        }

    override suspend fun addAccount(
        baseUrl: String,
        port: Int,
        username: String,
        password: String,
        label: String,
    ): Result<XtreamAccount> = withContext(ioDispatcher) {
        runCatching {
            val service = apiClient.getService(baseUrl, port)
            val response = service.authenticate(username, password)
            val userInfo = response.userInfo
                ?: throw IllegalStateException("Authentication failed: no user info returned")

            if (userInfo.auth != 1) {
                throw IllegalStateException("Authentication failed: invalid credentials")
            }

            val accountId = generateAccountId(baseUrl, username)
            val passwordKey = "xtream_pwd_$accountId"

            // Store password in encrypted preferences
            securePrefs.putSecret(passwordKey, password)

            val entity = XtreamAccountEntity(
                id = accountId,
                label = label,
                baseUrl = baseUrl,
                port = port,
                username = username,
                passwordKey = passwordKey,
                status = userInfo.status ?: "Unknown",
                expirationDate = userInfo.expDate?.toLongOrNull(),
                maxConnections = userInfo.maxConnections?.toIntOrNull() ?: 1,
                allowedFormatsJson = userInfo.allowedOutputFormats
                    ?.joinToString(",") ?: "",
                serverUrl = response.serverInfo?.url,
                httpsPort = response.serverInfo?.httpsPort?.toIntOrNull(),
            )

            dao.upsert(entity)
            Timber.i("XTREAM [Account] Added account: $label ($accountId)")
            entity.toDomain()
        }
    }

    override suspend fun removeAccount(accountId: String) {
        withContext(ioDispatcher) {
            val entity = dao.getById(accountId)
            if (entity != null) {
                securePrefs.removeSecret(entity.passwordKey)
            }
            dao.delete(accountId)
            Timber.i("XTREAM [Account] Removed account: $accountId")
        }
    }

    override suspend fun refreshAccountStatus(accountId: String): Result<XtreamAccount> =
        withContext(ioDispatcher) {
            runCatching {
                val entity = dao.getById(accountId)
                    ?: throw IllegalArgumentException("Account $accountId not found")
                val password = securePrefs.getSecret(entity.passwordKey)
                    ?: throw IllegalStateException("Password not found for account $accountId")

                val service = apiClient.getService(entity.baseUrl, entity.port)
                val response = service.authenticate(entity.username, password)
                val userInfo = response.userInfo
                    ?: throw IllegalStateException("Failed to refresh account status")

                val updated = entity.copy(
                    status = userInfo.status ?: "Unknown",
                    expirationDate = userInfo.expDate?.toLongOrNull(),
                    maxConnections = userInfo.maxConnections?.toIntOrNull() ?: entity.maxConnections,
                    allowedFormatsJson = userInfo.allowedOutputFormats
                        ?.joinToString(",") ?: entity.allowedFormatsJson,
                    serverUrl = response.serverInfo?.url ?: entity.serverUrl,
                    httpsPort = response.serverInfo?.httpsPort?.toIntOrNull() ?: entity.httpsPort,
                )

                dao.upsert(updated)
                updated.toDomain()
            }
        }

    override suspend fun getDecryptedPassword(accountId: String): String? =
        withContext(ioDispatcher) {
            val entity = dao.getById(accountId) ?: return@withContext null
            securePrefs.getSecret(entity.passwordKey)
        }

    private fun generateAccountId(baseUrl: String, username: String): String {
        val input = "$baseUrl$username"
        val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return md5.joinToString("") { "%02x".format(it) }.take(8)
    }

    override suspend fun importFromBackend(
        backendId: String,
        accounts: List<XtreamAccount>,
    ) = withContext(ioDispatcher) {
        accounts.forEach { account ->
            val entity = XtreamAccountEntity(
                id = account.id,
                label = account.label,
                baseUrl = account.baseUrl,
                port = account.port,
                username = account.username,
                passwordKey = "",
                status = account.status.name,
                expirationDate = account.expirationDate,
                maxConnections = account.maxConnections,
                allowedFormatsJson = account.allowedFormats.joinToString(","),
                serverUrl = account.serverUrl,
                httpsPort = account.httpsPort,
                backendId = backendId,
            )
            dao.upsert(entity)
        }
        Timber.i("XTREAM [Account] Imported ${accounts.size} accounts from backend $backendId")
    }

    override suspend fun cleanupBackendAccounts(backendId: String) =
        withContext(ioDispatcher) {
            dao.deleteByBackendId(backendId)
            Timber.i("XTREAM [Account] Cleaned up accounts for backend $backendId")
        }

    private fun XtreamAccountEntity.toDomain(): XtreamAccount = XtreamAccount(
        id = id,
        label = label,
        baseUrl = baseUrl,
        port = port,
        username = username,
        status = XtreamAccountStatus.fromApiStatus(status),
        expirationDate = expirationDate,
        maxConnections = maxConnections,
        allowedFormats = allowedFormatsJson.split(",").filter { it.isNotBlank() },
        serverUrl = serverUrl,
        httpsPort = httpsPort,
        backendId = backendId,
    )
}
