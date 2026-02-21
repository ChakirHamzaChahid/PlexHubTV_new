package com.chakir.plexhubtv.data.util

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android TV Channels for PlexHubTV.
 *
 * Handles creation, update, and deletion of the "Continue Watching" channel
 * displayed in the Android TV launcher. Works alongside WatchNextHelper
 * (which handles single-item Watch Next).
 */
@Singleton
class TvChannelManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onDeckRepository: OnDeckRepository,
    private val settingsDataStore: SettingsDataStore
) : com.chakir.plexhubtv.domain.service.TvChannelManager {
    companion object {
        const val CHANNEL_NAME = "PlexHubTV - Continue Watching"
        const val CHANNEL_DESCRIPTION = "Resume watching your favorite content"
        const val MAX_PROGRAMS = 15
    }

    /**
     * Creates the TV Channel if it doesn't exist.
     * @return Channel ID or null if creation failed or disabled
     */
    override suspend fun createChannelIfNeeded(): Long? {
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Creation skipped (disabled in settings)")
            return null
        }

        try {
            // Check if channel already exists
            val existingId = findExistingChannelId()
            if (existingId != null) {
                Timber.d("TV Channel: Already exists with ID=$existingId")
                return existingId
            }

            // Create new channel
            val channel = Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(CHANNEL_NAME)
                .setDescription(CHANNEL_DESCRIPTION)
                .setAppLinkIntentUri(Uri.parse("plexhub://home"))
                .build()

            val channelUri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )

            val channelId = channelUri?.lastPathSegment?.toLongOrNull()
            if (channelId != null) {
                // Request to make channel visible (user can still hide it manually)
                TvContractCompat.requestChannelBrowsable(context, channelId)
                Timber.i("TV Channel: Created successfully with ID=$channelId")
            } else {
                Timber.w("TV Channel: Creation failed (null ID)")
            }

            return channelId
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Creation failed")
            return null
        }
    }

    /**
     * Updates the Continue Watching channel with latest On Deck items.
     * - Fetches from OnDeckRepository
     * - Deletes old programs
     * - Inserts new programs
     */
    override suspend fun updateContinueWatching() {
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Update skipped (disabled in settings)")
            return
        }

        try {
            val channelId = createChannelIfNeeded() ?: run {
                Timber.w("TV Channel: Update skipped (no channel ID)")
                return
            }

            // Fetch latest On Deck items
            val mediaItems = onDeckRepository.getUnifiedOnDeck().first().take(MAX_PROGRAMS)

            if (mediaItems.isEmpty()) {
                Timber.w("TV Channel: No content available, clearing channel (ID=$channelId)")
                deleteAllPrograms(channelId)
                return
            }

            // Delete old programs
            deleteAllPrograms(channelId)

            // Insert new programs
            mediaItems.forEach { media ->
                insertProgram(media, channelId)
            }

            Timber.i("TV Channel: Updated with ${mediaItems.size} programs")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Update failed")
        }
    }

    /**
     * Deletes the channel and all its programs.
     */
    override suspend fun deleteChannel() {
        try {
            val channelId = findExistingChannelId() ?: run {
                Timber.d("TV Channel: Delete skipped (channel not found)")
                return
            }

            // Delete all programs first
            deleteAllPrograms(channelId)

            // Delete channel
            context.contentResolver.delete(
                TvContractCompat.buildChannelUri(channelId),
                null,
                null
            )

            Timber.i("TV Channel: Deleted successfully (ID=$channelId)")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Delete failed")
        }
    }

    /**
     * Finds the existing channel ID by display name.
     * @return Channel ID or null if not found
     */
    private fun findExistingChannelId(): Long? {
        return try {
            val cursor = context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                arrayOf(TvContractCompat.Channels._ID),
                "${TvContractCompat.Channels.COLUMN_DISPLAY_NAME} = ?",
                arrayOf(CHANNEL_NAME),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to find existing channel")
            null
        }
    }

    /**
     * Deletes all programs from the given channel.
     */
    private fun deleteAllPrograms(channelId: Long) {
        try {
            val deletedCount = context.contentResolver.delete(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID} = ?",
                arrayOf(channelId.toString())
            )
            Timber.d("TV Channel: Deleted $deletedCount old programs")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to delete programs")
        }
    }

    /**
     * Inserts a single program into the channel.
     */
    private fun insertProgram(media: MediaItem, channelId: Long) {
        try {
            // Validate required fields for deep linking
            if (media.ratingKey.isBlank() || media.serverId.isBlank()) {
                Timber.w("TV Channel: Skipping program with invalid ID: ${media.title}")
                return
            }

            val program = createProgram(media, channelId)
            context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues()
            )
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to insert program for ${media.title}")
        }
    }

    /**
     * Creates a PreviewProgram from MediaItem.
     */
    @Suppress("RestrictedApi")
    private fun createProgram(media: MediaItem, channelId: Long): PreviewProgram {
        val displayTitle = if (media.type == MediaType.Episode && !media.grandparentTitle.isNullOrBlank()) {
            "${media.grandparentTitle} - S${media.seasonIndex ?: 0}E${media.episodeIndex ?: 0} - ${media.title}"
        } else {
            media.title
        }

        val builder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(
                when (media.type) {
                    MediaType.Movie -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                    MediaType.Episode -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                    else -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                }
            )
            .setTitle(displayTitle)
            .setDescription(media.summary ?: "")
            .setPosterArtUri(Uri.parse(media.thumbUrl ?: ""))
            .setIntentUri(Uri.parse("plexhub://play/${media.ratingKey}?serverId=${media.serverId}"))
            .setInternalProviderId("${media.serverId}_${media.ratingKey}")

        // Add duration and position if available (with safe conversion)
        media.durationMs?.let {
            val safeDuration = it.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            builder.setDurationMillis(safeDuration)
        }
        if (media.viewOffset > 0) {
            val safeOffset = media.viewOffset.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            builder.setLastPlaybackPositionMillis(safeOffset)
        }

        return builder.build()
    }
}
