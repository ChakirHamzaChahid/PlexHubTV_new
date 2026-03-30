# Architecture PlexHubTV

> **Date** : 23 mars 2026
> **Version** : 3.0
> **Architecture** : Clean Architecture Multi-Modules
> **Plateforme** : Android TV (Jetpack Compose)

---

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Architecture en couches](#2-architecture-en-couches)
3. [Modules et dépendances](#3-modules-et-dépendances)
4. [Injection de dépendances (Hilt)](#4-injection-de-dépendances-hilt)
5. [Base de données (Room)](#5-base-de-données-room)
6. [Couche réseau](#6-couche-réseau)
7. [Stockage local](#7-stockage-local)
8. [Navigation](#8-navigation)
9. [Ecrans et Features](#9-ecrans-et-features)
10. [Player](#10-player)
11. [Gestion d'état](#11-gestion-détat)
12. [Stratégie Offline-First](#12-stratégie-offline-first)
13. [Enrichissement multi-serveur](#13-enrichissement-multi-serveur)
14. [Système d'erreurs](#14-système-derreurs)
15. [Thèmes](#15-thèmes)
16. [Workers](#16-workers)
17. [Intégration Jellyfin](#17-intégration-jellyfin)
18. [Gestion expiration de session](#18-gestion-expiration-de-session)
19. [Tests](#19-tests)

---

## 1. Vue d'ensemble

PlexHubTV est un client Android TV pour Plex Media Server et Jellyfin, construit en Kotlin avec Jetpack Compose. L'application offre une interface inspirée de Netflix, optimisée pour la navigation D-Pad, avec support multi-serveur (Plex, Jellyfin, Xtream, Backend), lecture offline et enrichissement croisé des métadonnées.

### Stack technique

| Catégorie | Technologies |
|-----------|-------------|
| Langage | Kotlin |
| UI | Jetpack Compose for TV, Material3 |
| Architecture | Clean Architecture, MVVM + MVI |
| DI | Hilt (Dagger) |
| Base de données | Room (v46), WAL mode, FTS |
| Réseau | Retrofit, OkHttp, Gson, kotlinx-serialization |
| Player | ExoPlayer (primaire) + MPV (fallback) |
| Images | Coil (cache adaptatif RAM) |
| Background | WorkManager |
| Préférences | DataStore + EncryptedSharedPreferences |
| Pagination | Paging 3 |
| Monitoring | Firebase Crashlytics + Performance |
| Tests | JUnit, MockK, Truth, Turbine |

### Diagramme des modules

```
┌─────────────────────────────────────────────────┐
│                     :app                         │
│   (Presentation: UI, ViewModels, DI, Workers)    │
└────────┬───────────────────┬────────────────────┘
         │                   │
         v                   v
┌────────────────┐  ┌────────────────┐
│    :domain     │  │     :data      │
│  (Use Cases,   │  │ (Repositories, │
│   Interfaces)  │  │   Mappers,     │
│                │  │   Paging)      │
└───────┬────────┘  └───────┬────────┘
        │                   │
        v                   v
┌─────────────────────────────────────────────────┐
│                    :core                         │
│  ┌────────┐ ┌────────┐ ┌──────────┐ ┌────────┐ │
│  │ model  │ │network │ │ database │ │datastore│ │
│  └────────┘ └────────┘ └──────────┘ └────────┘ │
│  ┌────────┐ ┌────────┐ ┌──────────┐ ┌────────┐ │
│  │ common │ │   ui   │ │designsys.│ │  nav.  │ │
│  └────────┘ └────────┘ └──────────┘ └────────┘ │
└─────────────────────────────────────────────────┘
```

---

## 2. Architecture en couches

L'application suit une Clean Architecture stricte à 4 couches :

```
Presentation (:app)  ──>  Domain (:domain)  <──  Data (:data)
                                 │
                           Core (:core:*)
```

### Presentation (`:app`)

- Compose UI + ViewModels (35 ViewModels dont `BaseViewModel` abstract)
- Navigation Compose
- Workers (WorkManager) — 6 workers
- Modules DI Hilt dans `app/di/`

### Domain (`:domain`)

- Use Cases (29 use cases, logique métier pure)
- Interfaces Repository (25 contrats)
- Services (PlaybackManager)
- Aucune dépendance Android Framework

### Data (`:data`)

- Implémentations Repository (24 implémentations)
- Mappers (6 : MediaMapper, BackendMediaMapper, XtreamMediaMapper, JellyfinMapper, ServerMapper, UserMapper)
- Paging 3 (MediaRemoteMediator)
- Agrégation multi-serveur (MediaDeduplicator)
- Construction SQL dynamique (MediaLibraryQueryBuilder)

### Core (`:core:*`)

- Code partagé entre toutes les couches
- 8 sous-modules indépendants
- Aucune dépendance vers `:app`, `:domain` ou `:data`

**Regle d'or** : les dépendances vont toujours vers le bas, jamais de dépendance inverse.

---

## 3. Modules et dépendances

| Module | Role | Dépend de |
|--------|------|-----------|
| `:app` | UI, ViewModels, DI, Workers | `:domain`, `:data`, tous les `:core:*` |
| `:domain` | Use Cases, interfaces Repository | `:core:model`, `:core:common` |
| `:data` | Implémentations Repository, Mappers | `:domain`, `:core:model`, `:core:common`, `:core:network`, `:core:database`, `:core:datastore` |
| `:core:model` | Entités métier (MediaItem, Server, etc.) | Kotlin Serialization |
| `:core:common` | Utilitaires, extensions, dispatchers | Coroutines |
| `:core:network` | Retrofit services, API clients, AuthEventBus | `:core:model`, Retrofit, OkHttp |
| `:core:database` | Room DB, DAOs, entités, migrations | `:core:model`, Room |
| `:core:datastore` | DataStore, EncryptedSharedPreferences | DataStore, Security Crypto |
| `:core:designsystem` | Thèmes Material3 | Compose |
| `:core:ui` | Composants UI réutilisables (Netflix-like) | `:core:model`, `:core:designsystem`, Compose |
| `:core:navigation` | Routes minimales pour NavigationItem | Compose Navigation |

### Structure `app/di/`

Le dossier `app/di/` contient uniquement du câblage Hilt (anciennement `app/core/`) :

| Package | Contenu |
|---------|---------|
| `app/di/navigation/` | `Screen` sealed class (routes complètes avec paramètres) |
| `app/di/image/` | `ImageModule`, `ImagePrefetchManager`, `PlexImageKeyer` |
| `app/di/network/` | `ConnectionManager`, `ServerClientResolver` |
| `app/di/datastore/` | Extensions DataStore |

---

## 4. Injection de dépendances (Hilt)

### Modules DI

| Module | Emplacement | Composant | Fournit |
|--------|-------------|-----------|---------|
| `DatabaseModule` | `core:database` | SingletonComponent | PlexDatabase, 21 DAOs, 35 Migrations |
| `NetworkModule` | `core:network` | SingletonComponent | OkHttpClient (trust SSL local), Retrofit, PlexApiService, TmdbApiService, OmdbApiService, AuthInterceptor, PlexCacheInterceptor |
| `JellyfinNetworkModule` | `core:network` | SingletonComponent | JellyfinApiService, JellyfinClient, JellyfinConnectionTester, JellyfinImageInterceptor |
| `RepositoryModule` | `data:di` | SingletonComponent | 26 @Binds (Auth, Media, Library, Search, Playback, OnDeck, Hubs, Favorites, Watchlist, Sync, Downloads, Collection, History, Iptv, Profile, Jellyfin, Playlist, PersonFavorite, Backend, Xtream...) |
| `DataStoreModule` | `core:datastore` | SingletonComponent | `DataStore<Preferences>` |
| `CoroutineModule` | `core:common` | SingletonComponent | `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`, `@ApplicationScope` |
| `WorkModule` | `core:common` | SingletonComponent | WorkManager |
| `PlayerModule` | `app:di` | SingletonComponent | PlayerFactory (ExoPlayerFactory) |
| `ImageModule` | `app:di` | SingletonComponent | Coil ImageLoader (cache adaptatif 10-15% RAM), PlexImageKeyer |
| `AnalyticsModule` | `app:di` | SingletonComponent | AnalyticsService (abstraction Firebase Analytics) |

### Qualifiers personnalisés

```kotlin
@IoDispatcher      // Dispatchers.IO
@DefaultDispatcher // Dispatchers.Default
@MainDispatcher    // Dispatchers.Main
@ApplicationScope  // CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

### Pattern Repository Binding

```kotlin
// data/di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuthRepo(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindMediaRepo(impl: MediaRepositoryImpl): MediaRepository
    // ... 26 bindings total
}
```

---

## 5. Base de données (Room)

### PlexDatabase (Version 46)

- Fichier : `core/database/src/.../PlexDatabase.kt`
- Mode WAL (Write-Ahead Logging) activé
- `PRAGMA synchronous = NORMAL`
- `PRAGMA cache_size = -8000` (8 Mo)
- Full-Text Search via `MediaFts` (table FTS4)
- Vue matérialisée `MediaUnifiedEntity` pour le browsing unifié

### Entités (24)

| Entité | Description |
|--------|-------------|
| `MediaEntity` | Films, séries, saisons, épisodes |
| `ServerEntity` | Serveurs Plex avec candidats de connexion |
| `DownloadEntity` | Métadonnées de contenu téléchargé |
| `ApiCacheEntity` | Cache des réponses API |
| `OfflineWatchProgressEntity` | Progression de lecture hors-ligne |
| `HomeContentEntity` | Cache du contenu écran d'accueil |
| `FavoriteEntity` | Favoris utilisateur |
| `RemoteKey` | Clés de pagination Paging 3 |
| `LibrarySectionEntity` | Métadonnées des bibliothèques |
| `TrackPreferenceEntity` | Préférences audio/sous-titres par média |
| `CollectionEntity` | Collections Plex |
| `MediaCollectionCrossRef` | Relation many-to-many (média <-> collection) |
| `ProfileEntity` | Profils multi-utilisateur locaux |
| `SearchCacheEntity` | Cache des résultats de recherche |
| `MediaFts` | Index de recherche plein texte (FTS4) |
| `XtreamAccountEntity` | Comptes streaming Xtream |
| `BackendServerEntity` | Configuration serveurs backend |
| `IdBridgeEntity` | Mapping IMDB/TMDB IDs cross-serveur |
| `MediaUnifiedEntity` | Vue matérialisée pour browsing unifié |
| `PersonFavoriteEntity` | Acteurs/réalisateurs favoris |
| `PlaylistEntity` | Playlists utilisateur |
| `PlaylistItemEntity` | Items de playlist |
| `WatchlistEntity` | Watchlist cloud Plex |
| `JellyfinServerEntity` | Serveurs Jellyfin (credentials chiffrés) |

### DAOs (21)

MediaDao, ServerDao, DownloadDao, ApiCacheDao, OfflineWatchProgressDao, HomeContentDao, FavoriteDao, RemoteKeysDao, LibrarySectionDao, TrackPreferenceDao, CollectionDao, ProfileDao, SearchCacheDao, XtreamAccountDao, BackendServerDao, IdBridgeDao, MediaUnifiedDao, PersonFavoriteDao, PlaylistDao, WatchlistDao, JellyfinServerDao

### Migrations (v11 -> v46)

| Migration | Description |
|-----------|-------------|
| 11 -> 12 | Index sur guid, type, imdbId, tmdbId |
| 15 -> 16 | Table `track_preferences` |
| 18 -> 19 | Clé composite (id, serverId) pour collections |
| 19 -> 20 | Colonnes resolvedThumbUrl, resolvedArtUrl, resolvedBaseUrl + index composite |
| 20 -> 21 | Colonne titleSortable (tri locale-aware) |
| 21 -> 22 | Colonne scrapedRating (notes TMDb/OMDb) |
| 22 -> 23 | Table `profiles` + profil par défaut |
| 23 -> 24 | Table `search_cache` |
| 24 -> 25 | Colonnes relay/publicAddress/httpsRequired/connectionCandidatesJson sur ServerEntity |
| 25 -> 26 | Suppression serveurs obsolètes (force re-fetch) |
| 26 -> 27 | Colonne parentIndex (numéro de saison pour épisodes) |
| 27 -> 28 | Colonne alternativeThumbUrls (images fallback multi-serveur) |
| 28 -> 29 | Colonne displayRating (note canonique pré-calculée) |
| 29 -> 30 | Colonne historyGroupKey (groupement historique lecture) |
| 30 -> 31 | Table `media_fts` (recherche plein texte) + triggers sync |
| 31 -> 32 | Corrections/index mineurs |
| 32 -> 33 | Table `xtream_accounts` (intégration Xtream) |
| 33 -> 34 | Table `backend_servers` + colonne sourceServerId |
| 34 -> 35 | Table `id_bridge` (mapping IMDB/TMDB cross-serveur) |
| 35 -> 36 | Index de performance supplémentaires |
| 36 -> 37 | Colonne metadataScore (score complétude métadonnées) |
| 37 -> 38 | Colonne isOwned (suivi serveurs possédés) |
| 38 -> 39 | Index de performance additionnels |
| 39 -> 40 | Colonne groupKey + table `media_unified` (vue matérialisée) |
| 40 -> 41 | Table `person_favorites` (acteurs/réalisateurs favoris) |
| 41 -> 42 | Tables `playlists` + `playlist_items` |
| 42 -> 43 | Colonnes isHidden/hiddenAt (masquage soft-delete depuis hubs) |
| 43 -> 44 | Colonnes overriddenSummary/overriddenThumbUrl (refresh TMDB manuel) |
| 44 -> 45 | Table `watchlist` (watchlist cloud) |
| 45 -> 46 | Table `jellyfin_servers` (intégration Jellyfin) |

### MediaLibraryQueryBuilder

Classe dédiée (`data/repository/MediaLibraryQueryBuilder.kt`) qui construit les requêtes SQL dynamiques pour le browsing bibliothèque. Utilise des **listes de colonnes explicites** pour éviter le piège Room `@RawQuery` (voir MEMORY.md).

Constantes clés :
- `UNIFIED_SELECT` / `NON_UNIFIED_SELECT` — colonnes explicites par type de requête
- `UNIFIED_GROUP_BY` / `NON_UNIFIED_GROUP_BY` — GROUP BY pour agrégation multi-source
- Méthodes : `buildPagedQuery()`, `buildCountQuery()`, `buildIndexQuery()`

**Point important** : `MediaEntity` stocke maintenant `parentIndex` (ajouté en v27) pour le matching épisodes par saison.

---

## 6. Couche réseau

### Services API (4 interfaces Retrofit)

#### PlexApiService (`core:network`)

URL de base dynamique : `plex.tv` pour l'auth, URL serveur pour les médias.

| Catégorie | Endpoints |
|-----------|-----------|
| Auth | `POST /api/v2/pins`, `GET /api/v2/pins/{id}`, `GET /api/v2/user` |
| Ressources | `GET /api/v2/resources` (découverte serveurs) |
| Watchlist | `GET /library/sections/watchlist/all`, `PUT /actions/addToWatchlist`, `DELETE /actions/removeFromWatchlist` |
| Média | `GET /hubs/search`, `GET /library/metadata/{key}`, `GET /library/sections/{id}/all` |
| Collections | `GET /library/sections/{id}/collections`, `GET /library/collections/{id}/children` |
| Playback | `GET /:/timeline`, `GET /:/scrobble`, `GET /:/unscrobble`, `PUT /:/prefs` |
| Plex Home | `GET /api/v2/home/users`, `POST /api/home/users/{uuid}/switch` |

Utilise `@Url` dynamique pour les appels multi-serveur.

#### TmdbApiService (`core:network`)

- Base : `https://api.themoviedb.org/`
- `GET /3/tv/{tv_id}` — Notes des séries (vote_average)
- Supporte le paramètre `language` (FR/EN via `metadataLanguage` setting)

#### OmdbApiService (`core:network`)

- Base : `https://www.omdbapi.com/`
- `GET /?i={imdbId}` — Notes IMDb (imdbRating)

#### JellyfinApiService (`core:network`)

- Base : URL dynamique par serveur Jellyfin
- Auth : `POST /Users/AuthenticateByName`
- Bibliothèques : `GET /Users/{userId}/Views`, `GET /Users/{userId}/Items`
- Détails : `GET /Users/{userId}/Items/{itemId}`, saisons, épisodes, similar
- Recherche : `GET /Items` avec paramètre `searchTerm`
- Playback : `POST /Sessions/Playing`, `POST /Sessions/Playing/Progress`, `POST /Sessions/Playing/Stopped`

### Gestion des connexions

- `ConnectionManager` : teste les candidats de connexion en parallèle (race), sélectionne la meilleure URL
- `ServerClientResolver` : résout les connexions serveur via `ConnectionManager.findBestConnection`
- `JellyfinConnectionTester` : valide la connectivité serveur Jellyfin via `/System/Info/Public`
- SSL trust pour les IP locales uniquement (certificats auto-signés sur LAN)
- `AuthInterceptor` : injecte le token Plex dans chaque requête, détecte les 401 (plex.tv uniquement)
- `JellyfinImageInterceptor` : injecte le header Authorization pour les images Jellyfin
- `PlexCacheInterceptor` : cache HTTP 5min pour les endpoints stables

---

## 7. Stockage local

### SettingsDataStore (`core:datastore`)

Deux backends de stockage :

| Type | Backend | Données |
|------|---------|---------|
| Non-sensible | `DataStore<Preferences>` | Thème, qualité vidéo, player engine, timestamps sync, préférences UI |
| Sensible | `SecurePreferencesManager` | Token Plex, Client ID, clés API TMDb/OMDb, tokens Jellyfin |

### Clés principales

| Catégorie | Clés | Chiffré |
|-----------|------|---------|
| Auth | `plexToken`, `clientId` | Oui |
| Utilisateur | `currentUserUuid`, `currentUserName` | Non |
| UI | `showHeroSection`, `episodePosterMode`, `appTheme` | Non |
| Player | `videoQuality`, `playerEngine` (ExoPlayer/MPV) | Non |
| Sync | `lastSyncTime`, `isFirstSyncComplete`, `excludedServerIds` | Non |
| Pistes | `preferredAudioLanguage`, `preferredSubtitleLanguage` | Non |
| API | `tmdbApiKey`, `omdbApiKey` | Oui |
| IPTV | `iptvPlaylistUrl` | Non |
| Cache | `cachedConnections` (serverId -> baseUrl) | Non |
| Métadonnées | `metadataLanguage` (FR/EN, défaut: "fr") | Non |
| Écran de veille | `screensaverEnabled`, `screensaverIntervalSeconds`, `screensaverShowClock` | Non |
| Bibliothèque | `librarySort`, `librarySortDescending`, `libraryGenre`, `libraryServerFilter` | Non |

### SecurePreferencesManager

- Chiffrement AES-256-GCM via Android Keystore (MasterKey)
- Thread-safe avec blocs `synchronized`
- Migration automatique depuis DataStore vers EncryptedSharedPreferences au premier lancement

---

## 8. Navigation

### Deux définitions de Screen

| Fichier | Rôle |
|---------|------|
| `app/di/navigation/Screen.kt` | Routes complètes avec paramètres (utilisé par l'app) |
| `core/navigation/Screen.kt` | Routes minimales (utilisé par NavigationItem dans core) |

### Graphes de navigation

#### Auth Graph

```
Splash -> Login -> PinInput -> PlexHomeSwitch -> LibrarySelection -> Loading -> Main
```

#### Main Graph (sidebar)

```
Main
├── Home
├── Movies
├── TVShows
├── Search
├── Downloads
├── Favorites
├── History
├── Playlists
├── Settings -> Debug, ServerStatus, SubtitleStyle, SettingsCategory
└── Iptv
```

#### Media Graph (routes paramétrées)

| Route | Paramètres |
|-------|-----------|
| `media_detail/{ratingKey}?serverId={serverId}` | ratingKey, serverId |
| `season_detail/{ratingKey}?serverId={serverId}` | ratingKey, serverId |
| `collection_detail/{collectionId}?serverId={serverId}` | collectionId, serverId |
| `playlist_detail/{playlistId}?serverId={serverId}` | playlistId, serverId |
| `person_detail/{personName}` | personName (URL-encoded) |
| `video_player/{ratingKey}?serverId={serverId}&startOffset=...&url=...&title=...` | ratingKey, serverId, startOffset, url, title |

#### Setup Graph

| Route | Description |
|-------|-------------|
| `xtream_setup` | Configuration compte Xtream |
| `jellyfin_setup` | Configuration serveur Jellyfin |
| `xtream_category_selection/{accountId}` | Sélection catégories Xtream |
| `app_profile_selection` | Sélection profil au démarrage |
| `app_profile_switch` | Changement de profil |

### NavigationItem (`core:navigation`)

Sealed class associant chaque `Screen` a un label et une icone pour la sidebar TV :

```
Home, Movies, TVShows, Search, Downloads, Favorites, History, Playlists, Settings, Iptv (Live TV)
```

---

## 9. Ecrans et Features

### BaseViewModel

Classe abstraite avec error channel unifié, héritée par la majorité des ViewModels :

```kotlin
abstract class BaseViewModel : ViewModel() {
    private val _errorEvents = Channel<AppError>()
    val errorEvents = _errorEvents.receiveAsFlow()
    protected suspend fun emitError(error: AppError) = _errorEvents.send(error)
}
```

### ViewModels (35)

| ViewModel | Feature | Responsabilité |
|-----------|---------|----------------|
| `AuthViewModel` | auth | Flux d'auth PIN Plex, OAuth |
| `PlexHomeSwitcherViewModel` | plexhome | Sélection utilisateur Plex Home |
| `SplashViewModel` | splash | Auto-login, bypass auth |
| `LoadingViewModel` | loading | Suivi progression sync initiale |
| `MainViewModel` | main | État global app, navigation, coordination 401 |
| `HomeViewModel` | home | Contenu unifié (On Deck + Hubs), sync WorkManager, prefetch images |
| `LibraryViewModel` | library | Navigation bibliothèque avec Paging3, filtres, tri |
| `LibrarySelectionViewModel` | libraryselection | Sélection bibliothèques au premier lancement |
| `SearchViewModel` | search | Recherche globale multi-serveur, debounce 400ms |
| `MediaDetailViewModel` | details | Détails média, Smart Start, sélection source, favoris, statut lecture |
| `SeasonDetailViewModel` | details | Liste des épisodes par saison, lecture épisode |
| `PersonDetailViewModel` | details | Détails acteur/réalisateur, filmographie |
| `MediaEnrichmentViewModel` | details | Enrichissement en arrière-plan |
| `CollectionDetailViewModel` | collection | Contenu d'une collection |
| `HubViewModel` | hub | Contenu d'un hub (ex: "Reprendre la lecture") |
| `FavoritesViewModel` | favorites | Liste des favoris |
| `HistoryViewModel` | history | Historique de lecture |
| `DownloadsViewModel` | downloads | Contenu téléchargé (offline) |
| `PlaylistListViewModel` | playlist | Liste des playlists |
| `PlaylistDetailViewModel` | playlist | Contenu d'une playlist |
| `SettingsViewModel` | settings | Paramètres (thème, qualité, moteur player, langue métadonnées) |
| `ServerStatusViewModel` | settings | État des connexions serveur |
| `SubtitleStyleViewModel` | settings | Style sous-titres personnalisable |
| `DebugViewModel` | debug | Outils de debug (BUILD_TYPE only) |
| `AppProfileViewModel` | appprofile | Gestion profils utilisateur locaux (CRUD complet) |
| `IptvViewModel` | iptv | Parsing playlist M3U, lecture IPTV |
| `XtreamSetupViewModel` | xtream | Configuration compte Xtream |
| `XtreamCategorySelectionViewModel` | xtream | Sélection catégories Xtream |
| `JellyfinSetupViewModel` | jellyfin | Configuration serveur Jellyfin (auth + test connexion) |
| `ScreensaverViewModel` | screensaver | Écran de veille avec horloge et art média |
| `PlayerControlViewModel` | player | Contrôles UI player, overlay |
| `PlaybackStatsViewModel` | player | Stats temps-réel (bitrate, codec, buffer) |
| `TrackSelectionViewModel` | player | Sélection pistes audio/sous-titres |

### Deux chemins de lecture

1. **MediaDetailViewModel.PlayClicked** : films et séries (récupère l'épisode suivant via `GetNextEpisodeUseCase`)
2. **SeasonDetailViewModel.PlayEpisode** : épisodes depuis la liste de saison

Les deux chemins passent par `PreparePlaybackUseCase` (qui encapsule `EnrichMediaItemUseCase` + source selection + queue building) avant la lecture.

---

## 10. Player

### Architecture du player

```
┌───────────────────────────────────────────────┐
│              PlayerController                  │
│              (@Singleton)                      │
│                                                │
│  ┌──────────────┐  ┌──────────────────────┐   │
│  │ PlayerUiState│  │ Sous-contrôleurs (7) │   │
│  │ (StateFlow)  │  │                      │   │
│  └──────────────┘  │ - PlayerActionHandler │   │
│                    │ - PlayerInitializer   │   │
│                    │ - PlayerMediaLoader   │   │
│                    │ - PlayerPositionTracker│  │
│                    │ - PlayerScrobbler     │   │
│                    │ - PlayerTrackController│  │
│                    │ - ChapterMarkerManager│   │
│                    └──────────────────────┘   │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │  PlayerStatsTracker (stats temps-réel)   │ │
│  └──────────────────────────────────────────┘ │
└───────────────────────────────────────────────┘
         |                    |
    ┌────────────┐     ┌────────────┐
    │  ExoPlayer │     │    MPV     │
    │ (primaire) │     │ (fallback) │
    └────────────┘     └────────────┘
```

### 3 ViewModels Player

| ViewModel | Responsabilité |
|-----------|----------------|
| `PlayerControlViewModel` | Play/pause, seek, skip, chapitres, qualité, dialogues |
| `TrackSelectionViewModel` | Sélection pistes audio/sous-titres |
| `PlaybackStatsViewModel` | Overlay performances (toggle on/off) |

### Sous-contrôleurs (7)

| Contrôleur | Role |
|------------|------|
| `PlayerActionHandler` | Actions utilisateur (play, pause, seek, skip) |
| `PlayerInitializer` | Initialisation du player |
| `PlayerMediaLoader` | Résolution URL de stream |
| `PlayerPositionTracker` | Mise à jour position (intervalle 1s) |
| `PlayerScrobbler` | Mises à jour timeline Plex/Jellyfin, popup épisode suivant |
| `PlayerTrackController` | Gestion pistes audio/sous-titres |
| `ChapterMarkerManager` | Marqueurs intro/crédits (skip) |

### Moteurs de lecture

| Moteur | Rôle | Détails |
|--------|------|---------|
| ExoPlayer | Primaire | Décodeur FFmpeg Jellyfin, support sous-titres ASS |
| MPV | Fallback | Basculement automatique sur erreur MediaCodec |

`TranscodeUrlBuilder` construit les URL de lecture directe ou transcodée (Plex). `JellyfinUrlBuilder` construit les URL de lecture Jellyfin.

---

## 11. Gestion d'état

### Pattern StateFlow + Channel

```kotlin
// Pattern standard ViewModel (hérite de BaseViewModel)
class SomeViewModel @Inject constructor(...) : BaseViewModel() {
    // État UI réactif
    private val _uiState = MutableStateFlow(SomeUiState())
    val uiState: StateFlow<SomeUiState> = _uiState.asStateFlow()

    // Événements one-shot (navigation)
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    // Erreurs héritées de BaseViewModel : errorEvents

    // Actions MVI-like
    fun onEvent(event: SomeEvent) { ... }
}
```

### Conventions

- **StateFlow** pour l'état UI observable (recomposition Compose)
- **Channel** pour les événements one-shot (navigation, toasts, erreurs)
- **SavedStateHandle** pour la restauration d'état après process death
- Les ViewModels exposent des `fun onEvent(event)` ou `fun onAction(action)` (pattern MVI-like)
- **BaseViewModel** fournit `errorEvents` et `emitError()` pour un error handling unifié
- **@Immutable** + `ImmutableList` sur les data classes d'état UI critiques pour optimiser la recomposition Compose

### PlayerController (cas spécial)

Le `PlayerController` est `@Singleton` avec un `MutableStateFlow<PlayerUiState>` partagé entre les 3 ViewModels player. Mise à jour via :

```kotlin
fun updateState(transform: (PlayerUiState) -> PlayerUiState)
```

---

## 12. Stratégie Offline-First

### Principe

Room est la Single Source of Truth (SSOT). Les données sont d'abord lues depuis le cache local, puis rafraîchies depuis le réseau en arrière-plan.

### Flux de données

```
UI  <──  ViewModel  <──  UseCase  <──  Repository
                                          │
                                    ┌─────┴─────┐
                                    │            │
                                  Room       Retrofit
                                (priorité)   (fallback)
```

### Composants clés

| Composant | Rôle |
|-----------|------|
| `LibrarySyncWorker` | Sync périodique (6h) films/séries depuis tous les serveurs (Plex + Jellyfin) |
| `CollectionSyncWorker` | Sync collections après LibrarySync |
| `RatingSyncWorker` | Scraping notes TMDb/OMDb |
| `UnifiedRebuildWorker` | Reconstruction de la vue matérialisée `media_unified` |
| `CachePurgeWorker` | Nettoyage périodique du cache API expiré |
| `ChannelSyncWorker` | Sync des chaînes IPTV/Xtream |
| `MediaUnifiedEntity` | Vue matérialisée pour browsing unifié performant |
| `OfflineWatchProgressEntity` | Sauvegarde progression lecture hors-ligne |
| `ApiCacheEntity` | Cache réponses API avec TTL |

### Limites

- `LibrarySyncWorker` synchronise uniquement les films et séries depuis `/library/sections/{id}/all` (Plex) et `/Users/{id}/Items` (Jellyfin), **PAS** les saisons ni les épisodes
- Les épisodes sont chargés a la demande dans `SeasonDetailViewModel`
- Les saisons ne sont pas dans Room → elles viennent toujours du réseau

---

## 13. Enrichissement multi-serveur

### EnrichMediaItemUseCase

`@Singleton` avec cache `ConcurrentHashMap` en mémoire.

### Stratégie Room-first

```
1. Vérifier le cache en mémoire (~0ms)
2. Requête Room locale (~5ms)
3. Fallback réseau si non trouvé (500-2000ms)
```

### Stratégies de matching

| Type | Stratégie | Raison |
|------|-----------|--------|
| Films/Séries | `unificationId` (agrège imdb/tmdb/title+year) | Identifiant stable cross-serveur |
| Épisodes | `grandparentTitle + parentTitle + index` | `unificationId` non fiable (TMDB IDs différents entre serveurs) |

### UnificationID

Construit en combinant les identifiants disponibles :

```
imdb:tt1234567
tmdb:12345
title:Movie Name|year:2024
```

### Points d'attention

- L'enrichissement passe par `PreparePlaybackUseCase` qui encapsule le flow complet (enrichment → source selection → queue)
- Le matching `parentIndex` dans le fallback réseau doit être null-safe
- Les épisodes d'un serveur distant peuvent ne pas être dans Room → le fallback réseau doit fonctionner
- Les sources Xtream et Backend ne nécessitent pas d'enrichissement cross-serveur

---

## 14. Système d'erreurs

### AppError (`core:model/AppError.kt`)

Sealed class hiérarchique pour une gestion typée des erreurs :

```
AppError
├── Network
│   ├── NoConnection
│   ├── Timeout
│   ├── ServerError
│   ├── NotFound
│   └── Unauthorized
├── Auth
│   ├── InvalidToken
│   ├── SessionExpired
│   ├── NoServersFound
│   └── PinGenerationFailed
├── Media
│   ├── NotFound
│   ├── LoadFailed
│   ├── NoPlayableContent
│   └── UnsupportedFormat
├── Playback
│   ├── InitializationFailed
│   ├── StreamingError
│   ├── CodecNotSupported
│   └── DrmError
├── Search
│   ├── QueryTooShort
│   ├── NoResults
│   └── SearchFailed
├── Storage
│   ├── DiskFull
│   ├── ReadError
│   └── WriteError
├── Unknown
└── Validation
```

### Extension de conversion

```kotlin
fun Throwable.toAppError(): AppError
// UnknownHostException -> Network.NoConnection
// SocketTimeoutException -> Network.Timeout
// IOException -> Network.ServerError
// else -> Unknown
```

### ErrorMessageResolver

Utilitaire de conversion `AppError → String` localisé (FR/EN) :

```kotlin
object ErrorMessageResolver {
    fun resolve(context: Context, error: AppError): String
}
```

### Propagation

- `BaseViewModel` fournit `emitError()` et `errorEvents: Flow<AppError>` (événements one-shot)
- `ErrorSnackbarHost` dans les écrans Compose consomme et affiche les erreurs localisées
- Les écrans critiques (MediaDetail, SeasonDetail) affichent un empty state "Content not found" avec bouton retry

---

## 15. Thèmes

### Thèmes disponibles (5)

| Thème | Accent | Fond | Description |
|-------|--------|------|-------------|
| Plex | Orange (#E5A00D) | Noir | Thème officiel Plex |
| Netflix | Rouge (#E50914) | Noir | Style Netflix |
| MonoDark | Blanc | Noir pur | Minimaliste sombre |
| MonoLight | Noir | Blanc | Minimaliste clair |
| Morocco | Rouge/Vert/Or | Sombre | Couleurs du Maroc |

### Implémentation

- Fichier : `core/designsystem/src/.../Theme.kt`
- Basé sur Material3 `ColorScheme`
- Optimisé pour TV (sombre par défaut)
- Support couleurs dynamiques (Android 12+)
- Sélection dans Settings, stocké dans DataStore (`appTheme`)
- Transitions NavHost avec FadeIn/FadeOut

---

## 16. Workers

### Workers WorkManager (6)

#### LibrarySyncWorker

- **Déclencheur** : Sync initiale + périodique (6h)
- **Action** : Sync films/séries depuis `/library/sections/{id}/all` (Plex) et `/Users/{id}/Items` (Jellyfin)
- **Particularités** : Foreground service, notifications de progression, gestion timeout
- **Chainage** : Déclenche `CollectionSyncWorker` puis `UnifiedRebuildWorker` en cas de succès

#### CollectionSyncWorker

- **Déclencheur** : Après LibrarySyncWorker
- **Action** : Sync collections Plex depuis les bibliothèques films/séries
- **Particularités** : Foreground service

#### RatingSyncWorker

- **Déclencheur** : Manuel ou périodique
- **Action** : Récupère les notes depuis TMDb (séries) et OMDb (films), met à jour `displayRating`
- **Particularités** : Rate limiting (250ms entre requêtes), gestion erreurs SSL

#### UnifiedRebuildWorker

- **Déclencheur** : Après sync réussie (LibrarySync ou CollectionSync)
- **Action** : Reconstruit la vue matérialisée `media_unified` pour le browsing unifié performant

#### CachePurgeWorker

- **Déclencheur** : Périodique
- **Action** : Supprime les entrées `ApiCacheEntity` expirées

#### ChannelSyncWorker

- **Déclencheur** : Manuel ou périodique
- **Action** : Sync des chaînes IPTV/Xtream

### Injection dans les Workers

Les Workers utilisent `@HiltWorker` avec `@AssistedInject` pour l'injection de dépendances.

---

## 17. Intégration Jellyfin

### Architecture

L'intégration Jellyfin suit le pattern `MediaSourceHandler` pour s'intégrer au système multi-source existant.

```
┌──────────────────────────────────────┐
│          JellyfinSetupScreen          │
│          JellyfinSetupViewModel       │
│   (URL, username, password → auth)    │
└──────────────┬───────────────────────┘
               │
               v
┌──────────────────────────────────────┐
│        JellyfinClient                 │
│   (authentification, browsing)        │
│                                       │
│  JellyfinApiService (Retrofit)        │
│  JellyfinConnectionTester             │
└──────────────┬───────────────────────┘
               │
               v
┌──────────────────────────────────────┐
│     JellyfinSourceHandler             │
│   (Room-first + API fallback)         │
│                                       │
│  JellyfinMapper (ticks→ms, IDs)       │
│  JellyfinUrlBuilder (stream URLs)     │
│  JellyfinPlaybackReporter (sessions)  │
│  JellyfinImageInterceptor (auth)      │
└──────────────────────────────────────┘
```

### Composants clés

| Composant | Rôle |
|-----------|------|
| `JellyfinApiService` | Interface Retrofit (auth, browse, search, playback) |
| `JellyfinClient` | Client haut-niveau (authentification, test connexion) |
| `JellyfinMapper` | Conversion DTO → domain (ticks→ms, ProviderIds, URLs relatives) |
| `JellyfinSourceHandler` | Strategy pattern : Room-first + API fallback pour détails/saisons/épisodes |
| `JellyfinUrlBuilder` | Construction URLs de lecture directe |
| `JellyfinPlaybackReporter` | Reporting session (start, progress, stop) |
| `JellyfinImageInterceptor` | Injection header Authorization pour les images |
| `JellyfinServerEntity` | Stockage credentials en Room |

### Particularités

- **Same-server sources** : les requêtes DAO excluent par `ratingKey + serverId` (pas `serverId` seul), permettant plusieurs versions d'un même contenu (VF/VO, 720p/4K)
- **Sync intégrée** : `LibrarySyncWorker` synchronise aussi les bibliothèques Jellyfin via `SyncJellyfinLibraryUseCase`
- **Recherche fusionnée** : résultats Jellyfin intégrés dans `SearchRepositoryImpl`

---

## 18. Gestion expiration de session

### Architecture

```
┌─────────────────────┐     401 Response     ┌──────────────────┐
│   AuthInterceptor   │ ─────────────────────>│   AuthEventBus   │
│   (OkHttp layer)    │     tryEmit(event)    │   (SharedFlow)   │
└─────────────────────┘                       └────────┬─────────┘
                                                       │ collect
                                                       v
                                              ┌──────────────────┐
                                              │  MainViewModel   │
                                              │  (dialog + nav)  │
                                              └──────────────────┘
```

### Composants

| Composant | Rôle |
|-----------|------|
| `AuthEventBus` | Singleton `MutableSharedFlow` (buffer=1, DROP_OLDEST), thread-safe |
| `AuthInterceptor` | Détecte les 401 depuis `plex.tv` uniquement (les 401 serveurs locaux sont ignorés) |
| `MainViewModel` | Coordonne l'affichage du dialog d'expiration et la navigation vers l'écran d'auth |

### Comportement

- L'interceptor retourne la réponse 401 au caller (pas de retry automatique) → évite les boucles infinies
- Déduplication des dialogs via flag dans MainViewModel (empêche les multiples 401 simultanés)
- Navigation vers l'auth avec `popUpTo(0)` pour vider le back stack

---

## 19. Tests

### Fichiers de tests existants (38)

#### Couche App (17)

| Fichier | Couverture |
|---------|-----------|
| `ChapterMarkerManagerTest.kt` | Marqueurs intro/crédits |
| `PlayerStatsTrackerTest.kt` | Collecte stats lecture |
| `PlayerTrackControllerTest.kt` | Gestion pistes audio/sous-titres |
| `PlayerScrobblerTest.kt` | Scrobbling timeline Plex |
| `PlayerControlViewModelTest.kt` | Contrôles player |
| `TrackSelectionViewModelTest.kt` | Sélection pistes |
| `PlaybackStatsViewModelTest.kt` | Overlay stats |
| `MediaDetailViewModelTest.kt` | Détails média |
| `HomeViewModelTest.kt` | Contenu accueil |
| `SearchViewModelTest.kt` | Recherche multi-serveur |
| `LibraryViewModelTest.kt` | Bibliothèque + filtres |
| `AppProfileViewModelTest.kt` | Profils locaux |
| `PlexHomeSwitcherViewModelTest.kt` | Plex Home |
| `PlaylistDetailViewModelTest.kt` | Détails playlist |
| `PlaylistListViewModelTest.kt` | Liste playlists |
| `MainViewModelTest.kt` | État global app |
| `GlobalCoroutineExceptionHandlerTest.kt` | Gestion exceptions globales |

#### Couche App — Autres

| Fichier | Couverture |
|---------|-----------|
| `SyncStatusModelTest.kt` | Modèle statut sync |

#### Couche Core (6)

| Fichier | Couverture |
|---------|-----------|
| `StringNormalizerTest.kt` | Normalisation de chaînes |
| `AppErrorTest.kt` | Conversion erreurs |
| `UnificationIdTest.kt` | Calcul unificationId |
| `AuthEventBusTest.kt` | Bus événements auth |
| `AuthInterceptorTest.kt` | Intercepteur 401 |
| `ConnectionManagerTest.kt` | Gestion connexions serveur |
| `NetworkSecurityTest.kt` | Sécurité réseau (SSL trust) |

#### Couche Data (3)

| Fichier | Couverture |
|---------|-----------|
| `MediaUrlResolverTest.kt` | Résolution URL média |
| `ProfileRepositoryImplTest.kt` | Repository profils |
| `MediaLibraryQueryBuilderTest.kt` | Construction SQL dynamique |
| `MediaMapperTest.kt` | Mapping DTO → domain |

#### Couche Domain (8)

| Fichier | Couverture |
|---------|-----------|
| `GetMediaDetailUseCaseTest.kt` | Détails média |
| `GetUnifiedHomeContentUseCaseTest.kt` | Contenu unifié accueil |
| `PrefetchNextEpisodeUseCaseTest.kt` | Prefetch épisode suivant |
| `SearchAcrossServersUseCaseTest.kt` | Recherche multi-serveur |
| `SyncWatchlistUseCaseTest.kt` | Sync watchlist |
| `ToggleFavoriteUseCaseTest.kt` | Toggle favoris |
| `EnrichMediaItemUseCaseTest.kt` | Enrichissement multi-serveur |
| `PlaybackManagerTest.kt` | Gestion lecture |
| `FilterContentByAgeUseCaseTest.kt` | Filtrage contenu par âge |

### Stack de tests

- **JUnit** : Framework de tests
- **MockK** : Mocking Kotlin-native
- **Truth** : Assertions lisibles (Google)
- **Turbine** : Tests Flow/StateFlow
- **Coroutines Test** : `runTest`, `StandardTestDispatcher`, `advanceUntilIdle`

---

**Derniere mise a jour** : 23 mars 2026
