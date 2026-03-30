# Hybrid Player: High-Bitrate & Deinterlacing — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix high-bitrate playback lag (120 GB remux) and add deinterlacing for interlaced content by configuring MPV's FFmpeg-based demuxer/cache and using `mediacodec-copy` mode.

**Architecture:** Extend MPV's initialization with VLC-like cache options, add `scanType` field through the DTO→domain pipeline, and extend PlayerController's routing logic to auto-switch to MPV for high-bitrate or interlaced content. Add a user setting for deinterlace mode.

**Tech Stack:** MPV (libmpv 0.5.1), ExoPlayer (Media3), Jetpack Compose, DataStore, Hilt

---

### Task 1: Add `scanType` to StreamDTO

**Files:**
- Modify: `core/network/src/main/java/com/chakir/plexhubtv/core/network/model/PlexResponse.kt:157-178`

**Step 1: Add `scanType` field to StreamDTO**

In `PlexResponse.kt`, add the field to `StreamDTO` (after line 177, before the closing paren):

```kotlin
data class StreamDTO(
    val id: String,
    val streamType: Int,
    val codec: String?,
    val index: Int?,
    val language: String?,
    val languageCode: String?,
    val title: String?,
    val displayTitle: String?,
    val selected: Boolean = false,
    val forced: Boolean = false,
    val channels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val key: String? = null,
    val colorRange: String? = null,
    val colorSpace: String? = null,
    val colorPrimaries: String? = null,
    val colorTransfer: String? = null,
    val profile: String? = null,
    val scanType: String? = null,       // "interlaced" or "progressive"
)
```

Plex API returns `scanType` as an XML attribute on `<Stream>` elements (values: `"interlaced"`, `"progressive"`). Gson will auto-map it.

**Step 2: Commit**

```bash
git add core/network/src/main/java/com/chakir/plexhubtv/core/network/model/PlexResponse.kt
git commit -m "feat(player): add scanType field to StreamDTO for interlace detection"
```

---

### Task 2: Add `scanType` to VideoStream domain model

**Files:**
- Modify: `core/model/src/main/java/com/chakir/plexhubtv/core/model/Stream.kt:64-78`

**Step 1: Add `scanType` to VideoStream**

```kotlin
@Serializable
@SerialName("VideoStream")
data class VideoStream(
    override val id: String,
    override val index: Int?,
    override val language: String?,
    override val languageCode: String?,
    override val title: String?,
    override val displayTitle: String?,
    override val codec: String?,
    override val selected: Boolean,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val hasHDR: Boolean = false,
    val scanType: String? = null,
) : MediaStream()
```

**Step 2: Commit**

```bash
git add core/model/src/main/java/com/chakir/plexhubtv/core/model/Stream.kt
git commit -m "feat(player): add scanType to VideoStream domain model"
```

---

### Task 3: Map `scanType` in MediaMapper

**Files:**
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt:165-169`

**Step 1: Pass scanType in mapStream()**

In the `mapStream()` function, VideoStream creation (streamType == 1), add `scanType`:

```kotlin
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
        scanType = dto.scanType,
    )
}
```

**Step 2: Commit**

```bash
git add data/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt
git commit -m "feat(player): map scanType from DTO to domain in MediaMapper"
```

---

### Task 4: Add deinterlace mode to SettingsDataStore

**Files:**
- Modify: `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`

**Step 1: Add preference key**

Add after the existing `PLAYER_ENGINE` key (around line 54):

```kotlin
private val DEINTERLACE_MODE = stringPreferencesKey("deinterlace_mode")
```

**Step 2: Add Flow getter**

Add near the other player-related flows (after `playerEngine` around line 181):

```kotlin
val deinterlaceMode: Flow<String> =
    dataStore.data
        .map { preferences -> preferences[DEINTERLACE_MODE] ?: "auto" }
```

**Step 3: Add save function**

Add near `savePlayerEngine()` (around line 335):

```kotlin
suspend fun saveDeinterlaceMode(mode: String) {
    dataStore.edit { preferences ->
        preferences[DEINTERLACE_MODE] = mode
    }
}
```

**Step 4: Commit**

```bash
git add core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt
git commit -m "feat(player): add deinterlace mode preference to DataStore"
```

---

### Task 5: Expose deinterlace mode in SettingsRepository

**Files:**
- Modify: `domain/src/main/java/com/chakir/plexhubtv/domain/repository/SettingsRepository.kt`
- Modify: the implementation file (likely in `data/src/main/java/.../repository/SettingsRepositoryImpl.kt`)

**Step 1: Add to interface**

Add next to `playerEngine`:

```kotlin
val deinterlaceMode: Flow<String>
suspend fun setDeinterlaceMode(mode: String)
```

**Step 2: Add to implementation**

Delegate to SettingsDataStore:

```kotlin
override val deinterlaceMode: Flow<String> = settingsDataStore.deinterlaceMode

override suspend fun setDeinterlaceMode(mode: String) {
    settingsDataStore.saveDeinterlaceMode(mode)
}
```

**Step 3: Commit**

```bash
git add domain/src/main/java/com/chakir/plexhubtv/domain/repository/SettingsRepository.kt
git add data/src/main/java/com/chakir/plexhubtv/data/repository/SettingsRepositoryImpl.kt
git commit -m "feat(player): expose deinterlace mode in SettingsRepository"
```

---

### Task 6: Add deinterlace toggle in Settings UI

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt`

**Step 1: Add to UiState and Action**

In `SettingsUiState.kt`:

```kotlin
// Add field to SettingsUiState (after playerEngine line 15):
val deinterlaceMode: String = "auto",
```

```kotlin
// Add action to SettingsAction sealed interface:
data class ChangeDeinterlaceMode(val mode: String) : SettingsAction
```

**Step 2: Add UI tile in SettingsScreen.kt**

In the Playback section (after the player engine tile, around line 203):

```kotlin
SettingsTile(
    title = stringResource(R.string.settings_deinterlace_mode),
    subtitle = when (state.deinterlaceMode) {
        "auto" -> stringResource(R.string.settings_deinterlace_auto)
        "off" -> stringResource(R.string.settings_deinterlace_off)
        else -> state.deinterlaceMode
    },
    onClick = { showDeinterlaceDialog = true },
)
```

Add the dialog state variable alongside the other dialog states:

```kotlin
var showDeinterlaceDialog by remember { mutableStateOf(false) }
```

Add the dialog alongside `showPlayerEngineDialog` (around line 708):

```kotlin
if (showDeinterlaceDialog) {
    val options = listOf(
        stringResource(R.string.settings_deinterlace_auto),
        stringResource(R.string.settings_deinterlace_off),
    )
    SettingsDialog(
        title = stringResource(R.string.settings_deinterlace_mode),
        options = options,
        currentValue = when (state.deinterlaceMode) {
            "auto" -> stringResource(R.string.settings_deinterlace_auto)
            "off" -> stringResource(R.string.settings_deinterlace_off)
            else -> state.deinterlaceMode
        },
        onDismissRequest = { showDeinterlaceDialog = false },
        onOptionSelected = { selected ->
            val mode = when (selected) {
                options[0] -> "auto"
                else -> "off"
            }
            onAction(SettingsAction.ChangeDeinterlaceMode(mode))
            showDeinterlaceDialog = false
        },
    )
}
```

**Step 3: Handle action in SettingsViewModel**

Add case in the `when` block that handles `SettingsAction`:

```kotlin
is SettingsAction.ChangeDeinterlaceMode -> {
    viewModelScope.launch {
        settingsRepository.setDeinterlaceMode(action.mode)
    }
}
```

And collect the flow in the ViewModel's init or state builder:

```kotlin
// Where deinterlaceMode is collected into uiState:
settingsRepository.deinterlaceMode.collect { mode ->
    _uiState.update { it.copy(deinterlaceMode = mode) }
}
```

**Step 4: Add string resources**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="settings_deinterlace_mode">Deinterlacing</string>
<string name="settings_deinterlace_auto">Auto (recommended)</string>
<string name="settings_deinterlace_off">Off</string>
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(player): add deinterlace mode toggle in Settings UI"
```

---

### Task 7: Configure MPV cache and deinterlace options

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayer.kt:8-42`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:19-145`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerFactory.kt:19-33,113-118`

This is the core change. MPV needs VLC-like cache configuration and conditional deinterlace support.

**Step 1: Add config data class and update MpvPlayer interface**

In `MpvPlayer.kt`, update the `initialize` signature:

```kotlin
data class MpvConfig(
    val deinterlace: Boolean = false,
)

interface MpvPlayer {
    val isPlaying: StateFlow<Boolean>
    val isBuffering: StateFlow<Boolean>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val error: StateFlow<String?>

    fun initialize(viewGroup: ViewGroup, config: MpvConfig = MpvConfig())

    fun play(url: String)
    fun resume()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun setSpeed(speed: Double)
    fun setAudioId(aid: String)
    fun setSubtitleId(sid: String)
    fun setAudioDelay(delayMs: Long)
    fun setSubtitleDelay(delayMs: Long)
    fun attach(lifecycleOwner: LifecycleOwner)
    fun release()
    fun getStats(): PlayerStats
}
```

**Step 2: Update MpvPlayerWrapper.initialize()**

In `MpvPlayerWrapper.kt`, update `initialize()` to accept and use the config:

```kotlin
override fun initialize(viewGroup: ViewGroup, config: MpvConfig) {
    if (isInitialized) return
    Timber.d("Initializing MPV... (deinterlace=${config.deinterlace})")

    try {
        MPVLib.create(context)
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")

        // Hardware decode mode:
        // - mediacodec-copy for deinterlace (copies frames to RAM → GPU post-processing)
        // - mediacodec for normal (direct Surface output, zero-copy)
        if (config.deinterlace) {
            MPVLib.setOptionString("hwdec", "mediacodec-copy")
            MPVLib.setOptionString("deinterlace", "yes")
            Timber.d("MPV: deinterlace ON (hwdec=mediacodec-copy)")
        } else {
            MPVLib.setOptionString("hwdec", "mediacodec")
        }

        // VLC-like demuxer cache for high-bitrate content
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", "800MiB")
        MPVLib.setOptionString("demuxer-readahead-secs", "30")
        MPVLib.setOptionString("demuxer-max-back-bytes", "200MiB")

        // Subtitles: use Android system fonts
        MPVLib.setOptionString("sub-ass", "yes")
        // ... rest of subtitle config unchanged ...
```

**Step 3: Update PlayerFactory interface and implementation**

In `PlayerFactory.kt`:

```kotlin
interface PlayerFactory {
    fun createExoPlayer(context: Context, isRelay: Boolean = false): ExoPlayer

    fun createMediaItem(
        uri: android.net.Uri,
        mediaId: String,
        isM3u8: Boolean,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration> = emptyList(),
    ): MediaItem

    fun createMpvPlayer(
        context: Context,
        scope: CoroutineScope,
        config: MpvConfig = MpvConfig(),
    ): MpvPlayer
}
```

Implementation:

```kotlin
override fun createMpvPlayer(
    context: Context,
    scope: CoroutineScope,
    config: MpvConfig,
): MpvPlayer {
    return MpvPlayerWrapper(context, scope, config)
}
```

Update `MpvPlayerWrapper` constructor to accept config:

```kotlin
class MpvPlayerWrapper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: MpvConfig = MpvConfig(),
) : MpvPlayer, SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver, DefaultLifecycleObserver {
```

And update `initialize(viewGroup: ViewGroup)` → `initialize(viewGroup: ViewGroup, config: MpvConfig)` to use `this.config` (passed via constructor) instead of parameter to avoid dual config paths. Keep the interface parameter for flexibility but use constructor config as default:

```kotlin
override fun initialize(viewGroup: ViewGroup, config: MpvConfig) {
    if (isInitialized) return
    // Use constructor config (set at creation time by PlayerFactory)
    val effectiveConfig = this.config
    // ... use effectiveConfig ...
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayer.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerFactory.kt
git commit -m "feat(player): add VLC-like cache config and deinterlace support to MPV"
```

---

### Task 8: Add intelligent routing in PlayerController

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt`

This is the final wiring. PlayerController needs to:
1. Detect high-bitrate (>60 Mbps) → route to MPV
2. Detect interlaced (`scanType == "interlaced"`) + deinterlace setting == "auto" → route to MPV with `deinterlace=true`

**Step 1: Add deinterlace setting to constructor**

The controller already has `settingsRepository` injected. No constructor change needed.

**Step 2: Add detection constants to companion object**

In `PlayerController.companion`:

```kotlin
/** Bitrate threshold (kbps) above which MPV is preferred for its FFmpeg demuxer */
private const val HIGH_BITRATE_THRESHOLD_KBPS = 60_000
```

**Step 3: Update switchToMpv() to accept config**

```kotlin
fun switchToMpv(config: MpvConfig = MpvConfig()) {
    Timber.d("SCREEN [Player] switchToMpv() called (deinterlace=${config.deinterlace})")
    if (isMpvMode) return
    isMpvMode = true
    FirebaseCrashlytics.getInstance().setCustomKey("player_engine", "MPV")

    player?.release()
    player = null

    mpvPlayer = playerFactory.createMpvPlayer(application, scope, config)
    // ... rest unchanged ...
```

**Step 4: Add routing logic in loadMedia()**

In `loadMedia()`, after the existing audio/video codec pre-flight checks (around line 717), add:

```kotlin
// High-bitrate pre-flight: route to MPV for FFmpeg demuxer superiority
if (isDirectPlay && !isMpvMode) {
    val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
    val videoBitrateKbps = videoStream?.bitrate ?: 0

    // Check deinterlace setting
    val deinterlaceMode = settingsRepository.deinterlaceMode.first()
    val isInterlaced = videoStream?.scanType?.equals("interlaced", ignoreCase = true) == true
    val needsDeinterlace = isInterlaced && deinterlaceMode == "auto"

    if (videoBitrateKbps > HIGH_BITRATE_THRESHOLD_KBPS || needsDeinterlace) {
        val reason = when {
            needsDeinterlace && videoBitrateKbps > HIGH_BITRATE_THRESHOLD_KBPS ->
                "interlaced + high-bitrate (${videoBitrateKbps}kbps)"
            needsDeinterlace -> "interlaced content (scanType=${videoStream?.scanType})"
            else -> "high-bitrate (${videoBitrateKbps}kbps > ${HIGH_BITRATE_THRESHOLD_KBPS}kbps)"
        }
        Timber.d("PlayerController: $reason → switching to MPV")
        performanceTracker.addCheckpoint(opId, "Auto-route → MPV", mapOf("reason" to reason))
        switchToMpv(MpvConfig(deinterlace = needsDeinterlace))
        return@launch
    }
}
```

**Important:** This block goes AFTER the existing codec pre-flight checks (lines 697-718) but BEFORE the track resolution code (line 720). The existing codec checks call `switchToMpv()` without config (defaulting to no deinterlace), which is correct for codec-only fallback.

**Step 5: Update the user-initiated `switchToMpv()` call in loadMedia()**

The existing check at line 626-629 (`if (engine == "MPV" && !isMpvMode)`) also needs to pass config. But since user is explicitly choosing MPV, we check deinterlace setting:

```kotlin
if (engine == "MPV" && !isMpvMode) {
    val deinterlaceMode = settingsRepository.deinterlaceMode.first()
    val videoStream = part?.streams?.filterIsInstance<com.chakir.plexhubtv.core.model.VideoStream>()?.firstOrNull()
    val isInterlaced = videoStream?.scanType?.equals("interlaced", ignoreCase = true) == true
    val needsDeinterlace = isInterlaced && deinterlaceMode == "auto"
    switchToMpv(MpvConfig(deinterlace = needsDeinterlace))
    return@launch
}
```

Note: at this point in the code, `part` is not yet resolved (it's resolved later). So move this check AFTER `val part = media.mediaParts.firstOrNull()` (line 691). Or better: keep the early check but without deinterlace config (media not loaded yet), and let the later routing logic handle deinterlace.

Actually, simplest approach: keep line 626-629 as-is (no config, basic MPV switch for user preference), and let the high-bitrate/interlace routing at line ~717 handle the config. If user chose MPV AND content is interlaced, the player is already in MPV mode so the routing block won't trigger. To handle this: set `deinterlace` property on MpvPlayerWrapper at runtime instead.

**Alternative (simpler):** Make deinterlace a runtime property on MpvPlayer:

In `MpvPlayer.kt` / `MpvPlayerWrapper.kt`:
```kotlin
fun setDeinterlace(enabled: Boolean)
```

Implementation:
```kotlin
override fun setDeinterlace(enabled: Boolean) {
    if (!isInitialized) return
    MPVLib.setPropertyString("deinterlace", if (enabled) "yes" else "no")
    if (enabled) {
        MPVLib.setPropertyString("hwdec", "mediacodec-copy")
    }
    Timber.d("MPV: deinterlace=${enabled}")
}
```

This avoids the init-time config complexity. The routing in `loadMedia()` calls `switchToMpv()` then `mpvPlayer?.setDeinterlace(true)` after.

**Step 6: Add import for MpvConfig**

At the top of `PlayerController.kt`:
```kotlin
import com.chakir.plexhubtv.feature.player.mpv.MpvConfig
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt
git commit -m "feat(player): add intelligent MPV routing for high-bitrate and interlaced content"
```

---

### Task 9: Build verification and manual testing

**Step 1: Build the project**

```bash
cd c:/Users/chakir/AndroidStudioProjects/PlexHubTV
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 2: Manual test matrix**

| Test | Content | Expected Player | Expected Behavior |
|------|---------|----------------|-------------------|
| Standard movie (1080p, 10 Mbps) | Any Plex movie | ExoPlayer | No change from before |
| High-bitrate remux (>60 Mbps) | 4K Blu-ray remux | MPV (auto-routed) | Smooth playback, no lag |
| Interlaced content (1080i) | IPTV or TV recording | MPV + deinterlace | No combing artifacts |
| User forces MPV engine | Any content | MPV | Works as before + cache |
| Deinterlace OFF in settings | 1080i content | ExoPlayer (default) | No deinterlace applied |

**Step 3: Check Timber logs**

Look for these log lines during playback:
- `"PlayerController: high-bitrate (XXXXkbps > 60000kbps) → switching to MPV"`
- `"PlayerController: interlaced content (scanType=interlaced) → switching to MPV"`
- `"MPV: deinterlace ON (hwdec=mediacodec-copy)"`
- `"Initializing MPV... (deinterlace=true)"`

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat(issue-62): hybrid player with high-bitrate cache and deinterlacing support

- Configure MPV with VLC-like demuxer cache (800MiB, 30s readahead)
- Add deinterlace via mediacodec-copy mode for interlaced content
- Auto-route high-bitrate (>60Mbps) content to MPV
- Auto-route interlaced content to MPV with deinterlace
- Add scanType field through DTO → domain pipeline
- Add deinterlace mode setting (Auto/Off)"
```
