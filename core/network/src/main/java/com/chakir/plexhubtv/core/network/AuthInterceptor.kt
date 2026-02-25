package com.chakir.plexhubtv.core.network

import android.os.Build
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepteur OkHttp pour injecter automatiquement les tokens d'authentification Plex.
 *
 * Thread-safe: Uses AtomicReference for non-blocking reads.
 * Values are updated asynchronously from DataStore via CoroutineScope.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        @ApplicationScope private val scope: CoroutineScope,
        private val authEventBus: AuthEventBus,
    ) : Interceptor {

    private val cachedToken = AtomicReference<String?>(null)
    private val cachedClientId = AtomicReference<String?>(null)

    init {
        // Non-blocking: collect values in background
        settingsDataStore.plexToken
            .onEach { cachedToken.set(it) }
            .launchIn(scope)
        settingsDataStore.clientId
            .onEach { cachedClientId.set(it) }
            .launchIn(scope)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val token = cachedToken.get()     // ✅ Non-blocking O(1) read
        val clientId = cachedClientId.get() // ✅ Non-blocking O(1) read

        if (clientId != null && originalRequest.header("X-Plex-Client-Identifier") == null) {
            requestBuilder.addHeader("X-Plex-Client-Identifier", clientId)
        }

        if (token != null && originalRequest.header("X-Plex-Token") == null && !originalRequest.url.toString().contains("X-Plex-Token")) {
            requestBuilder.addHeader("X-Plex-Token", token)
        }

        if (originalRequest.header("Accept") == null) {
            requestBuilder.addHeader("Accept", "application/json")
        }

        requestBuilder.header("X-Plex-Platform", "Android")
        requestBuilder.header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        requestBuilder.header("X-Plex-Provides", "player")
        requestBuilder.header("X-Plex-Product", "Plex for Android (TV)")
        requestBuilder.header("X-Plex-Version", "1.0.0")
        requestBuilder.header("X-Plex-Device", Build.MODEL)
        requestBuilder.header("X-Plex-Model", Build.MODEL)

        val response = chain.proceed(requestBuilder.build())

        // Detect 401 and signal token invalidation
        if (response.code == 401) {
            authEventBus.emitTokenInvalid()
        }

        return response
    }
}
