# PlexHubTV - Plan de Refactoring Incremental

> **Pour Claude:** Quand l'utilisateur demande "implemente l'etape X", executer UNIQUEMENT cette etape, compiler, verifier, committer.

**Objectif:** Reduire la dette technique identifiee dans ANALYSE_FONCTIONNELLE.md (30 problemes) via un refactoring ISO-fonctionnel incremental en 4 vagues et 16 etapes.

**Approche:** Ultra conservatrice. Chaque etape est autonome, compilable, et testable independamment. Aucun big bang. Commits isoles. Pas de feature flags necessaires (on extrait sans changer le comportement).

**Decouverte importante:** `MediaUrlResolver` existe deja (`data/src/main/java/.../core/util/MediaUrlResolver.kt`) — certains repos l'utilisent deja (Hubs, OnDeck), d'autres non (Library). L'etape 1.3 consiste a migrer les retardataires.

**Pile technique:** Kotlin, Hilt, Room, Paging 3, Compose, JUnit 4 + Google Truth + MockK

---

## VAGUE 1 : Securisation (Risque minimal)

---

### Etape 1.1 — Centraliser UnificationIdCalculator

**Problemes cibles:** DUP-07, MUT-04

**Objectif:** Extraire la logique de calcul de `unificationId` (presente dans 3 mappers avec 3 implementations differentes) dans une fonction pure unique dans `:core:model`.

**Fichiers a creer:**
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/UnificationId.kt`
- `core/model/src/test/java/com/chakir/plexhubtv/core/model/UnificationIdTest.kt`

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt` (remplacer `calculateUnificationId()` lignes 422-433)
- `data/src/main/java/com/chakir/plexhubtv/data/mapper/XtreamMediaMapper.kt` (remplacer `buildUnificationId()` lignes 158-164)
- `data/src/main/java/com/chakir/plexhubtv/data/mapper/BackendMediaMapper.kt` (ajouter fallback si `dto.unificationId` est vide)

**Plan de modification:**
1. Lire les 3 implementations existantes et identifier la logique canonique :
   - `MediaMapper` : `imdb://X` > `tmdb://X` > `title_year` (normalise via regex `[^a-z0-9 ]`)
   - `XtreamMediaMapper` : `title_normalized_year` (normalise via `StringNormalizer.normalizeForSorting`)
   - `BackendMediaMapper` : copie directe du DTO (pre-calcule par le backend)
2. Creer `UnificationId.kt` avec une fonction `calculateUnificationId(imdbId: String?, tmdbId: String?, title: String?, year: Int?): String` qui reproduit EXACTEMENT la logique de `MediaMapper` (la plus complete)
3. Creer les TUs dans `UnificationIdTest.kt` : IMDb prioritaire, TMDB si pas IMDb, fallback title+year, title null, caracteres speciaux
4. Modifier `MediaMapper` : supprimer `calculateUnificationId()`, appeler la nouvelle fonction
5. Modifier `XtreamMediaMapper` : supprimer `buildUnificationId()`, appeler la nouvelle fonction. **Attention** : Xtream n'a jamais d'IMDb/TMDB, donc le fallback title+year sera toujours utilise — verifier que la normalisation produit le MEME resultat (sinon les items Xtream existants ne matcheront plus)
6. Modifier `BackendMediaMapper` : ajouter un fallback `if (dto.unificationId.isBlank()) calculateUnificationId(...)` pour les DTOs sans unificationId pre-calcule
7. Build + run tests existants (`MediaMapperTest` lignes 359-375 couvrent deja l'unificationId)

**Tests manuels:**
- Ouvrir la vue unifiee (serveur "All") avec des films presents sur Plex ET backend → verifier qu'ils sont toujours groupes (badge multi correct)
- Ouvrir le detail d'un film Xtream → verifier que l'enrichment Room-first fonctionne (pas de latence reseau)
- Ouvrir le detail d'un film backend → idem

**Risques de regression:**
- **CRITIQUE** : Si la normalisation de la nouvelle fonction differe de l'ancienne XtreamMediaMapper, les items Xtream existants en Room auront un unificationId different → enrichment cassee. **Mitigation** : Comparer les outputs des 2 fonctions dans un TU avant de remplacer.
- Faible : BackendMediaMapper n'avait pas de fallback, mais les DTOs du backend ont toujours un unificationId pre-rempli.

**Conditions de done:**
- [ ] `UnificationId.kt` compile et ses TUs passent
- [ ] `MediaMapperTest` existants passent sans modification
- [ ] Build complet (`./gradlew assembleDebug`) reussit
- [ ] Commit isole : `refactor(DUP-07): extract UnificationIdCalculator to :core:model`

---

### Etape 1.2 — Creer ServerNameResolver

**Problemes cibles:** DUP-05, MUT-03

**Objectif:** Remplacer la construction manuelle de la map `serverId → serverName` (dupliquee dans 4 repositories) par un singleton injectable.

**Fichiers a creer:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/ServerNameResolver.kt`

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt` (supprimer bloc lignes 332-338, injecter ServerNameResolver)
- `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt` (supprimer `buildServerNameMap()`, injecter ServerNameResolver)
- `data/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt` (remplacer `servers.find { ... }?.name`)
- `data/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt` (idem)

**Plan de modification:**
1. Creer `ServerNameResolver` comme `@Singleton` avec injection de `AuthRepository`, `BackendRepository`, `XtreamAccountRepository`
2. Methode `suspend fun getServerNameMap(): Map<String, String>` — combine Plex (clientIdentifier→name), Backend (backend_id→label), Xtream (xtream_id→label)
3. Migrer `LibraryRepositoryImpl` en premier (le plus complet — inclut deja Plex+Backend)
4. Migrer `MediaDetailRepositoryImpl` (actuellement Plex-only → sera enrichi automatiquement)
5. Migrer `HubsRepositoryImpl` et `OnDeckRepositoryImpl` (utilisent `servers.find { ... }?.name`)
6. Build + verifier

**Tests manuels:**
- Vue unifiee : badge multi affiche les bons noms de serveurs (pas de "null" ou de badge manquant)
- Detail d'un film multi-source : dialog de source selection affiche les bons noms
- Hub "Recently Added" : les noms de serveurs sont corrects dans les tooltips/badges

**Risques de regression:**
- Faible : Extraction pure, meme logique. Le seul risque est d'oublier de fournir le ServerNameResolver dans le module Hilt.
- **Attention** : `HubsRepositoryImpl` et `OnDeckRepositoryImpl` n'incluaient PAS backend/xtream dans leur map → ils vont maintenant les inclure, ce qui pourrait faire apparaitre des badges multi la ou il n'y en avait pas (c'est le comportement CORRECT, pas une regression)

**Conditions de done:**
- [ ] Build complet reussit
- [ ] Les 4 repos utilisent ServerNameResolver au lieu de construire la map inline
- [ ] Badge multi fonctionne sur vue unifiee (Plex + Backend)
- [ ] Commit isole : `refactor(DUP-05): extract ServerNameResolver singleton`

---

### Etape 1.3 — Migrer les repos retardataires vers MediaUrlResolver

**Problemes cibles:** DUP-06

**Objectif:** `MediaUrlResolver` existe deja et est utilise par HubsRepositoryImpl et OnDeckRepositoryImpl. `LibraryRepositoryImpl` construit encore ses URLs manuellement (lignes 402-424). Migrer ce dernier pour utiliser le resolver existant.

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt` (remplacer le bloc URL inline par `mediaUrlResolver.resolveUrls()`)

**Plan de modification:**
1. Ajouter `MediaUrlResolver` a l'injection de `LibraryRepositoryImpl`
2. Remplacer le bloc de construction d'URL (lignes 402-424) par un appel a `mediaUrlResolver.resolveUrls(finalDomain, baseUrl, token)`
3. Verifier que `parentThumb` et `grandparentThumb` sont correctement resolus (le resolver existant les gere)
4. Build + verifier visuellement

**Tests manuels:**
- Ouvrir la library en mode grille → les posters s'affichent correctement (pas d'URL cassees)
- Ouvrir la library en mode liste → les thumbnails sont correctement redimensionnees
- Ouvrir un show → le poster parent s'affiche

**Risques de regression:**
- Faible : Le resolver fait exactement la meme chose que le code inline. Verifier que le `getOptimizedImageUrl` est appele avec les memes dimensions.

**Conditions de done:**
- [ ] Plus aucun `X-Plex-Token` inline dans `LibraryRepositoryImpl`
- [ ] Build complet reussit
- [ ] Posters et thumbnails s'affichent correctement
- [ ] Commit isole : `refactor(DUP-06): use MediaUrlResolver in LibraryRepositoryImpl`

---

## VAGUE 2 : Consolidation (Risque faible)

---

### Etape 2.1 — Creer BaseViewModel avec error channel

**Problemes cibles:** DUP-04, MUT-02

**Objectif:** Extraire le boilerplate `_errorEvents = Channel<AppError>` + `errorEvents = _errorEvents.receiveAsFlow()` dans une classe abstraite. Migrer les 5 ViewModels concernes.

**VMs concernes** (decouverte : 5, pas 11) :
- `MediaDetailViewModel.kt` (ligne 60 — unbuffered)
- `HubViewModel.kt` (ligne 40 — unbuffered)
- `HomeViewModel.kt` (ligne 43 — BUFFERED)
- `SearchViewModel.kt` (ligne 49 — BUFFERED)
- `LibraryViewModel.kt` (ligne 70 — unbuffered)

**Fichiers a creer:**
- `app/src/main/java/com/chakir/plexhubtv/feature/common/BaseViewModel.kt`

**Fichiers a modifier:**
- Les 5 VMs ci-dessus (supprimer les declarations error channel, heriter de BaseViewModel)

**Plan de modification:**
1. Creer `BaseViewModel` dans `feature/common/` :
   - `protected val _errorEvents = Channel<AppError>(Channel.BUFFERED)` (unifier sur BUFFERED pour eviter les suspensions bloquantes)
   - `val errorEvents = _errorEvents.receiveAsFlow()`
   - `protected suspend fun emitError(error: AppError) = _errorEvents.send(error)`
2. Migrer `HubViewModel` en premier (le plus simple)
3. Verifier que le Hub affiche toujours les erreurs
4. Migrer les 4 autres un par un, build entre chaque
5. Supprimer les declarations inline dans chaque VM

**Tests manuels:**
- Couper le WiFi → ouvrir un ecran Hub/Home/Library/Search → verifier que les erreurs s'affichent toujours en snackbar
- Remettre le WiFi → verifier que le contenu charge normalement

**Risques de regression:**
- Faible : Les VMs qui utilisaient `Channel()` unbuffered passent a BUFFERED. Le seul effet est que `emitError()` ne suspendra plus si aucun collecteur n'ecoute (la snackbar peut etre perdue si l'ecran n'est pas visible — c'est acceptable).
- **Attention** : Ne PAS migrer les VMs qui n'ont PAS d'error channel (CollectionDetail, etc.) — ils gèrent les erreurs autrement.

**Conditions de done:**
- [ ] 5 VMs heritent de BaseViewModel
- [ ] Plus aucune declaration `_errorEvents` inline dans ces 5 VMs
- [ ] Build complet reussit
- [ ] Les snackbars d'erreur s'affichent sur Hub, Home, Library, Search, MediaDetail
- [ ] Commit isole : `refactor(DUP-04): extract BaseViewModel with error channel`

---

### Etape 2.2 — Creer composable HandleErrors

**Problemes cibles:** DUP-04 (cote UI), MUT-05

**Objectif:** Extraire le pattern `LaunchedEffect(errorEvents) { errorEvents.collect { ... snackbar.show() } }` (duplique dans 5 ecrans) dans un composable reutilisable.

**Ecrans concernes** (5) :
- `HubScreen.kt` (ligne 75)
- `LibrariesScreen.kt` (ligne 151)
- `DiscoverScreen.kt` (ligne 99)
- `MediaDetailScreen.kt` (ligne 94)
- `SearchScreen.kt` (ligne 65)

**Fichiers a creer:**
- `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/HandleErrors.kt`

**Fichiers a modifier:**
- Les 5 Screen composables ci-dessus

**Plan de modification:**
1. Creer `HandleErrors.kt` avec un composable :
   ```kotlin
   @Composable
   fun HandleErrors(errorFlow: Flow<AppError>, snackbarHostState: SnackbarHostState)
   ```
2. Le composable encapsule le `LaunchedEffect` + `collect` + `snackbarHostState.showSnackbar()`
3. Migrer `HubScreen` en premier → remplacer le bloc LaunchedEffect par `HandleErrors(errorEvents, snackbarHostState)`
4. Verifier visuellement → migrer les 4 autres

**Tests manuels:**
- Couper WiFi → ouvrir chaque ecran → verifier snackbar d'erreur

**Risques de regression:**
- Minimal. Le composable fait exactement la meme chose.

**Conditions de done:**
- [ ] 5 ecrans utilisent `HandleErrors()` au lieu du LaunchedEffect inline
- [ ] Build complet reussit
- [ ] Commit isole : `refactor(DUP-04): extract HandleErrors composable`

---

### Etape 2.3 — Extraire MediaLibraryQueryBuilder

**Problemes cibles:** MUT-06 (source Gemini)

**Objectif:** Extraire les ~300 lignes de construction SQL dynamique (StringBuilder + SimpleSQLiteQuery) de `LibraryRepositoryImpl` dans une classe testable dediee.

**Fichiers a creer:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilder.kt`
- `data/src/test/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilderTest.kt`

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt` (remplacer 3 blocs SQL inline)

**Plan de modification:**
1. Creer `MediaLibraryQueryBuilder` avec :
   - `data class QueryConfig(isUnified, mediaType, libraryKey, filter, sortOrder, genre, serverId, excludedServerIds, query, sort, isDescending)`
   - `fun buildPagedQuery(config: QueryConfig): SimpleSQLiteQuery`
   - `fun buildCountQuery(config: QueryConfig): SimpleSQLiteQuery`
   - `fun buildIndexQuery(config: QueryConfig, letter: String): SimpleSQLiteQuery`
2. Ecrire les TUs AVANT de brancher dans LibraryRepositoryImpl :
   - Test unified mode : SQL contient `GROUP BY COALESCE`, `MAX(media.metadata_score)`, `GROUP_CONCAT`
   - Test non-unified mode : SQL contient `WHERE librarySectionId = ?`, `ORDER BY pageOffset ASC`
   - Test genre filter : SQL contient `genres LIKE ?`
   - Test server exclusion : SQL contient `AND serverId NOT IN (?)`
   - Test search query : SQL contient `AND title LIKE ?`
   - Test sort directions : `ASC` vs `DESC` correct
3. Extraire le SQL de `getLibraryContent()` (lignes 162-277) → `buildPagedQuery()`
4. Extraire le SQL de `getFilteredCount()` (lignes 474-529) → `buildCountQuery()`
5. Extraire le SQL de `getIndexOfFirstItem()` (lignes 572-636) → `buildIndexQuery()`
6. Build + run TUs + verifier visuellement

**Tests manuels:**
- Ouvrir library en vue unifiee → les films s'affichent et sont tries correctement
- Changer le tri (Title, Date Added, Year, Rating) → l'ordre change
- Filtrer par genre → le filtrage fonctionne
- Filtrer par serveur → le filtrage fonctionne
- Rechercher un film → les resultats sont corrects
- Alphabet jump → la navigation par lettre fonctionne

**Risques de regression:**
- **MOYEN** : Un espace ou une virgule manquante dans le SQL = crash silencieux (PagingSource emet `LoadState.Error` sans crash visible). **Mitigation** : TUs verifient la syntaxe SQL pour chaque combinaison de parametres.
- Les bind args doivent etre dans le meme ordre que les `?` dans le SQL.

**Conditions de done:**
- [ ] `MediaLibraryQueryBuilderTest` couvre unified/non-unified, tous les filtres, tous les tris
- [ ] `LibraryRepositoryImpl` n'a plus aucun `StringBuilder` SQL inline
- [ ] Build complet + TUs passent
- [ ] Library fonctionne identiquement (tri, filtre, recherche, alphabet jump)
- [ ] Commit isole : `refactor(MUT-06): extract MediaLibraryQueryBuilder`

---

### Etape 2.4 — Migrer ServerMapper de Gson a kotlinx-serialization

**Problemes cibles:** INC-05

**Objectif:** Eliminer la derniere utilisation de Gson dans le codebase (ServerMapper.kt) en faveur de kotlinx-serialization (utilise partout ailleurs).

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/mapper/ServerMapper.kt` (remplacer `Gson()` par `Json {}`)

**Plan de modification:**
1. Lire `ServerMapper.kt` — Gson est utilise pour serialiser/deserialiser `List<ConnectionCandidateEntity>` en JSON (stocke dans Room)
2. Ajouter `@Serializable` a `ConnectionCandidateEntity` si absent
3. Remplacer `gson.fromJson(...)` par `Json.decodeFromString<List<ConnectionCandidateEntity>>(json)`
4. Remplacer `gson.toJson(...)` par `Json.encodeToString(candidates)`
5. Ajouter un `try/catch` avec fallback Gson pour les entites existantes en base (migration progressive)
6. Supprimer l'import Gson et les declarations `private val gson`
7. Build + verifier que le login et la liste de serveurs fonctionnent

**Tests manuels:**
- Se deconnecter → se reconnecter → verifier que les serveurs s'affichent
- Ouvrir une library → verifier que la connexion au serveur fonctionne
- **Cas limite** : Un utilisateur avec des serveurs deja en base au format Gson → le fallback doit fonctionner

**Risques de regression:**
- **MOYEN** : Les entites existantes en Room sont stockees au format Gson. Si le format kotlinx-serialization differe (ex: champs null omis vs `null` explicite), la deserialisation echouera. **Mitigation** : Fallback try-catch qui tente d'abord kotlinx, puis Gson en dernier recours.
- Evaluer si on peut eventuellement supprimer la dependance Gson du module `:data` (verifier qu'aucun autre code ne l'utilise).

**Conditions de done:**
- [ ] Plus aucun import `com.google.gson` dans ServerMapper.kt
- [ ] Build complet reussit
- [ ] Login + affichage des serveurs fonctionne
- [ ] Fallback Gson fonctionne pour les entites existantes
- [ ] Commit isole : `refactor(INC-05): migrate ServerMapper from Gson to kotlinx-serialization`

---

## VAGUE 3 : Refactoring structurel (Risque modere)

---

### Etape 3.1 — Extraire PreparePlaybackUseCase

**Problemes cibles:** DUP-01, DUP-02, MUT-01

**Objectif:** Unifier le flow de playback (enrichment → source check → queue → play) duplique entre `MediaDetailViewModel.PlayClicked/playItem()` et `SeasonDetailViewModel.PlayEpisode`.

**Fichiers a creer:**
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/PreparePlaybackUseCase.kt`

**Fichiers a modifier:**
- `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt` (simplifier PlayClicked + playItem)
- `app/src/main/java/com/chakir/plexhubtv/feature/details/SeasonDetailViewModel.kt` (simplifier PlayEpisode)

**Plan de modification:**
1. Analyser les 2 flows existants, identifier les variations :
   - MediaDetailVM : enrichment avec cache-hit optimization + PerformanceTracker
   - SeasonDetailVM : enrichment sans cache-hit + pas de PerformanceTracker pour le play
2. Creer `PreparePlaybackUseCase` avec sealed result :
   - `ReadyToPlay(item: MediaItem)` — une seule source, pret a jouer
   - `NeedsSourceSelection(item: MediaItem, sources: List<MediaSource>)` — multi-source, dialog requis
   - `AlreadyHasSources(item: MediaItem)` — item deja enrichi (Xtream/Backend avec sources pre-chargees)
3. Le use case encapsule : enrichment (avec skip si Xtream), source counting, mais PAS la queue building ni la navigation (ca reste dans le VM)
4. Integrer dans `MediaDetailViewModel` — remplacer le bloc PlayClicked par appel au use case
5. Build + tester play depuis detail
6. Integrer dans `SeasonDetailViewModel` — remplacer PlayEpisode
7. Build + tester play depuis season

**Tests manuels:**
- Play un film Plex depuis le detail → lecture demarre
- Play un film multi-source → dialog de selection s'affiche
- Play un film Xtream → enrichment skippe, lecture directe
- Play un film Backend → enrichment fonctionne
- Play un episode depuis la liste des saisons → lecture demarre
- Play un episode multi-source → dialog de selection s'affiche

**Risques de regression:**
- **MOYEN-ELEVE** : Le flow de playback est le coeur de l'app. Une regression ici = app inutilisable. **Mitigation** : Integrer dans UN SEUL VM d'abord (MediaDetail), tester exhaustivement, puis migrer SeasonDetail.
- Le `isPlayButtonLoading` doit rester lie a `isEnriching` — ne pas casser ce lien.

**Conditions de done:**
- [ ] Les 2 VMs utilisent PreparePlaybackUseCase
- [ ] Play fonctionne depuis detail ET depuis season list
- [ ] Source selection dialog fonctionne pour multi-source
- [ ] Le bouton Play montre le loading pendant l'enrichment
- [ ] Build complet reussit
- [ ] Commit isole : `refactor(DUP-01,DUP-02): extract PreparePlaybackUseCase`

---

### Etape 3.2 — Extraire Loading Template helper

**Problemes cibles:** DUP-03

**Objectif:** Reduire le boilerplate de chargement (viewModelScope.launch { state.loading → useCase → fold }) present dans 5+ ViewModels.

**Fichiers a creer:**
- `app/src/main/java/com/chakir/plexhubtv/feature/common/ViewModelExtensions.kt`

**Fichiers a modifier:**
- `MediaDetailViewModel.kt`, `SeasonDetailViewModel.kt`, `CollectionDetailViewModel.kt`, `HomeViewModel.kt`, `HubViewModel.kt` (remplacer le template par l'extension)

**Plan de modification:**
1. Creer une extension function `ViewModel.launchLoading()` qui encapsule le pattern :
   - Set loading = true
   - Execute la suspend function
   - Fold success/failure
   - Set loading = false
2. Migrer `CollectionDetailViewModel` en premier (le plus simple)
3. Migrer les 4 autres progressivement

**Tests manuels:**
- Ouvrir chaque ecran → verifier que le loading indicator s'affiche puis disparait
- Simuler une erreur reseau → verifier que le loading s'arrete et l'erreur s'affiche

**Risques de regression:**
- Faible : Extraction de pattern identique. Le seul risque est de ne pas gerer correctement les lambdas `onSuccess`/`onFailure` specifiques a chaque VM.

**Conditions de done:**
- [ ] 5+ VMs utilisent l'extension au lieu du template inline
- [ ] Build complet reussit
- [ ] Commit isole : `refactor(DUP-03): extract launchLoading extension`

---

### Etape 3.3 — MediaSourceStrategy (interface + handlers)

**Problemes cibles:** MUT-07 (source Gemini)

**Objectif:** Eliminer les 21 branchements `startsWith("xtream_")`/`startsWith("backend_")` via un pattern Strategy. Cette etape cree l'infrastructure (interface + implementations) SANS migrer les VMs/repos (ca vient en 3.4).

**Fichiers a creer:**
- `domain/src/main/java/com/chakir/plexhubtv/domain/source/MediaSourceHandler.kt` (interface)
- `data/src/main/java/com/chakir/plexhubtv/data/source/PlexSourceHandler.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/source/XtreamSourceHandler.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/source/BackendSourceHandler.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/source/MediaSourceResolver.kt` (singleton qui route vers le bon handler)

**Plan de modification:**
1. Definir `MediaSourceHandler` dans `:domain` :
   - `fun matches(serverId: String): Boolean`
   - `suspend fun getDetail(ratingKey: String, serverId: String): Result<MediaItem>`
   - `suspend fun getSeasons(ratingKey: String, serverId: String): Result<List<MediaItem>>`
   - `suspend fun getEpisodes(seasonRatingKey: String, serverId: String): Result<List<MediaItem>>`
   - `suspend fun getStreamUrl(ratingKey: String, serverId: String): Result<String>`
   - `fun needsEnrichment(): Boolean`
2. Implementer `PlexSourceHandler` — delegue aux repos Plex existants
3. Implementer `XtreamSourceHandler` — encapsule la logique inline de `MediaDetailRepositoryImpl`
4. Implementer `BackendSourceHandler` — idem pour backend
5. Creer `MediaSourceResolver` : prend `Set<MediaSourceHandler>` via Hilt `@IntoSet`, expose `fun resolve(serverId): MediaSourceHandler`
6. Enregistrer les 3 handlers dans le module Hilt de `:data`
7. Build — a ce stade AUCUN code existant n'utilise encore les handlers, c'est du code additionnel

**Tests manuels:**
- Aucun test manuel necessaire (code additionnel non utilise)
- Verifier que le build compile

**Risques de regression:**
- **NUL** : On ajoute du code sans toucher a l'existant. Les branchements inline restent en place.

**Conditions de done:**
- [ ] Interface + 3 implementations + resolver compilent
- [ ] Hilt module enregistre les 3 handlers
- [ ] Build complet reussit
- [ ] Commit isole : `feat(MUT-07): add MediaSourceHandler interface + implementations`

---

### Etape 3.4 — Migrer MediaDetailRepositoryImpl vers MediaSourceResolver

**Problemes cibles:** MUT-07 (suite)

**Objectif:** Remplacer les 10 branchements `startsWith` dans `MediaDetailRepositoryImpl` par des appels au `MediaSourceResolver` cree en 3.3.

**Fichiers a modifier:**
- `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt` (10 branchements → delegation)

**Plan de modification:**
1. Injecter `MediaSourceResolver` dans `MediaDetailRepositoryImpl`
2. Migrer `getMediaDetail()` (lignes 57-62) : remplacer les 2 `if startsWith` par `resolver.resolve(serverId).getDetail()`
3. Build + tester detail Plex, Xtream, Backend
4. Migrer `getSeasonEpisodes()` (lignes 114-152) : 4 branchements → delegation
5. Build + tester saisons/episodes
6. Migrer `getShowSeasons()` (lignes 298-306) : 2 branchements
7. Migrer `getSimilarMedia()` (ligne 317) : 1 branchement
8. Migrer `metadataScore()` (ligne 266) : 1 branchement
9. Build final + test complet

**Tests manuels:**
- Detail d'un film Plex → metadata complete, poster correct
- Detail d'un film Xtream → metadata correcte
- Detail d'un film Backend → metadata correcte
- Saisons d'un show Plex → liste des saisons + episodes
- Saisons d'un show Xtream → idem
- Saisons d'un show Backend → idem
- "More Like This" (similar media) → s'affiche pour Plex, vide pour Xtream/Backend

**Risques de regression:**
- **MOYEN** : MediaDetailRepositoryImpl est utilise par le detail de chaque media. **Mitigation** : Migrer methode par methode avec build entre chaque, pas en big bang.

**Conditions de done:**
- [ ] 0 occurrence de `startsWith("xtream_")` ou `startsWith("backend_")` dans MediaDetailRepositoryImpl
- [ ] Detail/Saisons/Episodes fonctionnent pour les 3 types de source
- [ ] Build complet reussit
- [ ] Commit isole : `refactor(MUT-07): migrate MediaDetailRepositoryImpl to MediaSourceResolver`

---

### Etape 3.5 — Migrer les ViewModels vers MediaSourceResolver

**Problemes cibles:** MUT-07 (fin)

**Objectif:** Remplacer les 11 branchements restants dans 3 VMs + 1 controller.

**Fichiers a modifier:**
- `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerControlViewModel.kt` (5 branchements)
- `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt` (3 branchements)
- `app/src/main/java/com/chakir/plexhubtv/feature/details/SeasonDetailViewModel.kt` (2 branchements)
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (1 branchement)

**Plan de modification:**
1. Migrer `PlayerControlViewModel` (5 branchements — URL resolution, playback strategy)
2. Build + tester la lecture Plex, Xtream, Backend
3. Migrer `MediaDetailViewModel` (3 branchements — enrichment skip, playback)
4. Build + tester
5. Migrer `SeasonDetailViewModel` (2 branchements)
6. Build + tester
7. Migrer `PlayerController` (1 branchement — session tracking)
8. Build final

**Tests manuels:**
- Lecture complete d'un film Plex (play → pause → seek → stop)
- Lecture d'un film Xtream (direct stream)
- Lecture d'un film Backend (direct stream)
- Lecture d'un episode depuis la liste des saisons (3 sources)
- Verifier que le session tracking Plex fonctionne (position sauvegardee)

**Risques de regression:**
- **ELEVE** : PlayerControlViewModel gere le coeur de la lecture. **Mitigation** : Tester la lecture de bout en bout apres chaque fichier migre.

**Conditions de done:**
- [ ] Grep `startsWith("xtream_")` dans le codebase retourne 0 resultat (hors SQL/metadata_score)
- [ ] Lecture fonctionne pour les 3 types de source
- [ ] Build complet reussit
- [ ] Commit isole : `refactor(MUT-07): migrate ViewModels to MediaSourceResolver`

---

### Etape 3.6 — Splitter LibraryUiState

**Problemes cibles:** Maintenabilite du LibraryViewModel

**Objectif:** Decomposer le `LibraryUiState` (31 champs) en sous-states semantiques pour ameliorer la lisibilite et reduire les recompositions Compose inutiles.

**Fichiers a modifier:**
- `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryUiState.kt` (ou le fichier contenant le data class)
- `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt` (adapter les `.copy()` et `.update {}`)
- `app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt` (adapter les acces aux champs)

**Plan de modification:**
1. Creer les sous-states :
   - `LibraryDisplayState` : isLoading, isLoadingMore, totalItems, filteredItems, viewMode, selectedTab
   - `LibraryFilterState` : currentSort, isSortDescending, currentFilter, selectedGenre, selectedServerFilter, searchQuery, isSearchVisible, excludedServerIds, availableGenres, availableServers, availableServersMap
   - `LibrarySelectionState` : selectedServerId, selectedLibraryId, availableLibraries
   - `LibraryDialogState` : isSortDialogOpen, isServerFilterOpen, isGenreFilterOpen
   - `LibraryScrollState` : rawOffset, initialScrollIndex, lastFocusedId
2. Remplacer dans LibraryUiState par composition
3. Adapter LibraryViewModel : les `.copy()` deviennent `.copy(display = display.copy(isLoading = true))`
4. Adapter LibrariesScreen : les `uiState.isLoading` deviennent `uiState.display.isLoading`
5. Build + verifier visuellement

**Tests manuels:**
- Navigation complete dans Library : tri, filtre, genre, recherche, scroll, changement de vue
- Changement de serveur → les items changent
- Alphabet jump → le scroll fonctionne

**Risques de regression:**
- **MOYEN** : Beaucoup de `.copy()` a adapter dans le VM et de references dans le Screen. **Mitigation** : Le compilateur Kotlin detectera 100% des erreurs (property access).

**Conditions de done:**
- [ ] LibraryUiState utilise des sous-states semantiques
- [ ] Build complet reussit
- [ ] Toutes les fonctionnalites Library fonctionnent
- [ ] Commit isole : `refactor: split LibraryUiState into semantic sub-states`

---

## VAGUE 4 : Optimisations architecturales (Risque controle)

---

### Etape 4.1 — Decision sur le systeme de Profiles

**Problemes cibles:** PART-01

**Objectif:** Decider et implementer l'une des 2 options :
- **Option A** : Terminer la feature (CRUD + persistence + kids mode)
- **Option B** : Purger le code mort (supprimer UI + entities + repo non utilises)

**Cette etape necessite une decision de l'utilisateur avant implementation.**

**Si Option A (implementation complete) :**

**Fichiers a modifier:**
- `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSwitchScreen.kt` (wirer le CRUD)
- `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileViewModel.kt` (connecter ProfileRepository)
- `domain/src/main/java/com/chakir/plexhubtv/domain/repository/ProfileRepository.kt` (verifier les methodes)
- `core/datastore/` (persister l'active profile ID)

**Si Option B (purge) :**

**Fichiers a supprimer:**
- `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/` (dossier entier)
- Routes de navigation associees
- `ProfileEntity`, `ProfileDao` si non utilises ailleurs

**Tests manuels:**
- Option A : Creer un profil, switcher, verifier persistence apres restart
- Option B : Verifier que l'app demarre sans crash, que le bouton profiles dans TopBar est retire ou redirige

**Risques de regression:**
- Option A : Eleve (nouvelle feature, impact global sur le filtrage DB)
- Option B : Faible (suppression de code non utilise)

**Conditions de done:**
- [ ] Decision prise et documentee
- [ ] Implementation ou purge complete
- [ ] Build complet reussit
- [ ] Commit isole : `feat/chore(PART-01): [implement/remove] app profile system`

---

### Etape 4.2 — Performance Tracking uniforme

**Problemes cibles:** PART-05

**Objectif:** Ajouter `PerformanceTracker` dans les ViewModels qui ne l'ont pas (Home, Library, Search, Hub) pour avoir une observabilite complete.

**Fichiers a modifier:**
- `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt` (ajouter tracking sur loadHubs)
- `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt` (ajouter tracking sur loadLibrary)
- `app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt` (ajouter tracking sur search)
- `app/src/main/java/com/chakir/plexhubtv/feature/hub/HubViewModel.kt` (ajouter tracking sur loadHub)

**Plan de modification:**
1. Injecter `PerformanceTracker` dans chaque VM
2. Ajouter `tracker.startTrace("home_load")` / `tracker.endTrace()` autour des operations de chargement
3. Reprendre le pattern exact de `MediaDetailViewModel` pour la coherence
4. Build + verifier que les traces apparaissent dans Firebase Performance

**Tests manuels:**
- Ouvrir chaque ecran → verifier dans les logs que les traces PerformanceTracker sont emises
- (Optionnel) Verifier dans la console Firebase

**Risques de regression:**
- **NUL** : Ajout de tracking, pas de modification de logique.

**Conditions de done:**
- [ ] 4 VMs supplementaires trackent les performances
- [ ] Build complet reussit
- [ ] Commit isole : `feat(PART-05): add PerformanceTracker to Home/Library/Search/Hub`

---

### Etape 4.3 — Cleanup des Use Cases fantomes

**Problemes cibles:** PART-03, PART-04

**Objectif:** Supprimer les use cases vides ou redondants identifies dans l'analyse.

**Fichiers a evaluer:**
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/SyncOfflineContentUseCase.kt` (placeholder vide → supprimer)
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/EpisodeNavigationUseCase.kt` (verifier overlap avec `GetNextEpisodeUseCase`)
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/ResolveEpisodeSourcesUseCase.kt` (usage incertain)

**Plan de modification:**
1. Grep chaque use case dans le codebase pour verifier s'il est injecte/utilise quelque part
2. Supprimer les use cases non references
3. Pour les use cases references, evaluer si la reference est active ou morte
4. Build + verifier

**Tests manuels:**
- Build clean → aucune erreur de compilation
- Navigation dans l'app → aucun crash

**Risques de regression:**
- Faible si on verifie les references avant de supprimer. Hilt lancera une erreur au runtime si un binding manque.

**Conditions de done:**
- [ ] Use cases fantomes supprimes ou documentes comme volontairement conserves
- [ ] Build complet reussit
- [ ] Commit isole : `chore(PART-03,PART-04): remove unused use cases`

---

## RESUME GLOBAL

| Etape | Problemes | Effort | Risque | Prerequis |
|-------|-----------|--------|--------|-----------|
| 1.1 | DUP-07, MUT-04 | 1-2h | Faible | Aucun |
| 1.2 | DUP-05, MUT-03 | 2-3h | Faible | Aucun |
| 1.3 | DUP-06 | 30min | Faible | Aucun |
| 2.1 | DUP-04, MUT-02 | 1-2h | Faible | Aucun |
| 2.2 | DUP-04, MUT-05 | 1h | Faible | 2.1 |
| 2.3 | MUT-06 | 3-4h | Moyen | Aucun |
| 2.4 | INC-05 | 1-2h | Moyen | Aucun |
| 3.1 | DUP-01, DUP-02 | 3-4h | Moyen-Eleve | Aucun |
| 3.2 | DUP-03 | 1-2h | Faible | 2.1 |
| 3.3 | MUT-07 | 2-3h | Nul | Aucun |
| 3.4 | MUT-07 | 2-3h | Moyen | 3.3 |
| 3.5 | MUT-07 | 3-4h | Eleve | 3.3, 3.4 |
| 3.6 | Maintenabilite | 2-3h | Moyen | Aucun |
| 4.1 | PART-01 | 4-8h | Eleve/Faible | Aucun |
| 4.2 | PART-05 | 1-2h | Nul | Aucun |
| 4.3 | PART-03, PART-04 | 1h | Faible | Aucun |

**Effort total estime : 25-40h de sessions de travail**

**Ordre recommande (chemin critique) :**
1.1 → 1.2 → 1.3 → 2.1 → 2.2 → 2.3 → 2.4 → 3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6 → 4.1 → 4.2 → 4.3
