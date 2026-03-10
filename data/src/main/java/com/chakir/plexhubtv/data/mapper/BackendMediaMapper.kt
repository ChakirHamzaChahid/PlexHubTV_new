package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.database.computeMetadataScore
import com.chakir.plexhubtv.core.model.MediaPart
import com.chakir.plexhubtv.core.model.UnificationId
import com.chakir.plexhubtv.core.network.backend.BackendMediaItemDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendMediaMapper @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseMediaParts(raw: String?): List<MediaPart> {
        if (raw.isNullOrBlank() || raw == "[]" || raw == "null") return emptyList()
        return try {
            json.decodeFromString<List<MediaPart>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Maps a backend DTO to a MediaEntity for Room storage.
     * Key transformations:
     * - serverId: remapped from "xtream_X" → "backend_<backendId>"
     * - sourceServerId: stores the original backend serverId for API callbacks
     * - unificationId, historyGroupKey, displayRating: copied as-is (pre-computed by backend)
     */
    fun mapDtoToEntity(dto: BackendMediaItemDto, backendId: String): MediaEntity {
        val remappedServerId = "backend_$backendId"
        val unificationId = dto.unificationId.ifBlank {
            UnificationId.calculate(dto.imdbId, dto.tmdbId, dto.title, dto.year)
        }
        val historyGroupKey = dto.historyGroupKey.ifEmpty {
            unificationId.ifEmpty { "${dto.ratingKey}$remappedServerId" }
        }

        return MediaEntity(
            ratingKey = dto.ratingKey,
            serverId = remappedServerId,
            sourceServerId = dto.serverId,
            librarySectionId = dto.librarySectionId,
            title = dto.title,
            titleSortable = dto.titleSortable,
            filter = dto.filter,
            sortOrder = dto.sortOrder,
            pageOffset = dto.pageOffset,
            type = dto.type,
            thumbUrl = dto.thumbUrl,
            artUrl = dto.artUrl,
            year = dto.year,
            duration = dto.duration,
            summary = dto.summary,
            genres = dto.genres,
            contentRating = dto.contentRating,
            viewOffset = dto.viewOffset,
            viewCount = dto.viewCount,
            lastViewedAt = dto.lastViewedAt,
            parentTitle = dto.parentTitle,
            parentRatingKey = dto.parentRatingKey,
            parentIndex = dto.parentIndex,
            grandparentTitle = dto.grandparentTitle,
            grandparentRatingKey = dto.grandparentRatingKey,
            index = dto.index,
            parentThumb = dto.parentThumb,
            grandparentThumb = dto.grandparentThumb,
            guid = dto.guid,
            imdbId = dto.imdbId,
            tmdbId = dto.tmdbId,
            rating = dto.rating,
            audienceRating = dto.audienceRating,
            unificationId = unificationId,
            historyGroupKey = historyGroupKey,
            addedAt = dto.addedAt,
            updatedAt = dto.updatedAt,
            displayRating = dto.displayRating,
            scrapedRating = dto.scrapedRating,
            resolvedThumbUrl = dto.resolvedThumbUrl ?: dto.thumbUrl,
            resolvedArtUrl = dto.resolvedArtUrl ?: dto.artUrl,
            alternativeThumbUrls = dto.alternativeThumbUrls,
            mediaParts = parseMediaParts(dto.mediaParts),
            metadataScore = computeMetadataScore(
                summary = dto.summary,
                thumbUrl = dto.thumbUrl,
                imdbId = dto.imdbId,
                tmdbId = dto.tmdbId,
                year = dto.year,
                genres = dto.genres,
                serverId = remappedServerId,
                rating = dto.rating,
                audienceRating = dto.audienceRating,
                contentRating = dto.contentRating,
            ),
        )
    }
}
