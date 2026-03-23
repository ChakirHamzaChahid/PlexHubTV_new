# Zero-Copy Rendering Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate high-bitrate playback lag by switching both ExoPlayer and MPV to zero-copy rendering pipelines that bypass GPU texture upload.

**Architecture:** Two complementary changes:
- ExoPlayer: Enable tunneled playback via explicit `DefaultTrackSelector` with `setTunnelingEnabled(true)` — MediaCodec writes directly to the display surface, bypassing SurfaceTexture.
- MPV: Switch from `vo=gpu` (OpenGL ES compositing) to `vo=mediacodec_embed` (direct Surface rendering) when deinterlacing is not needed. Keep `vo=gpu` only for deinterlace mode.

**Tech Stack:** Media3 ExoPlayer, mpv-android (libmpv), MediaCodec, Android SurfaceView

---

### Task 1: ExoPlayer — Enable tunneled playback via DefaultTrackSelector

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerFactory.kt`

**Context:** Currently `ExoPlayer.Builder` creates an implicit `DefaultTrackSelector` with no tunneling. Tunneled rendering lets MediaCodec write decoded frames directly to the hardware display layer, bypassing SurfaceTexture GPU upload. This is the same technique VLC and Kodi use for smooth 4K playback on low-end SoCs.

**Step 1: Add DefaultTrackSelector import and creation in PlayerFactory**

In `PlayerFactory.kt`, modify `createExoPlayer()` to create an explicit `DefaultTrackSelector` with tunneling enabled:

```kotlin
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

// Inside createExoPlayer():
val trackSelector = DefaultTrackSelector(context).apply {
    parameters = buildUponParameters()
        .setTunnelingEnabled(true)
        .build()
}

return ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .setMediaSourceFactory(mediaSourceFactory)
    .setLoadControl(loadControl)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()
```

**Step 2: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — no compilation errors

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerFactory.kt
git commit -m "feat(player): enable ExoPlayer tunneled playback for zero-copy 4K rendering"
```

---

### Task 2: MPV — Switch to `vo=mediacodec_embed` for non-deinterlace mode

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt`

**Context:** Currently MPV always uses `vo=gpu` which goes through OpenGL ES texture upload for every frame. For non-deinterlace cases, `vo=mediacodec_embed` renders directly to the Android Surface (zero-copy), matching VLC's architecture. When deinterlacing IS needed, we keep `vo=gpu` because `mediacodec_embed` cannot do GPU post-processing.

**Step 1: Make vo conditional on deinterlace mode**

In `MpvPlayerWrapper.kt`, replace the current video output configuration (lines 79-93) with:

```kotlin
if (config.deinterlace) {
    // Deinterlace path: GPU rendering required for post-processing
    MPVLib.setOptionString("vo", "gpu")
    MPVLib.setOptionString("gpu-context", "android")
    MPVLib.setOptionString("opengl-es", "yes")
    MPVLib.setOptionString("hwdec", "mediacodec-copy")
    MPVLib.setOptionString("deinterlace", "yes")
    Timber.d("MPV: deinterlace ON (vo=gpu, hwdec=mediacodec-copy)")
} else {
    // Zero-copy path: MediaCodec renders directly to Surface
    MPVLib.setOptionString("vo", "mediacodec_embed")
    MPVLib.setOptionString("hwdec", "mediacodec")
    Timber.d("MPV: zero-copy mode (vo=mediacodec_embed, hwdec=mediacodec)")
}
```

**Step 2: Update SurfaceView pixel format**

The `PixelFormat.TRANSLUCENT` was needed for GPU output. For `mediacodec_embed`, the surface format should adapt:

```kotlin
surfaceView = SurfaceView(context).apply {
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )
    holder.addCallback(this@MpvPlayerWrapper)
    // TRANSLUCENT needed for vo=gpu; for mediacodec_embed the surface
    // format is managed by MediaCodec directly, but TRANSLUCENT is safe for both
    holder.setFormat(PixelFormat.TRANSLUCENT)
}
```

No change needed — `TRANSLUCENT` works for both modes.

**Step 3: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt
git commit -m "feat(player): switch MPV to vo=mediacodec_embed for zero-copy rendering"
```

---

### Task 3: Build verification (debug + release)

**Step 1: Debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Release build**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL

**Step 3: Verify ProGuard doesn't strip DefaultTrackSelector**

The existing rule `-keep class androidx.media3.** { *; }` in `app/proguard-rules.pro` already covers `DefaultTrackSelector`. No additional ProGuard rules needed.
