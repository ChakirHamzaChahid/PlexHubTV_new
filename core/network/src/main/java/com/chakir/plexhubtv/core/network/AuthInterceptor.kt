package com.chakir.plexhubtv.core.network

import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepteur OkHttp pour injecter automatiquement les tokens d'authentification Plex.
 *
 * Responsabilités :
 * - Ajoute `X-Plex-Token` (Token utilisateur) si disponible.
 * - Ajoute `X-Plex-Client-Identifier` (UUID de l'appareil).
 * - Ajoute les en-têtes standards de la plateforme Plex (OS, Version, App Name).
 *
 * Thread-safe: Uses runBlocking to read values synchronously on-demand,
 * avoiding coroutine leaks and ensuring consistent reads.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            // Read values synchronously on-demand (thread-safe)
            val token = runBlocking {
                settingsDataStore.plexToken.first()
            }
            val clientId = runBlocking {
                settingsDataStore.clientId.first()
            }

            if (clientId != null && originalRequest.header("X-Plex-Client-Identifier") == null) {
                requestBuilder.addHeader("X-Plex-Client-Identifier", clientId)
            }

            if (token != null && originalRequest.header("X-Plex-Token") == null && !originalRequest.url.toString().contains("X-Plex-Token")) {
                requestBuilder.addHeader("X-Plex-Token", token)
            }

            // Add common headers if not present
            if (originalRequest.header("Accept") == null) {
                requestBuilder.addHeader("Accept", "application/json")
            }

            // Standard Plex Headers
            requestBuilder.header("X-Plex-Platform", "Android")
            requestBuilder.header("X-Plex-Platform-Version", Build.VERSION.RELEASE) // Dynamic OS version
            requestBuilder.header("X-Plex-Provides", "player")
            requestBuilder.header("X-Plex-Product", "Plex for Android (TV)")
            requestBuilder.header("X-Plex-Version", "1.0.0") // App Version
            requestBuilder.header("X-Plex-Device", Build.MODEL) // Device Model
            requestBuilder.header("X-Plex-Model", Build.MODEL)

            return chain.proceed(requestBuilder.build())
        }
    }
