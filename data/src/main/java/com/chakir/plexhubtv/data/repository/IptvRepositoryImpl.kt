package com.chakir.plexhubtv.data.repository

import android.net.Uri
import com.chakir.plexhubtv.core.model.IptvChannel
import com.chakir.plexhubtv.data.iptv.M3uParser
import com.chakir.plexhubtv.domain.repository.IptvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IptvRepositoryImpl
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val settingsRepository: com.chakir.plexhubtv.domain.repository.SettingsRepository,
    ) : IptvRepository {
        private val _channels = MutableStateFlow<List<IptvChannel>>(emptyList())

        private companion object {
            val ALLOWED_M3U_SCHEMES = setOf("http", "https")
        }

        override fun getChannels(): Flow<List<IptvChannel>> = _channels.asStateFlow()

        override suspend fun refreshChannels(url: String): Result<Unit> =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val scheme = Uri.parse(url).scheme?.lowercase()
                if (scheme == null || scheme !in ALLOWED_M3U_SCHEMES) {
                    Timber.e("Rejected M3U URL with disallowed scheme '$scheme': ${url.take(80)}")
                    return@withContext Result.failure(
                        IOException("Invalid playlist URL: only http and https are allowed")
                    )
                }

                return@withContext try {
                    Timber.d("Fetching M3U from: $url")
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Timber.e("Failed to fetch M3U: ${response.code}")
                        return@withContext Result.failure(IOException("Failed to download M3U: ${response.code}"))
                    }

                    val inputStream = response.body?.byteStream()
                    if (inputStream != null) {
                        Timber.d("Parsing M3U...")
                        val parsed = M3uParser.parse(inputStream)
                        Timber.d("Parsed ${parsed.size} channels")
                        _channels.value = parsed
                        Result.success(Unit)
                    } else {
                        Timber.e("Empty response body")
                        Result.failure(IOException("Empty response body"))
                    }
                } catch (e: Exception) {
                    val errorMessage = when (e) {
                        is java.net.UnknownHostException ->
                            "Unable to connect to IPTV server. Please check the M3U URL or your internet connection."
                        is java.net.SocketTimeoutException ->
                            "Connection timeout. The IPTV server is not responding."
                        is IOException ->
                            "Network error: ${e.message ?: "Unable to download playlist"}"
                        else ->
                            "Failed to load IPTV channels: ${e.message ?: "Unknown error"}"
                    }
                    Timber.e(e, "Exception fetching M3U (Fix with AI)")
                    Result.failure(IOException(errorMessage, e))
                }
            }

        override suspend fun getM3uUrl(): String? {
            val savedUrl = settingsRepository.iptvPlaylistUrl.firstOrNull()
            if (!savedUrl.isNullOrBlank()) return savedUrl

            // Fallback to BuildConfig if available
            val buildConfigUrl = com.chakir.plexhubtv.data.BuildConfig.IPTV_PLAYLIST_URL
            return if (buildConfigUrl.isNotBlank()) buildConfigUrl else null
        }

        override suspend fun saveM3uUrl(url: String) {
            val scheme = Uri.parse(url).scheme?.lowercase()
            if (scheme == null || scheme !in ALLOWED_M3U_SCHEMES) {
                Timber.e("Refused to save M3U URL with disallowed scheme '$scheme'")
                return
            }
            settingsRepository.saveIptvPlaylistUrl(url)
        }
    }
