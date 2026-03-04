# ANALYSE FONCTIONNELLE - PlexHubTV

## 1. SYNTHESE EXECUTIVE

| Metrique | Valeur |
|---|---|
| Modules Gradle | 11 (Clean Architecture) |
| ViewModels | 27 |
| Use Cases | 49 |
| Repositories | 23 implementations / 18 interfaces |
| Mappers | 5 |
| Sync Workers | 4 |
| Entites Room | 18 |
| Routes Navigation | 27 |
| Settings | 35+ preferences |
| DB Version | 35 (migrations depuis v11) |

### Score par categorie

| Categorie | Score | Grade |
|---|---|---|
| Architecture globale | 85/100 | A- |
| Coherence des patterns | 82/100 | B+ |
| Duplication de code | 65/100 | C+ |
| Couverture fonctionnelle | 80/100 | B |
| Maintenabilite | 75/100 | B- |

### Problemes identifies : 30 total (fusion Claude + Gemini)
- **Critiques** : 4
- **Moyens** : 12 (+MUT-06 MediaLibraryQueryBuilder, +MUT-07 MediaSourceStrategy)
- **Faibles** : 14

---

## 2. CARTOGRAPHIE DES PROBLEMES

### 2.1 Duplications de code

#### DUP-01 : Enrichment Logic (Criticite: ELEVEE)
- **Localisation** : `MediaDetailViewModel.PlayClicked` + `SeasonDetailViewModel.PlayEpisode`
- **Duplication** : ~70% - Meme flow (enrich → check sources → show dialog OR play) avec variations mineures
- **Impact** : Divergence de comportement (MediaDetail a cache-hit optimization, SeasonDetail non)
- **Fichiers** : `MediaDetailViewModel.kt:137-155`, `SeasonDetailViewModel.kt` (event handler PlayEpisode)

#### DUP-02 : Playback Flow (Criticite: ELEVEE)
- **Localisation** : `MediaDetailViewModel.playItem()` + `SeasonDetailViewModel.PlayEpisode`
- **Duplication** : ~65% - Meme pipeline (enrich → source selection → queue → PlaybackManager → navigate)
- **Impact** : Bug fixes doivent etre appliques dans 2 endroits
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

#### DUP-04 : Error Channel Boilerplate (Criticite: MOYENNE)
- **Localisation** : 11+ ViewModels
- **Duplication** : 100% identique
  ```kotlin
  private val _errorEvents = Channel<AppError>()
  val errorEvents = _errorEvents.receiveAsFlow()
  ```
- **Impact** : Boilerplate dans chaque ViewModel + chaque Screen (LaunchedEffect)

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
| StateFlow + Channel (standard) | 11 VMs (MediaDetail, SeasonDetail, Home, Hub, Library, Search...) |
| Sealed State | AuthVM |
| Combined Flow | FavoritesVM |
| Paging + StateFlow | LibraryVM |
| Delegated State | PlayerControlVM, TrackSelectionVM |
- **Impact** : Faible - les variations sont justifiees par le contexte, mais augmente la courbe d'apprentissage

#### INC-02 : Error Handling mixte
| Pattern | Utilise par |
|---|---|
| Channel-based (standard) | MediaDetail, SeasonDetail, Home, Hub, Library, Search |
| State-based | IPTV, CollectionDetail |
| Sealed state | Auth |
- **Impact** : Moyen - 3 patterns differents pour la meme chose

#### INC-03 : Search State utilise un enum
- `SearchViewModel` utilise `enum SearchState { Idle, Searching, Results, NoResults, Error }`
- Tous les autres utilisent `Boolean isLoading`
- **Impact** : Faible - inconsistance mineure

#### INC-04 : GetAllSourcesUseCase incomplet (CORRIGE)
- Ne couvrait pas les Backend servers
- **Status** : Fix applique dans cette session (ajout BackendRepository + SourceType.Backend)

#### INC-05 : Serialization mixte Gson/kotlinx-serialization
- `ServerMapper` utilise **Gson** pour JSON
- `BackendMediaMapper`, modeles core utilisent **kotlinx-serialization**
- `HubsRepositoryImpl` a un fallback pour migration Gson→kotlinx corruption
- **Impact** : Moyen - deux librairies JSON pour le meme job, risque de confusion

---

### 2.3 Features partiellement implementees

#### PART-01 : App Profile System (Criticite: ELEVEE)
- **Status** : UI squelette existe, logique non wired
- **Manquant** :
  - CRUD Profile (create/edit/delete) non connecte
  - Active profile non persiste (pas de DataStore save)
  - Kids mode / content restrictions non implementes
  - Profile selection au premier lancement absent
- **Fichiers** : `AppProfileSwitchScreen.kt`, `AppProfileViewModel.kt`, `ProfileRepository.kt`

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
- `getSimilarMedia()` n'existe que pour les serveurs Plex
- Backend et Xtream n'ont pas d'equivalent
- **Impact** : Section "More Like This" vide pour ces sources

---

### 2.4 Opportunites de mutualisation

#### MUT-01 : PlaybackInitializerUseCase
- **Probleme** : DUP-01 + DUP-02
- **Proposition** : Extraire un use case `PreparePlaybackUseCase` qui encapsule :
  1. Enrichment (avec skip Xtream)
  2. Source selection check
  3. Queue building
  4. PlaybackManager initialization
- **Reduction estimee** : ~200 LOC entre les 2 ViewModels
- **Complexite** : Moyenne

#### MUT-02 : BaseViewModel avec error/navigation channels
- **Probleme** : DUP-04
- **Proposition** : Creer `abstract class BaseViewModel` avec :
  - `_errorEvents: Channel<AppError>`
  - `errorEvents: Flow<AppError>`
  - `fun emitError(error: AppError)`
- **Reduction estimee** : ~50 LOC par ViewModel (x11 = ~550 LOC)
- **Complexite** : Simple

#### MUT-03 : ServerNameResolver utility
- **Probleme** : DUP-05
- **Proposition** : Creer un `@Singleton class ServerNameResolver` qui maintient une map `serverId → serverName` combinee (Plex + Backend + Xtream)
- **Reduction estimee** : ~80 LOC (4 duplications)
- **Complexite** : Simple

#### MUT-04 : UnificationIdCalculator centralise
- **Probleme** : DUP-07
- **Proposition** : Extraire dans `:core:model` une fonction pure :
  ```kotlin
  fun calculateUnificationId(imdbId: String?, tmdbId: String?, title: String?, year: Int?): String
  ```
  Utilisee par les 3 mappers
- **Reduction estimee** : ~30 LOC + elimination du risque de divergence
- **Complexite** : Simple

#### MUT-05 : Composable ErrorHandler
- **Probleme** : DUP-04 (cote UI)
- **Proposition** : Extraire un composable :
  ```kotlin
  @Composable
  fun HandleErrors(errorFlow: Flow<AppError>, snackbarHostState: SnackbarHostState, onRetry: () -> Unit)
  ```
- **Reduction estimee** : ~10 LOC par ecran (x15 = ~150 LOC)
- **Complexite** : Simple

#### MUT-06 : MediaLibraryQueryBuilder (source: Gemini 3.1 Pro)
- **Probleme** : `LibraryRepositoryImpl` contient 640+ lignes dont ~300 de construction SQL dynamique via `StringBuilder` + `SimpleSQLiteQuery`, repartie dans 3 methodes (`getLibraryContent`, `getFilteredCount`, `getIndexOfFirstItem`)
- **Proposition** : Extraire un `MediaLibraryQueryBuilder` testable :
  ```kotlin
  class MediaLibraryQueryBuilder {
      data class QueryResult(val sql: String, val args: Array<Any>)

      fun buildPagedQuery(
          isUnified: Boolean, mediaType: String,
          libraryKey: String, filter: String, sortOrder: String,
          genre: List<String>?, serverId: String?,
          excludedServerIds: List<String>, query: String?,
          sort: String, isDescending: Boolean,
      ): QueryResult

      fun buildCountQuery(...): QueryResult
      fun buildIndexQuery(...): QueryResult
  }
  ```
- **Reduction estimee** : ~250 LOC extraites, 3 methodes de 100+ lignes → 3 appels d'une ligne
- **Complexite** : Moyenne (SQL fragile — un espace manquant casse tout, TUs obligatoires)
- **Fichiers** : `LibraryRepositoryImpl.kt` (lignes 156-277, 471-529, 569-636)

#### MUT-07 : MediaSourceStrategy (source: Gemini 3.1 Pro)
- **Probleme** : 21 occurrences de `serverId.startsWith("xtream_")` / `startsWith("backend_")` reparties dans 5 fichiers, violant l'Open/Closed Principle. Ajouter un nouveau type de source (ex: Jellyfin) necessite de modifier tous ces fichiers.
- **Localisation** :
  - `MediaDetailRepositoryImpl.kt` : 10 occurrences (detail loading, seasons, episodes)
  - `PlayerControlViewModel.kt` : 5 occurrences (URL resolution, playback strategy)
  - `MediaDetailViewModel.kt` : 3 occurrences (enrichment skip, playback)
  - `SeasonDetailViewModel.kt` : 2 occurrences (enrichment skip)
  - `PlayerController.kt` : 1 occurrence (session tracking)
- **Proposition** : Interface `MediaSourceHandler` avec implementations par type :
  ```kotlin
  interface MediaSourceHandler {
      fun matches(serverId: String): Boolean
      suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem>
      suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>>
      suspend fun getStreamUrl(ratingKey: String, serverId: String): Result<String>
      fun needsEnrichment(): Boolean
  }

  // Implementations: PlexSourceHandler, XtreamSourceHandler, BackendSourceHandler
  // Injection: Set<MediaSourceHandler> via Hilt @IntoSet
  ```
- **Reduction estimee** : Elimination des 21 branchements, extensibilite pour nouveaux types de source
- **Complexite** : Elevee (refactoring transversal touchant le flow critique de playback)

---

## 3. PLAN DE REFACTORING DETAILLE

### VAGUE 1 : Securisation (Risque minimal)

#### Refactoring 1.1 : Centraliser UnificationIdCalculator
- **Probleme** : DUP-07 - 3 implementations differentes
- **Solution** : Fonction pure dans `:core:model`
- **Fichiers impactes** :
  - `core/model/.../UnificationId.kt` (nouveau)
  - `data/mapper/MediaMapper.kt` (appel au lieu de calculer)
  - `data/mapper/XtreamMediaMapper.kt` (appel au lieu de calculer)
  - `data/mapper/BackendMediaMapper.kt` (appel si DTO vide)
- **Etapes** :
  1. Creer `UnificationId.kt` avec la logique extraite de `MediaMapper.calculateUnificationId()`
  2. Remplacer dans `MediaMapper` par appel a la nouvelle fonction
  3. Remplacer dans `XtreamMediaMapper` par appel
  4. Ajouter appel dans `BackendMediaMapper` si `dto.unificationId` est vide
  5. Verifier que les tests existants passent
- **Points de test** : L'enrichment Room-first continue de fonctionner (unificationId identique)
- **Risque de regression** : Faible - extraction pure, meme logique
- **Rollback** : Revert du commit

#### Refactoring 1.2 : Creer ServerNameResolver
- **Probleme** : DUP-05 - server name map construite 4 fois
- **Solution** : `@Singleton class ServerNameResolver` dans `:data`
- **Fichiers impactes** :
  - `data/repository/ServerNameResolver.kt` (nouveau)
  - `data/repository/LibraryRepositoryImpl.kt` (injecter + utiliser)
  - `data/repository/MediaDetailRepositoryImpl.kt` (idem)
  - `data/repository/HubsRepositoryImpl.kt` (idem)
  - `data/repository/OnDeckRepositoryImpl.kt` (idem)
- **Etapes** :
  1. Creer `ServerNameResolver` avec methode `suspend fun getServerNameMap(): Map<String, String>`
  2. Injecter dans `LibraryRepositoryImpl`, remplacer le bloc inline
  3. Repeter pour les 3 autres repositories
  4. Verifier le badge multi et les noms dans source selection
- **Points de test** : Badge multi affiche les bons noms, source selection dialog correct
- **Risque de regression** : Faible
- **Rollback** : Revert du commit

#### Refactoring 1.3 : Extraire URL Resolution helper
- **Probleme** : DUP-06 - pattern `"$baseUrl$path?X-Plex-Token=$token"` repete
- **Solution** : Extension function ou methode dans `MediaUrlResolver`
- **Fichiers impactes** : LibraryRepositoryImpl, HubsRepositoryImpl, OnDeckRepositoryImpl
- **Risque** : Faible

---

### VAGUE 2 : Consolidation (Risque faible)

#### Refactoring 2.1 : BaseViewModel avec error channel
- **Probleme** : DUP-04 - boilerplate identique dans 11+ ViewModels
- **Solution** :
  ```kotlin
  abstract class BaseViewModel : ViewModel() {
      private val _errorEvents = Channel<AppError>()
      val errorEvents = _errorEvents.receiveAsFlow()
      protected suspend fun emitError(error: AppError) = _errorEvents.send(error)
  }
  ```
- **Fichiers impactes** : Tous les ViewModels avec error channel (~11)
- **Etapes** :
  1. Creer `BaseViewModel` dans `app/feature/common/`
  2. Migrer un ViewModel simple (ex: CollectionDetailVM) en premier
  3. Verifier que l'error handling fonctionne
  4. Migrer progressivement les autres
- **Points de test** : Errors affichees correctement sur chaque ecran migre
- **Risque** : Faible - backward compatible (les VMs non migres continuent de fonctionner)
- **Rollback** : Revert par ViewModel (chaque migration est independante)

#### Refactoring 2.2 : Composable HandleErrors
- **Probleme** : DUP-04 cote UI - LaunchedEffect identique dans 15 ecrans
- **Solution** :
  ```kotlin
  @Composable
  fun HandleErrors(errorFlow: Flow<AppError>, snackbarHostState: SnackbarHostState)
  ```
- **Fichiers impactes** : Tous les Screen composables avec error handling
- **Etapes** :
  1. Creer dans `core/ui/`
  2. Remplacer dans un ecran simple d'abord
  3. Migrer progressivement
- **Risque** : Faible

#### Refactoring 2.3 : Completer GetAllSourcesUseCase (FAIT)
- **Status** : Deja corrige dans cette session
- Backend ajoute, `SourceType.Backend` cree

#### Refactoring 2.4 : Extraire MediaLibraryQueryBuilder (source: Gemini)
- **Probleme** : MUT-06 - 300+ lignes de SQL dynamique inline dans LibraryRepositoryImpl
- **Solution** : Classe dediee `MediaLibraryQueryBuilder` dont le seul role est de retourner un `SimpleSQLiteQuery` en fonction des parametres
- **Fichiers impactes** :
  - `data/repository/MediaLibraryQueryBuilder.kt` (nouveau)
  - `data/repository/LibraryRepositoryImpl.kt` (injecter + remplacer les 3 blocs SQL inline)
- **Etapes** :
  1. Creer `MediaLibraryQueryBuilder` avec `buildPagedQuery()`, `buildCountQuery()`, `buildIndexQuery()`
  2. Extraire le SQL de `getLibraryContent()` (lignes 162-277) vers `buildPagedQuery()`
  3. Extraire le SQL de `getFilteredCount()` (lignes 474-529) vers `buildCountQuery()`
  4. Extraire le SQL de `getIndexOfFirstItem()` (lignes 572-636) vers `buildIndexQuery()`
  5. Ecrire des TUs sur le QueryBuilder avec differentes combinaisons (unified/non-unified, tri, filtre, genre, search, exclusions)
  6. Remplacer le code inline dans LibraryRepositoryImpl
- **Points de test** : Verification du tri, filtrage, recherche et badge multi sur Library + Home
- **Risque de regression** : Moyen (un espace manquant en SQL = crash). TUs obligatoires avant remplacement.
- **Rollback** : Revert du commit (SQL inline restore)

#### Refactoring 2.5 : Migrer ServerMapper de Gson a kotlinx-serialization
- **Probleme** : INC-05 - Gson utilise seulement dans ServerMapper
- **Solution** : Remplacer par kotlinx-serialization (deja utilise partout ailleurs)
- **Fichiers** : `data/mapper/ServerMapper.kt`
- **Risque** : Moyen (changement de format de serialization des connection candidates)

---

### VAGUE 3 : Refactoring structurel (Risque modere)

#### Refactoring 3.1 : Extraire PreparePlaybackUseCase
- **Probleme** : DUP-01 + DUP-02 - enrichment + playback flow duplique
- **Solution** : Use case dans `:domain` qui encapsule le flow complet :
  ```kotlin
  class PreparePlaybackUseCase @Inject constructor(
      private val enrichMediaItemUseCase: EnrichMediaItemUseCase,
      private val playbackManager: PlaybackManager,
  ) {
      sealed class Result {
          data class ReadyToPlay(val item: MediaItem) : Result()
          data class NeedsSourceSelection(val item: MediaItem, val sources: List<MediaSource>) : Result()
      }
      suspend operator fun invoke(item: MediaItem): Result
  }
  ```
- **Fichiers impactes** :
  - `domain/usecase/PreparePlaybackUseCase.kt` (nouveau)
  - `MediaDetailViewModel.kt` (simplifier PlayClicked + playItem)
  - `SeasonDetailViewModel.kt` (simplifier PlayEpisode)
- **Etapes** :
  1. Creer le use case avec la logique extraite de MediaDetailVM
  2. Integrer dans MediaDetailVM, verifier playback fonctionne
  3. Integrer dans SeasonDetailVM, verifier playback fonctionne
  4. Supprimer l'ancien code
- **Points de test** : Play depuis detail, play depuis season, source selection dialog, Xtream skip
- **Risque** : Moyen - touche au flow de playback critique
- **Rollback** : Revert du use case + restauration de l'ancien code dans les 2 VMs

#### Refactoring 3.2 : Extraire Loading Template
- **Probleme** : DUP-03 - template de chargement identique
- **Solution** : Extension function :
  ```kotlin
  fun <T> ViewModel.loadResource(
      fetch: suspend () -> Flow<Result<T>>,
      onLoading: () -> Unit,
      onSuccess: (T) -> Unit,
      onFailure: (Throwable) -> Unit,
  )
  ```
- **Fichiers impactes** : Tous les VMs avec pattern load
- **Risque** : Moyen

#### Refactoring 3.3 : MediaSourceStrategy (source: Gemini)
- **Probleme** : MUT-07 - 21 branchements `startsWith("xtream_")`/`startsWith("backend_")` dans 5 fichiers
- **Solution** : Interface `MediaSourceHandler` avec implementations par type de source, injectees via Hilt `@IntoSet`
- **Fichiers impactes** :
  - `domain/source/MediaSourceHandler.kt` (nouveau - interface)
  - `data/source/PlexSourceHandler.kt` (nouveau)
  - `data/source/XtreamSourceHandler.kt` (nouveau)
  - `data/source/BackendSourceHandler.kt` (nouveau)
  - `data/repository/MediaDetailRepositoryImpl.kt` (10 branchements → delegation)
  - `app/feature/player/PlayerControlViewModel.kt` (5 branchements → delegation)
  - `app/feature/details/MediaDetailViewModel.kt` (3 branchements → delegation)
  - `app/feature/details/SeasonDetailViewModel.kt` (2 branchements → delegation)
  - `app/feature/player/controller/PlayerController.kt` (1 branchement → delegation)
- **Etapes** :
  1. Definir `MediaSourceHandler` dans `:domain` avec `matches()`, `getDetail()`, `getSeasons()`, `getStreamUrl()`, `needsEnrichment()`
  2. Implementer `PlexSourceHandler` — delegation aux repos Plex existants
  3. Implementer `XtreamSourceHandler` — encapsule la logique xtream_ actuellement inline
  4. Implementer `BackendSourceHandler` — encapsule la logique backend_ actuellement inline
  5. Creer `MediaSourceResolver` : `@Singleton` qui prend `Set<MediaSourceHandler>` et expose `fun resolve(serverId): MediaSourceHandler`
  6. Migrer `MediaDetailRepositoryImpl` en premier (10 branchements, fichier le plus impacte)
  7. Migrer `PlayerControlViewModel` (5 branchements)
  8. Migrer les ViewModels restants (5 branchements)
  9. Supprimer les branchements inline
- **Points de test** : Play depuis Plex, Xtream, Backend. Detail loading. Season loading. Source selection dialog.
- **Risque** : Eleve (touche au flow de playback critique + detail loading)
- **Rollback** : Revert des handlers + restauration des branchements inline (chaque fichier migre independamment)

#### Refactoring 3.4 : Splitter LibraryUiState (16 champs)
- **Probleme** : LibraryUiState a 16+ champs melengeant display, filter et selection
- **Solution** : Decomposer en sous-states :
  ```kotlin
  data class LibraryUiState(
      val display: LibraryDisplayState,
      val filters: LibraryFilterState,
      val selection: LibrarySelectionState,
  )
  ```
- **Fichiers impactes** : `LibraryViewModel.kt`, `LibraryScreen.kt`
- **Risque** : Moyen - impact sur toute la UI library

---

### VAGUE 4 : Optimisations architecturales (Risque controle)

#### Refactoring 4.1 : Completer le systeme de Profiles
- **Probleme** : PART-01 - App Profiles non fonctionnel
- **Solution** : Wire les methodes CRUD existantes dans ProfileRepository
- **Fichiers** : AppProfileSwitchScreen, AppProfileViewModel, ProfileRepository, DataStore
- **Risque** : Eleve (nouvelle feature)

#### Refactoring 4.2 : Performance Tracking uniforme
- **Probleme** : PART-05 - Seulement 2 VMs sur 27 trackent les performances
- **Solution** : Ajouter PerformanceTracker dans Library, Search, Home, Hub
- **Risque** : Faible (ajout, pas de modification)

#### Refactoring 4.3 : Potential Use Case cleanup
- **Probleme** : `GetNextEpisodeUseCase` vs `EpisodeNavigationUseCase` overlap
- **Solution** : Evaluer si l'un peut etre supprime
- **Risque** : Moyen

---

## 4. ROADMAP DE MISE EN OEUVRE

### VAGUE 1 - Securisation (1-2 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 1.1 | UnificationIdCalculator | 1h | Faible | Claude |
| 1.2 | ServerNameResolver | 2h | Faible | Claude |
| 1.3 | URL Resolution helper | 1h | Faible | Claude + Gemini |

### VAGUE 2 - Consolidation (3-4 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 2.1 | BaseViewModel | 3h | Faible | Claude |
| 2.2 | Composable HandleErrors | 2h | Faible | Claude |
| 2.3 | GetAllSourcesUseCase | FAIT | - | Claude |
| 2.4 | MediaLibraryQueryBuilder | 4h | Moyen | Gemini |
| 2.5 | ServerMapper Gson→kotlinx | 2h | Moyen | Claude |

### VAGUE 3 - Structurel (5-7 jours)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 3.1 | PreparePlaybackUseCase | 4h | Moyen | Claude |
| 3.2 | Loading Template | 3h | Moyen | Claude |
| 3.3 | MediaSourceStrategy | 6h | Eleve | Gemini |
| 3.4 | Split LibraryUiState | 4h | Moyen | Claude |

### VAGUE 4 - Optimisations (variable)
| # | Refactoring | Effort | Risque | Source |
|---|---|---|---|---|
| 4.1 | Profile System | 8h+ | Eleve | Claude |
| 4.2 | Performance Tracking | 3h | Faible | Claude + Gemini |
| 4.3 | Use Case cleanup | 2h | Moyen | Claude |

---

## 5. ANNEXES

### A. Inventaire complet des ViewModels (27)
MediaDetailVM, SeasonDetailVM, MediaEnrichmentVM, HomeVM, HubVM, LibraryVM, SearchVM, FavoritesVM, HistoryVM, AuthVM, PlayerControlVM, TrackSelectionVM, PlaybackStatsVM, CollectionDetailVM, PlexHomeSwitcherVM, AppProfileVM, IptvVM, SettingsVM, XtreamSetupVM, LibrarySelectionVM, LoadingVM, SplashVM, DebugVM, DownloadsVM, MainVM, ServerStatusVM, XtreamCategorySelectionVM

### B. Architecture des modules
```
:app (features + DI + workers)
├── :domain (use cases + repo interfaces)
├── :data (repo impls + mappers + sync)
├── :core:model (domain models)
├── :core:common (utils + dispatchers)
├── :core:network (Retrofit + API services)
├── :core:database (Room + DAOs + migrations)
├── :core:datastore (DataStore + SecurePrefs)
├── :core:navigation (routes)
├── :core:designsystem (theme)
└── :core:ui (shared composables)
```

### C. Metriques de coherence
| Aspect | Score |
|---|---|
| StateFlow usage | 95% |
| Channel navigation events | 90% |
| Error handling | 85% |
| Naming conventions | 95% |
| Composable reuse | 70% |
| Loading pattern | 75% |
