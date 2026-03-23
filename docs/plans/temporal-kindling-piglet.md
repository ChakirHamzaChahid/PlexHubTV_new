# Plan: Populate mediaParts for Xtream Episodes

## Context
Xtream episodes currently have empty `mediaParts: "[]"` in Room. The `get_series_info` API returns rich `info.video` and `info.audio` objects per episode with codec, resolution, bitrate, channels, language, etc. This data needs to be mapped into the existing `MediaPart` / `MediaStream` domain model so the UI can display stream info (resolution, codec, audio channels) for Xtream episodes, just like it does for Plex content.

## Files to Modify

### 1. `core/network/src/main/java/com/chakir/plexhubtv/core/network/xtream/XtreamDto.kt`

Add two new DTOs to model the `video` and `audio` sub-objects, and add fields to `XtreamEpisodeInfoDto`:

```kotlin
// New DTO for info.video
data class XtreamVideoCodecDto(
    val index: Int?,
    @SerializedName("codec_name") val codecName: String?,
    @SerializedName("codec_long_name") val codecLongName: String?,
    val profile: String?,
    val width: Int?,
    val height: Int?,
    @SerializedName("pix_fmt") val pixFmt: String?,
    @SerializedName("bit_rate") val bitRate: String?,
    @SerializedName("color_space") val colorSpace: String?,
    @SerializedName("color_transfer") val colorTransfer: String?,
    val tags: XtreamStreamTagsDto?,
)

// New DTO for info.audio
data class XtreamAudioCodecDto(
    val index: Int?,
    @SerializedName("codec_name") val codecName: String?,
    @SerializedName("codec_long_name") val codecLongName: String?,
    val profile: String?,
    val channels: Int?,
    @SerializedName("channel_layout") val channelLayout: String?,
    @SerializedName("sample_rate") val sampleRate: String?,
    @SerializedName("bit_rate") val bitRate: String?,
    val tags: XtreamStreamTagsDto?,
)

// New DTO for stream tags (shared by video/audio)
data class XtreamStreamTagsDto(
    val language: String?,
    val title: String?,
)
```

Update `XtreamEpisodeInfoDto` — add 3 fields:
```kotlin
data class XtreamEpisodeInfoDto(
    // ... existing fields ...
    val video: XtreamVideoCodecDto?,     // NEW
    val audio: XtreamAudioCodecDto?,     // NEW
    val bitrate: Int?,                   // NEW (overall kbps)
)
```

### 2. `data/src/main/java/com/chakir/plexhubtv/data/mapper/XtreamMediaMapper.kt`

Update `mapEpisodeToEntity()` to populate `mediaParts` from the video/audio DTOs:

- Build a `VideoStream` from `episode.info.video` (map `codecName` → `codec`, `width`/`height`, `bitRate` → `bitrate`, detect HDR from `colorTransfer`/`colorSpace`/`profile`)
- Build an `AudioStream` from `episode.info.audio` (map `codecName` → `codec`, `channels`, language from `tags.language`)
- Create a single `MediaPart` with:
  - `id` = episode id
  - `key` = ratingKey
  - `duration` = `durationSecs * 1000`
  - `container` = `containerExtension`
  - `streams` = listOf(videoStream, audioStream) (filtered of nulls)
- Set `mediaParts` on the `MediaEntity`

HDR detection logic (reuse same pattern as Plex mapper in `MediaMapper.kt:158-162`):
```kotlin
val isHdr = colorSpace == "bt2020"
    || colorTransfer == "smpte2084"
    || colorTransfer == "arib-std-b67"
    || profile?.contains("Main 10", ignoreCase = true) == true
```

## Verification
1. Build the project: `./gradlew assembleDebug`
2. Clear app data or increment DB version if needed (mediaParts column already exists, no schema change needed — only data population changes)
3. Navigate to an Xtream series → season → episode detail
4. Verify mediaParts is populated with video/audio stream info in logs
5. Verify stream info displays in the UI (resolution, codec, channels)
