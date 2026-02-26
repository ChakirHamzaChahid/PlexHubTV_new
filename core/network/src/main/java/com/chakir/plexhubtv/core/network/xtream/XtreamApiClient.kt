package com.chakir.plexhubtv.core.network.xtream

import com.chakir.plexhubtv.core.model.XtreamAccount
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val serviceCache = ConcurrentHashMap<String, XtreamApiService>()

    fun getService(baseUrl: String, port: Int): XtreamApiService {
        val normalizedBase = buildBaseUrl(baseUrl, port)
        return serviceCache.getOrPut(normalizedBase) {
            Retrofit.Builder()
                .baseUrl(normalizedBase)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(XtreamApiService::class.java)
        }
    }

    /**
     * Build a direct-play movie URL.
     * Format: `http://host:port/movie/username/password/streamId.ext`
     */
    fun buildMovieUrl(
        account: XtreamAccount,
        username: String,
        password: String,
        streamId: Int,
        extension: String,
    ): String {
        val base = buildBaseUrl(account.baseUrl, account.port)
        return "${base}movie/$username/$password/$streamId.$extension"
    }

    /**
     * Build a direct-play episode URL.
     * Format: `http://host:port/series/username/password/episodeId.ext`
     */
    fun buildEpisodeUrl(
        account: XtreamAccount,
        username: String,
        password: String,
        episodeId: String,
        extension: String,
    ): String {
        val base = buildBaseUrl(account.baseUrl, account.port)
        return "${base}series/$username/$password/$episodeId.$extension"
    }

    private fun buildBaseUrl(baseUrl: String, port: Int): String {
        val url = baseUrl.trimEnd('/')
        return "$url:$port/"
    }
}
