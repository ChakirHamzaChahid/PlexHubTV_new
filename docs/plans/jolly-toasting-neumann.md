# Analyse Comparative : PlexHubTV vs Wholphin

---

# ETAPE 1 — Architecture PlexHubTV

## Stack technique

| Composant | Technologie |
|-----------|-------------|
| Langage | Kotlin 100% |
| UI | Jetpack Compose + Material 3 + androidx.tv |
| Architecture | Clean Architecture (Presentation / Domain / Data / Core) |
| DI | Hilt (13 modules) |
| DB | Room v42, WAL journaling |
| Network | Retrofit + OkHttp + Gson |
| Player | ExoPlayer (Media3) + MPV |
| Images | Coil |
| Preferences | DataStore (encrypted) |
| Background | WorkManager (6 workers) |
| Navigation | Compose Navigation |

## Modules et volume de code

| Module | Fichiers .kt | Responsabilite |
|--------|-------------|----------------|
| `app/` (features) | 151 | Screens, ViewModels, Workers, DI |
| `core/database/` | 42 | Room entities, DAOs, migrations |
| `core/network/` | 32 | API clients (Plex, OMDB, TMDB, Xtream, Backend) |
| `core/model/` | 34 | Domain models (value objects) |
| `core/common/` | 11 | Utils (codec, format, cache, string) |
| `core/datastore/` | 4 | DataStore preferences |
| `core/navigation/` | 2 | Route definitions (Screen.kt) |
| `core/designsystem/` | 3 | Theme, Color, Typography |
| `core/ui/` | 12 | Composants reusables (cards, rows, hero) |
| `data/` | 52 | Repository impls, mappers, query builders |
| `domain/` | 62 | Interfaces, Use Cases (25), Services |
| **TOTAL** | **469** | |

## Packages app/ et leur role

| Package | Fichiers | Role |
|---------|----------|------|
| `feature/splash/` | 2 | Ecran de lancement |
| `feature/auth/` | 4 | Login Plex (token + PIN) |
| `feature/plexhome/` | 3 | Changement utilisateur Plex Home |
| `feature/libraryselection/` | 2 | Selection des bibliotheques a synchroniser |
| `feature/loading/` | 2 | Ecran de sync initial |
| `feature/appprofile/` | 5 | Profils locaux (emoji, kids mode, PIN) |
| `feature/main/` | 2 | Shell app (sidebar + NavHost) |
| `feature/home/` | 5+ | Home Netflix-style (hero, hubs, suggestions) |
| `feature/hub/` | 3+ | On Deck / Continue Watching |
| `feature/library/` | 6+ | Grille media avec filtres, tri, pagination |
| `feature/search/` | 4 | Recherche full-text multi-serveur |
| `feature/details/` | 8+ | Detail film/serie/personne, enrichissement |
| `feature/collection/` | 3 | Collections de films |
| `feature/player/` | **26** | Player complet (ExoPlayer+MPV, trickplay, chapters, MediaSession, equalizer, profiles) |
| `feature/playlist/` | 6 | Playlists Plex (list, detail, create, add) |
| `feature/favorites/` | 2 | Liste "My List" |
| `feature/history/` | 2 | Historique de visionnage |
| `feature/downloads/` | 3 | Telechargements (stub) |
| `feature/iptv/` | 2 | Lecteur IPTV M3U |
| `feature/xtream/` | 4 | Comptes Xtream Codes |
| `feature/settings/` | **15** | Settings avec sous-ecrans par categorie |
| `feature/screensaver/` | 3 | DreamService (slideshow artwork) |
| `feature/debug/` | 3 | Ecran debug |
| `work/` | 6 | LibrarySync, RatingSync, CollectionSync, ChannelSync, UnifiedRebuild, CachePurge |
| `di/` | 8 | Modules Hilt (App, Image, Firebase, Work) |

## Couche data — Modeles, DAOs, Repositories

### Entities Room (19 tables)
- `MediaEntity` — Films, series, episodes (table principale)
- `MediaUnifiedEntity` — Index agrege multi-serveur
- `MediaFts` — Index full-text search
- `ServerEntity` — Serveurs Plex
- `LibrarySectionEntity` — Sections de bibliotheque
- `CollectionEntities` — Collections + membres
- `HomeContentEntity` — Contenu home rows
- `SearchCacheEntity` — Cache recherche
- `FavoriteEntity` — Favoris
- `TrackPreferenceEntity` — Preferences audio/sous-titres
- `OfflineWatchProgressEntity` — Progression offline
- `ProfileEntity` — Profils locaux
- `ApiCacheEntity` — Cache HTTP
- `RemoteKey` — Curseurs de pagination
- `XtreamAccountEntity` — Comptes IPTV Xtream
- `IdBridgeEntity` — Mapping d'IDs cross-serveur
- `BackendServerEntity` — Serveurs backend
- `PersonFavoriteEntity` — Favoris acteurs/realisateurs (v41)
- `PlaylistEntity` + `PlaylistItemEntity` — Playlists Plex (v42)

### Repositories (21 implementations)
Architecture rigoureuse : chaque repository a une **interface dans domain/** et une **implementation dans data/**.

Repositories query : Library, MediaDetail, CollectionDetail, Search, OnDeck, Hubs, Favorites, Watchlist
Repositories metadata : Playback, TrackPreference, Downloads, Profile, Settings
Repositories externes : Backend, Iptv, XtreamAccount, XtreamVod, XtreamSeries
Repositories system : Auth, Account, Sync, OfflineWatchSync

### Query Builder
`MediaLibraryQueryBuilder.kt` — Generateur SQL complexe :
- Queries unifiees (multi-serveur) avec GROUP BY + GROUP_CONCAT
- Queries non-unifiees (single source)
- Correlated MAX pour selection de la bonne row
- Filtrage par genre, annee, note
- Tri par rating, date, titre, offset API

## Couche UI — Ecrans et composants

### Composants reusables (core/ui/)
- `NetflixHeroBillboard` — Section hero avec poster
- `NetflixMediaCard` — Carte media (poster + metadata)
- `NetflixContentRow` — Row horizontale scrollable
- `NetflixTopBar` — Top bar avec horloge
- `NetflixOnScreenKeyboard` — Clavier virtuel TV
- `BackdropColors` — Extraction couleurs depuis image
- `FallbackAsyncImage` — Image avec fallback
- `OverscanSafeArea` — Marge safe pour TV
- `Skeletons` — Loading shimmer

### Architecture Player (26 fichiers)
```
VideoPlayerScreen
  -> PlayerControlViewModel (UI state)
     -> PlayerController (orchestrateur)
        ├── PlayerInitializer (chargement media)
        ├── PlayerMediaLoader (resolution URLs + DeviceProfileService)
        ├── PlayerScrobbler (sync progression)
        ├── PlayerStatsTracker (metriques)
        ├── PlayerTrackController (audio/sous-titres)
        ├── ChapterMarkerManager (chapitres)
        ├── TrickplayManager (preview seek)
        ├── MediaSessionManager (controles systeme)
        ├── RefreshRateManager (match framerate ecran)
        ├── AudioEqualizerManager (equalizer 10 bandes)
        └── SubtitleSearchService (OpenSubtitles download)
     -> ExoPlayer / MpvPlayer
  -> profile/DeviceProfileService (detection codecs device)
```

URL Builders : DirectStream, Transcode, Backend, Xtream

## Points forts PlexHubTV

1. **Multi-source reelle** — Plex + Backend + Xtream unifies dans une seule vue
2. **Aggregation multi-serveur** — GROUP BY + correlated MAX pour deduplication
3. **Clean Architecture stricte** — Separation claire Presentation/Domain/Data/Core
4. **25+ Use Cases** — Logique metier testable et isolee (DeleteMediaUseCase, etc.)
5. **Enrichissement media** — Room-first + fallback OMDB/TMDB
6. **Rating system** — displayRating pre-calcule et indexe
7. **Player modulaire** — Architecture controller decomposee (13 composants dont MediaSession, RefreshRate, Equalizer, SubtitleSearch)
8. **Profils locaux** — Systeme complet avec emoji, kids mode, PIN parental
9. **DreamService** — Screensaver avec Compose integration
10. **Auto-update complet** — GitHub Releases API + download APK + install direct (FileProvider)
11. **Device Profile adaptatif** — Detection codecs via MediaCodecList, decision Direct Play intelligente
12. **Playlists** — CRUD complet via Plex API, sidebar, ajout depuis detail
13. **Gestion media** — Suppression serveur avec verification ownership + confirmation
14. **Favoris personnes** — Toggle favori sur PersonDetailScreen, persistence Room

## Points faibles / Manques PlexHubTV (mis a jour Mars 2026)

1. **Pas de support Jellyfin** — Single-source Plex
2. **Pas de Jellyseerr/Overseerr** — Pas de discovery/request
3. ~~**Pas de telechargement sous-titres**~~ — ✅ RESOLU: OpenSubtitles integre (SubtitleSearchService)
4. ~~**Home page statique**~~ — ✅ RESOLU: Backdrop reactif (BackdropColors + AppBackdrop + focus-driven), visibilite rows configurable
5. **Live TV basique** — M3U parser seulement, pas d'EPG
6. ~~**Pas de gestion playlist**~~ — ✅ RESOLU: Playlists CRUD complet (6 endpoints Plex API + Room + sidebar)
7. **Pas de Quick Connect** — Login par token/PIN uniquement
8. ~~**Pas de MediaSession**~~ — ✅ RESOLU: MediaSessionManager integre dans PlayerController
9. ~~**Home rows non reordonnables**~~ — ✅ RESOLU: Visibilite configurable + Move Up/Down reordering avec persistence DataStore
10. ~~**Pas de device profile adaptatif**~~ — ✅ RESOLU: DeviceProfileService avec detection codecs MediaCodecList

---

# ETAPE 2 — Architecture Wholphin

## Stack technique

| Composant | Technologie |
|-----------|-------------|
| Langage | Kotlin 98.2% |
| UI | Jetpack Compose + Material 3 + androidx.tv |
| Architecture | MVVM + Services Layer |
| DI | Hilt |
| DB | Room v31 (10 entities) |
| Network | Jellyfin SDK (OkHttp) |
| Player | ExoPlayer (Media3) + MPV + FFmpeg optionnel |
| Images | Coil (+ GIF, SVG) |
| Preferences | DataStore |
| Background | WorkManager (Suggestions worker) |
| Navigation | Compose Navigation + NavDrawer |
| Crash Report | ACRA |

## Patterns architecturaux

- **MVVM** — ViewModels avec StateFlow/LiveData
- **Repository** — ServerRepository, ItemPlaybackRepository, SeerrServerRepository
- **Services Layer** — 35+ services metier (pas de Use Cases formels)
- **Hilt DI** — Injection dans services, ViewModels, repositories
- **Room-First** — Cache local prioritaire, API en fallback

**Difference cle vs PlexHubTV** : Wholphin n'a PAS de couche Use Case formelle. La logique metier est distribuee dans des **Services** (35+) qui combinent orchestration et acces donnees. C'est plus pragmatique mais moins testable en isolation.

## Analyse de la couche data (Room + DAOs)

### Database (Room v31, 10 entities)

| Entity | Role |
|--------|------|
| `JellyfinServer` | Config connexion serveur |
| `JellyfinUser` | Utilisateurs authentifies |
| `ItemPlayback` | Progression lecture + resume |
| `NavDrawerPinnedItem` | Items epingles dans le drawer |
| `LibraryDisplayInfo` | Config affichage par bibliotheque |
| `PlaybackEffect` | Effets de lecture (vitesse, etc.) |
| `PlaybackLanguageChoice` | Preferences langue audio/sous-titres |
| `ItemTrackModification` | Modifications tracks (delai, sync) |
| `SeerrServer` | Config serveur Jellyseerr |
| `SeerrUser` | Utilisateurs Seerr |

### DAOs (7)
- `JellyfinServerDao` — CRUD serveurs
- `ItemPlaybackDao` — CRUD progression lecture
- `ServerPreferencesDao` — Preferences par serveur
- `LibraryDisplayInfoDao` — Config affichage
- `PlaybackLanguageChoiceDao` — Choix langue
- `SeerrServerDao` — CRUD Seerr
- `PlaybackEffectDao` — Effets lecture

### Repositories (3)
- `ServerRepository.kt` (11KB) — Gestion complete serveurs Jellyfin
- `ItemPlaybackRepository.kt` (9.8KB) — Etat lecture, tracks, langues
- `SeerrServerRepository.kt` — Integration Jellyseerr

**Note** : Wholphin a 3 repositories vs 21 pour PlexHubTV. La logique est concentree dans les Services.

## Analyse de la couche API Jellyfin

- **Jellyfin SDK** : `libs.jellyfin.core` + `libs.jellyfin.api` + `libs.jellyfin.api.okhttp`
- **Client type** : SDK officiel avec endpoints types (Items, Users, Sessions, LiveTV, etc.)
- **Device Profile** : `DeviceProfileService` detecte les capabilities codec du device
- **Connection** : Via `ServerRepository` qui gere les `JellyfinServer` entities
- **Auth** : Quick Connect + Username/Password

## Analyse de la couche UI Compose

### Ecrans principaux
- `HomePage.kt` — Home avec rows configurables
- `SearchPage.kt` — Recherche
- `MovieDetails.kt` + `MovieDetailsHeader.kt` — Detail film
- `SeriesDetails.kt` + `SeriesOverview.kt` + `SeriesOverviewContent.kt` — Detail serie
- `EpisodeDetails.kt` + `EpisodeDetailsHeader.kt` — Detail episode
- `PlaybackPage.kt` — Lecteur video plein ecran
- `DiscoverPage.kt` + `SeerrDiscoverPage.kt` — Discovery Jellyseerr
- `PreferencesContent.kt` — Settings
- `ServerList.kt` + `UserList.kt` — Setup

### Composants cartes (14 fichiers dans ui/cards/)
- `BannerCard`, `GridCard`, `EpisodeCard`, `SeasonCard`, `PersonCard`
- `ChapterCard`, `GenreCard`, `DiscoverItemCard`
- `ItemRow`, `PersonRow`, `ChapterRow`, `ExtrasRow`
- `ItemCardImage`, `WatchedIcon`

### Player (26 fichiers)
- Dual engine : ExoPlayer + MPV
- `PlaybackControls.kt` — Boutons de controle
- `PlaybackOverlay.kt` — OSD
- `PlaybackKeyHandler.kt` — Gestion D-pad/telecommande
- `SeekBar.kt` + `SeekBarState.kt` + `SeekAcceleration.kt` — Seekbar avancee
- `SeekPreviewImage.kt` — Trickplay thumbnails
- `DownloadSubtitlesDialog.kt` + `SubtitleDelay.kt` + `SubtitleSearchUtils.kt` — Gestion sous-titres
- `NextUpEpisode.kt` — Auto-play prochain episode
- `SkipIndicator.kt` — Skip intro/outro
- `MediaSessionPlayer.kt` — Integration MediaSession
- `PlaybackDebugOverlay.kt` — Debug overlay

### Navigation
- **NavDrawer** (lateral, style Plex) — `NavDrawer.kt` + `NavigationDrawerAndroid.kt`
- **Destinations** : `Destination.kt` + `DestinationContent.kt`
- **ApplicationContent.kt** — Structure top-level

### Themes
- Multiple themes couleur configurables par l'utilisateur
- `Theme.kt`, `ThemeColors.kt`, `Type.kt`
- Sous-dossier `colors/` avec palettes multiples

## Features implementees avec localisation

| Feature | Package/Fichiers |
|---------|-----------------|
| Home configurable | `ui/main/HomePage.kt`, `HomeViewModel.kt` |
| Recherche | `ui/main/SearchPage.kt` |
| Detail film | `ui/detail/movie/MovieDetails.kt`, `MovieViewModel.kt` |
| Detail serie | `ui/detail/series/SeriesDetails.kt`, `SeriesViewModel.kt` |
| Detail episode | `ui/detail/episode/EpisodeDetails.kt`, `EpisodeViewModel.kt` |
| Player dual engine | `ui/playback/` (26 fichiers) |
| Trickplay | `ui/CoilTrickplayTransformation.kt`, `SeekPreviewImage.kt` |
| MediaSession | `ui/playback/MediaSessionPlayer.kt` |
| Telechargement sous-titres | `ui/playback/DownloadSubtitlesDialog.kt`, `SubtitleSearchUtils.kt` |
| Delai sous-titres | `ui/playback/SubtitleDelay.kt` |
| Skip intro/outro | `ui/playback/SkipIndicator.kt` |
| Next Up auto-play | `ui/playback/NextUpEpisode.kt` |
| Theme song | `services/ThemeSongPlayer.kt` |
| Discovery Jellyseerr | `ui/discover/DiscoverPage.kt`, `SeerrDiscoverPage.kt` |
| Requests Seerr | `ui/discover/SeerrRequestsPage.kt` |
| Favoris personnes | `services/PeopleFavorites.kt` |
| Playlists | `data/model/Playlist.kt`, `ui/detail/PlaylistDetails.kt` |
| Screensaver | `WholphinDreamService.kt`, `ui/slideshow/`, `services/ScreensaverService.kt` |
| Auto-update | `services/UpdateChecker.kt`, `ui/setup/InstallUpdatePage.kt` |
| Quick Connect | `ui/preferences/QuickConnectDialog.kt` |
| PIN entry | `ui/setup/PinEntry.kt` |
| Server switching | `ui/setup/SwitchServerContent.kt` |
| User switching | `ui/setup/SwitchUserContent.kt` |
| EPG/Live TV | `ui/detail/livetv/` |
| Filtres bibliotheque | `data/filter/FilterValueOption.kt`, `ItemFilterBy.kt` |
| Device profile | `services/DeviceProfileService.kt` |
| Refresh rate adaptatif | `services/RefreshRateService.kt` |
| Suggestions background | `services/SuggestionsWorker.kt`, `SuggestionService.kt` |
| Gestion media | `services/MediaManagementService.kt` (suppression) |
| Backdrop dynamique | `services/BackdropService.kt` |
| Extras/trailers | `services/ExtrasService.kt`, `TrailerService.kt` |
| Server events | `services/ServerEventListener.kt` |

## Points forts Wholphin

1. **Services Layer riche** — 35+ services specialises, decouples
2. **Jellyfin SDK natif** — Integration profonde (device profile, sessions, live TV)
3. **Player tres complet** — 26 fichiers, MediaSession, subtitle download, delay adjust
4. **Discovery Jellyseerr** — Integration Seerr complete (browse, requests, permissions)
5. **Home configurable** — Rows reordonnables par l'utilisateur (PlexHubTV a maintenant un equivalent)
6. **Backdrop reactif** — Change dynamiquement selon l'item focus
7. **Device Profile adaptatif** — Detection codecs device
8. **Refresh rate switching** — Match le framerate du contenu
9. **Quick Connect** — Setup facile
10. **Crash reporting** — ACRA integre

## Limites / Specificites Jellyfin-only

1. **Mono-source** — Jellyfin uniquement, pas d'agregation multi-serveur
2. **Pas de Use Cases** — Logique metier dans services (moins testable)
3. **DB legere** — 10 entities seulement (pas de cache media local complet)
4. **Pas de sync offline** — Tout est online
5. **Pas de rating scraping** — Utilise les ratings Jellyfin telles quelles
6. **Pas de profils locaux** — Depend des profils Jellyfin serveur

---

# ETAPE 3 — Tableau comparatif detaille

| Dimension | PlexHubTV | Wholphin | Ecart |
|-----------|-----------|----------|-------|
| **Source(s) media** | Plex + Backend custom + Xtream IPTV | Jellyfin uniquement | PlexHubTV multi-source, Wholphin mono |
| **Architecture data** | Clean Arch stricte : Domain(interfaces) -> Data(impls), 21 repos, 25 use cases | MVVM + Services : 3 repos, 35+ services | PlexHubTV plus structure, Wholphin plus pragmatique |
| **Playback engine** | ExoPlayer + MPV, architecture controller decomposee (13 composants: MediaSession, RefreshRate, Equalizer, SubtitleSearch, DeviceProfile) | ExoPlayer + MPV + FFmpeg optionnel, 26 fichiers playback | **Equivalent a superieur** — PlexHubTV a rattrape tout l'ecart: MediaSession, subtitle download, delay, equalizer, refresh rate, device profile |
| **UI navigation** | Sidebar fixe + Compose Navigation | NavDrawer lateral + Compose Navigation | Equivalent en navigation, drawer vs sidebar |
| **Home page** | Netflix-style : Hero billboard + backdrop reactif au focus + rows visibilite configurable + reordonnables (Move Up/Down + persistence DataStore) | Rows configurables + reordonnables, backdrop reactif au focus, Semaphore(4) loading | **Equivalent** — PlexHubTV a maintenant visibilite + reordering (Move Up/Down avec persistence) |
| **Ecran detail** | Film/Serie/Episode/Collection/Personne/Playlist, enrichissement OMDB/TMDB, suppression media, favoris personnes | Film/Serie/Episode/Playlist/Collection, extras/trailers | **PlexHubTV superieur** : enrichissement externe + playlists + delete + person favorites |
| **Gestion profils** | Plex Home (serveur) + Profils locaux (Room, emoji, kids, PIN) | Profils Jellyfin serveur uniquement | **PlexHubTV superieur** : double systeme profils |
| **Persistence locale** | Room v42, 19 entities, cache media complet, FTS, aggregation, playlists, person favorites | Room v31, 10 entities, cache leger (playback + prefs) | **PlexHubTV superieur** : cache local riche pour offline |
| **Gestion images** | Coil + PlexImageKeyer + prefetch + PerformanceInterceptor | Coil + GIF/SVG + TrickplayTransformation | Equivalent. PlexHubTV a prefetch, Wholphin a GIF/SVG |
| **Themes** | 6 themes (Plex, MonoDark, MonoLight, Morocco, OLEDBlack, Netflix) | Multiple themes avec palettes configurables | Quasi-equivalent — PlexHubTV 6 themes fixes, Wholphin palettes custom |
| **Screensaver** | DreamService + Compose (artwork slideshow + clock) | DreamService + ScreensaverService + slideshow | Equivalent |
| **Discovery** | Suggestions locales (3 strategies genre/random/fresh) | Jellyseerr integration (browse, request, permissions) | **Wholphin superieur** : Seerr est un vrai discovery engine |
| **Sous-titres** | Selection track + styling + delai ajustable + telechargement OpenSubtitles (SubtitleSearchService + DownloadSubtitlesDialog) | Selection + telechargement OpenSubtitles + delai ajustable | **Equivalent** — PlexHubTV a maintenant le download OpenSubtitles + delai |
| **Live TV** | M3U parser + Xtream Codes | Jellyfin Live TV + DVR + EPG | **Wholphin superieur** : EPG natif |
| **Auto-update** | GitHub Releases API + APK download + install direct (ApkInstaller + FileProvider) | UpdateChecker + InstallUpdatePage (install direct) | **Equivalent** — PlexHubTV a maintenant l'install direct via FileProvider |
| **i18n** | Francais partiel (strings.xml EN, comments FR) | Anglais | Aucun n'est multi-langue |
| **MediaSession** | MediaSessionManager.kt integre dans PlayerController | MediaSessionPlayer.kt integre | **Equivalent** |
| **Device Profile** | DeviceProfileService (detection codecs MediaCodecList) + integration PlayerMediaLoader + TranscodeUrlBuilder + DebugScreen | DeviceProfileService (detection codecs) | **Equivalent** |
| **Refresh Rate** | RefreshRateManager (match framerate Display.Mode API) + integration VideoPlayerScreen | RefreshRateService (match framerate) | **Equivalent** |
| **Crash reporting** | Firebase Crashlytics + GlobalCoroutineExceptionHandler + Performance Monitoring | ACRA integre | **Equivalent** — PlexHubTV a une solution plus complete (Crashlytics > ACRA) |
| **Tests** | 36 fichiers tests unitaires (~315 methodes), MockK + Turbine + Coroutines Test | MockK + Robolectric + Compose tests | **PlexHubTV superieur** en couverture quantitative |

---

# ETAPE 4 — Catalogue des features a reprendre

## F01 — Home Page Backdrop Reactif — ✅ DEJA IMPLEMENTE

- **Statut** : COMPLET — Rien a faire
- **Implementation PlexHubTV** :
  - `BackdropColors.kt` (core/ui) — Extraction palette Android Palette API, cache LRU 50 entrees
  - `MainScreen.kt` — `AppBackdrop` composable avec crossfade 600ms + gradients dynamiques
  - `DiscoverScreen.kt` — `LaunchedEffect(uiState.focusedItem)` pousse artUrl vers MainScreen
  - `HomeViewModel.kt` — `HomeAction.FocusMedia` met a jour `focusedItem`
- **Flow** : Focus item → ViewModel → LaunchedEffect → callback `onBackdropChanged` → MainScreen → AppBackdrop recompose

## F02 — Home Rows Configurables — ✅ IMPLEMENTE

- **Statut** : COMPLET — Visibilite configurable ✅ + Reordering ✅
- **Implementation PlexHubTV** :
  - `SettingsDataStore.kt` — 3 prefs booleennes (`SHOW_CONTINUE_WATCHING`, `SHOW_MY_LIST`, `SHOW_SUGGESTIONS`) + `HOME_ROW_ORDER` (String comma-separated, default `"continue_watching,my_list,suggestions"`)
  - `SettingsRepository.kt` / `SettingsRepositoryImpl.kt` — `homeRowOrder: Flow<List<String>>`, `updateHomeRowOrder(order: List<String>)`, `moveHomeRowUp(rowId)`, `moveHomeRowDown(rowId)`
  - `SettingsViewModel.kt` — `MoveHomeRowUp(rowId)` / `MoveHomeRowDown(rowId)` actions, observe `homeRowOrder` flow
  - `GeneralSettingsScreen.kt` — "Home Layout" section avec `HomeRowSettingsItem` composable: Move Up/Down IconButtons + Switch toggle par row, dynamique via `state.homeRowOrder.forEachIndexed`
  - `HomeViewModel.kt` — `observeHomeRowPreferences()` observe les 3 flows + `homeRowOrder`
  - `NetflixHomeScreen.kt` — Rows rendues dans l'ordre de `homeRowOrder`, visibilite par row
- **Rien a faire**

## F03 — MediaSession Integration — ✅ IMPLEMENTE

- **Statut** : COMPLET
- **Implementation PlexHubTV** :
  - `MediaSessionManager.kt` (player/controller/) — `@Singleton`, `initialize(player: ExoPlayer)` cree MediaSession "PlexHubTV", `release()` cleanup
  - `PlayerController.kt` — `mediaSessionManager` injecte (ligne 56), `initialize(it)` (ligne 596), `release()` (ligne 247)
  - `app/build.gradle.kts` — `implementation(libs.media3.session)` (ligne 194)
- **Rien a faire**

## F04 — Telechargement de sous-titres (OpenSubtitles)

- **Source Wholphin** : `ui/playback/DownloadSubtitlesDialog.kt`, `SubtitleSearchUtils.kt`
- **Valeur ajoutee** : Permet de trouver et charger des sous-titres absents du serveur
- **Complexite d'adaptation** : Haute
- **Dependances** : API OpenSubtitles (REST), cle API, UI dialog de recherche
- **Specificite Jellyfin** : Non — generique (hash fichier video pour matching)
- **Adaptabilite** : Reutilisable apres abstraction de la source video

## F05 — Delai sous-titres ajustable — ✅ IMPLEMENTE

- **Statut** : COMPLET — Le trigger UI EXISTE
- **Implementation PlexHubTV** :
  - `PlayerSettingsDialog.kt` — Onglet "Subtitles" contient "Subtitle Sync" qui appelle `onShowSubtitleSync()` (lignes 148-173)
  - `PlayerControlViewModel.kt` — `ShowSubtitleSyncSelector` handler met `showSubtitleSyncDialog = true` (lignes 140-142)
  - `VideoPlayerScreen.kt` — `SyncSettingsDialog` rendu quand `uiState.showSubtitleSyncDialog` (lignes 531-541)
  - `PlayerUiState.kt` — `subtitleDelay: Long`, `showSubtitleSyncDialog`
  - `MpvPlayerWrapper.kt` — `setSubtitleDelay()` → `MPVLib.setPropertyDouble("sub-delay", ...)`
- **Flow** : Settings Dialog → Subtitles tab → "Subtitle Sync" → SyncSettingsDialog
- **Rien a faire**

## F06 — Refresh Rate Adaptatif — ✅ IMPLEMENTE

- **Statut** : COMPLET
- **Implementation PlexHubTV** :
  - `RefreshRateManager.kt` (player/controller/) — `matchRefreshRate(activity, videoFrameRate)`, `restoreOriginalRate(activity)`, Display.Mode API, API 23+ checks
  - `VideoPlayerScreen.kt` — LaunchedEffect monitore isPlaying/isBuffering (lignes 103-111), appelle `matchRefreshRate()` (ligne 108), DisposableEffect appelle `restoreOriginalRate()` (ligne 118)
- **Rien a faire**

## F07 — Device Profile Adaptatif — ✅ IMPLEMENTE

- **Statut** : COMPLET
- **Implementation PlexHubTV** :
  - `DeviceProfileService.kt` (player/profile/) — `@Singleton`, `DeviceProfile` data class (videoCodecs, audioCodecs, maxWidth, maxHeight, supportsHDR, maxBitDepth), `canDirectPlayVideo()`, `canDirectPlayAudio()`, `detectProfile()` via MediaCodecList
  - `PlayerMediaLoader.kt` — DeviceProfileService injecte (ligne 29), `canDirectPlayVideo("hevc")` (ligne 100), `canDirectPlayAudio(audioCodec)` (ligne 109)
  - `TranscodeUrlBuilder.kt` — DeviceProfileService injecte (ligne 20), envoie `videoResolution`, `videoCodecs`, `audioCodecs` params (lignes 111-118)
  - `DebugScreen.kt` — Section "Device Profile" affiche codecs, resolution, HDR, bit depth (lignes 199-212)
  - `DebugUiState.kt` — `DeviceProfileInfo` data class (lignes 112-118)
  - `DebugViewModel.kt` — `collectDeviceProfileInfo()` (lignes 269-278)
- **Rien a faire**

## F08 — Discovery via Jellyseerr/Overseerr

- **Source Wholphin** : `api/seerr/`, `services/SeerrService.kt`, `ui/discover/`
- **Valeur ajoutee** : Permet de decouvrir du contenu, faire des requetes (ajout a la bibliotheque)
- **Complexite d'adaptation** : Haute
- **Dependances** : API Seerr (REST), config serveur Seerr, permissions
- **Specificite Jellyfin** : Seerr supporte Jellyfin ET Plex — directement applicable
- **Adaptabilite** : Bonne — Overseerr (fork Plex) ou Jellyseerr (fork Jellyfin) sont supportables

## F09 — EPG (Guide des Programmes)

- **Source Wholphin** : `ui/detail/livetv/`, programme guide library
- **Valeur ajoutee** : Grille EPG pour Live TV (indispensable pour IPTV serieux)
- **Complexite d'adaptation** : Haute
- **Dependances** : Parser XMLTV, composant grille EPG, gestion timezone
- **Specificite Jellyfin** : L'API EPG est Jellyfin-specific, mais le format XMLTV est standard
- **Adaptabilite** : UI reutilisable, source de donnees a adapter (XMLTV pour Xtream)

## F10 — Playlists Personnalisees — ✅ COMPLET (100%)

- **Statut** : COMPLET — Compilation verifiee, tests unitaires ecrits, migration index corrigee
- **Implementation PlexHubTV** :
  - `PlaylistEntity.kt` + `PlaylistItemEntity.kt` (core/database/) — Entities Room avec composite PKs + index sur `(playlistId, serverId)`
  - `PlaylistDao.kt` — CRUD complet + `@Transaction replacePlaylistItems`
  - `Playlist.kt` (core/model/) — Domain model avec items list
  - `PlaylistRepository.kt` (domain/) — 7 methodes (getPlaylists, refreshPlaylists, getPlaylistDetail, createPlaylist, addToPlaylist, removeFromPlaylist, deletePlaylist)
  - `PlaylistRepositoryImpl.kt` (data/) — `@Singleton`, uses playlistDao + serverClientResolver + mediaMapper
  - `PlaylistListScreen.kt` + `PlaylistListViewModel.kt` — Grid layout 4 colonnes, empty state, loading skeleton
  - `PlaylistDetailScreen.kt` + `PlaylistDetailViewModel.kt` — TopAppBar + LazyVerticalGrid + delete dialog
  - `AddToPlaylistDialog.kt` — Avec inline CreatePlaylistDialog
  - `PlexApiService.kt` — 6 endpoints playlist (GET, POST, PUT, DELETE)
  - `PlexClient.kt` — 6 wrapper methods
  - `MainScreen.kt` — Navigation routes enregistrees (lignes 326-340)
  - `NavigationItem.kt` — Entree sidebar Playlists avec PlaylistPlay icon
  - `MediaDetailScreen.kt` — Bouton "Add to Playlist" + AddToPlaylistDialog integration
  - `MediaDetailUiState.kt` — `showAddToPlaylist`, `availablePlaylists`, `isLoadingPlaylists` + 4 events
  - `strings.xml` — 17 strings playlist
  - DB migration v41→42 (avec index `playlist_items(playlistId, serverId)`)
  - Tests unitaires: 19 tests (PlaylistListViewModelTest + PlaylistDetailViewModelTest)
- **Rien a faire**

## F11 — Crash Reporting (ACRA) — ✅ DEJA IMPLEMENTE (Firebase Crashlytics)

- **Statut** : COMPLET — Solution production-grade deja en place, superieure a ACRA
- **Implementation PlexHubTV** :
  - `PlexHubApplication.kt` — `initializeFirebase()` avec custom keys (version, build type)
  - `FirebaseModule.kt` — Injection Hilt de `FirebaseCrashlytics`
  - `GlobalCoroutineExceptionHandler.kt` — Handler global : re-throw en DEBUG, `crashlytics.recordException()` en RELEASE
  - Firebase BOM `34.9.0` + Crashlytics `3.0.6` + Analytics + Performance Monitoring
  - Collection desactivee en DEBUG, activee en RELEASE
  - Tests unitaires couvrent le handler
- **Avantage vs ACRA** : Dashboard Firebase, integration Analytics + Performance, pas de setup serveur

## F12 — Quick Connect / Server Discovery

- **Source Wholphin** : `ui/preferences/QuickConnectDialog.kt`
- **Valeur ajoutee** : Setup plus facile pour les utilisateurs non-techniques
- **Complexite d'adaptation** : Moyenne
- **Dependances** : Plex GDM (multicast discovery) ou Plex API discovery
- **Specificite Jellyfin** : Quick Connect est Jellyfin-specific, mais Plex a GDM
- **Adaptabilite** : Concept generique. Implementation specifique Plex via GDM broadcast

## F13 — Gestion de media (suppression) — ✅ IMPLEMENTE

- **Statut** : COMPLET — Full stack du endpoint API jusqu'au UI
- **Implementation PlexHubTV** :
  - `PlexApiService.kt` — `@DELETE deleteMedia(@Url url: String): Response<Unit>`
  - `PlexClient.kt` — `deleteMedia(ratingKey)` construit URL `/library/metadata/$ratingKey`
  - `MediaDetailRepository.kt` — `deleteMedia(ratingKey, serverId): Result<Unit>` + `isServerOwned(serverId): Boolean`
  - `MediaDetailRepositoryImpl.kt` — Implementation avec gestion HTTP 401/403/404 + cleanup DB local
  - `DeleteMediaUseCase.kt` — `operator fun invoke()` + `isServerOwned()` helper
  - `MediaDetailUiState.kt` — `isServerOwned`, `showDeleteConfirmation`, `isDeleting` + events `DeleteClicked`, `ConfirmDelete`, `DismissDeleteDialog`
  - `MediaDetailViewModel.kt` — `checkServerOwnership()` dans init, handlers delete flow
  - `MediaDetailScreen.kt` — Bouton delete conditionnel (lignes 346-379) + AlertDialog confirmation (lignes 170-193)
  - `strings.xml` — 4 strings delete
- **Rien a faire**

## F14 — Favoris Personnes (Acteurs/Realisateurs) — ✅ IMPLEMENTE

- **Statut** : COMPLET
- **Implementation PlexHubTV** :
  - `PersonFavoriteEntity.kt` (core/database/) — `@Entity(tableName = "person_favorites")` avec `tmdbId` (PK), `name`, `profilePath`, `knownFor`, `addedAt`
  - `PersonFavoriteDao.kt` — `getAllFavorites(): Flow`, `isFavorite(tmdbId): Flow<Boolean>`, `insert()` (REPLACE), `delete()`
  - `PersonFavoriteRepository.kt` (domain/) — `isFavorite(tmdbId): Flow<Boolean>`, `toggleFavorite(person: Person)`
  - `PersonFavoriteRepositoryImpl.kt` (data/) — `@Singleton`, toggle logic
  - `PlexDatabase.kt` — Entity enregistree, DAO abstract function, version 42
  - `DatabaseModule.kt` — MIGRATION_40_41 cree table `person_favorites`, `providePersonFavoriteDao()`
  - `RepositoryModule.kt` — `@Binds @Singleton` binding
  - `PersonDetailViewModel.kt` — Injecte `PersonFavoriteRepository`, `observeFavoriteStatus()` flow, `toggleFavorite()`
  - `PersonDetailScreen.kt` — Bouton coeur (`Icons.Default.Favorite`/`FavoriteBorder`, rose #E91E63)
- ~~**Issue mineure**~~ : ✅ RESOLU — Labels extraits vers `strings.xml` (EN) + `strings.xml` (FR) pour i18n
- **Rien a faire**

## F15 — Install Direct des Mises a Jour — ✅ IMPLEMENTE

- **Statut** : COMPLET
- **Implementation PlexHubTV** :
  - `ApkInstaller.kt` (core/update/) — OkHttp download avec progress tracking (`MutableStateFlow<InstallState>`), `downloadAndInstall()`, FileProvider URI, `Intent.ACTION_VIEW` avec MIME "application/vnd.android.package-archive"
  - `UpdateDialog.kt` — Affiche progression download, bouton "Install" conditionnel pour assets APK (lignes 112-117), fallback browser
  - `AndroidManifest.xml` — FileProvider config (lignes 94-103), permission `REQUEST_INSTALL_PACKAGES` (ligne 16)
  - `file_provider_paths.xml` — `<cache-path name="apk_updates" path="updates/" />`
- **Rien a faire**

---

# ETAPE 5 — Backlog priorise (mis a jour Mars 2026)

## DEJA FAIT (12/15 features)

| # | Feature | Statut |
|---|---------|--------|
| ~~F01~~ | ~~**Home Backdrop Reactif**~~ | ✅ Complet (BackdropColors + AppBackdrop + focus-driven) |
| ~~F02~~ | ~~**Home Rows Configurables**~~ | ✅ Complet (visibilite toggles + Move Up/Down reordering + persistence DataStore) |
| ~~F03~~ | ~~**MediaSession**~~ | ✅ Complet (MediaSessionManager + PlayerController integration + media3.session) |
| ~~F04~~ | ~~**Telechargement sous-titres**~~ | ✅ Complet (SubtitleSearchService + DownloadSubtitlesDialog + OpenSubtitles API) |
| ~~F05~~ | ~~**Delai sous-titres**~~ | ✅ Complet (trigger UI dans Settings → Subtitles → "Subtitle Sync") |
| ~~F06~~ | ~~**Refresh Rate Adaptatif**~~ | ✅ Complet (RefreshRateManager + VideoPlayerScreen integration) |
| ~~F07~~ | ~~**Device Profile Adaptatif**~~ | ✅ Complet (DeviceProfileService + PlayerMediaLoader + TranscodeUrlBuilder + DebugScreen) |
| ~~F11~~ | ~~**Crash Reporting**~~ | ✅ Complet (Firebase Crashlytics + GlobalCoroutineExceptionHandler) |
| ~~F13~~ | ~~**Suppression media**~~ | ✅ Complet (full stack: API DELETE + UseCase + ownership check + confirmation dialog) |
| ~~F14~~ | ~~**Favoris Personnes**~~ | ✅ Complet (PersonFavoriteEntity + DAO + Repository + heart button + i18n strings) |
| ~~F15~~ | ~~**Install direct MAJ**~~ | ✅ Complet (ApkInstaller + FileProvider + REQUEST_INSTALL_PACKAGES) |
| ~~F10~~ | ~~**Playlists**~~ | ✅ Complet (6 endpoints + Room + UI + sidebar + 19 tests unitaires + migration index fix) |

## BACKLOG RESTANT — ✅ VIDE (tous les items completes)

| # | Action | Statut |
|---|--------|--------|
| ~~1~~ | ~~**F10 — Verifier compilation Playlists**~~ | ✅ FAIT — BUILD SUCCESSFUL, migration index corrigee |
| ~~2~~ | ~~**F14 — Extraire strings i18n**~~ | ✅ FAIT — Labels extraits vers strings.xml EN + FR |
| ~~3~~ | ~~**F10 — Tests unitaires Playlists**~~ | ✅ FAIT — 19 tests (PlaylistListViewModelTest + PlaylistDetailViewModelTest) |
| ~~4~~ | ~~**F02 — Home Rows Reordering**~~ | ✅ FAIT — Move Up/Down + HomeRowSettingsItem + persistence DataStore |

## FUTURE

| # | Feature | Impact | Effort | Prerequis |
|---|---------|--------|--------|-----------|
| 5 | **F08 — Discovery Overseerr/Jellyseerr** | 5/5 | 5/5 | Support Jellyfin |
| 6 | **F09 — EPG** | 4/5 | 5/5 | Parser XMLTV |
| 7 | **F12 — Server Discovery (GDM)** | 3/5 | 3/5 | Aucun |

---

# ETAPE 6 — Recommandations d'architecture multi-sources

## 1. Abstraction de la couche data

PlexHubTV a deja une bonne base avec `MediaSourceHandler` interface et `MediaSourceResolver`.

**Recommandation** : Etendre ce pattern :

```
domain/source/
├── MediaSourceHandler.kt          (interface existante)
├── MediaSourceCapabilities.kt     (NOUVEAU — declare ce que la source supporte)
└── MediaSourceRegistry.kt         (NOUVEAU — enregistre les sources disponibles)

data/source/
├── PlexSourceHandler.kt           (existant)
├── BackendSourceHandler.kt        (existant)
├── XtreamSourceHandler.kt         (existant)
├── JellyfinSourceHandler.kt       (NOUVEAU)
└── MediaSourceResolver.kt         (existant — a enrichir)
```

Chaque `MediaSourceHandler` declare ses capabilities :
- `canBrowseLibrary`, `canSearch`, `canStream`, `canManagePlayback`, `canLiveTV`, `canDiscover`
- Le `MediaSourceResolver` route les appels vers la bonne source

## 2. Pattern recommande : Strategy + Registry

```kotlin
interface MediaSourceHandler {
    val sourceType: SourceType  // PLEX, JELLYFIN, XTREAM, BACKEND
    val capabilities: Set<Capability>

    suspend fun getLibraries(): List<LibrarySection>
    suspend fun getItems(libraryId: String, filter: Filter): List<MediaItem>
    suspend fun getDetail(itemId: String): MediaItem
    suspend fun getStreamUrl(itemId: String): String
    // ... etc
}

@Singleton
class MediaSourceRegistry @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards MediaSourceHandler>
) {
    fun getHandler(type: SourceType): MediaSourceHandler
    fun getAllHandlers(): List<MediaSourceHandler>
    fun getHandlersWithCapability(cap: Capability): List<MediaSourceHandler>
}
```

Avantage : Ajouter Jellyfin = implementer `JellyfinSourceHandler` et l'enregistrer dans Hilt. Zero modification du code existant.

## 3. Mapping des modeles

PlexHubTV a deja le bon pattern avec des **Mappers dedies** :
- `MediaMapper.kt` (Plex DTO -> Domain)
- `BackendMediaMapper.kt` (Backend DTO -> Domain)
- `XtreamMediaMapper.kt` (Xtream DTO -> Domain)

**Pour Jellyfin** : Creer `JellyfinMediaMapper.kt` qui mappe `BaseItemDto` (Jellyfin SDK) vers `MediaItem` (domain).

Points d'attention :
- **IDs** : Plex utilise `ratingKey` (int), Jellyfin utilise `UUID`. Le `unificationId` existant (IMDB/TMDB/title+year) resout ce probleme pour la deduplication.
- **Images** : Plex = `/library/metadata/{id}/thumb`, Jellyfin = `/Items/{id}/Images/Primary`. Abstraire dans `ImageUrlResolver`.
- **Streams** : Plex = `/video/:/transcode`, Jellyfin = `/Videos/{id}/stream`. Abstraire dans URL builders existants.

## 4. Code Wholphin reutilisable vs reecriture

### Deja porte dans PlexHubTV (audit Mars 2026) — RIEN A FAIRE
- `RefreshRateService.kt` → `RefreshRateManager.kt` dans PlexHubTV
- `SubtitleDelay.kt` → `SyncSettingsDialog` + `FocusableDelayAdjuster` dans PlexHubTV
- `MediaSessionPlayer.kt` → `MediaSessionManager.kt` dans PlexHubTV
- `DeviceProfileService.kt` → `DeviceProfileService.kt` dans PlexHubTV (player/profile/)
- `SeekAcceleration.kt` → Integre dans `EnhancedSeekBar` de PlexHubTV
- `BackdropService.kt` → `AppBackdrop` + `BackdropColors` dans PlexHubTV
- Crash reporting → Firebase Crashlytics (superieur a ACRA)
- `UpdateChecker.kt` → `ApkInstaller.kt` + `UpdateDialog.kt` dans PlexHubTV
- `ScreensaverService.kt` → `DreamService` dans PlexHubTV
- `PeopleFavorites.kt` → `PersonFavoriteRepository` + `PersonFavoriteEntity` dans PlexHubTV
- `MediaManagementService.kt` → `DeleteMediaUseCase` + full stack dans PlexHubTV
- `PlaylistCreator.kt` → `PlaylistRepository` + full stack dans PlexHubTV
- `DownloadSubtitlesDialog.kt` → `SubtitleSearchService` + `DownloadSubtitlesDialog` dans PlexHubTV

### A adapter (concept ok, implementation a refaire)
- ~~`HomeSettingsService.kt`~~ — ✅ PORTE: Reordering des rows implemente (Move Up/Down + persistence DataStore)
- `SeerrService.kt` — A adapter pour Overseerr (Plex) en plus de Jellyseerr

### Reecriture complete necessaire
- `ServerRepository.kt` — Entierement couple a Jellyfin SDK
- `ItemPlaybackRepository.kt` — Utilise les APIs Jellyfin pour le scrobbling
- Tous les ViewModels — Utilisent directement le Jellyfin SDK
- `api/seerr/` — Client genere specifique a Jellyseerr

## 5. Risques techniques integration Jellyfin

| Risque | Impact | Mitigation |
|--------|--------|------------|
| **Collision d'IDs** | Les ratingKey Plex et UUID Jellyfin peuvent collisionner dans Room | Utiliser un prefixe source (ex: `plex:12345`, `jf:uuid`) ou table separee |
| **Modeles incompatibles** | Les DTOs Plex et Jellyfin ont des champs tres differents | Le domaine `MediaItem` existant est deja assez generique — ajouter les champs manquants |
| **Auth divergente** | Plex (token OAuth) vs Jellyfin (username/password + API key) | Abstraire dans `AuthSourceHandler` avec strategies |
| **Image URLs** | Chaque source a un schema d'URL different | `ImageUrlResolver` par source handler |
| **Transcoding profiles** | Plex et Jellyfin gerent le transcodage differemment | `DeviceProfileService` abstrait avec output adapte par source |
| **Sync conflict** | Deux serveurs peuvent avoir le meme film — lequel enrichir ? | `unificationId` existant resout ca via deduplication |
| **Taille de la DB** | Ajouter Jellyfin = potentiellement doubler les entities | L'aggregation multi-serveur existante gere deja ce cas |
| **Test matrix** | Tester Plex + Jellyfin + Xtream = combinatoire explosive | Tests par source handler en isolation + tests d'integration cibles |

---

# Resume executif (mis a jour Mars 2026)

## Constat principal

**PlexHubTV a rattrape et depasse Wholphin** sur la quasi-totalite des features identifiees dans l'analyse comparative. Sur 15 features cataloguees, **12 sont entierement implementees** et 3 restent dans le futur (Overseerr, EPG, Server Discovery). Le backlog de correctifs est entierement vide.

## Audit de verification — Mars 2026

| Feature | Statut plan original | Statut reel apres audit |
|---------|---------------------|------------------------|
| F01 — Home Backdrop Reactif | A implementer | ✅ COMPLET |
| F03 — MediaSession | A implementer (MUST-HAVE) | ✅ COMPLET (MediaSessionManager) |
| F04 — Telechargement sous-titres | A implementer (NICE-TO-HAVE) | ✅ COMPLET (SubtitleSearchService + OpenSubtitles) |
| F05 — Delai sous-titres | UI manquante | ✅ COMPLET (trigger dans Settings → Subtitles) |
| F06 — Refresh Rate Adaptatif | A implementer (MUST-HAVE) | ✅ COMPLET (RefreshRateManager) |
| F07 — Device Profile Adaptatif | A implementer (NICE-TO-HAVE) | ✅ COMPLET (DeviceProfileService + integration Player + Debug) |
| F10 — Playlists | A implementer (NICE-TO-HAVE) | ✅ COMPLET (full stack + 19 tests + migration fix) |
| F11 — Crash Reporting | A implementer | ✅ COMPLET (Firebase Crashlytics) |
| F13 — Suppression media | A implementer (NICE-TO-HAVE) | ✅ COMPLET (full stack API → UI) |
| F14 — Favoris Personnes | A implementer (NICE-TO-HAVE) | ✅ COMPLET (Room + toggle + heart button) |
| F15 — Install direct MAJ | A implementer (MUST-HAVE) | ✅ COMPLET (ApkInstaller + FileProvider) |
| F02 — Home Rows Configurables | A implementer (NICE-TO-HAVE) | ✅ COMPLET (visibilite + reordering Move Up/Down + persistence) |

**Impact** : Toutes les 12 features implementables sont maintenant COMPLETES. Le backlog est vide.

## Backlog effectif restant — ✅ VIDE

Tous les items ont ete completes :
1. ~~**Verifier compilation F10 Playlists**~~ — ✅ BUILD SUCCESSFUL + migration index fix
2. ~~**Extraire strings i18n F14**~~ — ✅ Labels extraits EN + FR
3. ~~**Tests unitaires F10 Playlists**~~ — ✅ 19 tests ecrits
4. ~~**F02 Home Rows reordering**~~ — ✅ Move Up/Down + persistence DataStore

## Ecart restant avec Wholphin

| Dimension | Ecart |
|-----------|-------|
| Features player | ✅ Rattrape — PlexHubTV a maintenant MediaSession, refresh rate, subtitle download, equalizer, device profile |
| Home page | ✅ Rattrape — Visibilite configurable + reordering Move Up/Down avec persistence |
| Discovery | ❌ Toujours absent — Pas d'Overseerr/Jellyseerr |
| Live TV | ❌ Toujours basique — Pas d'EPG |
| Multi-source | ✅ PlexHubTV superieur — Plex + Backend + Xtream vs Jellyfin only |
| Architecture | ✅ PlexHubTV superieur — Clean Arch + 25+ Use Cases vs Services monolithiques |
| Tests | ✅ PlexHubTV superieur — 36 fichiers, ~315 methodes |

## Strategie

Le backlog "Wholphin parity" est **100% termine**. Tous les correctifs et features ont ete implementes. Le focus devrait aller vers :
- **Nouveaux horizons** : Overseerr/Jellyseerr integration (F08), EPG (F09), Server Discovery (F12), support Jellyfin (ETAPE 6)
- **Stabilisation continue** : Corriger les 4 tests pre-existants en echec (3 MediaDetailViewModelTest + 1 SearchViewModelTest)

---

# ETAPE 7 — Comparaison detaillee des Players (UI + Fonctionnalites)

## 1. Architecture Player

| Dimension | PlexHubTV | Wholphin |
|-----------|-----------|----------|
| **Fichiers player** | 26 fichiers | 28 fichiers |
| **Backend video** | ExoPlayer + MPV | ExoPlayer + MPV + FFmpeg optionnel |
| **Architecture** | Controller decompose (13 composants: PlayerController, Initializer, MediaLoader, Scrobbler, StatsTracker, TrackController, ChapterMarkerManager, TrickplayManager, MediaSessionManager, RefreshRateManager, AudioEqualizerManager, SubtitleSearchService, DeviceProfileService) | ViewModel monolithique (PlaybackViewModel ~1100 lignes) + services |
| **State management** | `PlayerUiState` data class + `StateFlow` | `LiveData` + `StateFlow` mixte |
| **Action routing** | 3 ViewModels (Control, Track, Stats) via sealed `PlayerAction` | 1 ViewModel unique |
| **URL builders** | 4 builders (DirectStream, Transcode, Backend, Xtream) | SDK Jellyfin natif |

**Verdict**: PlexHubTV a une architecture plus modulaire et testable (10 composants vs 1 ViewModel monolithique). Wholphin concentre tout dans le ViewModel.

---

## 2. Layout et Hierarchie UI

### PlexHubTV — Layout
```
VideoPlayerRoute
├── PlayerSurface (ExoPlayer PlayerView OU MPV FrameLayout)
├── AnimatedVisibility → NetflixPlayerControls
│   ├── Top Bar (Back + Titre + Server info + gradient)
│   ├── Center (Play/Pause geant 80x80dp)
│   ├── Bottom (EnhancedSeekBar + Transport buttons)
│   └── SkipMarkerButton (intro/credits, bottom-right)
├── CircularProgressIndicator (buffering, center)
├── ResumeToast (bottom-left)
├── PerformanceOverlay (top-right)
├── PlayerErrorOverlay (full-screen modal)
├── AutoNextPopup (top-right, card avec thumbnail)
├── PlayerSettingsDialog (quality + stats + sync)
├── AudioSelectionDialog
├── SubtitleSelectionDialog
├── SpeedSelectionDialog
├── SyncSettingsDialog (subtitle delay)
└── SyncSettingsDialog (audio delay)
```

### Wholphin — Layout
```
PlaybackPage
├── PlayerSurface (Media3)
├── LoadingPage (buffering overlay)
├── SkipIndicator (animated rotation arrow)
├── Skip progress bar
├── AnimatedVisibility → PlaybackOverlay
│   ├── Scrim (gradient vertical → noir 0.80 alpha)
│   ├── Logo/Clock (coins superieurs)
│   ├── SeekBar + SeekPreviewImage
│   ├── PlaybackControls (3 sections: left/center/right)
│   ├── Chapters view (etat alternatif, slide)
│   └── Queue view (etat alternatif, slide partiel)
├── SubtitleView (AndroidView)
├── Segment Skip Button (intro/outro)
├── NextUpEpisode Card (animated)
├── DownloadSubtitlesDialog (modal)
└── PlaybackDialog (settings complet)
```

**Differences cles** :
- PlexHubTV : Top bar avec titre + serveur, centre avec gros bouton play
- Wholphin : Pas de top bar titre, controles concentres en bas, overlay 3 etats (controls/chapters/queue)

---

## 3. Barre de Transport (Controles)

### PlexHubTV — NetflixPlayerControls
```
[◀ Chapitre] [⏪ -10s] [⏵/⏸ Play] [⏩ +30s] [▶ Chapitre]  ...  [▶▶ Next] [⏹ Stop] [📝 Subs] [🔊 Audio] [⚙ Settings]
```
- **Taille icones**: Non specifiee (standard)
- **Focus**: D-Pad, `interactionSource`, scale effect
- **Couleurs**: Blanc, chapitres en orange (#E5A00D)
- **Gap**: 24dp entre groupes
- **Tests**: Tags semantiques sur chaque bouton
- **Particularite**: Boutons chapitres conditionnels (seulement si chapitres existent)

### Wholphin — PlaybackControls
```
[⋯ More]  ...  [⏮] [⏪] [⏵/⏸ Play] [⏩] [⏭]  ...  [CC Subs] [⚙ Settings]
```
- **Taille icones**: 36x36dp (fixe)
- **Focus**: `focusGroup()`, `BringIntoViewRequester`
- **Couleurs**: `TransparentBlack25` fond, `border` couleur focused
- **Bouton Play**: Focus initial automatique
- **Particularite**: Bouton "More Options" (menu hamburger), pas de bouton Stop/Close

**Differences cles**:
| Element | PlexHubTV | Wholphin |
|---------|-----------|----------|
| Stop button | Oui (explicite) | Non (back = sortie) |
| Chapitres prev/next | Oui (boutons dedies) | Non (vue chapitres separee) |
| Audio button | Oui (dans la barre) | Non visible (dans More/Settings) |
| More/Menu | Non | Oui |
| Taille boutons | Variable | 36dp fixe |
| Top bar titre | Oui (titre + serveur) | Non |

---

## 4. Seek Bar

### PlexHubTV — EnhancedSeekBar
- **Hauteur**: 4dp normal, 6dp focused
- **Couleur progress**: Netflix Red
- **Couleur fond**: Gray 0.5 alpha
- **Coins**: Rounded 2dp
- **Chapters**: Separateurs blancs verticaux (0.6 alpha)
- **Markers**: Intro = vert, Credits = rouge (rectangles colores sur la barre)
- **Trickplay**: Thumbnail 160dp wide, priorite: chapter image → BIF bitmap → chapter title fallback
- **Temps**: Current time (gauche) + Chapter title (centre) + Duration (droite)
- **Interaction D-Pad**: LEFT = -10s, RIGHT = +10s, CENTER = confirmer
- **Touch**: `detectHorizontalDragGestures()` pour scrub continu
- **Acceleration**: Non implementee

### Wholphin — SeekBar
- **Hauteur**: Expand on focus (animation)
- **Couleur progress**: Primary theme color
- **Buffered**: Ligne separee visible
- **Handle**: Cercle blanc a la position
- **Trickplay**: Sprite sheet crop via `CoilTrickplayTransformation`, hauteur 160dp, bordure 1.5dp
- **Interaction D-Pad**: LEFT/RIGHT avec hold detection
- **Acceleration**: Oui — progressive selon duree du contenu:
  - < 30 min: 1-2x
  - 30-90 min: 1-4x
  - 90-150 min: 1-6x
  - > 150 min: 1-10x
- **Debounce**: 750ms avant execution du seek
- **Deux modes**: SteppedSeekBar (positions fixes) + IntervalSeekBar (duree fixe)

**Differences cles**:
| Element | PlexHubTV | Wholphin |
|---------|-----------|----------|
| Markers intro/credits sur barre | Oui (vert/rouge) | Non |
| Separateurs chapitres | Oui (blancs) | Non |
| Seek acceleration | Non | Oui (progressive, jusqu'a 10x) |
| Handle/thumb visuel | Non (pas de cercle) | Oui (cercle blanc) |
| Buffer indicator | Non visible | Oui (ligne separee) |
| Debounce | Non | Oui (750ms) |
| Modes de seek | 1 (intervalle) | 2 (stepped + interval) |

---

## 5. Skip Intro/Credits

### PlexHubTV — SkipMarkerButton
- **Position**: Bottom-right, 200dp du bas, 32dp du bord droit
- **Animation**: slideIn/slideOut horizontal + fadeIn/fadeOut
- **Couleurs**: Intro = vert (#4CAF50), Credits = orange (#FF9800), Default = bleu (#2196F3)
- **Focus**: Scale 1.1x + full opacity
- **Icone**: SkipNext + texte bold 12sp
- **Visibilite**: Seulement pendant le segment, seulement si mode = "ask"
- **Mode auto**: Skip automatique si mode = "auto" (gere dans ViewModel)
- **Mode off**: Jamais affiche

### Wholphin — Segment Skip Button + SkipIndicator
- **Skip Button**: Bouton dans l'overlay, position variable
- **SkipIndicator**: Animation rotation circulaire 800ms
  - Icone: Fleche circulaire (drawable)
  - Texte: Duree absolue du skip en secondes
  - Taille: 55dp box
  - Direction: Flip horizontal pour backward (scaleX = -1f)
- **Segments**: Intro, Outro, Commercial, Preview (4 types vs 2 pour PlexHubTV)
- **Auto-skip**: Configurable via preferences

**PlexHubTV superieur**: Markers visuels sur la seek bar (vert/rouge) montrent OU sont les intros/credits. Wholphin n'a pas ca.
**Wholphin superieur**: 4 types de segments vs 2, animation de skip plus elaboree.

---

## 6. Auto-Play Episode Suivant

### PlexHubTV — AutoNextPopup
- **Position**: Top-right (80dp top, 32dp right)
- **Taille**: 300dp width
- **Design**: Card noire 0.85 alpha, bordure primary 0.5 alpha, rounded 12dp
- **Contenu**: Thumbnail (80x45dp) + Label "Next Episode" + Titre + Boutons
- **Countdown**: 15 secondes, progress bar 3dp en bas
- **Boutons**: "Play Now (Xs)" + "Cancel" (outlined)
- **Animation**: slideIn vertical (du haut) + fadeIn
- **Focus**: Auto-focus sur Play Now

### Wholphin — NextUpEpisode
- **Design**: Card avec AsyncImage background fill
- **Overlay**: Cercle semi-transparent centre
  - Countdown timer texte (quand timer > 0)
  - PlayArrow 60dp (quand pret)
- **Aspect ratio**: Configurable (defaut WIDE)
- **Interaction**: onClick, auto-focus
- **Scale**: 0.6f quand visible (reduit le player)

**Differences cles**:
| Element | PlexHubTV | Wholphin |
|---------|-----------|----------|
| Position | Top-right (overlay) | Bottom-right (overlay) |
| Countdown | 15s avec progress bar | Configurable, cercle central |
| Thumbnail | Petit (80x45dp) + texte | Grande image background fill |
| Bouton Cancel | Oui | Oui (close icon) |
| Impact player | Aucun (overlay) | Reduit le player (scale 0.6f) |

---

## 7. Dialogs et Menus

### PlexHubTV — Dialogs separees
| Dialog | Style | Taille |
|--------|-------|--------|
| Settings (Qualite) | Surface dark 0xFF1A1A1A, rounded 16dp | 35% width, max 500dp |
| Audio Selection | SelectionDialog generique | 35% width |
| Subtitle Selection | SelectionDialog generique, option OFF | 35% width |
| Speed Selection | SelectionDialog, 6 vitesses | 35% width |
| Audio Sync | FocusableDelayAdjuster, ±50ms | 35% width, max 400dp |
| Subtitle Sync | FocusableDelayAdjuster, ±50ms | 35% width, max 400dp |

- **Focus**: SettingItem avec fond blanc quand focused, checkmark quand selected
- **Navigation**: D-Pad up/down pour naviguer, ENTER pour selectionner
- **Fermeture**: Back button ou bouton Close

### Wholphin — PlaybackDialog + Dialogs
| Dialog | Contenu |
|--------|---------|
| PlaybackDialog (Settings) | Quality (bitrates), Subtitles (tracks + delay), Audio (tracks + effects), Advanced (speed, skip) |
| DownloadSubtitles | Search bar + LazyColumn resultats (provider, langue, rating) |
| Track Selection | Liste avec codec, channels (2.0/5.1/7.1), bitrate |
| SubtitleDelay | 7 boutons: -1s, -250ms, -50ms, Reset, +50ms, +250ms, +1s |

**Differences cles**:
| Element | PlexHubTV | Wholphin |
|---------|-----------|----------|
| Settings structure | 1 dialog qualite + items menu | 1 dialog multi-onglets |
| Subtitle delay pas | ±50ms (D-Pad gauche/droite) | 7 boutons (±50ms, ±250ms, ±1s) |
| Download subtitles | Oui (SubtitleSearchService + DownloadSubtitlesDialog) | Oui (recherche + resultats) |
| Audio equalizer | Oui (AudioEqualizerManager, 10 bandes + presets) | Oui (presets, 10 bandes) |
| Speed options | 8 (0.25-2.0x) | 8 (0.25-2.0x) |
| Audio info detail | Titre + langue | Codec + channels + bitrate |

---

## 8. Gestion d'erreurs

### PlexHubTV — PlayerErrorOverlay
- **Full-screen**: Overlay noir 0.8 alpha
- **Surface**: 500dp, surfaceVariant 0.95 alpha, rounded 16dp
- **Icone**: Warning 64dp rouge
- **Titre contextuel**: "Network Error" / "Codec Error" / "Playback Error"
- **Message contextuel**: Texte adapte au type d'erreur
- **Compteur retry**: "Attempt X/Y" (si erreur reseau)
- **3 boutons**:
  1. Retry (auto-focus, scale 1.05x sur focus)
  2. Switch to MPV (si erreur reseau + 3 retries + pas deja MPV)
  3. Close
- **Tests**: Tags semantiques sur chaque bouton

### Wholphin
- **Loading state**: Enum Success/Error/Loading dans ViewModel
- **Pas d'overlay dedie** pour les erreurs — gere via etats du ViewModel
- **Transcode fallback**: Automatique si direct play echoue

**PlexHubTV nettement superieur**: Overlay d'erreur dedie avec diagnostic (type d'erreur), compteur de retry, et suggestion de switch MPV. Wholphin n'a pas d'UI d'erreur equivalente.

---

## 9. Debug / Performance Stats

### PlexHubTV — PerformanceOverlay
- **Position**: Top-right (40dp top, 16dp right)
- **Background**: Noir 0.7 alpha, rounded 8dp
- **Titre**: "Nerd Stats" (primary color, bold)
- **7 metriques**: Bitrate, Resolution, Video Codec, Audio Codec, Dropped Frames, FPS, Cache Duration
- **Font**: Monospace, 12sp, labels gris / valeurs vertes
- **Activation**: Via bouton dans Settings dialog

### Wholphin — PlaybackDebugOverlay
- **Position**: Top-left
- **Background**: Semi-transparent dark
- **Metriques**: Video/Audio Codec, Resolution, Frame Rate, Bitrate (current/peak/avg), Decoder (HW/SW), Buffer, Dropped Frames, Network info, Player Backend
- **Font**: Monospace
- **Activation**: Debug builds seulement
- **Refresh**: Toutes les secondes

**Differences cles**:
| Element | PlexHubTV | Wholphin |
|---------|-----------|----------|
| Accessible en production | Oui (toggle dans settings) | Non (debug builds only) |
| Metriques | 7 | ~10 |
| Position | Top-right | Top-left |
| Info supplementaire | Cache duration | Network bandwidth, decoder type, peak bitrate |

---

## 10. Fonctionnalites Exclusives

### PlexHubTV uniquement
1. **Top bar avec titre + serveur** — L'utilisateur voit toujours quel contenu joue et sur quel serveur
2. **Bouton Stop dedie** — Sortie explicite sans ambiguite
3. **Boutons chapitres prev/next** dans la barre de transport
4. **Markers visuels intro/credits** (vert/rouge) sur la seek bar
5. **Separateurs chapitres** (blancs) sur la seek bar
6. **Resume Toast** — Notification ephemere "Resuming from X:XX"
7. **Overlay erreur dedie** avec diagnostic + suggestion MPV switch
8. **3 ViewModels** pour separation des responsabilites (Control, Track, Stats)
9. **Multi-source URL builders** (Plex, Backend, Xtream, Direct)
10. **Nerd Stats en production** (PerformanceOverlay: codec, resolution, FPS, decoder, dropped frames, bitrate, cache)
11. **Home rows reordonnables** — Move Up/Down + persistence DataStore
12. **Playlists CRUD complet** — 6 endpoints Plex API + Room + sidebar + AddToPlaylistDialog
13. **Favoris personnes** — Toggle coeur sur PersonDetailScreen, persistence Room, i18n EN+FR
14. **Suppression media** — Full stack API DELETE + UseCase + ownership check + confirmation dialog
15. **Audio Equalizer** — 10 bandes avec presets (AudioEqualizerManager + AudioEqualizerDialog)
16. **6 themes** — Plex, MonoDark, MonoLight, Morocco, OLEDBlack, Netflix

### Wholphin uniquement (ce qui reste exclusif apres audit Mars 2026)

**Features deja portees dans PlexHubTV :**
1. ~~**Seek acceleration progressive**~~ — ✅ Maintenant dans PlexHubTV (EnhancedSeekBar)
2. ~~**Download de sous-titres**~~ — ✅ Maintenant dans PlexHubTV (SubtitleSearchService + DownloadSubtitlesDialog)
3. ~~**Audio equalizer**~~ — ✅ Maintenant dans PlexHubTV (AudioEqualizerManager + AudioEqualizerDialog)
4. ~~**8 vitesses**~~ — ✅ Maintenant dans PlexHubTV (0.25x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x)
5. ~~**Subtitle delay granulaire**~~ — ✅ Maintenant dans PlexHubTV (7 boutons dans SyncSettingsDialog)
6. ~~**Device Profile adaptatif**~~ — ✅ Maintenant dans PlexHubTV (DeviceProfileService)
7. ~~**Buffer indicator visible**~~ — ✅ Maintenant dans PlexHubTV (EnhancedSeekBar)
8. ~~**Seek debounce**~~ — ✅ Maintenant dans PlexHubTV
9. ~~**SkipIndicator anime**~~ — ✅ Equivalent dans PlexHubTV (SkipMarkerButton: slideIn/fadeIn + scale 1.1x focus)

**Features encore exclusives a Wholphin :**
1. **Vue Chapitres overlay** — Etat alternatif de l'overlay avec slide animation pour naviguer les chapitres visuellement
2. **Vue Queue/Playlist dans player** — Etat alternatif de l'overlay pour gerer la file d'attente pendant la lecture
3. **4 types de segments** (Intro, Outro, Commercial, Preview) vs 2 (Intro, Credits) pour PlexHubTV
4. **Scale player** — Reduit a 0.6x quand NextUp visible (PlexHubTV : overlay sans reduction)
5. **Bouton More/Menu** — Menu hamburger dans les controles (PlexHubTV : bouton Settings gear a la place)

---

## 11. Synthese et Recommandations

### Score par categorie (mis a jour Mars 2026)

| Categorie | PlexHubTV | Wholphin | Commentaire |
|-----------|-----------|----------|-------------|
| **Architecture player** | 9/10 | 6/10 | PlexHubTV: 13 composants modulaires vs 1 ViewModel monolithique |
| **Transport controls** | 8/10 | 7/10 | PlexHubTV: chapitres + stop + audio; Wholphin: menu more |
| **Seek bar** | 9/10 | 9/10 | PlexHubTV a rattrape: acceleration + debounce + buffer indicator + markers chapitres/intro/credits |
| **Skip intro/credits** | 8/10 | 7/10 | PlexHubTV: markers visuels sur barre + animation; Wholphin: 4 types segments |
| **Auto-next** | 8/10 | 7/10 | Equivalent, styles differents |
| **Dialogs/Settings** | 9/10 | 9/10 | PlexHubTV a rattrape: download subs + equalizer + multi-onglets + 8 vitesses |
| **Gestion erreurs** | 9/10 | 4/10 | PlexHubTV: overlay dedie, diagnostic, MPV switch |
| **Debug/Stats** | 8/10 | 6/10 | PlexHubTV: PerformanceOverlay accessible en prod (codec, FPS, bitrate, cache) |
| **Sous-titres** | 9/10 | 9/10 | PlexHubTV a rattrape: download OpenSubtitles + delay granulaire (7 boutons) |
| **Theming** | 7/10 | 8/10 | PlexHubTV: 6 themes (Plex, MonoDark, MonoLight, Morocco, OLEDBlack, Netflix); Wholphin: palettes custom |
| **TOTAL** | **84/100** | **72/100** | **PlexHubTV superieur** (+12 points) |

### Top 5 ameliorations a porter depuis Wholphin (mis a jour Mars 2026)

| # | Feature | Impact | Effort | Statut |
|---|---------|--------|--------|--------|
| ~~1~~ | ~~**Seek acceleration**~~ | Haut | Faible | ✅ PORTE (EnhancedSeekBar) |
| ~~2~~ | ~~**Download sous-titres**~~ | Haut | Moyen | ✅ PORTE (SubtitleSearchService + DownloadSubtitlesDialog) |
| ~~3~~ | ~~**Buffer indicator sur seek bar**~~ | Moyen | Faible | ✅ PORTE (EnhancedSeekBar) |
| 4 | **Overlay chapitres/queue** | Moyen | Moyen | ❌ Restant — Vue alternative overlay pour chapitres + file d'attente |
| ~~5~~ | ~~**Subtitle delay granulaire**~~ | Moyen | Faible | ✅ PORTE (7 boutons dans SyncSettingsDialog) |

**Seule amelioration restante**: Overlay chapitres/queue (item 4). Toutes les autres ont ete portees.

### Top 5 avantages PlexHubTV a conserver

1. **Error overlay avec diagnostic** — Unique et tres utile pour les TV (pas de logcat)
2. **Markers visuels intro/credits sur la seek bar** — Feedback visuel superieur
3. **Top bar titre + serveur** — Contexte toujours visible
4. **Nerd Stats en production** — Debug accessible a tous
5. **Architecture controller decomposee** — Testabilite et maintenabilite

---
---

# ETAPE 8 — Implementation NICE-TO-HAVE Features — ✅ TERMINE

## Contexte

Les features MUST-HAVE (ETAPE 7: seek acceleration, buffer indicator, subtitle delay, stats overlay, multi-tab settings, subtitle download, audio equalizer) sont **TERMINÉES**. Les NICE-TO-HAVE sont egalement **TERMINÉES** (audit Mars 2026).

## Statut final

1. **F13 — Suppression media** — ✅ COMPLET
2. **F14 — Favoris Personnes** — ✅ COMPLET (+ i18n strings EN/FR)
3. **F07 — Device Profile Adaptatif** — ✅ COMPLET
4. **F02 — Home Rows Configurables** — ✅ COMPLET (visibilite + reordering Move Up/Down + persistence DataStore)
5. **F10 — Playlists** — ✅ COMPLET (compilation verifiee + 19 tests unitaires + migration index fix)

---

## F13 — Suppression Media (Impact 2/5, Effort 1/5) — ✅ IMPLEMENTÉ

**Objectif**: Supprimer du contenu du serveur Plex depuis l'app, avec confirmation. Uniquement pour les propriétaires du serveur.

### Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `core/network/.../PlexApiService.kt` | Ajouter `@DELETE deleteMedia()` |
| `domain/.../repository/MediaDetailRepository.kt` | Ajouter `deleteMedia(ratingKey, serverId)` |
| `data/.../repository/MediaDetailRepositoryImpl.kt` | Implémenter: API DELETE + local DB delete |
| `app/.../feature/details/MediaDetailUiState.kt` | Ajouter `DeleteClicked`, `ConfirmDelete`, `DismissDeleteDialog` events + `showDeleteConfirmation`, `isDeleting`, `isServerOwner` |
| `app/.../feature/details/MediaDetailViewModel.kt` | Injecter `DeleteMediaUseCase`, handler events, vérifier `isOwned` |
| `app/.../feature/details/MediaDetailScreen.kt` | Ajouter bouton Delete (rouge, trash icon) dans `ActionButtonsRow` + dialog confirmation |
| `app/src/main/res/values/strings.xml` | Strings EN |
| `app/src/main/res/values-fr/strings.xml` | Strings FR |

### Fichiers à créer

| Fichier | Contenu |
|---------|---------|
| `domain/.../usecase/DeleteMediaUseCase.kt` | Appel repo + gestion résultat |
| `app/.../feature/details/components/DeleteConfirmDialog.kt` | AlertDialog avec titre, message "%s sera supprimé", Confirm/Cancel |

### Points clés
- **Endpoint Plex**: `DELETE {baseUrl}/library/metadata/{ratingKey}` avec `X-Plex-Token`
- **Visibilité bouton**: Seulement si `Server.isOwned == true` (vérifiable via `serverEntity` dans le repo)
- **Post-suppression**: Supprimer de Room via `mediaDao.deleteMedia(ratingKey, serverId)` puis naviguer back
- **Pas de DB migration** (utilise tables existantes)

---

## F14 — Favoris Personnes (Impact 3/5, Effort 2/5) — ✅ IMPLEMENTÉ

**Objectif**: Marquer des acteurs/réalisateurs comme favoris depuis `PersonDetailScreen`. Stockage local Room (pas d'API Plex pour ça).

### Fichiers à créer

| Fichier | Contenu |
|---------|---------|
| `core/database/.../PersonFavoriteEntity.kt` | Entity: `tmdbId` (PK), `name`, `profilePath`, `knownFor`, `addedAt` |
| `core/database/.../PersonFavoriteDao.kt` | `getAllFavorites()`, `isFavorite(tmdbId)`, `insert()`, `delete()` |
| `core/model/.../PersonFavorite.kt` | Domain model: `tmdbId`, `name`, `photoUrl`, `knownFor`, `addedAt` |
| `domain/.../repository/PersonFavoriteRepository.kt` | Interface: `getFavorites()`, `isFavorite()`, `toggleFavorite()` |
| `data/.../repository/PersonFavoriteRepositoryImpl.kt` | Implémentation avec mapping entity↔domain |

### Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `core/database/.../PlexDatabase.kt` | Ajouter entity + DAO, version 40→41 |
| `core/database/.../DatabaseModule.kt` | Migration 40→41 (CREATE TABLE person_favorites), `@Provides` DAO |
| `data/.../di/RepositoryModule.kt` | `@Binds` PersonFavoriteRepository |
| `app/.../feature/details/PersonDetailViewModel.kt` | Injecter `PersonFavoriteRepository`, exposer `isFavorite: StateFlow`, ajouter `toggleFavorite()` |
| `app/.../feature/details/PersonDetailScreen.kt` | Ajouter bouton coeur/étoile dans `PersonFixedHeader` (après metadata) |
| `app/.../feature/favorites/FavoritesViewModel.kt` | (Optionnel) Injecter repo, exposer section "Favorite People" |
| `app/.../feature/favorites/FavoritesScreen.kt` | (Optionnel) Section avatars circulaires dans LazyRow |
| Strings EN + FR | Labels favoris personne |

### Schema DB

```sql
CREATE TABLE person_favorites (
    tmdbId INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    profilePath TEXT,
    knownFor TEXT,
    addedAt INTEGER NOT NULL
);
```

---

## F07 — Device Profile Adaptatif (Impact 3/5, Effort 3/5) — ✅ IMPLEMENTÉ

**Objectif**: Détecter les codecs supportés par l'appareil et utiliser cette info pour décider intelligemment entre Direct Play et Transcode.

### État actuel
- `PlayerMediaLoader.kt` a déjà `hasHardwareHEVCDecoder()` et `hasHardwareAudioDecoder()` utilisant `MediaCodecList`
- Direct play decision simpliste: `bitrate >= 200000 && part?.key != null`
- `TranscodeUrlBuilder.kt` envoie des params hardcodés sans info de capability
- `AuthInterceptor.kt` envoie des headers X-Plex-* basiques (pas de capabilities)

### Fichiers à créer

| Fichier | Contenu |
|---------|---------|
| `app/.../feature/player/profile/DeviceProfileService.kt` | `@Singleton`, scan `MediaCodecList` une fois, expose `DeviceProfile` avec sets de codecs video/audio supportés, max resolution, HDR, bit depth |

### Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `app/.../feature/player/controller/PlayerMediaLoader.kt` | Injecter `DeviceProfileService`, remplacer `hasHardwareHEVCDecoder()` + `isProblematicAudioCodec()` par `deviceProfileService.canDirectPlay(videoCodec, audioCodec, container)` |
| `app/.../feature/player/url/TranscodeUrlBuilder.kt` | Injecter `DeviceProfileService`, ajouter params de capability (`videoProfiles`, `audioProfiles`, `videoResolution`) dans l'URL de transcode |
| `core/datastore/.../SettingsDataStore.kt` | Ajouter pref `device_profile_auto_detect` (Boolean, default true) |
| `app/.../feature/settings/categories/PlaybackSettingsScreen.kt` | Ajouter toggle "Auto-detect codecs" |
| `app/.../feature/debug/DebugScreen.kt` | Afficher profil détecté (codecs, HDR, resolution) |
| Strings EN + FR | Labels settings + debug |

### DeviceProfile data class

```kotlin
data class DeviceProfile(
    val videoCodecs: Set<String>,    // "h264", "hevc", "vp9", "av1"
    val audioCodecs: Set<String>,    // "aac", "ac3", "eac3", "dts", "opus", "flac"
    val containers: Set<String>,     // "mkv", "mp4", "avi", "mov"
    val maxWidth: Int,               // ex: 3840
    val maxHeight: Int,              // ex: 2160
    val supportsHDR: Boolean,
    val maxBitDepth: Int,            // 8, 10, 12
)
```

### Logique canDirectPlay

```kotlin
fun canDirectPlay(videoCodec: String?, audioCodec: String?, container: String?): Boolean {
    val videoOk = videoCodec == null || normalizeCodec(videoCodec) in profile.videoCodecs
    val audioOk = audioCodec == null || normalizeCodec(audioCodec) in profile.audioCodecs
    val containerOk = container == null || container.lowercase() in profile.containers
    return videoOk && audioOk && containerOk
}
```

---

## F02 — Home Rows Configurables (Impact 4/5, Effort 3/5) — 🔶 PARTIEL (visibilite done, reordering pending)

**Objectif**: Permettre à l'utilisateur de réordonner et masquer les rangées de la home page via les Settings.

### État actuel
- `NetflixHomeScreen.kt`: 4 sections hardcodées (Continue Watching, My List, Suggestions, Hub Rows)
- `HubsRepositoryImpl.kt`: `hubDisplayOrderComparator` hardcodé pour l'ordre des hubs
- `HomeUiState`: listes séparées (`onDeck`, `favorites`, `suggestions`, `hubs`)
- `HomeContentDao.getHubsList()` retourne les hub identifiers disponibles

### Fichiers à créer

| Fichier | Contenu |
|---------|---------|
| `core/model/.../HomeRowConfig.kt` | `data class HomeRowConfig(identifier: String, enabled: Boolean, position: Int)` |
| `domain/.../repository/HomeRowConfigRepository.kt` | Interface: `getConfig()`, `saveConfig()` |
| `data/.../repository/HomeRowConfigRepositoryImpl.kt` | Persistence via SettingsDataStore (JSON string), default config logic |
| `app/.../feature/settings/HomeRowConfigScreen.kt` | Écran de configuration: liste avec toggle + boutons up/down |
| `app/.../feature/settings/HomeRowConfigViewModel.kt` | ViewModel pour l'écran de config |

### Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `core/datastore/.../SettingsDataStore.kt` | Ajouter pref `home_row_config` (JSON string) |
| `data/.../di/RepositoryModule.kt` | `@Binds` HomeRowConfigRepository |
| `app/.../feature/home/HomeUiState.kt` | Ajouter sealed `HomeRow` (ContinueWatching, MyList, Suggestions, HubRow) + `orderedRows: ImmutableList<HomeRow>` |
| `app/.../feature/home/HomeViewModel.kt` | Injecter `HomeRowConfigRepository`, combiner config + data pour construire `orderedRows` |
| `app/.../feature/home/NetflixHomeScreen.kt` | Remplacer les 4 sections hardcodées par une itération sur `state.orderedRows` |
| `core/navigation/.../Screen.kt` | Route `HomeRowConfig` |
| `app/.../feature/settings/categories/GeneralSettingsScreen.kt` | Ajouter "Customize Home" tile |
| `app/.../feature/settings/SettingsViewModel.kt` | Ajouter navigation event |
| NavHost principal | Enregistrer route `HomeRowConfig` |
| Strings EN + FR | Tous les labels |

### HomeRow sealed interface

```kotlin
sealed interface HomeRow {
    val identifier: String
    data class ContinueWatching(val items: ImmutableList<MediaItem>) : HomeRow { override val identifier = "continue_watching" }
    data class MyList(val items: ImmutableList<MediaItem>) : HomeRow { override val identifier = "my_list" }
    data class Suggestions(val items: ImmutableList<MediaItem>) : HomeRow { override val identifier = "suggestions" }
    data class HubRow(override val identifier: String, val hub: Hub) : HomeRow
}
```

### Config par défaut

```json
[
  {"identifier":"continue_watching","enabled":true,"position":0},
  {"identifier":"my_list","enabled":true,"position":1},
  {"identifier":"suggestions","enabled":true,"position":2}
]
```

Les hubs dynamiques du serveur sont ajoutés après position 2, en préservant l'ordre du `hubDisplayOrderComparator` existant.

---

## F10 — Playlists (Impact 3/5, Effort 3/5) — ✅ QUASI-COMPLET (98%)

**Objectif**: Support complet des playlists Plex: lister, voir items, créer, ajouter/supprimer items, supprimer playlist. Suit le pattern de la feature Collection.

### Endpoints Plex API

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/playlists?playlistType=video` | Lister les playlists |
| GET | `/playlists/{id}/items` | Items d'une playlist |
| POST | `/playlists?title=X&type=video&uri=server://...` | Créer une playlist |
| PUT | `/playlists/{id}/items?uri=server://...` | Ajouter un item |
| DELETE | `/playlists/{id}/items/{itemId}` | Retirer un item |
| DELETE | `/playlists/{id}` | Supprimer une playlist |

### Fichiers à créer

| Fichier | Contenu |
|---------|---------|
| `core/database/.../PlaylistEntity.kt` | Entity playlist: `id`, `serverId` (composite PK), `title`, `summary`, `thumbUrl`, `itemCount`, `durationMs`, `lastSync` |
| `core/database/.../PlaylistItemEntity.kt` | CrossRef: `playlistId`, `serverId`, `itemRatingKey`, `orderIndex`, `playlistItemId` |
| `core/database/.../PlaylistDao.kt` | CRUD + JOIN avec `media` pour items |
| `core/model/.../Playlist.kt` | Domain model |
| `domain/.../repository/PlaylistRepository.kt` | Interface: `getPlaylists()`, `getDetail()`, `create()`, `addItem()`, `removeItem()`, `delete()`, `refresh()` |
| `data/.../repository/PlaylistRepositoryImpl.kt` | Impl avec `ServerClientResolver` multi-serveur |
| `app/.../feature/playlist/PlaylistListScreen.kt` | Grille de playlists |
| `app/.../feature/playlist/PlaylistListViewModel.kt` | ViewModel |
| `app/.../feature/playlist/PlaylistDetailScreen.kt` | Détail: header + grid items (pattern Collection) |
| `app/.../feature/playlist/PlaylistDetailViewModel.kt` | ViewModel |
| `app/.../feature/playlist/PlaylistDetailUiState.kt` | State |
| `app/.../feature/playlist/CreatePlaylistDialog.kt` | Dialog avec champ titre |
| `app/.../feature/details/components/AddToPlaylistDialog.kt` | Dialog: liste playlists existantes + "Create New" |

### Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `core/network/.../PlexApiService.kt` | 6 nouveaux endpoints playlist |
| `core/database/.../PlexDatabase.kt` | 2 entities, DAO, version++ |
| `core/database/.../DatabaseModule.kt` | Migration, `@Provides` DAO |
| `data/.../di/RepositoryModule.kt` | `@Binds` PlaylistRepository |
| `core/navigation/.../Screen.kt` | Routes `Playlists`, `PlaylistDetail` |
| `core/navigation/.../NavigationItem.kt` | Entrée sidebar "Playlists" (entre Favorites et History) |
| `app/.../feature/details/MediaDetailUiState.kt` | Events `AddToPlaylistClicked`, `AddToPlaylist(playlistId)` |
| `app/.../feature/details/MediaDetailScreen.kt` | Bouton "Add to Playlist" dans actions |
| `app/.../feature/details/MediaDetailViewModel.kt` | Handler events playlist |
| NavHost principal | Enregistrer routes playlist |
| Strings EN + FR | Tous les labels |

### DB Schema

```sql
CREATE TABLE playlists (
    id TEXT NOT NULL,
    serverId TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT,
    thumbUrl TEXT,
    playlistType TEXT NOT NULL DEFAULT 'video',
    itemCount INTEGER NOT NULL DEFAULT 0,
    durationMs INTEGER NOT NULL DEFAULT 0,
    lastSync INTEGER NOT NULL,
    PRIMARY KEY (id, serverId)
);

CREATE TABLE playlist_items (
    playlistId TEXT NOT NULL,
    serverId TEXT NOT NULL,
    itemRatingKey TEXT NOT NULL,
    orderIndex INTEGER NOT NULL,
    playlistItemId TEXT NOT NULL,
    PRIMARY KEY (playlistId, serverId, itemRatingKey)
);
CREATE INDEX idx_playlist_items ON playlist_items (playlistId, serverId);
```

---

## Versions DB — Toutes migrations appliquees

| Feature | Migration | Statut |
|---------|-----------|--------|
| F13 (Delete) | Aucune | ✅ Utilise tables existantes |
| F14 (Person Favorites) | v40→41 | ✅ CREATE TABLE person_favorites — appliquee |
| F10 (Playlists) | v41→42 | ✅ CREATE TABLE playlists + playlist_items — appliquee |

**Version courante : Room v42**

---

## Vérification

1. **Compilation**: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` après chaque feature
2. **Tests unitaires**: `./gradlew :app:testDebugUnitTest --tests "com.chakir.plexhubtv.feature.*"`
3. **Tests DB**: Vérifier que les migrations 40→41 et 41→42 passent (les tests de migration existants)
4. **Test manuel**: Build APK et tester sur Android TV device/emulator chaque feature
