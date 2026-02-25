package com.chakir.plexhubtv.data.mapper

import com.chakir.plexhubtv.core.common.StringNormalizer
import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.core.network.model.MetadataDTO
import com.chakir.plexhubtv.core.network.model.StreamDTO
import com.chakir.plexhubtv.core.util.ContentRatingHelper
import com.chakir.plexhubtv.core.util.getOptimizedImageUrl
import javax.inject.Inject

/**
 * Mapper central pour les objets Média.
 * Assure la conversion bidirectionnelle entre :
 * - DTO (API Network)
 * - Entity (Base de données Room)
 * - Domain Model (Utilisé par l'UI)
 *
 * Gère aussi la normalisation des URLs d'images (ajout du token, optimisation).
 */
class MediaMapper
    @Inject
    constructor() {
        // --- Network to Domain ---
        fun mapDtoToDomain(
            dto: MetadataDTO,
            serverId: String,
            baseUrl: String,
            accessToken: String?,
        ): MediaItem {
            val rawThumb = dto.thumb?.let { "$baseUrl$it?X-Plex-Token=$accessToken" }
            val rawArt = dto.art?.let { "$baseUrl$it?X-Plex-Token=$accessToken" }

            return MediaItem(
                id = "${serverId}_${dto.ratingKey}",
                ratingKey = dto.ratingKey,
                serverId = serverId,
                unificationId = calculateUnificationId(dto),
                title = dto.title,
                type = mapType(dto.type),
                thumbUrl = getOptimizedImageUrl(rawThumb, width = 300, height = 450) ?: rawThumb,
                artUrl = getOptimizedImageUrl(rawArt, width = 1280, height = 720) ?: rawArt,
                summary = dto.summary,
                year = dto.year,
                durationMs = dto.duration,
                viewOffset = dto.viewOffset ?: 0,
                viewCount = dto.viewCount?.toLong() ?: 0,
                isWatched = (dto.viewCount ?: 0) > 0,
                // Rich Metadata
                guid = dto.guid,
                imdbId = extractImdbId(dto),
                tmdbId = extractTmdbId(dto),
                studio = dto.studio,
                contentRating = ContentRatingHelper.normalize(dto.contentRating),
                audienceRating = dto.audienceRating,
                rating = dto.rating,
                addedAt = dto.addedAt,
                updatedAt = dto.updatedAt,
                tagline = dto.tagline,
                genres = dto.genres?.map { it.tag } ?: emptyList(),
                // Hierarchy
                parentTitle = dto.parentTitle,
                grandparentTitle = dto.grandparentTitle,
                grandparentThumb = dto.grandparentThumb?.let { "$baseUrl$it?X-Plex-Token=$accessToken" },
                parentThumb = dto.parentThumb?.let { "$baseUrl$it?X-Plex-Token=$accessToken" },
                parentIndex = dto.parentIndex,
                parentRatingKey = dto.parentRatingKey,
                episodeIndex = dto.index,
                seasonIndex = dto.parentIndex,
                childCount = dto.childCount,
                // Grandparent
                grandparentRatingKey = dto.grandparentRatingKey,
                // Parts & Streams
                mediaParts =
                    dto.media?.flatMap { mediaDto ->
                        mediaDto.parts?.map { partDto ->
                            com.chakir.plexhubtv.core.model.MediaPart(
                                id = partDto.id,
                                key = partDto.key,
                                duration = partDto.duration,
                                file = partDto.file,
                                size = partDto.size,
                                container = partDto.container,
                                streams =
                                    partDto.streams?.map { streamDto ->
                                        mapStream(streamDto)
                                    } ?: emptyList(),
                            )
                        } ?: emptyList()
                    } ?: emptyList(),
                // Cast
                role =
                    dto.roles?.map { roleDto ->
                        com.chakir.plexhubtv.core.model.CastMember(
                            id = roleDto.id,
                            filter = roleDto.filter,
                            role = roleDto.role,
                            tag = roleDto.tag,
                            thumb = roleDto.thumb?.let { "$baseUrl$it?X-Plex-Token=$accessToken" },
                        )
                    } ?: emptyList(),
                baseUrl = baseUrl,
                accessToken = accessToken,
                chapters =
                    dto.chapters?.map {
                        com.chakir.plexhubtv.core.model.Chapter(
                            title = it.title ?: "Chapter",
                            startTime = it.startTimeOffset ?: 0L,
                            endTime = it.endTimeOffset ?: 0L,
                            thumbUrl = it.thumb?.let { t -> "$baseUrl$t?X-Plex-Token=$accessToken" },
                        )
                    } ?: emptyList(),
                markers =
                    dto.markers?.map {
                        com.chakir.plexhubtv.core.model.Marker(
                            title = it.type ?: "Marker",
                            type = it.type ?: "unknown",
                            startTime = it.startTimeOffset ?: 0L,
                            endTime = it.endTimeOffset ?: 0L,
                        )
                    } ?: emptyList(),
            )
        }

        private fun mapStream(dto: StreamDTO): com.chakir.plexhubtv.core.model.MediaStream {
            return when (dto.streamType) {
                1 -> {
                    val isHdr =
                        dto.colorSpace == "bt2020" ||
                            dto.colorTransfer == "smpte2084" ||
                            dto.colorTransfer == "arib-std-b67" ||
                            dto.profile?.contains("Main 10", ignoreCase = true) == true

                    com.chakir.plexhubtv.core.model.VideoStream(
                        id = dto.id, index = dto.index, language = dto.language, languageCode = dto.languageCode,
                        title = dto.title, displayTitle = dto.displayTitle, codec = dto.codec, selected = dto.selected,
                        width = dto.width, height = dto.height, bitrate = dto.bitrate, hasHDR = isHdr,
                    )
                }
                2 ->
                    com.chakir.plexhubtv.core.model.AudioStream(
                        id = dto.id, index = dto.index, language = dto.language, languageCode = dto.languageCode,
                        title = dto.title, displayTitle = dto.displayTitle, codec = dto.codec, selected = dto.selected,
                        channels = dto.channels,
                    )
                3 ->
                    com.chakir.plexhubtv.core.model.SubtitleStream(
                        id = dto.id, index = dto.index, language = dto.language, languageCode = dto.languageCode,
                        title = dto.title, displayTitle = dto.displayTitle, codec = dto.codec, selected = dto.selected,
                        forced = dto.forced, key = dto.key,
                    )
                else ->
                    com.chakir.plexhubtv.core.model.UnknownStream(
                        dto.id,
                        dto.index,
                        dto.language,
                        dto.languageCode,
                        dto.title,
                        dto.displayTitle,
                        dto.codec,
                        dto.selected,
                    )
            }
        }

        // --- Network to Entity ---
        fun mapDtoToEntity(
            dto: MetadataDTO,
            serverId: String,
            libraryKey: String,
        ): MediaEntity {
            return MediaEntity(
                ratingKey = dto.ratingKey,
                serverId = serverId,
                librarySectionId = libraryKey,
                title = dto.title,
                titleSortable = StringNormalizer.normalizeForSorting(dto.title),
                // PHASE 2: Pre-calculate unificationId
                unificationId = calculateUnificationId(dto),
                guid = dto.guid,
                imdbId = extractImdbId(dto),
                tmdbId = extractTmdbId(dto),
                type = dto.type,
                thumbUrl = dto.thumb,
                artUrl = dto.art,
                summary = dto.summary,
                year = dto.year,
                duration = dto.duration,
                viewOffset = dto.viewOffset ?: 0,
                lastViewedAt = dto.lastViewedAt ?: 0,
                parentTitle = dto.parentTitle,
                parentRatingKey = dto.parentRatingKey,
                parentIndex = dto.parentIndex,
                grandparentTitle = dto.grandparentTitle,
                grandparentRatingKey = dto.grandparentRatingKey,
                index = dto.index,
                mediaParts =
                    dto.media?.flatMap { mediaDto ->
                        mediaDto.parts?.map { partDto ->
                            com.chakir.plexhubtv.core.model.MediaPart(
                                id = partDto.id,
                                key = partDto.key,
                                duration = partDto.duration,
                                file = partDto.file,
                                size = partDto.size,
                                container = partDto.container,
                                streams =
                                    partDto.streams?.map { streamDto ->
                                        mapStream(streamDto)
                                    } ?: emptyList(),
                            )
                        } ?: emptyList()
                    } ?: emptyList(),
                rating = dto.rating,
                audienceRating = dto.audienceRating,
                displayRating = dto.audienceRating ?: dto.rating ?: 0.0,
                contentRating = ContentRatingHelper.normalize(dto.contentRating),
                genres = dto.genres?.joinToString(",") { it.tag },
                addedAt = dto.addedAt ?: 0,
                updatedAt = System.currentTimeMillis(),
                parentThumb = dto.parentThumb,
                grandparentThumb = dto.grandparentThumb,
            )
        }

        private fun extractImdbId(dto: MetadataDTO): String? {
            val regex = "tt\\d{6,}".toRegex()
            dto.guids?.forEach { guid ->
                val match = regex.find(guid.id)
                if (match != null) return match.value
            }
            val legacyGuid = dto.guid ?: return null
            return regex.find(legacyGuid)?.value
        }

        private fun extractTmdbId(dto: MetadataDTO): String? {
            dto.guids?.forEach { guid ->
                if (guid.id.contains("tmdb") || guid.id.contains("themoviedb")) {
                    val match = "\\d+".toRegex().find(guid.id.split("/").last())
                    if (match != null) return match.value
                }
            }
            val legacyGuid = dto.guid ?: return null
            if (legacyGuid.contains("tmdb") || legacyGuid.contains("themoviedb")) {
                val match = "\\d+".toRegex().find(legacyGuid.split("/").last())
                if (match != null) return match.value
            }
            return null
        }

        fun mapDomainToEntity(
            item: MediaItem,
            libraryKey: String,
        ): MediaEntity {
            return MediaEntity(
                ratingKey = item.ratingKey,
                serverId = item.serverId,
                librarySectionId = libraryKey,
                unificationId = item.unificationId ?: "${item.serverId}_${item.ratingKey}",
                title = item.title,
                titleSortable = StringNormalizer.normalizeForSorting(item.title),
                guid = item.guid,
                imdbId = item.imdbId,
                tmdbId = item.tmdbId,
                type = mapTypeToString(item.type),
                thumbUrl = item.thumbUrl,
                artUrl = item.artUrl,
                summary = item.summary,
                year = item.year,
                duration = item.durationMs,
                viewOffset = item.viewOffset,
                lastViewedAt = item.lastViewedAt ?: 0,
                parentTitle = item.parentTitle,
                parentRatingKey = item.parentRatingKey,
                parentIndex = item.parentIndex,
                grandparentTitle = item.grandparentTitle,
                grandparentRatingKey = item.grandparentRatingKey,
                index = item.episodeIndex ?: item.seasonIndex,
                mediaParts = item.mediaParts,
                rating = item.rating,
                audienceRating = item.audienceRating,
                contentRating = ContentRatingHelper.normalize(item.contentRating),
                genres = item.genres.joinToString(","),
                addedAt = item.addedAt ?: 0,
                updatedAt = item.updatedAt ?: 0,
                parentThumb = item.parentThumb,
                grandparentThumb = item.grandparentThumb,
            )
        }

        fun mapEntityToDomain(entity: MediaEntity): MediaItem {
            // displayRating is pre-computed at write time: COALESCE(scrapedRating, audienceRating, rating, 0.0)
            // Works identically for both unified and non-unified views.
            val finalRating = entity.displayRating.takeIf { it > 0.0 }

            return MediaItem(
                id = "${entity.serverId}_${entity.ratingKey}",
                ratingKey = entity.ratingKey,
                serverId = entity.serverId,
                unificationId = entity.unificationId,
                title = entity.title,
                guid = entity.guid,
                type = mapType(entity.type),
                imdbId = entity.imdbId,
                tmdbId = entity.tmdbId,
                thumbUrl = entity.resolvedThumbUrl ?: entity.thumbUrl,
                artUrl = entity.resolvedArtUrl ?: entity.artUrl,
                alternativeThumbUrls = entity.alternativeThumbUrls?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                summary = entity.summary,
                year = entity.year,
                durationMs = entity.duration,
                viewOffset = entity.viewOffset,
                lastViewedAt = entity.lastViewedAt,
                parentTitle = entity.parentTitle,
                parentRatingKey = entity.parentRatingKey,
                parentIndex = entity.parentIndex,
                grandparentTitle = entity.grandparentTitle,
                grandparentRatingKey = entity.grandparentRatingKey,
                episodeIndex = if (entity.type == "episode") entity.index else null,
                seasonIndex = if (entity.type == "season" || entity.type == "episode") entity.parentIndex ?: entity.index else null,
                mediaParts = entity.mediaParts,
                rating = finalRating,
                audienceRating = entity.audienceRating,
                contentRating = ContentRatingHelper.normalize(entity.contentRating),
                genres = entity.genres?.split(",") ?: emptyList(),
                updatedAt = entity.updatedAt,
                parentThumb = entity.parentThumb,
                grandparentThumb = entity.grandparentThumb,
            )
        }

        fun isQualityMetadata(dto: MetadataDTO): Boolean {
            if (dto.type == "movie") {
                // Strict filter for movies: MUST have IMDb or TMDB ID
                val imdbId = extractImdbId(dto)
                val tmdbId = extractTmdbId(dto)
                return !dto.title.isNullOrBlank() && (!imdbId.isNullOrBlank() || !tmdbId.isNullOrBlank())
            }
            if (dto.type == "show") {
                // Strict filter for shows: MUST have IMDb or TMDB ID (same as movies)
                val imdbId = extractImdbId(dto)
                val tmdbId = extractTmdbId(dto)
                return !dto.title.isNullOrBlank() && (!imdbId.isNullOrBlank() || !tmdbId.isNullOrBlank())
            }
            // Episodes: Relaxed filter - only require title (rely on parent show's metadata quality)
            if (dto.type == "episode") {
                return !dto.title.isNullOrBlank()
            }
            return true
        }

        fun isQualityEntity(entity: MediaEntity): Boolean {
            // Relaxed Filter
            if (entity.type == "movie" || entity.type == "show") {
                return !entity.title.isNullOrBlank()
            }
            return true
        }

        private fun mapType(type: String?): MediaType {
            return when (type?.lowercase()) {
                "movie" -> MediaType.Movie
                "show" -> MediaType.Show
                "episode" -> MediaType.Episode
                "season" -> MediaType.Season
                else -> MediaType.Unknown
            }
        }

        private fun mapTypeToString(type: MediaType): String {
            return when (type) {
                MediaType.Movie -> "movie"
                MediaType.Show -> "show"
                MediaType.Episode -> "episode"
                MediaType.Season -> "season"
                else -> "unknown"
            }
        }

        private fun calculateUnificationId(dto: MetadataDTO): String {
            val imdbId = extractImdbId(dto)
            val tmdbId = extractTmdbId(dto)
            return when {
                !imdbId.isNullOrBlank() -> "imdb://$imdbId"
                !tmdbId.isNullOrBlank() -> "tmdb://$tmdbId"
                else -> {
                    val safeTitle = dto.title?.lowercase()?.trim()?.replace(Regex("[^a-z0-9 ]"), "") ?: "unknown"
                    "${safeTitle}_${dto.year ?: 0}"
                }
            }
        }
    }
