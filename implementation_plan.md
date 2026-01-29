# Plan de Migration : Flutter vers Android Natif (Kotlin + Compose)

Ce document présente l'analyse de l'application existante et l'architecture cible pour la réécriture en Kotlin et Jetpack Compose.

## 1. Inventaire des Écrans et Routes

Voici la structure de navigation identifiée, basée sur l'analyse du code Flutter existant.

| Écran / Route | Rôle Fonctionnel | Données Nécessaires (Inputs/Modèles) | Actions Utilisateur Clés | États Principaux |
| :--- | :--- | :--- | :--- | :--- |
| **AuthScreen** | Authentification Plex et découverte des serveurs. | `PlexAuthService` (PIN, User Info), `StorageService`. | Connexion via Navigateur, Scan QR Code, Saisie PIN, Retry. | `Idle`, `Authenticating` (Polling), [Error](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1211-1214), `Success`. |
| **MainScreen** | Shell de l'application. Gère la navigation principale (BottomBar / NavigationRail). | `MultiServerProvider`, `OfflineModeProvider`. | Navigation entre onglets (Accueil, Bibliothèques, Recherche, Téléchargements, Paramètres). | `Online`, `Offline Mode`. |
| **DiscoverScreen** (Home) | Tableau de bord principal. Affiche "Continuer la lecture" (On Deck) et les Hubs de contenu. | `AggregationService`, `PlexHub`, `PlexMetadata` (OnDeck). | Pull-to-refresh, Clic Média, Lecture rapide, Scroll Hero Banner. | `Loading`, `Content Loaded` (List), [Error](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1211-1214). |
| **LibrariesScreen** | Navigation dans les bibliothèques. 4 Sous-onglets : Recommandés, Explorer, Collections, Playlists. | `PlexLibrary` list, `PlexMetadata` items, [Filters](file:///c:/Users/chakir/plezy/plezy/lib/screens/libraries/libraries_screen.dart#673-680), `Sorts`. | Changement de bibliothèque, Filtrage, Tri, Changement de vue (Grid/List), Pagination. | `Loading`, `List (Paged)`, `Empty`, [Error](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1211-1214). |
| **SearchScreen** | Recherche globale unifiée sur tous les serveurs connectés. | `AggregationService` (Search), `PlexMetadata` results. | Saisie texte (Debounce), Clic résultat, Clear. | `Idle`, `Searching`, `Results`, `No Results`. |
| **MediaDetailScreen** | Détails d'un Film ou d'une Série. | `PlexMetadata` (Item), `PlexMetadata` (Children/Seasons), `DownloadStatus`. | Play, Trailer, Favoris, Noter, Télécharger, Changer de serveur source. | `Loading`, [Loaded](file:///c:/Users/chakir/plezy/plezy/lib/screens/libraries/libraries_screen.dart#240-258), [Offline](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#841-867) (Cached). |
| **SeasonDetailScreen** | Liste des épisodes d'une saison spécifique. | `PlexMetadata` (Season), List `<Episode>`. | Sélection épisode, Tout marquer vu. | `Loading`, [Loaded](file:///c:/Users/chakir/plezy/plezy/lib/screens/libraries/libraries_screen.dart#240-258), `Empty`. |
| **VideoPlayerScreen** | Lecteur vidéo. | `PlexMetadata` (MediaInfo), [PlaybackState](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1228-1239), `Subtitles/Audio`. | Play/Pause, Seek, Audio/Subs track selection, Next/Prev, PIP. | `Buffering`, [Playing](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1136-1157), `Paused`, [Completed](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1158-1187), [Error](file:///c:/Users/chakir/plezy/plezy/lib/screens/video_player_screen.dart#1211-1214). |
| **DownloadsScreen** | Gestion du contenu hors-ligne. | `DownloadProvider` (Local DB), `OfflineWatchProvider`. | Supprimer téléchargement, Lire hors-ligne, Retry. | `Loading`, [List](file:///c:/Users/chakir/plezy/plezy/lib/screens/season_detail_screen.dart#131-137), `Empty`. |
| **SettingsScreen** | Configuration de l'application. | `SettingsProvider` (DataStore). | Thème, Qualité Vidéo, Cache, Serveurs. | [Loaded](file:///c:/Users/chakir/plezy/plezy/lib/screens/libraries/libraries_screen.dart#240-258). |

## 2. Architecture Cible (Android Moderne)

Nous adopterons une **Clean Architecture** suivant les recommandations de Google (Guide to App Architecture).

### Organisation en Couches (Layers)

1.  **UI Layer (Presentation)**
    *   **Technologies** : Jetpack Compose, Material3.
    *   **Pattern** : MVVM (Model-View-ViewModel).
    *   **State** : `StateFlow` exposé par le ViewModel, collecté par l'UI ("Unidirectional Data Flow").
    *   **Navigation** : Jetpack Navigation Compose (Single Activity).

2.  **Domain Layer** (Optionnel mais recommandé pour la logique complexe d'agrégation)
    *   **Rôle** : Contient la logique métier pure ("Business Logic"), agnostique du framework Android.
    *   **Composants** :
        *   `Models` : Data classes pures (ex: `MediaItem`, [Server](file:///c:/Users/chakir/plezy/plezy/lib/screens/media_detail_screen.dart#656-748)).
        *   `UseCases` : Actions atomiques (ex: `GetUnifiedOnDeckUseCase`, `SyncOfflineContentUseCase`).
        *   `Repository Interfaces` : Contrats pour l'accès aux données.

3.  **Data Layer**
    *   **Rôle** : Gestion des sources de données (réseau, base de données, cache).
    *   **Composants** :
        *   `Repositories` (Implémentations) : Orchestrent la source de vérité (Offline-first : DB locale comme source, Réseau pour sync).
        *   `Data Sources` :
            *   **Remote** : Retrofit (API Plex).
            *   **Local** : Room Database (Cache métadonnées, état de lecture).
            *   **Preferences** : Jetpack DataStore (Paramètres utilisateur).

### Stack Technique Recommandée

*   **Langage** : Kotlin.
*   **Injection de Dépendances** : Hilt.
*   **Asynchronicité** : Coroutines & Flow.
*   **Réseau** : Retrofit + OkHttp (Gestion des headers Plex Token).
*   **Image Loading** : Coil (Intégration native Compose).
*   **Base de données** : Room (Gestion structurelle des objets Plex complexes).
*   **Lecteur Vidéo** : **Media3 (ExoPlayer)**.
    *   *Note* : L'app Flutter utilise `mpv`. Sur Android natif, ExoPlayer est le standard industriel : plus stable, supporte le DRM, le hardware tunneling, et s'intègre mieux au lifecycle Android.

### Structure du Projet (Feature-First)

Pour une meilleure scalabilité, le code sera organisé par fonctionnalités ("Features") plutôt que par type technique.

```text
com.plezy.app
├── core
│   ├── network          # Retrofit, Client Plex, Interceptors
│   ├── database         # Room DB, DAOs, Converters
│   ├── datastore        # Gestion des préférences
│   ├── designsystem     # Theme, Typography, Components réutilisables
│   └── navigation       # Graphe de navigation global
├── data
│   ├── repository       # Implémentations (PlexRepositoryImpl, AuthRepositoryImpl)
│   └── model            # DTOs (Data Transfer Objects - Network models)
├── domain
│   ├── model            # Modèles métier propres
│   ├── repository       # Interfaces
│   └── usecase          # Logique métier (ex: Aggregation logic)
├── feature
│   ├── auth             # AuthScreen, AuthViewModel
│   ├── home             # DiscoverScreen, HomeViewModel
│   ├── library          # LibrariesScreen, LibraryViewModel
│   ├── details          # MediaDetailScreen, DetailsViewModel
│   ├── player           # VideoPlayerScreen (Media3 based)
│   ├── search           # SearchScreen
│   └── downloads        # Gestion offline
└── app                  # MainActivity, Application class (Hilt setup)
```

## 3. Points d'Attention Spécifiques

*   **Plex Aggregation (Multi-Server)** : La logique actuelle de Flutter `DataAggregationService` est critique. Elle doit être portée dans le Domain Layer (via des UseCases) pour fusionner proprement les listes provenant de multiples sources Retrofit.
*   **Authentication** : Le flow Plex (PIN polling) doit être géré avec des Coroutines robustes pour éviter les leaks mémoire.
*   **Navigation** : Utiliser des routes typées (Type-safe navigation) pour passer les arguments simples (`ratingKey`, `serverId`). Pour les objets complexes, passer seulement l'ID et recharger depuis la DB/Cache locale (Pattern "Single Source of Truth").

## 4. Architecture Détaillée et Navigation

### Structure de Navigation (Jetpack Navigation)

Utilisation d'un `NavHost` unique dans `MainActivity`.

**Graphe de Navigation :**

*   **AuthGraph** (Start Destination si non connecté)
    *   `login_landing`
    *   `login_pin`
*   **MainGraph** (Start Destination si connecté) 
    *   `home` (Discover)
    *   `library_tabs` (Contient les 4 onglets : Recommended, Browse, Collections, Playlists)
    *   `search`
    *   `downloads`
    *   `settings`
*   **MediaGraph**
    *   `media_detail/{ratingKey}?serverId={serverId}`
    *   `season_detail/{ratingKey}?serverId={serverId}`
    *   `video_player/{ratingKey}?serverId={serverId}&startOffset={offset}`

**Arguments :**
Ne passer que les IDs (`ratingKey`, `serverId`) via la route. Les objets complexes sont récupérés depuis le Repository via l'ID.

### ViewModels et UiState

Les ViewModels doivent exposer un `StateFlow<UiState>` unique et immutable.

#### A. DiscoverViewModel (Home)
*   **UiState**: `LoadState` (Loading, Success(onDeck: List<Media>, hubs: List<Hub>), Error).
*   **Events**: [Refresh](file:///c:/Users/chakir/plezy/plezy/lib/screens/search_screen.dart#132-143), `OpenMedia(Media)`, `PlayMedia(Media)`.
*   **Responsabilités**: Appeler `GetUnifiedHomeContentUseCase` qui agrège les données.

#### B. MediaDetailViewModel
*   **UiState**:
    ```kotlin
    data class MediaDetailUiState(
        val isLoading: Boolean = true,
        val media: MediaItem? = null,
        val seasons: List<MediaItem> = emptyList(), // Si c'est une série
        val cast: List<CastMember> = emptyList(),
        val isOffline: Boolean = false,
        val error: String? = null
    )
    ```
*   **Events**: `ToggleWatchStatus`, `ToggleFavorite`, `PlayClicked`, `DownloadClicked`.
*   **Responsabilités**: Charger les métadonnées complètes, vérifier l'état du téléchargement, gérer les actions utilisateur.

#### C. PlayerViewModel
*   **UiState**: `PlayerUiState` (isPlaying, currentPosition, duration, buffering, currentItem, playlist/queue).
*   **Responsabilités**: Initialiser ExoPlayer, sync état de lecture avec Plex, gestion focus audio.

## 5. Exemple Complet : MediaDetailScreen

Voici un exemple concret de l'implémentation "Clean Architecture" pour l'écran de détail média.

### 1. Data Layer (Repository Interface & Implementation)

**Interface (Domain)**
```kotlin
// domain/repository/MediaRepository.kt
interface MediaRepository {
    suspend fun getMediaDetails(ratingKey: String, serverId: String): Result<MediaItem>
    suspend fun getSeasons(showRatingKey: String, serverId: String): Result<List<MediaItem>>
    suspend fun toggleWatchStatus(media: MediaItem): Result<Boolean>
}
```

**Implementation (Data)**
```kotlin
// data/repository/MediaRepositoryImpl.kt
class MediaRepositoryImpl @Inject constructor(
    private val plexApi: PlexApiService,
    private val mediaDao: MediaDao, // Room
    private val mapper: MediaMapper
) : MediaRepository {
    
    override suspend fun getMediaDetails(ratingKey: String, serverId: String): Result<MediaItem> {
        return try {
            // Stratégie Offline-first : 
            // 1. Essayer de charger du cache local si disponible
            // 2. Sinon, appel réseau et mise en cache
            val cached = mediaDao.getMedia(ratingKey)
            if (cached != null) return Result.success(mapper.mapToDomain(cached))
            
            val response = plexApi.getMetadata(ratingKey)
            val entity = mapper.mapToEntity(response, serverId)
            mediaDao.insert(entity)
            Result.success(mapper.mapToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ... autres méthodes
}
```

### 2. Domain Layer (UseCase)

```kotlin
// domain/usecase/GetMediaDetailUseCase.kt
class GetMediaDetailUseCase @Inject constructor(
    private val repository: MediaRepository
) {
    suspend operator fun invoke(ratingKey: String, serverId: String): Flow<Result<MediaItem>> = flow {
        emit(repository.getMediaDetails(ratingKey, serverId))
    }
}
```

### 3. Presentation Layer (ViewModel)

```kotlin
// feature/details/MediaDetailViewModel.kt
@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val toggleWatchStatusUseCase: ToggleWatchStatusUseCase
) : ViewModel() {

    // Arguments depuis la navigation
    private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    // UI State
    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getMediaDetailUseCase(ratingKey, serverId).collect { result ->
                result.onSuccess { media ->
                    _uiState.update { it.copy(isLoading = false, media = media) }
                }.onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            }
        }
    }

    fun handleAction(action: MediaDetailAction) {
        when (action) {
            is MediaDetailAction.ToggleWatchStatus -> toggleWatch()
            is MediaDetailAction.Play -> { /* Navigation Event via Channel */ }
            // ... autres actions
        }
    }

    private fun toggleWatch() {
         viewModelScope.launch {
             // Optimistic update
             val currentMedia = _uiState.value.media ?: return@launch
             val newStatus = !currentMedia.isWatched
             _uiState.update { it.copy(media = currentMedia.copy(isWatched = newStatus)) }
             
             // Appel API réel
             toggleWatchStatusUseCase(currentMedia).onFailure {
                 // Revert si échec
                 _uiState.update { it.copy(media = currentMedia) }
             }
         }
    }
}

// Définitions State & Action
data class MediaDetailUiState(
    val isLoading: Boolean = true,
    val media: MediaItem? = null,
    val error: String? = null
)

sealed interface MediaDetailAction {
    object ToggleWatchStatus : MediaDetailAction
    object Play : MediaDetailAction
}
```

### 4. Presentation Layer (Compose Screen)

```kotlin
// feature/details/MediaDetailScreen.kt

@Composable
fun MediaDetailRoute(
    viewModel: MediaDetailViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit,
    onBackClick: () -> Unit
) {
    // Collecte du state de manière lifecycle-aware
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaDetailScreen(
        uiState = uiState,
        onAction = viewModel::handleAction,
        onBackClick = onBackClick,
        onPlayClick = { mediaId -> onNavigateToPlayer(mediaId) }
    )
}

@Composable
fun MediaDetailScreen(
    uiState: MediaDetailUiState,
    onAction: (MediaDetailAction) -> Unit,
    onBackClick: () -> Unit,
    onPlayClick: (String) -> Unit
) {
    Scaffold(
        topBar = { 
            // Custom Transparent TopBar 
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.media != null) {
            LazyColumn(modifier = Modifier.padding(padding)) {
                // Hero Image
                item { 
                    AsyncImage(
                        model = uiState.media.thumbUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Title & Info
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = uiState.media.title, style = MaterialTheme.typography.headlineMedium)
                        Row {
                            Button(onClick = { onPlayClick(uiState.media.id) }) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Text("Lecture")
                            }
                            IconButton(onClick = { onAction(MediaDetailAction.ToggleWatchStatus) }) {
                                Icon(
                                    imageVector = if (uiState.media.isWatched) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = "Vu"
                                )
                            }
                        }
                        Text(text = uiState.media.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                // ... Seasons list, Cast list, etc.
            }
        }
    }
}
