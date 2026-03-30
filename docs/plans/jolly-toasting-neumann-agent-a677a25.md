# Wholphin Android TV Application - Comprehensive Architectural Analysis

**Repository**: https://github.com/damontecres/Wholphin
**Language**: Kotlin (98.2%)
**License**: GPL-2.0
**Min SDK**: 23 (Android 6.0) | **Target/Compile SDK**: 36
**Server Compatibility**: Jellyfin 10.10.x / 10.11.x

---

## 1. Architecture Patterns

### 1.1 Overall Pattern: MVVM + Service Layer + Hilt DI

Wholphin follows a **MVVM (Model-View-ViewModel)** architecture with a thick **service layer** sitting between ViewModels and the data/API layer. Unlike many Android apps that use a Repository-per-entity pattern, Wholphin uses a **service-per-concern** pattern where each service encapsulates a specific domain concern (e.g., `BackdropService`, `ScreensaverService`, `FavoriteWatchManager`, `MediaReportService`).

**Key architectural characteristics:**
- **No UseCase layer** -- ViewModels inject services directly
- **Single Activity** (`MainActivity`) hosting Jetpack Compose UI
- **Navigation3** (experimental AndroidX Navigation3) for backstack management -- NOT the standard Jetpack Navigation Component
- **Protocol Buffers** for DataStore preferences (not JSON/XML)
- **Jellyfin SDK** as the primary API client (not raw HTTP)

### 1.2 Dependency Injection: Hilt

DI is configured in two Hilt modules:

**`AppModule`** (`services/hilt/AppModule.kt`):
- Provides `Jellyfin` SDK instance, `ApiClient`, `OkHttpClient` (standard and auth variants)
- Two qualified OkHttpClients: `@StandardOkHttpClient` and `@AuthOkHttpClient` (adds Authorization header via interceptor)
- Qualified coroutine scopes: `@IoCoroutineScope`, `@DefaultCoroutineScope`
- Qualified dispatchers: `@IoDispatcher`, `@DefaultDispatcher`
- `WorkManager`, `SeerrApi`, `RememberTabManager`

**`DatabaseModule`** (`services/hilt/DatabaseModule.kt`):
- Provides Room `AppDatabase` singleton
- Individual DAO providers: `JellyfinServerDao`, `ItemPlaybackDao`, `ServerPreferencesDao`, `LibraryDisplayInfoDao`, `PlaybackLanguageChoiceDao`, `SeerrServerDao`, `PlaybackEffectDao`
- Proto DataStore for `AppPreferences`

### 1.3 Application Class

`WholphinApplication` is `@HiltAndroidApp`-annotated, implements `Configuration.Provider` for WorkManager, and sets up:
- **Timber logging** (DebugTree in debug, filtered INFO+ in release)
- **StrictMode** (network detection on main thread, death-on-network in debug)
- **ACRA crash reporting** (dialog-based, sends crash reports to Jellyfin server via `clientLogApi`)
- **Compose diagnostic stack traces** in debug

---

## 2. Jellyfin API Consumption

### 2.1 SDK-Based API Access

Wholphin uses the **official Jellyfin Kotlin SDK** (`org.jellyfin.sdk`) rather than raw HTTP calls. The SDK is initialized in `AppModule`:

```kotlin
createJellyfin {
    context = context
    clientInfo = ClientInfo(name = "Wholphin", version = BuildConfig.VERSION_NAME)
    deviceInfo = androidDevice(context)
    apiClientFactory = CoroutineContextApiClientFactory(OkHttpFactory(okHttpClient))
    socketConnectionFactory = okHttpFactory
    minimumServerVersion = Jellyfin.minimumVersion
}
```

The SDK provides typed API extensions like `api.userLibraryApi`, `api.imageApi`, `api.libraryApi`, `api.clientLogApi`, `api.userViewsApi`, etc.

### 2.2 Authentication Flow

**`ServerRepository`** manages authentication state:
- `CurrentUser` data class holds server + user + accessToken
- `EqualityMutableLiveData` for reactive current-user state
- Session restoration via `restoreSession()` on app start
- Quick Connect authorization support via `authorizeQuickConnect()`
- PIN protection per user profile
- Auth header injection via `@AuthOkHttpClient` OkHttp interceptor (using `AuthorizationHeaderBuilder`)

### 2.3 Server Discovery & Setup

The setup flow uses a separate `SetupDestination` sealed class with `NavDisplay`:
- `SetupDestination.Loading` -- spinner during session restoration
- `SetupDestination.ServerList` -- `SwitchServerContent`
- `SetupDestination.UserList` -- `SwitchUserContent` with server parameter
- `SetupDestination.AppContent` -- main app content after authentication

---

## 3. Room Database Schema

### 3.1 Database Configuration

**Version**: 31 (with export schema enabled)
**Name**: `wholphin`
**Migration strategy**: AutoMigration for most versions (3->4, 4->5, ..., 30->31), manual Migration for 2->3

### 3.2 Entities (10 total)

| Entity | Table | Purpose |
|--------|-------|---------|
| `JellyfinServer` | `servers` | Server connection info (id, name, url, version) |
| `JellyfinUser` | `users` | User credentials with FK to servers (id, name, serverId, accessToken, pin) |
| `ItemPlayback` | `ItemPlayback` | Per-user track selection memory (sourceId, audioIndex, subtitleIndex) |
| `NavDrawerPinnedItem` | -- | User-pinned navigation drawer items |
| `LibraryDisplayInfo` | `LibraryDisplayInfo` | Per-user per-library sort/filter/view preferences |
| `PlaybackEffect` | -- | Audio effects and equalizer settings |
| `PlaybackLanguageChoice` | -- | Remembered language preferences for audio/subtitles |
| `ItemTrackModification` | `ItemTrackModification` | Audio delay modifications per track |
| `SeerrServer` | -- | Jellyseerr server connection details |
| `SeerrUser` | -- | Jellyseerr user credentials |

### 3.3 Relationships

- `JellyfinUser` -> `JellyfinServer` (FK on `serverId`, CASCADE delete)
- `ItemPlayback` -> `JellyfinUser` (FK on `userId` via `rowId`, CASCADE delete/update)
- `LibraryDisplayInfo` -> `JellyfinUser` (FK on `userId`, CASCADE delete/update)
- `JellyfinServerUsers` is an `@Embedded` + `@Relation` composite (server + its users)

### 3.4 DAOs (7 total)

| DAO | Key Operations |
|-----|---------------|
| `JellyfinServerDao` | `addOrUpdateServer()`, `addOrUpdateUser()`, `getServers()`, `deleteServer()` |
| `ItemPlaybackDao` | `getItem()`, `saveItem()`, `deleteItem()`, `getTrackModifications()` |
| `ServerPreferencesDao` | Server-specific preferences (referenced in DB module) |
| `LibraryDisplayInfoDao` | `getItem()`, `saveItem()`, `getItems()` -- per-user per-library view state |
| `PlaybackLanguageChoiceDao` | Audio/subtitle language memory |
| `SeerrServerDao` | Jellyseerr server CRUD |
| `PlaybackEffectDao` | Playback audio effects |

### 3.5 Type Converters

`Converters` class handles: `UUID <-> String` (dashes removed), `ItemSortBy <-> String`, `SortOrder <-> String`, `GetItemsFilter <-> JSON`, `ViewOptions <-> JSON`

### 3.6 Key Design Difference vs PlexHubTV

Wholphin does **NOT** cache media items locally. There is no `MediaEntity` table. All content browsing is done via live Jellyfin API calls. The Room database stores only:
- Server/user credentials
- User preferences (sort, filter, view options)
- Playback state (track selections, language preferences)
- Pinned navigation items

This is a fundamentally different approach from PlexHubTV's `LibrarySyncWorker` + local `MediaEntity` cache.

---

## 4. UI Component Library

### 4.1 Framework: Jetpack Compose for TV

Wholphin is built entirely with **Jetpack Compose** using `androidx.tv.material3` (TV Material Design 3 components). There is no Leanback library usage.

### 4.2 All Screens

**Home & Navigation:**
- `HomePage` / `HomeViewModel` -- Configurable home screen with lazy column of rows
- `SearchPage` -- Voice + text search
- `NavDrawer` / `NavDrawerViewModel` -- Modal navigation drawer with profile, libraries, search, settings

**Detail Screens:**
- `MovieDetails` / `MovieDetailsHeader` / `MovieViewModel` -- Movie detail page
- `SeriesDetails` / `SeriesOverview` / `SeriesOverviewContent` / `SeriesViewModel` -- TV series with season/episode browsing
- `EpisodeDetails` / `EpisodeDetailsHeader` / `EpisodeViewModel` -- Individual episode details
- `PersonPage` -- Actor/director details
- `PlaylistDetails` / `PlaylistList` -- Playlist management
- `FavoritesPage` -- Aggregated favorites view
- `DebugPage` -- Debug information

**Collection Folder Screens (library browsing):**
- `CollectionFolderMovie` -- Movie library grid
- `CollectionFolderTv` -- TV show library grid
- `CollectionFolderBoxSet` -- Box set collections
- `CollectionFolderGeneric` -- Generic collection grid
- `CollectionFolderPlaylist` -- Playlist collection
- `CollectionFolderPhotoAlbum` -- Photo album grid
- `CollectionFolderRecordings` -- DVR recordings
- `CollectionFolderLiveTv` -- Live TV channel grid

**Jellyseerr/Discover:**
- `DiscoverPage` / `SeerrDiscoverPage` / `SeerrRequestsPage` -- Browse Jellyseerr content
- `DiscoverMovieDetails` / `DiscoverMovieDetailsHeader` / `DiscoverMovieViewModel`
- `DiscoverSeriesDetails` / `DiscoverSeriesViewModel`
- `DiscoverPersonPage`

**Live TV:**
- `TvGuideGrid` / `TvGuideHeader` -- EPG grid
- `DvrSchedule` -- DVR scheduling
- `ProgramDialog` -- Program details dialog
- `LiveTvViewModel` / `LiveTvViewOptionsDialog`

**Playback:**
- `PlaybackPage` / `PlaybackControls` / `PlaybackOverlay` -- Full playback experience
- `PlaybackDialog` / `DownloadSubtitlesDialog` -- Playback settings
- `SeekBar` / `SeekBarState` / `SeekAcceleration` -- Custom seek bar with trickplay
- `NextUpEpisode` -- Auto-play next episode overlay
- `PlaybackDebugOverlay` -- Debug stats overlay

**Settings:**
- `PreferencesPage` -- Main settings screen
- `SubtitleStylePage` -- Subtitle customization (including HDR variant)
- `HomeSettingsPage` / `HomeRowSettings` / `HomeSettingsAddRow` / `HomeSettingsGlobal` -- Home page customization

**Setup:**
- `SwitchServerContent` -- Server selection/addition
- `SwitchUserContent` -- User selection with PIN
- `InstallUpdatePage` -- In-app update installer

**Photo:**
- `SlideshowPage` -- Photo slideshow with configurable duration

### 4.3 Reusable Card Components

- `BannerCard` -- Standard media card
- `EpisodeCard` -- Episode-specific card
- `GenreCard` -- Genre tag card
- `GridCard` -- Grid layout card
- `PersonCard` -- Actor/person card
- `SeasonCard` -- Season card
- `ChapterCard` -- Video chapter card
- `DiscoverItemCard` -- Jellyseerr item card
- `ItemCardImage` -- Base image card component
- `WatchedIcon` -- Watched/unplayed overlay

### 4.4 Reusable Row Components

- `ItemRow` -- Horizontal scrolling item row
- `PersonRow` -- Horizontal person row
- `ChapterRow` -- Chapter marker row
- `ExtrasRow` -- Extras/bonus content row
- `FocusableItemRow` -- TV-optimized focusable row

### 4.5 Shared UI Components (30+)

`AppScreensaver`, `Button`, `CircularProgress`, `CollectionFolderGrid`, `Dialogs`, `EditTextBox`, `ErrorMessage`, `FilterByButton`, `GenreCardGrid`, `GenreText`, `ItemGrid`, `LicenseInfo`, `LoadingRow`, `Optional`, `OverviewText`, `PlayButtons`, `QuickDetails`, `Rating`, `RecommendedContent`, `RecommendedMovie`, `RecommendedTvShow`, `SelectedLeadingContent`, `SeriesComponents`, `SliderBar`, `SortByButton`, `SwitchWithLabel`, `TabRow`, `TableRow`, `TimeDisplay`, `TitleValueText`, `TrailerDialog`, `TvDropdownMenuItem`, `VideoStreamDetails`, `ViewOptionsDialog`, `VoiceInputManager`, `VoiceSearchButton`

---

## 5. Navigation System

### 5.1 Navigation3 (Experimental)

Wholphin uses **AndroidX Navigation3** (`androidx.navigation3`), which is a newer experimental navigation API (distinct from the standard Jetpack Navigation Component). This is a notable and forward-looking choice.

**`NavigationManager`** (singleton service):
- Maintains a `MutableList<NavKey>` backstack
- Methods: `navigateTo()`, `navigateToFromDrawer()`, `goBack()`, `goToHome()`, `reloadHome()`, `replace()`
- Backstack manipulation is manual (not declarative NavHost)

**`Destination`** sealed class (implements `NavKey`, `@Serializable`):
- 17 destination types: `Home`, `HomeSettings`, `Settings`, `SubtitleSettings`, `Search`, `SeriesOverview`, `MediaItem`, `Recordings`, `Playback`, `PlaybackList`, `FilteredCollection`, `ItemGrid`, `Slideshow`, `Favorites`, `Discover`, `DiscoveredItem`, `UpdateApp`, `License`, `Debug`
- Each destination has a `fullScreen` flag determining nav drawer visibility

### 5.2 Content Routing

**`ApplicationContent`** composable:
- Uses `NavDisplay` with `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator`
- Routes to either full-screen destinations or `NavDrawer`-wrapped destinations

**`DestinationContent`** composable:
- Giant `when` block routing each `Destination` subtype to its corresponding composable
- Handles backdrop clearing for non-detail pages
- Routes `MediaItem` by `BaseItemKind` type (SERIES, MOVIE, EPISODE, BOX_SET, PLAYLIST, COLLECTION_FOLDER, PERSON, PHOTO_ALBUM, etc.)

### 5.3 Navigation Drawer

**`NavDrawer`** composable with **`NavDrawerViewModel`**:
- Modal navigation drawer pattern for TV
- Sections: Profile icon, Search, Home, Library items (from server), Settings, More (Favorites, Discover), Expand/Collapse
- Focus management for D-pad navigation
- Collection type icons per library
- OLED Black theme support

---

## 6. Image Loading Strategy

### 6.1 Coil 3 Configuration

**`CoilConfig`** composable sets up a global Coil `ImageLoader`:
- **Memory cache**: `MemoryCache.Builder().maxSizePercent(ctx)` (percentage of available memory)
- **Disk cache**: Configurable size (default 200MB, range 25MB-1000MB), stored in `coil3_image_cache`
- **Crossfade**: Disabled globally (`crossfade(false)`)
- **OkHttp integration**: `OkHttpNetworkFetcherFactory` with custom cache strategy
- **Debug logging**: Optional `DebugLogger` when debug logging enabled

### 6.2 Custom Cache Strategy

`WholphinCacheStrategy` wraps `CacheControlCacheStrategy`:
- **Trickplay images** (URLs containing `/Trickplay/`): Always serve from cache (preloaded)
- **Other images**: Delegate to standard `CacheControlCacheStrategy`

### 6.3 Image URL Service

**`ImageUrlService`** (singleton):
- Complex image resolution logic that handles series/season/episode hierarchy
- Image types: PRIMARY, BACKDROP, THUMB, LOGO, BANNER
- Series images fall back to parent for episodes/seasons
- Quality: 96 (constant)
- Convenience: `rememberImageUrl()` composable for Compose integration

### 6.4 Trickplay Support

**`CoilTrickplayTransformation`**: Custom Coil transformation for trickplay sprite sheets -- crops specific tiles from preview image grids for seek bar thumbnails.

---

## 7. Playback Implementation

### 7.1 Dual Engine: ExoPlayer + MPV

**`PlayerFactory`** (singleton):
- Creates either **ExoPlayer** or **MPV** player based on user preference
- Three backend options: `EXO_PLAYER`, `MPV`, `PREFER_MPV` (falls back to ExoPlayer)
- `WholphinRenderersFactory` extends `DefaultRenderersFactory`:
  - Configurable AV1 decoder support via reflection (Libdav1d)
  - Media extension support (FFmpeg)
  - Configurable frame drop notification threshold

### 7.2 PlaybackViewModel (~1100 lines)

The largest ViewModel in the codebase, handling:
- `Player.Listener` and `AnalyticsListener` implementation
- Stream selection (audio, video, subtitles)
- Playlist management with next-up episodes
- Playback progress tracking and position reporting
- Media segments (intro/outro/commercial detection and skip)
- Transcode fallback logic
- Decoder information monitoring
- Subtitle timing and cue management
- Assisted factory pattern for parameterized creation

### 7.3 Playback UI

**`PlaybackPage`** composable:
- `PlayerSurface` from Media3 for video rendering
- Animated overlay with custom `PlaybackControls`
- Custom `SeekBar` with trickplay preview images (`SeekPreviewImage`)
- `SkipIndicator` for D-pad seek feedback
- `NextUpEpisode` auto-play countdown
- `DownloadSubtitlesDialog` for external subtitle management
- `PlaybackDebugOverlay` for stats
- `SubtitleDelay` controls for audio sync
- `PlaybackKeyHandler` for remote control input mapping
- `SeekAcceleration` for progressive seek speed

### 7.4 Playback Reporting

**`MediaReportService`**:
- Sends playback state to Jellyfin server
- Includes device profile, media sources, preferences
- Uses `clientLogApi.logFile()` for debug report transmission

### 7.5 Device Profile

**`DeviceProfileService`** (singleton):
- Lazy `MediaCodecCapabilitiesTest` (probes hardware codec support)
- Caches `DeviceProfile` with configuration fingerprint
- Parameters: maxBitrate, AC3, stereo downmix, ASS/PGS subtitles, Dolby Vision EL, AV1, Jellyfin 10.11 flag
- Thread-safe with Mutex

---

## 8. Theme System

### 8.1 Multiple Color Themes

Theme selection via `AppThemeColors` enum (Protobuf-generated):
- Multiple color options including PURPLE (default)
- OLED Black support in NavDrawer

### 8.2 Dynamic Backdrop Colors

**`BackdropService`** (singleton):
- Extracts dominant colors from backdrop images using Android `Palette` API
- Three extracted colors: primary (darkVibrant/darkMuted at 40% opacity), secondary (temperature-aware selection), tertiary (vibrant at 35%)
- LRU cache (50 entries) for extracted colors
- Colors drive gradient overlays in `ApplicationContent`

### 8.3 Backdrop Styles

`BackdropStyle` enum: `BACKDROP_DYNAMIC_COLOR` (default), and other options (image only, colors only, etc.)

---

## 9. Screensaver / DreamService

### 9.1 Dual Screensaver Implementation

**In-App Screensaver** (`ScreensaverService` + `AppScreensaver`):
- Configurable start delay, duration per image, animation
- Filters by item types (movies, series) and max age rating
- Uses `ApiRequestPager` to fetch random items with backdrop images
- Prefetches images via Coil
- Pause during playback, resume on idle

**System DreamService** (`WholphinDreamService`):
- Extends Android `DreamService` for system-level screensaver
- Implements `SavedStateRegistryOwner` for Compose in DreamService
- Full lifecycle management (LifecycleRegistry)
- Session restoration via `ServerRepository.restoreSession()`
- Reuses `AppScreensaverContent` composable with theme support

### 9.2 Screensaver Preferences

Extensive configuration:
- Enable/disable, start delay, image duration
- Show clock, animate transitions
- Max age filter (content rating)
- Item types filter (Movie, Series, etc.)

---

## 10. Subtitle Handling

### 10.1 Comprehensive Subtitle Support

- **Style customization**: Font size, color, bold, italic, opacity, edge color/style/thickness, background color/opacity/style, margin
- **HDR-specific styles**: Separate subtitle preferences for HDR content (`hdrSubtitlesPreferences`)
- **Format support**: ASS direct play, PGS direct play (configurable in playback overrides)
- **Image subtitle opacity**: Separate control for bitmap-based subtitles
- **Subtitle download**: `DownloadSubtitlesDialog` for fetching external subtitles (requires server plugin)
- **Subtitle delay**: `SubtitleDelay` component for timing adjustment
- **Track selection**: `TrackSelectionUtils` + `PlaybackTrackInfo` for stream switching
- **Language memory**: `PlaybackLanguageChoiceDao` persists preferred languages per series

### 10.2 Subtitle Settings Page

`SubtitleStylePage` with separate configurations for SDR and HDR viewing conditions.

---

## 11. Live TV Support

### 11.1 Full Live TV & DVR

Wholphin has comprehensive Live TV support:

**Screens:**
- `CollectionFolderLiveTv` -- Channel grid
- `TvGuideGrid` / `TvGuideHeader` -- Electronic Program Guide
- `DvrSchedule` -- DVR recording schedule
- `ProgramDialog` -- Program details with record option
- `RecordingMarker` -- Visual recording indicator
- `LiveTvViewOptionsDialog` -- Display options

**Home Row Integration:**
- `HomeRowConfig.TvPrograms` -- Currently airing programs
- `HomeRowConfig.TvChannels` -- Favorite channels
- `HomeRowConfig.Recordings` -- Recent recordings

**Preferences:**
- Show header, favorite channels at beginning, sort by recently watched, color code programs

---

## 12. Jellyseerr / Discovery Integration

### 12.1 OpenAPI-Generated Client

Wholphin uses **OpenAPI code generation** from a Swagger specification to create the Jellyseerr API client (`SeerrApiClient`). This is defined in `build.gradle.kts` and generates typed API classes.

### 12.2 Service Architecture

**`SeerrApi`** -- Low-level API client wrapper
**`SeerrServerRepository`** -- Server credentials management with `SeerrServerDao`
**`SeerrService`** (singleton) -- High-level service:
- `search()` -- Search Jellyseerr catalog
- `discoverTv()` / `discoverMovies()` -- Browse trending/new content
- `trending()` -- Trending content
- `upcomingMovies()` / `upcomingTv()` -- Upcoming releases
- `similar()` -- TMDB-based similar content recommendations (movies, series, people)
- `getTvSeries()` -- Detailed TV series info

### 12.3 Discovery UI

- `DiscoverPage` -- Main discovery hub
- `SeerrDiscoverPage` -- Browsable discover rows
- `SeerrRequestsPage` -- View/manage media requests
- `DiscoverMovieDetails` / `DiscoverSeriesDetails` -- Detail pages for discovered content
- `DiscoverPersonPage` -- Person credits from Jellyseerr
- `RequestSeasons` -- Season-level request management
- `ExpandableDiscoverButtons` -- Action buttons for requests

### 12.4 Integration Points

- Search page integrates Jellyseerr results alongside Jellyfin results
- Detail pages show "Similar" content from Jellyseerr when TMDB IDs match
- Home rows can include Jellyseerr trending/discover content

---

## 13. Preferences / DataStore Usage

### 13.1 Protocol Buffers DataStore

Preferences are stored using **Proto DataStore** with Protocol Buffer serialization -- NOT JSON or SharedPreferences (except legacy ACRA toggle).

**`AppPreferences`** (Proto message) contains nested messages:
- `PlaybackPreferences` (skip times, bitrate, segment skipping, player backend, refresh rate, etc.)
- `PlaybackOverrides` (AC3, stereo downmix, ASS/PGS, Dolby Vision, AV1, FFmpeg)
- `MpvOptions` (hardware decoding, GPU next)
- `HomePagePreferences` (max items, next-up settings, combine continue/next)
- `InterfacePreferences` (theme songs, theme colors, nav drawer, clock, backdrop style)
- `SubtitlePreferences` (font, colors, styles, opacity -- SDR + HDR variants)
- `LiveTvPreferences` (header, favorites, sorting, color coding)
- `ScreensaverPreferences` (delay, duration, animation, age filter, item types)
- `AdvancedPreferences` (image cache size)
- `PhotoPreferences` (slideshow duration, play videos)

### 13.2 Type-Safe Preference System

**`AppPreference<Pref, T>`** sealed interface with specialized implementations:
- `AppSwitchPreference` -- Boolean toggle
- `AppSliderPreference` -- Numeric range
- `AppChoicePreference` -- Enum dropdown
- `AppMultiChoicePreference` -- Multi-select
- `AppStringPreference` -- Text input
- `AppClickablePreference` -- Action button
- `AppDestinationPreference` -- Navigation link

Each preference has `title`, `defaultValue`, `getter`, `setter`, `summary()`, and `validate()`. Preferences are organized into `PreferenceGroup` lists for the settings UI.

### 13.3 Preference Groups

- **Basic**: Interface, Playback, Next Up, Profile, About, More
- **Advanced**: Interface, Playback, Skip segments, Player backend (conditional sub-groups for ExoPlayer vs MPV), Updates, More
- **Live TV**: Header, favorites, sorting, color coding
- **Screensaver**: Enable, delay, duration, clock, animation, age filter, item types
- **ExoPlayer**: FFmpeg, downmix, AC3, ASS, PGS, Dolby Vision, AV1
- **MPV**: Hardware decoding, GPU next, conf file

---

## 14. Background Services

### 14.1 WorkManager Workers

**`SuggestionsWorker`** (`@HiltWorker`):
- Fetches personalized content suggestions in background
- Seed-based algorithm: fetches watch history -> extracts genres -> finds similar unwatched items
- Three suggestion sources: contextual (genre-match), random (discovery), fresh (recently added)
- Caches results in `SuggestionsCache` with change detection
- Concurrent library processing with `supervisorScope`

**`TvProviderWorker`** (`services/tvprovider/`):
- Populates Android TV home screen recommendations
- Scheduled by `TvProviderSchedulerService`

### 14.2 Singleton Services (30+)

| Service | Purpose |
|---------|---------|
| `BackdropService` | Dynamic color extraction from backdrop images |
| `ScreensaverService` | In-app screensaver state management |
| `NavigationManager` | Backstack management |
| `ImageUrlService` | Jellyfin image URL construction |
| `MediaReportService` | Debug media info submission to server |
| `DeviceProfileService` | Hardware capability profiling |
| `FavoriteWatchManager` | Favorite/watched state management |
| `DatePlayedService` | Play date tracking |
| `HomeSettingsService` | Home page row configuration |
| `NavDrawerService` | Drawer item management |
| `PlayerFactory` | ExoPlayer/MPV instantiation |
| `PlaylistCreator` | Playlist creation logic |
| `StreamChoiceService` | Media stream selection |
| `ThemeSongPlayer` | Background theme music |
| `TrailerService` | Trailer fetching |
| `ExtrasService` | Bonus content fetching |
| `SeerrService` | Jellyseerr integration |
| `SeerrServerRepository` | Jellyseerr credentials |
| `UserPreferencesService` | Preference access |
| `UpdateChecker` | App update checking |
| `AppUpgradeHandler` | Post-update migrations |
| `MediaManagementService` | Item deletion |
| `RefreshRateService` | Display mode switching |
| `PlaybackLifecycleObserver` | Playback lifecycle |
| `ServerEventListener` | Server-side events |
| `LatestNextUpService` | Latest/next-up content |
| `SuggestionService` | Suggestion orchestration |
| `SuggestionsCache` | In-memory suggestion cache |
| `SuggestionsSchedulerService` | Worker scheduling |
| `SetupNavigationManager` | Setup flow navigation |
| `PeopleFavorites` | Person favorites |

---

## 15. Data Models (Non-Entity)

### 15.1 Core Domain Models

**`BaseItem`** (`data/model/BaseItem.kt`):
- Wraps `BaseItemDto` from Jellyfin SDK
- `@Stable` for Compose recomposition optimization
- Computed properties: `title`, `subtitle`, `aspectRatio`, `playbackPosition`, `played`, `favorite`, `timeRemainingOrRuntime`
- Pre-computed `BaseItemUi` with `quickDetails` (AnnotatedString with inline content for ratings)
- `destination()` method generates navigation target based on item type
- Implements `CardGridItem` interface for grid display

**`HomeRowConfig`** (sealed interface, 12 types):
`ContinueWatching`, `NextUp`, `ContinueWatchingCombined`, `RecentlyAdded`, `RecentlyReleased`, `Genres`, `Favorite`, `Recordings`, `TvPrograms`, `TvChannels`, `Suggestions`, `ByParent`, `GetItems`

Each has configurable `HomeRowViewOptions` (height, spacing, content scale, aspect ratio, image type, titles, etc.)

**Other models**: `Chapter`, `DiscoverItem`, `GetItemsFilter`, `ItemTrackModification`, `Person`, `PlaybackEffect`, `PlaybackLanguageChoice`, `Playlist`, `SeerrPermission`, `SeerrServer`, `ServerPreferences`, `Trailer`, `VideoFilter`, `HomePageSettings`

---

## 16. Filter System

### 16.1 Type-Safe Filters

**`ItemFilterBy<T>`** sealed interface:
- `GenreFilter` (List<UUID>)
- `PlayedFilter` (Boolean)
- `FavoriteFilter` (Boolean)
- `OfficialRatingFilter` (List<String>)
- `VideoTypeFilter` (List<FilterVideoType> -- 4K, HD, SD, 3D, Blu-Ray, DVD)
- `CommunityRatingFilter` (Int -- minimum rating)
- `YearFilter` (List<Int>)
- `DecadeFilter` (List<Int>)

### 16.2 Filter Contexts

Pre-defined filter option lists:
- `DefaultFilterOptions` -- Full set for standard libraries
- `DefaultForFavoritesFilterOptions` -- Without Favorite filter
- `DefaultForGenresFilterOptions` -- Without Genre filter
- `DefaultPlaylistItemsOptions` -- For playlist content

---

## 17. Notable Technical Choices

### 17.1 Navigation3 (Experimental)

Using the experimental `androidx.navigation3` API instead of the stable Jetpack Navigation Component is a bold choice. It provides more control over backstack management but risks breaking changes.

### 17.2 Protocol Buffers for Preferences

Using Proto DataStore instead of JSON/SharedPreferences provides:
- Type safety at compile time
- Efficient binary serialization
- Schema evolution with backward compatibility
- Generated builder APIs

### 17.3 No Local Content Cache

Unlike PlexHubTV's LibrarySyncWorker approach, Wholphin fetches ALL content from the Jellyfin API in real-time. This means:
- No offline browsing capability
- No stale data issues
- Simpler data layer (no sync complexity)
- Every screen load requires network access

### 17.4 OpenAPI Code Generation for Jellyseerr

Auto-generating the Jellyseerr API client from Swagger spec ensures API contract compliance and reduces manual maintenance.

### 17.5 ACRA Crash Reporting (to Jellyfin Server)

Unique approach: crash reports are sent to the user's own Jellyfin server via `clientLogApi`, not to a third-party service. This respects privacy.

### 17.6 Dual Screensaver Architecture

Both an in-app screensaver and Android system `DreamService` -- ensuring the screensaver works whether the app is active or in background.

### 17.7 Suggestion Algorithm

The `SuggestionsWorker` implements a sophisticated recommendation engine:
- Seed from watch history (genre extraction)
- 40% contextual (genre-matched unwatched), 30% random, 30% fresh
- Background processing with caching and change detection

### 17.8 Compile SDK 36 / Kotlin 2.3

Very aggressive SDK and language version targeting, indicating active development.

---

## 18. Strengths

1. **Complete Jellyfin feature coverage**: Live TV, DVR, Photo albums, Slideshows, Playlists, Box Sets
2. **Dual player backend**: ExoPlayer + MPV gives users fallback options for problematic media
3. **Jellyseerr integration**: Full discovery, request, and similar content features
4. **Highly configurable home screen**: 12 row types with per-row view options
5. **Comprehensive preference system**: Proto DataStore with 60+ settings
6. **Rich subtitle support**: Full style customization, HDR variants, external download
7. **TV-first design**: D-pad navigation, focus management, screensaver, DreamService
8. **Dynamic theming**: Palette-based color extraction from backdrop images
9. **Trickplay support**: Seek bar preview thumbnails with sprite sheet processing
10. **Privacy-respecting**: Crash reports to user's own server
11. **Background intelligence**: Suggestion algorithm, TV provider integration

## 19. Limitations

1. **No offline support**: No local content caching -- every browse requires network
2. **Experimental navigation**: Navigation3 may introduce breaking changes
3. **Single server**: No multi-server support (one Jellyfin server at a time)
4. **No content rating/aggregation**: Unlike PlexHubTV's displayRating system, relies entirely on Jellyfin's community/critic ratings
5. **Large codebase**: 70+ Kotlin files with dense service layer may be difficult to onboard
6. **No content unification**: Cannot deduplicate content across multiple servers (Jellyfin is inherently single-server)
7. **Android TV only**: minSdk 23 but UI targets leanback/TV form factor exclusively

---

## 20. Comparison Points with PlexHubTV

| Aspect | Wholphin (Jellyfin) | PlexHubTV (Plex) |
|--------|---------------------|-------------------|
| **API** | Jellyfin SDK (typed) | Plex REST API (raw HTTP) |
| **Local cache** | None (API-only) | Room-based LibrarySyncWorker |
| **Navigation** | Navigation3 (experimental) | Compose Navigation (stable) |
| **Preferences** | Proto DataStore (binary) | DataStore (likely JSON/Proto) |
| **DI** | Hilt | Hilt |
| **Player** | ExoPlayer + MPV | ExoPlayer (likely) |
| **Multi-server** | Single server | Multi-server with unification |
| **Content dedup** | N/A | unificationId + displayRating |
| **Crash reporting** | ACRA to user's Jellyfin | Custom (TBD) |
| **Home config** | 12 row types, per-row options | Configurable (TBD) |
| **Live TV** | Full (EPG, DVR, Recordings) | N/A (Plex Live TV separate) |
| **Discovery** | Jellyseerr integration | N/A |
| **Screensaver** | In-app + DreamService | TBD |
| **Theme** | Dynamic backdrop colors | TBD |
