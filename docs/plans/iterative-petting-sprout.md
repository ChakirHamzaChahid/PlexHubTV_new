# Rapport d'analyse comparative : PlexHubTV vs Wholphin vs Plezy

---

## SECTION 0 — Contexte des applications

### PlexHubTV
- **Role** : Hub multi-sources (Plex + Xtream/IPTV + Backend custom, futur Jellyfin)
- **Plateforme** : Android TV natif
- **Stack** : Kotlin 100%, Jetpack Compose + Material 3 + androidx.tv, Media3/ExoPlayer + MPV, Hilt (13 modules), Room v42 (WAL), DataStore (encrypted), Retrofit + OkHttp + Gson, Coil, WorkManager (6 workers), Firebase Crashlytics + Performance
- **Positionnement** : Agregateur unifie multi-sources avec unification de contenu cross-serveurs
- **Architecture** : Clean Architecture multi-module (11 modules), MVVM, 25+ Use Cases

### Wholphin
- **Role** : Client Android TV pour Jellyfin uniquement
- **Plateforme** : Android TV / Fire TV (Android 6+)
- **Stack** : Kotlin 98.2%, Jetpack Compose + Material 3 + androidx.tv, Jellyfin Kotlin SDK, ExoPlayer + MPV + FFmpeg optionnel, Hilt, Room v31 (10 entities), DataStore, Coil (+GIF/SVG), WorkManager, ACRA crash reporting
- **Positionnement** : Alternative Plex-like pour utilisateurs Jellyfin, 1400+ stars, 44 releases
- **Architecture** : MVVM mono-module avec services layer (35+ services, pas de Use Cases formels)

### Plezy
- **Role** : Client Plex multi-plateforme avance
- **Plateforme** : Android, iOS, macOS, Windows, Linux (**Flutter**)
- **Stack** : **Dart 76.8%** + GLSL 15%, Flutter 3.8.1+, MPV natif (libmpv-android / MPVKit), Drift (SQLite), Provider pattern, code generation, Sentry crash reporting
- **Positionnement** : Client Plex premium, 1600+ stars, 43 releases, riche en fonctionnalites (Watch Together, Live TV, downloads, shaders, 16 langues)
- **NOTE CRITIQUE** : Plezy est une app **Flutter/Dart**, PAS native Android/Kotlin. Aucun code n'est directement reutilisable — seuls les concepts, patterns et features sont transferables.

---

## SECTION 1 — Architecture du projet (structure globale)

### PlexHubTV — Clean Architecture multi-module (469 fichiers .kt)

**11 modules Gradle :**
| Module | Fichiers .kt | Responsabilite |
|---|---|---|
| `:app` (features) | 151 | Screens, ViewModels, Workers, DI |
| `:core:database` | 42 | Room entities, DAOs, migrations |
| `:core:network` | 32 | API clients (Plex, OMDB, TMDB, Xtream, Backend) |
| `:core:model` | 34 | Domain models (value objects) |
| `:core:common` | 11 | Utils (codec, format, cache, string) |
| `:core:datastore` | 4 | DataStore preferences |
| `:core:navigation` | 2 | Route definitions (Screen.kt) |
| `:core:designsystem` | 3 | Theme, Color, Typography |
| `:core:ui` | 12 | Composants reusables (cards, rows, hero) |
| `:data` | 52 | Repository impls, mappers, query builders |
| `:domain` | 62 | Interfaces, Use Cases (25), Services |
| **TOTAL** | **469** | |

**Pattern** : Clean Architecture stricte — `domain` ne depend de rien, `data` implemente `domain`, `app` orchestre tout via Hilt.

**18+ feature packages dans app/ :**

| Package | Fichiers | Role |
|---------|----------|------|
| `feature/player/` | **26** | Player complet (ExoPlayer+MPV, trickplay, chapters, MediaSession, equalizer, profiles) |
| `feature/settings/` | **15** | Settings avec sous-ecrans par categorie |
| `feature/details/` | 8+ | Detail film/serie/personne, enrichissement OMDB/TMDB |
| `feature/library/` | 6+ | Grille media avec filtres, tri, pagination |
| `feature/playlist/` | 6 | Playlists Plex (list, detail, create, add) |
| `feature/home/` | 5+ | Home Netflix-style (hero, hubs, suggestions) |
| `feature/appprofile/` | 5 | Profils locaux (emoji, kids mode, PIN) |
| `feature/auth/` | 4 | Login Plex (token + PIN) |
| `feature/xtream/` | 4 | Comptes Xtream Codes |
| `feature/search/` | 4 | Recherche full-text multi-serveur |
| `feature/hub/` | 3+ | On Deck / Continue Watching |
| `feature/collection/` | 3 | Collections de films |
| `feature/downloads/` | 3 | Telechargements |
| `feature/plexhome/` | 3 | Changement utilisateur Plex Home |
| `feature/screensaver/` | 3 | DreamService (slideshow artwork) |
| `feature/debug/` | 3 | Ecran debug |
| `feature/favorites/` | 2 | Liste "My List" |
| `feature/history/` | 2 | Historique de visionnage |
| `feature/iptv/` | 2 | Lecteur IPTV M3U |
| `work/` | 6 | LibrarySync, RatingSync, CollectionSync, ChannelSync, UnifiedRebuild, CachePurge |

**Multi-source abstraction** : `MediaSourceHandler` (interface domain) → `PlexSourceHandler`, `XtreamSourceHandler`, `BackendSourceHandler`. `MediaSourceResolver` orchestre.

**Composants UI reusables (core/ui/) :** NetflixHeroBillboard, NetflixMediaCard, NetflixContentRow, NetflixTopBar, NetflixOnScreenKeyboard, BackdropColors, FallbackAsyncImage, OverscanSafeArea, Skeletons

### Wholphin — MVVM mono-module (~200 fichiers .kt)

**Module unique : `app/`**

| Package | Responsabilite |
|---|---|
| `api/seerr/` | Client API Jellyseerr (SeerrApiClient) |
| `data/` | Room DB (AppDatabase v31), 7 DAOs, 17 modeles, filtres |
| `preferences/` | AppPreference, UserPreferences, ScreensaverPreference (DataStore) |
| `services/` | **35+ services** (PlayerFactory, HomeSettingsService, NavigationManager, RefreshRateService, ThemeSongPlayer, SeerrService, UpdateChecker, SuggestionsWorker, DeviceProfileService, etc.) |
| `services/hilt/` | AppModule, DatabaseModule |
| `services/tvprovider/` | TvProviderWorker, TvProviderSchedulerService |
| `ui/cards/` | 14 composants cartes (BannerCard, GridCard, EpisodeCard, ChapterCard, etc.) |
| `ui/components/` | 30+ composants (Dialogs, FilterByButton, PlayButtons, Rating, VoiceSearchButton, etc.) |
| `ui/nav/` | ApplicationContent, Destination (sealed serializable), NavDrawer |
| `ui/detail/` | MovieDetails, SeriesDetails, EpisodeDetails, discover/, livetv/ |
| `ui/playback/` | **26 fichiers** (PlaybackPage, Controls, SeekBar, Trickplay, NextUp, etc.) |
| `ui/main/` | HomePage, HomeViewModel, settings/ (HomeRowSettings, HomeRowPresets) |

**Difference cle vs PlexHubTV** : Pas de couche Use Case formelle. La logique metier est distribuee dans des **Services** (35+). Plus pragmatique mais moins testable en isolation. 3 repositories vs 21 pour PlexHubTV.

### Plezy — Provider + Services (Flutter, ~300+ fichiers Dart)

| Dossier | Responsabilite |
|---|---|
| `lib/database/` | Drift DB (app_database, tables, download_operations) — 4 fichiers seulement |
| `lib/models/` | 32 modeles Plex (PlexMetadata, PlexLibrary, PlexHomeUser, LiveTV, PlayQueue, etc.) + 2 sous-dossiers |
| `lib/providers/` | 12 providers (MultiServer, Download, Libraries, PlaybackState, Theme, UserProfile, Shader, OfflineMode, etc.) |
| `lib/services/` | **40 services** (PlexClient, MultiServerManager, ServerConnectionOrchestrator, BifThumbnailService, DownloadManager, DiscordRPC, SleepTimer, PiP, GamepadService, ShaderService, etc.) |
| `lib/screens/` | 11 ecrans + 7 sous-dossiers (auth, livetv, settings, downloads, profile, playlist, companion_remote) |
| `lib/widgets/` | 30 widgets + 2 sous-dossiers (video_controls, companion_remote) |
| `lib/focus/` | **10 fichiers** focus TV (DpadNavigator, FocusMemoryTracker, FocusableWrapper, InputModeTracker, etc.) |
| `lib/watch_together/` | Feature complete (models, providers, screens, services, widgets) |
| `lib/mpv/` | Integration MPV native |
| `lib/theme/` | Theming |
| `lib/i18n/` | 16 langues |

### Comparaison — Architecture globale

| Aspect | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Framework** | Native Android (Compose TV) | Native Android (Compose TV) | **Flutter** (cross-platform) |
| **Langage** | Kotlin 100% | Kotlin 98.2% | Dart 76.8% |
| **Architecture** | Clean Architecture multi-module | MVVM mono-module + services | Provider + services |
| **Modules** | 11 modules Gradle | 1 module | 1 projet Flutter |
| **Volume code** | **469 fichiers .kt** | ~200 fichiers .kt | ~300+ fichiers .dart |
| **Separation domain/data** | Oui (stricte, 25+ Use Cases) | Non (35+ services) | Non (40 services + 12 providers) |
| **DI** | Hilt (13 modules) | Hilt | Provider (Flutter natif) |
| **Navigation** | Compose Nav + sealed Screen | Compose Nav + serializable Destination + NavDrawer | Flutter Navigator + routes |
| **DB** | Room v42, 19 entities, 15+ DAOs | Room v31, 10 entities, 7 DAOs | Drift (SQLite, 4 fichiers) |
| **Multi-source** | Oui (Plex + Xtream + Backend) | Non (Jellyfin only + Seerr) | Non (Plex only) |
| **Tests** | 36 fichiers, ~315 methodes | MockK + Robolectric | Non visible |
| **Crash reporting** | Firebase Crashlytics (production-grade) | ACRA | Sentry |

---

## SECTION 2 — Couche data, API et multi-serveurs

### 2.1 Wholphin — Data Jellyfin

**AppDatabase (Room v31, 10 entites) :**

| Entity | Role |
|---|---|
| `JellyfinServer` | Config connexion serveur |
| `JellyfinUser` | Utilisateurs authentifies |
| `ItemPlayback` | Progression lecture + resume |
| `NavDrawerPinnedItem` | Items epingles dans le drawer |
| `LibraryDisplayInfo` | Config affichage par bibliotheque (vue, tri, filtres) |
| `PlaybackEffect` | Effets de lecture (vitesse, etc.) |
| `PlaybackLanguageChoice` | Preferences langue audio/sous-titres |
| `ItemTrackModification` | Modifications tracks (delai, sync) |
| `SeerrServer` | Config serveur Jellyseerr |
| `SeerrUser` | Utilisateurs Seerr |

**7 DAOs, 3 Repositories** : `ServerRepository` (11KB), `ItemPlaybackRepository` (9.8KB), `SeerrServerRepository`. La logique est concentree dans les Services plutot que les repos.

**API** : Jellyfin Kotlin SDK officiel (`libs.jellyfin.core` + `libs.jellyfin.api` + `libs.jellyfin.api.okhttp`)

**Multi-serveurs** : `ServerRepository` gere la liste des serveurs Jellyfin. Chaque serveur a son propre user et preferences via `ServerPreferencesDao`.

**Extras** : `ExtrasItem.kt` gere les bonus (trailers, deleted scenes, behind-the-scenes).

### 2.2 Plezy — Data Plex (Flutter/Drift)

**Database leger** : Drift (SQLite pour Flutter), 4 fichiers — `tables.dart`, `download_operations.dart`, `app_database.dart`, `app_database.g.dart`.

**Services data cles (40 services) :**
- `plex_client.dart` — Client API Plex complet
- `plex_auth_service.dart` — Auth Plex (tokens, PIN)
- `multi_server_manager.dart` — Gestion multi-serveurs Plex
- `server_connection_orchestrator.dart` — Connection intelligente (sonde sante, retry, fallback offline)
- `server_registry.dart` — Registre serveurs depuis API Plex
- `data_aggregation_service.dart` — Aggregation cross-serveurs
- `playback_progress_tracker.dart` — Suivi progression
- `offline_watch_sync_service.dart` — Sync lecture offline
- `plex_api_cache.dart` — Cache API
- `track_selection_service.dart` — Preferences audio/sous-titres

**Multi-serveurs avance** : `MultiServerManager` + `MultiServerProvider` + `ServerConnectionOrchestrator`. Health probes periodiques (10s mobile, 2min desktop), graceful degradation vers mode offline.

**Profiles** : `plex_home.dart`, `plex_home_user.dart`, `user_switch_response.dart`, `user_profile_provider.dart` — Gestion complete Plex Home avec switch user.

### 2.3 PlexHubTV — Data multi-sources

**Room DB v42, 19 entities :**
- `MediaEntity` + `MediaUnifiedEntity` + `MediaFts` — Films/series/episodes + index agrege + FTS
- `ServerEntity` + `LibrarySectionEntity` + `HomeContentEntity` — Infra serveur
- `CollectionEntities` (Collection + Members) — Collections
- `FavoriteEntity` + `PersonFavoriteEntity` — Favoris media + personnes
- `TrackPreferenceEntity` — Preferences audio/sous-titres
- `OfflineWatchProgressEntity` — Progression offline
- `ProfileEntity` — Profils locaux
- `SearchCacheEntity` + `ApiCacheEntity` — Caches
- `RemoteKey` — Curseurs pagination
- `XtreamAccountEntity` + `BackendServerEntity` — Sources externes
- `IdBridgeEntity` — Mapping IDs cross-serveur (IMDB/TMDB)
- `PlaylistEntity` + `PlaylistItemEntity` — Playlists Plex (v42)

**21 repositories** : Architecture rigoureuse interface domain → implementation data.

**Query Builder** : `MediaLibraryQueryBuilder.kt` — SQL complexe avec GROUP BY + GROUP_CONCAT, correlated MAX, filtrage genre/annee/note, tri par rating/date/titre.

### Comparaison — Couche data & APIs

| Aspect | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Sources supportees** | Plex + Xtream + Backend | Jellyfin + Jellyseerr | Plex only |
| **Multi-serveurs** | Oui (ServerDao, multi-source) | Oui (JellyfinServerDao + users) | Oui (MultiServerManager + orchestrator) |
| **DB locale** | Room v42, 19 entities, 15+ DAOs | Room v31, 10 entities, 7 DAOs | Drift (4 fichiers) |
| **Abstraction source** | Oui (MediaSourceHandler interface) | Non (Jellyfin SDK direct) | Non (PlexClient direct) |
| **Unification cross-source** | Oui (IdBridge + UnificationId) | Non applicable | Non applicable |
| **Aggregation** | AggregationService + Deduplicator + correlated MAX | Non | DataAggregationService (cross-servers) |
| **Repositories** | **21 implementations** | 3 implementations | Services directs |
| **Use Cases** | **25+ Use Cases** formels | 0 (logique dans services) | 0 (logique dans services/providers) |
| **Cache API** | ApiCacheDao (Room) | Non explicite | plex_api_cache.dart |
| **Discovery** | Suggestions locales (3 strategies) | Jellyseerr (browse, request, permissions) | Plex Discover natif |
| **Pref langues/tracks** | TrackPreferenceDao | PlaybackLanguageChoiceDao | TrackSelectionService |
| **Health probes serveur** | Non | Non | Oui (periodiques + fallback offline) |

---

## SECTION 3 — UI / UX Android TV

### 3.1 Wholphin — UX TV Reference

**Home configurable :**
- `HomeSettingsPage.kt` + `HomeRowSettings.kt` + `HomeRowPresets.kt` — Ordre des rows, types (LatestAdded, NextUp, ContinueWatching, favorites, custom), style d'image, show/hide titres, presets
- `HomeViewModel.kt` charge les rows depuis `HomeSettingsService`

**Navigation :** NavDrawer global + `Destination` sealed serializable + `ApplicationContent` + `NavigationManager` service

**D-Pad / Focus :**
- `SeekAcceleration.kt` — Acceleration progressive selon duree contenu (< 30min: 1-2x, 30-90min: 1-4x, 90-150min: 1-6x, > 150min: 1-10x)
- `PlaybackKeyHandler.kt` — Gestion complete touches telecommande
- `KeyIdentifier.kt` — Identification touches physiques
- Debounce 750ms avant execution du seek

**Trickplay :** `CoilTrickplayTransformation.kt` — Sprite sheet crop via Coil. `SeekPreviewImage.kt` — Affichage vignette pendant seek.

**Live TV :** `TvGuideGrid.kt` + `TvGuideHeader.kt` — Guide EPG complet. `DvrSchedule.kt` — Programmation enregistrements. `LiveTvViewModel.kt`.

### 3.2 Plezy — UX Multi-plateforme

**Focus TV (10 fichiers dedies) :**
- `dpad_navigator.dart` — Navigation D-Pad complete
- `focus_memory_tracker.dart` — Memorisation du focus entre navigations
- `focusable_wrapper.dart` (15.8 KB) — Wrapper generique pour rendre tout widget focusable
- `input_mode_tracker.dart` — Detection auto telecommande vs touch vs gamepad
- `key_event_utils.dart`, `locked_hub_controller.dart`, `focus_theme.dart`, `focusable_action_bar.dart`, `focusable_button.dart`, `focusable_chip_mixin.dart`

**Widgets TV specifiques :**
- `focusable_media_card.dart`, `focusable_filter_chip.dart`, `focusable_list_tile.dart`, `focusable_tab_chip.dart`
- `horizontal_scroll_with_arrows.dart`, `side_navigation_rail.dart`
- `tv_color_picker.dart`, `tv_number_spinner.dart`
- `hub_section.dart`

**Live TV :** `screens/livetv/` + `models/livetv_channel.dart`, `livetv_program.dart`, `livetv_dvr.dart`, `livetv_hub_result.dart`

**Watch Together :** `lib/watch_together/` — Feature complete (models, providers, screens, services, widgets)

### 3.3 PlexHubTV — UX actuelle

**Home :** NetflixHomeScreen (hero billboard + rows) + DiscoverScreen + rows configurables (visibilite toggles + Move Up/Down reordering + persistence DataStore) + backdrop reactif (BackdropColors + AppBackdrop + focus-driven)

**Player (26 fichiers, architecture controller decomposee) :**
```
VideoPlayerRoute
├── PlayerSurface (ExoPlayer OU MPV)
├── NetflixPlayerControls
│   ├── Top Bar (Back + Titre + Server info + gradient)
│   ├── Center (Play/Pause 80x80dp)
│   ├── Bottom (EnhancedSeekBar + Transport: [◀Chap] [⏪-10s] [⏵/⏸] [⏩+30s] [▶Chap] ... [▶▶Next] [⏹Stop] [📝Subs] [🔊Audio] [⚙Settings])
│   └── SkipMarkerButton (intro=vert/credits=orange, bottom-right)
├── PerformanceOverlay (top-right, "Nerd Stats" — 7 metriques)
├── PlayerErrorOverlay (full-screen: diagnostic + retry counter + MPV switch suggestion)
├── AutoNextPopup (15s countdown + thumbnail + Play Now/Cancel)
├── PlayerSettingsDialog (qualite + stats + sync)
├── AudioEqualizerDialog (10 bandes + presets)
├── DownloadSubtitlesDialog (OpenSubtitles search)
└── SyncSettingsDialog (subtitle/audio delay ±50ms/±250ms/±1s)
```

**13 composants controller :** PlayerController, Initializer, MediaLoader, Scrobbler, StatsTracker, TrackController, ChapterMarkerManager, TrickplayManager, MediaSessionManager, RefreshRateManager, AudioEqualizerManager, SubtitleSearchService, DeviceProfileService

**4 URL builders :** DirectStream, Transcode, Backend, Xtream

### Comparaison — Player detaillee

| Element | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Architecture** | Controller decompose (13 composants) | ViewModel monolithique (~1100 lignes) | Services separes |
| **Moteur** | ExoPlayer + MPV | ExoPlayer + MPV + FFmpeg optionnel | MPV principal |
| **Seek bar markers** | Intro(vert) + Credits(rouge) + Chapitres(blancs) | Non | Non confirme |
| **Seek acceleration** | Oui (EnhancedSeekBar) | Oui (SeekAcceleration progressive 1-10x + debounce 750ms) | Oui (DpadNavigator) |
| **Trickplay** | TrickplayManager (chapter image → BIF → title fallback) | CoilTrickplayTransformation (sprite sheet crop) | BifThumbnailService |
| **Chapitres** | ChapterMarkerManager + ChapterOverlay + boutons prev/next dans barre | ChapterCard/Row + vue overlay alternative | Non confirme |
| **Queue** | QueueOverlay | Vue overlay alternative slide | PlayQueueLauncher |
| **Auto-play next** | AutoNextPopup (15s, top-right, overlay sans reduction) | NextUpEpisode (configurable, scale player 0.6x) | Auto-play series |
| **Skip intro/credits** | SkipMarkerButton (2 types: intro/credits) + mode auto/ask/off | SkipIndicator (4 types: Intro/Outro/Commercial/Preview) | Non confirme |
| **Subtitle download** | SubtitleSearchService + DownloadSubtitlesDialog | DownloadSubtitlesDialog + SubtitleSearchUtils | Non mentionne |
| **Subtitle delay** | SyncSettingsDialog (7 boutons: ±50ms/±250ms/±1s) | SubtitleDelay (7 boutons identiques) | Non mentionne |
| **Audio equalizer** | AudioEqualizerManager + Dialog (10 bandes + presets) | Presets + 10 bandes | Non |
| **Error overlay** | PlayerErrorOverlay (diagnostic + retry counter + MPV switch) | Loading state enum (pas d'overlay dedie) | Non mentionne |
| **Debug stats** | PerformanceOverlay (7 metriques, accessible en PROD) | PlaybackDebugOverlay (~10 metriques, DEBUG only) | Non mentionne |
| **MediaSession** | MediaSessionManager | MediaSessionPlayer | MediaControlsManager |
| **Refresh rate** | RefreshRateManager | RefreshRateService | Non mentionne |
| **Device profile** | DeviceProfileService (detection codecs + DebugScreen) | DeviceProfileService | Non mentionne |
| **PiP** | Non | Non | Oui (PipService + VideoPipManager) |
| **Watch Together** | Non | Non | Oui (feature complete) |
| **Lecteur externe** | Non | Non | Oui (ExternalPlayerService — VLC, Infuse) |
| **Shaders video** | Non | Non | Oui (Anime4K, NVScaler — ShaderService) |
| **Sleep Timer** | Non | Non | Oui (SleepTimerService) |
| **Theme song** | ThemeSongService | ThemeSongPlayer | Non mentionne |
| **Ambient lighting** | Non | Non | Oui (AmbientLightingService) |

### Comparaison — UX Home & Navigation

| Aspect | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Home** | Netflix-style (hero + rows) + backdrop reactif | Rows configurables + presets + backdrop | Hub-style Plex |
| **Home personnalisable** | Oui (visibilite + reordering Move Up/Down + DataStore) | Oui (ordre, types, presets, style image) | Partiellement (masquer libs) |
| **Navigation** | Sidebar Netflix-style | NavDrawer global lateral | SideNavigationRail |
| **Screensaver** | DreamService + Compose slideshow | WholphinDreamService + ScreensaverService | Non mentionne |
| **Voice search** | Non | Oui (VoiceSearchButton + SpeechRecognizer) | Non |
| **Focus framework** | Compose TV natif | Compose TV natif | 10 fichiers dedies (FocusMemoryTracker, DpadNavigator, etc.) |
| **Themes** | 6 themes (Plex, MonoDark, MonoLight, Morocco, OLEDBlack, Netflix) | Multiple palettes configurables | ThemeProvider |
| **i18n** | FR + EN | EN | **16 langues** |

### Score par categorie (Player)

| Categorie | PlexHubTV | Wholphin | Plezy | Commentaire |
|---|---|---|---|---|
| **Architecture player** | 9/10 | 6/10 | 7/10 | PlexHubTV: 13 composants modulaires vs 1 ViewModel monolithique |
| **Transport controls** | 8/10 | 7/10 | 7/10 | PlexHubTV: chapitres + stop + audio |
| **Seek bar** | 9/10 | 9/10 | 7/10 | PlexHubTV + Wholphin: markers, acceleration, debounce |
| **Skip intro/credits** | 8/10 | 7/10 | 5/10 | PlexHubTV: markers visuels sur barre |
| **Dialogs/Settings** | 9/10 | 9/10 | 7/10 | PlexHubTV: download subs + equalizer |
| **Gestion erreurs** | 9/10 | 4/10 | 5/10 | PlexHubTV: overlay dedie, diagnostic, MPV switch |
| **Debug/Stats** | 8/10 | 6/10 | 4/10 | PlexHubTV: PerformanceOverlay en prod |
| **Features exclusives** | 7/10 | 6/10 | 9/10 | Plezy: PiP, Watch Together, shaders, sleep timer, ambient |
| **TOTAL** | **67/80** | **54/80** | **51/80** | PlexHubTV superieur en player core |

---

## SECTION 4 — Themes, preferences, profils, securite

| Aspect | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Themes multiples** | 6 themes fixes (Plex, MonoDark, MonoLight, Morocco, OLEDBlack, Netflix) | Palettes custom configurables | ThemeProvider |
| **Profils applicatifs** | Oui (AppProfile: emoji, kids, PIN) | Non (users Jellyfin serveur) | Non (profils Plex serveur) |
| **Profils serveur** | Oui (Plex Home: PlexHomeSwitcherScreen) | Oui (Jellyfin users: SwitchUserContent) | Oui (Plex Home complet) |
| **PIN protection** | Oui (ParentalPinDialog) | Oui (PinEntry.kt) | Oui (Plex PIN) |
| **Pref par serveur** | Via ServerEntity | Oui (ServerPreferencesDao dedie) | Via MultiServerManager |
| **Pref d'affichage/lib** | Non persiste | Oui (LibraryDisplayInfoDao) | Via HiddenLibrariesProvider |
| **Subtitle style** | Oui (SubtitleStyleScreen) | Probable | Oui (ASS/SSA complet) |
| **Quick Connect** | Non (token/PIN uniquement) | Oui (QuickConnectDialog) | Non |

---

## SECTION 5 — Diagnostics, logs, stabilite, performance

| Aspect | PlexHubTV | Wholphin | Plezy |
|---|---|---|---|
| **Debug screen** | Oui (DebugScreen + DebugViewModel + DeviceProfile) | Oui (DebugPage) | Non mentionne |
| **Player debug overlay** | Oui (PerformanceOverlay — 7 metriques, PROD) | Oui (PlaybackDebugOverlay — ~10 metriques, DEBUG) | Non mentionne |
| **Crash reporting** | Firebase Crashlytics (production-grade) + Performance Monitoring | ACRA | Sentry |
| **Exception handler** | GlobalCoroutineExceptionHandler | Non visible | Sentry auto |
| **Performance tracker** | PerformanceTracker + PerformanceImageInterceptor | Non | Health probes periodiques |
| **Auto-update** | UpdateChecker + ApkInstaller (FileProvider, direct install) | UpdateChecker + InstallUpdatePage | UpdateService |
| **Error overlay player** | PlayerErrorOverlay (diagnostic + retry + MPV switch) | Non | Non mentionne |
| **Offline degradation** | OfflineWatchSync | Non | Oui (OfflineModeProvider avance + downloads) |

---

## SECTION 6 — Catalogue des features (avec statut PlexHubTV)

### Features deja presentes dans PlexHubTV (12/15 de l'audit Wholphin)

| # | Feature | Origine | Statut PlexHubTV |
|---|---|---|---|
| F01 | Home Backdrop Reactif | Wholphin | ✅ BackdropColors + AppBackdrop + focus-driven |
| F02 | Home Rows Configurables | Wholphin | ✅ Visibilite + Move Up/Down + persistence DataStore |
| F03 | MediaSession | Wholphin | ✅ MediaSessionManager + PlayerController |
| F04 | Telechargement sous-titres | Wholphin | ✅ SubtitleSearchService + DownloadSubtitlesDialog |
| F05 | Delai sous-titres | Wholphin | ✅ SyncSettingsDialog (7 boutons) |
| F06 | Refresh Rate Adaptatif | Wholphin | ✅ RefreshRateManager |
| F07 | Device Profile Adaptatif | Wholphin | ✅ DeviceProfileService + DebugScreen |
| F10 | Playlists | Wholphin | ✅ 6 endpoints + Room + UI + sidebar + 19 tests |
| F11 | Crash Reporting | Wholphin | ✅ Firebase Crashlytics (superieur a ACRA) |
| F13 | Suppression media | Wholphin | ✅ DeleteMediaUseCase + full stack |
| F14 | Favoris Personnes | Wholphin | ✅ PersonFavoriteEntity + DAO + Repository + heart button |
| F15 | Install direct MAJ | Wholphin | ✅ ApkInstaller + FileProvider |

### Features Wholphin encore exclusives

| # | Feature | Description | Fichiers Wholphin | Interet |
|---|---|---|---|---|
| W1 | Vue Chapitres/Queue overlay | Etat alternatif de l'overlay player pour naviguer chapitres et gerer la queue | `PlaybackOverlay.kt` | Moyen — PlexHubTV a deja ChapterOverlay + QueueOverlay separes |
| W2 | 4 types de segments skip | Intro, Outro, Commercial, Preview vs 2 pour PlexHubTV | `SkipIndicator.kt` | Faible — Plex n'expose que intro/credits |
| W3 | Discovery Jellyseerr | Browse, request, permissions via Seerr | `api/seerr/`, `SeerrService.kt`, `ui/discover/` | Haut — Overseerr (fork Plex) applicable |
| W4 | EPG / Live TV complet | Grille EPG, DVR, programmes | `ui/detail/livetv/` (8 fichiers) | Haut — Manque pour IPTV serieux |
| W5 | Quick Connect | Setup facile Jellyfin | `QuickConnectDialog.kt` | Moyen — Plex a GDM discovery |
| W6 | Preferences affichage/lib | Persister tri/vue/filtres par bibliotheque | `LibraryDisplayInfoDao`, `LibraryDisplayInfo` | Moyen |
| W7 | Voice search | Recherche vocale Android TV | `VoiceSearchButton.kt` | Moyen |

### Features Plezy exclusives (nouvelles par rapport a l'audit Wholphin)

| # | Feature | Description | Fichiers Plezy | Interet | Complexite |
|---|---|---|---|---|---|
| P1 | **Sleep Timer** | Timer d'arret automatique lecture | `services/sleep_timer_service.dart` | **Haut** — Tres demande | **Faible** |
| P2 | **Picture-in-Picture** | Lecture dans fenetre flottante | `services/pip_service.dart`, `video_pip_manager.dart` | **Haut** | Moyen |
| P3 | **Watch Together** | Lecture synchronisee entre amis | `lib/watch_together/` (feature complete) | Haut | **Haute** |
| P4 | **Health probes serveur** | Sonde sante periodique + fallback offline | `server_connection_orchestrator.dart`, `multi_server_manager.dart` | **Haut** | Moyen |
| P5 | **Companion Remote** | Controle depuis un autre appareil | `companion_remote/` (screens, services, widgets, models) | Moyen | Haute |
| P6 | **Video shaders** | Anime4K, NVScaler upscaling | `shader_service.dart`, `shader_asset_loader.dart`, assets GLSL | Moyen | Moyen |
| P7 | **Lecteur externe** | Support VLC, Infuse, etc. | `external_player_service.dart` | Faible (Android TV) | Faible |
| P8 | **Discord Rich Presence** | Afficher ce qu'on regarde sur Discord | `discord_rpc_service.dart` | Faible (pas Discord sur TV) | Faible |
| P9 | **Metadata editing** | Editer metadonnees depuis le client | `metadata_edit_screen.dart` | Faible | Moyen |
| P10 | **i18n etendue** | 16 langues | `lib/i18n/` | Moyen | Faible |
| P11 | **Ambient lighting** | Eclairage ambiant synchronise | `ambient_lighting_service.dart` | Faible (niche) | Moyen |
| P12 | **Gamepad service** | Support complet manettes | `gamepad_service.dart` | Moyen | Moyen |
| P13 | **Focus memory** | Memoriser position du focus entre navigations | `focus_memory_tracker.dart` | Moyen (concept) | Faible |
| P14 | **Input mode detection** | Auto-detect telecommande vs touch vs gamepad | `input_mode_tracker.dart` | Moyen (concept) | Faible |

---

## SECTION 7 — Backlog priorise pour PlexHubTV

### MUST-HAVE (court terme, effort faible)
| # | Feature | Origine | Impact | Effort | Description |
|---|---|---|---|---|---|
| 1 | **Sleep Timer** | Plezy P1 | 5/5 | 1/5 | Timer CountDown dans `feature/player/controller/SleepTimerManager.kt`. Options: 15/30/60min, fin episode, fin film |
| 2 | **Preferences affichage par lib** | Wholphin W6 | 3/5 | 2/5 | Entite Room `LibraryDisplayPreference` + DAO + lecture dans `LibraryViewModel` |

### NICE-TO-HAVE (moyen terme)
| # | Feature | Origine | Impact | Effort | Description |
|---|---|---|---|---|---|
| 3 | **Health probes serveur** | Plezy P4 | 4/5 | 3/5 | Enrichir `ServerClientResolver` avec probes periodiques + fallback graceful |
| 4 | **Picture-in-Picture** | Plezy P2 | 3/5 | 3/5 | Android PiP API + Activity config + Media3 keep-alive |
| 5 | **Voice search** | Wholphin W7 | 3/5 | 2/5 | `SpeechRecognizer` API + integration `SearchViewModel` |
| 6 | **Focus memory tracker** | Plezy P13 | 3/5 | 2/5 | `rememberSaveable` pattern pour persister position focus |
| 7 | **i18n etendue** | Plezy P10 | 3/5 | 2/5 | Ajouter 3-5 langues prioritaires (ES, DE, PT, IT) |
| 8 | **Gamepad support** | Plezy P12 | 2/5 | 2/5 | Mapping manettes pour navigation + player |

### FUTURE / EXPERIMENTAL
| # | Feature | Origine | Impact | Effort | Description |
|---|---|---|---|---|---|
| 9 | **EPG / Live TV** | Wholphin W4 + Plezy | 5/5 | 5/5 | Grille EPG (XMLTV parser) + integration Plex Live TV + Xtream EPG |
| 10 | **Discovery Overseerr** | Wholphin W3 | 5/5 | 5/5 | API Overseerr (fork Plex de Jellyseerr) |
| 11 | **Watch Together** | Plezy P3 | 4/5 | 5/5 | Plex Watch Together API + sync temps reel |
| 12 | **Companion Remote** | Plezy P5 | 4/5 | 5/5 | Plex Companion protocol |
| 13 | **Server Discovery (GDM)** | Wholphin W5 | 3/5 | 3/5 | Plex GDM multicast discovery |
| 14 | **Video shaders** | Plezy P6 | 2/5 | 3/5 | MPV shader API (Anime4K, NVScaler) |

---

## SECTION 8 — Recommandations d'architecture multi-sources

### 1. Data & multi-serveurs

**PlexHubTV est deja en avance** grace a `MediaSourceHandler` + `UnificationId` + `IdBridgeEntity`. Recommandations :

**Pattern Strategy + Registry (existant dans jolly-toasting-neumann.md ETAPE 6) :**
```
domain/source/
├── MediaSourceHandler.kt          (existant)
├── MediaSourceCapabilities.kt     (NOUVEAU — declare ce que la source supporte)
└── MediaSourceRegistry.kt         (NOUVEAU — enregistre les sources disponibles)

data/source/
├── PlexSourceHandler.kt           (existant)
├── BackendSourceHandler.kt        (existant)
├── XtreamSourceHandler.kt         (existant)
├── JellyfinSourceHandler.kt       (FUTUR)
└── MediaSourceResolver.kt         (existant — a enrichir)
```

**Health probes (inspire Plezy)** : `ServerConnectionOrchestrator` de Plezy avec sonde sante periodique (10s mobile, 2min desktop) et fallback offline. Implementer dans `ServerClientResolver` ou nouveau `ServerHealthMonitor`.

**Preferences par bibliotheque (inspire Wholphin)** : `LibraryDisplayInfoDao` + entite Room. Persister tri/vue/filtres par lib.

**Future Jellyfin** : SDK Jellyfin Kotlin directement reutilisable. Mapping via `JellyfinMediaMapper`. IDs resolus par `UnificationId` existant (IMDB/TMDB).

### 2. Player

**PlexHubTV a le player le plus complet et le mieux architecture** des trois apps (13 composants vs 1 ViewModel monolithique Wholphin). Ajouts recommandes :

- **Sleep timer** : `SleepTimerManager` dans `feature/player/controller/`. Options: 15/30/60min, fin episode, fin film.
- **PiP** : `Activity.enterPictureInPictureMode()` + `AndroidManifest.xml` config + Media3 keep-alive.

### 3. UX Android TV

- **Focus memory** (concept Plezy) : `rememberSaveable` pour persister position focus entre navigations.
- **Voice search** (Wholphin) : `VoiceSearchButton` + `SpeechRecognizer` API.
- **Input mode detection** (concept Plezy) : Adapter l'UI selon telecommande vs touch vs gamepad.

### 4. Diagnostics & stabilite

PlexHubTV est deja bien equipe (Firebase Crashlytics, PerformanceTracker, DebugScreen, PlayerErrorOverlay). Ameliorations :

- **Health probes serveur** (Plezy) : Detecter deconnexions plus tot.
- **Log export** : Permettre export depuis DebugScreen.

---

## Ecart restant avec Wholphin et Plezy (resume)

| Dimension | vs Wholphin | vs Plezy |
|---|---|---|
| Features player | ✅ PlexHubTV superieur (+12 points scoring) | ✅ PlexHubTV superieur en core, Plezy a PiP/Watch Together/shaders |
| Home page | ✅ Equivalent (les deux configurables) | ✅ PlexHubTV superieur (Netflix-style + backdrop) |
| Discovery | ❌ Wholphin a Jellyseerr | ✅ Equivalent (Plex Discover) |
| Live TV | ❌ Wholphin a EPG complet | ❌ Plezy a EPG complet |
| Multi-source | ✅ PlexHubTV superieur (3 sources vs 1) | ✅ PlexHubTV superieur |
| Architecture | ✅ PlexHubTV superieur (Clean Arch + Use Cases) | ✅ PlexHubTV superieur (natif vs Flutter) |
| Tests | ✅ PlexHubTV superieur (36 fichiers, ~315 methodes) | ✅ PlexHubTV superieur |
| Profils | ✅ PlexHubTV superieur (double systeme) | ✅ Equivalent |
| Offline/Downloads | ✅ Equivalent | ❌ Plezy superieur (OfflineModeProvider avance) |
| Cross-platform | ❌ Android TV only | ❌ Plezy sur 6 plateformes |
| i18n | ❌ 2 langues | ❌ Plezy: 16 langues |
| Watch Together | ❌ Absent | ❌ Plezy a feature complete |
| PiP | ❌ Absent | ❌ Plezy a PiP |
| Sleep Timer | ❌ Absent | ❌ Plezy a SleepTimer |

**Conclusion** : PlexHubTV domine en architecture, player core, et multi-source. Les manques principaux sont des **features UX** (sleep timer, PiP, EPG, Watch Together) et des **features sociales/cross-platform** (Watch Together, i18n, Companion Remote) — largement inspires de Plezy qui est le plus riche en features utilisateur.
