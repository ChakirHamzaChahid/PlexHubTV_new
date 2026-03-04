package com.chakir.plexhubtv.core.network.backend

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val serviceCache = ConcurrentHashMap<String, BackendApiService>()

    fun getService(baseUrl: String): BackendApiService {
        val key = baseUrl.trimEnd('/') + "/"
        return serviceCache.getOrPut(key) {
            Retrofit.Builder()
                .baseUrl(key)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(BackendApiService::class.java)
        }
    }
}
