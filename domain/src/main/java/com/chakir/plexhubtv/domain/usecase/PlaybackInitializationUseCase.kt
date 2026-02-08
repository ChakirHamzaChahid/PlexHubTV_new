package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.repository.DownloadsRepository
import com.chakir.plexhubtv.domain.repository.MediaRepository
import javax.inject.Inject

data class PlaybackData(
    val videoUrl: String,
    val isOffline: Boolean,
    val hasExternalSubtitles: Boolean = false,
    val externalSubtitleUrls: List<String> = emptyList(),
)

/**
 * Use case for initializing playback by determining the best video source.
 * Handles both online streaming and offline downloaded content.
 */

/**
 * Cas d'utilisation critique pour l'initialisation de la lecture.
 *
 * Responsabilités :
 * 1. Vérifie si le média est disponible en local (téléchargement terminé).
 *    Si oui, force la lecture locale (Mode Offline).
 * 2. Sinon, génère l'URL de streaming direct vers le serveur Plex,
 *    en incluant le token d'authentification (X-Plex-Token).
 */
class PlaybackInitializationUseCase
    @Inject
    constructor(
        private val downloadsRepository: DownloadsRepository,
        private val mediaRepository: MediaRepository,
    ) {
        /**
         * Get playback data for a media item, preferring offline downloads if available
         */
        suspend fun getPlaybackData(
            media: MediaItem,
            preferOffline: Boolean = true,
        ): Result<PlaybackData> {
            return try {
                // Step 1: Check for downloaded version if offline preferred
                if (preferOffline) {
                    downloadsRepository.getDownloadedItem(media.ratingKey).getOrNull()?.let { download ->
                        if (download.isCompleted) {
                            return Result.success(
                                PlaybackData(
                                    videoUrl = download.filePath ?: "",
                                    isOffline = true,
                                ),
                            )
                        }
                    }
                }

                // Step 2: Build streaming URL for online playback
                val baseUrl = media.baseUrl ?: return Result.failure(Exception("No base URL available"))
                val token = media.accessToken ?: return Result.failure(Exception("No access token"))

                // Construct direct play URL
                val streamUrl =
                    buildStreamUrl(
                        baseUrl = baseUrl,
                        ratingKey = media.ratingKey,
                        token = token,
                        mediaIndex = 0, // TODO: Support media version selection
                    )

                Result.success(
                    PlaybackData(
                        videoUrl = streamUrl,
                        isOffline = false,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        private fun buildStreamUrl(
            baseUrl: String,
            ratingKey: String,
            token: String,
            mediaIndex: Int,
        ): String {
            // Build direct play URL
            //  In real implementation, this would handle:
            // - Transcoding vs direct play decision
            // - Quality selection
            // - Media part/stream selection
            return "$baseUrl/library/metadata/$ratingKey/file?X-Plex-Token=$token"
        }
    }
