package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.di.ApplicationScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Module Dagger Hilt pour la configuration réseau.
 *
 * Fournit deux OkHttpClients avec des politiques SSL distinctes :
 *
 * - **Default [OkHttpClient]** : Utilise un [X509ExtendedTrustManager] hostname-aware qui
 *   accepte les certificats auto-signés UNIQUEMENT pour les IPs privées (LAN).
 *   Les connexions vers des domaines publics (plex.tv, etc.) utilisent la validation SSL standard.
 *   Utilisé par : PlexApiService (plex.tv + serveurs LAN), ConnectionTester, ImageLoader.
 *
 * - **@Named("public") [OkHttpClient]** : Validation SSL standard du système, aucun TrustManager
 *   custom. Utilisé par : TMDb API, OMDb API.
 *
 * - [Retrofit] : Point d'entrée pour l'API Plex.
 * - [PlexApiService] : Interface Retrofit.
 * - [Gson] : Sérialisation JSON.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    internal fun isPrivateAddress(host: String): Boolean {
        return try {
            if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
            val address = InetAddress.getByName(host)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        } catch (_: Exception) {
            false
        }
    }

    @Provides
    @Singleton
    fun provideGson(): Gson =
        GsonBuilder()
            .setLenient()
            .create()

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
        @ApplicationScope scope: CoroutineScope
    ): AuthInterceptor {
        return AuthInterceptor(settingsDataStore, scope)
    }

    /**
     * Public OkHttpClient with standard system SSL validation.
     * No custom TrustManager — certificates are validated normally.
     * Used by TMDb and OMDb APIs (external public services).
     */
    @Provides
    @Singleton
    @Named("public")
    fun providePublicOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Default OkHttpClient with hostname-aware TrustManager.
     * Uses [X509ExtendedTrustManager] to access the actual hostname during TLS handshake:
     * - Public domains (plex.tv, etc.): Standard certificate validation (rejects self-signed)
     * - Private IPs (192.168.x.x, 10.x.x.x, etc.): Accepts self-signed certs (Plex LAN servers)
     *
     * Used by PlexApiService (plex.tv cloud + LAN servers), ConnectionTester, ImageLoader.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as java.security.KeyStore?)
        val defaultTrustManager =
            trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: throw IllegalStateException("No X509TrustManager found in system TrustManagers")

        val localAwareTrustManager =
            object : X509ExtendedTrustManager() {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) {
                    defaultTrustManager.checkClientTrusted(chain, authType)
                }

                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                    socket: Socket,
                ) {
                    defaultTrustManager.checkClientTrusted(chain, authType)
                }

                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                    engine: SSLEngine,
                ) {
                    defaultTrustManager.checkClientTrusted(chain, authType)
                }

                // Legacy method without hostname: enforce strict validation (no catch)
                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) {
                    defaultTrustManager.checkServerTrusted(chain, authType)
                }

                // Socket variant: called by JSSE during TLS handshake, gives us hostname
                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                    socket: Socket,
                ) {
                    try {
                        if (defaultTrustManager is X509ExtendedTrustManager) {
                            defaultTrustManager.checkServerTrusted(chain, authType, socket)
                        } else {
                            defaultTrustManager.checkServerTrusted(chain, authType)
                        }
                    } catch (e: CertificateException) {
                        // Accept self-signed ONLY for private/local IPs
                        val hostname = (socket as? SSLSocket)?.handshakeSession?.peerHost
                        if (hostname != null && isPrivateAddress(hostname)) return
                        throw e
                    }
                }

                // SSLEngine variant: same logic for NIO-based connections
                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                    engine: SSLEngine,
                ) {
                    try {
                        if (defaultTrustManager is X509ExtendedTrustManager) {
                            defaultTrustManager.checkServerTrusted(chain, authType, engine)
                        } else {
                            defaultTrustManager.checkServerTrusted(chain, authType)
                        }
                    } catch (e: CertificateException) {
                        // Accept self-signed ONLY for private/local IPs
                        val hostname = engine.handshakeSession?.peerHost
                        if (hostname != null && isPrivateAddress(hostname)) return
                        throw e
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers
            }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(localAwareTrustManager), java.security.SecureRandom())

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .sslSocketFactory(sslContext.socketFactory, localAwareTrustManager)
            .hostnameVerifier { hostname, session ->
                // Accept any hostname for private/local IPs (self-signed Plex servers)
                if (isPrivateAddress(hostname)) return@hostnameVerifier true
                // For public domains, use standard hostname verification
                javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://plex.tv/") // Default base URL, overridden by @Url in service
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providePlexApiService(retrofit: Retrofit): PlexApiService {
        return retrofit.create(PlexApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideServerConnectionTester(okHttpClient: OkHttpClient): ServerConnectionTester {
        return OkHttpConnectionTester(okHttpClient)
    }

    // ========================================
    // TMDb API (for Series ratings)
    // ========================================

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(@Named("public") okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        val tmdbClient = okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/")
            .client(tmdbClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(
        @Named("tmdb") retrofit: Retrofit,
    ): TmdbApiService {
        return retrofit.create(TmdbApiService::class.java)
    }

    // ========================================
    // OMDb API (for Movies + Series fallback)
    // ========================================

    @Provides
    @Singleton
    @Named("omdb")
    fun provideOmdbRetrofit(@Named("public") okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        val omdbClient = okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(omdbClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideOmdbApiService(
        @Named("omdb") retrofit: Retrofit,
    ): OmdbApiService {
        return retrofit.create(OmdbApiService::class.java)
    }
}
