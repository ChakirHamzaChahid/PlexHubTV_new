package com.chakir.plexhubtv.core.util

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper pour l'intégration avec le canal "Ma sélection" (Watch Next) d'Android TV.
 *
 * Permet d'ajouter ou mettre à jour la tuile "Continuer la lecture" sur l'écran d'accueil
 * du système Android TV, permettant à l'utilisateur de reprendre la lecture sans ouvrir l'app.
 */
@Singleton
class WatchNextHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Updates or adds a media item to the global "Watch Next" channel.
         * @param media The media item being watched.
         * @param positionMs Current playback position in ms.
         * @param durationMs Total duration in ms.
         */
        fun updateWatchNext(
            media: MediaItem,
            positionMs: Long,
            durationMs: Long,
        ) {
            // Skip if position is 0 or duration is invalid
            if (durationMs <= 0) return

            // Don't show in Watch Next if nearly finished (> 95%) or just started (< 1min)
            val progress = positionMs.toDouble() / durationMs.toDouble()
            if (progress > 0.95) {
                removeFromWatchNext(media)
                return
            }
            if (positionMs < 60000) return

            try {
                val internalId = "${media.serverId}_${media.ratingKey}"
                val existingProgramId = findExistingProgramId(internalId)

                val displayTitle =
                    if (media.type == MediaType.Episode && !media.grandparentTitle.isNullOrBlank()) {
                        "${media.grandparentTitle} - S${media.seasonIndex ?: 0}E${media.episodeIndex ?: 0} - ${media.title}"
                    } else {
                        media.title
                    }

                val builder =
                    WatchNextProgram.Builder()
                        .setType(getWatchNextType(media.type))
                        .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                        .setTitle(displayTitle)
                        .setDescription(media.summary ?: "")
                        .setPosterArtUri(Uri.parse(media.thumbUrl ?: ""))
                        .setDurationMillis(durationMs.toInt())
                        .setLastPlaybackPositionMillis(positionMs.toInt())
                        .setInternalProviderId(internalId)
                        // Intent uri for deep linking: plexhub://play/{ratingKey}?serverId={serverId}
                        .setIntentUri(Uri.parse("plexhub://play/${media.ratingKey}?serverId=${media.serverId}"))

                if (media.type == MediaType.Episode) {
                    builder.setSeasonNumber(media.seasonIndex ?: 0)
                        .setEpisodeNumber(media.episodeIndex ?: 0)
                }

                val program = builder.build()

                if (existingProgramId != -1L) {
                    // Update existing program
                    context.contentResolver.update(
                        TvContractCompat.buildWatchNextProgramUri(existingProgramId),
                        program.toContentValues(),
                        null,
                        null,
                    )
                } else {
                    // Insert new program
                    context.contentResolver.insert(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        program.toContentValues(),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating Watch Next channel")
            }
        }

        /**
         * Removes a media item from the "Watch Next" channel.
         */
        fun removeFromWatchNext(media: MediaItem) {
            try {
                val internalId = "${media.serverId}_${media.ratingKey}"
                val existingProgramId = findExistingProgramId(internalId)
                if (existingProgramId != -1L) {
                    context.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(existingProgramId),
                        null,
                        null,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing from Watch Next")
            }
        }

        private fun findExistingProgramId(internalId: String): Long {
            return try {
                val cursor =
                    context.contentResolver.query(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        arrayOf(TvContractCompat.WatchNextPrograms._ID),
                        "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                        arrayOf(internalId),
                        null,
                    )
                cursor?.use {
                    if (it.moveToFirst()) {
                        return it.getLong(0)
                    }
                }
                -1L
            } catch (e: Exception) {
                -1L
            }
        }

        private fun getWatchNextType(type: MediaType): Int {
            return when (type) {
                MediaType.Movie -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                MediaType.Episode -> TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                else -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
            }
        }
    }
