package com.chakir.plexhubtv.data.repository

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

        override fun getChannels(): Flow<List<IptvChannel>> = _channels.asStateFlow()

        override suspend fun refreshChannels(url: String): Result<Unit> =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                    Timber.e(e, "Exception fetching M3U")
                    Result.failure(e)
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
            settingsRepository.saveIptvPlaylistUrl(url)
        }
    }
