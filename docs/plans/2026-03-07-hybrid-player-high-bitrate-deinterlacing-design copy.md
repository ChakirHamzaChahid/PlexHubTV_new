# Hybrid Player: High-Bitrate Playback & Deinterlacing

**Date:** 2026-03-07
**Issue:** [#62](https://github.com/ChakirHamzaChahid/PlexHubTV_new/issues/62)
**Status:** Approved

## Problem Statement

Two distinct playback problems affect PlexHubTV:

1. **High-bitrate lag** — Large files (e.g. Lord of the Rings 120 GB, ~80-100 Mbps) lag on both ExoPlayer and MPV, while VLC plays them perfectly.
2. **Interlaced content** — 1080i/60i content (IPTV, TV recordings) is not deinterlaced, producing combing artifacts.

## Root Cause Analysis

### High-Bitrate Lag

**VLC** uses FFmpeg's `libavformat` (Matroska demuxer) with aggressive buffering (~300 MB read-ahead). This handles high-bitrate MKV files efficiently.

**ExoPlayer** (current LAN config):
- `maxBufferMs = 15_000` (15s) — adequate in bytes but ExoPlayer's Matroska extractor is less efficient than FFmpeg's for complex multi-track MKVs.
- `bufferForPlaybackMs = 1_500` — starts playback with minimal buffer.

**MPV** (current config — the core issue):
```kotlin
MPVLib.setOptionString("vo", "gpu")
MPVLib.setOptionString("hwdec", "mediacodec")
// No cache/buffer options configured at all!
```

MPV uses FFmpeg for demuxing (same engine as VLC), but with **zero cache configuration**. Default MPV network streaming buffers are very conservative. This is why MPV performs **worse** than ExoPlayer despite using the same demuxer engine as VLC.

### Interlaced Content

Android's `MediaCodec` Surface output mode produces `GL_TEXTURE_EXTERNAL_OES` textures that are read-only from the GPU perspective. No shader-based deinterlacing is possible.

Research on Kodi's Android implementation confirms:
- MediaCodec active → deinterlace options greyed out
- Software YADIF via FFmpeg decode → too slow for HD on ARM (~5-10 fps at 1080i)
- Hardware deinterlace depends entirely on SoC (only NVIDIA Shield confirmed working)

**Solution:** MPV's `hwdec=mediacodec-copy` mode decodes with hardware but copies frames to system memory, enabling GPU post-processing including deinterlacing. ~10-15% CPU overhead for the copy, but decode remains hardware-accelerated.

## Design

### Axe 1: MPV Buffer Configuration (High-Bitrate Fix)

Add to `MpvPlayerWrapper.initialize()`:

```kotlin
// Network/demuxer cache — matches VLC-like buffering
MPVLib.setOptionString("cache", "yes")
MPVLib.setOptionString("demuxer-max-bytes", "800MiB")
MPVLib.setOptionString("demuxer-readahead-secs", "30")
MPVLib.setOptionString("demuxer-max-back-bytes", "200MiB")
```

These options configure FFmpeg's demuxer (inside MPV) to buffer aggressively, matching VLC's behavior for high-bitrate content.

### Axe 2: Deinterlacing via MPV (Interlaced Content Fix)

When interlaced content is detected, configure MPV with:

```kotlin
MPVLib.setOptionString("hwdec", "mediacodec-copy")  // instead of "mediacodec"
MPVLib.setOptionString("deinterlace", "yes")
```

`mediacodec-copy` = hardware decode + memory copy → enables GPU deinterlace filter.

### Axe 3: Intelligent Routing

Extend `PlayerController.loadMedia()` routing logic:

| Condition | Player | Config |
|-----------|--------|--------|
| High bitrate (>60 Mbps) | MPV | `hwdec=mediacodec`, cache enabled |
| Interlaced content (`scanType=interlaced`) | MPV | `hwdec=mediacodec-copy`, `deinterlace=yes` |
| Problematic audio/video codec | MPV | existing logic (unchanged) |
| Standard content | ExoPlayer | existing config (unchanged) |

**Detection:**
- **Bitrate**: from `VideoStream.bitrate` (already in model, populated by Plex API)
- **Interlaced**: new `scanType` field on `VideoStream`, mapped from Plex `Stream` XML attribute `scanType` (values: `"interlaced"`, `"progressive"`)

### Axe 4: User Setting

Add a deinterlacing toggle in player settings:
- **Auto** (default) — detect interlaced content and apply deinterlacing
- **Off** — disable deinterlacing (current behavior)

No high-bitrate toggle needed — MPV buffer config is always beneficial.

## Files to Modify

| File | Change |
|------|--------|
| `app/.../player/mpv/MpvPlayerWrapper.kt` | Add cache options to `initialize()`. Accept `deinterlace` flag to conditionally set `mediacodec-copy` + `deinterlace=yes` |
| `app/.../player/controller/PlayerController.kt` | Add bitrate + interlace detection in `loadMedia()`. Route to MPV when thresholds met. Pass flags to MPV creation |
| `app/.../player/PlayerFactory.kt` | Extend `createMpvPlayer()` signature with `highBitrate: Boolean`, `deinterlace: Boolean` flags |
| `core/model/.../Stream.kt` | Add `val scanType: String? = null` to `VideoStream` |
| `data/.../mapper/MediaMapper.kt` | Map `scanType` from Plex API DTO to domain model |
| `core/network/.../dto/` | Add `scanType` field to stream DTO |
| `core/datastore/.../SettingsDataStore.kt` | Add `deinterlaceMode` preference (auto/off) |
| `app/.../feature/settings/` | Add deinterlace toggle in player settings UI |

## Non-Goals

- **ExoPlayer FFmpeg decoder for video**: `media3-ffmpeg-decoder` only provides audio decoders. Software video decoding on ARM is too slow for 4K.
- **Runtime FFmpeg probe**: Adds 200-500ms latency, overkill when Plex metadata is available.
- **Forced MPV for all content**: ExoPlayer remains the default for standard content (lighter, better Android integration).

## Testing Strategy

1. **High-bitrate**: Test with 4K remux (>80 Mbps) — verify smooth playback via MPV with cache
2. **Deinterlacing**: Test with 1080i content — verify no combing artifacts
3. **Regression**: Verify standard content still plays via ExoPlayer unchanged
4. **Memory**: Monitor RAM usage with 800 MiB demuxer buffer on typical Android TV devices (2-4 GB RAM)
