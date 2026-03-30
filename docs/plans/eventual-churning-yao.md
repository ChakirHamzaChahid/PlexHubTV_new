# Fix: Trailer/Extra Playback 401 Error

## Context

When playing a trailer (extra) from the detail screen, ExoPlayer gets a **401 Unauthorized** error. The flow:
1. `MediaDetailViewModel.PlayExtra` creates a `MediaItem` with `type = MediaType.Clip`
2. `PlayerController.loadMedia()` fetches **fresh metadata** from the Plex API for the extra's ratingKey
3. The Plex API returns the extra with `type="clip"`, but `mapType()` in `MediaMapper` **does NOT handle "clip"** — it falls through to `MediaType.Unknown`
4. The `isDirectPlay` check at `PlayerController.kt:865` is:
   ```kotlin
   isDirectPlay = bitrate >= 200000 && part?.key != null && media.type != MediaType.Clip
   ```
   Since `media.type = Unknown` (not `Clip`), `isDirectPlay = true`
5. In direct play mode, CDN-hosted trailer URLs are returned **without authentication token** → 401

## Root Cause

**`MediaMapper.mapType()` doesn't map `"clip"` → `MediaType.Clip`**, so the Clip guard in `PlayerController` never triggers, and extras are incorrectly played via direct play instead of server transcode/proxy.

## Changes

### 1. Add "clip" to `mapType()` (PRIMARY FIX)
**File**: [MediaMapper.kt:431-438](data/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt#L431-L438)

Add `"clip" -> MediaType.Clip` to the `when` block:
```kotlin
private fun mapType(type: String?): MediaType {
    return when (type?.lowercase()) {
        "movie" -> MediaType.Movie
        "show" -> MediaType.Show
        "episode" -> MediaType.Episode
        "season" -> MediaType.Season
        "clip" -> MediaType.Clip          // ← ADD THIS
        else -> MediaType.Unknown
    }
}
```

This ensures extras fetched from the API get `type = MediaType.Clip`, which makes `isDirectPlay = false` in `PlayerController.kt:865`, routing them through the server transcode/proxy endpoint (which handles CDN authentication internally).

### 2. Add Clip check to `PlayerMediaLoader` (CONSISTENCY)
**File**: [PlayerMediaLoader.kt:98](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerMediaLoader.kt#L98)

The `PlayerMediaLoader` is missing the Clip guard that exists in `PlayerController`:
```kotlin
// Current (line 98):
val isDirectPlay = bitrate >= 200000 && part?.key != null

// Fix:
val isDirectPlay = bitrate >= 200000 && part?.key != null && media.type != com.chakir.plexhubtv.core.model.MediaType.Clip
```

### 3. Defense-in-depth: append token to external URLs in direct play (OPTIONAL SAFETY NET)
**File**: [TranscodeUrlBuilder.kt:40-41](app/src/main/java/com/chakir/plexhubtv/feature/player/url/TranscodeUrlBuilder.kt#L40-L41)

Currently, external URLs (starting with `http://` or `https://`) are returned as-is without the token. As a safety net:
```kotlin
if (partKey.startsWith("http://") || partKey.startsWith("https://")) {
    // Append token to external URLs (CDN-hosted extras, etc.)
    val separator = if (partKey.contains("?")) "&" else "?"
    return Uri.parse("$partKey${separator}X-Plex-Token=$token")
}
```

## Verification

1. Build the app and launch on the emulator/device
2. Navigate to any movie detail page that has trailers/extras
3. Click on a trailer — it should now play without error
4. Also verify that regular movie/episode playback still works (no regression)
