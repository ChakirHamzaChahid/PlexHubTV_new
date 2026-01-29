package com.chakir.plexhubtv.core.network

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class AuthInterceptor @Inject constructor(
    private val settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore
) : Interceptor {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
    @Volatile
    private var cachedToken: String? = null
    
    @Volatile
    private var cachedClientId: String? = null

    init {
        scope.launch {
            settingsDataStore.plexToken.collect { cachedToken = it }
        }
        scope.launch {
            settingsDataStore.clientId.collect { cachedClientId = it }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val token = cachedToken
        val clientId = cachedClientId

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
