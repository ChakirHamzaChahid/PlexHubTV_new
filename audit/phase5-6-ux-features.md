# Phase 5 & 6 Audit: UX & TV Design + Feature Proposals

**Date**: 2026-02-25
**Auditor**: UX & Feature Agent
**Scope**: All Screen composables, UI components, and competitive feature analysis

---

## Table of Contents

1. [Phase 5: UX & TV Design Audit](#phase-5-ux--tv-design-audit)
   - [5.1 D-Pad Navigation](#51-d-pad-navigation)
   - [5.2 Focus Indicators](#52-focus-indicators)
   - [5.3 Loading States](#53-loading-states)
   - [5.4 Empty States](#54-empty-states)
   - [5.5 Error States](#55-error-states)
   - [5.6 Transitions & Animations](#56-transitions--animations)
   - [5.7 Overscan Safe Area](#57-overscan-safe-area)
   - [5.8 Typography & Spacing](#58-typography--spacing)
   - [5.9 Remote Control UX](#59-remote-control-ux)
   - [5.10 Onboarding](#510-onboarding)
   - [5.11 Player UX](#511-player-ux)
2. [Phase 6: Feature Proposals](#phase-6-feature-proposals)
   - [6.1 Wow Features](#61-wow-features)
   - [6.2 Monetization Features](#62-monetization-features)
   - [6.3 Retention Features](#63-retention-features)
3. [Priority Matrix](#priority-matrix)

---

## Phase 5: UX & TV Design Audit

### 5.1 D-Pad Navigation

**Overall Assessment: GOOD with gaps**

The app shows solid D-Pad awareness across most screens. Every screen has `FocusRequester` usage for initial focus placement. The Netflix TopBar has proper `focusProperties` to trap UP navigation and route DOWN navigation. However, several gaps remain.

#### Findings

| ID | Screen | Issue | Severity | Priority |
|----|--------|-------|----------|----------|
| NAV-01 | `MainScreen` | Back button from content correctly focuses TopBar, then second Back would exit app. This is proper TV UX. | OK | -- |
| NAV-02 | `NetflixHomeScreen` | Home screen only shows the Hero Billboard. No content rows below it. `onNavigateDown` is a no-op (`/* Stay on Accueil */`). User presses DOWN and nothing happens. This feels broken. The Hub screen has the content rows. | HIGH | P0 |
| NAV-03 | `NetflixDetailScreen` | Play button gets initial focus via `LaunchedEffect`. Tab navigation (Episodes/MoreLikeThis/Collections/Trailers) uses LazyRow with `focusGroup()` for horizontal containment. Good. However, no focus restoration when returning from player/season detail. | MEDIUM | P1 |
| NAV-04 | `SeasonDetailScreen` | Focus goes to first episode on load. Good. But no handling for navigating UP from the first episode to the season info panel (left column). D-Pad LEFT from episode list should move focus to season action buttons. Currently unhandled. | MEDIUM | P1 |
| NAV-05 | `NetflixSearchScreen` | Keyboard-to-results navigation via `onPreviewKeyEvent` handling DOWN/RIGHT. Good. But LEFT from results back to keyboard is handled via `leftExitFocusRequester`. Good implementation. | OK | -- |
| NAV-06 | `FavoritesScreen` | No initial focus request. Grid loads but user must navigate blind until they press a D-Pad direction. Should auto-focus first grid item. | MEDIUM | P1 |
| NAV-07 | `HistoryScreen` | Same as Favorites: no `FocusRequester` for initial focus on first grid item. | MEDIUM | P1 |
| NAV-08 | `DownloadsScreen` | No initial focus on first download item. User lands on an unfocused screen. | MEDIUM | P1 |
| NAV-09 | `SettingsScreen` | No initial focus on first settings tile. The LazyColumn loads but nothing is focused. User must press a direction to start navigating. Should focus the first tile or the back button. | MEDIUM | P1 |
| NAV-10 | `IptvScreen` | Focus goes to channel list after load (`channelListFocusRequester`). Good. Error state focuses retry button. Good. | OK | -- |
| NAV-11 | `CollectionDetailScreen` | Grid gets focus via `gridFocusRequester`. Good. But no back button in TopAppBar is wired for D-Pad (the `ArrowBack` icon exists in the code but `onNavigateBack` is only called from Route, no TopBar nav icon visible). | LOW | P2 |
| NAV-12 | `HubScreen` | First content row gets focus via `firstRowFocusRequester`. Proper focus assignment based on which row exists first (Continue Watching > My List > Hubs). Good implementation. | OK | -- |
| NAV-13 | `LibrariesScreen` | Focus restoration on return from detail via `lastFocusedId` and `focusRestorationRequester`. Excellent Netflix-like behavior. Alphabet sidebar navigation on Title sort. Good. | OK | -- |
| NAV-14 | TopBar | `focusProperties.exit` blocks UP, allows DOWN. `onPreviewKeyEvent` routes DOWN to content when `contentFocusRequester` is provided. But `contentFocusRequester` is never passed from `MainScreen` -- always null. DOWN from TopBar relies on default Compose focus traversal. | LOW | P2 |

#### Wireframe: NAV-02 Fix (Home + Hub Merge)

```
Current:
+--[TopBar]--[Home] [Hub] [Movies]...--+
|                                       |
|  [Home Tab]     |  [Hub Tab]          |
|  Hero Billboard |  Continue Watching  |
|  (only)         |  My List            |
|  DOWN = no-op   |  Recently Added     |
|                 |  Hub Rows...        |
+------------------+--------------------+

Proposed: Merge Home and Hub into single screen
+--[TopBar]--[Home] [Movies] [TV Shows]...--+
|                                            |
|  Hero Billboard (On Deck items)            |
|  ---------------------------------------- |
|  Continue Watching  [>] [>] [>] [>]        |
|  My List            [>] [>] [>] [>]        |
|  Recently Added     [>] [>] [>] [>]        |
|  Popular on Server  [>] [>] [>] [>]        |
|                                            |
+--------------------------------------------+
DOWN from Hero -> Continue Watching
DOWN from rows -> more rows
UP from first row -> Hero buttons
```

---

### 5.2 Focus Indicators

**Overall Assessment: EXCELLENT**

The app has a consistent and polished focus indicator system.

| ID | Component | Finding | Status |
|----|-----------|---------|--------|
| FOC-01 | `NetflixMediaCard` | Scale animation (1.08x) + white border on focus. `drawWithContent` for border avoids layout shift. `zIndex` elevation prevents clipping by neighbors. | EXCELLENT |
| FOC-02 | `NetflixNavItem` | Text color changes: focused = NetflixRed, selected = White, normal = White@70%. Bold when focused/selected. Clean, readable. | GOOD |
| FOC-03 | `NetflixTopBar` icons | Scale (1.15x) + color animation to NetflixRed. Profile avatar gets white border ring. Consistent with nav items. | GOOD |
| FOC-04 | `CollectionCard` | Scale (1.05x) + border color animation + background brightness change. | GOOD |
| FOC-05 | `ExtraCard` | Scale (1.05x) + border animation. Play icon opacity increases on focus. | GOOD |
| FOC-06 | `EnhancedEpisodeItem` | Scale (1.03x) + primary color border + background highlight. Subtle but clear. | GOOD |
| FOC-07 | `ChannelListItem` | Scale (1.05x) + primary color border. | GOOD |
| FOC-08 | `DownloadItem` | Scale (1.05x) + primary border + background tint. | GOOD |
| FOC-09 | `EnhancedSeekBar` | Height increases from 4dp to 6dp when focused. Track brightness increases. | GOOD |
| FOC-10 | `NetflixPlayButton` / `NetflixInfoButton` | White border on focus. Info button background brightens. | GOOD |

**Minor Issues:**
- FOC-11: `SettingsScreen` tiles use `SettingsTile` from `SettingsComponents.kt` - need to verify these have focus indicators. Settings is a focus-heavy screen on TV.
- FOC-12: `FilterButton` in Library has no explicit focus indicator beyond the default OutlinedButton behavior. Should add scale/color animation for consistency.

---

### 5.3 Loading States

**Overall Assessment: EXCELLENT**

The app has proper shimmer skeleton screens for most loading scenarios, which is Netflix-level quality.

| ID | Screen | Loading State | Quality |
|----|--------|--------------|---------|
| LOAD-01 | `HomeScreen/HubScreen` | `HomeScreenSkeleton` - shimmer billboard + row skeletons. | EXCELLENT |
| LOAD-02 | `LibrariesScreen` | `LibraryGridSkeleton` - shimmer grid with title + card placeholders. | EXCELLENT |
| LOAD-03 | `SeasonDetailScreen` | `SeasonDetailSkeleton` - two-column layout with episode item skeletons. | EXCELLENT |
| LOAD-04 | `NetflixDetailScreen` | No skeleton -- relies on backdrop image loading. The content appears progressively. | MEDIUM -- could add `DetailHeroSkeleton` (already exists but unused) |
| LOAD-05 | `FavoritesScreen` | `CircularProgressIndicator` only. No skeleton. | LOW -- should use grid skeleton |
| LOAD-06 | `HistoryScreen` | `CircularProgressIndicator` only. No skeleton. | LOW -- should use grid skeleton |
| LOAD-07 | `DownloadsScreen` | `CircularProgressIndicator` only. No skeleton. | LOW -- should use list skeleton |
| LOAD-08 | `IptvScreen` | `CircularProgressIndicator` only. No skeleton. | LOW -- acceptable for IPTV |
| LOAD-09 | `CollectionDetailScreen` | `CircularProgressIndicator` only. No skeleton. | LOW -- should use grid skeleton |
| LOAD-10 | `InitialSyncState` | Progress bar + percentage + message. Welcoming. | GOOD |
| LOAD-11 | `LoadingScreen` | Progress bar + percentage + message. Good for first sync. | GOOD |
| LOAD-12 | `SearchScreen` | `CircularProgressIndicator` centered. | MEDIUM -- could show result skeletons |

#### Wireframe: LOAD-04 Fix (Detail Screen Skeleton)

```
+------------------------------------------------------+
|                                                      |
|  [Shimmer backdrop full-screen]                      |
|                                                      |
|  +---[Gradient overlay]---+                          |
|  |                        |                          |
|  | [Shimmer title bar]    |                          |
|  | [Shimmer metadata row] |                          |
|  | [Shimmer ====  ====  ] |  <- action buttons       |
|  | [Shimmer summary]      |                          |
|  | [Shimmer summary]      |                          |
|  +------------------------+                          |
|                                                      |
|  [Shimmer tab bar]                                   |
|  [Shimmer card] [card] [card] [card] [card]          |
+------------------------------------------------------+
```

---

### 5.4 Empty States

**Overall Assessment: ADEQUATE but uninspiring**

| ID | Scenario | Current State | Recommendation | Priority |
|----|----------|--------------|----------------|----------|
| EMPTY-01 | Home (no On Deck) | `EmptyState` composable with "No content" text + Refresh button | Add illustration/icon + constructive message: "Start watching to see content here" | P2 |
| EMPTY-02 | Hub (no content) | "No content available" plain text | Add icon + message + link to Library | P2 |
| EMPTY-03 | Library (no items) | Not explicitly handled -- Paging shows empty grid | Add empty state with server configuration prompt | P1 |
| EMPTY-04 | Search (idle) | "Type to search across all your Plex servers" | Good message. Could add trending/suggested searches | P3 |
| EMPTY-05 | Search (no results) | "No results found for [query]" | Add suggestions: check spelling, try different terms | P2 |
| EMPTY-06 | Favorites (empty) | "No favorites yet" centered text | Add heart icon + instructional text "Add favorites from any movie or show" | P2 |
| EMPTY-07 | History (empty) | History empty text from string resource | Add play icon + "Start watching to build your history" | P2 |
| EMPTY-08 | Downloads (empty) | "No downloaded content." | Add download icon + "Download content for offline viewing" | P2 |
| EMPTY-09 | IPTV (no playlist) | Error state with icon, not empty state | Separate empty (no URL configured) from error (URL failed). Empty should guide to settings. | P2 |
| EMPTY-10 | No server connection | `OfflinePlaceholder`: "Content unavailable offline." | Needs offline icon, suggestion to check network, retry button | P1 |
| EMPTY-11 | Season detail (no episodes) | Icon + "No episodes found" | Good. Could add retry button. | P3 |

#### Wireframe: EMPTY-06 Favorites

```
+--------------------------------------------------+
|  My List                                         |
|                                                  |
|              +----------+                        |
|              |   Heart  |  <- 64dp icon           |
|              |   Icon   |                        |
|              +----------+                        |
|                                                  |
|         No items in your list yet                |
|                                                  |
|    Tap the + button on any movie or show         |
|    to add it to your list.                       |
|                                                  |
|          [ Browse Library ]   <- Button          |
+--------------------------------------------------+
```

---

### 5.5 Error States

**Overall Assessment: GOOD**

| ID | Component | Finding | Quality |
|----|-----------|---------|---------|
| ERR-01 | `AppError` hierarchy | Comprehensive sealed class with typed errors for Network, Auth, Media, Playback, Search, Storage. | EXCELLENT |
| ERR-02 | `ErrorSnackbarHost` | Centralized snackbar error display with retry action for retryable errors. Used in Home, Hub, Library, Search. | GOOD |
| ERR-03 | Auth error state | Warning icon (64dp) + error message + Retry button with focus. | GOOD |
| ERR-04 | Loading error state | Warning icon + error message + Retry/Exit buttons. | GOOD |
| ERR-05 | IPTV error state | TV icon + title + detailed message + Retry/Change URL buttons. | EXCELLENT |
| ERR-06 | Player error overlay | `PlayerErrorOverlay` with error type, retry count, MPV switch option. | EXCELLENT |
| ERR-07 | Season detail error | Error icon + title + message. No retry button. | MEDIUM -- add retry |
| ERR-08 | Collection error | Raw error text in red. No icon, no retry button. | LOW -- needs improvement |
| ERR-09 | Detail screen | No visible error handling beyond snackbar. If media load fails, screen may show empty backdrop. | MEDIUM |
| ERR-10 | Settings sync error | `state.syncError` shown as red text below sync section. | ADEQUATE |

**No stacktraces are visible to users.** All errors are mapped through `AppError.toUserMessage()`.

---

### 5.6 Transitions & Animations

**Overall Assessment: GOOD**

| ID | Animation | Implementation | Quality |
|----|-----------|---------------|---------|
| ANIM-01 | Hero billboard rotation | `Crossfade` with 500ms tween. 8-second auto-rotation. | GOOD |
| ANIM-02 | Card focus scale | `animateFloatAsState` tween 200ms with FastOutSlowInEasing | EXCELLENT |
| ANIM-03 | Card border color | `animateColorAsState` tween 200ms | GOOD |
| ANIM-04 | TopBar show/hide | `AnimatedVisibility` fade 300ms | GOOD |
| ANIM-05 | Player controls | `AnimatedVisibility` fadeIn/fadeOut | GOOD |
| ANIM-06 | Auto-next popup | `slideInVertically` + `fadeIn` | GOOD |
| ANIM-07 | Screen transitions | Default NavHost transitions (cut). No slide/fade between screens. | LOW -- add shared element transitions |
| ANIM-08 | Image loading | No crossfade on Coil image load. Images pop in. | MEDIUM -- add `crossfade(true)` to ImageRequest |
| ANIM-09 | Detail screen entry | No entry animation. Content just appears. | MEDIUM -- add fade-in for metadata |
| ANIM-10 | Seekbar expansion | Height animates between 4dp/6dp on focus | GOOD |

#### Recommendation: ANIM-07 Screen Transitions

```kotlin
// Add to NavHost composable calls:
composable(
    route = Screen.Detail.route,
    enterTransition = { fadeIn(tween(300)) + slideInHorizontally { it / 4 } },
    exitTransition = { fadeOut(tween(200)) },
    popEnterTransition = { fadeIn(tween(300)) },
    popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally { it / 4 } }
)
```

---

### 5.7 Overscan Safe Area

**Overall Assessment: MIXED**

| ID | Screen | Padding | Assessment |
|----|--------|---------|------------|
| OVER-01 | `NetflixTopBar` | 48dp horizontal | GOOD (approximates 5% of 1920px = 96px = ~32dp at xxhdpi) |
| OVER-02 | `NetflixContentRow` | 48dp horizontal start | GOOD |
| OVER-03 | `FavoritesScreen` | 58dp start/end, 80dp top | GOOD |
| OVER-04 | `LibrariesScreen` | 58dp start/end | GOOD |
| OVER-05 | `HistoryScreen` | 16dp start/end | TOO SMALL -- content may be cut off on overscan TVs |
| OVER-06 | `DownloadsScreen` | 16dp content padding via Scaffold | TOO SMALL |
| OVER-07 | `SettingsScreen` | Scaffold padding only -- Settings items extend to edges | MEDIUM -- needs 48dp+ horizontal padding |
| OVER-08 | `CollectionDetailScreen` | 16dp grid padding | TOO SMALL |
| OVER-09 | `SeasonDetailScreen` | 48dp all sides | GOOD |
| OVER-10 | `NetflixDetailScreen` | 50dp start, 50dp end on items | GOOD |
| OVER-11 | `IptvScreen` | 16dp content padding | TOO SMALL |
| OVER-12 | `NetflixSearchScreen` | 32dp all sides + 56dp top for TopBar | MEDIUM -- could be larger horizontally |
| OVER-13 | Player controls | 32dp bottom padding, 16dp top/end | GOOD |

**Recommendation**: Standardize overscan safe margins to **48dp horizontal, 27dp vertical** (Google's Android TV recommendation). Extract as theme constants.

---

### 5.8 Typography & Spacing

**Overall Assessment: GOOD**

| ID | Finding | Assessment |
|----|---------|------------|
| TYPO-01 | Font sizes increased to 14sp for card metadata (from 10-11sp). Readable at 3m TV distance. | GOOD |
| TYPO-02 | Hero billboard uses `displayMedium` for title, `bodyLarge` for summary. Appropriate hierarchy. | GOOD |
| TYPO-03 | Detail screen title uses `displayMedium` ExtraBold. Very readable. | GOOD |
| TYPO-04 | Auth PIN display uses 72sp with 8sp letter spacing. Highly visible from across the room. | EXCELLENT |
| TYPO-05 | Settings text sizes rely on Material3 defaults. Some tiles may be too small for TV at default. | MEDIUM |
| TYPO-06 | Player time display uses 12sp. May be too small for 10-foot viewing. Should be 14-16sp. | MEDIUM |
| TYPO-07 | SeekBar chapter labels use 12sp. Same issue. | MEDIUM |
| TYPO-08 | Debug ID badges in NetflixMediaCard use 10sp. Acceptable since these are debug-only. | OK |
| TYPO-09 | Color contrast: White text on dark backgrounds throughout. Consistent. Gold (#FFD700) rating stars, green (#46D369) match percentage. Good contrast. | GOOD |
| TYPO-10 | French content descriptions used in semantics (`"Ecran d'accueil"`, `"Grille de la bibliotheque"`). Should be localized or use English default for accessibility. | LOW |

---

### 5.9 Remote Control UX

**Overall Assessment: GOOD**

| ID | Input | Handling | Screen | Quality |
|----|-------|---------|--------|---------|
| REM-01 | DPAD_CENTER/ENTER | Opens content / confirms action | All | GOOD |
| REM-02 | BACK | TopBar focus then exit (MainScreen), Close dialogs (Player), Navigate back (Detail) | All | GOOD |
| REM-03 | MEDIA_PLAY_PAUSE | Toggle play/pause | Player | GOOD |
| REM-04 | MEDIA_PLAY | Play | Player | GOOD |
| REM-05 | MEDIA_PAUSE | Pause | Player | GOOD |
| REM-06 | MEDIA_FAST_FORWARD | Seek +30s | Player | GOOD |
| REM-07 | MEDIA_REWIND | Seek -10s | Player | GOOD |
| REM-08 | MEDIA_NEXT | Next episode | Player | GOOD |
| REM-09 | MEDIA_PREVIOUS | Previous | Player | GOOD |
| REM-10 | Long press | NOT HANDLED | All | MEDIUM -- could use for context menus |
| REM-11 | Number keys | NOT HANDLED | Player | LOW -- could map to seek percentages |
| REM-12 | DPAD LEFT/RIGHT on seekbar | Seek -10s/+10s | Player | GOOD |

**Missing**: No long-press support for quick actions (e.g., long-press on a card to add to favorites, long-press on episode to download). This is a common Netflix/YouTube TV pattern.

---

### 5.10 Onboarding

**Overall Assessment: GOOD foundation, needs polish**

| ID | Step | Current State | Recommendation | Priority |
|----|------|--------------|----------------|----------|
| ONB-01 | Splash | Netflix-style video intro with Skip button. Auto-focused Skip button. Handles video error fallback. | EXCELLENT | -- |
| ONB-02 | Auth | PIN-based login (TV-optimized, no keyboard needed). Clear instructions with URL and PIN code. Progress indicator. | GOOD | -- |
| ONB-03 | Token login | Hidden behind "Advanced login" toggle. Good for dev/test without cluttering UX. | GOOD | -- |
| ONB-04 | Auto-login | `BuildConfig.PLEX_TOKEN` auto-submits on launch. Good for development. | OK | -- |
| ONB-05 | Library Selection | Screen exists for choosing which libraries to sync. Navigated to from Splash/Loading. | GOOD | -- |
| ONB-06 | Initial Sync | Progress bar with percentage and descriptive messages. "Welcome to PlexHubTV" title. | GOOD | -- |
| ONB-07 | First Home | No first-use tutorial or feature highlights. User lands directly on Home. | MEDIUM -- add optional feature discovery | P2 |
| ONB-08 | Profile Selection | `AppProfileSelectionScreen` exists but is incomplete (skeleton UI). | INCOMPLETE | P2 |
| ONB-09 | Error during onboard | Loading error state has Retry + Exit buttons. Good recovery flow. | GOOD | -- |

#### Wireframe: ONB-07 First-Use Overlay

```
+--------------------------------------------------+
|  [TopBar with pulse animation on each element]    |
|                                                   |
|  +--[Tooltip bubble]--+                           |
|  | Navigate between   |                           |
|  | screens using the  |                           |
|  | top bar            |                           |
|  +--------v-----------+                           |
|                                                   |
|  Hero Billboard                                   |
|                                                   |
|  [ Play ] [ More Info ]                           |
|  +--[Tooltip]--------+                            |
|  | Press OK to play  |                            |
|  | or get details    |                            |
|  +-------------------+                            |
|                                                   |
|         [ Got it! ]  <- dismiss button            |
+--------------------------------------------------+
```

---

### 5.11 Player UX

**Overall Assessment: VERY GOOD**

| ID | Feature | Implementation | Quality |
|----|---------|---------------|---------|
| PLY-01 | Auto-hide controls | 5-second timeout when playing. Resets on any D-Pad input. | GOOD |
| PLY-02 | Show controls when paused | `shouldShowControls = controlsVisible OR (!isPlaying AND !isBuffering)` | GOOD |
| PLY-03 | Next episode popup | `AutoNextPopup` with thumbnail, "Play Now" (auto-focused) + "Cancel". Slide-in animation. | EXCELLENT |
| PLY-04 | Skip intro/credits | `SkipMarkerButton` positioned at bottom-right, triggered by `visibleMarkers`. | GOOD |
| PLY-05 | Chapter markers | Visual separators on seekbar. Thumbnail preview during scrubbing. Chapter name display. | EXCELLENT |
| PLY-06 | Seek preview | Chapter-based thumbnails during drag. Time display follows scrub position. | GOOD |
| PLY-07 | Track selection | Audio/Subtitle selection dialogs. Separate from main settings. | GOOD |
| PLY-08 | Playback speed | Speed selection dialog with common presets. | GOOD |
| PLY-09 | Audio/Subtitle sync | Delay adjustment dialogs for audio and subtitle sync. | GOOD |
| PLY-10 | Performance overlay | Toggle-able stats overlay (bitrate, codec, buffer). | GOOD |
| PLY-11 | Error recovery | Error overlay with retry, switch to MPV option, close. Network retry count shown. | EXCELLENT |
| PLY-12 | Buffering indicator | Centered `CircularProgressIndicator` with accessibility label. | GOOD |
| PLY-13 | Dual player engine | ExoPlayer (primary) + MPV (fallback). Seamless switch on codec errors. | EXCELLENT |
| PLY-14 | Keep screen on | `keepScreenOn = true` on both ExoPlayer and MPV views. | GOOD |

**Missing features:**

| ID | Feature | Impact | Priority |
|----|---------|--------|----------|
| PLY-15 | Seek preview thumbnails (non-chapter) | HIGH -- Netflix has frame-accurate thumbnails during seek | P2 |
| PLY-16 | Binge-watching countdown | MEDIUM -- Netflix shows "Next episode in 5...4...3..." countdown with progress ring | P1 |
| PLY-17 | "Are you still watching?" prompt | MEDIUM -- After 3+ consecutive episodes, ask to prevent idle playback | P2 |
| PLY-18 | Picture-in-Picture | LOW -- PiP comment in code says "if necessary". Android TV supports PiP. | P3 |
| PLY-19 | Resume playback indicator | HIGH -- When opening player, show "Resuming from 1:23:45" toast briefly | P1 |

#### Wireframe: PLY-16 Binge Countdown

```
+------------------------------------------------------+
|                                                      |
|                 [Video playing]                       |
|                                                      |
|                              +---[Next Episode]---+  |
|                              |                    |  |
|                              | [Thumb] S2:E4     |  |
|                              | "The Reckoning"   |  |
|                              |                    |  |
|                              |   Playing in: (5)  |  |
|                              |   [==========]     |  |
|                              |                    |  |
|                              | [Play Now] [Cancel]|  |
|                              +--------------------+  |
+------------------------------------------------------+
```

---

## Phase 6: Feature Proposals

### 6.1 Wow Features

#### WOW-01: Continue Watching Intelligence
**Effort: M | Impact: HIGH | Priority: P0**

Current state: On Deck items exist but are simple. Enhance with:
- "X minutes remaining" on each card (already implemented for WIDE cards)
- Smart ordering: most recently watched first, then by last-aired unfinished show
- "Remove from Continue Watching" via long-press context menu
- Cross-device resume position sync (already working via Plex scrobble)

#### WOW-02: Personalized Recommendations Row
**Effort: L | Impact: HIGH | Priority: P1**

- "Because You Watched [Title]" rows with similar content
- "Trending on Your Server" based on other Plex Home users' activity
- "New Episodes" row for tracked shows
- Leverages existing Hub API from Plex (`/hubs`)

#### WOW-03: Android TV Channels Integration
**Effort: M | Impact: HIGH | Priority: P1**

The settings toggle for TV channels exists (`isTvChannelsEnabled`). Implementation needed:
- "Continue Watching" channel on Android TV home screen
- "Recommended" channel
- "New Releases" channel
- Use `TvInputService` / `PreviewChannelHelper`
- Each channel card opens directly to content or player

#### WOW-04: Kids Mode / Parental Controls
**Effort: L | Impact: HIGH | Priority: P2**

Profile system partially exists. Extend with:
- Kids profile with emoji avatar picker
- Content filtering by rating (G, PG, PG-13)
- PIN protection to exit Kids mode
- Simplified UI with larger cards, brighter colors
- Restricted navigation (no Settings, no Live TV)

#### WOW-05: Screensaver / Ambient Mode
**Effort: S | Impact: MEDIUM | Priority: P2**

- Activate after 5 minutes of inactivity
- Slow-pan through artwork from library
- Show current time and date
- Dim overlay
- Any D-Pad input dismisses

#### WOW-06: Viewing Stats Dashboard
**Effort: M | Impact: MEDIUM | Priority: P3**

- Total watch time this week/month
- Most-watched genres
- Movie vs TV split
- "Year in Review" style annual report
- Completion rates per show

#### WOW-07: Multi-Server Dashboard
**Effort: S | Impact: MEDIUM | Priority: P2**

Enhance server visibility in Settings with a dedicated dashboard:
- Server health indicators (online/offline/latency)
- Library size per server
- Last sync time per server
- Quick connection test

### 6.2 Monetization Features

#### MON-01: Free vs Premium Tier
**Effort: L | Impact: HIGH | Priority: P1**

```
FREE TIER:
- 1 server connection
- 1 profile
- Standard video quality (720p max)
- Basic library browsing
- Standard player (ExoPlayer only)

PREMIUM TIER ($4.99/month or $39.99/year):
- Unlimited servers
- Unlimited profiles
- Maximum quality (4K/HDR passthrough)
- MPV fallback engine
- Offline downloads
- IPTV/Live TV
- Android TV channels
- Custom themes
- Viewing stats
- Skip intro/credits markers
- Audio/subtitle sync
- Playback speed control
```

#### MON-02: Google Play Billing Integration
**Effort: M | Impact: HIGH | Priority: P1**

- In-app subscription via Google Play Billing Library v6
- Monthly and annual plans
- Free trial (7 or 14 days)
- Grace period for expired subscriptions
- Restore purchases on new device
- `BillingClient` integration in `:data` module

#### MON-03: One-Time Purchase Option
**Effort: S | Impact: MEDIUM | Priority: P2**

- Lifetime license ($99.99) as alternative to subscription
- Appeals to users who prefer owning software
- Reduces churn from subscription fatigue

#### MON-04: Promo Codes / Referral System
**Effort: M | Impact: MEDIUM | Priority: P3**

- Generate shareable promo codes
- "Give 1 month, get 1 month" referral program
- Partner with Plex community forums for launch promotion

### 6.3 Retention Features

#### RET-01: Push Notifications
**Effort: M | Impact: HIGH | Priority: P1**

- "New episode of [Show] is available" when LibrarySync detects new content
- "Continue watching [Title] - X minutes remaining"
- Weekly digest: "X new movies, Y new episodes added this week"
- Firebase Cloud Messaging or local notifications via WorkManager
- User-configurable notification preferences in Settings

#### RET-02: Custom Lists / Watchlist
**Effort: M | Impact: HIGH | Priority: P1**

Current: Favorites (binary toggle) and Plex Watchlist sync exist.
Enhance:
- Multiple named lists ("Want to Watch", "Best of 2025", "Family Movie Night")
- Drag-to-reorder within lists
- Share lists with other Plex Home users
- "Add to List" from detail screen and long-press context menu

#### RET-03: Personal Reviews & Ratings
**Effort: S | Impact: MEDIUM | Priority: P2**

- 5-star rating after finishing a movie/show
- Optional text review
- Sync rating back to Plex server (`PUT /:/rate`)
- Show user's own rating vs community rating

#### RET-04: Watch Party / Social Features
**Effort: XL | Impact: MEDIUM | Priority: P3**

- Synchronized playback with remote friends
- Real-time chat overlay during playback
- Reactions (thumbs up, laugh, etc.)
- Requires WebSocket server or Firebase Realtime Database
- Complex but highly differentiating

#### RET-05: Content Calendar
**Effort: M | Impact: MEDIUM | Priority: P2**

- Calendar view showing when new episodes air for tracked shows
- "Coming Soon" section based on Plex metadata
- Reminder notifications for upcoming episodes

#### RET-06: Download Queue Intelligence
**Effort: S | Impact: MEDIUM | Priority: P2**

- Auto-download next episode of watched shows over WiFi
- Quality presets for downloads (SD/HD/Original)
- Storage management with auto-delete of watched downloads
- Download scheduling (e.g., overnight only)

---

## Priority Matrix

### P0 -- Must Fix Before Release (Blocking)

| ID | Category | Issue | Effort |
|----|----------|-------|--------|
| NAV-02 | D-Pad | Home screen DOWN navigation is a no-op. Hub has all content rows. Merge or fix navigation between them. | M |

### P1 -- Critical for Polish

| ID | Category | Issue | Effort |
|----|----------|-------|--------|
| NAV-03 | D-Pad | Detail screen: no focus restoration on return from child screens | S |
| NAV-04 | D-Pad | Season detail: no LEFT navigation from episodes to season info | S |
| NAV-06 | D-Pad | Favorites: no initial focus | S |
| NAV-07 | D-Pad | History: no initial focus | S |
| NAV-08 | D-Pad | Downloads: no initial focus | S |
| NAV-09 | D-Pad | Settings: no initial focus | S |
| OVER-05 | Overscan | History: 16dp padding too small | S |
| OVER-06 | Overscan | Downloads: 16dp padding too small | S |
| OVER-08 | Overscan | Collection: 16dp padding too small | S |
| OVER-11 | Overscan | IPTV: 16dp padding too small | S |
| EMPTY-10 | Empty State | Offline placeholder needs icon + retry | S |
| PLY-16 | Player | Binge countdown (Netflix "Next in 5...") | M |
| PLY-19 | Player | Resume position toast | S |
| WOW-01 | Feature | Continue Watching intelligence | M |
| WOW-03 | Feature | Android TV Channels integration | M |
| MON-01 | Monetize | Free vs Premium tier definition | L |
| MON-02 | Monetize | Google Play Billing | M |
| RET-01 | Retention | Push notifications for new content | M |
| RET-02 | Retention | Custom named lists | M |

### P2 -- Nice to Have for Launch

| ID | Category | Issue | Effort |
|----|----------|-------|--------|
| NAV-11 | D-Pad | Collection: back button handling | S |
| NAV-14 | D-Pad | TopBar contentFocusRequester not wired | S |
| LOAD-04 | Loading | Detail screen skeleton | S |
| LOAD-05 | Loading | Favorites skeleton | S |
| ANIM-07 | Animation | Screen transition animations | S |
| ANIM-08 | Animation | Image crossfade on load | S |
| EMPTY-01-09 | Empty States | Polish all empty states with icons + guidance | M |
| ONB-07 | Onboard | First-use feature discovery | M |
| PLY-15 | Player | Seek preview thumbnails | L |
| PLY-17 | Player | "Still watching?" prompt | S |
| WOW-04 | Feature | Kids mode / parental controls | L |
| WOW-05 | Feature | Screensaver / ambient mode | S |
| WOW-07 | Feature | Multi-server dashboard | S |
| MON-03 | Monetize | Lifetime purchase option | S |
| RET-03 | Retention | Personal reviews & ratings | S |
| RET-05 | Retention | Content calendar | M |
| RET-06 | Retention | Download queue intelligence | S |

### P3 -- Post-Launch Roadmap

| ID | Category | Issue | Effort |
|----|----------|-------|--------|
| TYPO-06 | Typography | Player time text size | S |
| PLY-18 | Player | Picture-in-Picture | M |
| WOW-02 | Feature | Personalized recommendation rows | L |
| WOW-06 | Feature | Viewing stats dashboard | M |
| MON-04 | Monetize | Promo codes / referrals | M |
| RET-04 | Retention | Watch party / social | XL |

---

## Summary

### Strengths

1. **Focus system is best-in-class** -- consistent scale + border + color animations across all interactive components
2. **Shimmer skeletons** already exist for Home, Library, Season detail -- Netflix-level loading UX
3. **Player is feature-complete** -- dual engine, chapters, markers, skip intro, auto-next, track selection, performance overlay, error recovery
4. **Error handling is typed and comprehensive** -- `AppError` hierarchy with retryable classification
5. **Remote control support** covers all standard media keys
6. **Hero billboard** with auto-rotation, crossfade, proper focus management
7. **Library** has focus restoration, alphabet sidebar, filters, sort, grid/list view toggle
8. **Accessibility** -- test tags and content descriptions on most components

### Critical Gaps

1. **Home screen is empty below the hero** -- all content rows are on the separate Hub tab. This makes the first screen feel barren.
2. **6 screens lack initial focus** (Favorites, History, Downloads, Settings, Collection, and partially Search results)
3. **4 screens have inadequate overscan margins** (16dp instead of 48dp)
4. **No monetization infrastructure** -- no billing, no tier system
5. **Profile system is incomplete** -- skeleton UI only for app profiles
6. **No push notifications** for new content

### Estimated Total Effort for P0+P1

- **Small tasks (S)**: 12 items, ~1-2 hours each = ~18 hours
- **Medium tasks (M)**: 7 items, ~4-8 hours each = ~42 hours
- **Large tasks (L)**: 1 item (monetization tier), ~16-24 hours = ~20 hours

**Total P0+P1 estimate: ~80 hours of development work**
