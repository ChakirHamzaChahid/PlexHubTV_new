# DEFECT #2: Player Episode Navigation & Auto-Next Popup Fix

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Fix 3 player defects: next episode creates new ExoPlayer (video surface lost), auto-next popup at 90% never appears for direct streams, and release() blocks main thread.

**Architecture:** Reuse existing ExoPlayer instance for episode changes instead of recreating it. Set `nextItem` in UI state for all playback paths (not just Plex). Move player release off the synchronous call path.

---

## Context

After fixing Issue #61 (skip intro regression + next episode replay), testing revealed 3 remaining defects:

- **Symptome B**: On Xtream/Backend sources, clicking "Next Episode" plays audio from E02 but video surface stays on E01 (emulator). On physical box, the same episode replays from 0:00.
- **Symptome A**: The auto-next popup at 90% progress NEVER appears (all devices, all backends).
- **Release crash risk**: `player?.release()` called synchronously in `onCleared()` can cause `ExoTimeoutException`.

### Root Cause Analysis

1. **Symptome B**: `PlayerControlViewModel.loadOrPlayMedia()` → `resolveAndPlayDirectStream()` → `playerController.initialize()` → `initializePlayer()` creates a **NEW** ExoPlayer. But the Compose `AndroidView` (VideoPlayerScreen:272-287) captures the OLD player reference in its factory lambda (no `update` lambda). New player has no surface → audio plays, video stays on old frame.

2. **Symptome A**: `PlayerController.playDirectUrlInternal()` (lines 208-219) sets `currentItem` but **never sets `nextItem`**. The auto-next popup condition is `uiState.showAutoNextPopup && uiState.nextItem != null` (VideoPlayerScreen:378). Since `nextItem` is always null for direct streams, popup never shows. Also `playerScrobbler.resetAutoNext()` is never called for direct streams, so stale `autoNextTriggered=true` from previous episode blocks the popup.

3. **Release blocking**: `PlayerController.release()` calls `player?.release()` synchronously on main thread from `ViewModel.onCleared()`. ExoPlayer release can hang → `ExoTimeoutException`.

---

## Task 1: Reuse ExoPlayer for direct stream episode changes

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerControlViewModel.kt` (lines 161-169)

**What to change:**

In `loadOrPlayMedia()`, when `isDirectStream`, do NOT call `resolveAndPlayDirectStream()` (which calls `initialize()` and recreates the player). Instead, resolve the URL directly and call `playerController.playDirectStream(url, media)` on the existing player.

**Before:**
```kotlin
private fun loadOrPlayMedia(media: MediaItem) {
    if (directStreamUrlBuilder.isDirectStream(media.serverId)) {
        resolveAndPlayDirectStream(
            media.ratingKey, media.serverId, 0L, media,
        ) { directStreamUrlBuilder.buildUrl(media.ratingKey, media.serverId) }
    } else {
        playerController.loadMedia(media.ratingKey, media.serverId)
    }
}
```

**After:**
```kotlin
private fun loadOrPlayMedia(media: MediaItem) {
    if (directStreamUrlBuilder.isDirectStream(media.serverId)) {
        // Reuse existing player — don't call initialize() which recreates ExoPlayer
        viewModelScope.launch {
            val url = directStreamUrlBuilder.buildUrl(media.ratingKey, media.serverId)
            if (url != null) {
                playerController.playDirectStream(url, media)
            } else {
                Timber.e("[Player] Failed to resolve stream URL for ${media.ratingKey} on ${media.serverId}")
                playerController.updateState {
                    it.copy(error = "Failed to get stream URL", isBuffering = false)
                }
            }
        }
    } else {
        playerController.loadMedia(media.ratingKey, media.serverId)
    }
}
```

**Why this works:** `playDirectStream()` → `playDirectUrlInternal()` reuses the existing `player` instance via `player?.apply { setMediaItem(); prepare(); playWhenReady = true }`. No new player created, surface stays bound.

---

## Task 2: Set `nextItem` and reset auto-next for direct streams

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lines 183-247, `playDirectUrlInternal`)

**What to change:**

In `playDirectUrlInternal()`, add two things:
1. Call `playerScrobbler.resetAutoNext()` before updating UI state (same as `loadMedia()` does at line 658)
2. Set `nextItem = playbackManager.getNextMedia()` in the UI state update (same as `loadMedia()` does at line 669)

**Before** (lines 208-219):
```kotlin
_uiState.update {
    it.copy(
        currentItem = mediaItem,
        isPlaying = true,
        isBuffering = true,
        currentPosition = if (it.currentItem?.id != mediaItem.id) 0L else it.currentPosition,
        audioTracks = audios,
        subtitleTracks = subtitles,
        selectedAudio = audios.find { t -> t.isSelected },
        selectedSubtitle = subtitles.find { t -> t.isSelected } ?: SubtitleTrack.OFF,
    )
}
```

**After:**
```kotlin
playerScrobbler.resetAutoNext()
val next = playbackManager.getNextMedia()

_uiState.update {
    it.copy(
        currentItem = mediaItem,
        nextItem = next,
        showAutoNextPopup = false,
        isPlaying = true,
        isBuffering = true,
        currentPosition = if (it.currentItem?.id != mediaItem.id) 0L else it.currentPosition,
        audioTracks = audios,
        subtitleTracks = subtitles,
        selectedAudio = audios.find { t -> t.isSelected },
        selectedSubtitle = subtitles.find { t -> t.isSelected } ?: SubtitleTrack.OFF,
    )
}
```

---

## Task 3: Non-blocking player release

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lines 137-159, `release()`)

**What to change:**

Null references immediately (preventing further use), then fire-and-forget the actual `release()` calls via `applicationScope.launch(mainDispatcher)` so `onCleared()` doesn't block.

**Before:**
```kotlin
fun release() {
    positionTrackerJob?.cancel()
    positionTrackerJob = null
    playerStatsTracker.stopTracking()
    playerScrobbler.stop()
    player?.release()
    player = null
    mpvPlayer?.release()
    mpvPlayer = null
    sessionJob.cancel()
    sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
    _uiState.value = PlayerUiState()
    isMpvMode = false
    isDirectPlay = false
    hasShownResumeToast = false
}
```

**After:**
```kotlin
fun release() {
    positionTrackerJob?.cancel()
    positionTrackerJob = null
    playerStatsTracker.stopTracking()
    playerScrobbler.stop()

    // Capture references and null immediately to prevent further use
    val exo = player
    val mpv = mpvPlayer
    player = null
    mpvPlayer = null

    // Fire-and-forget release on main thread via applicationScope
    // Avoids blocking onCleared() if ExoPlayer.release() hangs
    applicationScope.launch(mainDispatcher) {
        try { exo?.release() } catch (e: Exception) { Timber.w(e, "ExoPlayer release failed") }
        try { mpv?.release() } catch (e: Exception) { Timber.w(e, "MPV release failed") }
    }

    sessionJob.cancel()
    sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
    _uiState.value = PlayerUiState()
    isMpvMode = false
    isDirectPlay = false
    hasShownResumeToast = false
}
```

**Why `mainDispatcher`:** ExoPlayer requires release on the main looper thread. Using `applicationScope` ensures it survives ViewModel destruction. The key improvement is that `release()` returns immediately instead of blocking.

---

## Task 4: Fix PerformanceTracker warnings for direct stream path

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lines 183-247, `playDirectUrlInternal`)

**What to change:**

The ExoPlayer `Player.Listener.onIsPlayingChanged` (line 358) calls `performanceTracker.addCheckpoint("player_load_$ratingKey", ...)` and `endOperation(...)`. But for direct streams, no `startOperation()` was called with that opId → "unknown operation" warning.

Add `startOperation()` at the beginning of `playDirectUrlInternal()` and `endOperation()` on error path:

**Add at beginning of `playDirectUrlInternal()`** (after scheme check):
```kotlin
val opId = "player_load_${item?.ratingKey ?: "direct"}"
performanceTracker.startOperation(
    opId,
    com.chakir.plexhubtv.core.common.PerfCategory.PLAYBACK,
    "Direct Stream Play",
    mapOf("url" to url.take(80))
)
```

**Add on error path** (inside the `if (scheme !in ALLOWED_DIRECT_SCHEMES)` block):
```kotlin
performanceTracker.endOperation(opId, success = false, errorMessage = "Disallowed scheme: $scheme")
```

The success path is already handled by the ExoPlayer listener's `onIsPlayingChanged` callback.

---

## Verification

1. **Build:** `./gradlew assembleDebug` must succeed
2. **Symptome B test:** Play a series episode from Xtream/Backend source → click Next → video should switch to E02 (not audio-only)
3. **Symptome A test:** Play any series episode → watch until 90% → auto-next popup should appear
4. **Release test:** Navigate away from player mid-playback → no ANR or ExoTimeoutException in logcat
5. **Regression:** Play a Plex series episode → skip intro/outro buttons should still appear → next episode should still work
