# ANALYSE FONCTIONNELLE - PlexHubTV

> **Derniere mise a jour** : 23 mars 2026

## 1. SYNTHESE EXECUTIVE

| Metrique | Valeur |
|---|---|
| Modules Gradle | 11 (Clean Architecture) |
| ViewModels | 35 |
| Use Cases | 29 |
| Repositories | 24 implementations / 25 interfaces |
| Mappers | 6 |
| Sync Workers | 6 |
| Entites Room | 24 |
| DAOs | 21 |
| Routes Navigation | 31 |
| Settings | 45+ preferences |
| DB Version | 46 (migrations depuis v11) |
| Tests | 38 fichiers |

### Score par categorie

| Categorie | Score | Grade |
|---|---|---|
| Architecture globale | 88/100 | A |
| Coherence des patterns | 85/100 | A- |
| Duplication de code | 72/100 | B- |
| Couverture fonctionnelle | 85/100 | A- |
| Maintenabilite | 80/100 | B |

### Ameliorations depuis la derniere analyse (14 fev 2026)
- BaseViewModel unifie le error handling (DUP-04 resolu)
- MediaLibraryQueryBuilder extrait (MUT-06 resolu)
- PreparePlaybackUseCase extrait (DUP-01/DUP-02 partiellement resolu)
- App Profiles complet (PART-01 resolu)
- Integration Jellyfin ajoutee (nouvelle source multi-serveur)
- 38 tests unitaires (vs 18 precedemment)
- ErrorMessageResolver pour messages d'erreur localises FR/EN
- @Immutable annotations pour optimisation recomposition Compose
- Firebase Analytics extrait en AnalyticsService

### Problemes restants : 22 total
- **Critiques** : 1 (MUT-07 MediaSourceStrategy)
- **Moyens** : 8
- **Faibles** : 13

---

## 2. CARTOGRAPHIE DES PROBLEMES

### 2.1 Duplications de code

#### DUP-01 : Enrichment Logic (Criticite: REDUITE — partiellement resolu)
- **Status** : PreparePlaybackUseCase extrait, mais les ViewModels conservent une couche d'adaptation
- **Localisation** : `MediaDetailViewModel.PlayClicked` + `SeasonDetailViewModel.PlayEpisode`
- **Duplication residuelle** : ~30% (vs 70% avant)
- **Fichiers** : `MediaDetailViewModel.kt`, `SeasonDetailViewModel.kt`

#### DUP-02 : Playback Flow (Criticite: REDUITE — partiellement resolu)
- **Status** : Le flow commun est dans `PreparePlaybackUseCase`
- **Duplication residuelle** : ~25% (vs 65% avant) — adaptation ViewModel-specifique
- **Fichiers** : `MediaDetailViewModel.kt`, `SeasonDetailViewModel.kt`

#### DUP-03 : Loading Template (Criticite: MOYENNE)
- **Localisation** : MediaDetailVM, SeasonDetailVM, CollectionDetailVM, HomeVM, HubVM
- **Duplication** : ~85% - Template identique :
  ```kotlin
  viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val result = useCase(args).first()
      result.fold(onSuccess = { ... }, onFailure = { ... })
  }
  ```
- **Impact** : ~300 LOC dupliquees, maintenance accrue

#### ~~DUP-04 : Error Channel Boilerplate~~ → RESOLU
- **Status** : ✅ FAIT — `BaseViewModel` fournit `errorEvents` et `emitError()` uniformement

#### DUP-05 : Server Name Map Building (Criticite: MOYENNE)
- **Localisation** : LibraryRepositoryImpl, MediaDetailRepositoryImpl, HubsRepositoryImpl, OnDeckRepositoryImpl
- **Duplication** : 4 fois le meme pattern de construction `serverId → serverName`
- **Impact** : Bug du multi-badge (backend exclu) etait present dans 4 endroits

#### DUP-06 : URL Resolution Pattern (Criticite: FAIBLE)
- **Localisation** : LibraryRepositoryImpl, HubsRepositoryImpl, OnDeckRepositoryImpl, PlaybackRepositoryImpl
- **Duplication** : `"$baseUrl$path?X-Plex-Token=$token"` repete 4+ fois
- **Impact** : Faible mais pourrait etre centralise dans `MediaUrlResolver`

#### DUP-07 : unificationId Calculation (Criticite: ELEVEE)
- **Localisation** : `MediaMapper.calculateUnificationId()`, `XtreamMediaMapper.buildUnificationId()`, `BackendMediaMapper` (copie du DTO)
- **Duplication** : 3 implementations differentes du meme concept
- **Impact** : Risque d'incoherence entre sources (si une implementation diverge, l'enrichment echoue)

#### DUP-08 : Favorite Checking (Criticite: FAIBLE)
- **Localisation** : MediaDetailVM, SeasonDetailVM, HubVM
- **Duplication** : ~60% - Meme flow avec modeles differents
- **Impact** : Faible, les variations sont justifiees

---

### 2.2 Incoherences architecturales

#### INC-01 : State Management heterogene
| Pattern | ViewModels |
|---|---|
| BaseViewModel + StateFlow + Channel (standard) | 20+ VMs (majorite) |
| Sealed State | AuthVM |
| Combined Flow | FavoritesVM |
| Paging + StateFlow | LibraryVM |
| Delegated State | PlayerControlVM, TrackSelectionVM |
- **Impact** : Faible - les variations sont justifiees par le contexte

#### INC-02 : Error Handling — largement unifie
| Pattern | Utilise par |
|---|---|
| BaseViewModel channel (standard) | Majorite des VMs |
| State-based | IPTV, CollectionDetail |
| Sealed state | Auth |
- **Impact** : Reduit (2 patterns secondaires vs 1 dominant)

#### INC-03 : Search State utilise un enum
- `SearchViewModel` utilise `enum SearchState { Idle, Searching, Results, NoResults, Error }`
- Tous les autres utilisent `Boolean isLoading`
- **Impact** : Faible - inconsistance mineure

#### ~~INC-04 : GetAllSourcesUseCase incomplet~~ → RESOLU
- **Status** : ✅ FAIT — Backend + Jellyfin ajoutes

#### INC-05 : Serialization mixte Gson/kotlinx-serialization
- `ServerMapper` utilise **Gson** pour JSON
- `BackendMediaMapper`, modeles core utilisent **kotlinx-serialization**
- `HubsRepositoryImpl` a un fallback pour migration Gson→kotlinx corruption
- **Impact** : Moyen - deux librairies JSON pour le meme job, risque de confusion

---

### 2.3 Features partiellement implementees

#### ~~PART-01 : App Profile System~~ → COMPLET ✅
- **Status** : ✅ FAIT — CRUD complet, persistence Room avec flag `isActive`, selection au demarrage quand 2+ profils
- **Fichiers** : `AppProfileSwitchScreen.kt`, `AppProfileViewModel.kt`, `ProfileRepository.kt`, `ProfileDao.kt`, `ProfileEntity.kt`
- **Tests** : `AppProfileViewModelTest.kt`
- **Restant** : Kids mode / content restrictions (filtrage de contenu non encore applique)

#### PART-02 : Plex Home PIN Dialog (Criticite: FAIBLE)
- **Status** : UI existe mais non testee
- **Fichier** : `PlexHomePinDialog` dans `PlexHomeSwitcherScreen.kt`

#### PART-03 : SyncOfflineContentUseCase (Criticite: FAIBLE)
- **Status** : Placeholder vide
- **Fichier** : `domain/usecase/SyncOfflineContentUseCase.kt`

#### PART-04 : ResolveEpisodeSourcesUseCase (Criticite: FAIBLE)
- **Status** : Interface definie, implementation existante mais usage incertain
- **Fichier** : `domain/usecase/` (interface only)

#### PART-05 : Performance Tracking partiel (Criticite: MOYENNE)
- Seulement MediaDetailVM et SeasonDetailVM utilisent `PerformanceTracker`
- Absent de : Library, Search, Home, Hub
- **Impact** : Pas d'observabilite sur les ecrans les plus utilises

#### PART-06 : Similar Media absente pour Backend/Xtream (Criticite: FAIBLE)
- `getSimilarMedia()` n'existe que pour Plex et Jellyfin
- Backend et Xtream n'ont pas d'equivalent
- **Impact** : Section "More Like This" vide pour ces sources

#### PART-07 : Jellyfin Integration → COMPLET ✅
- **Status** : ✅ FAIT — Integration complete (auth, browse, search, playback, sync, images)
- **Fichiers cles** : `JellyfinApiService.kt`, `JellyfinClient.kt`, `JellyfinSourceHandler.kt`, `JellyfinMapper.kt`, `JellyfinSetupViewModel.kt`
- **Tests** : Mapper, API, integration dans LibrarySyncWorker
- **Voir** : Section 17 de ARCHITECTURE.md pour details complets

---

### 2.4 Opportunites de mutualisation

#### ~~MUT-01 : PlaybackInitializerUseCase~~ → RESOLU ✅
- **Status** : ✅ FAIT — `PreparePlaybackUseCase` extrait dans `:domain`
- **Fichier** : `domain/usecase/PreparePlaybackUseCase.kt`
- Utilise par MediaDetailVM et SeasonDetailVM

#### ~~MUT-02 : BaseViewModel avec error/navigation channels~~ → RESOLU ✅
- **Status** : ✅ FAIT — `BaseViewModel` dans `app/feature/common/`
- ~10 ViewModels migres, error handling unifie

#### MUT-03 : ServerNameResolver utility
- **Probleme** : DUP-05
- **Proposition** : Creer un `@Singleton class ServerNameResolver` qui maintient une map `serverId → serverName` combinee (Plex + Backend + Xtream + Jellyfin)
- **Reduction estimee** : ~80 LOC (4 duplications)
- **Complexite** : Simple

#### MUT-04 : UnificationIdCalculator centralise
- **Probleme** : DUP-07
- **Proposition** : Extraire dans `:core:model` une fonction pure :
  ```kotlin
  fun calculateUnificationId(imdbId: String?, tmdbId: String?, title: String?, year: Int?): String
  ```
  Utilisee par les 3+ mappers (Media, Xtream, Backend, Jellyfin)
- **Reduction estimee** : ~30 LOC + elimination du risque de divergence
- **Complexite** : Simple

#### MUT-05 : Composable ErrorHandler
- **Probleme** : DUP-04 (cote UI) — partiellement resolu par BaseViewModel mais le LaunchedEffect reste duplique
- **Proposition** : Extraire un composable :
  ```kotlin
  @Composable
  fun HandleErrors(errorFlow: Flow<AppError>, snackbarHostState: SnackbarHostState, onRetry: () -> Unit)
  ```
- **Reduction estimee** : ~10 LOC par ecran (x15 = ~150 LOC)
- **Complexite** : Simple

#### ~~MUT-06 : MediaLibraryQueryBuilder~~ → RESOLU ✅
- **Status** : ✅ FAIT — Classe extraite dans `data/repository/MediaLibraryQueryBuilder.kt`
- **Tests** : `MediaLibraryQueryBuilderTest.kt`
- Construction SQL avec listes de colonnes explicites (evite le piege Room @RawQuery)

#### MUT-07 : MediaSourceStrategy (source: Gemini 3.1 Pro) — PRIORITE HAUTE
- **Probleme** : Branchements `serverId.startsWith("xtream_")` / `startsWith("backend_")` / `startsWith("jellyfin_")` repartis dans 5+ fichiers. L'ajout de Jellyfin a augmente le nombre de branchements.
- **Localisation** :
  - `MediaDetailRepositoryImpl.kt` : 10+ occurrences
  - `PlayerControlViewModel.kt` : 5+ occurrences
  - `MediaDetailViewModel.kt` : 3+ occurrences
  - `SeasonDetailViewModel.kt` : 2+ occurrences
  - `PlayerController.kt` : 1+ occurrence
- **Proposition** : Interface `MediaSourceHandler` avec implementations par type :
  ```kotlin
  interface MediaSourceHandler {
      fun matches(serverId: String): Boolean
      suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem>
      suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>>
      suspend fun getStreamUrl(ratingKey: String, serverId: String): Result<String>
      fun needsEnrichment(): Boolean
  }
  // Implementations: PlexSourceHandler, XtreamSourceHandler, BackendSourceHandler, JellyfinSourceHandler
  ```
- **Note** : `JellyfinSourceHandler` existe deja — il pourrait servir de reference pour les autres
- **Reduction estimee** : Elimination de 25+ branchements, extensibilite pour nouveaux types de source
- **Complexite** : Elevee (refactoring transversal touchant le flow critique de playback)

---

## 3. PLAN DE REFACTORING DETAILLE

### VAGUE 1 : Securisation (Risque minimal)

#### Refactoring 1.1 : Centraliser UnificationIdCalculator
- **Probleme** : DUP-07 - 3+ implementations differentes
- **Solution** : Fonction pure dans `:core:model`
- **Fichiers impactes** :
  - `core/model/.../UnificationId.kt` (nouveau)
  - `data/mapper/MediaMapper.kt` (appel au lieu de calculer)
  - `data/mapper/XtreamMediaMapper.kt` (appel au lieu de calculer)
  - `data/mapper/BackendMediaMapper.kt` (appel si DTO vide)
  - `data/mapper/JellyfinMapper.kt` (appel si applicable)
- **Risque de regression** : Faible - extraction pure, meme logique
- **Rollback** : Revert du commit

#### Refactoring 1.2 : Creer ServerNameResolver
- **Probleme** : DUP-05 - server name map construite 4+ fois
- **Solution** : `@Singleton class ServerNameResolver` dans `:data`
- **Fichiers impactes** : LibraryRepositoryImpl, MediaDetailRepositoryImpl, HubsRepositoryImpl, OnDeckRepositoryImpl
- **Risque** : Faible

#### Refactoring 1.3 : Extraire URL Resolution helper
- **Probleme** : DUP-06 - pattern URL repete
- **Solution** : Extension function dans `MediaUrlResolver`
- **Risque** : Faible

---

### VAGUE 2 : Consolidation (Risque faible)

#### ~~Refactoring 2.1 : BaseViewModel~~ → ✅ FAIT

#### Refactoring 2.2 : Composable HandleErrors
- **Probleme** : LaunchedEffect identique dans 15 ecrans
- **Solution** : Composable reutilisable dans `core/ui/`
- **Risque** : Faible

#### ~~Refactoring 2.3 : GetAllSourcesUseCase~~ → ✅ FAIT

#### ~~Refactoring 2.4 : MediaLibraryQueryBuilder~~ → ✅ FAIT

#### Refactoring 2.5 : Migrer ServerMapper de Gson a kotlinx-serialization
- **Probleme** : INC-05 - Gson utilise seulement dans ServerMapper
- **Solution** : Remplacer par kotlinx-serialization (deja utilise partout ailleurs)
- **Fichiers** : `data/mapper/ServerMapper.kt`
- **Risque** : Moyen (changement de format de serialization des connection candidates)

---

### VAGUE 3 : Refactoring structurel (Risque modere)

#### ~~Refactoring 3.1 : PreparePlaybackUseCase~~ → ✅ FAIT

#### Refactoring 3.2 : Extraire Loading Template
- **Probleme** : DUP-03 - template de chargement identique
- **Solution** : Extension function `loadResource()`
- **Fichiers impactes** : Tous les VMs avec pattern load
- **Risque** : Moyen

#### Refactoring 3.3 : MediaSourceStrategy (PRIORITE)
- **Probleme** : MUT-07 - 25+ branchements dans 5+ fichiers (augmente avec Jellyfin)
- **Solution** : Interface `MediaSourceHandler` + implementations par type
- **Note** : `JellyfinSourceHandler` est un exemple existant du pattern cible
- **Risque** : Eleve (flow critique de playback)

#### Refactoring 3.4 : Splitter LibraryUiState (16 champs)
- **Probleme** : LibraryUiState melange display, filter et selection
- **Solution** : Decomposer en sous-states
- **Risque** : Moyen

---

### VAGUE 4 : Optimisations architecturales (Risque controle)

#### ~~Refactoring 4.1 : Completer le systeme de Profiles~~ → ✅ FAIT

#### Refactoring 4.2 : Performance Tracking uniforme
- **Probleme** : PART-05 - Seulement 2 VMs trackent les performances
- **Solution** : Ajouter PerformanceTracker dans Library, Search, Home, Hub
- **Risque** : Faible

#### Refactoring 4.3 : Potential Use Case cleanup
- **Probleme** : `GetNextEpisodeUseCase` vs `EpisodeNavigationUseCase` overlap
- **Solution** : Evaluer si l'un peut etre supprime
- **Risque** : Moyen

---

## 4. ROADMAP DE MISE EN OEUVRE (mise a jour mars 2026)

### Deja complete ✅
| # | Refactoring | Status |
|---|---|---|
| 2.1 | BaseViewModel | ✅ FAIT |
| 2.3 | GetAllSourcesUseCase | ✅ FAIT |
| 2.4 | MediaLibraryQueryBuilder | ✅ FAIT |
| 3.1 | PreparePlaybackUseCase | ✅ FAIT |
| 4.1 | Profile System | ✅ FAIT |

### VAGUE 1 - Securisation (1-2 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 1.1 | UnificationIdCalculator | 1h | Faible | Claude |
| 1.2 | ServerNameResolver | 2h | Faible | Claude |
| 1.3 | URL Resolution helper | 1h | Faible | Claude + Gemini |

### VAGUE 2 - Consolidation (restant : 2-3 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 2.2 | Composable HandleErrors | 2h | Faible | Claude |
| 2.5 | ServerMapper Gson→kotlinx | 2h | Moyen | Claude |

### VAGUE 3 - Structurel (restant : 4-5 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 3.2 | Loading Template | 3h | Moyen | Claude |
| 3.3 | MediaSourceStrategy | 6h | Eleve | Gemini |
| 3.4 | Split LibraryUiState | 4h | Moyen | Claude |

### VAGUE 4 - Optimisations (restant : variable)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 4.2 | Performance Tracking | 3h | Faible | Claude + Gemini |
| 4.3 | Use Case cleanup | 2h | Moyen | Claude |

---

## 5. ANNEXES

### A. Inventaire complet des ViewModels (35)

AuthVM, PlexHomeSwitcherVM, SplashVM, LoadingVM, MainVM, HomeVM, HubVM, LibraryVM, LibrarySelectionVM, SearchVM, MediaDetailVM, SeasonDetailVM, PersonDetailVM, MediaEnrichmentVM, CollectionDetailVM, FavoritesVM, HistoryVM, DownloadsVM, PlaylistListVM, PlaylistDetailVM, SettingsVM, ServerStatusVM, SubtitleStyleVM, DebugVM, AppProfileVM, IptvVM, XtreamSetupVM, XtreamCategorySelectionVM, JellyfinSetupVM, ScreensaverVM, PlayerControlVM, PlaybackStatsVM, TrackSelectionVM, BaseViewModel (abstract)

### B. Inventaire complet des Use Cases (29)

DeleteMediaUseCase, EnrichMediaItemUseCase, EpisodeNavigationUseCase, FilterContentByAgeUseCase, GetCollectionUseCase, GetEnabledServerIdsUseCase, GetFavoritesUseCase, GetLibraryContentUseCase, GetLibraryIndexUseCase, GetMediaCollectionsUseCase, GetMediaDetailUseCase, GetNextEpisodeUseCase, GetPlayQueueUseCase, GetSimilarMediaUseCase, GetUnifiedHomeContentUseCase, GetUnifiedSeasonsUseCase, GetWatchHistoryUseCase, GetXtreamCategoriesUseCase, IsFavoriteUseCase, PrefetchNextEpisodeUseCase, PreparePlaybackUseCase, ResolveEpisodeSourcesUseCase, SearchAcrossServersUseCase, SortOnDeckUseCase, SyncJellyfinLibraryUseCase, SyncWatchlistUseCase, SyncXtreamLibraryUseCase, ToggleFavoriteUseCase, ToggleWatchStatusUseCase

### C. Inventaire complet des Repository Interfaces (25)

AccountRepository, AuthRepository, BackendRepository, CategoryRepository, DownloadsRepository, FavoritesRepository, HubsRepository, IptvRepository, JellyfinServerRepository, LibraryRepository, MediaDetailRepository, OfflineWatchSyncRepository, OnDeckRepository, PersonFavoriteRepository, PlaybackRepository, PlaylistRepository, ProfileRepository, SearchRepository, SettingsRepository, SyncRepository, TrackPreferenceRepository, WatchlistRepository, XtreamAccountRepository, XtreamSeriesRepository, XtreamVodRepository

### D. Architecture des modules
```
:app (features + DI + workers)
├── :domain (use cases + repo interfaces)
├── :data (repo impls + mappers + sync)
├── :core:model (domain models)
├── :core:common (utils + dispatchers)
├── :core:network (Retrofit + API services + AuthEventBus)
├── :core:database (Room + DAOs + 35 migrations)
├── :core:datastore (DataStore + SecurePrefs)
├── :core:navigation (routes)
├── :core:designsystem (theme)
└── :core:ui (shared composables)
```

### E. Metriques de coherence
| Aspect | Score |
|---|---|
| StateFlow usage | 97% |
| BaseViewModel adoption | 90% |
| Channel navigation events | 92% |
| Error handling (BaseViewModel) | 90% |
| Naming conventions | 95% |
| Composable reuse | 75% |
| Loading pattern | 75% |
| @Immutable annotations | 80% |
