# Plan v2: Player Bugs & Improvements (12 items)

## Context
The PlexHubTV video player has 12 reported bugs/improvements around focus management, menu behavior, stats display, equalizer, seek controls, and header info. All issues affect the Android TV experience (Mi Box S + remote). The player is built with Compose + ExoPlayer/MPV hybrid engine.

**v2 changes**: Fixed 9 issues found during critical review of v1 (2 critical, 4 major, 3 minor).

---

## BUG 1 + BUG 11 (merged) — Focus initial & navigation globale

**Problem**: Focus lands on center big button instead of bottom play/pause. D-Pad navigation incoherent.

**Root cause**: `playPauseFocusRequester` attached to center big icon button at [NetflixPlayerControls.kt:174](app/src/main/java/com/chakir/plexhubtv/feature/player/components/NetflixPlayerControls.kt#L174), not the bottom transport row.

**Plan**:
1. Move `playPauseFocusRequester` from center big button to the bottom transport play/pause button at [NetflixPlayerControls.kt:253](app/src/main/java/com/chakir/plexhubtv/feature/player/components/NetflixPlayerControls.kt#L253)
2. Remove the center big button's `focusRequester` modifier — keep it clickable for touch, but NOT D-Pad focusable (add `focusProperties { canFocus = false }`)
3. Verify each dialog/overlay has its own `FocusRequester` for first item on open (already done for most — verify `AudioEqualizerDialog`)

**Files**: `NetflixPlayerControls.kt`

**Risks**: Focus race conditions if delays are too short. Test with 350ms delay.

**v2 note**: Removed dead-code step from v1 ("skip focus when showMoreMenu is true") — when sub-dialogs open from More Menu, `showMoreMenu` is already set to `false` (line 194), so this condition can never be true.

---

## BUG 9 — Sortie du menu options: trop d'appuis retour

**Problem**: Exiting options requires multiple Back presses. `DismissDialog` closes ALL overlays at once.

**Root cause**: Back handler at [VideoPlayerScreen.kt:234-247](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L234) fires `DismissDialog` which closes ALL 12 overlay flags simultaneously. From a sub-dialog, user needs: Back (close all) → Back (hide controls) → Back (exit player) = 3 presses.

**Plan**:
1. Add `PlayerAction.DismissCurrentOverlay` to the sealed interface in [PlayerUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerUiState.kt)
2. Implement layered dismiss in [PlayerControlViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerControlViewModel.kt):
   ```kotlin
   is PlayerAction.DismissCurrentOverlay -> {
       val s = uiState.value
       playerController.updateState {
           when {
               s.showSettings -> it.copy(showSettings = false)
               s.showSpeedSelection -> it.copy(showSpeedSelection = false)
               s.showAudioSyncDialog -> it.copy(showAudioSyncDialog = false)
               s.showSubtitleSyncDialog -> it.copy(showSubtitleSyncDialog = false)
               s.showSubtitleDownload -> it.copy(showSubtitleDownload = false)
               s.showEqualizer -> it.copy(showEqualizer = false)
               s.showAudioSelection -> it.copy(showAudioSelection = false)
               s.showSubtitleSelection -> it.copy(showSubtitleSelection = false)
               s.showChapterOverlay -> it.copy(showChapterOverlay = false)
               s.showQueueOverlay -> it.copy(showQueueOverlay = false)
               s.showMoreMenu -> it.copy(showMoreMenu = false)
               else -> it
           }
       }
   }
   ```
3. In [VideoPlayerScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt), change `BackHandler` from `DismissDialog` to `DismissCurrentOverlay`
4. **CRITICAL (v2 fix)**: Change ALL `Dialog(onDismissRequest = ...)` callbacks to also use `DismissCurrentOverlay`:
   - `PlayerSettingsDialog` onDismiss (line ~535)
   - `SpeedSelectionDialog` onDismiss
   - `AudioSyncDialog` onDismiss
   - `SubtitleSyncDialog` onDismiss
   - `AudioEqualizerDialog` onDismiss

   **Rationale**: Compose `Dialog` creates a separate window — its `onDismissRequest` fires independently from parent `BackHandler`. If we only change the parent BackHandler but leave Dialog callbacks pointing to `ToggleSettings`/`DismissDialog`, the Dialog's own Back handler will still fire the old action.

   **However**: `BackHandler(enabled = isDialogVisible)` takes priority over `Dialog.onDismissRequest` for system Back key. So the Dialog's `onDismissRequest` primarily fires for the "X" button clicks and outside-dialog taps. Changing both ensures consistency regardless of how user dismisses.
5. Keep `DismissDialog` as-is for programmatic close-all scenarios
6. Result: Back from Quality → controls visible. Back → controls hidden. Back → exit player. (3 presses max from deepest level, down from 4+)

**Files**: `PlayerUiState.kt`, `PlayerControlViewModel.kt`, `VideoPlayerScreen.kt`

**Risks**: None — `DismissCurrentOverlay` is a strict subset of `DismissDialog`. Each Back press closes exactly one layer.

---

## BUG 2 — Menu Qualite: persistence apres retour

**Problem**: Quality panel stays visible after Back.

**Root cause**: `PlayerSettingsDialog` uses `Dialog(onDismissRequest = onDismiss)` which calls `onAction(PlayerAction.ToggleSettings)`. This toggle action doesn't clean up properly.

**Plan** (dependent on BUG 9):
1. Change `onDismiss` callback in [VideoPlayerScreen.kt:535](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L535) from `onAction(PlayerAction.ToggleSettings)` to `onAction(PlayerAction.DismissCurrentOverlay)` — this is already covered by BUG 9 step 4
2. No additional work needed — BUG 9's layered dismiss naturally fixes this

**Files**: `VideoPlayerScreen.kt` (already covered by BUG 9)

---

## BUG 7 — Fleches G/D: seek vs menu

**Problem**: Left/Right D-Pad opens menu instead of seeking when controls are hidden.

**Root cause**: In [VideoPlayerScreen.kt:263-277](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L263), DPAD_LEFT/RIGHT are grouped with CENTER/UP/DOWN — they all just show controls if hidden.

**Plan**:
1. When `!controlsVisible` AND no dialog is open: Left/Right should seek (+-10s) WITHOUT showing controls, return `true`
2. When `isDialogVisible`: return `false` (let dialog handle its own navigation)
3. When `controlsVisible` AND no dialog: return `false` (let Compose focus system navigate between transport buttons normally — user can reach seek bar, subtitles, audio, More via D-Pad)

   ```kotlin
   DPAD_LEFT, DPAD_RIGHT -> {
       if (isDialogVisible) false  // Let dialog handle
       else if (!controlsVisible) {
           val delta = if (keyCode == DPAD_LEFT) -10_000L else 10_000L
           onAction(PlayerAction.SeekTo(uiState.currentPosition + delta))
           true
       } else {
           false  // Controls visible: normal focus navigation
       }
   }
   ```
4. Show a brief seek indicator (e.g., "<<10s" or "10s>>") that auto-hides after 800ms — simple `AnimatedVisibility` composable in `NetflixPlayerControls.kt`

**Files**: `VideoPlayerScreen.kt`, `NetflixPlayerControls.kt`

**v2 fix**: Removed v1's step 2 which said "Controls visible: seek anyway (Plex behavior)" — this would **break ALL D-Pad navigation** in controls. User would never be able to reach subtitle/audio/More buttons.

**Risks**: When seek bar IS focused, its own `onKeyEvent` at [EnhancedSeekBar.kt:220](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/EnhancedSeekBar.kt#L220) already intercepts L/R and returns `true`, so the parent handler won't fire. No conflict.

---

## BUG 8 — Barre d'avancement: fermeture prematuree du menu

**Problem**: Controls auto-hide while interacting with seek bar.

**Root cause**: Seek bar's `onKeyEvent` returns `true`, preventing parent `onKeyEvent` from resetting `lastInteractionTime`.

**Plan**:
1. Add `onInteraction: () -> Unit = {}` callback to `EnhancedSeekBar`
2. Call `onInteraction()` in `onKeyEvent` handler (lines 221-262) before returning `true`
3. Call `onInteraction()` in `pointerInput` drag handlers on drag start and during drag
4. Thread `onInteraction` through [NetflixPlayerControls.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/components/NetflixPlayerControls.kt) to `EnhancedSeekBar`
5. In [VideoPlayerScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt), provide `onInteraction = { lastInteractionTime = System.currentTimeMillis() }`

**Files**: `EnhancedSeekBar.kt`, `NetflixPlayerControls.kt`, `VideoPlayerScreen.kt`

**Risks**: None. Simple callback propagation.

---

## BUG 10 — Informations en-tete du player

**Problem**: Missing server name for movies, missing S01E03 indicator for episodes.

**Root cause**: [NetflixPlayerControls.kt:145-159](app/src/main/java/com/chakir/plexhubtv/feature/player/components/NetflixPlayerControls.kt#L145) — header only shows `grandparentTitle + "Playing from: server"` for episodes. No `SXXEXX` format. No server name for movies.

**Plan**:
1. Rewrite header section in `NetflixPlayerControls.kt`:
   ```kotlin
   Column {
       // Line 1: For episodes "ShowName · S01E03 — Episode Title", for movies just "Title"
       val headerTitle = buildString {
           val gp = media?.grandparentTitle
           if (gp != null) {
               append(gp)
               val s = media.parentIndex
               val e = media.episodeIndex
               if (s != null && e != null) {
                   append(" · S${s.toString().padStart(2,'0')}E${e.toString().padStart(2,'0')}")
               }
               append(" — ")
           }
           append(media?.title ?: unknownTitle)
       }
       Text(text = headerTitle, style = titleLarge, color = White, maxLines = 1)

       // Line 2: Server name (if available)
       val serverName = media?.remoteSources
           ?.firstOrNull { it.serverId == media.serverId }
           ?.serverName
       if (serverName != null) {
           Text(text = serverName, style = bodyMedium, color = White.copy(0.7f))
       }
   }
   ```
2. Output:
   - Movie: Line 1 = `"Inception"`, Line 2 = `"MyPlexServer"`
   - Episode: Line 1 = `"Breaking Bad · S01E03 — ...And the Bag's in the River"`, Line 2 = `"MyPlexServer"`
   - IPTV (no remoteSources): Line 1 = title, no Line 2

**Files**: `NetflixPlayerControls.kt`

**Risks**: `remoteSources` may be empty for direct URL playback (IPTV). Handled with null check.

---

## BUG 6 — Debit reseau: unite d'affichage

**Problem**: Bitrate shown in "kbps" instead of "Mbps".

**Root cause**: [PlayerStatsTracker.kt:116](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerStatsTracker.kt#L116) formats as `"$bitrateKbps kbps"`.

**Plan**:
1. Add helper in `PlayerStatsTracker.kt`:
   ```kotlin
   private fun formatBitrate(kbps: Long): String = when {
       kbps < 0 -> "N/A"
       kbps < 1000 -> "$kbps kbps"
       else -> "${"%.1f".format(kbps / 1000.0)} Mbps"
   }
   ```
2. Apply to `bitrate` field at line 116
3. Apply to peak/avg display in [PerformanceOverlay.kt:73-74](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/PerformanceOverlay.kt#L73) — call same helper or format inline

**Files**: `PlayerStatsTracker.kt`, `PerformanceOverlay.kt`

**Risks**: None. Display-only change.

---

## BUG 3 — Stats detaillees: taille & persistence

**Problem**: Stats overlay too wide. Persists after leaving menu.

**Root cause**: [PerformanceOverlay.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/PerformanceOverlay.kt) has no `maxWidth` constraint.

**Plan**:
1. In [PerformanceOverlay.kt:31-38](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/PerformanceOverlay.kt#L31), add `.widthIn(max = (LocalConfiguration.current.screenWidthDp / 3).dp)` — cap at 1/3 screen width
2. Auto-close on player exit is already handled: `PlayerController.release()` resets state to `PlayerUiState()` default (which has `showPerformanceOverlay = false`)
3. `DismissDialog` / `DismissCurrentOverlay` correctly do NOT close `showPerformanceOverlay` — it's a toggle, not a dialog

**Files**: `PerformanceOverlay.kt`

**Risks**: None. Single constraint.

---

## BUG 5 — Chapitres: piege de focus

**Problem**: Navigating past last chapter traps focus.

**Root cause**: [ChapterOverlay.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/ChapterOverlay.kt) `LazyRow` (line 124) has no focus boundary. DOWN from last item escapes to nothing.

**Plan**:
1. Add `Modifier.focusProperties { down = FocusRequester.Cancel }` to the `LazyRow` — blocks downward focus escape
2. Add `BackHandler(onBack = onDismiss)` inside `ChapterOverlay` — ensures Back always works regardless of focus state
3. Test: last chapter → Down → focus stays. Back → overlay closes.

**Files**: `ChapterOverlay.kt`

**Risks**: None. Standard Compose TV focus boundary pattern.

---

## BUG 4 — Egaliseur non fonctionnel

**Problem**: EQ adjustments produce no audible effect. UI doesn't update reactively.

**Root cause (audio)**: `attachToAudioSession()` creates EQ with `enabled = false` and never enables it. `setBandLevel()` doesn't enable EQ either (only `selectPreset()` does).

**Root cause (UI)**: `_state` is a plain `var EqualizerState`, not a `StateFlow`. Compose cannot observe changes.

**Plan**:
1. Convert `_state` to `MutableStateFlow<EqualizerState>` and expose `val state: StateFlow<EqualizerState>` in [AudioEqualizerManager.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/AudioEqualizerManager.kt)
2. Update all mutations from `_state = _state.copy(...)` to `_state.update { it.copy(...) }`
3. In `setBandLevel()`: add `if (!_state.value.enabled) { equalizer?.enabled = true }` — auto-enable on first user interaction (NOT on attach, to avoid wasting DSP resources)
4. `selectPreset()` already enables EQ (line 90-92) — no change needed there
5. `attachToAudioSession()` keeps `enabled = false` — EQ only activates when user interacts
6. In [VideoPlayerScreen.kt:604](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L604), collect state as Flow: `val eqState by audioEqualizerManager.state.collectAsStateWithLifecycle()`
7. Pass `eqState` (plain value) to `AudioEqualizerDialog` — dialog signature stays unchanged (accepts `EqualizerState`, not `StateFlow`)

**Files**: `AudioEqualizerManager.kt`, `VideoPlayerScreen.kt`

**v2 fix**: Don't auto-enable on attach (wastes DSP). Don't change dialog signature (over-engineering). Collect StateFlow at VideoPlayerScreen level, pass plain value down.

**Risks**: Must ensure `Equalizer` object is created on the right thread. Current code runs on main thread via ViewModel action handler — safe.

---

## BUG 12 — Format d'image 4/3: non applicable

**Problem**: Cannot force 4:3 aspect ratio on legacy content.

**Root cause**: [VideoPlayerScreen.kt:366](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L366) hardcodes `RESIZE_MODE_FIT`. No UI to change it.

**Plan**:
1. Create `AspectRatioMode` enum in [PlayerUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerUiState.kt):
   ```kotlin
   enum class AspectRatioMode(val label: String, val exoResizeMode: Int) {
       FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
       FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
       ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
       fun next(): AspectRatioMode = entries[(ordinal + 1) % entries.size]
   }
   ```
2. Add `aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT` to `PlayerUiState`
3. Add `data object CycleAspectRatio : PlayerAction` to sealed interface
4. Implement in ViewModel: `playerController.updateState { it.copy(aspectRatioMode = it.aspectRatioMode.next()) }`
5. In [VideoPlayerScreen.kt:366](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L366), use `uiState.aspectRatioMode.exoResizeMode` instead of hardcoded constant
6. For MPV: map to `mpvPlayer.setProperty("video-aspect-override", ...)` — FIT="no", FILL="-1", ZOOM=compute from container
7. Add menu item in [PlayerMoreMenu.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/ui/components/PlayerMoreMenu.kt): `Icons.Default.AspectRatio`, label = "Aspect Ratio: ${currentMode.label}"`
8. Add `currentAspectRatioLabel: String` parameter to `PlayerMoreMenu` for display

**v2 fix**: Use enum instead of raw `Int` — prevents leaking ExoPlayer constants into domain state. True 4:3 forced mode is not achievable with ExoPlayer's resize modes alone (they scale the view, not the content aspect ratio). FIT/FILL/ZOOM covers the practical use cases. For forced 4:3, would need `AspectRatioFrameLayout` + custom `Matrix` transform — defer to future work.

**Files**: `PlayerUiState.kt`, `PlayerControlViewModel.kt`, `PlayerMoreMenu.kt`, `VideoPlayerScreen.kt`

**Risks**: MPV aspect override needs JNI call verification. Test on Mi Box S.

---

## Implementation Order

| Priority | Bugs | Session | Rationale |
|----------|------|---------|-----------|
| 1 | **BUG 9** | Session 1 | Foundation — layered dismiss needed by BUG 2 and all dialog fixes |
| 2 | **BUG 2** | Session 1 | Trivial once BUG 9 is done — just change callback |
| 3 | **BUG 7, BUG 8** | Session 1 | Core seek UX — most impactful for daily use |
| 4 | **BUG 1/11** | Session 1 | Focus initial — quick win |
| 5 | **BUG 10** | Session 2 | Header info — isolated UI change |
| 6 | **BUG 6** | Session 2 | Bitrate format — trivial |
| 7 | **BUG 3** | Session 2 | Stats width — single constraint |
| 8 | **BUG 5** | Session 2 | Chapter focus — isolated fix |
| 9 | **BUG 4** | Session 3 | Equalizer — StateFlow refactor + testing |
| 10 | **BUG 12** | Session 3 | Aspect ratio — new feature, needs testing |

---

## Verification

For each bug, test on Mi Box S with standard Android TV remote:

| Bug | Test |
|-----|------|
| **1/11** | Open player → controls appear → focus is on bottom play/pause (not center big button) |
| **2** | More > Quality > press Back → settings close cleanly |
| **3** | Toggle stats → overlay does not exceed 1/3 screen width |
| **4** | More > Equalizer > adjust a band manually → audio effect is audible. Select "Bass Boost" → bass is audible |
| **5** | More > Chapters > last chapter > press Down → focus stays on last item. Press Back → overlay closes |
| **6** | Stats overlay shows "12.5 Mbps" not "12500 kbps" for high-bitrate streams |
| **7** | Controls hidden → press Left → video seeks back 10s, no menu appears. Controls visible → press Left → D-Pad navigates buttons (not seek) |
| **8** | Focus seek bar → hold Right → controls stay visible throughout |
| **9** | More > Quality → Back → controls visible → Back → controls hidden → Back → player exits (3 presses max) |
| **10** | Episode: header = "Breaking Bad · S01E03 — Title", line 2 = "ServerName". Movie: header = "Title", line 2 = "ServerName" |
| **12** | More > Aspect Ratio → cycles through Fit/Fill/Zoom, visible change on content |

## Regression Tests

After all fixes, verify these cross-cutting scenarios:
1. **Focus chain**: Open player → navigate to subtitles → select one → controls still visible → navigate to More → open Speed → Back → controls visible → can navigate to all buttons
2. **Auto-hide**: Open controls → do nothing for 5s → controls auto-hide. Interact with seek bar → controls stay visible while interacting
3. **Stats persistence**: Toggle stats ON → exit player → re-enter player → stats are OFF (state reset)
4. **EQ persistence**: Enable EQ → play → close EQ dialog → audio effect persists. Exit player → EQ released (no audio leak)
