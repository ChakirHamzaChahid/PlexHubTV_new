# PlexHubTV — Audit Complet V2

> **Date** : 10 février 2026
> **Branche auditée** : `claude/android-tv-performance-audit-bTs5u`
> **Statistiques** : 265 fichiers Kotlin · 7 modules Gradle · 22 fichiers de tests · 0 fichier Java

---

## Résumé exécutif

PlexHubTV est une application Android TV Kotlin + Jetpack Compose structurée en Clean Architecture (UI / Domain / Data) avec Hilt, Room, Retrofit et Coil. La base architecturale est **solide** : les couches sont bien séparées, les 24 use cases isolent correctement la logique métier, les 16 interfaces de repository exposent des contrats propres, et la synchronisation offline avec queue différée est bien pensée.

Cependant, l'audit révèle **4 problèmes systémiques majeurs** :
1. **12 écrans sur 38 utilisent encore `LazyColumn`/`LazyRow` standard** au lieu de `TvLazyColumn`/`TvLazyRow`, rendant la navigation D-Pad non fonctionnelle sur ces écrans.
2. **Le modèle `MediaItem` est un "god model" de 46 champs avec `@Parcelize`**, propagé dans `Hub.items: List<MediaItem>`, causant une sérialisation massive à chaque sauvegarde d'état.
3. **7 fichiers Composable créent des `MutableInteractionSource()` sans `remember {}`**, provoquant des fuites mémoire à chaque recomposition.
4. **Une faille de sécurité** : la validation du PIN des profils Plex Home est désactivée (commentée), et les tokens/clés API sont stockés en clair dans DataStore.

Le plan d'action priorisé ci-dessous couvre 47 items répartis en 3 niveaux de priorité.

---

## 1. Architecture & organisation du code

### 1.1 Structure globale

```
PlexHubTV_new/
├── app/                          # Couche Présentation + Data (138 fichiers)
│   ├── core/                     #   Infrastructure locale (image, network, util, nav, datastore)
│   ├── data/                     #   Implémentations des repositories, mappers, paging
│   └── feature/                  #   17 features (auth, home, details, player, library, search…)
├── domain/                       # Couche Métier (40 fichiers + 6 tests)
│   ├── repository/               #   16 interfaces
│   ├── usecase/                  #   24 use cases
│   └── service/                  #   PlaybackManager
├── core/model/                   # Modèles partagés (19 data classes)
├── core/network/                 # Retrofit, OkHttp, API services (12 fichiers)
├── core/database/                # Room, DAOs, entities, migrations (25 fichiers)
├── core/datastore/               # DataStore Preferences (1 fichier)
└── core/common/                  # Utilitaires partagés (8 fichiers + 1 test)
```

**Points forts** :
- Séparation claire UI / Domain / Data respectée globalement
- 24 use cases dans `domain/usecase/` isolent la logique métier
- 16 interfaces `domain/repository/` garantissent l'inversion de dépendance
- DI via Hilt avec `@Binds` (bonnes pratiques)
- Version catalog Gradle pour les dépendances

**Points faibles architecturaux** :

| Problème | Fichier(s) | Impact |
|----------|------------|--------|
| `data/` dans `app/` au lieu d'un module `:data` | `app/src/main/java/.../data/` | Empêche la compilation incrémentale du data layer, couple le data au UI |
| `core/` local dans `app/` duplique les modules `core/` racine | `app/src/main/java/.../core/` vs `core/` | Confusion : 2 "cores" avec des responsabilités qui se chevauchent |
| `MediaItem` god model (46 champs + `@Parcelize`) | `core/model/MediaItem.kt:27-81` | Sérialisation coûteuse, couplage global, 5 listes imbriquées |
| `PlayerViewModel` god-VM (696 lignes, 13 dépendances) | `feature/player/PlayerViewModel.kt` | Non testable, non maintenable, gère ExoPlayer + MPV + scrobbling + stats + chapitres |
| `MediaDetailViewModel` surchargé (357 lignes, 10 dépendances) | `feature/details/MediaDetailViewModel.kt` | Enrichissement multi-serveur, collections, favoris, watch status, similar media |
| Use case pass-through (delegation triviale) | `ToggleFavoriteUseCase`, `SyncWatchlistUseCase`, etc. | 30% des use cases ne font qu'appeler le repository — abstraction sans valeur ajoutée |
| Duplication de code dans `HubsRepositoryImpl` | `data/repository/HubsRepositoryImpl.kt:65-228` | Chemin cache et chemin réseau identiques à 95% |

### 1.2 Architecture cible recommandée

```
PlexHubTV_new/
├── app/                          # Point d'entrée, DI, navigation, MainActivity
├── feature/
│   ├── home/                     # Module feature isolé (UI + VM)
│   ├── details/
│   ├── player/
│   ├── library/
│   ├── search/
│   ├── settings/
│   ├── auth/
│   └── ...
├── domain/                       # Use cases, interfaces repo, services
├── data/                         # Module séparé (repositories, mappers, paging)
├── core/
│   ├── model/                    # Modèles partagés
│   ├── network/                  # Retrofit, API
│   ├── database/                 # Room, DAOs
│   ├── datastore/                # Préférences
│   ├── common/                   # Utils, exceptions
│   ├── designsystem/             # Thème, couleurs, typographie
│   └── ui/                       # Composants UI partagés (NetflixMediaCard, TopBar…)
└── build-logic/                  # Convention plugins Gradle
```

**Bénéfice principal** : compilation incrémentale, isolation par feature, testabilité.

### 1.3 Fichiers exemplaires

| Fichier | Lignes | Pourquoi |
|---------|--------|----------|
| `SearchViewModel.kt` | 101 | 1 dépendance, debounce propre, flow collect, gestion erreur |
| `AuthViewModel.kt` | 136 | Polling avec Job, states sealed, `Result` cohérent |
| `SyncRepositoryImpl.kt` | 181 | Batch semi-parallèle, métriques détaillées, préservation des ratings |
| `PlexImageKeyer.kt` | 66 | Clé de cache robuste, stripping des tokens, normalisation URL |
| `StringNormalizer.kt` | 64 | Normalisation tri multilingue, 5 étapes, bien testé (9 tests) |
| `EnhancedSeekBar.kt` | 259 | Support D-Pad complet, marqueurs chapitres/intro/crédits, focus-aware |

### 1.4 Fichiers les plus problématiques

| Fichier | Lignes | Problème principal |
|---------|--------|--------------------|
| `PlayerViewModel.kt` | 696 | God-VM : 13 deps, ExoPlayer + MPV + scrobbling + stats + UI state |
| `LibraryRepositoryImpl.kt` | 450 | SQL dynamique complexe avec `GROUP_CONCAT`, dur à tester |
| `LibraryViewModel.kt` | 385 | 11 deps, filtrage/tri/paging/WorkManager dans un seul VM |
| `MediaMapper.kt` | 377 | Triple mapping (DTO→Domain, DTO→Entity, Entity→Domain), optimisation image incohérente |
| `OfflineWatchSyncRepositoryImpl.kt` | 379 | File d'attente offline complexe, swallowing d'exceptions |
| `MediaItem.kt` | 81 | 46 champs, @Parcelize, 5 listes imbriquées |
| `SeasonDetailScreen.kt` | 544 | Standard LazyColumn, pas de TvLazy, race conditions focus |
| `LibrariesScreen.kt` | 593 | Standard LazyVerticalGrid/LazyColumn, aucun support TV |

---

## 2. Performance & expérience Android TV

### 2.1 Problèmes TV critiques — D-Pad & Focus

#### 2.1.1 Écrans utilisant encore `LazyColumn`/`LazyRow` standard (D-Pad cassé)

| Écran | Fichier | Conteneur problématique | Impact |
|-------|---------|------------------------|--------|
| DiscoverScreen | `home/DiscoverScreen.kt` | `LazyColumn` | Pas de scroll-to-focus, pas de focus restoration |
| MediaDetailScreen | `details/MediaDetailScreen.kt` | `LazyColumn`, `LazyRow` | Carrousels non navigables |
| SeasonDetailScreen | `details/SeasonDetailScreen.kt` | `LazyColumn` | Liste épisodes sans focus |
| LibrariesScreen | `library/LibrariesScreen.kt` | `LazyVerticalGrid`, `LazyColumn` | Grille bibliothèque inutilisable |
| SearchScreen (legacy) | `search/SearchScreen.kt` | `LazyColumn` | Résultats non focusables |
| FavoritesScreen | `favorites/FavoritesScreen.kt` | `LazyVerticalGrid` | Grille favoris cassée |
| HistoryScreen | `history/HistoryScreen.kt` | `LazyVerticalGrid` | Grille historique cassée |
| DownloadsScreen | `downloads/DownloadsScreen.kt` | `LazyColumn` | Liste téléchargements |
| IptvScreen | `iptv/IptvScreen.kt` | `LazyColumn` | Chaînes non navigables |
| CollectionDetailScreen | `collection/CollectionDetailScreen.kt` | `LazyVerticalGrid` | Grille collection cassée |
| HubDetailScreen | `hub/HubDetailScreen.kt` | `LazyVerticalGrid` | Grille hub cassée |
| AlphabetSidebar | `library/AlphabetSidebar.kt` | `LazyColumn` | Sidebar non focusable |

**Correction requise** : migrer vers `TvLazyColumn` / `TvLazyRow` / `TvLazyVerticalGrid` avec `pivotOffsets = PivotOffsets(parentFraction = 0.0f)` et des clés composites `"${ratingKey}_${serverId}"`.

#### 2.1.2 Focus management manquant

| Fichier | Problème | Ligne(s) |
|---------|----------|----------|
| `MediaDetailScreen.kt` | `MutableInteractionSource()` sans `remember {}` | 150, 172, 185 |
| `SeasonDetailScreen.kt` | `onFocusChanged()` modifie un state mutable directement | 269-270 |
| `FilterDialog.kt` | Aucun `focusable()` sur les FilterChip | 50-59 |
| `PlayerSettingsDialog.kt` | Pas de `focusable()` sur les items de dialogue | 172-181 |
| `IptvScreen.kt` | Aucun focus management | Tout le fichier |
| `CollectionDetailScreen.kt` | Aucun focus management | Tout le fichier |
| `HubDetailScreen.kt` | Aucun focus management | Tout le fichier |

#### 2.1.3 Écrans déjà corrigés (audit V1)

| Écran | Fichier | Status |
|-------|---------|--------|
| NetflixHomeScreen | `home/NetflixHomeScreen.kt` | ✅ `TvLazyColumn` + `pivotOffsets` |
| NetflixContentRow | `home/components/NetflixContentRow.kt` | ✅ `TvLazyRow` + composite keys |
| NetflixDetailScreen | `details/NetflixDetailScreen.kt` | ✅ `TvLazyColumn`/`TvLazyRow` |
| NetflixSearchScreen | `search/NetflixSearchScreen.kt` | ✅ `TvLazyColumn` + `NetflixContentRow` |
| NetflixHeroBillboard | `home/components/NetflixHeroBillboard.kt` | ✅ `Crossfade` + `FocusRequester` |
| NetflixMediaCard | `home/components/NetflixMediaCard.kt` | ✅ `focusable()` avant `clickable()` |
| NetflixOnScreenKeyboard | `search/components/NetflixOnScreenKeyboard.kt` | ✅ `focusable()` sur KeyButton |
| MainScreen | `main/MainScreen.kt` | ✅ `FocusRequester` TopBar ↔ Content |

### 2.2 Fuites mémoire — `MutableInteractionSource` non mémorisé

Chaque `MutableInteractionSource()` créé sans `remember {}` est **recréé à chaque recomposition**, causant des allocations croissantes.

| Fichier | Lignes concernées | Nombre d'instances |
|---------|-------------------|--------------------|
| `NetflixHeroBillboard.kt` | 65, 230, 263 | 3 |
| `NetflixMediaCard.kt` | 78 | 1 (multiplié par le nombre de cartes) |
| `NetflixTopBar.kt` | 145, 179, 208 | 3 |
| `PlezyPlayerControls.kt` | 215 | 1 |
| `PlayerSettingsDialog.kt` | 264, 360 | 2 |
| `NetflixOnScreenKeyboard.kt` | 100 | 1 (multiplié par ~40 touches) |
| `ServerStatusScreen.kt` | 99 | 1 |

**Correction** : Envelopper dans `remember { MutableInteractionSource() }`.

### 2.3 Sérialisation & mémoire

| Problème | Fichier | Impact | Priorité |
|----------|---------|--------|----------|
| `MediaItem` 46 champs + `@Parcelize` | `core/model/MediaItem.kt:27` | `TransactionTooLargeException` sur `SavedStateHandle` | CRITIQUE |
| `Hub.items: List<MediaItem>` sérialisé | `core/model/Hub.kt:18-26` | Hub avec 50 items = sérialisation de 50 × 46 champs | CRITIQUE |
| `MediaPart` + `Stream` + `Chapter` + `Marker` tous `@Parcelize` | Respective models | Chaîne de sérialisation récursive | HAUTE |
| 200 MB cache mémoire images | `core/image/ImageModule.kt:29` | Pression mémoire sur appareils bas de gamme | MOYENNE |

### 2.4 Problèmes de recomposition

| Problème | Fichier | Lignes |
|----------|---------|--------|
| Lambdas non-mémorisées dans `items {}` | `SeasonDetailScreen.kt` | 235-236 |
| State locale `isFocused` créée dans `forEach` | `FavoritesScreen.kt`, `SearchScreen.kt` | 94-95, 136 |
| `animateFloatAsState` dans boucle sans memo | `NetflixOnScreenKeyboard.kt` | 101 |
| `animateColorAsState` recréé dans `forEach` | `SeasonDetailScreen.kt` | 235 |

### 2.5 Requêtes N+1 et performances data

| Problème | Fichier | Lignes | Impact |
|----------|---------|--------|--------|
| `getMediaCollections()` boucle N+1 | `MediaDetailRepositoryImpl.kt` | 227-255 | 10 collections = 10 queries DB |
| `Blocking .first()` dans Flow mapping | `MediaDetailRepositoryImpl.kt` | 247 | ANR potentiel si appelé depuis Main |
| `Blocking .first()` dans Playback | `PlaybackRepositoryImpl.kt` | 156 | Gel UI possible |
| SQL dynamique `GROUP_CONCAT` + `COALESCE`/`NULLIF` | `LibraryRepositoryImpl.kt` | 152-159 | Complexe à optimiser/debugger |
| Pas de timeout par serveur dans `SearchRepository` | `SearchRepositoryImpl.kt` | 54-64 | Serveur lent bloque tous les résultats |

### 2.6 Player

| Problème | Fichier | Lignes | Impact |
|----------|---------|--------|--------|
| `hasHardwareHEVCDecoder()` désactivé (retourne `false`) | `PlayerViewModel.kt` | 674-692 | Fallback MPV systématique pour HEVC |
| `player` et `mpvPlayer` exposés en `var` public | `PlayerViewModel.kt` | 60-63 | Violation d'encapsulation, état mutable partagé |
| `autoNextTriggered` flag mutable | `PlayerViewModel.kt` | 485 | Code smell, race condition potentielle |
| Pas de retry sur les appels réseau player | `PlaybackRepositoryImpl.kt` | Global | Un échec réseau = perte de progression |

### 2.7 Optimisations recommandées par priorité

| # | Optimisation | Impact attendu | Effort |
|---|-------------|----------------|--------|
| 1 | Migrer 12 écrans vers `TvLazy*` | D-Pad fonctionnel partout | Moyen |
| 2 | Retirer `@Parcelize` de `MediaItem`, `Hub`, `Stream`, `MediaPart` | Fin des TransactionTooLargeException | Faible |
| 3 | `remember { MutableInteractionSource() }` dans 7 fichiers | Fin des fuites mémoire recomposition | Faible |
| 4 | Résoudre N+1 dans `getMediaCollections()` | -90% temps chargement collections | Faible |
| 5 | Ajouter timeout par serveur dans SearchRepository | Pas de blocage par serveur lent | Faible |
| 6 | Réactiver `hasHardwareHEVCDecoder()` | Lecture HEVC native sans MPV | Faible |
| 7 | Adapter cache mémoire au RAM disponible | Stabilité sur appareils bas de gamme | Faible |
| 8 | Splitter `PlayerViewModel` en 3 VMs | Testabilité, maintenabilité | Élevé |

---

## 3. Qualité, testabilité et maintenabilité

### 3.1 État des tests

**22 fichiers de tests · 67 cas de test · Frameworks : MockK + Google Truth + Robolectric**

| Couche | Fichiers | Tests | Couverture |
|--------|----------|-------|------------|
| Utilities (`core/util/`) | 3 | 19 | ✅ Forte — ContentRating, StringNormalizer, MediaUrl |
| Mappers (`data/mapper/`) | 1 | 5 | ⚠️ Moyenne — pas de tests null, champs manquants |
| Repositories (`data/repository/`) | 3 | 11 | ⚠️ Faible — 2-4 tests par repository critique |
| ViewModels (`feature/`) | 5 | 15 | ❌ Très faible — 2-4 tests par VM complexe |
| Player controllers | 4 | 14 | ⚠️ Moyenne — bonne couverture de base |
| Domain use cases | 6 | 18 | ⚠️ Moyenne — happy path surtout |
| **Total** | **22** | **67** | |

#### Lacunes critiques de couverture

| Composant | Tests existants | Tests manquants |
|-----------|-----------------|-----------------|
| `PlayerViewModel` (696 lignes) | 2 tests | Track selection, stats, chapitres, MPV fallback, pause/resume |
| `HomeViewModel` | 2 tests | Pagination, prefetch images, erreurs réseau, sync WorkManager |
| `MediaDetailViewModel` (357 lignes) | 3 tests | Similar media, collections, enrichissement, source selection |
| `MediaDetailRepositoryImpl` (311 lignes) | 2 tests | Cache, timeout, fallback GUID, concurrent calls |
| Tous les écrans Compose | 0 tests | Aucun test UI/screenshot/focus |
| Intégration multi-couche | 0 tests | Aucun test API→Mapper→Repository→ViewModel |
| Concurrence/race conditions | 0 tests | Aucun test d'accès concurrent |

### 3.2 Éléments nuisant à la maintenabilité

#### Complexité cyclomatique élevée

| Fichier | Méthode | Complexité estimée | Raison |
|---------|---------|-------------------|--------|
| `LibraryRepositoryImpl.kt` | `getLibraryContent()` | ~25 | SQL dynamique avec 6 filtres |
| `PlayerViewModel.kt` | `onAction()` | ~20 | 15+ branches `when` |
| `MediaMapper.kt` | `mapDtoToDomain()` | ~15 | 46 champs + conditionnels |
| `OfflineWatchSyncRepositoryImpl.kt` | `syncPendingUpdates()` | ~15 | Retry avec multiples états |
| `ConnectionManager.kt` | `findBestConnection()` | ~12 | Race logic multi-URL |

#### Duplication de code

| Duplication | Fichiers | Lignes estimées |
|-------------|----------|-----------------|
| Chemin cache vs réseau identiques à 95% | `HubsRepositoryImpl.kt:65-228` | ~160 lignes |
| `SettingsViewModel` — action dupliquée | `SettingsViewModel.kt:91-96` | 6 lignes (copy-paste exact) |
| Image URL optimization dans 2 fichiers | `ImageUtil.kt` + `PlexImageHelper.kt` | ~60 lignes |
| `formatTime()` dupliqué | `NetflixPlayerControls.kt` + `EnhancedSeekBar.kt` | 10 lignes |

#### Mélange de responsabilités

| Fichier | Responsabilités mélangées |
|---------|--------------------------|
| `PlayerViewModel.kt` | ExoPlayer mgmt, MPV mgmt, track selection, scrobbling, stats, chapitres, UI state (8 responsabilités) |
| `MediaDetailViewModel.kt` | Detail loading, enrichment, collections, favorites, watch status, similar media, source dialog |
| `LibraryViewModel.kt` | Paging, filtering, sorting, server selection, WorkManager, letter jumping |
| `MediaRepositoryImpl.kt` | Façade sur 7 repositories (12 dépendances constructeur) |

### 3.3 Faille de sécurité — PIN profils désactivé

```kotlin
// feature/auth/profiles/ProfileViewModel.kt:36-46
is ProfileAction.SelectUser -> {
    // BYPASS PIN: Always switch directly, even if protected
    switchUser(action.user)
    /*
    if (action.user.protected || action.user.hasPassword) {
        _uiState.update { it.copy(showPinDialog = true, ...) }
    } else {
        switchUser(action.user)
    }
    */
}
```

**Impact** : N'importe quel utilisateur peut accéder à n'importe quel profil Plex Home sans PIN.

### 3.4 Sécurité — Tokens en clair

```kotlin
// core/datastore/SettingsDataStore.kt:25-41
private val PLEX_TOKEN = stringPreferencesKey("plex_token")      // Plaintext!
private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")  // Plaintext!
private val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")  // Plaintext!
```

**Risque** : Fichier DataStore lisible sur appareil rooté. Les clés API TMDb/OMDb sont aussi compilées dans l'APK via `BuildConfig`.

### 3.5 Race condition — AuthInterceptor

```kotlin
// core/network/AuthInterceptor.kt:47-56
val token = cachedToken      // Lecture volatile
val clientId = cachedClientId // Lecture volatile
// Le token peut changer entre ces deux lectures et l'ajout du header
```

### 3.6 État mutable exposé publiquement

```kotlin
// feature/details/SeasonDetailViewModel.kt:69-70
val downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap()) // PUBLIC!
val isOfflineMode = MutableStateFlow(false)                                   // PUBLIC!
```

### 3.7 Stratégie recommandée pour augmenter la qualité

#### Phase 1 — Tests unitaires critiques (Effort faible)

| Cible | Tests à ajouter | Objectif |
|-------|----------------|----------|
| `PlayerViewModel` | +8 tests (track, stats, chapitres, MPV, pause) | De 2 → 10 |
| `MediaDetailViewModel` | +7 tests (similar, collections, enrichment, errors) | De 3 → 10 |
| `HomeViewModel` | +5 tests (prefetch, errors, sync, pagination) | De 2 → 7 |
| `MediaDetailRepositoryImpl` | +5 tests (cache, timeout, concurrent, N+1) | De 2 → 7 |
| `LibraryViewModel` | +4 tests (filter combos, letter jump, errors) | De 4 → 8 |

#### Phase 2 — Tests d'intégration (Effort moyen)

- Repository tests end-to-end avec Room in-memory
- Mapper + Repository + ViewModel flow complet
- Sync workers avec WorkManager TestInitHelper

#### Phase 3 — Tests UI & Screenshot (Effort élevé)

- Tests Compose pour chaque écran Netflix (screenshot tests via Roborazzi)
- Tests focus D-Pad (TestFocusManager)
- Tests de navigation (NavHostController testing)

#### Conventions à adopter

- Nommage tests : `fun method_scenario_expectedResult()`
- Minimum 8 tests par ViewModel (happy path, erreurs, edge cases, concurrence)
- CI obligatoire avant merge (GitHub Actions)
- Detekt strict mode (retirer `ignoreFailures.set(true)`)

---

## 4. Features actuelles et features manquantes

### 4.1 Features existantes

#### Onboarding / Authentification
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Connexion via Plex PIN (OAuth-like) | `AuthScreen.kt`, `AuthViewModel.kt` | ✅ Fonctionnel |
| Sélection de profil Plex Home | `ProfileScreen.kt`, `ProfileViewModel.kt` | ⚠️ PIN désactivé |
| Écran de chargement initial avec sync | `LoadingScreen.kt`, `LoadingViewModel.kt` | ✅ Fonctionnel |
| Détection serveurs (race multi-URL) | `ConnectionManager.kt` | ✅ Bien implémenté |

#### Navigation TV
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Top Bar Netflix (navigation horizontale) | `NetflixTopBar.kt`, `MainScreen.kt` | ✅ Fonctionnel |
| Sidebar latérale alternative | `AppSidebar.kt` | ✅ Présent |
| Navigation entre écrans | `Screen.kt`, NavHost dans `MainScreen.kt` | ✅ Fonctionnel |
| FocusRequester TopBar ↔ Content | `MainScreen.kt` | ✅ Corrigé (audit V1) |

#### Découverte / Home
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Hero billboard (film en vedette) | `NetflixHeroBillboard.kt` | ✅ Corrigé (Crossfade) |
| Continue Watching (On Deck) | `NetflixHomeScreen.kt`, `OnDeckRepositoryImpl.kt` | ✅ Fonctionnel |
| Hubs dynamiques (Trending, Recently Added…) | `NetflixHomeScreen.kt`, `HubsRepositoryImpl.kt` | ✅ Fonctionnel |
| Image prefetching sélectif | `HomeViewModel.kt:123-135` | ✅ Bien optimisé |
| Déduplication multi-serveur | `MediaDeduplicator.kt` | ✅ Fonctionnel |

#### Catalogue / Bibliothèque
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Grille bibliothèque avec pagination (Paging 3) | `LibrariesScreen.kt`, `MediaRemoteMediator.kt` | ✅ Fonctionnel (mais LazyVerticalGrid standard) |
| Filtres avancés (genre, année, note, résolution) | `FilterDialog.kt`, `LibraryViewModel.kt` | ✅ Riche |
| Tri (titre, date ajout, note, année…) | `LibraryViewModel.kt` | ✅ Fonctionnel |
| Sidebar alphabétique (jump to letter) | `AlphabetSidebar.kt` | ⚠️ Standard LazyColumn |
| Vue unifiée multi-serveur | `LibraryRepositoryImpl.kt` | ✅ Implémenté |
| Détail de hub | `HubDetailScreen.kt` | ⚠️ Standard LazyVerticalGrid |
| Détail de collection | `CollectionDetailScreen.kt` | ⚠️ Standard LazyVerticalGrid |

#### Détail média
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Fiche détaillée (poster, synopsis, cast, notes) | `NetflixDetailScreen.kt` | ✅ Corrigé (TvLazy) |
| Fiche détail legacy | `MediaDetailScreen.kt` | ⚠️ Standard LazyColumn |
| Onglets (Episodes, Similar, Collections) | `NetflixDetailTabs.kt` | ✅ Fonctionnel |
| Sélection de saison + liste épisodes | `SeasonDetailScreen.kt`, `SeasonDetailViewModel.kt` | ⚠️ Standard LazyColumn |
| Badges techniques (codec, HDR, résolution) | `TechnicalBadges.kt` | ✅ Présent |
| Sélection de source (multi-serveur) | `SourceSelectionDialog.kt` | ✅ Fonctionnel |
| Enrichissement multi-serveur (remoteSources) | `EnrichMediaItemUseCase.kt` | ✅ Fonctionnel |
| Média similaire | `GetSimilarMediaUseCase.kt` | ✅ Fonctionnel |
| Collections associées | `GetMediaCollectionsUseCase.kt` | ⚠️ N+1 query |

#### Lecture vidéo (Player)
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| ExoPlayer (Media3) avec HLS | `PlayerFactory.kt`, `PlayerViewModel.kt` | ✅ Fonctionnel |
| Fallback MPV (codecs exotiques) | `MpvPlayer.kt`, `MpvPlayerWrapper.kt` | ✅ Fonctionnel |
| Décodeur FFmpeg (Media3) | `build.gradle.kts` (jellyfin media3-ffmpeg) | ✅ Intégré |
| Contrôles Netflix (play/pause, seek, skip) | `NetflixPlayerControls.kt` | ✅ Corrigé (EnhancedSeekBar) |
| Contrôles Plezy alternatifs | `PlezyPlayerControls.kt` | ⚠️ MutableInteractionSource leak |
| Seek bar améliorée (D-Pad, chapitres, marqueurs) | `EnhancedSeekBar.kt` | ✅ Bien implémenté |
| Skip intro / crédits | `SkipMarkerButton.kt`, `ChapterMarkerManager.kt` | ✅ Fonctionnel |
| Sélection audio / sous-titres | `PlayerTrackController.kt` | ✅ Avec priorité fallback |
| Qualité vidéo (transcodage) | `TranscodeUrlBuilder.kt` | ✅ Fonctionnel |
| Vitesse de lecture | `PlayerUiState.kt:40` | ✅ Présent |
| Sync audio / sous-titres (delay) | `PlayerUiState.kt:41-42` | ✅ Présent |
| Scrobbling (progression vers Plex) | `PlayerScrobbler.kt` | ✅ 10s interval |
| Stats performance (overlay) | `PerformanceOverlay.kt`, `PlayerStatsTracker.kt` | ✅ Détaillé |
| Auto-next (épisode suivant) | `PlayerViewModel.kt:485+` | ✅ Popup à 90% |
| Reprise de lecture (resume) | `PlaybackRepositoryImpl.kt` | ✅ Fonctionnel |
| Android TV Watch Next channel | `WatchNextHelper.kt` | ✅ Bien implémenté (>1min, <95%) |
| Format sous-titres ASS | `build.gradle.kts` (ass-media) | ✅ Intégré |

#### Recherche
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Recherche Netflix (clavier + résultats) | `NetflixSearchScreen.kt`, `NetflixOnScreenKeyboard.kt` | ✅ Corrigé (TvLazy) |
| Recherche legacy | `SearchScreen.kt` | ⚠️ Standard LazyColumn |
| Recherche fédérée multi-serveur | `SearchAcrossServersUseCase.kt` | ✅ Parallèle avec graceful degradation |
| Debounce 500ms | `SearchViewModel.kt:72` | ✅ Correct |

#### Favoris / Watchlist / Historique
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Favoris locaux | `FavoritesScreen.kt`, `FavoritesRepositoryImpl.kt` | ✅ Fonctionnel |
| Sync favoris → Plex Watchlist (GUID) | `FavoritesRepositoryImpl.kt:100-151` | ⚠️ Fire-and-forget |
| Historique de visionnage | `HistoryScreen.kt`, `GetWatchHistoryUseCase.kt` | ✅ Fonctionnel |
| Toggle watch status | `ToggleWatchStatusUseCase.kt` | ✅ Optimistic updates |

#### IPTV
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Parsing M3U | `M3uParser.kt` | ✅ Fonctionnel |
| Liste de chaînes avec recherche | `IptvScreen.kt`, `IptvViewModel.kt` | ⚠️ Standard LazyColumn |

#### Téléchargements / Offline
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Liste des téléchargements | `DownloadsScreen.kt`, `DownloadsViewModel.kt` | ⚠️ Standard LazyColumn |
| Sync offline watch progress | `OfflineWatchSyncRepositoryImpl.kt` | ✅ File d'attente avec retry |
| Sync automatique en background | `LibrarySyncWorker.kt`, `CollectionSyncWorker.kt`, `RatingSyncWorker.kt` | ✅ WorkManager + Foreground |

#### Paramètres
| Feature | Fichier(s) | Status |
|---------|------------|--------|
| Thème (clair/sombre) | `SettingsScreen.kt`, `SettingsViewModel.kt` | ✅ Fonctionnel |
| Qualité par défaut | `SettingsScreen.kt` | ✅ Fonctionnel |
| Moteur de lecture (ExoPlayer/MPV) | `SettingsScreen.kt` | ✅ Fonctionnel |
| Langue audio/sous-titres par défaut | `SettingsScreen.kt` | ✅ Fonctionnel |
| Exclusion de serveurs | `SettingsScreen.kt` | ✅ Fonctionnel |
| Serveur par défaut | `SettingsScreen.kt` | ✅ Fonctionnel |
| Status des serveurs | `ServerStatusScreen.kt` | ✅ Fonctionnel |
| Gestion du cache | `CacheManager.kt` | ✅ Fonctionnel |
| Sync watchlist manuelle | `SettingsViewModel.kt` | ✅ Fonctionnel |

### 4.2 Features manquantes pour un niveau pro

#### Haute priorité

| Feature manquante | Valeur ajoutée | Effort |
|-------------------|----------------|--------|
| **Recommandations personnalisées** (basées sur l'historique) | Engagement ++, UX Netflix-like | Élevé |
| **Continue Watching amélioré** (progression visuelle, tri multi-serveur) | UX pro, reprise intuitive | Moyen |
| **Préchargement intelligent** (prédiction de navigation, buffer prochain épisode) | Réduction lag perçu | Moyen |
| **Gestion d'erreur centralisée** (Snackbar/Toast unifié, retry automatique) | Robustesse perçue | Moyen |
| **Accessibilité TV** (ContentDescription systématique, TalkBack support) | Conformité Google Play | Moyen |
| **CI/CD pipeline** (GitHub Actions : build, lint, tests, détect) | Qualité continue, non-régression | Moyen |

#### Moyenne priorité

| Feature manquante | Valeur ajoutée | Effort |
|-------------------|----------------|--------|
| **Profils avec avatar** (switch rapide, restrictions contenu) | Multi-utilisateur, contrôle parental | Élevé |
| **Sections dynamiques configurables** (pinning, réordonnancement) | Personnalisation home | Moyen |
| **Écran "Plus d'infos"** (trailers, bandes-annonces via YouTube/Plex) | Engagement, décision de visionnage | Moyen |
| **Recherche vocale** (microphone Android TV) | UX TV standard | Faible |
| **Écran de debug** (logs, version, état sync, connexions) | Support technique | Faible |
| **Notification de mise à jour** (bibliothèque, contenu ajouté) | Rétention | Moyen |
| **Reprise multi-device** (sync position via Plex timeline) | Continuité de visionnage | Faible (déjà scrobblé) |

#### Basse priorité

| Feature manquante | Valeur ajoutée | Effort |
|-------------------|----------------|--------|
| **Mode PiP** (Picture-in-Picture) | Standard Android TV | Moyen |
| **Télémétrie / Analytics** (crashlytics, événements usage) | Données produit | Faible |
| **Animations de transition** (entre écrans, shared element) | Polish UX | Moyen |
| **Onboarding guidé** (tutoriel première utilisation) | Réduction friction | Faible |
| **Widget Watch Next** (intégration launcher Android TV) | Découvrabilité | Faible (déjà WatchNextHelper) |
| **Multi-langue UI** (i18n complète) | Marché international | Moyen |
| **Mode invité** (accès limité sans compte) | Facilité d'essai | Faible |

---

## 5. Plan d'action priorisé

### Priorité 1 — Indispensable à court terme (stabilité, sécurité, D-Pad)

| # | Action | Fichier(s) | Impact | Effort |
|---|--------|------------|--------|--------|
| 1.1 | **Réactiver la validation PIN des profils** | `auth/profiles/ProfileViewModel.kt:36-46` | Sécurité : empêcher accès non autorisé aux profils | Faible |
| 1.2 | **Retirer `@Parcelize` de `MediaItem`, `Hub`, `Stream`, `MediaPart`, `Chapter`, `Marker`** | `core/model/MediaItem.kt`, `Hub.kt`, `Stream.kt`, `MediaPart.kt`, `Chapter.kt`, `Marker.kt` | Fin des `TransactionTooLargeException` et freezes | Faible |
| 1.3 | **Migrer 12 écrans vers `TvLazyColumn`/`TvLazyRow`/`TvLazyVerticalGrid`** | Voir tableau §2.1.1 | D-Pad fonctionnel sur tous les écrans | Moyen |
| 1.4 | **`remember { MutableInteractionSource() }` dans 7 fichiers** | Voir tableau §2.2 | Fin des fuites mémoire | Faible |
| 1.5 | **Ajouter `focusable()` et `FocusRequester` aux écrans manquants** | `FilterDialog.kt`, `PlayerSettingsDialog.kt`, `IptvScreen.kt`, `CollectionDetailScreen.kt`, `HubDetailScreen.kt` | Navigation TV complète | Moyen |
| 1.6 | **Corriger N+1 dans `getMediaCollections()`** — batch query avec `WHERE id IN (...)` | `MediaDetailRepositoryImpl.kt:227-255` | -90% temps chargement collections | Faible |
| 1.7 | **Réactiver `hasHardwareHEVCDecoder()`** | `PlayerViewModel.kt:674-692` | Lecture HEVC native sans fallback MPV | Faible |
| 1.8 | **Fixer la duplication d'action dans SettingsViewModel** | `SettingsViewModel.kt:91-96` | Comportement prévisible | Faible |
| 1.9 | **Fixer `SeasonDetailViewModel` — états publics mutables** | `SeasonDetailViewModel.kt:69-70` | Encapsulation, pas de modification extérieure | Faible |
| 1.10 | **Ajouter timeout par serveur dans SearchRepository** | `SearchRepositoryImpl.kt:54-64` | Pas de blocage par serveur lent | Faible |
| 1.11 | **Fixer le nested android {} dans core/network** | `core/network/build.gradle.kts:32-37` | Build correct | Faible |
| 1.12 | **Retirer le chemin Java Windows hardcodé** | `gradle.properties:23` | Build portable Linux/macOS/CI | Faible |

### Priorité 2 — Améliorations importantes (architecture, qualité pro, UX TV)

| # | Action | Fichier(s) | Impact | Effort |
|---|--------|------------|--------|--------|
| 2.1 | **Splitter `PlayerViewModel` en 3 VMs** (PlayerControl, TrackSelection, PlaybackStats) | `feature/player/PlayerViewModel.kt` | Testabilité, maintenabilité, SRP | Élevé |
| 2.2 | **Splitter `MediaDetailViewModel`** (MediaDetail, MediaEnrichment) | `feature/details/MediaDetailViewModel.kt` | Réduction complexité, parallélisation | Moyen |
| 2.3 | **Extraire `data/` dans un module `:data` séparé** | `app/src/main/java/.../data/` → `:data/` | Compilation incrémentale, isolation | Moyen |
| 2.4 | **Extraire les composants UI partagés dans `:core:ui`** | `NetflixMediaCard`, `NetflixContentRow`, `NetflixTopBar` | Réutilisabilité, isolation | Moyen |
| 2.5 | **Chiffrer les tokens avec EncryptedSharedPreferences** | `core/datastore/SettingsDataStore.kt:25-41` | Sécurité des données au repos | Moyen |
| 2.6 | **Éliminer la duplication HubsRepository** (cache/réseau → stratégie unique) | `HubsRepositoryImpl.kt:65-228` | -160 lignes, maintenabilité | Moyen |
| 2.7 | **Consolider l'optimisation d'image** (supprimer ImageUtil, garder PlexImageHelper) | `ImageUtil.kt` + `PlexImageHelper.kt` | Fin de la duplication | Faible |
| 2.8 | **Adapter le cache mémoire au RAM disponible** | `ImageModule.kt:29` | Stabilité sur appareils bas de gamme | Faible |
| 2.9 | **Ajouter clés composites à tous les `items {}`** | 14 fichiers identifiés §2.1 | Stabilité état, focus restoration | Moyen |
| 2.10 | **Implémenter GitHub Actions CI** (build + lint + tests) | `.github/workflows/ci.yml` | Non-régression automatique | Moyen |
| 2.11 | **Augmenter la couverture tests ViewModel** (de 2-4 → 8-10 par VM) | 5 fichiers tests existants | Confiance dans les refactors | Moyen |
| 2.12 | **Supprimer les use cases pass-through** (qui ne font que déléguer) | `ToggleFavoriteUseCase`, `SyncWatchlistUseCase`, etc. | Moins d'indirection inutile | Faible |
| 2.13 | **Gestion d'erreur centralisée** (SnackbarHost global, retry automatique) | Nouveau composant | UX robuste, feedback utilisateur | Moyen |
| 2.14 | **Continue Watching amélioré** (barre de progression, tri par dernière vue) | `NetflixHomeScreen.kt`, `OnDeckRepositoryImpl.kt` | UX Netflix pro | Moyen |
| 2.15 | **Préchargement du prochain épisode** (buffer pendant le visionnage) | `PlayerViewModel.kt` | Transition fluide | Moyen |

### Priorité 3 — Améliorations de confort / long terme

| # | Action | Fichier(s) | Impact | Effort |
|---|--------|------------|--------|--------|
| 3.1 | **Tests screenshot Compose** (Roborazzi) | Nouveau | Détection régression visuelle | Élevé |
| 3.2 | **Tests d'intégration multi-couche** | Nouveau | Confiance système end-to-end | Élevé |
| 3.3 | **Écran de debug** (version, état sync, logs, connexions) | Nouveau | Support technique | Faible |
| 3.4 | **Recherche vocale** (SpeechRecognizer Android TV) | `SearchViewModel.kt` | UX TV standard | Faible |
| 3.5 | **Mode PiP** (Picture-in-Picture) | `VideoPlayerScreen.kt` | Standard Android TV | Moyen |
| 3.6 | **Animations de transition** (shared element, fade) | Navigation composables | Polish UX | Moyen |
| 3.7 | **Profils avec avatar et restrictions** | Nouveau feature module | Multi-utilisateur pro | Élevé |
| 3.8 | **Sections home configurables** (pinning, réordonnancement) | `HomeViewModel.kt`, nouveau | Personnalisation | Élevé |
| 3.9 | **Télémétrie / Crashlytics** | Nouveau module | Données produit, stabilité | Faible |
| 3.10 | **Accessibilité TV** (ContentDescription, TalkBack) | Tous les composables | Conformité Google Play | Moyen |
| 3.11 | **i18n complète** (strings.xml multi-langue) | `res/values/`, `res/values-fr/` | Marché international | Moyen |
| 3.12 | **Activer `ksp.incremental`** | `gradle.properties:35` | Build 30-40% plus rapide | Faible |
| 3.13 | **Detekt strict mode** (retirer `ignoreFailures.set(true)`) | `build.gradle.kts` | Qualité code bloquante | Faible |
| 3.14 | **Simplifier `LibraryRepositoryImpl`** SQL dynamique → code Kotlin | `LibraryRepositoryImpl.kt:144-198` | Testabilité, maintenabilité | Élevé |
| 3.15 | **Convention plugins Gradle** (build-logic module) | Nouveau | DRY build config | Moyen |
| 3.16 | **Retirer clés API de l'APK** (backend proxy) | `ApiKeyManager.kt`, `NetworkModule.kt` | Sécurité production | Élevé |
| 3.17 | **Onboarding guidé** (tutoriel première utilisation) | Nouveau | Réduction friction | Faible |
| 3.18 | **Notifications de nouveau contenu** | Nouveau worker | Rétention utilisateur | Moyen |
| 3.19 | **Bandes-annonces** (YouTube/Plex trailers) | Nouveau | Engagement, décision de visionnage | Moyen |
| 3.20 | **Fixer le thread-safety AuthInterceptor** | `AuthInterceptor.kt:47-56` | Pas de race condition sur token | Faible |

---

## 6. Questions ouvertes

L'audit ne peut pas être complet sans réponses aux questions suivantes :

| # | Question | Raison |
|---|----------|--------|
| Q1 | Y a-t-il un backend/proxy prévu, ou l'app parle directement aux serveurs Plex ? | Impacte la stratégie sécurité (clés API, tokens) |
| Q2 | Les écrans legacy (MediaDetailScreen, SearchScreen, LibrariesScreen) doivent-ils être maintenus, ou seulement les variantes Netflix ? | Impacte le scope de migration TvLazy |
| Q3 | Le support MPV est-il stratégique à long terme, ou un fallback temporaire ? | Impacte l'investissement dans PlayerViewModel |
| Q4 | Quels appareils Android TV cibles (RAM, CPU) ? | Impacte les seuils mémoire (cache image) et les tests perf |
| Q5 | Y a-t-il des plans pour un store (Google Play / sideload) ? | Impacte ProGuard, signing, CI/CD |
| Q6 | La fonctionnalité IPTV est-elle stratégique ou expérimentale ? | Impacte l'investissement en qualité sur cet écran |
| Q7 | Le PIN des profils a-t-il été désactivé volontairement pour le dev, ou est-ce un bug ? | Impacte la priorité de correction sécurité |
| Q8 | Y a-t-il un design system (Figma, etc.) ou les composants sont définis uniquement dans le code ? | Impacte la stratégie `:core:ui` |

---

## Annexe A — Métriques du projet

| Métrique | Valeur |
|----------|--------|
| Fichiers Kotlin total | 265 |
| Fichiers de tests | 22 (67 cas) |
| Modules Gradle | 7 |
| ViewModels | 19 |
| Composables écrans | 38 |
| Use cases | 24 |
| Repository interfaces | 16 |
| Repository implémentations | 17 |
| DAOs | 11 |
| Entities Room | 12 |
| Migrations DB | 6 (v11→22) |
| Workers | 3 |
| Endpoints API Plex | ~20 |
| APIs externes | 2 (TMDb, OMDb) |
| compileSdk | 36 |
| minSdk | 27 |
| targetSdk | 34 |
| Version app | 0.8.0 |

## Annexe B — Arbre de dépendances Gradle

```
app (compileSdk 36)
├── core:model         (Kotlin + KotlinX Serialization)
├── core:common        (Timber, Hilt, Coroutines)
├── domain             (Use cases, Coroutines)
├── core:network       (Retrofit, OkHttp, Gson, Hilt)
├── core:database      (Room, Paging 3, Hilt)
├── core:datastore     (DataStore Preferences, Hilt)
├── Compose TV         (androidx.tv.foundation, material3)
├── Media3             (ExoPlayer, HLS, FFmpeg decoder)
├── Coil               (Image loading)
├── WorkManager        (Background sync)
└── Tests: MockK, Truth, Robolectric, Coroutines Test
```

## Annexe C — Configuration Detekt active

| Règle | Seuil |
|-------|-------|
| LongMethod | 60 lignes |
| CyclomaticComplexMethod | 15 |
| LargeClass | 600 lignes |
| TooManyFunctions | 11 par classe |
| MaxLineLength | 120 caractères |
| MagicNumber | Activé (excl. tests) |
