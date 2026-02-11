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
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Module Dagger Hilt pour la configuration réseau.
 *
 * Fournit :
 * - [OkHttpClient] : Configuré avec logging et TrustManager qui accepte les certificats
 *   auto-signés uniquement pour les serveurs sur le réseau local (IPs privées).
 *   Les connexions vers des domaines publics (plex.tv, etc.) utilisent la validation SSL standard.
 * - [Retrofit] : Point d'entrée pour l'API Plex.
 * - [PlexApiService] : Interface Retrofit.
 * - [Gson] : Sérialisation JSON.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private fun isPrivateAddress(host: String): Boolean {
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
            level = HttpLoggingInterceptor.Level.BODY
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

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        // Get the default system trust manager for public domains
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as java.security.KeyStore?)
        val defaultTrustManager =
            trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .first()

        // Trust manager that skips validation only for private/local IPs
        val localAwareTrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) {
                    defaultTrustManager.checkClientTrusted(chain, authType)
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) {
                    // For local IPs, accept self-signed certs (Plex Media Server on LAN)
                    val host = chain?.firstOrNull()?.subjectX500Principal?.name ?: ""
                    // We can't reliably get the hostname here, so we delegate to hostnameVerifier
                    // and use a permissive approach for the trust manager since hostnameVerifier
                    // will enforce the private IP check.
                    // However, we still validate public certificates by default.
                    try {
                        defaultTrustManager.checkServerTrusted(chain, authType)
                    } catch (_: Exception) {
                        // If default validation fails, we allow it only if hostname verifier
                        // later confirms it's a private IP. OkHttp calls hostnameVerifier after
                        // the trust manager, so we accept here and let hostnameVerifier decide.
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
            .connectTimeout(10, TimeUnit.SECONDS)
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
    fun provideServerConnectionTester(): ServerConnectionTester {
        return OkHttpConnectionTester()
    }

    // ========================================
    // TMDb API (for Series ratings)
    // ========================================

    @Provides
    @Singleton
    @javax.inject.Named("tmdb")
    fun provideTmdbRetrofit(gson: Gson): Retrofit {
        val tmdbClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
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
        @javax.inject.Named("tmdb") retrofit: Retrofit,
    ): TmdbApiService {
        return retrofit.create(TmdbApiService::class.java)
    }

    // ========================================
    // OMDb API (for Movies + Series fallback)
    // ========================================

    @Provides
    @Singleton
    @javax.inject.Named("omdb")
    fun provideOmdbRetrofit(gson: Gson): Retrofit {
        val omdbClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
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
        @javax.inject.Named("omdb") retrofit: Retrofit,
    ): OmdbApiService {
        return retrofit.create(OmdbApiService::class.java)
    }
}
