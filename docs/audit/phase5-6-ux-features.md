# Phase 5+6: UX & Features Audit

## Executive Summary

PlexHubTV delivers a surprisingly polished Netflix-like TV experience with strong skeleton loading, consistent focus management, and a professional theme system. The biggest UX gaps are **French-only error messages shown to a predominantly English-speaking user base**, **missing Compose navigation animations between screens**, and **the search screen lacking lowercase key support on its on-screen keyboard**. Feature-wise, the app has most premium building blocks in place; the main gaps are **kids mode content filtering** (profile toggle exists but not enforced), **notifications for new content**, and **Google Play Billing for monetization**.

---

## Phase 5: UX Audit

### Per-Screen Analysis

---

#### Home Screen (`DiscoverScreen.kt`, `NetflixHomeScreen.kt`, `HomeViewModel.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `firstRowFocusRequester` on first content row (`NetflixHomeScreen.kt:47-57`). Focus auto-requested on mount. Snap-to-row scrolling via `focusedRowIndex` + `scrollToItem`. |
| Loading state | OK | `HomeScreenSkeleton` with shimmer — hero placeholder + 4 row skeletons (`DiscoverScreen.kt:163-167`, `Skeletons.kt:188-212`). |
| Empty state | OK | `EmptyState` composable with message, guidance text, and Refresh button (`DiscoverScreenComponents.kt:14-35`). |
| Error state | WARN | `HandleErrors` feeds into `ErrorSnackbarHost`. However, errors display in **French only** (`ErrorExtensions.kt`). The `ErrorState` composable at `DiscoverScreen.kt:170-192` exists but is **never called** in the `when` block (line 130-159) — only snackbar errors are shown. |
| Initial sync | OK | Dedicated `InitialSyncState` with phase-aware messaging, progress bar, and library count (`DiscoverScreen.kt:195-253`). Excellent UX. |
| Animations | OK | `Crossfade` on HomeHeader item transitions (`HomeHeader.kt:49`). Backdrop crossfade in `MainScreen.kt:401-419`. |
| Overscan | OK | Content rows use `PaddingValues(horizontal = 48.dp)` (`NetflixContentRow.kt:70`). Row titles padded `start = 48.dp`. |

**Issues found:**
1. **No error recovery state in main `when` block** — `DiscoverScreen.kt:130-159` has `isInitialSync`, `isLoading`, `isEmpty`, `else` but no explicit error branch. If loading fails AND the lists remain empty, user sees EmptyState with "Make sure your Plex server is running" — acceptable but could be more specific.
2. **Watchlist items show French error** — `HomeViewModel.kt:119-121`: `"Ce titre n'est pas disponible dans votre librairie"` is French. Should use string resources.

---

#### Media Detail Screen (`MediaDetailScreen.kt`, `NetflixDetailTabs.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | Play button has `focusRequester` support (`MediaDetailScreen.kt:250`). Tab row is scrollable and focusable. |
| Loading state | OK | `DetailHeroSkeleton` with shimmer for title, metadata, summary, and action buttons (`Skeletons.kt:105-183`). |
| Empty state | WARN | No explicit empty state when `state.media == null && !state.isLoading`. The screen just shows nothing (blank). |
| Error state | OK | `HandleErrors` + `ErrorSnackbarHost` with retry callback (`MediaDetailScreen.kt:98-100`). |
| Animations | OK | Focus scale animations on action buttons (1.05x-1.1x). Color transitions on focus. |
| Overscan | WARN | The detail screen itself does not apply overscan padding — relies on inner layout. Netflix-style full-bleed backdrop is intentional but action buttons at `padding(horizontal = 16.dp)` (`MediaDetailScreen.kt:228`) are close to edge. |

**Issues found:**
1. **No "media not found" state** — If `state.media` is null after loading completes, user sees blank screen. Should show a "Content not found" message with back navigation.
2. **Source selection dialog** accessible via `SourceSelectionDialog` — good multi-server UX.
3. **Delete button always visible** — `MediaDetailScreen.kt:385-417` shows delete/hide button for all users. Could confuse regular users.

---

#### Season Detail Screen (`SeasonDetailScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `firstEpisodeFocusRequester` on first episode, `seasonInfoFocusRequester` for left panel. `focusProperties` routes LEFT from episodes to season info (`SeasonDetailScreen.kt:317-324`). |
| Loading state | OK | `SeasonDetailSkeleton` with left/right column layout matching actual content (`Skeletons.kt:329-398`). |
| Empty state | OK | Explicit empty state with icon + "No episodes found" (`SeasonDetailScreen.kt:205-228`). |
| Error state | OK | Error icon + message displayed inline (`SeasonDetailScreen.kt:176-204`). |
| Source resolution overlay | OK | Loading overlay during playback preparation (`SeasonDetailScreen.kt:357-376`). |
| Overscan | OK | 48dp padding on content area (`SeasonDetailScreen.kt:234`). |
| Animations | OK | Focus scale (1.03x) and color transitions on episode items. |

**Issues found:**
1. **No retry button on error state** — Season error shows message but no retry/back button. User must use Back key.

---

#### Library Screen (`LibraryComponents.kt`, `LibraryViewModel.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | WARN | `SearchAppBar` has `focusRequester` for text field but the main library grid focus initialization was not visible in the components file read. |
| Loading state | OK | Uses `LibraryGridSkeleton` (inferred from other screens using it). |
| Search | OK | Explicit search trigger (not per-keystroke) — good for TV D-pad (`LibraryComponents.kt:28-29`). |

---

#### Search Screen (`NetflixSearchScreen.kt`, `SearchScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `keyboardFocusRequester` on mount focuses the on-screen keyboard (`NetflixSearchScreen.kt:50-52`). `leftExitFocusRequester` on result rows routes back to keyboard. |
| Loading state | OK | 3x `MediaRowSkeleton` shimmer during search (`NetflixSearchScreen.kt:131-136`). |
| Empty state | OK | Idle message when no query, "No results" message with query shown (`NetflixSearchScreen.kt:117-153`). |
| Error state | OK | Error text shown inline + errors via snackbar host (`NetflixSearchScreen.kt:154-167`). |
| On-screen keyboard | WARN | **Uppercase only** — keys are A-Z, 0-9 (`NetflixOnScreenKeyboard.kt:41-48`). No lowercase, no special characters (accents, hyphens). This limits search for titles with special characters. |
| Overscan | OK | 32dp padding from edges (`NetflixSearchScreen.kt:67-68`). |

**Issues found:**
1. **No lowercase/special character support** on on-screen keyboard. Netflix's keyboard has both rows.
2. **Results grouped by type** in horizontal rows — excellent categorized presentation.

---

#### Settings Screen (`SettingsGridScreen.kt`, `SettingsScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `firstCardFocusRequester` on first settings card (`SettingsGridScreen.kt:28-31`). |
| Layout | OK | Category grid (4+3 cards) — clean, navigable with D-pad. |
| Overscan | OK | 48dp horizontal padding (`SettingsGridScreen.kt:48`). |
| Version display | OK | App version shown at bottom (`SettingsGridScreen.kt:100-113`). |

---

#### Player Screen (`VideoPlayerScreen.kt`, `NetflixPlayerControls.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `focusRequester` on play/pause button in controls. Auto-focus when controls appear (`VideoPlayerScreen.kt:206-215`). Focus restored after dialog dismissal (`VideoPlayerScreen.kt:221-232`). |
| Key handling | OK | Comprehensive D-pad + media key handling: LEFT/RIGHT seek, UP/DOWN/CENTER show controls, MEDIA_PLAY_PAUSE, FAST_FORWARD, REWIND, NEXT, PREVIOUS (`VideoPlayerScreen.kt:256-325`). |
| Controls auto-hide | OK | 5-second auto-hide timer, reset on interaction (`VideoPlayerScreen.kt:198-203`). |
| Error overlay | OK | `PlayerErrorOverlay` with contextual messages (network/codec/generic), retry button, MPV fallback suggestion after 3 failures, close button (`PlayerErrorOverlay.kt`). Auto-focuses retry button. |
| Next episode | OK | `AutoNextPopup` with 15-second countdown, Play Now / Cancel buttons. Auto-focuses Play Now. Player scales to 75% when popup visible (`VideoPlayerScreen.kt:334-347`). |
| Resume indicator | OK | `ResumeToast` non-focusable overlay showing resume position, auto-dismisses after 5s (`VideoPlayerScreen.kt:469-482`). |
| Buffering indicator | OK | `CircularProgressIndicator` centered during buffering (`VideoPlayerScreen.kt:459-467`). |
| Back handling | OK | Layered: error -> dismiss dialog -> hide controls -> close player (`VideoPlayerScreen.kt:234-247`). |
| Subtitle/Audio | OK | Accessible via control bar buttons (subtitle, audio, more menu). |
| Seek bar | OK | `EnhancedSeekBar` with chapters and markers support, trickplay thumbnails. |
| Animations | OK | `AnimatedVisibility` (fadeIn/fadeOut) on controls, slideIn/slideOut on more menu, chapter overlay, queue overlay. Player scale animation with spring physics. |

**Issues found:**
1. **Center big play/pause button is `focusable(false)`** (`NetflixPlayerControls.kt:193`) — intentional to avoid focus confusion, but means D-pad users can't focus it directly. The transport bar play/pause handles this.
2. **No explicit volume control** in the UI controls (volume is handled by system remote keys).

---

#### Auth/Login Screen (`AuthScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `pinButtonFocusRequester` on PIN button at mount. `cancelButtonFocusRequester` during auth. `retryButtonFocusRequester` on error (`AuthScreen.kt:93-94, 186-191, 255-260`). |
| PIN display | OK | Large 72sp PIN code with letter spacing. Progress indicator during polling. |
| Error state | OK | Warning icon + error message + retry button. User-facing messages. |
| Onboarding clarity | WARN | No explanatory text about what PlexHubTV is or what a Plex account is. The title just says a string resource. First-time users who don't know Plex may be confused. |
| Advanced login | OK | Hidden by default, toggle to show token input — good for developers. |

---

#### Favorites Screen (`FavoritesScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `gridFocusRequester` on grid, requested when favorites load (`FavoritesScreen.kt:154-157`). |
| Loading state | OK | `LibraryGridSkeleton` shimmer (`FavoritesScreen.kt:105-111`). |
| Empty state | OK | Heart icon + "No favorites" + hint text (`FavoritesScreen.kt:112-139`). |
| Sort | OK | `FavoritesSortChip` with dropdown for Date Added, Title, Year, Rating. |
| Overscan | OK | `start = 58.dp, end = 58.dp, top = 80.dp` padding. |

---

#### History Screen (`HistoryScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `firstItemFocusRequester` on first grid item with delayed focus request (`HistoryScreen.kt:130-140`). |
| Loading state | OK | `LibraryGridSkeleton` only during initial load, grid persists during refresh to prevent focus loss (`HistoryScreen.kt:87-88`). |
| Empty state | OK | Clock icon + "No watch history" + hint text (`HistoryScreen.kt:96-124`). |
| Paging | OK | Uses Paging 3 `LazyPagingItems` with proper `LoadState` checks. |

---

#### Downloads Screen (`DownloadsScreen.kt`)

| Aspect | Grade | Details |
|--------|-------|---------|
| Focus management | OK | `listFocusRequester` on download list (`DownloadsScreen.kt:127-134`). |
| Loading state | OK | `EpisodeItemSkeleton` repeated 5x (`DownloadsScreen.kt:87-95`). |
| Empty state | OK | Cloud download icon + "No downloads" + hint text (`DownloadsScreen.kt:96-125`). |
| Delete action | OK | Delete icon button per item with error tint. |

**Issues found:**
1. **TopAppBar with `Modifier.padding(top = 56.dp)`** — hardcoded padding to clear Netflix TopBar. Could break if TopBar height changes.
2. **Download feature is "fully stubbed"** per comment in `MediaDetailScreen.kt:268-269`. Delete button exists but download initiation is not implemented.

---

### Cross-Cutting UX Issues

| # | Severity | Issue | Screen(s) | Fix | Effort |
|---|----------|-------|-----------|-----|--------|
| 1 | HIGH | Error messages in French only | All screens | Move `toUserMessage()` to use string resources with locale support | M |
| 2 | HIGH | Some hardcoded French strings | HomeViewModel (line 119,121) | Replace with `stringResource()` | S |
| 3 | MEDIUM | No Compose navigation animations | MainScreen NavHost | Add `enterTransition`/`exitTransition` to NavHost composables | M |
| 4 | MEDIUM | On-screen keyboard uppercase only | Search | Add lowercase toggle or mixed case keys | S |
| 5 | MEDIUM | No "content not found" empty state | MediaDetailScreen | Add null media + not loading state handler | S |
| 6 | MEDIUM | Season error has no retry button | SeasonDetailScreen | Add retry button to error state | S |
| 7 | LOW | `labelSmall` at 12sp may be small at 3m | Typography | Consider 14sp minimum for all labels on TV | S |
| 8 | LOW | Hardcoded 56dp TopBar clearance | Downloads, others | Extract TopBar height as a constant or use real insets | S |
| 9 | LOW | No screen transition animations | All | `fadeIn`/`fadeOut` or `slideIn` on navigation transitions | M |
| 10 | LOW | Debug IDs badge shown on focused cards in debug builds | NetflixMediaCard:286 | Acceptable for debug, but ensure `BuildConfig.DEBUG` is properly stripped in release | - |

---

### D-Pad Navigation Audit

#### Path 1: App Launch -> Home -> Browse Row -> Select Movie -> Detail -> Play -> Back to Detail -> Back to Home

- **App Launch**: SplashScreen -> Auth (if needed) -> LoadingScreen (sync progress) -> MainScreen
- **Home**: TopBar focused items allow DPAD_DOWN to route into content via `contentFocusRequester` (`NetflixTopBar.kt:141-155`). First content row auto-focused via `firstRowFocusRequester`.
- **Browse Row**: LazyRow with `focusGroup()` groups horizontal navigation. Edge handling: LEFT from first item = `FocusRequester.Cancel` (stays), RIGHT from last = `FocusRequester.Cancel` (stays). UP/DOWN handled by `LazyColumn` parent.
- **Select Movie**: Click navigates to MediaDetailRoute via navigation event.
- **Detail**: Play button auto-focused. Back button via `BackHandler` navigates back.
- **Play**: PlayerScreen with full key event handling. Back closes player, returns to Detail.
- **Back to Home**: Standard back navigation. **Focus restoration**: NavHost uses `saveState = true` and `restoreState = true` in `MainScreen.kt:372-377`. LazyRow/LazyColumn state survives via `rememberLazyListState()`.

**Verdict**: OK — Focus chain is well-managed. `BackHandler` at each level. Focus restoration via Compose navigation state saving.

#### Path 2: Home -> Search -> Type Query -> Select Result -> Detail -> Play Episode

- **Search**: Initial focus on keyboard `A` key. Type query by navigating keyboard grid with D-pad. Results appear in horizontal rows on right side.
- **Results navigation**: `leftExitFocusRequester = keyboardFocusRequester` allows LEFT from results to return to keyboard.
- **Select Result**: Click on result card navigates to detail. From detail, navigate to season, select episode, play.

**Verdict**: OK — Keyboard-to-results focus flow is well-designed with explicit focus routing.

#### Path 3: Home -> Settings -> Change Theme -> Back -> Library -> Filter -> Sort

- **Settings**: Grid of category cards. First card auto-focused. DPAD navigates between cards.
- **Category screen**: Individual settings screens with scroll and selection.
- **Back**: `popBackStack()` returns to settings grid.
- **Library**: Grid view with filter/sort chips.

**Verdict**: OK — Settings grid is navigable. Category sub-screens handle back correctly.

#### Path 4: Player Controls -> Seek -> Track Selection -> Next Episode

- **Controls visible**: Play/pause button focused. D-pad LEFT/RIGHT navigates between transport controls.
- **Seek**: When controls hidden, LEFT/RIGHT seeks 10s. Enhanced seek bar accessible when controls visible.
- **Track selection**: Subtitle/Audio buttons in control bar open dialogs.
- **Next episode**: AutoNextPopup with auto-focus on "Play Now" button.

**Verdict**: OK — Comprehensive key handling and layered navigation.

#### Path 5: Login -> Server Selection -> Profile Selection -> Home

- **Login**: PIN button auto-focused. PIN code displayed large. Cancel button focused during auth.
- **Profile Selection**: App profiles screen with grid of profiles.
- **Home**: After profile selection, navigates to main screen with home focused.

**Verdict**: OK — Auth flow has proper focus management at each state.

---

### Accessibility

**Content Descriptions (Semantics):**
- OK: All screens have `testTag` and `semantics { contentDescription }` annotations.
- OK: Cards have type-specific descriptions: "Film: Title", "Serie: Title" (`NetflixMediaCard.kt:121-127`).
- OK: Player controls have string resource-based descriptions.
- OK: Progress bars have percentage descriptions.

**Font Sizes:**
- `labelSmall`: 12sp (defined in `Type.kt:32-37`) — borderline for 3m viewing distance.
- `bodyLarge`: 16sp — acceptable.
- `titleLarge`: 22sp — good for titles.
- Rating badge text: 14sp (overridden from labelSmall).
- Card titles use `labelMedium` with `FontWeight.Bold`.

**Contrast:**
- White text on dark backgrounds throughout — good contrast.
- Muted text uses `alpha = 0.6-0.7f` — may be hard to read at distance.
- Rating badge: Gold star on dark background — distinctive.

**Text Truncation:**
- OK: Titles use `maxLines = 1/2` with `TextOverflow.Ellipsis` throughout.
- OK: Summaries truncated with `maxLines = 3-6`.

---

### Overscan & Safe Areas

- **OverscanSafeArea component** exists (`OverscanSafeArea.kt`) with 48dp padding — but it is NOT widely used. Most screens implement their own padding.
- **Home**: Row content padded 48dp horizontal, bottom 50dp.
- **Season Detail**: 48dp padding on content area.
- **Favorites**: 58dp start/end padding.
- **History**: 48dp padding.
- **Search**: 32dp padding (slightly narrow but acceptable in split-screen layout).
- **Settings**: 48dp padding.
- **Downloads**: 48dp horizontal via `contentPadding`.
- **Player**: Full-screen, controls have 32dp padding.

**Verdict**: Generally safe. The `OverscanSafeArea` composable is under-utilized — screens apply their own padding inconsistently (48dp vs 58dp vs 32dp).

---

### Animations & Transitions

| Animation | Implementation | Quality |
|-----------|---------------|---------|
| Card focus scale | `animateFloatAsState` 1.0 -> 1.08 with FastOutSlowInEasing, 200ms | OK |
| Card border color | `animateColorAsState` Transparent -> White, 200ms | OK |
| Card scrim on focus | `animateFloatAsState` for gradient overlay alpha | OK |
| Home header crossfade | `Crossfade(tween(400))` | OK |
| App backdrop crossfade | `Crossfade(tween(600))` | OK |
| Player controls | `AnimatedVisibility(fadeIn/fadeOut)` | OK |
| Player overlays | `slideInHorizontally`/`slideOutHorizontally` for more menu, queue; `slideInVertically`/`slideOutVertically` for chapters | OK |
| Player scale (NextUp) | `spring(MediumBouncy, StiffnessLow)` for 0.75x scale | OK |
| TopBar visibility | `AnimatedVisibility(fadeIn(300)/fadeOut(300))` | OK |
| Screen transitions | **NONE** — NavHost has no enter/exit transitions | MISSING |

**Key gap**: Screen-to-screen transitions are instant (no animation). Adding `fadeIn/fadeOut` or sliding transitions would significantly improve perceived quality.

---

### Remote Control UX

| Feature | Implementation | Notes |
|---------|---------------|-------|
| Long press on cards | `combinedClickable(onLongClick)` in `NetflixMediaCard.kt:134-139` | OK — Available for context menus |
| D-pad seek in player | `onKeyEvent` handler with DPAD_LEFT/RIGHT | OK |
| Media key handling | PLAY_PAUSE, PLAY, PAUSE, FF, RW, NEXT, PREV | Comprehensive |
| Focus on buttons | `interactionSource.collectIsFocusedAsState()` | OK |
| No touch gestures | No swipe/pinch gestures detected | OK for TV |
| Back key handling | `BackHandler` at every level | OK |

---

## Phase 6: Feature Proposals

### Priority Matrix

| Feature | Effort | Impact | Priority | Status |
|---------|--------|--------|----------|--------|
| Localize error messages to English | S | High | Must-have | Partial (French only) |
| Screen transition animations | M | High | Must-have | Missing |
| Kids mode content filtering | M | High | Must-have | Partial (toggle exists, filtering stubbed) |
| Continue Watching (cross-device) | - | High | - | EXISTS (On Deck from Plex API) |
| Multi-user profiles | - | High | - | EXISTS (AppProfile + PlexHome) |
| Custom screensaver | - | Medium | - | EXISTS (PlexHubDreamService) |
| Playlists | - | Medium | - | EXISTS (PlaylistList + PlaylistDetail) |
| Favorites with sort | - | Medium | - | EXISTS |
| Watch history | - | Medium | - | EXISTS (paged) |
| Theme system (6 themes) | - | Medium | - | EXISTS |
| Viewing statistics dashboard | L | Medium | Nice-to-have | New |
| Notifications for new content | M | Medium | Nice-to-have | New |
| Google Play Billing | L | High | Must-have (for monetization) | New |
| License/activation system | M | Medium | Nice-to-have | New |
| Watchlist sync (Plex cloud) | - | Medium | - | EXISTS |
| On-screen keyboard improvements | S | Medium | Nice-to-have | Partial |
| Personal recommendations (Suggestions) | - | Medium | - | EXISTS (`GetSuggestionsUseCase`) |
| Android TV channels | M | Medium | Nice-to-have | Partial (setting exists) |
| Watch party / shared viewing | XL | Low | Future | New |
| Personal ratings/reviews | M | Low | Future | New |

### Detailed Proposals

#### 1. Localize Error Messages (Must-have, Effort: S)

**Description**: All error messages in `ErrorExtensions.kt` are hardcoded in French. The app should use Android string resources for proper localization.

**Impact**: High — English-speaking users see confusing French error messages like "Impossible de se connecter au reseau".

**Files to modify**:
- `core/model/src/main/java/.../ErrorExtensions.kt` — Replace hardcoded strings with localized lookup
- `core/model/src/main/res/values/strings.xml` — Add English error strings
- `app/src/main/java/.../HomeViewModel.kt:119,121` — Replace hardcoded French strings

---

#### 2. Screen Transition Animations (Must-have, Effort: M)

**Description**: Add enter/exit animations to NavHost screen transitions. Currently, screen switches are instant with no visual transition.

**Impact**: High — Every premium TV app (Netflix, Disney+, Apple TV+) has smooth screen transitions. Instant switches feel jarring.

**Implementation**:
- Add `enterTransition = { fadeIn(tween(300)) }` and `exitTransition = { fadeOut(tween(200)) }` to NavHost or individual `composable()` calls.
- Detail screens could use `slideInHorizontally` for a "push" feel.

**Files to modify**:
- `app/src/main/java/.../feature/main/MainScreen.kt` — NavHost animations
- `app/src/main/java/.../MainActivity.kt` — Top-level NavHost if separate

---

#### 3. Kids Mode Content Filtering (Must-have, Effort: M)

**Description**: The app profile system has a "kids" toggle (`ProfileFormDialog`) and `FilterContentByAgeUseCase` is called in HomeViewModel. However, actual content filtering (blocking R/TV-MA content in Library, Search, etc.) is not enforced everywhere.

**Impact**: High — Parents need confidence that kids profiles actually restrict content.

**Implementation**:
- Apply `FilterContentByAgeUseCase` in Library, Search, Favorites, and History ViewModels
- Show a "Kids" badge on the TopBar when a kids profile is active
- Filter search results server-side if possible, or client-side

**Files to modify**:
- `app/src/main/java/.../feature/library/LibraryViewModel.kt`
- `app/src/main/java/.../feature/search/SearchViewModel.kt`
- `app/src/main/java/.../feature/favorites/FavoritesViewModel.kt`
- `core/ui/src/main/java/.../NetflixTopBar.kt` (kids badge)

---

#### 4. Viewing Statistics Dashboard (Nice-to-have, Effort: L)

**Description**: A dedicated screen showing personal viewing stats: total watch time, most-watched genres, watch time by day/week, completion rates, favorite actors.

**Impact**: Medium — Engaging feature that encourages continued use and differentiates from competitors.

**Implementation**:
- Aggregate data from `HistoryEntity` (already tracks watch events)
- Create `StatsViewModel` and `StatsScreen` with charts/infographics
- Show weekly activity heatmap, genre breakdown pie chart, "Your year in review" summary

---

#### 5. Google Play Billing (Must-have for monetization, Effort: L)

**Description**: Integrate Google Play Billing Library for in-app purchases or subscriptions.

**Implementation tiers**:
- **Free tier**: Basic playback, single server, ad-supported or limited features
- **Premium tier**: Multi-server, offline downloads, profiles, themes, stats
- **Lifetime purchase**: One-time payment option

**Implementation**:
- Add `com.android.billingclient:billing-ktx` dependency
- Create `BillingRepository` and `BillingViewModel`
- Gate premium features behind `isPremium` check
- Add "Upgrade" CTA in settings and feature-locked screens

---

#### 6. Notifications for New Content (Nice-to-have, Effort: M)

**Description**: Notify users when new episodes of shows they follow are available, or when new movies are added to their libraries.

**Impact**: Medium — Drives engagement and re-opens the app.

**Implementation**:
- Extend `LibrarySyncWorker` to diff previous sync results
- Use `NotificationManager` with Android TV notification channels
- Show "New Episode: Breaking Bad S05E12" style notifications
- Respect quiet hours and user preferences

---

#### 7. On-Screen Keyboard Improvements (Nice-to-have, Effort: S)

**Description**: Add lowercase letter support, special characters (hyphen, apostrophe, period), and potentially voice search integration.

**Files to modify**:
- `core/ui/src/main/java/.../NetflixOnScreenKeyboard.kt`

---

#### 8. License/Activation System (Nice-to-have, Effort: M)

**Description**: Alternative to Google Play Billing for sideloaded installations. Hardware-bound license keys that can be purchased from a website.

**Implementation**:
- Generate device fingerprint from Android ID + package signature
- Server-side license validation API
- Offline grace period (7 days)
- `LicenseRepository` with `EncryptedSharedPreferences` storage

---

### Monetization Strategy

| Tier | Price | Features |
|------|-------|----------|
| Free / Trial | $0 (7-day trial) | Single server, basic playback, 2 profiles |
| Premium | $9.99/year or $1.49/month | Multi-server, all themes, offline, unlimited profiles, stats |
| Lifetime | $24.99 one-time | All premium features forever |

**Recommended approach**: Start with a generous free tier to build user base, then add premium features gradually. The existing feature set (multi-server aggregation, themes, profiles, playlists) already justifies a premium tier.

---

## Summary

- **UX Score**: 8/10
- **Critical UX issues**: 2 (French-only errors, no screen transitions)
- **Medium UX issues**: 4 (uppercase-only keyboard, missing empty state in detail, no season retry, inconsistent overscan)
- **Feature gaps**: 3 (kids filtering enforcement, billing, notifications)
- **Proposed new features**: 8
- **Existing features in good shape**: 15+ (profiles, themes, playlists, watchlist, favorites, history, downloads, screensaver, multi-server, enrichment, seek bar with trickplay, chapter markers, auto-next, source selection, on-screen keyboard)

**Overall assessment**: PlexHubTV is a mature, well-architected TV app with Netflix-level UI patterns. The codebase shows strong attention to D-pad navigation, focus management, loading states, and error handling. The main polish items are internationalization, screen transitions, and consistent overscan padding. Feature-wise, the app is surprisingly complete for most premium use cases — monetization infrastructure is the primary gap.
