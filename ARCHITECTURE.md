# Architecture PlexHubTV

> **Date** : 14 février 2026
> **Version** : 2.0
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
17. [Tests](#17-tests)

---

## 1. Vue d'ensemble

PlexHubTV est un client Android TV pour Plex Media Server, construit en Kotlin avec Jetpack Compose. L'application offre une interface inspirée de Netflix, optimisée pour la navigation D-Pad, avec support multi-serveur, lecture offline et enrichissement croisé des métadonnées.

### Stack technique

| Catégorie | Technologies |
|-----------|-------------|
| Langage | Kotlin |
| UI | Jetpack Compose for TV, Material3 |
| Architecture | Clean Architecture, MVVM + MVI |
| DI | Hilt (Dagger) |
| Base de données | Room (v26), WAL mode |
| Réseau | Retrofit, OkHttp, Gson |
| Player | ExoPlayer (primaire) + MPV (fallback) |
| Images | Coil (cache adaptatif RAM) |
| Background | WorkManager |
| Préférences | DataStore + EncryptedSharedPreferences |
| Pagination | Paging 3 |
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

- Compose UI + ViewModels
- Navigation Compose
- Workers (WorkManager)
- Modules DI Hilt dans `app/di/`

### Domain (`:domain`)

- Use Cases (logique métier pure)
- Interfaces Repository (contrats)
- Services (PlaybackManager)
- Aucune dépendance Android Framework

### Data (`:data`)

- Implémentations Repository
- Mappers (DTO -> Domain)
- Paging 3 (MediaRemoteMediator)
- Agrégation multi-serveur (MediaDeduplicator)

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
| `:core:network` | Retrofit services, API clients | `:core:model`, Retrofit, OkHttp |
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
| `DatabaseModule` | `core:database` | SingletonComponent | PlexDatabase, tous les DAOs, Migrations |
| `NetworkModule` | `core:network` | SingletonComponent | OkHttpClient (trust SSL local), Retrofit, PlexApiService, TmdbApiService, OmdbApiService, AuthInterceptor |
| `RepositoryModule` | `data:di` | SingletonComponent | 14+ @Binds (Auth, Media, Library, Search, Playback, OnDeck, Hubs, Favorites, Watchlist, Sync, Downloads, Collection, History, Iptv...) |
| `DataStoreModule` | `core:datastore` | SingletonComponent | `DataStore<Preferences>` |
| `CoroutineModule` | `core:common` | SingletonComponent | `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`, `@ApplicationScope` |
| `WorkModule` | `core:common` | SingletonComponent | WorkManager |
| `PlayerModule` | `app:di` | SingletonComponent | PlayerFactory (ExoPlayerFactory) |
| `ImageModule` | `app:di` | SingletonComponent | Coil ImageLoader (cache adaptatif 10-15% RAM), PlexImageKeyer |

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
    // ... 14+ bindings
}
```

---

## 5. Base de données (Room)

### PlexDatabase (Version 26)

- Fichier : `core/database/src/.../PlexDatabase.kt`
- Mode WAL (Write-Ahead Logging) activé
- `PRAGMA synchronous = NORMAL`
- `PRAGMA cache_size = -8000` (8 Mo)

### Entités (14)

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
| `ProfileEntity` | Profils multi-utilisateur (Plex Home) |
| `SearchCacheEntity` | Cache des résultats de recherche |

### DAOs (13)

MediaDao, ServerDao, DownloadDao, ApiCacheDao, OfflineWatchProgressDao, HomeContentDao, FavoriteDao, RemoteKeysDao, LibrarySectionDao, TrackPreferenceDao, CollectionDao, ProfileDao, SearchCacheDao

### Migrations (v11 -> v26)

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

**Point important** : `MediaEntity` ne stocke PAS `parentIndex` (numéro de saison pour les épisodes) — seulement `index` (numéro d'épisode/saison). Le mapper `mapEntityToDomain` ne mappe donc jamais `parentIndex`.

---

## 6. Couche réseau

### Services API (3 interfaces Retrofit)

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
| Plex Home | `GET /api/home/users`, `POST /api/home/users/{uuid}/switch` |

Utilise `@Url` dynamique pour les appels multi-serveur.

#### TmdbApiService (`core:network`)

- Base : `https://api.themoviedb.org/`
- `GET /3/tv/{tv_id}` — Notes des séries (vote_average)

#### OmdbApiService (`core:network`)

- Base : `https://www.omdbapi.com/`
- `GET /?i={imdbId}` — Notes IMDb (imdbRating)

### Gestion des connexions

- `ConnectionManager` : teste les candidats de connexion en parallèle (race), sélectionne la meilleure URL
- `ServerClientResolver` : résout les connexions serveur via `ConnectionManager.findBestConnection`
- SSL trust pour les IP locales uniquement (certificats auto-signés sur LAN)
- `AuthInterceptor` : injecte le token Plex dans chaque requête

---

## 7. Stockage local

### SettingsDataStore (`core:datastore`)

Deux backends de stockage :

| Type | Backend | Données |
|------|---------|---------|
| Non-sensible | `DataStore<Preferences>` | Thème, qualité vidéo, player engine, timestamps sync, préférences UI |
| Sensible | `SecurePreferencesManager` | Token Plex, Client ID, clés API TMDb/OMDb |

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
Splash -> Login -> PinInput -> Profiles -> Loading -> Main
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
├── Settings -> Debug, ServerStatus
└── Iptv
```

#### Media Graph (routes paramétrées)

| Route | Paramètres |
|-------|-----------|
| `media_detail/{ratingKey}?serverId={serverId}` | ratingKey, serverId |
| `season_detail/{ratingKey}?serverId={serverId}` | ratingKey, serverId |
| `collection_detail/{collectionId}?serverId={serverId}` | collectionId, serverId |
| `video_player/{ratingKey}?serverId={serverId}&startOffset=...&url=...&title=...` | ratingKey, serverId, startOffset, url, title |

### NavigationItem (`core:navigation`)

Sealed class associant chaque `Screen` a un label et une icone pour la sidebar TV :

```
Home, Movies, TVShows, Search, Downloads, Favorites, History, Settings, Iptv (Live TV)
```

---

## 9. Ecrans et Features

### ViewModels (24)

| ViewModel | Feature | Responsabilité |
|-----------|---------|----------------|
| `AuthViewModel` | auth | Flux d'auth PIN Plex, OAuth |
| `ProfileViewModel` (auth) | auth | Sélection profil Plex Home |
| `SplashViewModel` | auth | Auto-login, bypass auth |
| `LoadingViewModel` | loading | Suivi progression sync initiale |
| `MainViewModel` | main | État global app, navigation |
| `HomeViewModel` | home | Contenu unifié (On Deck + Hubs), sync WorkManager, prefetch images |
| `LibraryViewModel` | library | Navigation bibliothèque avec Paging3, filtres, tri |
| `SearchViewModel` | search | Recherche globale multi-serveur, debounce 500ms |
| `MediaDetailViewModel` | details | Détails média, Smart Start (reprise/épisode suivant), sélection source, favoris, statut lecture |
| `SeasonDetailViewModel` | details | Liste des épisodes par saison, lecture épisode |
| `CollectionDetailViewModel` | collection | Contenu d'une collection |
| `HubDetailViewModel` | hub | Contenu d'un hub (ex: "Reprendre la lecture") |
| `FavoritesViewModel` | favorites | Liste des favoris |
| `HistoryViewModel` | history | Historique de lecture |
| `DownloadsViewModel` | downloads | Contenu téléchargé (offline) |
| `SettingsViewModel` | settings | Paramètres (thème, qualité, moteur player) |
| `ServerStatusViewModel` | settings | État des connexions serveur |
| `DebugViewModel` | settings | Outils de debug (BUILD_TYPE only) |
| `ProfileViewModel` | profile | Gestion profils utilisateur |
| `IptvViewModel` | iptv | Parsing playlist M3U, lecture IPTV |
| `PlayerControlViewModel` | player | Contrôles UI player, overlay |
| `PlaybackStatsViewModel` | player | Stats temps-réel (bitrate, codec, buffer) |
| `TrackSelectionViewModel` | player | Sélection pistes audio/sous-titres |
| `MediaEnrichmentViewModel` | player | Enrichissement en arrière-plan |

### Deux chemins de lecture

1. **MediaDetailViewModel.PlayClicked** : films et séries (récupère l'épisode suivant via `GetNextEpisodeUseCase`)
2. **SeasonDetailViewModel.PlayEpisode** : épisodes depuis la liste de saison

Les deux chemins passent par `EnrichMediaItemUseCase` avant la lecture.

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
| `PlayerScrobbler` | Mises à jour timeline Plex, popup épisode suivant |
| `PlayerTrackController` | Gestion pistes audio/sous-titres |
| `ChapterMarkerManager` | Marqueurs intro/crédits (skip) |

### Moteurs de lecture

| Moteur | Rôle | Détails |
|--------|------|---------|
| ExoPlayer | Primaire | Décodeur FFmpeg Jellyfin, support sous-titres ASS |
| MPV | Fallback | Basculement automatique sur erreur MediaCodec |

`TranscodeUrlBuilder` construit les URL de lecture directe ou transcodée.

---

## 11. Gestion d'état

### Pattern StateFlow + Channel

```kotlin
// Pattern standard ViewModel
class SomeViewModel : ViewModel() {
    // État UI réactif
    private val _uiState = MutableStateFlow(SomeUiState())
    val uiState: StateFlow<SomeUiState> = _uiState.asStateFlow()

    // Événements one-shot (navigation, erreurs)
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private val _errorEvents = Channel<AppError>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()

    // Actions MVI-like
    fun onEvent(event: SomeEvent) { ... }
}
```

### Conventions

- **StateFlow** pour l'état UI observable (recomposition Compose)
- **Channel** pour les événements one-shot (navigation, toasts, erreurs)
- **SavedStateHandle** pour la restauration d'état après process death
- Les ViewModels exposent des `fun onEvent(event)` ou `fun onAction(action)` (pattern MVI-like)

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
| `LibrarySyncWorker` | Sync périodique (6h) films/séries depuis tous les serveurs |
| `CollectionSyncWorker` | Sync collections après LibrarySync |
| `RatingSyncWorker` | Scraping notes TMDb/OMDb |
| `OfflineWatchProgressEntity` | Sauvegarde progression lecture hors-ligne |
| `ApiCacheEntity` | Cache réponses API avec TTL |

### Limites

- `LibrarySyncWorker` synchronise uniquement les films et séries depuis `/library/sections/{id}/all`, **PAS** les saisons ni les épisodes
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

- L'enrichissement doit se faire dans les **deux** ViewModels de lecture (MediaDetail + SeasonDetail)
- Le matching `parentIndex` dans le fallback réseau doit être null-safe (Room ne stocke pas `parentIndex`)
- Les épisodes d'un serveur distant peuvent ne pas être dans Room → le fallback réseau doit fonctionner

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

### Propagation

- Les ViewModels émettent les erreurs via `Channel<AppError>` (événements one-shot)
- `ErrorSnackbarHost` dans les écrans Compose consomme et affiche les erreurs

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

---

## 16. Workers

### Workers WorkManager (3)

#### LibrarySyncWorker

- **Déclencheur** : Sync initiale + périodique (6h)
- **Action** : Sync films/séries depuis `/library/sections/{id}/all` pour tous les serveurs
- **Particularités** : Foreground service, notifications de progression, gestion timeout
- **Chainage** : Déclenche `CollectionSyncWorker` en cas de succès

#### CollectionSyncWorker

- **Déclencheur** : Après LibrarySyncWorker
- **Action** : Sync collections Plex depuis les bibliothèques films/séries
- **Particularités** : Foreground service

#### RatingSyncWorker

- **Déclencheur** : Manuel ou périodique
- **Action** : Récupère les notes depuis TMDb (séries) et OMDb (films)
- **Particularités** : Rate limiting (250ms entre requêtes), gestion erreurs SSL

### Injection dans les Workers

Les Workers utilisent `@HiltWorker` avec `@AssistedInject` pour l'injection de dépendances.

---

## 17. Tests

### Fichiers de tests existants (18)

#### Couche App (7)

| Fichier | Couverture |
|---------|-----------|
| `ChapterMarkerManagerTest.kt` | Marqueurs intro/crédits |
| `PlayerStatsTrackerTest.kt` | Collecte stats lecture |
| `PlayerTrackControllerTest.kt` | Gestion pistes audio/sous-titres |
| `PlayerControlViewModelTest.kt` | Contrôles player (19 tests) |
| `TrackSelectionViewModelTest.kt` | Sélection pistes (6 tests) |
| `PlaybackStatsViewModelTest.kt` | Overlay stats (6 tests) |
| `MediaDetailViewModelTest.kt` | Détails média (10 tests) |

#### Couche Core (2)

| Fichier | Couverture |
|---------|-----------|
| `StringNormalizerTest.kt` | Normalisation de chaînes |
| `AppErrorTest.kt` | Conversion erreurs |

#### Couche Data (2)

| Fichier | Couverture |
|---------|-----------|
| `MediaUrlResolverTest.kt` | Résolution URL média |
| `ProfileRepositoryImplTest.kt` | Repository profils |

#### Couche Domain (7)

| Fichier | Couverture |
|---------|-----------|
| `GetMediaDetailUseCaseTest.kt` | Détails média |
| `GetUnifiedHomeContentUseCaseTest.kt` | Contenu unifié accueil |
| `PrefetchNextEpisodeUseCaseTest.kt` | Prefetch épisode suivant |
| `SearchAcrossServersUseCaseTest.kt` | Recherche multi-serveur |
| `SyncWatchlistUseCaseTest.kt` | Sync watchlist |
| `ToggleFavoriteUseCaseTest.kt` | Toggle favoris |
| `TrackSelectionUseCaseTest.kt` | Sélection pistes |

### Stack de tests

- **JUnit** : Framework de tests
- **MockK** : Mocking Kotlin-native
- **Truth** : Assertions lisibles (Google)
- **Turbine** : Tests Flow/StateFlow
- **Coroutines Test** : `runTest`, `StandardTestDispatcher`, `advanceUntilIdle`

### Tests manquants

Voir `MISSING_TESTS.md` pour la liste des tests a restaurer (supprimés lors du refactoring).

---

**Derniere mise a jour** : 14 février 2026
