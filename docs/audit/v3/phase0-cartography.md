# Phase 0 — Cartographie PlexHubTV (Agent 0)

> **Rôle** : Cartography Agent / livrable partagé aux 5 agents aval (Stability, Security, Performance, Architecture, UX, Release).
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Règles** : aucun jugement, citations de fichiers, marqueurs `→ à valider sur device`, `→ non vérifié`, `→ échantillonné`.

---

## 1. Executive overview

PlexHubTV est un client Android TV (Jetpack Compose / Compose for TV, D-Pad first) pour Plex Media Server avec intégration multi-sources secondaires (Jellyfin, Xtream, un backend propriétaire "Backend", IPTV M3U). Clean Architecture multi-modules Kotlin, MVVM + MVI avec `BaseViewModel` partagé, Hilt pour l'injection, Room (v47) offline-first en SSOT, Retrofit/OkHttp pour le réseau, Media3/ExoPlayer (primaire) + libmpv (fallback) pour la lecture, WorkManager pour la synchro périodique, Firebase Crashlytics/Analytics/Performance, Coil pour les images. Cible Android TV (`android.software.leanback`), `minSdk=27`, `targetSdk=35`, `compileSdk=36`, single APK ARM/x86 multi-ABI.

---

## 2. Module map

Source : `settings.gradle.kts` et `*/build.gradle.kts`.

| Module Gradle | Chemin | Responsabilité | Fichiers .kt (src/main, hors build/) | Dépendances vers autres modules |
|---|---|---|---|---|
| `:app` | `app/` | UI Compose, ViewModels, DI racine, Workers, MainActivity, Application | ~201 | `:domain`, `:data`, `:core:model`, `:core:common`, `:core:network`, `:core:navigation`, `:core:database`, `:core:datastore`, `:core:designsystem`, `:core:ui` |
| `:domain` | `domain/` | Use cases, interfaces Repository, services (`PlaybackManager`, `AnalyticsService`, `TvChannelManager`), interface `MediaSourceHandler` | ~70 | `:core:model`, `:core:common` → non vérifié (à confirmer dans `domain/build.gradle.kts`) |
| `:data` | `data/` | Impl repositories, mappers, paging, source handlers (Plex/Jellyfin/Xtream/Backend), `MediaLibraryQueryBuilder`, `MediaDeduplicator`, `data/di/*` modules Hilt, `TvChannelManagerImpl` | ~61 | `:domain`, `:core:model`, `:core:common`, `:core:network`, `:core:database`, `:core:datastore` |
| `:core:model` | `core/model/` | Entités domain-model (MediaItem, Server, AppError, UnificationId, Profile, XtreamCategory, SourceType, etc.) | ~40 | Kotlin Serialization (aucun module) |
| `:core:common` | `core/common/` | `CoroutineModule` (dispatchers + `@ApplicationScope`), `FlowExtensions`, `StringNormalizer`, `PerformanceTracker`, utils codec/format/track, `CacheManager`, `ContentRatingHelper`, `ContentUtils` | ~11 | Coroutines |
| `:core:network` | `core/network/` | `NetworkModule` (OkHttp+Retrofit), `PlexApiService`, `TmdbApiService`, `OmdbApiService`, `OpenSubtitlesApiService`, `JellyfinApiService`, `XtreamApiService`, `BackendApiService`, `AuthInterceptor`, `PlexCacheInterceptor`, `AuthEventBus`, `ConnectionManager`, `PlexClient`, `JellyfinClient`, `ApiCache`, `ApiKeyManager`, `ServerConnectionTester`, DTOs | ~40 | `:core:model` (dep modules → non vérifié dans ce relevé) |
| `:core:database` | `core/database/` | `PlexDatabase` (v47), 24 entités, 21 DAOs, `DatabaseModule`, 36 migrations, `Converters`, schemas JSON exportés dans `core/database/schemas/` | ~46 | `:core:model` → non vérifié |
| `:core:datastore` | `core/datastore/` | `SettingsDataStore`, `SecurePreferencesManager` (EncryptedSharedPreferences AES-256-GCM via MasterKey), `DataStoreExtensions`, `DataStoreModule` | ~4 | DataStore, androidx.security.crypto |
| `:core:designsystem` | `core/designsystem/` | `Theme.kt`, `Color.kt`, `Dimensions.kt`, `Type.kt` (5 thèmes : Plex, Netflix, MonoDark, MonoLight, Morocco, + CinemaGold en cours de refonte sur cette branche) | ~4 | Compose Material3 |
| `:core:ui` | `core/ui/` | Composants UI partagés (`NetflixMediaCard`, `NetflixContentRow`, `NetflixHeroBillboard`, `NetflixTopBar`, `NetflixOnScreenKeyboard`, `CinemaGoldComponents`, `SpotlightGrid`, `FallbackAsyncImage`, `ErrorSnackbarHost`, `HandleErrors`, `ErrorMessageResolver`, `Skeletons`, `OverscanSafeArea`, `FocusUtils`, `BackdropColors`, `ThemedButton`, `HomeHeader`) | ~17 | `:core:model`, `:core:designsystem` |
| `:core:navigation` | `core/navigation/` | `Screen.kt` (sealed class routes minimales), `NavigationItem.kt` (items sidebar TV) | ~2 | Compose Navigation |

Total estimé : ~496 fichiers Kotlin (hors dossiers `build/`).

> `domain/build.gradle.kts`, `data/build.gradle.kts` et les `core/*/build.gradle.kts` existent mais n'ont pas été ouverts en profondeur → **échantillonné** pour le détail des bindings inter-modules.

---

## 3. Entry points

### 3.1 Application class

- `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt` — `@HiltAndroidApp`, implémente `SingletonImageLoader.Factory` (Coil) et `Configuration.Provider` (WorkManager).
- `onCreate()` :
  1. `Timber.plant` (DebugTree ou `CrashReportingTree` vers Firebase Crashlytics selon BuildType).
  2. Off main thread (`defaultDispatcher`) : `installSecurityProviders()` (Conscrypt en provider #1 + GMS `ProviderInstaller.installIfNeeded` en fallback) + `initializeFirebase()` (Crashlytics/Analytics/Perf avec collection désactivée en DEBUG).
  3. `initializeAppInParallel()` : 5 jobs parallèles (`SettingsDataStore` warm-up, Coil `ImageLoader` warm-up, `WorkerFactory` warm-up, `OkHttpClient` warm-up, `ConnectionManager` warm-up sur 3 serveurs max avec timeout 2 s). Signal `_appReady = true` quand tous terminés.
  4. `setupBackgroundSync()` : annule les anciens `RatingSyncWorker`, enqueue `LibrarySync_Initial` si `!isFirstSyncComplete && isLibrarySelectionDone`, ou `UnifiedRebuild_Startup` si déjà syncé ; puis enqueue 4 `UniquePeriodicWork` (`LibrarySync` 6 h, `CollectionSync` 6 h, `ChannelSync` 3 h, `CachePurge` 1 j).
  5. `tvChannelManagerLazy.get().createChannelIfNeeded()` (intégration Android TV Channel / `androidx.tvprovider`).
- Heavy singletons injectés via `dagger.Lazy` pour différer la création hors du thread principal : `OkHttpClient`, `SettingsDataStore`, `ImageLoader`, `ConnectionManager`, `AuthRepository`, `TvChannelManager`.
- `StateFlow<Boolean> appReady` exposé publiquement (utilisé par `SplashViewModel` → **non vérifié**).

### 3.2 Activities

Déclarations dans `app/src/main/AndroidManifest.xml` :

| Activité | Intent filters | Exported |
|---|---|---|
| `com.chakir.plexhubtv.MainActivity` | `MAIN` + `LAUNCHER` + `LEANBACK_LAUNCHER` ; deep link `VIEW / BROWSABLE` `plexhub://play` | `true` |

- `MainActivity.kt` : `@AndroidEntryPoint ComponentActivity`, demande `POST_NOTIFICATIONS` sur Tiramisu+, instancie `MainViewModel`, applique thème via `SettingsDataStore.appTheme` (`PlexHubTheme(appTheme=...)`), `enableEdgeToEdge()`, pose `NavHost` (`startDestination = Screen.Splash.route`) dans `PlexHubApp(mainViewModel)`.
- `PlexHubApp()` contient tout le graphe `NavHost` (routes : Splash, Login, LibrarySelection, Loading, PlexHomeSwitch, AppProfileSelection, AppProfileSwitch, Main, MediaDetail, SeasonDetail, CollectionDetail, PersonDetail, JellyfinSetup, XtreamSetup, XtreamCategorySelection, VideoPlayer). `VideoPlayer` enregistre le deepLink `plexhub://play/{ratingKey}?serverId={serverId}`. `SessionExpiredDialog` + `UpdateDialog` montés au niveau racine.

### 3.3 Services

| Service | Type | Fichier |
|---|---|---|
| `PlexHubDreamService` | `DreamService` (screensaver) avec permission `BIND_DREAM_SERVICE`, meta `@xml/dream_service` | `app/src/main/java/com/chakir/plexhubtv/feature/screensaver/PlexHubDreamService.kt` |
| `androidx.work.impl.foreground.SystemForegroundService` | `foregroundServiceType="dataSync"` (override via `tools:replace`) | fourni par `androidx.work` |

Aucun `Service` bound custom, aucun service démarré custom, aucun `JobService` custom → non vérifié au-delà du manifest.

### 3.4 WorkManager workers

Dossier `app/src/main/java/com/chakir/plexhubtv/work/` (tous `@HiltWorker + @AssistedInject`, confirmés par la présence de `WorkerParameters`).

| Worker | Fichier | Schedule enregistré dans `PlexHubApplication.setupBackgroundSync()` | Rôle |
|---|---|---|---|
| `LibrarySyncWorker` | `LibrarySyncWorker.kt` | `OneTimeWorkRequest` (Initial) + `PeriodicWorkRequest 6h` (`LibrarySync`), initial delay 20 min, backoff exponential 30 s, constraint `NetworkType.CONNECTED` | Sync films+séries Plex + Jellyfin depuis `/library/sections/{id}/all` et `/Users/{id}/Items`. Foreground service, chain vers `CollectionSyncWorker` puis `UnifiedRebuildWorker` → **non vérifié dans le code, description ARCHITECTURE.md** |
| `CollectionSyncWorker` | `CollectionSyncWorker.kt` | `PeriodicWorkRequest 6h` (`CollectionSync`), backoff 30 s | Sync collections Plex |
| `RatingSyncWorker` | `RatingSyncWorker.kt` | **Manuel uniquement** — `cancelAllWorkByTag` exécuté au démarrage pour nettoyer les anciennes planifications | Notes TMDb/OMDb → `displayRating` |
| `UnifiedRebuildWorker` | `UnifiedRebuildWorker.kt` | `OneTimeWorkRequest` au démarrage si `isFirstSyncComplete`, sinon chaîné après `LibrarySync` → **non vérifié dans le code** | Reconstruit la view matérialisée `media_unified` |
| `CachePurgeWorker` | `CachePurgeWorker.kt` | `PeriodicWorkRequest 1j` (`CachePurge`), initial delay 1 h | Purge `ApiCacheEntity` expiré |
| `ChannelSyncWorker` | `ChannelSyncWorker.kt` | `PeriodicWorkRequest 3h` (`ChannelSync`), backoff 30 s, constraint `NetworkType.CONNECTED` | Sync TV Channel Android (recommandations) / IPTV channels → **à valider sur device** |

> Toutes les planifications utilisent `ExistingPeriodicWorkPolicy.KEEP`. Le worker scheduling s'exécute sans attendre `isLibrarySelectionComplete` pour les 4 périodiques (seul `Initial` l'attend).

### 3.5 BroadcastReceivers

Aucun `<receiver>` déclaré dans `AndroidManifest.xml`.

### 3.6 ContentProviders / initializers

| Provider | Rôle |
|---|---|
| `androidx.startup.InitializationProvider` | Initializers `androidx.startup`. Le manifest **retire explicitement** (`tools:node="remove"`) `WorkManagerInitializer` car l'app fournit sa propre `Configuration` via `Configuration.Provider`. |
| `androidx.core.content.FileProvider` | Autorité `${applicationId}.fileprovider`, `@xml/file_provider_paths`. Utilisé pour partager l'APK téléchargé avec le package installer lors des updates in-app (`core/update/ApkInstaller.kt`). |

---

## 4. Screens / composables (paires Screen ↔ ViewModel)

Chemin racine : `app/src/main/java/com/chakir/plexhubtv/feature/`. Les *Route composables et *Screen composables vivent dans le même package.

| Feature package | Screen(s) / Route / Composables principaux | ViewModel(s) |
|---|---|---|
| `splash/` | `SplashScreen.kt` (+ `SplashRoute` → non vérifié) | `SplashViewModel` |
| `auth/` | `AuthScreen.kt`, `AuthRoute`, `AuthUiState.kt`, `components/SessionExpiredDialog.kt` | `AuthViewModel` |
| `loading/` | `LoadingScreen.kt`, `LoadingRoute`, `SyncStatusModel.kt` | `LoadingViewModel` |
| `libraryselection/` | `LibrarySelectionScreen.kt`, `LibrarySelectionRoute` | `LibrarySelectionViewModel` |
| `plexhome/` | `PlexHomeSwitcherScreen.kt`, `PlexHomeSwitcherRoute`, `PlexHomeSwitcherUiState.kt` | `PlexHomeSwitcherViewModel` |
| `appprofile/` | `AppProfileSelectionScreen.kt`, `AppProfileSwitchScreen.kt`, `ProfileFormDialog.kt`, `AppProfileUiState.kt` | `AppProfileViewModel` (shared) |
| `main/` | `MainScreen.kt` (conteneur sidebar) | `MainViewModel` (à la racine, `app/MainViewModel.kt`) |
| `home/` | `NetflixHomeScreen.kt`, `DiscoverScreen.kt`, `DiscoverScreenComponents.kt`, `HomeUiState.kt` | `HomeViewModel` |
| `library/` | `LibrariesScreen.kt`, `LibraryComponents.kt`, `AlphabetSidebar.kt`, `FilterDialog.kt`, `FilterSnapshot.kt`, `LibraryUiState.kt` | `LibraryViewModel` |
| `search/` | `NetflixSearchScreen.kt`, `SearchScreen.kt`, `SearchUiState.kt` | `SearchViewModel` |
| `details/` | `MediaDetailScreen.kt`, `NetflixDetailScreen.kt`, `NetflixDetailTabs.kt`, `PersonDetailScreen.kt`, `SeasonDetailScreen.kt`, `components/SourceSelectionDialog.kt`, `components/TechnicalBadges.kt`, `MediaDetailUiState.kt` | `MediaDetailViewModel`, `SeasonDetailViewModel`, `PersonDetailViewModel`, `MediaEnrichmentViewModel` |
| `collection/` | `CollectionDetailScreen.kt`, `CollectionDetailUiState.kt` | `CollectionDetailViewModel` |
| `hub/` | `HubScreen.kt`, `HubUiState.kt`, `components/RemoveFromOnDeckDialog.kt` | `HubViewModel` |
| `favorites/` | `FavoritesScreen.kt` | `FavoritesViewModel` |
| `history/` | `HistoryScreen.kt` | `HistoryViewModel` |
| `downloads/` | `DownloadsScreen.kt`, `DownloadsUiState.kt` | `DownloadsViewModel` |
| `playlist/` | `PlaylistListScreen.kt`, `PlaylistDetailScreen.kt`, `AddToPlaylistDialog.kt` | `PlaylistListViewModel`, `PlaylistDetailViewModel` |
| `settings/` | `SettingsScreen.kt`, `SettingsGridScreen.kt`, `SettingsCategoryCard.kt`, `SettingsComponents.kt`, `SubtitleStyleScreen.kt`, `SettingsUiState.kt`, `SettingsCategory.kt`, `categories/{DataSync,General,Playback,Server,Services,System}SettingsScreen.kt`, `serverstatus/ServerStatusScreen.kt` | `SettingsViewModel`, `SubtitleStyleViewModel`, `serverstatus/ServerStatusViewModel` |
| `debug/` | `DebugScreen.kt`, `DebugUiState.kt` | `DebugViewModel` |
| `screensaver/` | `ScreensaverContent.kt`, `PlexHubDreamService` (service) | `ScreensaverViewModel` |
| `iptv/` | `IptvScreen.kt`, `components/{CategoryColumn,ChannelColumn,PlayerEpgColumn}.kt` | `IptvViewModel` |
| `jellyfin/` | `JellyfinSetupScreen.kt`, `JellyfinSetupRoute` | `JellyfinSetupViewModel` |
| `xtream/` | `XtreamSetupScreen.kt`, `XtreamCategorySelectionScreen.kt` | `XtreamSetupViewModel`, `XtreamCategorySelectionViewModel` |
| `player/` | `VideoPlayerScreen.kt`, `VideoPlayerRoute`, `PlayerUiState.kt`, `components/NetflixPlayerControls.kt`, `ui/components/{AudioEqualizerDialog, ChapterOverlay, DownloadSubtitlesDialog, EnhancedSeekBar, PerformanceOverlay, PlayerErrorOverlay, PlayerMoreMenu, PlayerSettingsDialog, QueueOverlay, SkipMarkerButton}.kt` | `PlayerControlViewModel`, `TrackSelectionViewModel`, `PlaybackStatsViewModel` (3 VMs partagent `PlayerController` singleton) |
| `common/` | `BaseViewModel.kt`, `ViewModelExtensions.kt` | Base abstract (error channel `AppError` + `emitError()`) |

Total ViewModels ≈ 35 (cf. ARCHITECTURE.md §9, confirmé par énumération).

> `core/ui/ParentalPinDialog.kt`, `core/ui/ThemeSongService.kt`, et `core/update/{UpdateDialog, UpdateChecker, ApkInstaller, UpdateInfo}.kt` sont des composants app-level hors `feature/` → **échantillonnés**.

---

## 5. Domain layer — use cases

Répertoire : `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/`.

| Use case | Rôle (1 ligne) |
|---|---|
| `DeleteMediaUseCase` | Supprime un média côté serveur + DB |
| `EnrichMediaItemUseCase` | **@Singleton** cache `ConcurrentHashMap`, Room-first puis fallback réseau, matching `unificationId` (films/séries) ou `grandparent+parent+index` (épisodes) |
| `EpisodeNavigationUseCase` | Navigation entre épisodes (prev/next) |
| `FilterContentByAgeUseCase` | Filtrage parental (profils kids mode) |
| `GetCollectionUseCase` | Récupère une collection par ID |
| `GetEnabledServerIdsUseCase` | Liste des serveurs actifs (non exclus) |
| `GetFavoriteActorsUseCase` | Liste des acteurs favoris |
| `GetFavoritesUseCase` | Liste favoris utilisateur |
| `GetLibraryContentUseCase` | Contenu bibliothèque (paging, filtres, tri) |
| `GetLibraryIndexUseCase` | Index alphabétique / navigation rapide lettre |
| `GetMediaCollectionsUseCase` | Collections auxquelles appartient un média |
| `GetMediaDetailUseCase` | Détails média enrichis |
| `GetNextEpisodeUseCase` | Épisode suivant pour Smart Start |
| `GetPlayQueueUseCase` | Construction file de lecture |
| `GetSimilarMediaUseCase` | Médias similaires (discover rails) |
| `GetUnifiedHomeContentUseCase` | Contenu unifié HomeScreen (hubs + on deck) depuis `media_unified` |
| `GetUnifiedSeasonsUseCase` | Agrégation de saisons multi-serveurs |
| `GetWatchHistoryUseCase` | Historique de lecture |
| `GetXtreamCategoriesUseCase` | Catégories Xtream |
| `IsFavoriteUseCase` | Booléen favori |
| `PrefetchNextEpisodeUseCase` | Prefetch next episode durant lecture courante |
| `PreparePlaybackUseCase` | Pipeline complet avant `PlaybackManager.play()` : enrichment → source selection → URL build → queue |
| `ResolveEpisodeSourcesUseCase` (interface) + `ResolveEpisodeSourcesUseCaseImpl` (dans `data/usecase/`) | Résout les sources disponibles pour un épisode multi-serveur |
| `SearchAcrossServersUseCase` | Recherche multi-serveurs fusionnée |
| `SortOnDeckUseCase` | Tri On Deck |
| `SyncJellyfinLibraryUseCase` | Sync bibliothèques Jellyfin |
| `SyncWatchlistUseCase` | Sync watchlist cloud Plex |
| `SyncXtreamLibraryUseCase` | Sync Xtream VOD/séries |
| `ToggleFavoriteUseCase` | Ajout/suppression favori |
| `ToggleWatchStatusUseCase` | Marquer vu/non vu |
| `GetSuggestionsUseCase` | `app/src/.../domain/usecase/GetSuggestionsUseCase.kt` — suggestions (placement app-level → **non vérifié**, c'est le seul use case hors `:domain`) |

Total dans `:domain/usecase/` : 29 (+ 1 dans `:app`). ARCHITECTURE.md dit "29 use cases".

### Services (domain/service)
- `PlaybackManager.kt` — orchestrateur lecture, interface consommée par `PlayerController`
- `PlaybackReporter.kt` — interface abstraite de reporting (scrobbling)
- `AnalyticsService.kt` — abstraction analytics (impl Firebase côté `:app/di/AnalyticsModule.kt`)
- `TvChannelManager.kt` — interface TV Channel (impl `data/util/TvChannelManagerImpl.kt`)

### Source interface
- `domain/source/MediaSourceHandler.kt` — Strategy pattern pour Plex/Jellyfin/Xtream/Backend.

---

## 6. Data layer

### 6.1 Repositories

Conventions : interface dans `:domain/repository/`, implémentation dans `:data/repository/`. 25 interfaces `domain`, 24 impls `data` recensées (`ResolveEpisodeSourcesUseCaseImpl` est un use case, pas un repo).

| Interface (`:domain/repository/`) | Implémentation (`:data/repository/`) |
|---|---|
| `AuthRepository.kt` | `AuthRepositoryImpl.kt` |
| `AccountRepository.kt` | `AccountRepositoryImpl.kt` |
| `LibraryRepository.kt` | `LibraryRepositoryImpl.kt` |
| `MediaDetailRepository.kt` | `MediaDetailRepositoryImpl.kt` |
| `SearchRepository.kt` | `SearchRepositoryImpl.kt` |
| `PlaybackRepository.kt` | `PlaybackRepositoryImpl.kt` |
| `OnDeckRepository.kt` | `OnDeckRepositoryImpl.kt` |
| `HubsRepository.kt` | `HubsRepositoryImpl.kt` |
| `FavoritesRepository.kt` | `FavoritesRepositoryImpl.kt` |
| `WatchlistRepository.kt` | `WatchlistRepositoryImpl.kt` |
| `SyncRepository.kt` | `SyncRepositoryImpl.kt` |
| `DownloadsRepository.kt` | `DownloadsRepositoryImpl.kt` |
| `CategoryRepository.kt` | `HybridCategoryRepository.kt` (fusion multi-source) |
| `OfflineWatchSyncRepository.kt` | `OfflineWatchSyncRepositoryImpl.kt` |
| `IptvRepository.kt` | `IptvRepositoryImpl.kt` |
| `ProfileRepository.kt` | `ProfileRepositoryImpl.kt` |
| `JellyfinServerRepository.kt` | `JellyfinServerRepositoryImpl.kt` |
| `PlaylistRepository.kt` | `PlaylistRepositoryImpl.kt` |
| `PersonFavoriteRepository.kt` | `PersonFavoriteRepositoryImpl.kt` |
| `BackendRepository.kt` | `BackendRepositoryImpl.kt` |
| `XtreamAccountRepository.kt` | `XtreamAccountRepositoryImpl.kt` |
| `XtreamSeriesRepository.kt` | `XtreamSeriesRepositoryImpl.kt` |
| `XtreamVodRepository.kt` | `XtreamVodRepositoryImpl.kt` |
| `TrackPreferenceRepository.kt` | `TrackPreferenceRepositoryImpl.kt` |
| `SettingsRepository.kt` | `SettingsRepositoryImpl.kt` |

Classes annexes `data/repository/` : `ServerClientResolver.kt`, `ServerNameResolver.kt`, `JellyfinClientResolver.kt`, `MediaLibraryQueryBuilder.kt`, `AggregationService.kt`, `aggregation/MediaDeduplicator.kt`.

Bindings Hilt : `data/di/RepositoryModule.kt` (et `NetworkBindingsModule.kt`, `PlaybackReporterModule.kt`, `SourceHandlerModule.kt`).

### 6.2 Source handlers (`data/source/`)

- `PlexSourceHandler.kt`
- `JellyfinSourceHandler.kt` (modifié sur cette branche)
- `XtreamSourceHandler.kt`
- `BackendSourceHandler.kt`
- `MediaSourceResolver.kt` — sélection de la source adéquate selon `SourceType` (`:core:model/SourceType.kt`, untracked dans git status)

### 6.3 Mappers (`data/mapper/`)

- `MediaMapper.kt` — Plex DTO → MediaItem
- `BackendMediaMapper.kt` — Backend DTO → MediaItem
- `JellyfinMapper.kt` — Jellyfin DTO → MediaItem (ticks→ms, ProviderIds, URLs relatives)
- `XtreamMediaMapper.kt` — Xtream DTO → MediaItem (modifié sur cette branche)
- `ServerMapper.kt` — Server DTO → `Server`
- `UserMapper.kt` — User DTO → `UserProfile`

### 6.4 Autres data

- `data/cache/RoomApiCache.kt` — impl `ApiCache` sur Room
- `data/paging/MediaRemoteMediator.kt` — Paging 3 + Room
- `data/playback/PlexPlaybackReporter.kt` / `JellyfinPlaybackReporter.kt` — scrobbling
- `data/network/DataStoreApiKeyProvider.kt`, `DataStoreAuthTokenProvider.kt`, `DataStoreConnectionCacheStore.kt` — bridges DataStore → `:core:network`
- `data/iptv/M3uParser.kt` — parser M3U pour IPTV
- `data/util/TvChannelManagerImpl.kt` — implémentation TV Channel Android
- `data/repository/` : `core/util/MediaUrlResolver.kt` est curieusement sous `data/src/main/java/com/chakir/plexhubtv/core/util/` → **non vérifié** (placement atypique à auditer en Phase Architecture)

---

## 7. Database

### 7.1 PlexDatabase

- Fichier : `core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt`
- **Version : 47** (ARCHITECTURE.md indique v46 — **divergence documentée**, migration 46→47 ajoutée, cf `DatabaseModule.kt` ligne 751)
- `@TypeConverters(Converters::class)`, `exportSchema = true` (schemas sous `core/database/schemas/com.chakir.plexhubtv.core.database.PlexDatabase/`)
- Mode WAL activé + pragmas (`synchronous=NORMAL`, `cache_size=-8000`) → décrit dans ARCHITECTURE.md, **non vérifié** dans le code de ce relevé
- 36 migrations (`MIGRATION_11_12` → `MIGRATION_46_47`) déclarées dans `DatabaseModule.kt` lignes 15–818 (grep confirmé)

### 7.2 Entités (24)

Source : déclaration `@Database(entities=[...])` dans `PlexDatabase.kt`.

```
MediaEntity, ServerEntity, DownloadEntity, ApiCacheEntity, OfflineWatchProgressEntity,
HomeContentEntity, FavoriteEntity, RemoteKey, LibrarySectionEntity, TrackPreferenceEntity,
CollectionEntity, MediaCollectionCrossRef, ProfileEntity, SearchCacheEntity, MediaFts (FTS4),
XtreamAccountEntity, BackendServerEntity, IdBridgeEntity, MediaUnifiedEntity,
PersonFavoriteEntity, PlaylistEntity, PlaylistItemEntity, WatchlistEntity, JellyfinServerEntity
```

Fichier "CollectionEntities.kt" contient `CollectionEntity` + `MediaCollectionCrossRef`.

### 7.3 DAOs (21) + nombre d'annotations `@Query` (rapide via grep)

| DAO | `@Query` count |
|---|---|
| `MediaDao` | 54 |
| `MediaUnifiedDao` | 10 |
| `OfflineWatchProgressDao` | 10 |
| `ProfileDao` | 9 |
| `PlaylistDao` | 8 |
| `CollectionDao` | 6 |
| `JellyfinServerDao` | 5 |
| `RemoteKeysDao` | 5 |
| `ApiCacheDao` | 4 |
| `FavoriteDao` | 4 |
| `HomeContentDao` | 4 |
| `SearchCacheDao` | 4 |
| `WatchlistDao` | 4 |
| `XtreamAccountDao` | 4 |
| `BackendServerDao` | 3 |
| `DownloadDao` | 3 |
| `LibrarySectionDao` | 3 |
| `PersonFavoriteDao` | 3 |
| `ServerDao` | 3 |
| `TrackPreferenceDao` | 3 |
| `IdBridgeDao` | 1 |

Notes :
- `MediaDao` contient aussi plusieurs `@RawQuery` consommés par `MediaLibraryQueryBuilder` (les 54 ci-dessus comptent uniquement `@Query`).
- `MediaLibraryQueryBuilder.kt` (`data/repository/`) construit les `SupportSQLiteQuery` dynamiques (browsing paginé, sort, filtres, agrégation multi-source).

### 7.4 Converters

- `core/database/src/main/java/com/chakir/plexhubtv/core/database/Converters.kt` — annotation `@TypeConverters` unique au niveau DB.

---

## 8. Network

### 8.1 Retrofit services

Tous dans `core/network/src/main/java/com/chakir/plexhubtv/core/network/`.

| Service | Base URL (grep `NetworkModule.kt`) | Rôle |
|---|---|---|
| `PlexApiService` | `https://plex.tv/` (default) + **`@Url` dynamique** par serveur | Auth PIN, resources, watchlist, médias, collections, playback, Plex Home |
| `TmdbApiService` | `https://api.themoviedb.org/` | Notes TV (vote_average) — utilisé par `RatingSyncWorker` |
| `OmdbApiService` | `https://www.omdbapi.com/` | Notes IMDb |
| `OpenSubtitlesApiService` | `https://api.opensubtitles.com/api/v1/` | Téléchargement sous-titres dans le player |
| `JellyfinApiService` | `@Url` dynamique par serveur Jellyfin (défini dans `JellyfinNetworkModule.kt`) | Auth, browse, search, playback |
| `XtreamApiService` | `@Url` dynamique (cf `core/network/xtream/`) | Xtream Codes (player_api) |
| `BackendApiService` | `http://192.168.0.175:8186/` en debug (cf `BuildConfig.API_BASE_URL`), `https://plex.tv/` en release → **attention : la release utilise plex.tv comme fallback `API_BASE_URL`** | Backend propriétaire "PlexHub Backend" |

### 8.2 Interceptors

- `AuthInterceptor.kt` — injecte `X-Plex-Token` sur chaque requête Plex, émet `AuthEventBus.TokenInvalid` sur 401 **plex.tv uniquement** (les 401 serveurs locaux sont ignorés). Pas de retry logic.
- `PlexCacheInterceptor.kt` — cache HTTP 5 min pour endpoints stables.
- `di/image/JellyfinImageInterceptor.kt` (dans `:app`) — injecte header `Authorization` pour les images Jellyfin côté Coil.
- `di/image/PerformanceImageInterceptor.kt` — observabilité chargement images.
- `player/net/RangeRetryInterceptor.kt` — retry range requests HTTP (player OkHttp DataSource).
- `player/net/CrlfFixSocketFactory.kt` — workaround CRLF pour certains serveurs.
- `okhttp.logging.HttpLoggingInterceptor` — fourni par libs, configuration inconnue → **non vérifié**.

### 8.3 OkHttp / Retrofit setup

- `core/network/NetworkModule.kt` — fournit `OkHttpClient` (avec trust SSL pour IPs locales via `NetworkSecurityConfig` + `Conscrypt`), instancie les 4 Retrofit (`plex.tv`, `api.themoviedb.org`, `www.omdbapi.com`, `api.opensubtitles.com`).
- `core/network/jellyfin/JellyfinNetworkModule.kt` — module Hilt dédié Jellyfin.
- `core/network/ConnectionManager.kt` — teste les candidats de connexion en parallèle (race), cache les résultats.
- `core/network/ServerConnectionTester.kt` — test unitaire d'URL serveur.
- `core/network/jellyfin/JellyfinConnectionTester.kt` — idem côté Jellyfin (`/System/Info/Public`).
- `core/network/ApiCache.kt` + `data/cache/RoomApiCache.kt` — cache applicatif.
- `core/network/ApiKeyManager.kt` + `core/network/ApiKeyProvider.kt` + `data/network/DataStoreApiKeyProvider.kt` — rotation/fourniture clés TMDb/OMDb depuis `SecurePreferencesManager`.
- `core/network/auth/AuthEventBus.kt` — `MutableSharedFlow` (buffer=1, `DROP_OLDEST`) pour les événements de session expirée.
- `core/network/auth/AuthTokenProvider.kt` — interface fournie côté data via `DataStoreAuthTokenProvider`.
- `core/network/ConnectionCacheStore.kt` — interface, impl `DataStoreConnectionCacheStore`.

### 8.4 Network security config

- `app/src/main/res/xml/network_security_config.xml` — trust SSL autosigné LAN → **échantillonné** (non ouvert).

---

## 9. Player

### 9.1 Moteurs

- **ExoPlayer / Media3 1.5.1 (primaire)** — `androidx.media3:media3-exoplayer:1.5.1` + `exoplayer-hls`, `ui`, `common`, `session`, `datasource-okhttp`. Extensions : `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` (FFmpeg decoder), `io.github.peerless2012:ass-media:0.4.0-beta01` (ASS subtitles).
- **MPV (fallback)** — `dev.jdtech.mpv:libmpv:0.5.1`. Fichiers `player/mpv/MpvPlayer.kt`, `player/mpv/MpvPlayerWrapper.kt`.
- Basculement : `PlayerFactory.kt` (fichier `app/src/.../feature/player/PlayerFactory.kt`), sélection via `SettingsDataStore.playerEngine` ou fallback auto sur erreur MediaCodec.

### 9.2 Fichiers player (tous dans `app/src/main/java/com/chakir/plexhubtv/feature/player/`)

- **Racine player** : `VideoPlayerScreen.kt`, `PlayerFactory.kt`, `PlayerUiState.kt`, `StreamMetadata.kt`, `ExoStreamMetadata.kt`
- **ViewModels** : `PlayerControlViewModel.kt`, `TrackSelectionViewModel.kt`, `PlaybackStatsViewModel.kt`
- **Controllers (`player/controller/`)** :
  - `PlayerController.kt` (@Singleton, `MutableStateFlow<PlayerUiState>` partagé)
  - `PlayerActionHandler.kt`
  - `PlayerInitializer.kt`
  - `PlayerMediaLoader.kt`
  - `PlayerPositionTracker.kt`
  - `PlayerScrobbler.kt`
  - `PlayerTrackController.kt`
  - `PlayerStatsTracker.kt`
  - `ChapterMarkerManager.kt`
  - `TrickplayManager.kt`
  - `RefreshRateManager.kt` (match refresh rate TV)
  - `AudioEqualizerManager.kt`
  - `MediaSessionManager.kt`
  - `SubtitleSearchService.kt`
- **UI components (`player/ui/components/`)** : `AudioEqualizerDialog`, `ChapterOverlay`, `DownloadSubtitlesDialog`, `EnhancedSeekBar`, `PerformanceOverlay`, `PlayerErrorOverlay`, `PlayerMoreMenu`, `PlayerSettingsDialog`, `QueueOverlay`, `SkipMarkerButton`
- **Composants hors ui/** : `components/NetflixPlayerControls.kt`
- **URL builders (`player/url/`)** : `PlaybackUrlBuilder.kt` (interface), `PlaybackUrlBuilderModule.kt`, `DirectStreamUrlBuilder.kt`, `TranscodeUrlBuilder.kt`, `JellyfinUrlBuilder.kt`, `BackendUrlBuilder.kt`, `XtreamUrlBuilder.kt`
- **Network player (`player/net/`)** : `RangeRetryInterceptor.kt`, `CrlfFixSocketFactory.kt`
- **Device profile (`player/profile/`)** : `DeviceProfileService.kt` (capabilities codec/HDR/DolbyVision détectés à la volée → **à valider sur device**)
- **DI (`player/di/`)** : `PlayerModule.kt`

### 9.3 Track selection & subtitles

- `PlayerTrackController.kt` gère audio + sous-titres (sélection par préférence `preferredAudioLanguage`/`preferredSubtitleLanguage` de `SettingsDataStore`)
- `SubtitleSearchService.kt` + `DownloadSubtitlesDialog.kt` + `OpenSubtitlesApiService` → recherche/téléchargement sous-titres OpenSubtitles
- Rendu sous-titres ASS via `peerless2012:ass-media` (dependency app)
- Styles sous-titres : `feature/settings/SubtitleStyleScreen.kt` + `SubtitleStyleViewModel.kt`

---

## 10. Critical third-party dependencies (from `gradle/libs.versions.toml`)

| Category | Library | Version |
|---|---|---|
| Kotlin | `kotlin` | **2.2.10** |
| Build tools | `agp` (Android Gradle Plugin) | **9.0.1** |
| KSP | | 2.3.2 |
| Compose BOM | `composeBom` | **2026.01.00** |
| Compose foundation (pinned) | `foundation` | 1.10.2 |
| TV Foundation | `androidx.tv:tv-foundation` | 1.0.0-alpha12 |
| TV Material | `androidx.tv:tv-material` | 1.1.0-alpha01 |
| Material3 | `androidx.compose.material3` | 1.3.1 |
| Navigation Compose | | 2.9.6 |
| Lifecycle | | 2.10.0 |
| Activity Compose | | 1.9.3 |
| Collections Immutable | | 0.3.7 |
| Palette | `androidx.palette` | 1.0.0 |
| **Media3 / ExoPlayer** | `media3` | **1.5.1** (+ `media3-common-ktx` 1.9.2 — divergent minor) |
| FFmpeg decoder | `org.jellyfin.media3:media3-ffmpeg-decoder` | 1.9.0+1 |
| ASS subtitles | `io.github.peerless2012:ass-media` | 0.4.0-beta01 |
| libmpv | `dev.jdtech.mpv:libmpv` | 0.5.1 |
| **Room** | `room` | **2.8.4** |
| **Hilt** | `hilt` (Dagger) | **2.58** |
| Hilt AndroidX (hiltCompose/work) | | 1.3.0 |
| **Retrofit** | | 3.0.0 |
| Retrofit kotlinx-serialization converter | `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | 1.0.0 |
| **OkHttp** | | 5.3.2 |
| Gson | | 2.11.0 |
| Kotlinx Serialization | | 1.9.0 |
| **Coil 3** | `io.coil-kt.coil3:coil-compose/coil-video/coil-network-okhttp` | 3.3.0 |
| Paging 3 | `paging` | 3.3.6 |
| WorkManager | | 2.11.0 |
| DataStore Preferences | | 1.2.0 |
| Security Crypto | | 1.1.0-alpha06 |
| **Coroutines** | | 1.10.2 |
| Timber | | 5.0.1 |
| TV Provider | `androidx.tvprovider` | 1.0.0 |
| Conscrypt | `org.conscrypt:conscrypt-android` | 2.5.2 |
| Play Services Basement | | 18.5.0 |
| **Firebase BOM** | `firebase-bom` | **34.9.0** (Crashlytics + Analytics + Performance) |
| google-services plugin | | 4.4.4 |
| firebase-crashlytics plugin | | 3.0.6 |
| firebase-perf plugin | | 2.0.2 |
| Detekt plugin | | 1.23.7 |
| Ktlint plugin | | 12.1.2 |
| JUnit 4 | | 4.13.2 |
| MockK | | 1.13.8 |
| Truth | | 1.1.5 |
| Robolectric | `org.robolectric:robolectric` | 4.11.1 (dans `app/build.gradle.kts` en dur) |
| Turbine | **non listé** dans `libs.versions.toml` → **non vérifié** (ARCHITECTURE.md dit "Turbine", mais la toml ne l'inclut pas) |
| LeakCanary | `com.squareup.leakcanary:leakcanary-android:2.14` (debugImplementation, en dur dans `app/build.gradle.kts`) | 2.14 |

### SDK levels (`app/build.gradle.kts`)

- `compileSdk = 36`
- `minSdk = 27` (Android 8.1)
- `targetSdk = 35` (Android 15)
- `versionCode = 1`, `versionName = "1.0.16"` → **divergence** (versionCode=1 avec versionName 1.0.16, à investiguer Release Agent)
- `ndk.abiFilters = ["armeabi-v7a", "arm64-v8a", "x86", "x86_64"]` (malgré le commentaire "x86 excluded")
- Java 17 / JVM target 17

### Compose

- Module `:app` utilise `libs.androidx.compose.material3` (Material3 classique, **pas** Compose for TV Material) ; les modules `core/ui` et features utilisent un mélange Material3 + tv-material → **non vérifié dans le détail**.

---

## 11. Build pipeline

### 11.1 Flavors

**Aucun product flavor** défini (`app/build.gradle.kts` ne contient que `buildTypes`).

### 11.2 Build types

Source : `app/build.gradle.kts` lignes 51–88.

| Build type | BuildConfig fields | Minify | Shrink resources | Signing |
|---|---|---|---|---|
| `debug` | `API_BASE_URL="http://192.168.0.175:8186/"`, `PLEX_TOKEN` / `IPTV_PLAYLIST_URL` / `TMDB_API_KEY` / `OMDB_API_KEY` chargés depuis `local.properties` | `false` (par défaut) | `false` | debug keystore par défaut |
| `release` | `API_BASE_URL="https://plex.tv/"`, `PLEX_TOKEN=""`, `IPTV_PLAYLIST_URL=""`, `TMDB_API_KEY=""`, `OMDB_API_KEY=""` | **`true`** | **`true`** | `signingConfigs.release` si `keystore/keystore.properties` existe, sinon fallback sur **debug keystore** |

Fichier ProGuard : `app/proguard-rules.pro` (+ `proguard-android-optimize.txt` par défaut). Les modules `:core:common`, `:core:database`, `:core:network`, `:core:ui` ont leurs `consumer-rules.pro` + `proguard-rules.pro` locaux → **échantillonné**.

### 11.3 Signing config

- Keystore : `keystore/plexhubtv-release.jks` (présent dans le repo — **à vérifier par Security Agent : fichier sensible commité ?**)
- Propriétés : `keystore/keystore.properties` (présent, également `keystore.properties.bak`)
- Les valeurs (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) sont lues depuis `keystore.properties` via `Properties().load(FileInputStream(...))` au moment de configure Gradle → **non vérifié dans gitignore**
- `enableV1Signing = true`, `enableV2Signing = true` (pas de V3/V4)
- **Fallback release → debug signing** si keystore absent : code à ligne 76-81 de `app/build.gradle.kts`

### 11.4 ABI splits / App Bundle

- Commentaire ligne 131 : "No ABI splits — single universal APK (ARM only, x86 excluded via ndk.abiFilters)" — **contradictoire** avec `abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")` ligne 31 (x86 et x86_64 sont effectivement inclus).
- Pas de `bundle {}` block explicite dans `app/build.gradle.kts` → configuration AAB par défaut AGP.

### 11.5 Baseline Profiles

- Aucune trace de `androidx.baselineprofile` plugin dans `libs.versions.toml` ni `build.gradle.kts`.
- Aucun module `:baselineprofile` dans `settings.gradle.kts`.
- → **Absent**.

### 11.6 packaging

- Exclusions META-INF standards (`AL2.0`, `LGPL2.1`, `DEPENDENCIES`, `LICENSE*`, `NOTICE*`, `ASL2.0`, `*.kotlin_module`).
- `jniLibs.pickFirsts` pour `libc++_shared.so` toutes ABIs.

### 11.7 Lint/quality

- Ktlint et Detekt appliqués à tous les sous-projets via `subprojects {}` dans `build.gradle.kts` racine.
- Detekt config : `config/detekt/detekt.yml`, `buildUponDefaultConfig = true`, `autoCorrect = true`.
- Ktlint : `ignoreFailures = true` (donc non bloquant).

---

## 12. Sensitive configuration files

### 12.1 DataStore / preferences

- `core/datastore/src/main/java/.../SettingsDataStore.kt` — DataStore Preferences (non chiffré), clés listées dans ARCHITECTURE.md §7 (thème, sort library, timestamps sync, etc.)
- `core/datastore/src/main/java/.../SecurePreferencesManager.kt` — EncryptedSharedPreferences (AES-256-GCM, Android Keystore MasterKey) pour `plexToken`, `clientId`, `tmdbApiKey`, `omdbApiKey`, tokens Jellyfin → synchronisé avec blocs `synchronized`, migration automatique depuis DataStore au premier lancement (cf ARCHITECTURE.md §7).

### 12.2 Secrets / API keys

| Secret | Source | Backend en production |
|---|---|---|
| `PLEX_TOKEN` | `local.properties` → `BuildConfig.PLEX_TOKEN` en debug ; vide en release | `SecurePreferencesManager.plexToken` |
| `TMDB_API_KEY` | `local.properties` → `BuildConfig.TMDB_API_KEY` en debug ; vide en release | `SecurePreferencesManager.tmdbApiKey` |
| `OMDB_API_KEY` | `local.properties` → `BuildConfig.OMDB_API_KEY` en debug ; vide en release | `SecurePreferencesManager.omdbApiKey` |
| `IPTV_PLAYLIST_URL` | `local.properties` → `BuildConfig.IPTV_PLAYLIST_URL` en debug ; vide en release | `SettingsDataStore.iptvPlaylistUrl` |
| Keystore passwords | `keystore/keystore.properties` | — |
| Clé IP locale backend | `http://192.168.0.175:8186/` **en dur** dans `app/build.gradle.kts` ligne 53 | — |

- `local.properties` : **présent à la racine** (`c:/.../PlexHubTV/local.properties`). Typiquement gitignored, à confirmer.
- `keystore/keystore.properties` : **présent** dans `keystore/`. **À vérifier par Security Agent**.
- `app/google-services.json` : **présent** (configuration Firebase, considérée semi-sensible par Google).

### 12.3 Network security

- `app/src/main/res/xml/network_security_config.xml` — trust autosigné LAN → **échantillonné**
- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` — rules transfert (`allowBackup="false"`) → **échantillonné**

### 12.4 Manifest sensible

- `android:allowBackup="false"` ✓
- `android:largeHeap="true"` ✓
- `REQUEST_INSTALL_PACKAGES` permission (pour les updates in-app via `ApkInstaller`)
- Deep link `plexhub://play` exposé (exported=true MainActivity)

---

## 13. Localization

- `app/src/main/res/values/strings.xml` (défaut, anglais présumé)
- `app/src/main/res/values-fr/strings.xml` (français)
- Les deux sont modifiés sur la branche courante (git status). Aucune autre langue → **deux locales seulement**.

---

## 14. Test inventory

### 14.1 Unit tests (src/test)

| Module | Fichiers de tests | Classes testées |
|---|---|---|
| `:app/src/test` | 18 | `MainViewModelTest`, `handler/GlobalCoroutineExceptionHandlerTest`, `feature/appprofile/AppProfileViewModelTest`, `feature/details/MediaDetailViewModelTest`, `feature/home/HomeViewModelTest`, `feature/library/LibraryViewModelTest`, `feature/loading/SyncStatusModelTest`, `feature/player/{PlayerControlViewModelTest, TrackSelectionViewModelTest, PlaybackStatsViewModelTest}`, `feature/player/controller/{ChapterMarkerManagerTest, PlayerScrobblerTest, PlayerStatsTrackerTest, PlayerTrackControllerTest}`, `feature/playlist/{PlaylistDetailViewModelTest, PlaylistListViewModelTest}`, `feature/plexhome/PlexHomeSwitcherViewModelTest`, `feature/search/SearchViewModelTest` |
| `:core:common/src/test` | 1 | `StringNormalizerTest` |
| `:core:model/src/test` | 2 | `AppErrorTest`, `UnificationIdTest` |
| `:core:network/src/test` | 4 | `auth/AuthEventBusTest`, `AuthInterceptorTest`, `ConnectionManagerTest`, `NetworkSecurityTest` |
| `:data/src/test` | 4 | `core/util/MediaUrlResolverTest`, `mapper/MediaMapperTest`, `repository/MediaLibraryQueryBuilderTest`, `repository/ProfileRepositoryImplTest` |
| `:domain/src/test` | 9 | `service/PlaybackManagerTest`, `usecase/{EnrichMediaItemUseCaseTest, FilterContentByAgeUseCaseTest, GetMediaDetailUseCaseTest, GetUnifiedHomeContentUseCaseTest, PrefetchNextEpisodeUseCaseTest, SearchAcrossServersUseCaseTest, SyncWatchlistUseCaseTest, ToggleFavoriteUseCaseTest}` |

**Total unit tests** : 38 fichiers (corrélé à ARCHITECTURE.md §19).

Modules sans test unitaire : `:core:database`, `:core:datastore`, `:core:designsystem`, `:core:ui`, `:core:navigation`.

### 14.2 Instrumented tests (src/androidTest)

| Module | Fichiers | Tests |
|---|---|---|
| `:app/src/androidTest` | 4 | `TestData.kt` (fixtures), `feature/favorites/FavoritesScreenTest`, `feature/home/HomeScreenTest`, `feature/search/SearchScreenTest` |

Seul `:app` a du code instrumenté (Compose UI test). Aucun test instrumenté pour le player, la DB Room, les workers.

### 14.3 Gaps connus (cf `docs/MISSING_TESTS.md`)

Le document `docs/MISSING_TESTS.md` est **obsolète** : il décrit un état historique où 11 fichiers manquaient. Le relevé actuel montre que la plupart des tests cités comme "manquants" existent (tous les ViewModel tests Player, `MediaDetailViewModelTest`, `HomeViewModelTest`, `LibraryViewModelTest`, `SearchViewModelTest`, `MediaMapperTest`, `PlayerScrobblerTest` sont présents). Manquent toujours : `MediaDetailRepositoryImplTest`, `PlaybackRepositoryImplTest`, `ContentRatingHelperTest`, `MediaDeduplicatorTest`. → **Test Agent devra rafraîchir `MISSING_TESTS.md`**.

---

## 15. Non-audited zones (declared)

Ces zones ont été volontairement **non analysées en profondeur** dans cette phase 0 :

- **Répertoires `build/`** de tous les modules (`app/build/`, `core/*/build/`, `domain/build/`, `data/build/`) — code généré Kotlin/KSP (`generated/ksp/`, `generated/source/kapt/`), classes compilées, artifacts AAR/APK, rapports Lint/Detekt → **ignorés** dans tous les `find` via `-not -path "*/build/*"`.
- **Code généré Hilt/Room** (dans `build/generated/source/ksp/…_HiltModules.java`, `_Impl.kt`, `_Factory.java`, etc.) → **ignoré**.
- **Ressources non-texte** : tous les drawables (`res/drawable/`), mipmaps (`res/mipmap-*`), fichiers binaires dans `res/raw/` (`intro.mp4`, `intro_sound.mp3`), logos (`plexhub_logo_final_large.png`) → **échantillonnés**.
- **Schemas Room JSON** : `core/database/schemas/com.chakir.plexhubtv.core.database.PlexDatabase/{11-47}.json` → **échantillonnés** (existence confirmée, contenu non ouvert).
- **Scripts PowerShell/Batch** à la racine : `gradlew.bat`, `run_ktlint.bat`, `move_domain_files.ps1` → **échantillonnés**.
- **Logs / dumps divers** à la racine : `build_error.txt`, `build_error_utf8.txt`, `coroutine_scopes.txt`, `get_calls.txt`, `post_calls.txt`, `global_scopes.txt`, `hs_err_pid*.log`, `replay_pid*.log`, `lint_report.txt`, `test_output.txt`, `nul` → **ignorés** (artefacts développeur, pas du code source).
- **Documents `docs/`** : le dossier `docs/` contient 30+ .md (audits antérieurs v1→v5, UX, performance, etc.), privacy policies HTML, dossiers `plans/`, `refonte/`, `screenshot/`. Seuls `ARCHITECTURE.md` + `MISSING_TESTS.md` ont été lus en profondeur. Le reste est **échantillonné**.
- **`docs/audit/`** : livrables des audits précédents (v3 et antérieurs). Non ouverts → **échantillonnés**.
- **Module `:core:datastore`** (4 fichiers) et `:core:designsystem` (4 fichiers), `:core:navigation` (2 fichiers) — existence énumérée, contenu **non ouvert** dans cette phase.
- **Fichiers `consumer-rules.pro` / `proguard-rules.pro`** des modules `core/*` → **échantillonnés**.
- **`app/src/main/res/xml/network_security_config.xml`** — rules trust SSL LAN → **à lire par Security Agent**.
- **Intégration complète du player Media3 FFmpeg decoder et des device profiles HDR/DoVi** → **à valider sur device**.
- **Ressources de test (`res/layout/`)** : le dossier existe, non exploré → **échantillonné**.

---

## 16. Pitfall map (points d'attention connus)

Re-surfacés depuis `ARCHITECTURE.md`, `MEMORY.md` (mémoire projet), et commentaires de code croisés.

### 16.1 Room `@RawQuery` trap (MediaLibraryQueryBuilder)
- **Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilder.kt`
- **Problème historique** : `SELECT media.*` + alias calculés avec noms identiques à des colonnes → Room prend la colonne de table en priorité.
- **Fix en place** : listes de colonnes explicites `UNIFIED_SELECT` / `NON_UNIFIED_SELECT` + GROUP_CONCAT aliases. Toute nouvelle colonne ajoutée à `MediaEntity` DOIT être ajoutée dans ces constantes (trap documenté dans MEMORY.md : `historyGroupKey` a causé un `LoadState.Error` silencieux).
- **Corollaires** :
  - Correlated MAX sur `(ratingKey, serverId)` doit être combiné pour éviter cross-server mismatches.
  - `thumbUrl`, `artUrl`, `resolvedThumbUrl` doivent venir de la même "winning row" via `bestRowField()` (sortKey + CHAR(31) marker + INSTR/SUBSTR).
  - `NON_UNIFIED_SELECT` ne doit PAS référencer `metadata_score` ni `bridgedImdbId` (uniquement dans la sous-requête unifiée).
  - `WHERE type = ?` obligatoire pour les non-unified.
  - GROUP BY inclut `media.type` comme safety net contre l'agrégation cross-type.

### 16.2 `pageOffset UNIQUE INDEX` (MediaEntity)
- **Index unique** : `(serverId, librarySectionId, filter, sortOrder, pageOffset)`.
- **Trap** : tout nouveau `MediaSourceHandler`/mapper DOIT définir `pageOffset` unique par item, sinon `INSERT OR REPLACE` supprime silencieusement les rows précédents (seul le dernier item survit).
- **Plex sync** : `pageOffset = start + index`.
- **Jellyfin sync** : DOIT faire de même → **à vérifier dans `JellyfinSourceHandler.kt` et `SyncJellyfinLibraryUseCase`** (fichier modifié sur la branche courante).

### 16.3 Enrichissement double-path (MediaDetail + SeasonDetail)
- Épisodes : enrichment doit se faire dans `MediaDetailViewModel.PlayClicked` **ET** `SeasonDetailViewModel.PlayEpisode`. Ne pas oublier l'un des deux.
- `isPlayButtonLoading` doit rester liée à `isEnriching`.
- Remote server episodes peuvent ne pas être en Room → le fallback réseau dans `EnrichMediaItemUseCase` doit fonctionner (matching `grandparentTitle + parentTitle + index`).
- `mapDtoToDomain` DOIT calculer `unificationId` sinon l'enrichment tape toujours la voie réseau (500-2000 ms au lieu de ~5 ms).

### 16.4 Session expiration (SEC4)
- `AuthInterceptor` émet `AuthEventBus.TokenInvalid` **uniquement** sur 401 depuis `plex.tv` (pas sur les 401 serveurs locaux, pour éviter les faux-positifs).
- Pas de retry automatique (évite les boucles infinies).
- `MainViewModel` déduplique les dialogs via un flag interne.
- Navigation auth avec `popUpTo(0)` pour clear back stack.

### 16.5 Double système de profils
- **Plex Home** (`feature/plexhome/`) : switch côté serveur, DB purgée, re-auth requise. Endpoint `/api/v2/home/users` (et non `/api/home/users` qui renvoie du XML). Réponse wrappée dans `MediaContainer.User[]` → `PlexHomeUsersResponse` DTO.
- **App Profiles** (`feature/appprofile/`) : profils locaux Room (`ProfileEntity.isActive`), CRUD complet, emoji picker, kids toggle. `LoadingViewModel.navigateAfterSync()` montre la sélection si ≥ 2 profils.
- **TODO documentés** : tests PIN Plex Home, application du kids mode au contenu (filter use case existe mais pas encore câblé).

### 16.6 Divergences doc ↔ code relevées

- **DB version** : `ARCHITECTURE.md` dit v46, le code `PlexDatabase.kt` et `DatabaseModule.kt` disent **v47** (migration 46→47 ajoutée).
- **Commentaire "No ABI splits (x86 excluded)"** : `app/build.gradle.kts` ligne 131 contredit la ligne 31 qui inclut `x86` et `x86_64` dans `abiFilters`.
- **versionCode=1** pour versionName 1.0.16 : incohérent pour une release (Play Store rejette versionCode non incrémenté).
- **Release `API_BASE_URL` = `https://plex.tv/`** : valeur du buildConfigField pour le Backend propriétaire en release, alors que la vraie base Backend est définie ailleurs (probablement dans `BackendApiClient` via config runtime) → **à valider**.
- **Turbine** mentionné dans ARCHITECTURE.md §19 mais absent de `libs.versions.toml` → si utilisé, dépendance manquante ou présente dans un `build.gradle.kts` de module non relevé ici → **non vérifié**.
- **`MISSING_TESTS.md` obsolète** : décrit 11 tests "supprimés" dont la plupart existent à nouveau.

### 16.7 Placement atypique

- `data/src/main/java/com/chakir/plexhubtv/core/util/MediaUrlResolver.kt` : utilise le package `core.util` mais vit dans le module `:data`. Son test `MediaUrlResolverTest.kt` est aussi dans `:data/src/test/java/.../core/util/`. Placement inhabituel (package ≠ module) → **à noter par Architecture Agent**.
- `app/src/main/java/com/chakir/plexhubtv/domain/usecase/GetSuggestionsUseCase.kt` : use case dans `:app` au lieu de `:domain`. Seul cas → **à noter**.

### 16.8 Debug / observabilité

- `leakcanary-android:2.14` en `debugImplementation` → actif en debug.
- `debug` flavor désactive Crashlytics/Analytics/Perf collection (`setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)`).
- `Timber` en debug via `DebugTree`, en release via `CrashReportingTree` (`app/handler/CrashReportingTree.kt`).
- `GlobalCoroutineExceptionHandler.kt` (`app/handler/`) — handler global pour les exceptions non capturées dans les coroutines.
- `SettingsDataStore.cachedConnections` — pas chiffré mais contient des URLs serveurs (IP LAN), potentiellement sensible → **à évaluer par Security Agent**.

---

## 17. Diagramme rapide d'entrée → lecture (pour orientation des agents aval)

```
Launcher intent → MainActivity → PlexHubApplication.onCreate (Hilt ready)
                                    │
                      parallel warm-up (DataStore, OkHttp, Coil, WorkerFactory, ConnectionManager)
                                    │
                              setupBackgroundSync (WorkManager UniquePeriodicWork x4)
                                    │
          NavHost: Splash → Login (PIN Plex) → LibrarySelection → Loading → AppProfileSelection → Main
                                    │
                  Main: sidebar (Home/Library/Search/Favorites/History/Downloads/Playlists/Settings/Iptv)
                                    │
                       MediaDetail → (PreparePlaybackUseCase) → EnrichMediaItemUseCase (Room-first, network fallback)
                                    │
                                  PlaybackManager.play → PlayerController (@Singleton)
                                    │
                    ExoPlayer (primary, Media3 1.5.1 + FFmpeg decoder + ASS) OR MpvPlayerWrapper (fallback)
                                    │
                          Scrobbling via PlayerScrobbler → Plex/Jellyfin PlaybackReporter
```

---

## 18. Fichiers de référence à fournir aux agents aval

Liste des fichiers les plus critiques pour les phases suivantes (chemins absolus Windows) :

**Phase Stability / Security** :
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\PlexHubApplication.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\MainActivity.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\MainViewModel.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\AndroidManifest.xml`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\res\xml\network_security_config.xml`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\network\src\main\java\com\chakir\plexhubtv\core\network\NetworkModule.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\network\src\main\java\com\chakir\plexhubtv\core\network\AuthInterceptor.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\network\src\main\java\com\chakir\plexhubtv\core\network\auth\AuthEventBus.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\datastore\src\main\java\com\chakir\plexhubtv\core\datastore\SettingsDataStore.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\datastore\src\main\java\com\chakir\plexhubtv\core\datastore\SecurePreferencesManager.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\handler\CrashReportingTree.kt` (chemin logique)
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\handler\GlobalCoroutineExceptionHandler.kt` (chemin logique)
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\keystore\keystore.properties` (si gitté)
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\google-services.json`

**Phase Performance** :
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\data\src\main\java\com\chakir\plexhubtv\data\repository\MediaLibraryQueryBuilder.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\data\src\main\java\com\chakir\plexhubtv\data\paging\MediaRemoteMediator.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\database\src\main\java\com\chakir\plexhubtv\core\database\MediaDao.kt` (54 @Query)
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\database\src\main\java\com\chakir\plexhubtv\core\database\PlexDatabase.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\database\src\main\java\com\chakir\plexhubtv\core\database\DatabaseModule.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\di\image\ImageModule.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\di\image\ImagePrefetchManager.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\work\LibrarySyncWorker.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\work\UnifiedRebuildWorker.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\network\src\main\java\com\chakir\plexhubtv\core\network\ConnectionManager.kt`

**Phase Architecture** :
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\settings.gradle.kts`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\build.gradle.kts`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\build.gradle.kts`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\gradle\libs.versions.toml`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\domain\src\main\java\com\chakir\plexhubtv\domain\usecase\EnrichMediaItemUseCase.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\domain\src\main\java\com\chakir\plexhubtv\domain\usecase\PreparePlaybackUseCase.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\domain\src\main\java\com\chakir\plexhubtv\domain\source\MediaSourceHandler.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\data\src\main\java\com\chakir\plexhubtv\data\di\RepositoryModule.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\data\src\main\java\com\chakir\plexhubtv\data\source\MediaSourceResolver.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\common\BaseViewModel.kt`

**Phase Player / UX** :
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\player\controller\PlayerController.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\player\PlayerFactory.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\player\mpv\MpvPlayerWrapper.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\player\profile\DeviceProfileService.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\feature\player\url\PlaybackUrlBuilder.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\core\designsystem\src\main\java\com\chakir\plexhubtv\core\designsystem\Theme.kt`

**Phase Release** :
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\proguard-rules.pro`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\keystore\plexhubtv-release.jks`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\config\detekt\detekt.yml`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\core\update\UpdateChecker.kt`
- `c:\Users\chakir\AndroidStudioProjects\PlexHubTV\app\src\main\java\com\chakir\plexhubtv\core\update\ApkInstaller.kt`

---

**Fin du livrable Phase 0.** Toutes les phases suivantes peuvent consommer ce document comme carte de référence. Pour tout élément marqué "→ non vérifié", "→ à valider sur device" ou "→ échantillonné", l'agent concerné devra ouvrir le fichier ciblé en profondeur avant de conclure.
