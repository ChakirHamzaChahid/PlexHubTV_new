package com.chakir.plexhubtv.feature.player.controller

import android.content.Context
import com.chakir.plexhubtv.core.network.ApiKeyManager
import com.chakir.plexhubtv.core.network.OpenSubtitlesApiService
import com.chakir.plexhubtv.core.network.model.OpenSubtitleResult
import com.chakir.plexhubtv.core.network.model.OpenSubtitlesDownloadRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SubtitleSearchResult(
    val fileId: Long,
    val language: String,
    val release: String,
    val downloadCount: Int,
    val rating: Double,
    val hearingImpaired: Boolean,
    val fileName: String,
)

@Singleton
class SubtitleSearchService
    @Inject
    constructor(
        private val api: OpenSubtitlesApiService,
        private val apiKeyManager: ApiKeyManager,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun search(
            query: String,
            language: String? = null,
            seasonNumber: Int? = null,
            episodeNumber: Int? = null,
        ): Result<List<SubtitleSearchResult>> {
            val apiKey = apiKeyManager.getOpenSubtitlesApiKey()
            if (apiKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("OpenSubtitles API key not configured"))
            }

            return try {
                val type = if (seasonNumber != null) "episode" else null
                val response = api.search(
                    apiKey = apiKey,
                    query = query,
                    languages = language,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    type = type,
                )

                val results = response.data.mapNotNull { result -> mapResult(result) }
                Result.success(results)
            } catch (e: Exception) {
                Timber.e(e, "OpenSubtitles search failed for query: $query")
                Result.failure(e)
            }
        }

        suspend fun download(fileId: Long): Result<File> {
            val apiKey = apiKeyManager.getOpenSubtitlesApiKey()
            if (apiKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("OpenSubtitles API key not configured"))
            }

            return try {
                val response = api.download(
                    apiKey = apiKey,
                    request = OpenSubtitlesDownloadRequest(fileId = fileId),
                )

                val link = response.link
                    ?: return Result.failure(IllegalStateException("No download link returned"))

                val fileName = response.fileName ?: "subtitle_$fileId.srt"
                val subtitleFile = File(context.cacheDir, "subtitles/$fileName")
                subtitleFile.parentFile?.mkdirs()

                // Download the actual subtitle file
                val url = java.net.URL(link)
                url.openStream().use { input ->
                    subtitleFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                Timber.d("Subtitle downloaded: ${subtitleFile.absolutePath} (remaining: ${response.remaining})")
                Result.success(subtitleFile)
            } catch (e: Exception) {
                Timber.e(e, "OpenSubtitles download failed for fileId: $fileId")
                Result.failure(e)
            }
        }

        private fun mapResult(result: OpenSubtitleResult): SubtitleSearchResult? {
            val file = result.attributes.files.firstOrNull() ?: return null
            return SubtitleSearchResult(
                fileId = file.fileId,
                language = result.attributes.language ?: "unknown",
                release = result.attributes.release ?: file.fileName ?: "Unknown",
                downloadCount = result.attributes.downloadCount,
                rating = result.attributes.ratings,
                hearingImpaired = result.attributes.hearingImpaired,
                fileName = file.fileName ?: "subtitle.srt",
            )
        }
    }
