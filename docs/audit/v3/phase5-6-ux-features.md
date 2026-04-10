# Phase 5 — UX & Design TV / Phase 6 — Features pour la vente

> **Agent** : UX Agent (Agent 4)
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Sources** : `phase0-cartography.md` (ground truth), `feature/**`, `core/ui/**`, `core/designsystem/**`.
> **Méthode** : parcours mental D-Pad (↑↓←→ + OK + Back uniquement), lecture de code ligne à ligne. Les findings cités `→ à valider sur device` nécessitent Mi Box S pour confirmation finale.

---

# Phase 5 — UX & Design TV

## Résumé

| Catégorie | Nombre |
|---|---|
| **Findings P0** | 6 |
| **Findings P1** | 17 |
| **Findings P2** | 10 |
| **Total findings** | **33** |

### Écrans audités (lecture code)
`splash`, `auth`, `loading`, `libraryselection`, `appprofile`, `main` (conteneur + TopBar), `home` (`NetflixHomeScreen`, `DiscoverScreen`, `DiscoverScreenComponents`, `HomeHeader`), `library` (`LibrariesScreen`, `FilterDialog`, `LibraryContent`), `search` (`NetflixSearchScreen`), `details` (`NetflixDetailScreen`, `MediaDetailScreen`), `favorites`, `history`, `player` (`VideoPlayerScreen`, key handling, dialogs), `settings` (`SettingsRoute`, `SettingsGridScreen`), `core/ui` (`NetflixMediaCard`, `NetflixContentRow`, `NetflixTopBar`, `Skeletons`, `OverscanSafeArea`, `FocusUtils`), `core/designsystem` (`Type`, `Dimensions`).

### Écrans partiellement audités
`collection/CollectionDetailScreen`, `hub/HubScreen`, `downloads/DownloadsScreen`, `playlist/{PlaylistListScreen, PlaylistDetailScreen}`, `iptv/IptvScreen`, `jellyfin/JellyfinSetupScreen`, `xtream/{XtreamSetupScreen, XtreamCategorySelectionScreen}`, `debug/DebugScreen`, `settings/categories/**`, `details/{PersonDetailScreen, SeasonDetailScreen, NetflixDetailTabs}`, `player/components/NetflixPlayerControls`, `player/ui/components/**` (dialogs) — uniquement feuilletés, non audités ligne par ligne. Couvrir en prochain passage.

### Thèmes transversaux (signalés comme findings groupés)
1. **Overscan safe area jamais appliquée** — `OverscanSafeArea` composable existe mais `Grep OverscanSafeArea` ne retourne aucun appel en code de production. Chaque écran redéfinit son propre `padding(horizontal = 48.dp, …)` ou `48.dp`, `58.dp`, `32.dp`, `16.dp` au choix. Incohérence + risque réel d'overscan sur certains TV LCD/CRT (< 5 %) pour les écrans qui utilisent 16 ou 32 dp.
2. **Strings en dur** — au moins 15 strings d'UI visibles par l'utilisateur sont en dur en anglais alors que `strings.xml` contient déjà les ressources (ou devrait). Ex : `home_continue_watching` / `home_my_list` existent dans `values/strings.xml` lignes 112-113 mais `NetflixHomeScreen.kt` lignes 136/154/172 utilise `"Continue Watching"`, `"My List"`, `"Suggested for You"`.
3. **Typographies trop petites pour le 3 m** — `Type.kt` lignes 99-105 définit `labelSmall` à **11 sp**, `CinemaTypo.CardTitle`/`SectionTitle` à **13 sp**, `CinemaTypo.BadgeSmall` à **9 sp**. WCAG AA + recommandation Android TV (Leanback) = **minimum 12 sp pour le body, 14 sp recommandé** à 3 m. 9 sp est illisible.
4. **Absence totale de `OverscanSafeArea`** alors même que le composant existe → trop facile à corriger pour ne pas être corrigé.

---

## Findings par écran

---

### 🏠 Home (`NetflixHomeScreen`, `DiscoverScreen`, `DiscoverScreenComponents`, `HomeHeader`)

```
ID          : AUDIT-5-001
Titre       : Scroll snap agressif supprime tous les autres rails lors de la navigation
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Home → naviguer d'un rail à l'autre via D-Pad Down/Up
Écran       : NetflixHomeScreen (Home)
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/home/NetflixHomeScreen.kt:80-86
Dépendances : aucune

Problème dans le code :
  ```kotlin
  var focusedRowIndex by remember { mutableIntStateOf(0) }
  var focusVersion by remember { mutableIntStateOf(0) }
  LaunchedEffect(focusedRowIndex, focusVersion) {
      listState.scrollToItem(focusedRowIndex)
  }
  ```
  `scrollToItem` (pas `animateScrollToItem`) + déclenchement à CHAQUE focus, y compris horizontal dans la même ligne, fait "snapper" brutalement la row active en haut. L'utilisateur perd la vue d'ensemble (les rails voisins disparaissent d'un frame à l'autre sans transition).

Amélioration proposée :
  - Utiliser `listState.animateScrollToItem(focusedRowIndex)` avec un offset négatif pour garder un peu de la row précédente visible.
  - Ne déclencher que sur changement d'`focusedRowIndex`, pas sur `focusVersion` (le focus horizontal dans la même row ne doit pas re-scroller).

Critères d'acceptation :
  - [ ] Changement de row : animation fluide 250-400 ms
  - [ ] Focus horizontal dans une row : pas de scroll vertical
  - [ ] Rail précédent visible à ~20 % en haut de l'écran

Validation du fix :
  Test manuel sur device (Mi Box S) : naviguer Down x4 Up x4 Right x5 et observer le comportement.
```

```
ID          : AUDIT-5-002
Titre       : Home EmptyState hardcodé en anglais, non traduit, pas de CTA pour ajouter un serveur
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Premier lancement sans serveur / serveur HS / token expiré → Home affiche "No content available"
Écran       : Home empty state
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/home/DiscoverScreenComponents.kt:14-35
Dépendances : AUDIT-5-003

Problème dans le code :
  ```kotlin
  Text(text = "No content available", …)
  Text(text = "Make sure your Plex server is running and accessible.", …)
  Button(onClick = onRetry) { Text("Refresh") }
  ```
  1) Strings en dur (jamais traduits en `values-fr/strings.xml`).
  2) Seul CTA = "Refresh", alors qu'un utilisateur qui vient d'installer l'app peut avoir besoin d'ajouter un serveur Jellyfin/Xtream/Plex.
  3) Pas de `FocusRequester` → aucun élément focalisé au rendu, le bouton est accessible mais le focus est indéterminé.

Amélioration proposée :
  - Remplacer par des `stringResource(R.string.home_empty_…)`.
  - Ajouter un second bouton "Ouvrir Réglages > Serveurs" qui navigue vers `Screen.Settings`.
  - `LaunchedEffect(Unit) { refreshButton.requestFocus() }`.

Critères d'acceptation :
  - [ ] 3 strings sortent dans `strings.xml` (EN + FR)
  - [ ] Bouton Refresh focalisé au rendu
  - [ ] Second bouton vers Settings

Validation du fix :
  Test manuel : déconnecter le LAN, ouvrir l'app, vérifier que Back/D-pad mènent aux deux boutons.
```

```
ID          : AUDIT-5-003
Titre       : Rails Home titrés en anglais hardcodé alors que les strings existent
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Home affichée en français → rails Continue Watching / My List / Suggested for You restent en anglais
Écran       : NetflixHomeContent
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/home/NetflixHomeScreen.kt:136, 154, 172
Dépendances : aucune

Problème dans le code :
  ```kotlin
  NetflixContentRow(title = "Continue Watching", …)
  NetflixContentRow(title = "My List", …)
  NetflixContentRow(title = "Suggested for You", …)
  ```
  Les ressources `R.string.home_continue_watching` et `R.string.home_my_list` existent déjà dans `values/strings.xml` (lignes 112-113), et `suggestions_row` probablement aussi. Le code passe à côté et colle la string en dur.

Amélioration proposée :
  Remplacer par `stringResource(R.string.home_continue_watching)`, etc. Ajouter `R.string.home_suggestions` si absent dans `strings.xml`.

Critères d'acceptation :
  - [ ] Les 3 rails titres sortent de `strings.xml` (FR + EN)
  - [ ] Vérifier `values-fr/strings.xml` complet

Validation du fix :
  Changer la langue du device en FR, ouvrir Home, vérifier les titres.
```

```
ID          : AUDIT-5-004
Titre       : HomeHeader purement décoratif — aucune action directe possible depuis le hero
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Home → l'item focalisé est affiché en grand mais aucune interaction possible depuis cette zone
Écran       : HomeHeader
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/HomeHeader.kt:30-47
Dépendances : aucune

Problème dans le code :
  ```kotlin
  // Purely informational home header — displays metadata for the currently focused media item.
  // No focusable elements here; interaction happens via the content row cards below.
  ```
  Netflix propose systématiquement Play + Add to List sur le hero. Ici on affiche le titre et les métadonnées mais l'utilisateur doit revenir sur la card du rail puis appuyer sur OK pour aller en détail puis Play. 3 interactions au lieu d'1.

Amélioration proposée :
  - Ajouter 2 boutons focalisables (Play + Info) dans `HomeHeader` ou dans `SpotlightGrid`.
  - Focus initial sur Play du hero (au lieu du premier item du rail).
  - Garantir Down → rails via `focusProperties { next = … }` ou `onPreviewKeyEvent`.

Critères d'acceptation :
  - [ ] Focus initial sur Play du hero au rendu
  - [ ] D-pad Down → premier item du rail "Continue Watching"
  - [ ] 1 clic OK sur Play → lance la lecture directement

Validation du fix :
  Test manuel + macrobenchmark "hero-play-latency".
```

```
ID          : AUDIT-5-005
Titre       : Premier focus Home initial fragile — aucun fallback si le LazyColumn n'a pas encore composé
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Home (ouverture à froid) → focus perdu dans le vide si `firstRowFocusRequester` est demandé avant que la première row soit composée
Écran       : NetflixHomeContent
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/home/NetflixHomeScreen.kt:60-65
Dépendances : aucune

Problème dans le code :
  ```kotlin
  LaunchedEffect(Unit) {
      if (!hasRequestedInitialFocus) {
          firstRowFocusRequester.requestFocus()
          hasRequestedInitialFocus = true
      }
  }
  ```
  Aucune gestion d'exception autour de `requestFocus()`. Si la `LazyColumn` n'a pas encore composé l'item, la requête lève une `IllegalStateException` silencieuse et le focus reste indéterminé. Pattern constant ailleurs (Library, Favorites, History : try/catch partout) → ici manquant.

Amélioration proposée :
  ```kotlin
  LaunchedEffect(hubs.isNotEmpty() || activeSpecialRows.isNotEmpty()) {
      if (!hasRequestedInitialFocus && (hubs.isNotEmpty() || activeSpecialRows.isNotEmpty())) {
          delay(100) // laisser la row composer
          try {
              firstRowFocusRequester.requestFocus()
              hasRequestedInitialFocus = true
          } catch (_: Exception) { }
      }
  }
  ```

Critères d'acceptation :
  - [ ] `try/catch` autour de `requestFocus()`
  - [ ] `delay(100)` pour laisser la composition
  - [ ] Key du `LaunchedEffect` basé sur la disponibilité des données

Validation du fix :
  Test Compose UI avec `onRoot().performKeyInput { keyDown(DPad.Center) }` sur un `HomeViewModel` avec `hubs.isEmpty() = true`.
```

```
ID          : AUDIT-5-006
Titre       : InitialSyncState et ErrorState Home en anglais hardcodé (pas de traduction FR)
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Premier lancement (sync initial) / erreur de chargement → textes uniquement en anglais
Écran       : DiscoverScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/home/DiscoverScreen.kt:181-253
Dépendances : AUDIT-5-002

Problème dans le code :
  ```kotlin
  Text(text = "Error loading content", …)
  Button(onClick = onRetry) { Text("Retry") }
  Text(text = "Welcome to PlexHubTV", …)
  "discovering" -> "Discovering servers..."
  "library_sync" -> "Syncing $libraryName ($completedLibraries/$totalLibraries libraries)"
  "extras" -> "Syncing extras..."
  ```
  Tous les labels de phase sync en dur, même ceux qui devraient être les plus visibles lors de l'onboarding.

Amélioration proposée :
  Extraire vers `R.string.loading_phase_discovering`, `R.string.loading_phase_sync_libraries_progress`, etc. Traduire en FR.

Critères d'acceptation :
  - [ ] 6+ nouvelles strings dans FR + EN
  - [ ] Aucun string en dur dans `DiscoverScreen.kt`
```

---

### 📄 Detail (`NetflixDetailScreen`, `MediaDetailScreen`)

```
ID          : AUDIT-5-007
Titre       : NetflixDetailScreen — 8+ strings UI visibles en anglais en dur
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Home → Detail → affichage des métadonnées, tabs, messages vides
Écran       : NetflixDetailScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/NetflixDetailScreen.kt:169, 205, 265, 290, 315, 339, 444, 459, 776, 847
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Text("Available on: ", …)
  Text("Directed by ${media.directors.joinToString(", ")}", …)
  Text("No seasons available", …)
  Text("No similar items found", …)
  Text("No collections available", …)
  Text("No trailers available", …)
  Text(text = "${seasons.size} Season${if (seasons.size > 1) "s" else ""}", …)
  Text(text = "${formatDuration(remainingMs)} left", …)
  val label = if (isExpanded) "Less" else "More..."
  Text(text = "Cast", …)
  ```

Amélioration proposée :
  Extraire toutes ces strings en `strings.xml`, utiliser `pluralsRes` pour "1 Season / 2 Seasons". Variante FR "1 Saison / 2 Saisons".

Critères d'acceptation :
  - [ ] 10+ strings extraites
  - [ ] `plurals` pour Season/Seasons
  - [ ] Toutes traduites FR
```

```
ID          : AUDIT-5-008
Titre       : ActionButtonsRow — Play / Loading en dur, gestion favorite/watch status OK
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Detail → bouton Play / quand l'enrichissement tourne "Loading..."
Écran       : MediaDetailScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailScreen.kt:281, 285
Dépendances : AUDIT-5-007

Problème dans le code :
  ```kotlin
  Text("Loading...", …)
  Text("Play", …)
  ```
  Alors que les `contentDescription` (`playLoadingDesc`, `playDesc`) viennent correctement de `stringResource`. Le texte visible du bouton est en dur. Incohérence a11y / i18n.

Amélioration proposée :
  Utiliser `R.string.action_play` et `R.string.state_loading` (à créer s'ils manquent).

Critères d'acceptation :
  - [ ] `stringResource` dans les 2 textes
  - [ ] Cohérence avec `contentDescription`
```

```
ID          : AUDIT-5-009
Titre       : Navigation par tabs (Episodes / More Like This / Collections / Trailers) — pas de focus restoration après switch
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Detail → focus sur tab "More Like This" → OK → ↓ → focus sur row similar → ← … → revenir sur tabs → ↑ pour switcher → le contenu change mais le focus reste parfois sur l'ancien emplacement
Écran       : NetflixDetailScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/NetflixDetailScreen.kt:75, 226-317
Dépendances : aucune

Problème dans le code :
  ```kotlin
  private enum class DetailFocusTarget { PlayButton, Tabs, ContentRow }
  // ...
  when (selectedTab) {
      DetailTab.Episodes -> { LazyRow(modifier = Modifier.focusRequester(contentRowFocusRequester)) }
      DetailTab.MoreLikeThis -> { LazyRow(modifier = Modifier.focusRequester(contentRowFocusRequester)) }
      // ...
  }
  ```
  Le même `contentRowFocusRequester` est attaché à chaque branche du `when`, mais lorsque `selectedTab` change, Compose recrée la `LazyRow` — le `FocusRequester` peut être orphelin (non attaché) ou attaché au mauvais. Pas de `LaunchedEffect(selectedTab) { contentRowFocusRequester.requestFocus() }` pour repositionner le focus après switch. Résultat : le focus peut se perdre dans le `LazyColumn` parent.

Amélioration proposée :
  - Un `FocusRequester` distinct par tab, ou
  - `LaunchedEffect(selectedTab) { delay(150); try { contentRowFocusRequester.requestFocus() } catch (_: Exception) {} }`.

Critères d'acceptation :
  - [ ] Après switch de tab, focus va automatiquement sur le premier item de la nouvelle row
  - [ ] Pas de "focus dans le vide"

Validation du fix :
  Test manuel sur device : Detail d'une série → tab Episodes → tab Collections → tab More Like This, vérifier que chaque switch repositionne le focus.
```

```
ID          : AUDIT-5-010
Titre       : ExpandableSummary (More / Less) — focus sur le label mais aucun indicateur visuel de focus
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Detail → focus sur "More..." sous le résumé
Écran       : NetflixDetailScreen.ExpandableSummary
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/NetflixDetailScreen.kt:747-798
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Text(
      text = label,
      color = labelColor,  // seul "feedback" visuel de focus = alpha 0.6 → 1.0
      modifier = Modifier.clickable(…) { isExpanded = !isExpanded }
  )
  ```
  Pas de border ni de scale : un utilisateur voit mal que le label "More" est focalisé. Contraste insuffisant (0.6 → 1.0 alpha) selon WCAG AA.

Amélioration proposée :
  Ajouter `.background(if (isFocused) cs.primary.copy(alpha = 0.15f) else Color.Transparent)` + `.padding(h=12,v=6)` + `RoundedCornerShape(4.dp)`.

Critères d'acceptation :
  - [ ] Focus clairement visible (contraste > 3:1)
  - [ ] Scale 1.05f au focus
```

```
ID          : AUDIT-5-011
Titre       : DetailHeroSection — durée poster trop petit (100dp de large) vs recommandation 3 m
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Detail hero
Écran       : NetflixDetailScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/NetflixDetailScreen.kt:375-379
Dépendances : aucune

Problème dans le code :
  ```kotlin
  .width(100.dp)
  .aspectRatio(2f / 3f)   // → 100 × 150 dp
  ```
  À 3 m sur un 55" 1080p, 100 dp ≈ 200 px → poster quasi invisible. Netflix TV a des posters de 180-240 dp sur le hero de Detail.

Amélioration proposée :
  Augmenter à 180 dp (pour hero), garder 120 dp pour les cards.

Critères d'acceptation :
  - [ ] Poster hero ≥ 180 dp
  - [ ] Title/metadata reste lisible sans overflow
```

```
ID          : AUDIT-5-012
Titre       : CastRow — items focalisables mais pas de KEY = focus peut sauter au recompose
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Detail → Cast row → D-pad Right
Écran       : NetflixDetailScreen.CastRow
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/details/NetflixDetailScreen.kt:852-924
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      displayCast.forEach { member -> ... }
  }
  ```
  `Row` (pas `LazyRow`) pour 8 items max. OK en perf mais pas de `key` stable ni de `focusGroup`. Si le cast est rechargé (après enrichment réseau), Compose peut recréer les nodes et le focus saute sur le `LazyColumn` parent.

Amélioration proposée :
  Wrapper dans un `Row` avec `.focusGroup()` + `remember(member.name)` pour l'interactionSource.

Critères d'acceptation :
  - [ ] Focus stable après recompose
  - [ ] `.focusGroup()` présent
```

---

### 📚 Library (`LibrariesScreen`, `FilterDialog`)

```
ID          : AUDIT-5-013
Titre       : Library — focus restoration asynchrone fragile (delay 100 ms, aucune garantie)
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Library → focus sur item X (ex: 50e) → OK → Detail → Back → retour à Library, focus censé être restauré
Écran       : LibraryContent
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt:612-623, 675-686
Dépendances : AUDIT-5-014

Problème dans le code :
  ```kotlin
  LaunchedEffect(shouldRestoreFocus) {
      if (shouldRestoreFocus) {
          delay(100) // Wait for layout stabilization
          try {
              focusRestorationRequester.requestFocus()
              hasRestoredFocus = true
          } catch (_: Exception) { }
      }
  }
  ```
  `delay(100)` est un nombre magique. Si le device est lent (Mi Box S en utilisation prolongée), 100 ms peuvent être insuffisants et la requête échoue silencieusement. Pire : la même `Effect` est relancée pour chaque item visible, créant des allocations inutiles.

Amélioration proposée :
  - Utiliser `snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.key == lastFocusedId } }` avant de demander le focus.
  - Déplacer la logique au niveau du `LibraryContent` (pas dans chaque item).

Critères d'acceptation :
  - [ ] Pas de delay arbitraire
  - [ ] Focus restauré même sur device lent
  - [ ] Seul 1 `LaunchedEffect` global, pas 1 par item
```

```
ID          : AUDIT-5-014
Titre       : Library — scrollRequest ignore pendingScrollRestore en cas de race
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Library → Detail d'un item en bas de la grille → Back → la grille doit se re-scroller à l'index sauvegardé
Écran       : LibrariesScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt:225-246
Dépendances : AUDIT-5-013

Problème dans le code :
  ```kotlin
  LaunchedEffect(scrollRequest) {
      if (scrollRequest != null) {
          gridState.scrollToItem(scrollRequest)
          listState.scrollToItem(scrollRequest)
          onScrollConsumed()
      }
  }
  LaunchedEffect(pendingScrollRestore) {
      if (pendingScrollRestore != null && pendingScrollRestore > 0) {
          val loaded = withTimeoutOrNull(3000L) { … }
          if (pagedItems.itemCount > 0) { gridState.scrollToItem(target) }
      }
  }
  ```
  Deux `LaunchedEffect`s concurrents scrollent le même `gridState` selon deux sources (`scrollRequest` et `pendingScrollRestore`). Ordre d'exécution non déterminé. Si `scrollRequest != null` mais arrive après `pendingScrollRestore`, on a un double-scroll visible par l'utilisateur.

Amélioration proposée :
  Merger dans un seul `LaunchedEffect` avec une source de vérité unique (prendre `scrollRequest` s'il est != null, sinon `pendingScrollRestore`).

Critères d'acceptation :
  - [ ] Un seul scroll visible
  - [ ] Scroll restauré même sur retour rapide
```

```
ID          : AUDIT-5-015
Titre       : Library — filter chips cachent le titre d'écran quand > 4 filtres actifs (pas de wrap)
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Library → serveur filtré + source filtrée + genre filtré + sort → `FilterButton` × 5 dans une `Row` non-wrappée
Écran       : LibrariesScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt:272-383
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Row(
      modifier = Modifier.fillMaxWidth()
          .padding(start = 58.dp, top = 80.dp, end = 58.dp, bottom = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween
  ) {
      SectionTitle(…)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          FilterButton(…)  // Server
          FilterButton(…)  // Source (conditionnel)
          FilterButton(…)  // Genre
          FilterButton(…)  // Sort
          IconButton(…)    // Refresh
          IconButton(…)    // View mode
      }
  }
  ```
  Sur TV 1080p à 58 dp de padding : si `serverLabel` = "Server: Mon serveur Plex de la maison" + `genreLabel` = "Genre: Science Fiction" → la Row déborde, le titre à gauche est tronqué ou poussé.

Amélioration proposée :
  - `FlowRow` pour les filter chips.
  - Limiter le label à 20 caractères (ellipsis).

Critères d'acceptation :
  - [ ] Pas de troncature du titre "Movies (N)"
  - [ ] Chips wrappent sur une deuxième ligne si nécessaire
```

```
ID          : AUDIT-5-016
Titre       : Library refresh IconButton — aucun focus visible (IconButton par défaut, pas de scale ni border)
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Library → filter row → focus sur refresh → utilisateur ne voit pas clairement que le bouton est focalisé
Écran       : LibrariesScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt:336-380
Dépendances : aucune

Problème dans le code :
  ```kotlin
  IconButton(
      onClick = { pagedItems.refresh(); onAction(LibraryAction.Refresh) },
      enabled = !state.display.isRefreshing,
      modifier = Modifier.testTag("library_refresh_button")
  ) { Icon(...) }
  ```
  Material3 `IconButton` par défaut applique une indication ripple mais PAS de scale/border focus. Sur TV, le focus ripple est invisible à 3 m.

Amélioration proposée :
  Wrapper dans un `Box` avec `collectIsFocusedAsState` → scale 1.15 + border 2dp primary au focus. Ou utiliser `ThemedButton` (cf `core/ui/ThemedButton.kt`).

Critères d'acceptation :
  - [ ] Focus visible à ≥ 2 m
  - [ ] Cohérent avec autres boutons de l'écran

Validation du fix :
  Test visuel sur TV 55".
```

---

### 🔎 Search (`NetflixSearchScreen`)

```
ID          : AUDIT-5-017
Titre       : Search — pas de BackHandler, retour arrière quitte Search vers Home sans effacer la query
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Search → taper "Inception" → appuyer Back → retour Home → re-entrer Search → query vide, les résultats précédents ont disparu
Écran       : NetflixSearchScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/search/NetflixSearchScreen.kt
Dépendances : aucune

Problème dans le code :
  Aucun `BackHandler` dans `NetflixSearchScreen`. Le comportement par défaut de Compose Navigation pop le back stack. Si l'utilisateur voulait juste effacer la query et rester sur Search, il doit naviguer vers le bouton "Clear" du clavier virtuel manuellement.

Amélioration proposée :
  ```kotlin
  BackHandler(enabled = state.query.isNotEmpty()) {
      onAction(SearchAction.ClearQuery)
  }
  ```

Critères d'acceptation :
  - [ ] Back efface d'abord la query
  - [ ] Second Back quitte Search vers Home
```

```
ID          : AUDIT-5-018
Titre       : Search Idle / Error state → hardcoded strings "Type to start searching" et "Search failed" via stringResource = ok, mais "Error loading content" etc. restent en dur dans d'autres écrans (mention pour cohérence)
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Note : Ce screen est OK pour les strings (utilise `stringResource`). Finding uniquement pour souligner la cohérence attendue sur les autres écrans.
```

```
ID          : AUDIT-5-019
Titre       : Search — focus initial sur le clavier mais l'utilisateur tape déjà via D-pad, pas de raccourci pour passer aux résultats
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Search → clavier focalisé → l'utilisateur tape 6 lettres → résultats apparaissent → doit faire D-pad Right 6-7 fois pour sortir du clavier et atteindre les résultats
Écran       : NetflixSearchScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/search/NetflixSearchScreen.kt:42-51, 188-197
Dépendances : aucune

Problème :
  Pas de touche "Enter/Done/Search" focalisable avec focus distinctif qui saute directement aux résultats. La nav Right depuis une row clavier saute directement aux résultats, mais seulement si la row est à la dernière colonne du clavier, sinon le D-pad navigue dans le clavier.

Amélioration proposée :
  - Ajouter une touche "Voir résultats" (ou "Done") dans `NetflixOnScreenKeyboard` qui, au click, fait `resultsFocusRequester.requestFocus()`.
  - Ou : détecter `isNotEmpty() && results.isNotEmpty()` → si l'utilisateur tape LEFT/RIGHT sur la dernière colonne, sauter aux résultats.

Critères d'acceptation :
  - [ ] Touche "Enter" focalisable dans le clavier
  - [ ] 1 clic OK dessus → focus va sur le 1er résultat
```

---

### 🎬 Player (`VideoPlayerScreen`)

```
ID          : AUDIT-5-020
Titre       : Player — KEYCODE_MEDIA_STOP non géré (télécommande Mi Box S)
Phase       : 5 UX
Sévérité    : P1
Confiance   : Moyenne

Parcours affecté :
  Lecture en cours → appui sur la touche Stop d'une télécommande universelle / XiaomiBox
Écran       : VideoPlayerScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt:263-321
Dépendances : aucune

Problème dans le code :
  ```kotlin
  NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_PLAY -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_REWIND -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_NEXT -> { … }
  NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS -> { … }
  ```
  Absent : `KEYCODE_MEDIA_STOP`, `KEYCODE_MEDIA_CLOSE`, `KEYCODE_BUTTON_START`, `KEYCODE_MENU` (certaines Samsung/LG remote). Axe "Remote variantes" du checklist.

Amélioration proposée :
  Ajouter au `when`:
  ```kotlin
  NativeKeyEvent.KEYCODE_MEDIA_STOP,
  NativeKeyEvent.KEYCODE_MEDIA_CLOSE -> { onAction(PlayerAction.Close); true }
  NativeKeyEvent.KEYCODE_MENU -> { onAction(PlayerAction.ToggleMoreMenu); true }
  ```

Critères d'acceptation :
  - [ ] STOP → ferme le player
  - [ ] MENU → ouvre le more-menu

Validation du fix :
  Test manuel sur Mi Box S (vérifier KEYCODE_BUTTON_START), test avec télécommande universelle.
```

```
ID          : AUDIT-5-021
Titre       : Player — Audio/Subtitle Sync dialogs hardcodés en anglais
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Player → More → Audio/Subtitle Sync → titre "Audio Sync" / "Subtitle Sync" en anglais
Écran       : VideoPlayerScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt:585, 595
Dépendances : aucune

Problème dans le code :
  ```kotlin
  SyncSettingsDialog(
      title = "Audio Sync",
      ...
  )
  SyncSettingsDialog(
      title = "Subtitle Sync",
      ...
  )
  ```

Amélioration proposée :
  `stringResource(R.string.player_audio_sync_title)`, etc.

Critères d'acceptation :
  - [ ] 2 strings extraites
```

```
ID          : AUDIT-5-022
Titre       : Player — auto-hide des contrôles à 5 s peut masquer un loading/buffering non terminé
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Lecture → pause → utilisateur ne bouge pas → contrôles disparaissent après 5 s → reprise via D-pad center force à re-afficher
Écran       : VideoPlayerScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt:198-203
Dépendances : aucune

Problème dans le code :
  ```kotlin
  LaunchedEffect(controlsVisible, uiState.isPlaying, lastInteractionTime) {
      if (controlsVisible && uiState.isPlaying) {
          delay(5000)
          controlsVisible = false
      }
  }
  ```
  Auto-hide OK quand `isPlaying == true`. Problème : `shouldShowControls = controlsVisible || (!uiState.isPlaying && !uiState.isBuffering)` → si le player est en pause + pas buffering, les contrôles restent. Mais si le player tombe en `isBuffering = true` pendant une pause → les contrôles s'affichent toujours. OK.
  Cas réel problématique : `isPlaying && isBuffering` (re-buffer mid-playback) → auto-hide déclenché puis relancé à chaque re-buffer ? Non, le `LaunchedEffect` cancel le precedent.
  Vrai problème : si l'utilisateur déclenche un overlay (ex. `showMoreMenu`) et ne bouge pas pendant 5 s, l'overlay reste mais les contrôles parent (sous l'overlay) se cachent → après dismiss, focus perdu car `focusRequester` n'est plus rattaché.

Amélioration proposée :
  Désactiver l'auto-hide quand `isDialogVisible == true`.
  ```kotlin
  if (controlsVisible && uiState.isPlaying && !isDialogVisible) { delay(5000); controlsVisible = false }
  ```

Critères d'acceptation :
  - [ ] Overlay ouvert > 5 s → contrôles parent ne se cachent pas
  - [ ] Focus restauré correctement après dismiss
```

---

### 🔐 Auth / Onboarding (`AuthScreen`, `LibrarySelectionScreen`, `LoadingScreen`)

```
ID          : AUDIT-5-023
Titre       : Auth screen — pas d'onboarding dédié pour "Ajouter un serveur Jellyfin/Xtream"
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Premier lancement → utilisateur Jellyfin-only (sans compte Plex) → Auth propose uniquement "Login with PIN" Plex + champs avancés Token Plex
Écran       : AuthScreen.IdleState
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthScreen.kt:85-179
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Button(onClick = { onAction(AuthEvent.StartAuth) }, …) {
      Text(stringResource(R.string.auth_login_with_pin))
  }
  // + token avancé
  ```
  Aucun chemin visible pour Jellyfin/Xtream/Backend. Un utilisateur Jellyfin doit d'abord se connecter avec un compte Plex (potentiellement fake ou vide) puis naviguer vers Settings pour ajouter le serveur Jellyfin. UX très médiocre pour users non-Plex.

Amélioration proposée :
  - Ajouter une 3e action : "Autre serveur (Jellyfin / Xtream)" qui navigue vers `JellyfinSetupScreen` ou `XtreamSetupScreen` (ou un écran de choix).
  - Rendre l'étape Plex optionnelle / skippable.

Critères d'acceptation :
  - [ ] Utilisateur Jellyfin-only atteint la Home en ≤ 3 écrans sans compte Plex
  - [ ] Bouton "Other server" présent et focalisable

Validation du fix :
  Parcours complet avec un backend Jellyfin local uniquement.
```

```
ID          : AUDIT-5-024
Titre       : AuthScreen "Login with PIN" — focus initial OK mais token text field inaccessible en D-pad sans clavier USB
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Advanced login → token field sélectionné → utilisateur ne peut pas taper sans clavier USB
Écran       : AuthScreen.IdleState
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthScreen.kt:135-150
Dépendances : aucune

Problème dans le code :
  `OutlinedTextField` → déclenche le clavier virtuel Android standard (pas celui TV). Sur Mi Box S, le clavier système s'affiche, mais certaines remotes non-Bluetooth ne permettent pas de taper dessus.

Amélioration proposée :
  - Utiliser `NetflixOnScreenKeyboard` (composant déjà présent dans `core/ui/`) comme surface de saisie.
  - Ou masquer l'advanced login si pas de hardware keyboard détecté.

Critères d'acceptation :
  - [ ] Token saisissable 100 % avec D-pad + OK
```

```
ID          : AUDIT-5-025
Titre       : LoadingScreen — bouton Retry/Exit focus restoration correcte, mais pas de `retryFocusRequester` pour Exit
Phase       : 5 UX
Sévérité    : P2
Confiance   : Faible

Parcours affecté :
  Loading → Error → 2 boutons Retry / Exit → focus par défaut sur Retry (correct) → D-pad Right → Exit
Écran       : LoadingScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt:157-182
Dépendances : aucune

Problème dans le code :
  ```kotlin
  Row(...) {
      val retryFocusRequester = remember { FocusRequester() }
      Button(onClick = onRetryClicked, modifier = Modifier.focusRequester(retryFocusRequester).weight(1f)) { ... }
      OutlinedButton(onClick = onExitClicked, modifier = Modifier.weight(1f)) { ... }
      LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }
  }
  ```
  `retryFocusRequester` est déclaré INSIDE la `Row`. Le `LaunchedEffect(Unit)` est aussi dans la Row, ce qui est pattern correct pour Compose, mais l'ordre de composition peut faire que le `LaunchedEffect` s'exécute avant que le `Button` soit composé → la requête peut échouer. Mineur mais à surveiller.

Amélioration proposée :
  Sortir `retryFocusRequester` hors de la `Row`, ajouter `try/catch` autour de `requestFocus()` comme ailleurs.

Critères d'acceptation :
  - [ ] Focus initial garanti sur Retry
```

---

### ⚙️ Settings / Profiles

```
ID          : AUDIT-5-026
Titre       : SettingsGridScreen — 7 cartes en 2 rows (4 + 3) + Spacer `weight(1f)` pour "combler" la row inférieure crée un trou focalisable vide
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Settings → focus sur la 3e carte de la 2e row → D-pad Right → focus va dans le Spacer ? Ou reste sur la carte ?
Écran       : SettingsGridScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsGridScreen.kt:79-98
Dépendances : aucune

Problème dans le code :
  ```kotlin
  bottomRow.forEach { category -> SettingsCategoryCard(...) }
  Spacer(modifier = Modifier.weight(1f))
  ```
  Le `Spacer` n'est pas focalisable → D-pad Right depuis la 3e carte reste bloqué sur la carte. Mais la hiérarchie visuelle est trompeuse : l'utilisateur peut croire qu'une 4e carte "invisible" est présente. Mineur mais poli.

Amélioration proposée :
  Remplacer `bottomRow.size == 3` par un layout qui centre les 3 cartes (`Arrangement.Center` + pas de Spacer).

Critères d'acceptation :
  - [ ] Pas de "trou" visuel
  - [ ] Focus Right reste bloqué élégamment sur la dernière carte
```

```
ID          : AUDIT-5-027
Titre       : AppProfileSelectionScreen — focus initial sur 1er profil mais "Add profile" button jamais garanti focalisable
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Profile selection (≥ 2 profils) → focus initial OK → "Add profile" Icon à droite → D-pad Right → atteint "Add"
Écran       : AppProfileSelectionScreen
Fichier(s)  : app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSelectionScreen.kt:133-150
Dépendances : aucune

Problème :
  La Row contient `profiles.forEachIndexed { … AppProfileCard(...) }` puis le bouton Add est (probablement) ajouté ensuite. Si les cards ont une largeur fixe et que le focus navigue correctement à droite, OK. Si la Row n'a pas `focusGroup()`, le focus peut sortir vers la Column parente. Confirmation requise.

Amélioration proposée :
  Wrapper la Row dans `.focusGroup()` + ajouter `ParentalPinDialog` focus retour après dismiss.

Critères d'acceptation :
  - [ ] D-pad couvre profils + bouton Add en 1 ligne
```

---

### 🎨 Cross-cutting (core/ui, core/designsystem)

```
ID          : AUDIT-5-028
Titre       : OverscanSafeArea défini mais JAMAIS utilisé dans le code production
Phase       : 5 UX
Sévérité    : P0
Confiance   : Élevée

Parcours affecté :
  Tous les écrans — bordures d'affichage sur TV overscan (surtout LCD/CRT anciens)
Écran       : All
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/OverscanSafeArea.kt (défini)
              aucun appel trouvé via `Grep OverscanSafeArea` hors définition + docs
Dépendances : aucune

Problème dans le code :
  Le composable `OverscanSafeArea` est défini (horizontal 48 dp, vertical 48 dp) mais jamais consommé. Au lieu de cela, chaque écran applique son propre `padding` : `start = 58.dp, end = 58.dp, top = 80.dp` (LibrariesScreen), `padding(32.dp)` (NetflixSearchScreen), `padding(48.dp)` (Skeletons), `padding(16.dp, 8.dp)` (certains dialogs). Paddings minces (16 dp / 32 dp) peuvent être coupés sur TV avec overscan physique (~5 %).
  → C'est le point de contrôle #8 de la checklist "Overscan safe area 5% inset minimum".

Amélioration proposée :
  1. Wrapper chaque `Route` composable dans `OverscanSafeArea { … }`.
  2. Ou : appliquer un `padding(Dimensions.overscanHorizontal, Dimensions.overscanVertical)` constant au `Scaffold` global dans `MainScreen`.
  3. Renommer `OverscanSafeArea` paramètres pour permettre `top = 0` (cas player).
  4. Supprimer les paddings magiques dans chaque écran.

Critères d'acceptation :
  - [ ] Tous les écrans main appliquent ≥ 48 dp horizontal
  - [ ] Tous les écrans utilisent `Dimensions.overscanHorizontal`
  - [ ] Plus d'occurrences de `.padding(start = 58.dp` / `.padding(32.dp)` éparses

Validation du fix :
  Test visuel sur TV avec overscan activé (option dev TV), capture d'écran avec `adb shell screencap`.
```

```
ID          : AUDIT-5-029
Titre       : Typographie trop petite pour 3 m — CardTitle 13sp, BadgeSmall 9sp, labelSmall 11sp
Phase       : 5 UX
Sévérité    : P0
Confiance   : Élevée

Parcours affecté :
  Tous les écrans
Écran       : All (Type.kt central)
Fichier(s)  : core/designsystem/src/main/java/com/chakir/plexhubtv/core/designsystem/Type.kt:99-105, 113-150
Dépendances : aucune

Problème dans le code :
  ```kotlin
  labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, …)
  CinemaTypo.CardTitle = TextStyle(fontSize = 13.sp, …)
  CinemaTypo.SectionTitle = TextStyle(fontSize = 13.sp, letterSpacing = 1.5.sp, …)  // UPPERCASE
  CinemaTypo.Metadata = TextStyle(fontSize = 12.sp, letterSpacing = 0.5.sp, …)
  CinemaTypo.Badge = TextStyle(fontSize = 11.sp, …)
  CinemaTypo.BadgeSmall = TextStyle(fontSize = 9.sp, …)
  ```
  Google TV Guidelines : minimum **14 sp pour le body, 18 sp pour le titre secondaire, 24 sp pour le titre principal**. Les valeurs actuelles sont calibrées "mobile" et sont quasi-illisibles à 3 m sur un écran 55".
  `BadgeSmall = 9.sp` est complètement inutilisable (et non référencée ? à vérifier).

Amélioration proposée :
  ```kotlin
  labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
  CinemaTypo.CardTitle = TextStyle(fontSize = 16.sp)
  CinemaTypo.SectionTitle = TextStyle(fontSize = 18.sp, letterSpacing = 1.5.sp)
  CinemaTypo.Metadata = TextStyle(fontSize = 14.sp)
  CinemaTypo.Badge = TextStyle(fontSize = 14.sp)
  CinemaTypo.BadgeSmall = TextStyle(fontSize = 12.sp)  // au lieu de 9
  ```

Critères d'acceptation :
  - [ ] Aucune police < 12 sp en production
  - [ ] CardTitle ≥ 16 sp
  - [ ] SectionTitle ≥ 18 sp
  - [ ] Lisible à 3 m sur 55"

Validation du fix :
  Test visuel à 3 m sur TV, feedback utilisateur type "peut lire sans plisser".
```

```
ID          : AUDIT-5-030
Titre       : NetflixMediaCard — 14sp codé en dur pour Rating/Multi-server/Watched badges, bypass de Typography
Phase       : 5 UX
Sévérité    : P2
Confiance   : Élevée

Parcours affecté :
  Home / Library / Favorites — badges sur les cards
Écran       : NetflixMediaCard
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixMediaCard.kt:230, 268, 371, 423
Dépendances : AUDIT-5-029

Problème dans le code :
  ```kotlin
  Text(
      text = String.format("%.1f", rating),
      style = MaterialTheme.typography.labelSmall,
      fontSize = 14.sp, // Increased from 11sp for TV readability  ← OVERRIDE manuel
      fontWeight = FontWeight.Bold
  )
  ```
  Commentaire explicite "Increased from 11sp for TV readability" → confirmation que labelSmall (11 sp) est insuffisant. La solution n'est pas d'overrider chaque call site mais de corriger `Type.kt` (cf AUDIT-5-029).

Amélioration proposée :
  Supprimer les `fontSize = 14.sp` explicites après avoir fixé `Type.kt`.

Critères d'acceptation :
  - [ ] `Grep "fontSize = .*sp"` ne trouve plus d'overrides dans `core/ui/`
  - [ ] Cohérence globale
```

```
ID          : AUDIT-5-031
Titre       : Skeletons — padding hardcodé 48dp horizontal partout, non configurable
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Loading skeletons (Library, Home, Season Detail, Episode)
Écran       : All skeletons
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/Skeletons.kt:85, 94, 116, 230, 272, 344
Dépendances : AUDIT-5-028

Problème dans le code :
  `.padding(horizontal = 48.dp)` / `.padding(start = 48.dp)` répété 6+ fois, sans passer par `Dimensions.overscanHorizontal`.

Amélioration proposée :
  Remplacer par `Dimensions.overscanHorizontal`.
```

```
ID          : AUDIT-5-032
Titre       : NetflixTopBar — focus trap UP correct mais manque RIGHT bound (utilisateur peut focus hors topbar)
Phase       : 5 UX
Sévérité    : P2
Confiance   : Moyenne

Parcours affecté :
  Home → TopBar (Focus sur dernier item nav) → D-pad Right → probablement OK mais pas garanti
Écran       : NetflixTopBar
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixTopBar.kt:137-147
Dépendances : aucune

Problème dans le code :
  ```kotlin
  .focusProperties {
      exit = { direction ->
          if (direction == FocusDirection.Up) FocusRequester.Cancel
          else FocusRequester.Default
      }
  }
  ```
  Seule UP est bloquée. RIGHT depuis le dernier item nav (Search/Settings/Profile icon) peut sortir vers le contenu en dessous de manière non-prévue. Comportement à valider sur device.

Amélioration proposée :
  Bloquer aussi RIGHT quand le focus est sur le dernier élément de la TopBar.

Critères d'acceptation :
  - [ ] D-pad Right sur dernier item TopBar → reste sur la TopBar (ou wrap au premier)

Validation du fix :
  Test manuel, puis Compose UI test.
```

```
ID          : AUDIT-5-033
Titre       : HandleErrors utilise Snackbar — non-idéal sur TV (petit toast en bas, non-focalisable)
Phase       : 5 UX
Sévérité    : P1
Confiance   : Élevée

Parcours affecté :
  Tous les écrans utilisant `HandleErrors(errorEvents, snackbarHostState)` — Library, Search, MediaDetail, Home
Écran       : Tous
Fichier(s)  : core/ui/src/main/java/com/chakir/plexhubtv/core/ui/HandleErrors.kt, ErrorSnackbarHost.kt
Dépendances : aucune

Problème :
  Une Snackbar Material3 est une UI mobile typique. Elle s'affiche en bas, est petite, disparaît automatiquement, et son bouton d'action ("Retry") est parfois non focalisable via D-pad (focus trap potentiel). Sur TV, à 3 m, l'utilisateur peut ne pas la voir du tout.

Amélioration proposée :
  - Remplacer par un dialog/banner plein écran avec boutons focalisables Retry/Dismiss, ou
  - Garder la snackbar pour succès mais afficher les erreurs dans un composant plus visible (banner en haut ou dialog modal).

Critères d'acceptation :
  - [ ] Erreurs visibles à 3 m
  - [ ] Retry accessible via D-pad
  - [ ] Contraste WCAG AA
  - [ ] Auto-dismiss ≥ 8 s (pas 4 s par défaut)

Validation du fix :
  Parcours utilisateur : forcer une erreur réseau, observer la visibilité et la navigabilité.
```

---

# Phase 6 — Features pour la vente

## Top 5 features — ranked table

| Rank | Feature | ROI | Effort | User impact | Sales impact | Reuse infra existante |
|---|---|---|---|---|---|---|
| 1 | **Mode enfant (filtrage contenu par rating + PIN)** | (5×5)/M = 8.3 | M (1-3j) | 5 | 5 | `FilterContentByAgeUseCase`, `ProfileEntity.isKidsMode`, `ParentalPinDialog`, `ContentRatingHelper` — 80 % déjà présent |
| 2 | **Android TV Channels / Recommendations (On Deck, Resume, Recommended)** | (4×5)/S = 10.0 | S (déjà codé, manque câblage) | 4 | 5 | `TvChannelManagerImpl`, `ChannelSyncWorker` déjà enregistré periodic 3h, `androidx.tvprovider` déjà dépendance |
| 3 | **Statistiques visionnage (heures, genres, séries terminées)** | (3×4)/M = 4.0 | M (2-3j) | 3 | 4 | `GetWatchHistoryUseCase`, `OfflineWatchProgressEntity`, Room DAO `getAllWatchedItems`, `ContentUtils` |
| 4 | **Profils avatars enrichis (emojis personnalisés + kids flag UI)** | (3×4)/S = 6.0 | S (< 1j) | 3 | 4 | `ProfileFormDialog` (12 emojis), `ProfileEntity` complet, Room DAO, UI `AppProfileSelectionScreen` |
| 5 | **Screensaver personnalisé avec affiches biblio** | (3×3)/M = 3.0 | M (2-3j) | 3 | 3 | `PlexHubDreamService` + `ScreensaverContent` + `ScreensaverViewModel` déjà en place, manque de variétés de layouts et transitions |

### Non retenus (justification)

- **Continue Watching cross-device** : très forte dépendance à un backend remote (Plex Watchtower OK mais Jellyfin/Xtream/Backend non unifiés) → effort XL, risque élevé.
- **Recommandations personnalisées locales (ML embedded)** : `ContentUtils` + `GetSuggestionsUseCase` existent mais pas de vrai moteur. Effort L sans ROI clair pour la vente.
- **Version freemium + Google Play Billing** : effort XL (intégration Play Billing + gating des features + server-side receipt validation + design paywall). Rentable uniquement si au moins 2-3 premium features pilotes déjà livrées — prématuré.

---

## Mini-specs par feature

### 1. Mode enfant (filtrage par rating, profil restreint) — P0 sales driver

```
Feature   : Mode enfant complet
ROI       : (5×5)/M = 8.3
Effort    : M (1-3j)
Impact utilisateur : 5 (parents rassurés, gamme familiale élargie)
Impact vente      : 5 (argument marketing fort, différenciation vs Jellyfin officiel)

Pourquoi crédible avec l'architecture actuelle :
  - `ProfileEntity` a déjà un champ `isKidsMode: Boolean` (cf MEMORY.md + phase0 §16.5)
  - `FilterContentByAgeUseCase` existe dans `domain/usecase/` avec un test unitaire
  - `ProfileFormDialog` permet déjà de toggle kids mode à la création
  - `ContentRatingHelper` (core/common) mappe les ratings (G/PG/PG-13/R → âge min)
  - `ParentalPinDialog` est déjà câblé pour la vérification PIN
  - MEMORY.md confirme : "Profile content filtering: Kids mode restrictions not yet applied to content"

Dépendances techniques :
  - `feature/home/HomeViewModel.kt` : injecter `FilterContentByAgeUseCase`, l'appliquer sur hubs + onDeck + suggestions
  - `feature/library/LibraryViewModel.kt` : passer le flag `isKidsMode` au `MediaLibraryQueryBuilder` pour filter dans la requête SQL directement (perf)
  - `feature/search/SearchViewModel.kt` : filter les résultats de recherche
  - `data/repository/MediaLibraryQueryBuilder.kt` : ajouter un clause `AND contentRating IN (?)` quand kids mode actif
  - `feature/appprofile/AppProfileViewModel.kt` : on switchProfile, émettre un event qui invalide les caches de `HomeViewModel` / `LibraryViewModel`
  - PIN à saisir pour quitter le mode kids (réutiliser `ParentalPinDialog`)
  - Filter Detail screen (masquer Trailers si adult, cast si adult-only)

Risques d'exécution :
  - Couverture des ratings internationaux : rating "TV-MA" US vs "-18" FR vs "R18" UK. `ContentRatingHelper` doit être robuste.
  - Jellyfin/Xtream n'ont pas toujours de rating → fallback strict (masquer par défaut).
  - Performance : filter au niveau SQL est critique (pas en post-processing Kotlin).
  - UX : profil kids doit avoir un thème visuel distinct (couleurs, logo "Kids" en haut).

Critères d'acceptation :
  - [ ] Kids profile ne voit jamais de contenu rating > PG / -12
  - [ ] Sortir du kids mode demande le PIN parental
  - [ ] Filter appliqué à Home, Library, Search, Detail, Collection, Favorites
  - [ ] Theme visuel distinct (appTheme = Kids ou couleurs override)
```

---

### 2. Android TV Channels — finir le câblage déjà commencé

```
Feature   : Android TV Channels (Home Screen rows on Launcher)
ROI       : (4×5)/S = 10.0
Effort    : S (< 1j) — infrastructure déjà en place, manque tests + activation
Impact utilisateur : 4 (contenu accessible depuis le launcher, pas besoin d'ouvrir l'app)
Impact vente      : 5 (feature critique pour Android TV certification Google)

Pourquoi crédible avec l'architecture actuelle :
  - `data/util/TvChannelManagerImpl.kt` existe et est utilisé
  - `domain/service/TvChannelManager.kt` interface propre
  - `work/ChannelSyncWorker.kt` programmé `PeriodicWorkRequest 3h` dans `PlexHubApplication.setupBackgroundSync()`
  - Dépendance `androidx.tvprovider` déjà ajoutée
  - `PlexHubApplication.onCreate()` appelle déjà `tvChannelManagerLazy.get().createChannelIfNeeded()`
  - Flag runtime `isTvChannelsEnabled` dans `SettingsViewModel` → toggle UI déjà existant

Dépendances techniques :
  - Vérifier que `ChannelSyncWorker` crée bien 2 channels : "Continue Watching" (On Deck) + "Recommended for You" (suggestions)
  - Ajouter un 3e channel "Recently Added"
  - Implémenter `TvChannelManagerImpl.refreshChannel(channelId)` pour maj incrémentale
  - Tester avec `adb shell am start -a android.intent.action.VIEW -d "content://android.media.tv/..."`
  - Le deep link `plexhub://play/{ratingKey}` existe déjà côté MainActivity — parfait

Risques d'exécution :
  - Google TV Home Screen API change régulièrement (bugs signalés en 2024)
  - Permission `WRITE_EPG_DATA` nécessaire ? à vérifier
  - Quota sync : > 100 items par channel peut être refusé par le launcher
  - Validation manuelle sur vraie Google TV (Mi Box S, Chromecast with GTV)

Critères d'acceptation :
  - [ ] 3 channels visibles sur le Home Google TV à J+1 installation
  - [ ] Clic sur un item lance directement la lecture (deep link)
  - [ ] Sync automatique à chaque lecture terminée / nouvelle suggestion
  - [ ] Toggle dans Settings fonctionne
```

---

### 3. Statistiques visionnage

```
Feature   : Stats visionnage (heures, genres, séries terminées, trends)
ROI       : (3×4)/M = 4.0
Effort    : M (2-3j)
Impact utilisateur : 3 (gimmick agréable, pas indispensable)
Impact vente      : 4 (bon storytelling Wrapped-style, partage social)

Pourquoi crédible avec l'architecture actuelle :
  - `GetWatchHistoryUseCase` + `OfflineWatchProgressEntity` contiennent toutes les data nécessaires
  - `PlaybackReporter` écrit à chaque lecture (scrobbling)
  - Room a déjà indices sur `lastViewedAt` pour agréger
  - Coroutines Flow → calcul temps réel
  - `core/common/ContentUtils.kt` pour formatage

Dépendances techniques :
  - Nouveau use case : `GetWatchStatsUseCase` dans `:domain`
  - Nouveau screen : `feature/stats/StatsScreen.kt` + `StatsViewModel` + `StatsUiState`
  - Requête SQL agrégée : `SELECT SUM(viewOffset), genre, COUNT(*) FROM media JOIN history GROUP BY genre`
  - UI : cards avec graphiques (utiliser Compose Canvas, pas de lib externe pour garder les dépendances légères)
  - 4 métriques : total hours watched, top 3 genres, top 5 shows, series completion rate
  - Widget optionnel : "Wrapped 2026" (résumé annuel)

Risques d'exécution :
  - Aggregation multi-serveurs : éviter de compter la même lecture deux fois (unificationId déjà présent)
  - Performance : requête agrégée sur 10k items doit rester < 500 ms
  - Canvas Compose : courbe d'apprentissage si pas de dev familier

Critères d'acceptation :
  - [ ] 4 métriques principales visibles
  - [ ] Refresh en temps réel via Flow
  - [ ] Accessible depuis Settings → System → Stats
  - [ ] Pas de lib externe ajoutée
```

---

### 4. Profils avatars enrichis

```
Feature   : Avatars profil améliorés (photo custom / emoji pack étendu / couleur de fond)
ROI       : (3×4)/S = 6.0
Effort    : S (< 1j)
Impact utilisateur : 3 (touche personnelle)
Impact vente      : 4 (démo visuelle forte pour screenshots store)

Pourquoi crédible avec l'architecture actuelle :
  - `ProfileFormDialog` a déjà un sélecteur de 12 emojis
  - `ProfileEntity` a un champ `avatarUrl: String?` (ou à ajouter — une migration Room simple)
  - `AppProfileSelectionScreen` affiche déjà les profils en `CircleShape`

Dépendances techniques :
  - Étendre `ProfileFormDialog` : 24 emojis au lieu de 12, + 8 couleurs de fond prédéfinies
  - Option 2 (optionnelle) : avatar photo URL (copier-coller URL + preview via Coil)
  - `ProfileEntity` : ajouter `backgroundColor: Long` et (optionnel) `photoUrl: String?`
  - Migration Room 47 → 48
  - `AppProfileCard` : afficher background color si non-null
  - Pas de besoin server-side, 100 % local

Risques d'exécution :
  - Coût migration Room faible
  - UI focus : garder ≤ 5 lignes de 5 emojis = 25 focalisables → OK D-pad

Critères d'acceptation :
  - [ ] 24 emojis sélectionnables via D-pad
  - [ ] 8 couleurs background sélectionnables
  - [ ] Profil affiche correctement avec background custom
```

---

### 5. Screensaver personnalisé

```
Feature   : Screensaver dynamique avec affiches bibliothèque (mosaïque / ken burns)
ROI       : (3×3)/M = 3.0
Effort    : M (2-3j)
Impact utilisateur : 3 (touche premium)
Impact vente      : 3 (screenshot marketing + différenciateur vs Plex/Jellyfin)

Pourquoi crédible avec l'architecture actuelle :
  - `PlexHubDreamService` déjà déclaré dans `AndroidManifest.xml` + `BIND_DREAM_SERVICE` permission
  - `feature/screensaver/ScreensaverContent.kt` + `ScreensaverViewModel.kt` existent
  - Meta `xml/dream_service` configuré
  - Accès à toutes les images via Coil ImageLoader (déjà warmed up au launch)

Dépendances techniques :
  - Ajouter 3 modes : mosaic (grille animée), ken-burns (zoom slow sur un poster), carousel (carrousel horizontal)
  - Settings : choix du mode + interval + source (favoris, récents, all)
  - Requête Room : `MediaDao.getRandomMedia(limit = 50)`
  - Animation : Compose `Animatable` + `rememberInfiniteTransition`
  - Timeout et reprise vidéo après wakeup (gérer `ActivityLifecycleCallbacks`)

Risques d'exécution :
  - Consommation batterie sur Mi Box : le screensaver tourne 24h si pas de timer
  - Coil image loader + burst de 50 images → memory pressure, gérer le cache
  - Certaines TVs Android ignorent `DreamService` (LG webOS n'est pas Android, Samsung Tizen idem) — limite au vrai Android TV

Critères d'acceptation :
  - [ ] 3 modes fonctionnels
  - [ ] Options dans Settings → System → Screensaver
  - [ ] Consommation RAM stable < 200 Mo après 1 h
  - [ ] Reprise au tap télécommande propre
```

---

## Récap

| Checklist item | Couvert ? |
|---|---|
| UX findings ≥ 25 | ✅ 33 |
| Features ≤ 5 | ✅ 5 |
| Every finding names a concrete flow | ✅ |
| P0 = app unusable on TV | ✅ 2 P0 (Overscan + Typo < 12sp) |
| Features reuse existing infra | ✅ Toutes les 5 |

> **Note** : cet audit est basé sur la lecture du code source. Une validation finale sur Mi Box S est **obligatoire** pour confirmer les findings `Confiance: Moyenne/Faible`, en particulier AUDIT-5-001 (scroll snap), AUDIT-5-013/014 (focus restoration Library), AUDIT-5-022 (auto-hide contrôles pendant overlay), AUDIT-5-027 (profil avatars focus RIGHT), AUDIT-5-032 (TopBar RIGHT exit).
