# PlexHubTV Netflix Android TV Clone - Implementation Plan

## Context

**Problem:** PlexHubTV is a functional Android TV Plex client with sidebar navigation (`NavigationDrawer`), basic hub rows, and a hero carousel. The UX does not match the immersive Netflix Android TV experience users expect from a modern streaming app.

**Objective:** Refactor the entire UI/UX layer to replicate the Netflix Android TV experience (top navigation bar, immersive hero billboard, horizontal content rows with zoom-on-focus cards, full-screen detail pages, minimal player overlay, TV keyboard search) while **preserving all existing data/domain layers** (repositories, API services, ViewModels logic, Room DB, player engines).

**Approach:** UI-layer only transformation. Create ~10 new Compose files, modify ~9 existing files. No changes to API services, repositories, database, models, or player engine code.

---

# 1. Analyse UX/UI et Benchmarking Netflix

## 1.1 Principes UX Majeurs de Netflix Android TV

### Navigation par rangees horizontales (Rows)
- L'ecran d'accueil est une liste verticale (`TvLazyColumn`) de rangees horizontales (`TvLazyRow`).
- Chaque rangee a un titre (ex: "Continue Watching", "Trending Now", "Action Movies") et une liste scrollable de cartes.
- La rangee du haut est un **hero billboard** occupant ~65% de la hauteur ecran, avec auto-rotation toutes les 8s.

### Navigation D-Pad
- **Focus tres visible**: carte zoomee (scale 1.08x), bordure blanche, ombre portee, titre qui apparait en dessous.
- **Deplacements fluides**: horizontal dans une rangee, vertical entre rangees. `TvLazyColumn` + `TvLazyRow` gerent automatiquement le focus restoration.
- **Gestion des bords**: le focus ne sort jamais de l'ecran. En haut, D-pad UP amene a la top bar. En bas, le scroll s'arrete a la derniere rangee.

### Carte de contenu (Poster Card)
- **Etat normal**: poster seul, pas de titre, coins arrondis 6dp.
- **Etat focus**: zoom 1.08x, bordure blanche 2dp, ombre 16dp, titre + metadonnees apparaissent en dessous avec animation `fadeIn + expandVertically`.
- **Etat selection (click)**: navigation vers l'ecran detail.

### Fiche detaillee
- Plein ecran avec backdrop large, gradient assombrissant, titre, metadonnees, synopsis (4 lignes max), boutons (Play, My List, Watched).
- Onglets: "Episodes" (pour series), "More Like This", "Details".
- Rangees contextuelles de contenu similaire.

### Recommandations dynamiques
- Les rangees sont alimentees par les hubs Plex existants (`/hubs` endpoint) qui fournissent "Recently Added", "Recommended", "Because you watched X", genre rows, etc.
- Les donnees `onDeck` alimentent "Continue Watching".
- Les favoris alimentent "My List".

## 1.2 Ecrans Cles Netflix a Reproduire

| Ecran | Composable Existant | Nouveau Composable |
|-------|--------------------|--------------------|
| Home | `DiscoverScreen.kt` | `NetflixHomeScreen.kt` |
| Detail media | `MediaDetailScreen.kt` | `NetflixDetailScreen.kt` |
| Player (overlay) | `PlezyPlayerControls.kt` | `NetflixPlayerControls.kt` |
| Recherche | `SearchScreen.kt` | `NetflixSearchScreen.kt` |
| Profils | `ProfileScreen.kt` | Conserve (deja OK) |
| Continue Watching | Rangee dans Home | Rangee `CardType.WIDE` dans Home |
| Ma Liste | `FavoritesScreen.kt` | Rangee dans Home + ecran dedie restyle |
| Navigation | `AppSidebar.kt` | `NetflixTopBar.kt` |

## 1.3 Description par Ecran

### Home
- **Objectif UX**: Decouverte de contenu, reprise de lecture, navigation rapide.
- **Blocs**: Hero billboard (auto-rotate, boutons Play/Info), rangees horizontales (Continue Watching, Ma Liste, hubs Plex par genre/type).
- **Interactions telecommande**: D-pad haut/bas entre rangees, gauche/droite dans une rangee, Enter pour ouvrir detail, D-pad UP depuis premiere rangee = focus top bar.

### Detail Media
- **Objectif UX**: Decision de lecture, decouverte du contenu similaire.
- **Blocs**: Backdrop hero, titre/metadonnees, boutons action, synopsis, onglets (Episodes/More Like This/Details).
- **Interactions telecommande**: D-pad navigue entre boutons et onglets, Enter lance lecture ou ouvre onglet.

### Player
- **Objectif UX**: Lecture immersive, controles temporaires.
- **Blocs**: Overlay semi-transparente avec titre (haut), play/pause central, barre de progression + controles (bas), boutons skip intro/credits.
- **Interactions telecommande**: N'importe quel D-pad affiche l'overlay pendant 5s, center = play/pause, gauche/droite = seek.

### Recherche
- **Objectif UX**: Trouver du contenu par nom.
- **Blocs**: Clavier virtuel grille (gauche 300dp), resultats en rangees horizontales (droite).
- **Interactions telecommande**: D-pad navigue sur le clavier, Enter tape une lettre, D-pad droite depuis le clavier amene aux resultats.

### Profils
- **Objectif UX**: Selection utilisateur ("Who's watching").
- **Blocs**: Grille d'avatars. Deja implemente dans `ProfileScreen.kt`, conserve tel quel.

---

# 2. Design UX Cible de PlexHubTV (Clone Netflix)

## 2.1 Architecture de Navigation Globale

### Top Navigation Bar (remplace le sidebar)

```
[Logo PlexHub] [Home] [TV Shows] [Movies] [My List]     [Search Icon] [Profile Avatar]
```

- **Fichier**: `feature/main/NetflixTopBar.kt` (NOUVEAU)
- **Composable**: `NetflixTopBar(selectedItem, isScrolled, isVisible, onItemSelected)`
- **Comportement**:
  - Fond transparent par defaut, devient `Color.Black.copy(alpha=0.85f)` quand le contenu scrolle
  - Hauteur fixe: 56dp
  - Le contenu (Home hero) s'affiche DERRIERE la top bar (pas de padding top sur Home)
  - Items: Home, TV Shows, Movies, My List (= Favorites renomme)
  - Search = icone loupe a droite
  - Settings accessible via avatar profil ou via Settings dans un sous-menu

### Comportement boutons Back/Home
- **Back**: `navController.popBackStack()` standard. Depuis Home, quitte l'app.
- **Home Android TV**: Retour au launcher Android TV (systeme, pas gere par l'app).

### Modification fichier existant: `MainScreen.kt`
- Remplacer `AppSidebar { ... }` par `Box { NavHost(...); NetflixTopBar(...) }`
- Le `NavHost` remplit tout l'ecran, la `NetflixTopBar` est en overlay `zIndex(1f)` en haut
- `NavigationItem.Favorites` renomme label de "Favorites" en "My List"

## 2.2 Home Screen

### Structure (wireframe textuel)

```
+------------------------------------------------------+
| [Logo] [Home] [TV Shows] [Movies] [My List]  [Q] [P] |  <- Top Bar (overlay)
|                                                        |
|  +-------------------------------------------------+  |
|  |                                                 |  |
|  |          HERO BILLBOARD (65% hauteur)           |  |
|  |   Backdrop image plein ecran                    |  |
|  |                                                 |  |
|  |  Titre du Film/Serie                            |  |
|  |  2024 | 2h15 | PG-13                            |  |
|  |  Synopsis court sur 3 lignes max...             |  |
|  |  [> Play]  [i More Info]                        |  |
|  |  . . o . .  (indicateurs page)                  |  |
|  +-------------------------------------------------+  |
|                                                        |
|  Continue Watching                                     |
|  [====] [====] [====] [====] [====] [====]   ->       |
|                                                        |
|  My List                                               |
|  [||] [||] [||] [||] [||] [||] [||]          ->       |
|                                                        |
|  Recently Added Movies                                 |
|  [||] [||] [||] [||] [||] [||] [||]          ->       |
|                                                        |
|  Action & Adventure                                    |
|  [||] [||] [||] [||] [||] [||] [||]          ->       |
|                                                        |
+------------------------------------------------------+
```

### Rangees definies a partir des donnees Plex existantes

| Rangee | Source de donnees | CardType |
|--------|-------------------|----------|
| Hero Billboard | `onDeck.take(10)` | N/A (full-screen) |
| Continue Watching | `onDeck` (items avec `viewOffset > 0`) | `WIDE` (16:9) |
| My List | `favorites` (de `FavoritesRepository`) | `POSTER` (2:3) |
| Recently Added (Movies) | `hubs` filtre `hubIdentifier == "recentlyAdded"` | `POSTER` |
| Recently Added (TV) | `hubs` filtre | `POSTER` |
| Par genre (Action, Comedy, etc.) | `hubs` genre-based | `POSTER` |
| Recommendations | `hubs` filtre `hubIdentifier contains "recommended"` | `POSTER` |

### Fichiers impliques
- **NOUVEAU**: `feature/home/NetflixHomeScreen.kt` - Composable `NetflixHomeContent(onDeck, hubs, favorites, onAction, onScrollStateChanged)`
- **NOUVEAU**: `feature/home/components/NetflixHeroBillboard.kt` - Hero auto-rotatif
- **NOUVEAU**: `feature/home/components/NetflixContentRow.kt` - Rangee horizontale reutilisable
- **NOUVEAU**: `feature/home/components/NetflixMediaCard.kt` - Carte Netflix
- **MODIFIE**: `feature/home/DiscoverScreen.kt` - `ContentState` appelle `NetflixHomeContent` au lieu du `LazyColumn` actuel
- **MODIFIE**: `feature/home/HomeUiState.kt` - Ajouter champ `favorites: List<MediaItem> = emptyList()`
- **MODIFIE**: `feature/home/HomeViewModel.kt` - Injecter `FavoritesRepository`, combiner favorites flow dans l'etat

## 2.3 Ecran Detail Media

### Wireframe

```
+------------------------------------------------------+
|                                                        |
|  BACKDROP IMAGE (400dp hauteur, plein ecran)          |
|  <- Gradient sombre en bas et a gauche                |
|                                                        |
|  Titre du Film                                         |
|  2024 | 2h15 | PG-13 | Warner Bros                    |
|  ★ 8.2 IMDb  ★ 92% RT                                 |
|                                                        |
|  [> Play]  [+ My List]  [✓ Watched]                   |
|                                                        |
+------------------------------------------------------+
|                                                        |
|  Synopsis du film sur maximum 4 lignes. Cliquer       |
|  pour voir plus...                                     |
|                                                        |
|  [Episodes] [More Like This] [Details]  <- Onglets    |
|  ________________________________________________     |
|                                                        |
|  S1 E1 - Pilot                     45min  [Watched]   |
|  S1 E2 - The Beginning             42min              |
|  S1 E3 - Rising Action             44min              |
|                                                        |
+------------------------------------------------------+
```

### Fichiers impliques
- **NOUVEAU**: `feature/details/NetflixDetailScreen.kt` - `NetflixDetailContent(media, seasons, similarItems, state, onAction)`
- **NOUVEAU**: `feature/details/components/NetflixDetailTabs.kt` - Onglets Episodes/More Like This/Details
- **MODIFIE**: `feature/details/MediaDetailScreen.kt` - Remplacer le body de `MediaDetailContent` par `NetflixDetailContent`
- **INCHANGE**: `MediaDetailViewModel.kt` - fournit deja `media`, `seasons`, `similarItems`, `collections`

## 2.4 Ecran de Lecture / Player

### Overlay Netflix-like

```
+------------------------------------------------------+
|  [<-]  Titre du Film                                  |
|         S1:E3 - Episode Title                          |
|                                                        |
|                                                        |
|                    ( ▶ )                               |  <- Bouton central 72dp
|                                                        |
|                                                        |
|                                    [Skip Intro >>]    |
|                                                        |
|  ===|================o=====|=====  1:23:45 / 2:15:00  |
|  [|<<] [>>|]                    [Audio] [Sub] [⚙]    |
+------------------------------------------------------+
```

### Fichiers impliques
- **NOUVEAU**: `feature/player/ui/NetflixPlayerControls.kt`
- **MODIFIE**: `feature/player/VideoPlayerScreen.kt` - Remplacer `PlezyPlayerControls` par `NetflixPlayerControls`
- **REUTILISE**: `EnhancedSeekBar.kt`, `SkipMarkerButton.kt`, `PlayerSettingsDialog.kt`
- **INCHANGE**: `PlayerViewModel.kt` (34K lignes), tous les controllers player

## 2.5 Ecran de Recherche

### Wireframe

```
+------------------------------------------------------+
|  [Logo] [Home] [TV Shows] [Movies] [My List]  [Q] [P] |
|                                                        |
| +----------+  +--------------------------------------+|
| | Search   |  |                                      ||
| | ________ |  |  Movies                              ||
| |          |  |  [||] [||] [||] [||] [||]      ->    ||
| | A B C D  |  |                                      ||
| | E F G H  |  |  TV Shows                            ||
| | I J K L  |  |  [||] [||] [||] [||] [||]      ->   ||
| | M N O P  |  |                                      ||
| | Q R S T  |  |  Episodes                            ||
| | U V W X  |  |  [====] [====] [====]          ->    ||
| | Y Z 1 2  |  |                                      ||
| | 3 4 5 6  |  |                                      ||
| | [SPACE]  |  |                                      ||
| | [DEL]    |  |                                      ||
| +----------+  +--------------------------------------+|
+------------------------------------------------------+
```

### Fichiers impliques
- **NOUVEAU**: `feature/search/NetflixSearchScreen.kt`
- **NOUVEAU**: `feature/search/components/NetflixOnScreenKeyboard.kt`
- **MODIFIE**: `feature/search/SearchScreen.kt` - Remplacer body par `NetflixSearchContent`
- **INCHANGE**: `SearchViewModel.kt`

## 2.6 Optionnel: Profils et "Mon Espace"

- `ProfileScreen.kt` est deja implemente avec "Who's watching" - **conserve tel quel**
- Pas de section "Mon Netflix" dediee pour le MVP. Les fonctions sont distribuees:
  - Continue Watching = rangee Home
  - Ma Liste = rangee Home + ecran `FavoritesScreen` restyle
  - Historique = `HistoryScreen` restyle

---

# 3. Architecture Technique et Patterns

## 3.1 Stack Recommandee (deja en place)

| Composant | Lib actuelle | Version | Action |
|-----------|-------------|---------|--------|
| UI | Jetpack Compose + Compose for TV | BOM 2026.01.00 + tv-material 1.0.1 | **Conserver** |
| Architecture | MVVM (ViewModel + StateFlow) | N/A | **Conserver** |
| Player | Media3/ExoPlayer + MPV | 1.5.1 | **Conserver** |
| Images | Coil Compose | 2.7.0 | **Conserver** |
| DI | Hilt | 2.58 | **Conserver** |
| Navigation | Navigation Compose | 2.9.6 | **Conserver** |
| DB | Room | 2.8.4 | **Conserver** |
| Network | Retrofit + OkHttp | 3.0.0 + 5.3.2 | **Conserver** |

## 3.2 Couche UI - Composables TV Optimises

### Nouveaux Composables Principaux

| Composable | Fichier | Role |
|------------|---------|------|
| `NetflixTopBar` | `feature/main/NetflixTopBar.kt` | Barre de navigation horizontale overlay |
| `NetflixTopBarContent` | (meme fichier) | Layout Row interne |
| `NetflixNavItem` | (meme fichier) | Item de tab avec focus underline |
| `NetflixSearchIcon` | (meme fichier) | Icone loupe focusable |
| `NetflixProfileAvatar` | (meme fichier) | Avatar circulaire |
| `NetflixHomeContent` | `feature/home/NetflixHomeScreen.kt` | Orchestrateur Home (`TvLazyColumn`) |
| `NetflixHeroBillboard` | `feature/home/components/NetflixHeroBillboard.kt` | Hero auto-rotatif 480dp |
| `NetflixPlayButton` | (meme fichier) | Bouton blanc "Play" avec focus scale |
| `NetflixInfoButton` | (meme fichier) | Bouton gris "More Info" |
| `NetflixContentRow` | `feature/home/components/NetflixContentRow.kt` | Rangee titre + `TvLazyRow` |
| `NetflixMediaCard` | `feature/home/components/NetflixMediaCard.kt` | Carte poster/wide avec focus zoom |
| `NetflixProgressBar` | (meme fichier) | Barre rouge de progression lecture |
| `NetflixDetailContent` | `feature/details/NetflixDetailScreen.kt` | Page detail plein ecran |
| `NetflixMetadataRow` | (meme fichier) | Ligne annee/duree/rating |
| `NetflixMyListButton` | (meme fichier) | Bouton + / check "Ma Liste" |
| `NetflixWatchedButton` | (meme fichier) | Bouton watched toggle |
| `NetflixDetailTabs` | `feature/details/components/NetflixDetailTabs.kt` | Onglets Episodes/Similar/Details |
| `NetflixPlayerControls` | `feature/player/ui/NetflixPlayerControls.kt` | Overlay player minimale |
| `NetflixSearchContent` | `feature/search/NetflixSearchScreen.kt` | Layout recherche keyboard+results |
| `NetflixOnScreenKeyboard` | `feature/search/components/NetflixOnScreenKeyboard.kt` | Clavier grille TV |
| `KeyboardKey` | (meme fichier) | Touche individuelle focusable |

### Gestion du Focus et Navigation D-Pad

**Patterns recommandes:**
1. **`TvLazyColumn` + `TvLazyRow`** (de `androidx.tv.foundation`) gerent automatiquement la restauration du focus entre rangees
2. **`FocusRequester`** pour forcer le focus initial (ex: bouton Play du hero, premier onglet)
3. **`Modifier.onFocusChanged`** pour les animations de zoom/bordure sur les cartes
4. **`rememberTvLazyListState()`** pour persister la position de scroll
5. **`pivotOffsets = PivotOffsets(parentFraction = 0.0f)`** sur `TvLazyColumn` pour que l'item focus reste en haut de l'ecran
6. **Callbacks `onScrollStateChanged`** pour communiquer l'etat de scroll a la top bar (transparence)

**Gestion Top Bar <-> Content:**
```
Top Bar FocusRequester <-- D-pad UP depuis premiere rangee
Content FocusRequester <-- D-pad DOWN depuis top bar
```

## 3.3 Couche Domain / Data (INCHANGEE)

| Service/Repository | Deja implemente | Utilise par |
|--------------------|-----------------|-------------|
| `AuthRepository` | Oui - `AuthRepositoryImpl.kt` | Auth screens |
| `MediaRepository` | Oui - `MediaRepositoryImpl.kt` (delegue aux sous-repos) | Home, Detail, Library |
| `HubsRepository` | Oui - `HubsRepositoryImpl.kt` | Home (rangees hubs) |
| `OnDeckRepository` | Oui - `OnDeckRepositoryImpl.kt` | Home (Continue Watching) |
| `FavoritesRepository` | Oui - `FavoritesRepositoryImpl.kt` | Home (Ma Liste), Favorites |
| `WatchlistRepository` | Oui - `WatchlistRepositoryImpl.kt` | Ma Liste sync Plex |
| `PlaybackRepository` | Oui - `PlaybackRepositoryImpl.kt` | Player |
| `SearchRepository` | Oui - `SearchRepositoryImpl.kt` | Search |
| `LibraryRepository` | Oui - `LibraryRepositoryImpl.kt` | Movies, TV Shows |
| `MediaDetailRepository` | Oui - `MediaDetailRepositoryImpl.kt` | Detail |
| `SettingsRepository` | Oui - `SettingsRepositoryImpl.kt` | Settings |

## 3.4 Integration Backend Existant

### Endpoints necessaires par ecran (tous deja implementes)

| Ecran | Endpoint Plex | Repository |
|-------|---------------|------------|
| Home - Hero/Continue Watching | `GET /hubs` (onDeck) | `OnDeckRepository.getUnifiedOnDeck()` |
| Home - Rangees hubs | `GET /hubs` | `HubsRepository.getUnifiedHubs()` |
| Home - Ma Liste | Local DB | `FavoritesRepository.getFavorites()` |
| Detail | `GET /metadata/{ratingKey}` | `MediaDetailRepository.getMediaDetail()` |
| Detail - Episodes | `GET /library/metadata/{id}/children` | `MediaRepository.getSeasonEpisodes()` |
| Detail - Similaires | `GET /hubs` (similar) | `MediaRepository.getSimilarMedia()` |
| Player - Progress | `GET /timeline/poll` | `PlaybackRepository.updatePlaybackProgress()` |
| Player - Scrobble | `GET /scrobble` | `PlaybackRepository` |
| Search | `GET /search` | `SearchRepository.searchMedia()` |
| Library | `GET /library/sections/{id}/all` | `LibraryRepository` |

### Adaptations backend necessaires: **AUCUNE**

Tous les endpoints pour alimenter les rangees Netflix-like existent deja. L'endpoint `/hubs` de Plex retourne naturellement des "hubs" categorises (recently added, recommended, genre-based), ce qui mappe directement sur les rangees Netflix.

**Seule modification ViewModel**: `HomeViewModel` doit injecter `FavoritesRepository` pour fournir la rangee "Ma Liste" sur le Home.

## 3.5 Gestion de l'Etat et Performance

### Cache local (deja en place)
- **3 niveaux**: Memoire (`cachedServers`) -> Room DB (11 entities) -> API Plex
- **PlexApiCache**: TTL configurable pour les reponses API
- **Coil**: Cache image memoire + disque (configure dans `PlexHubApplication.kt`)

### Pagination (deja en place)
- **Paging 3**: `MediaRemoteMediator` pour les listes longues (library)
- **pageOffset**: Index O(1) dans Room pour pagination rapide

### Prechargement images
- **Coil `ImagePrefetchManager`**: deja injecte dans `HomeViewModel`, prefetch les posters a l'avance
- **`crossfade(false)`** sur les cartes pour eviter le clignotement (deja en place dans `MediaCard`)

### Strategie recomposition Compose
1. **`key` sur tous les items** `TvLazyColumn`/`TvLazyRow` (utiliser `"${ratingKey}_${serverId}"`)
2. **`remember(key)` pour calculs derives** (progress bar ratio, image URL resolution)
3. **`derivedStateOf`** pour observer l'etat de scroll (top bar transparence)
4. **Eviter les lambda instables**: passer des methodes ViewModel par reference (`viewModel::onAction`)
5. **Limiter le hero billboard a 10 items** pour borner la memoire des backdrops

---

# 4. Mapping Fonctionnalites PlexHubTV -> Nouvelle UI Netflix-like

## 4.1 Tableau de Mapping

| Fonctionnalite Existante | Ecran/Composant Actuel | Nouvel Ecran/Composant Netflix | Ajustement UX | Statut |
|--------------------------|------------------------|-------------------------------|----------------|--------|
| Authentification Plex (PIN) | `AuthScreen.kt` | `AuthScreen.kt` (inchange) | Aucun | Conserve |
| Selection profil | `ProfileScreen.kt` | `ProfileScreen.kt` (inchange) | Aucun | Conserve |
| Navigation laterale (sidebar) | `AppSidebar.kt` | `NetflixTopBar.kt` | Sidebar -> Top bar horizontale | Repackage |
| Home / Discover | `DiscoverScreen.kt` | `NetflixHomeScreen.kt` | Hero carousel -> Hero billboard, rangees restylee | Repackage |
| Hero carousel (On Deck) | `HeroCarousel` dans `DiscoverScreen.kt` | `NetflixHeroBillboard.kt` | LazyRow -> plein ecran auto-rotatif | Repackage |
| Rangees de hubs | `LazyRow` dans `DiscoverScreen.kt` | `NetflixContentRow.kt` | Standard Compose -> TV Compose + Netflix cards | Repackage |
| Cartes media (posters) | `MediaCard` dans `DiscoverScreen.kt` | `NetflixMediaCard.kt` | Scale 1.1 -> 1.08, titre apparait au focus, progress bar | Repackage |
| Navigation films | `LibrariesScreen.kt` (movies) | `LibrariesScreen.kt` restyle | Grid existant + Netflix cards, retire alphabet sidebar | Repackage |
| Navigation series | `LibrariesScreen.kt` (tvshows) | `LibrariesScreen.kt` restyle | Idem films | Repackage |
| Detail film/serie | `MediaDetailScreen.kt` | `NetflixDetailScreen.kt` | Layout 2 colonnes -> plein ecran backdrop + onglets | Repackage |
| Detail saison | `SeasonDetailScreen.kt` | Integre dans `NetflixDetailTabs.kt` (onglet Episodes) | Ecran dedie -> onglet dans detail | Repackage |
| Favoris | `FavoritesScreen.kt` | Rangee "Ma Liste" Home + `FavoritesScreen.kt` restyle | "Favorites" -> "Ma Liste" | Repackage |
| Historique | `HistoryScreen.kt` | `HistoryScreen.kt` restyle | Netflix cards | Repackage |
| Continue watching | Rangee implicite dans Home | Rangee explicite `CardType.WIDE` + progress bar | Plus visible, premiere rangee apres hero | Repackage |
| Recherche | `SearchScreen.kt` | `NetflixSearchScreen.kt` | SearchBar -> clavier grille TV + resultats en rangees | Repackage |
| Lecture video | `VideoPlayerScreen.kt` | `VideoPlayerScreen.kt` (controles changes) | Overlay PlezyControls -> Netflix minimal overlay | Repackage |
| Controles player | `PlezyPlayerControls.kt` | `NetflixPlayerControls.kt` | 3 sections -> overlay minimaliste | Repackage |
| Skip intro/credits | `SkipMarkerButton.kt` | `SkipMarkerButton.kt` (reutilise) | Aucun | Conserve |
| Seek bar + chapitres | `EnhancedSeekBar.kt` | `EnhancedSeekBar.kt` (reutilise) | Aucun | Conserve |
| Audio/subtitle selection | `PlayerTrackController` | Inchange | Aucun | Conserve |
| Downloads / Offline | `DownloadsScreen.kt` | `DownloadsScreen.kt` (restyle minimal) | Padding top pour top bar | Conserve |
| IPTV | `IptvScreen.kt` | `IptvScreen.kt` (restyle minimal) | Padding top pour top bar | Conserve |
| Settings | `SettingsScreen.kt` | `SettingsScreen.kt` (restyle minimal) | Accessible via profil avatar | Conserve |
| Themes multiples | `Theme.kt` | `Theme.kt` + nouveau theme "Netflix" | Ajout palette Netflix | Evolutif |
| Recommandations avancees | N/A | N/A | Moteur de reco cote backend | Evolutif |

## 4.2 Categorisation

### Strictement Conserve (aucun changement)
- Authentification (`AuthScreen`, `AuthViewModel`, `AuthRepository`)
- Selection profil (`ProfileScreen`)
- Logique player (`PlayerViewModel`, 34K lignes, tous controllers)
- Skip markers (`SkipMarkerButton`)
- Seek bar (`EnhancedSeekBar`)
- Settings dialogs player (`PlayerSettingsDialog`)
- Toute la couche data/domain (16 repositories, 10+ use cases, Room DB, API services)
- Models (`MediaItem`, `Hub`, `Server`, etc.)
- Background sync (`LibrarySync`, `CollectionSync` workers)

### Repackage (nouvelle presentation, meme logique)
- Navigation: sidebar -> top bar
- Home: LazyColumn+LazyRow -> TvLazyColumn+TvLazyRow+HeroBillboard
- Cards: MediaCard -> NetflixMediaCard
- Detail: 2 colonnes -> plein ecran+onglets
- Player overlay: PlezyControls -> NetflixControls
- Recherche: SearchBar -> clavier grille

### Optionnel/Evolutif
- Theme Netflix dedie
- Recommandations personnalisees avancees (necessite backend)
- Profils multiples locaux
- Systeme de notes thumbs up/down

---

# 5. Details UI: Composants et Animations

## 5.1 Composants UI Principaux

### `NetflixMediaCard`
- **Role**: Carte de contenu (poster ou thumbnail) avec animations de focus Netflix.
- **Fichier**: `feature/home/components/NetflixMediaCard.kt`
- **Props**:
  - `media: MediaItem` - Donnees du media
  - `cardType: CardType` - `POSTER` (140x200dp, 2:3), `WIDE` (240x135dp, 16:9), `TOP_TEN` (140x200dp + rang)
  - `onClick: () -> Unit` - Action clic (navigation detail)
  - `onPlay: () -> Unit` - Action lecture directe
- **Etats**:
  - Normal: poster seul, coins arrondis 6dp
  - Focus: scale 1.08x (`tween 200ms`), bordure blanche 2dp, ombre 16dp, titre+metadonnees apparaissent (`fadeIn+expandVertically 200ms`)
  - Loading: placeholder gris anime (skeleton)
  - Error: placeholder icone film gris

### `NetflixContentRow`
- **Role**: Rangee horizontale avec titre et liste scrollable de cartes.
- **Fichier**: `feature/home/components/NetflixContentRow.kt`
- **Props**:
  - `title: String`
  - `items: List<MediaItem>`
  - `cardType: CardType`
  - `onItemClick: (MediaItem) -> Unit`
  - `onItemPlay: (MediaItem) -> Unit`
- **Implementation**: `TvLazyRow` avec `contentPadding(horizontal=48.dp)`, espacement 8dp

### `NetflixHeroBillboard`
- **Role**: Bandeau hero plein ecran avec auto-rotation et boutons action.
- **Fichier**: `feature/home/components/NetflixHeroBillboard.kt`
- **Props**:
  - `items: List<MediaItem>` (max 10)
  - `onPlay: (MediaItem) -> Unit`
  - `onInfo: (MediaItem) -> Unit`
  - `autoRotateIntervalMs: Long = 8000L`
- **Layers**: Backdrop image -> gradient scrims (bas: vertical, gauche: horizontal) -> titre/metadata/boutons -> indicateurs page
- **Dimensions**: `fillMaxWidth().height(480.dp)`
- **Etats**: Loading (shimmer), Error (fallback gradient), Content (image+info)

### `NetflixPlayerControls`
- **Role**: Overlay de controle player minimaliste.
- **Fichier**: `feature/player/ui/NetflixPlayerControls.kt`
- **Props**:
  - `uiState: PlayerUiState`
  - `onAction: (PlayerAction) -> Unit`
  - `title: String`, `subtitle: String?`
  - `chapters: List<Chapter>`, `markers: List<Marker>`
  - `playPauseFocusRequester: FocusRequester?`
- **Layout**: Scrim `Black.alpha(0.4)` -> Top (titre) -> Center (play/pause 72dp) -> Bottom (seekbar + boutons)
- **Reutilise**: `EnhancedSeekBar`, `SkipMarkerButton` existants

### `NetflixTopBar`
- **Role**: Barre de navigation horizontale superieure.
- **Fichier**: `feature/main/NetflixTopBar.kt`
- **Props**:
  - `selectedItem: NavigationItem`
  - `isScrolled: Boolean`
  - `isVisible: Boolean`
  - `onItemSelected: (NavigationItem) -> Unit`
- **Animation**: fond transparent -> `Black.alpha(0.85)` via `animateColorAsState(tween(300ms))`

### `NetflixOnScreenKeyboard`
- **Role**: Clavier grille pour la recherche TV.
- **Fichier**: `feature/search/components/NetflixOnScreenKeyboard.kt`
- **Props**:
  - `onKeyPressed: (Char) -> Unit`
  - `onDelete: () -> Unit`
  - `onClear: () -> Unit`
- **Layout**: 6 rangees de 6 caracteres (A-Z, 0-9) + rangee speciale (SPACE, DEL, CLR)

## 5.2 Animations & Transitions

### Focus zoom + shadow sur les cartes
```kotlin
val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(200, FastOutSlowInEasing))
val elevation by animateDpAsState(if (isFocused) 16.dp else 0.dp, tween(200))
val zIndex by animateFloatAsState(if (isFocused) 10f else 0f)
```

### Titre apparition au focus
```kotlin
AnimatedVisibility(
    visible = isFocused,
    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
)
```

### Top bar transparence
```kotlin
val bgColor by animateColorAsState(
    if (isScrolled) Color.Black.copy(alpha = 0.85f) else Color.Transparent,
    tween(300)
)
```

### Hero billboard transition
- `crossfade` entre les backdrops via `AnimatedContent` ou `Crossfade` composable avec `tween(500ms)`
- Indicateurs page: largeur animee (`animateDpAsState`)

### Player overlay apparition
- Overlay complete: `AnimatedVisibility(enter=fadeIn(300ms), exit=fadeOut(300ms))`
- Auto-hide: 5s timeout (logique existante dans `VideoPlayerScreen.kt`)

---

# 6. Plan de Livraison / Roadmap

## 6.1 Decoupage en Sprints

### Sprint 1: Fondation - Navigation + Design System (Jours 1-3)

**User Stories:**
- US-1.1: En tant qu'utilisateur, je vois une barre de navigation en haut de l'ecran avec Home, TV Shows, Movies, Ma Liste
- US-1.2: En tant qu'utilisateur, la barre de navigation devient semi-transparente quand je scroll vers le bas
- US-1.3: En tant qu'utilisateur, je peux naviguer entre les onglets avec le D-pad

**Taches Techniques:**
1. Ajouter couleurs Netflix dans `core/designsystem/Color.kt` (`NetflixRed`, `NetflixBlack`, `NetflixDarkGray`, etc.)
2. Ajouter `NetflixColorScheme` et option "Netflix" dans `core/designsystem/Theme.kt`
3. Creer `feature/main/NetflixTopBar.kt` avec sous-composables (`NetflixNavItem`, `NetflixSearchIcon`, `NetflixProfileAvatar`)
4. Modifier `feature/main/MainScreen.kt`: remplacer `AppSidebar` par `Box { NavHost + NetflixTopBar overlay }`
5. Renommer label `NavigationItem.Favorites` de "Favorites" en "My List"
6. Tester: navigation D-pad entre tabs, tous les routes existants fonctionnent

**Dependances backend:** Aucune

---

### Sprint 2: Home Screen Complet (Jours 4-7)

**User Stories:**
- US-2.1: En tant qu'utilisateur, je vois un hero billboard plein ecran avec le contenu en cours, qui change automatiquement toutes les 8s
- US-2.2: En tant qu'utilisateur, je peux cliquer "Play" ou "More Info" sur le hero
- US-2.3: En tant qu'utilisateur, je vois des rangees horizontales: Continue Watching, Ma Liste, Recently Added, genres
- US-2.4: En tant qu'utilisateur, les cartes zooment et montrent le titre quand je les selectionne avec le D-pad
- US-2.5: En tant qu'utilisateur, je vois une barre de progression rouge sur les contenus en cours

**Taches Techniques:**
1. Creer `feature/home/components/NetflixMediaCard.kt` avec variants POSTER/WIDE/TOP_TEN, animations focus, `NetflixProgressBar`
2. Creer `feature/home/components/NetflixContentRow.kt` avec `TvLazyRow`
3. Creer `feature/home/components/NetflixHeroBillboard.kt` avec auto-rotation, gradients, boutons Play/Info
4. Creer `feature/home/NetflixHomeScreen.kt` orchestrant hero + rangees dans `TvLazyColumn`
5. Modifier `feature/home/HomeUiState.kt`: ajouter `favorites: List<MediaItem>`
6. Modifier `feature/home/HomeViewModel.kt`: injecter `FavoritesRepository`, combiner dans `_uiState`
7. Modifier `feature/home/DiscoverScreen.kt`: `ContentState` appelle `NetflixHomeContent`
8. Performance: verifier 60fps scroll, limiter hero a 10 items

**Dependances backend:** Aucune (toutes les donnees viennent des repos existants)

---

### Sprint 3: Ecran Detail + Library (Jours 8-10)

**User Stories:**
- US-3.1: En tant qu'utilisateur, l'ecran detail affiche un backdrop plein ecran avec le titre, les metadonnees et des boutons d'action
- US-3.2: En tant qu'utilisateur, je vois des onglets Episodes/More Like This/Details sur l'ecran detail d'une serie
- US-3.3: En tant qu'utilisateur, la navigation dans les episodes se fait via l'onglet Episodes sans changer d'ecran
- US-3.4: En tant qu'utilisateur, les ecrans Movies et TV Shows utilisent les nouvelles cartes Netflix

**Taches Techniques:**
1. Creer `feature/details/NetflixDetailScreen.kt` avec hero backdrop, metadata, boutons
2. Creer `feature/details/components/NetflixDetailTabs.kt` avec onglets Episodes/More Like This/Details
3. Modifier `feature/details/MediaDetailScreen.kt`: body utilise `NetflixDetailContent`
4. Restyle `feature/library/LibrariesScreen.kt`: NetflixMediaCard, retrait AlphabetSidebar, padding top bar, filtres simplifies
5. Restyle `feature/favorites/FavoritesScreen.kt`: heading "Ma Liste", Netflix cards
6. Tester: selection saison, lecture episode, toggle favori, toggle watched

**Dependances backend:** Aucune

---

### Sprint 4: Player Overlay + Search (Jours 11-13)

**User Stories:**
- US-4.1: En tant qu'utilisateur, le player affiche une overlay minimale Netflix-style avec titre, play/pause central, et barre de progression
- US-4.2: En tant qu'utilisateur, les boutons skip intro/credits apparaissent au bon moment
- US-4.3: En tant qu'utilisateur, l'ecran recherche affiche un clavier grille navigable au D-pad
- US-4.4: En tant qu'utilisateur, les resultats de recherche s'affichent en rangees horizontales par type

**Taches Techniques:**
1. Creer `feature/player/ui/NetflixPlayerControls.kt` reutilisant `EnhancedSeekBar` et `SkipMarkerButton`
2. Modifier `feature/player/VideoPlayerScreen.kt`: remplacer `PlezyPlayerControls` par `NetflixPlayerControls`
3. Verifier: auto-hide 5s, tous les dialogs (audio/sub/settings) fonctionnent, skip markers
4. Creer `feature/search/components/NetflixOnScreenKeyboard.kt`
5. Creer `feature/search/NetflixSearchScreen.kt` avec layout keyboard+resultats
6. Modifier `feature/search/SearchScreen.kt`: body utilise `NetflixSearchContent`
7. Tester: navigation D-pad clavier, saisie, resultats, ouverture detail depuis resultats

**Dependances backend:** Aucune

---

### Sprint 5: Polish + Integration (Jours 14-15)

**User Stories:**
- US-5.1: En tant qu'utilisateur, l'historique affiche les contenus avec le style Netflix
- US-5.2: En tant qu'utilisateur, les ecrans Downloads, IPTV et Settings s'affichent correctement avec la top bar
- US-5.3: En tant qu'utilisateur, le focus est restaure quand je reviens sur un ecran precedent

**Taches Techniques:**
1. Restyle `feature/history/HistoryScreen.kt` avec Netflix cards
2. Ajouter `padding(top = 56.dp)` aux ecrans Downloads, IPTV, Settings, ServerStatus
3. Ajouter acces Settings via avatar profil dans la top bar
4. Focus restoration testing sur tous les ecrans (back navigation)
5. Test E2E complet: Login -> Profil -> Home -> Detail -> Player -> Back -> Search -> Ma Liste
6. Performance profiling sur device TV reel ou emulateur
7. Fix bugs visuels, ajustement espacement/tailles

**Dependances backend:** Aucune

---

# 7. Exigences Techniques, Contraintes et Bonnes Pratiques

## 7.1 Compatibilite Android TV

- **Telecommande uniquement**: pas de `clickable` sans equivalent `focusable`. Tout element interactif doit etre focusable via D-pad.
- **Resolutions**: 1080p (1920x1080) et 720p (1280x720). Utiliser `dp` pour tout le sizing.
- **Safe areas**: 48dp de padding horizontal pour eviter le overscan TV. Les rangees utilisent `contentPadding(horizontal=48.dp)`.
- **Taille elements**: minimum 48dp pour les zones cliquables, posters 140dp+ de large pour lisibilite a distance.
- **Manifest** (deja configure): `android.software.leanback` required, `android.hardware.touchscreen` not required, `LEANBACK_LAUNCHER` category.

## 7.2 Librairies Recommandees (toutes deja presentes)

| Librairie | Version | Usage |
|-----------|---------|-------|
| `androidx.tv:tv-foundation` | 1.0.0-alpha12 | `TvLazyColumn`, `TvLazyRow` |
| `androidx.tv:tv-material` | 1.0.1 | `ImmersiveList`, TV Material3 components |
| `androidx.compose:compose-bom` | 2026.01.00 | Compose foundation |
| `androidx.media3:media3-exoplayer` | 1.5.1 | Video playback |
| `io.coil-kt:coil-compose` | 2.7.0 | Image loading + caching |
| `com.google.dagger:hilt-android` | 2.58 | Dependency injection |
| `androidx.navigation:navigation-compose` | 2.9.6 | Screen navigation |
| `androidx.room:room-runtime` | 2.8.4 | Local database |

**Aucune nouvelle dependance requise.**

## 7.3 Performance & UX TV

### Minimiser les recompositions lourdes
- Utiliser `key` sur tous les items des listes lazy
- `remember(key)` pour les calculs derives (ratios, URLs)
- `derivedStateOf` pour observer le scroll state
- Eviter les captures de lambda instables dans les composables
- `@Stable` ou `@Immutable` sur les data classes passees aux composables si necessaire

### Gestion timeouts reseau
- Deja configure: 10s connect, 30s read/write dans `NetworkModule.kt`
- `PlexApiCache` avec TTL pour limiter les appels redondants

### Placeholders et skeletons
- Coil fournit `placeholder(ColorPainter(shimmerColor))` pour les images en cours de chargement
- Etats Loading dans chaque UiState (`isLoading = true` -> afficher shimmer)
- Error states avec bouton retry

### Fluidite sur TV peu puissantes
- **Hero billboard**: limiter a 10 items, charger le backdrop suivant en avance avec Coil prefetch
- **TvLazyRow/Column**: utilisation native de recycling, pas de `items(items.size)` mais `items(items, key = ...)`
- **Animations**: `tween(200ms)` pour les zoom focus (pas de `spring` qui peut janker sur hardware faible)
- **Crossfade images desactive** dans les cartes (`crossfade(false)`)
- **Coil disk cache**: 250MB deja configure dans `PlexHubApplication.kt`

---

# 8. Suggestions d'Evolutions Futures

## 8.1 Profils Multiples Locaux

**Description**: Permettre a plusieurs utilisateurs de l'app d'avoir des favoris, historique et preferences separes, sans Plex Home.

**Mini-plan:**
- **Data**: Ajouter `UserProfile` entity dans Room, champ `profileId` sur `FavoriteEntity`, `SettingsDataStore`
- **UI**: Ecran selection profil au lancement (grille avatars type Netflix), avatar dans top bar
- **Backend**: Aucun changement cote Plex, separation purement locale
- **Effort**: ~3 jours

## 8.2 Recommandations Personnalisees Avancees

**Description**: Generer des rangees "Because you watched X", "Top picks for you" basees sur l'historique de visionnage.

**Mini-plan:**
- **Data**: Nouveau `RecommendationEngine` dans le module `domain/`, analyse historique local + genres/acteurs
- **UI**: Nouvelles rangees dans Home avec titres dynamiques
- **Backend**: Optionnel - endpoint `/recommendations` cote Python backend pour des calculs plus avances
- **Effort**: ~5 jours (simple heuristique locale), ~2 semaines (avec backend ML)

## 8.3 Notifications (Nouveaux Episodes, Nouveautes)

**Description**: Alerter l'utilisateur quand un nouvel episode d'une serie suivie est disponible ou quand du nouveau contenu est ajoute.

**Mini-plan:**
- **Data**: Comparer `addedAt` des items entre syncs dans `SyncRepositoryImpl`, `NotificationManager` Android
- **UI**: Badge sur l'icone Ma Liste dans top bar, ecran notifications dedie
- **Backend**: Le worker `LibrarySync` (toutes les 6h) detecte deja les nouveautes
- **Effort**: ~3 jours

## 8.4 Systeme de Notes (Thumbs Up/Down)

**Description**: Permettre a l'utilisateur de noter les contenus pour ameliorer les recommandations.

**Mini-plan:**
- **Data**: Nouvelle `RatingEntity` dans Room (`ratingKey`, `rating: Int` [-1, 0, 1]), `RatingRepository`
- **UI**: Boutons thumbs up/down sur l'ecran detail et dans les cartes au focus
- **Backend**: Optionnel - sync avec Plex via `PUT /library/metadata/{id}/rate`
- **Effort**: ~2 jours

---

# Resume des Fichiers

## 10 Fichiers a Creer

| # | Chemin | Composable Principal |
|---|--------|---------------------|
| 1 | `app/.../feature/main/NetflixTopBar.kt` | `NetflixTopBar` |
| 2 | `app/.../feature/home/NetflixHomeScreen.kt` | `NetflixHomeContent` |
| 3 | `app/.../feature/home/components/NetflixHeroBillboard.kt` | `NetflixHeroBillboard` |
| 4 | `app/.../feature/home/components/NetflixContentRow.kt` | `NetflixContentRow` |
| 5 | `app/.../feature/home/components/NetflixMediaCard.kt` | `NetflixMediaCard` |
| 6 | `app/.../feature/details/NetflixDetailScreen.kt` | `NetflixDetailContent` |
| 7 | `app/.../feature/details/components/NetflixDetailTabs.kt` | `NetflixDetailTabs` |
| 8 | `app/.../feature/player/ui/NetflixPlayerControls.kt` | `NetflixPlayerControls` |
| 9 | `app/.../feature/search/NetflixSearchScreen.kt` | `NetflixSearchContent` |
| 10 | `app/.../feature/search/components/NetflixOnScreenKeyboard.kt` | `NetflixOnScreenKeyboard` |

## 9 Fichiers a Modifier

| # | Chemin | Modification |
|---|--------|-------------|
| 1 | `app/.../feature/main/MainScreen.kt` | Remplacer `AppSidebar` par `Box + NetflixTopBar` overlay |
| 2 | `app/.../feature/home/DiscoverScreen.kt` | `ContentState` appelle `NetflixHomeContent` |
| 3 | `app/.../feature/home/HomeUiState.kt` | Ajouter `favorites: List<MediaItem>` |
| 4 | `app/.../feature/home/HomeViewModel.kt` | Injecter `FavoritesRepository`, combiner dans state |
| 5 | `app/.../feature/details/MediaDetailScreen.kt` | Body utilise `NetflixDetailContent` |
| 6 | `app/.../feature/player/VideoPlayerScreen.kt` | `PlezyPlayerControls` -> `NetflixPlayerControls` |
| 7 | `app/.../feature/search/SearchScreen.kt` | Body utilise `NetflixSearchContent` |
| 8 | `app/.../core/designsystem/Color.kt` | Ajouter constantes Netflix (`NetflixRed`, etc.) |
| 9 | `app/.../core/designsystem/Theme.kt` | Ajouter `NetflixColorScheme` et option "Netflix" |

## Verification

### Tests End-to-End
1. Lancer l'app -> ecran auth -> login PIN -> selection profil -> Home
2. Verifier: hero billboard auto-rotate, boutons Play/Info, rangees scrollables
3. D-pad: naviguer entre rangees, top bar, cartes avec focus visible
4. Ouvrir detail: backdrop, metadonnees, onglets, lecture
5. Player: overlay minimaliste, skip intro, audio/sub selection
6. Search: clavier grille, saisie, resultats en rangees
7. Back navigation: focus restaure sur chaque ecran
8. Offline: redirection Downloads, banner offline

### Performance
- 60fps scroll sur Home avec 10+ rangees
- Hero billboard transition fluide
- Pas de jank sur zoom focus des cartes
- Memoire: pas de leak avec rotation hero (limiter a 10 items)
