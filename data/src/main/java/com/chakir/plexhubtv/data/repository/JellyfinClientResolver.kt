package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.JellyfinServerDao
import com.chakir.plexhubtv.core.datastore.SecurePreferencesManager
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.model.SourcePrefix
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinApiService
import com.chakir.plexhubtv.core.network.jellyfin.JellyfinClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs and caches [JellyfinClient] instances from stored credentials.
 * Parallel to [ServerClientResolver] which does the same for Plex servers.
 *
 * Also maintains a baseUrl->token registry for image authentication:
 * Coil's JellyfinImageInterceptor uses [findTokenForUrl] to add
 * `Authorization: MediaBrowser Token="..."` headers to image requests,
 * following the standard Jellyfin auth pattern (like Wholphin).
 *
 * Cache is keyed by Jellyfin server ID (unprefixed). Invalidation happens on
 * server removal or token rotation.
 */
@Singleton
class JellyfinClientResolver @Inject constructor(
    private val dao: JellyfinServerDao,
    private val api: JellyfinApiService,
    private val securePrefs: SecurePreferencesManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cache = ConcurrentHashMap<String, JellyfinClient>()

    /** baseUrl (trimmed) -> access token. Populated eagerly from DB and on getClient(). */
    private val serverTokens = ConcurrentHashMap<String, String>()

    init {
        // Eagerly load server tokens so Coil can authenticate images on cold start
        scope.launch { refreshTokenCache() }
    }

    private suspend fun refreshTokenCache() {
        try {
            dao.getAll().forEach { server ->
                val token = securePrefs.getSecret("jellyfin_token_${server.id}")
                if (!token.isNullOrBlank()) {
                    serverTokens[server.baseUrl.trimEnd('/')] = token
                }
            }
            Timber.d("JellyfinClientResolver: Loaded ${serverTokens.size} server tokens for image auth")
        } catch (e: Exception) {
            Timber.e(e, "JellyfinClientResolver: Failed to load server tokens")
        }
    }

    /**
     * Find the access token for a URL that starts with a known Jellyfin server baseUrl.
     * Used by JellyfinImageInterceptor for Coil image authentication.
     */
    fun findTokenForUrl(url: String): String? =
        serverTokens.entries.firstOrNull { (baseUrl, _) -> url.startsWith(baseUrl) }?.value

    /**
     * Resolve a [JellyfinClient] for the given serverId.
     * Accepts both prefixed ("jellyfin_xxx") and unprefixed ("xxx") IDs.
     */
    suspend fun getClient(serverId: String): JellyfinClient? {
        val rawId = if (serverId.startsWith(SourcePrefix.JELLYFIN)) {
            serverId.removePrefix(SourcePrefix.JELLYFIN)
        } else {
            serverId
        }

        // Check cache first
        cache[rawId]?.let { return it }

        // Build from DB + secure storage
        val entity = dao.getById(rawId) ?: run {
            Timber.e("JellyfinClientResolver: No server entity in DB for rawId=$rawId")
            return null
        }

        val token = securePrefs.getSecret("jellyfin_token_$rawId")
        if (token.isNullOrBlank()) {
            Timber.e("JellyfinClientResolver: No token in SecurePrefs for key=jellyfin_token_$rawId")
            return null
        }

        val client = JellyfinClient(
            serverId = rawId,
            serverName = entity.name,
            baseUrl = entity.baseUrl,
            userId = entity.userId,
            accessToken = token,
            api = api,
        )

        cache[rawId] = client
        // Also register for image auth (Coil interceptor)
        serverTokens[entity.baseUrl.trimEnd('/')] = token
        Timber.d("JellyfinClientResolver: client built and cached for ${entity.name} (${entity.baseUrl})")
        return client
    }

    /** Invalidate cached client (e.g., after token refresh or server removal). */
    fun invalidate(serverId: String) {
        val rawId = serverId.removePrefix(SourcePrefix.JELLYFIN)
        val client = cache.remove(rawId)
        // Also clean up image auth registry
        client?.let { serverTokens.remove(it.baseUrl.trimEnd('/')) }
    }

    /** Invalidate all cached clients. */
    fun invalidateAll() {
        cache.clear()
        serverTokens.clear()
    }
}
