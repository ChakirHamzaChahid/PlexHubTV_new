package com.chakir.plexhubtv.feature.player.controller

import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SubtitleStream
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.service.PlaybackManager
import com.chakir.plexhubtv.domain.usecase.GetMediaDetailUseCase
import com.chakir.plexhubtv.feature.player.url.TranscodeUrlBuilder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlayerMediaLoader
    @Inject
    constructor(
        private val getMediaDetailUseCase: GetMediaDetailUseCase,
        private val settingsRepository: SettingsRepository,
        private val transcodeUrlBuilder: TranscodeUrlBuilder,
        private val playerTrackController: PlayerTrackController,
        private val chapterMarkerManager: ChapterMarkerManager,
        private val playerScrobbler: PlayerScrobbler,
        private val playbackManager: PlaybackManager,
    ) {
        data class MediaLoadResult(
            val item: MediaItem,
            val streamUri: String?,
            val isDirectPlay: Boolean,
            val audioTracks: List<com.chakir.plexhubtv.core.model.AudioTrack>,
            val subtitleTracks: List<com.chakir.plexhubtv.core.model.SubtitleTrack>,
            val selectedAudio: com.chakir.plexhubtv.core.model.AudioTrack?,
            val selectedSubtitle: com.chakir.plexhubtv.core.model.SubtitleTrack?,
            val needsMpvSwitch: Boolean = false,
        )

        suspend fun loadMedia(
            rKey: String,
            sId: String,
            isMpvMode: Boolean,
            bitrateOverride: Int? = null,
            audioStreamId: String? = null,
            subtitleStreamId: String? = null,
            onMpvSwitchRequired: () -> Unit,
        ): Result<MediaLoadResult> {
            val qualityPref = settingsRepository.getVideoQuality().first()

            val bitrate =
                bitrateOverride ?: when {
                    qualityPref.startsWith("20 Mbps") -> 20000
                    qualityPref.startsWith("12 Mbps") -> 12000
                    qualityPref.startsWith("8 Mbps") -> 8000
                    qualityPref.startsWith("4 Mbps") -> 4000
                    qualityPref.startsWith("3 Mbps") -> 3000
                    else -> 200000
                }

            var result: Result<MediaLoadResult>? = null

            getMediaDetailUseCase(rKey, sId).collect { detailResult ->
                detailResult.onSuccess { detail ->
                    playerScrobbler.resetAutoNext()

                    val (audios, subtitles) = playerTrackController.populateTracks(detail.item)
                    val media = detail.item
                    chapterMarkerManager.setChapters(media.chapters)
                    chapterMarkerManager.setMarkers(media.markers)

                    val clientId = settingsRepository.clientId.first() ?: "PlexHubTV-Client"
                    val part = media.mediaParts.firstOrNull()

                    // HEVC Detection
                    val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
                    val isHevc =
                        videoStream?.codec?.equals("hevc", ignoreCase = true) == true ||
                            videoStream?.codec?.equals("h265", ignoreCase = true) == true

                    val isDirectPlay = bitrate >= 200000 && part?.key != null

                    if (isHevc && !hasHardwareHEVCDecoder() && !isMpvMode) {
                        onMpvSwitchRequired()
                        result = Result.success(MediaLoadResult(media, null, isDirectPlay, audios, subtitles, null, com.chakir.plexhubtv.core.model.SubtitleTrack.OFF, true))
                        return@onSuccess
                    }

                    val (finalAudioStreamId, finalSubtitleStreamId) =
                        playerTrackController.resolveInitialTracks(
                            rKey,
                            sId,
                            part,
                            audioStreamId,
                            subtitleStreamId,
                        )

                    val aIndex = part?.streams?.filterIsInstance<AudioStream>()?.find { it.id == finalAudioStreamId }?.index
                    val sIndex = part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.id == finalSubtitleStreamId }?.index

                    val resolvedAudio = audios.find { it.streamId == finalAudioStreamId } ?: audios.firstOrNull()
                    val resolvedSubtitle = subtitles.find { it.streamId == finalSubtitleStreamId } ?: com.chakir.plexhubtv.core.model.SubtitleTrack.OFF

                    val streamUri =
                        if (part != null) {
                            transcodeUrlBuilder.buildUrl(
                                media, part, rKey, isDirectPlay, bitrate, clientId,
                                finalAudioStreamId, finalSubtitleStreamId, aIndex, sIndex,
                            )
                        } else {
                            null
                        }

                    if (streamUri == null) {
                        result = Result.failure(Exception("Unable to play media: Invalid URL"))
                    } else {
                        result =
                            Result.success(
                                MediaLoadResult(
                                    item = media,
                                    streamUri = streamUri.toString(),
                                    isDirectPlay = isDirectPlay,
                                    audioTracks = audios,
                                    subtitleTracks = subtitles,
                                    selectedAudio = resolvedAudio,
                                    selectedSubtitle = resolvedSubtitle,
                                ),
                            )
                    }
                }.onFailure { e ->
                    result = Result.failure(e)
                }
            }

            return result ?: Result.failure(Exception("Unknown error loading media"))
        }

        private fun hasHardwareHEVCDecoder(): Boolean {
            return try {
                val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
                codecList.codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } &&
                        !info.name.contains("google", ignoreCase = true) &&
                        !info.name.contains("sw", ignoreCase = true)
                }
            } catch (e: Exception) {
                false
            }
        }
    }
