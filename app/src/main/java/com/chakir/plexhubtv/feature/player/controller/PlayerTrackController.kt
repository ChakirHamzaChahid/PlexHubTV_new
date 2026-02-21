package com.chakir.plexhubtv.feature.player.controller

import androidx.media3.exoplayer.ExoPlayer
import com.chakir.plexhubtv.core.model.AudioStream
import com.chakir.plexhubtv.core.model.AudioTrack
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.SubtitleStream
import com.chakir.plexhubtv.core.model.SubtitleTrack
import com.chakir.plexhubtv.domain.model.TrackPreference
import com.chakir.plexhubtv.domain.repository.PlaybackRepository
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import com.chakir.plexhubtv.domain.repository.TrackPreferenceRepository
import com.chakir.plexhubtv.feature.player.mpv.MpvPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@javax.inject.Singleton
class PlayerTrackController
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val trackPreferenceRepository: TrackPreferenceRepository,
        private val playbackRepository: PlaybackRepository,
    ) {
        /**
         * Determine les pistes audio et sous-titres à utiliser au chargement du média.
         * Basé sur (1) Arguments, (2) DB locale, (3) Préférences globales, (4) Défaut Plex.
         */
        suspend fun resolveInitialTracks(
            ratingKey: String,
            serverId: String,
            part: com.chakir.plexhubtv.core.model.MediaPart?,
            argAudioStreamId: String?,
            argSubtitleStreamId: String?,
        ): Pair<String?, String?> {
            var finalAudioStreamId: String? = argAudioStreamId
            var finalSubtitleStreamId: String? = argSubtitleStreamId

            if (finalAudioStreamId == null || finalSubtitleStreamId == null) {
                // Level 2: DB
                val dbPref = trackPreferenceRepository.getPreference(ratingKey, serverId)

                // Audio
                if (finalAudioStreamId == null) {
                    finalAudioStreamId = dbPref?.audioStreamId
                }

                // Subtitle
                if (finalSubtitleStreamId == null) {
                    finalSubtitleStreamId = dbPref?.subtitleStreamId
                }

                // Level 3 & 4: Metadata / Profile
                if (finalAudioStreamId == null) {
                    val preferredAudioLang = settingsRepository.preferredAudioLanguage.first()
                    val bestAudio =
                        if (preferredAudioLang != null) {
                            part?.streams?.filterIsInstance<AudioStream>()?.find {
                                areLanguagesEqual(it.language, preferredAudioLang)
                            } ?: part?.streams?.filterIsInstance<AudioStream>()?.find { it.selected } // Fallback
                        } else {
                            part?.streams?.filterIsInstance<AudioStream>()?.find { it.selected }
                        }
                    finalAudioStreamId = bestAudio?.id
                }

                if (finalSubtitleStreamId == null) {
                    val preferredSubLang = settingsRepository.preferredSubtitleLanguage.first()
                    val bestSub =
                        if (preferredSubLang != null) {
                            part?.streams?.filterIsInstance<SubtitleStream>()?.find {
                                areLanguagesEqual(it.language, preferredSubLang)
                            } ?: part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.selected } // Fallback
                        } else {
                            part?.streams?.filterIsInstance<SubtitleStream>()?.find { it.selected }
                        }
                    finalSubtitleStreamId = bestSub?.id
                }
            }
            return Pair(finalAudioStreamId, finalSubtitleStreamId)
        }

        /**
         * Change la piste audio. Persiste le choix et synchronise avec Plex.
         * @param onReloadRequired Callback si un rechargement (transcoding) est requis.
         */
        fun selectAudioTrack(
            track: AudioTrack,
            currentItem: MediaItem?,
            currentSubtitleStreamId: String?, // Needed to preserve subtitle selection during reload
            scope: CoroutineScope,
            exoPlayer: ExoPlayer?,
            mpvPlayer: MpvPlayer?,
            isMpvMode: Boolean,
            isDirectPlay: Boolean,
            audioTracksInUi: List<AudioTrack>,
            onReloadRequired: (audioStreamId: String, subtitleStreamId: String?) -> Unit,
        ) {
            if (currentItem == null) return

            // 1. Persistence
            scope.launch {
                try {
                    val existing = trackPreferenceRepository.getPreference(currentItem.ratingKey, currentItem.serverId)
                    trackPreferenceRepository.savePreference(
                        TrackPreference(
                            ratingKey = currentItem.ratingKey,
                            serverId = currentItem.serverId,
                            audioStreamId = track.streamId,
                            subtitleStreamId = existing?.subtitleStreamId ?: currentSubtitleStreamId,
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to persist audio preference")
                }
            }

            // 2. Sync to Plex
            scope.launch {
                val part = currentItem.mediaParts.firstOrNull()
                if (part != null) {
                    try {
                        playbackRepository.updateStreamSelection(
                            serverId = currentItem.serverId,
                            partId = part.id,
                            audioStreamId = track.streamId,
                        )
                    } catch (e: Exception) {
                        Timber.w("Failed to sync audio selection: ${e.message}")
                    }
                }
            }

            // 3. Apply to Player
            if (isMpvMode && isDirectPlay) {
                val index = audioTracksInUi.indexOf(track) + 1
                if (index > 0) {
                    mpvPlayer?.setAudioId(index.toString())
                }
                return
            }

            if (isDirectPlay) {
                val p = exoPlayer ?: return

                // Try to find matching group in ExoPlayer
                val groups = p.currentTracks.groups
                var selectedGroupIndex = -1
                var selectedTrackIndex = -1

                // Strategy 1: Match by Language
                for (i in 0 until groups.size) {
                    val group = groups[i]
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (j in 0 until group.length) {
                            val format = group.getTrackFormat(j)
                            if (areLanguagesEqual(format.language, track.language)) {
                                selectedGroupIndex = i
                                selectedTrackIndex = j
                                break
                            }
                        }
                    }
                    if (selectedGroupIndex != -1) break
                }

                // Strategy 2: Fallback to Order Match
                if (selectedGroupIndex == -1) {
                    val uiIndex = audioTracksInUi.indexOf(track)
                    var audioGroupCounter = 0
                    for (i in 0 until groups.size) {
                        if (groups[i].type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                            if (audioGroupCounter == uiIndex) {
                                selectedGroupIndex = i
                                selectedTrackIndex = 0
                                break
                            }
                            audioGroupCounter++
                        }
                    }
                }

                if (selectedGroupIndex != -1) {
                    val builder = p.trackSelectionParameters.buildUpon()
                    builder.setOverrideForType(
                        androidx.media3.common.TrackSelectionOverride(groups[selectedGroupIndex].mediaTrackGroup, selectedTrackIndex),
                    )
                    p.trackSelectionParameters = builder.build()
                }
            } else {
                // Transcoding: Reload required
                // Update ExoPlayer preferences first (CRITICAL for HLS multi-track)
                exoPlayer?.let { p ->
                    val builder = p.trackSelectionParameters.buildUpon()
                    if (track.language != null) {
                        builder.setPreferredAudioLanguage(track.language)
                    }
                    p.trackSelectionParameters = builder.build()
                }
                onReloadRequired(track.streamId ?: "0", currentSubtitleStreamId)
            }
        }

        fun selectSubtitleTrack(
            track: SubtitleTrack,
            currentItem: MediaItem?,
            currentAudioStreamId: String?,
            scope: CoroutineScope,
            exoPlayer: ExoPlayer?,
            mpvPlayer: MpvPlayer?,
            isMpvMode: Boolean,
            isDirectPlay: Boolean,
            subtitleTracksInUi: List<SubtitleTrack>,
            onReloadRequired: (audioStreamId: String?, subtitleStreamId: String) -> Unit,
        ) {
            if (currentItem == null) return

            val subStreamIdToSave = if (track.id == "no") "0" else (track.streamId ?: "0")

            // 1. Persistence
            scope.launch {
                try {
                    val existing = trackPreferenceRepository.getPreference(currentItem.ratingKey, currentItem.serverId)
                    trackPreferenceRepository.savePreference(
                        TrackPreference(
                            ratingKey = currentItem.ratingKey,
                            serverId = currentItem.serverId,
                            audioStreamId = existing?.audioStreamId ?: currentAudioStreamId,
                            subtitleStreamId = subStreamIdToSave,
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to persist subtitle preference")
                }
            }

            // 2. Sync to Plex
            scope.launch {
                val part = currentItem.mediaParts.firstOrNull()
                if (part != null) {
                    try {
                        playbackRepository.updateStreamSelection(
                            serverId = currentItem.serverId,
                            partId = part.id,
                            subtitleStreamId = subStreamIdToSave,
                        )
                    } catch (e: Exception) {
                        Timber.w("Failed to sync subtitle selection: ${e.message}")
                    }
                }
            }

            if (isMpvMode && isDirectPlay) {
                if (track.id == "no") {
                    mpvPlayer?.setSubtitleId("no")
                } else {
                    val validTracks = subtitleTracksInUi.filter { it.id != "no" }
                    val index = validTracks.indexOf(track) + 1
                    if (index > 0) {
                        mpvPlayer?.setSubtitleId(index.toString())
                    }
                }
                return
            }

            if (isDirectPlay) {
                val p = exoPlayer ?: return
                val builder = p.trackSelectionParameters.buildUpon()

                if (track.id == "no") {
                    builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                } else {
                    builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)

                    val groups = p.currentTracks.groups
                    var selectedGroupIndex = -1
                    var selectedTrackIndex = -1

                    // Strategy 1: Match by Language
                    for (i in 0 until groups.size) {
                        val group = groups[i]
                        if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                            for (j in 0 until group.length) {
                                val format = group.getTrackFormat(j)
                                if (areLanguagesEqual(format.language, track.language)) {
                                    selectedGroupIndex = i
                                    selectedTrackIndex = j
                                    break
                                }
                            }
                        }
                        if (selectedGroupIndex != -1) break
                    }

                    if (selectedGroupIndex != -1) {
                        builder.setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(groups[selectedGroupIndex].mediaTrackGroup, selectedTrackIndex),
                        )
                    }
                }
                p.trackSelectionParameters = builder.build()
            } else {
                // Transcoding: Reload
                exoPlayer?.let { p ->
                    val builder = p.trackSelectionParameters.buildUpon()
                    if (track.id == "no") {
                        builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    } else {
                        builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                        if (track.language != null) {
                            builder.setPreferredTextLanguage(track.language)
                        }
                    }
                    p.trackSelectionParameters = builder.build()
                }
                onReloadRequired(currentAudioStreamId, subStreamIdToSave)
            }
        }

        fun populateTracks(item: MediaItem): Pair<List<AudioTrack>, List<SubtitleTrack>> {
            val part = item.mediaParts.firstOrNull() ?: return Pair(emptyList(), emptyList())
            val audios =
                part.streams.filterIsInstance<AudioStream>().map { stream ->
                    AudioTrack(
                        id = "plex-${stream.index}",
                        title = stream.displayTitle ?: stream.title ?: "Audio",
                        language = stream.language,
                        codec = stream.codec,
                        channels = stream.channels,
                        index = stream.index,
                        isSelected = stream.selected,
                        streamId = stream.id,
                    )
                }
            val subtitles = mutableListOf<SubtitleTrack>()
            subtitles.add(SubtitleTrack.OFF) // Add Off option

            subtitles.addAll(
                part.streams.filterIsInstance<SubtitleStream>().map { stream ->
                    SubtitleTrack(
                        id = "plex-${stream.index}",
                        title = stream.displayTitle ?: stream.title ?: "Subtitle",
                        language = stream.language,
                        codec = stream.codec,
                        index = stream.index,
                        isSelected = stream.selected,
                        isExternal = stream.isExternal,
                        streamId = stream.id,
                    )
                },
            )
            return Pair(audios, subtitles)
        }

        fun syncTracksWithExoPlayer(
            exoPlayer: ExoPlayer,
            currentAudioTracks: List<AudioTrack>,
            currentSubtitleTracks: List<SubtitleTrack>,
            currentSelectedAudio: AudioTrack?,
            currentSelectedSubtitle: SubtitleTrack?,
        ): Pair<AudioTrack?, SubtitleTrack?> {
            val currentTracks = exoPlayer.currentTracks
            var newSelectedAudio = currentSelectedAudio
            var newSelectedSubtitle = currentSelectedSubtitle

            currentTracks.groups.forEach { group ->
                val type = group.type

                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    val language = format.language ?: "und"

                    if (isSelected) {
                        when (type) {
                            androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                                // Find matching UI track by language
                                val matchingTrack =
                                    currentAudioTracks.find { uiTrack ->
                                        areLanguagesEqual(uiTrack.language, language)
                                    } ?: newSelectedAudio

                                newSelectedAudio = matchingTrack
                            }
                            androidx.media3.common.C.TRACK_TYPE_TEXT -> {
                                val matchingTrack =
                                    currentSubtitleTracks.find { uiTrack ->
                                        areLanguagesEqual(uiTrack.language, language)
                                    } ?: newSelectedSubtitle
                                newSelectedSubtitle = matchingTrack
                            }
                        }
                    }
                }
            }
            return Pair(newSelectedAudio, newSelectedSubtitle)
        }

        public fun areLanguagesEqual(
            lang1: String?,
            lang2: String?,
        ): Boolean {
            if (lang1 == lang2) return true
            if (lang1.isNullOrEmpty() || lang1 == "und") {
                return lang2.isNullOrEmpty() || lang2 == "und"
            }
            if (lang2.isNullOrEmpty() || lang2 == "und") return false

            val normalized1 = lang1.lowercase().trim()
            val normalized2 = lang2.lowercase().trim()

            if (normalized1 == normalized2) return true

            return try {
                val l1 = if (normalized1.length == 3) java.util.Locale.forLanguageTag(normalized1) else java.util.Locale(normalized1)
                val l2 = if (normalized2.length == 3) java.util.Locale.forLanguageTag(normalized2) else java.util.Locale(normalized2)
                l1.isO3Language.equals(l2.isO3Language, ignoreCase = true)
            } catch (e: Exception) {
                try {
                    val l1 = java.util.Locale(normalized1)
                    val l2 = java.util.Locale(normalized2)
                    l1.isO3Language.equals(l2.isO3Language, ignoreCase = true)
                } catch (e2: Exception) {
                    normalized1 == normalized2
                }
            }
        }
    }
