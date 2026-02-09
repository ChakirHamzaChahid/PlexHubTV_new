# PlexHubTV Netflix-like Audit Report

## Executive Summary

L'implémentation Netflix-like de PlexHubTV souffre de **3 problèmes fondamentaux** qui rendent l'application quasi-inutilisable :

1. **Utilisation de `LazyColumn`/`LazyRow` standard au lieu de `TvLazyColumn`/`TvLazyRow`** — cassant la navigation D-Pad et la restauration du focus
2. **Images chargées en taille originale (`Size.ORIGINAL`)** sur des cartes de 140dp — causant une saturation mémoire et des freezes
3. **`HomeUiState` sérialisé via `@Parcelize` dans `SavedStateHandle`** avec potentiellement des centaines de MediaItem — causant des `TransactionTooLargeException` et des lags massifs

Corrigés ces 3 points seuls, l'app devrait retrouver une utilisabilité correcte.

---

## 1. Audit de Conformité au Plan Netflix-like

### 1.1 Tableau Récapitulatif

| Section / Feature | Statut | Fichiers Concernés | Commentaire |
|---|---|---|---|
| **Navigation Top Bar** | ✅ Partiel | `NetflixTopBar.kt`, `MainScreen.kt` | Layout OK, mais pas de gestion focus Top Bar ↔ Content, pas de `FocusRequester` |
| **Home Hero Billboard** | ✅ Partiel | `NetflixHeroBillboard.kt` | Auto-rotation OK, gradients OK, mais hauteur 550dp (plan: 480dp), `Size.ORIGINAL` pour les images, boutons standard au lieu de TV Material |
| **Home Content Rows** | ❌ KO | `NetflixContentRow.kt`, `NetflixHomeScreen.kt` | **Utilise `LazyRow`/`LazyColumn` au lieu de `TvLazyRow`/`TvLazyColumn`**. Le plan exige explicitement TV components |
| **Media Cards** | ✅ Partiel | `NetflixMediaCard.kt` | Scale 1.05 (plan: 1.08), pas d'`AnimatedVisibility` pour le titre (plan l'exige), utilise `clickable`+`focusable` au lieu de TV API |
| **Detail Screen** | ✅ Partiel | `NetflixDetailScreen.kt`, `NetflixDetailTabs.kt` | Backdrop + gradient OK, onglets Episodes/MoreLikeThis OK, mais `LazyRow` imbriqué dans `LazyColumn` item = focus cassé |
| **Player Controls** | ✅ Partiel | `NetflixPlayerControls.kt` | Layout OK mais ne réutilise pas `EnhancedSeekBar`/`SkipMarkerButton` comme prévu par le plan |
| **Search TV** | ✅ Partiel | `NetflixSearchScreen.kt`, `NetflixOnScreenKeyboard.kt` | Layout keyboard+résultats OK, mais `KeyButton` manque `.focusable()`, résultats en `LazyVerticalGrid` au lieu de rangées horizontales |
| **Theme Netflix** | ✅ OK | `Color.kt`, `Theme.kt` | Couleurs Netflix et `NetflixColorScheme` correctement ajoutés |
| **Performance** | ❌ KO | Tous les fichiers Netflix | `Size.ORIGINAL`, pas de `derivedStateOf`, `@Parcelize` sur état lourd, double image background, `AnimatedContent` sur images full-res |
| **D-Pad Navigation** | ❌ KO | Tous les écrans | Pas de `TvLazy*`, pas de `FocusRequester` initial, pas de gestion focus groups, pas de `pivotOffsets` |
| **Favorites Flow** | ✅ OK | `HomeViewModel.kt`, `HomeUiState.kt` | `FavoritesRepository` injecté, favorites dans l'état |
| **Back Navigation** | ✅ OK | `MainActivity.kt`, `MainScreen.kt` | `popBackStack()` standard, navigation routes correctes |

### 1.2 Détail des Non-Conformités Majeures

**Plan Section 3.2 — Composables TV Optimisés :**
> *"TvLazyColumn + TvLazyRow (de `androidx.tv.foundation`) gèrent automatiquement la restauration du focus entre rangées"*

**Implémentation actuelle :** Tous les composables utilisent `LazyColumn`/`LazyRow` de `androidx.compose.foundation`. Les imports TV (`androidx.tv.foundation`, `androidx.tv.material3`) ne sont utilisés nulle part dans les fichiers Netflix.

**Plan Section 3.2 — Focus Management :**
> *"FocusRequester pour forcer le focus initial (ex: bouton Play du hero, premier onglet)"*
> *"Top Bar FocusRequester ← D-pad UP depuis première rangée / Content FocusRequester ← D-pad DOWN depuis top bar"*

**Implémentation actuelle :** Aucun `FocusRequester` dans `NetflixHomeScreen.kt`, `NetflixHeroBillboard.kt`, `NetflixTopBar.kt` ou `MainScreen.kt`. Zéro gestion de la transition focus entre Top Bar et contenu.

**Plan Section 5.1 — Card Animations :**
> *"Focus: scale 1.08x, bordure blanche 2dp, ombre 16dp, titre+métadonnées apparaissent (fadeIn+expandVertically 200ms)"*

**Implémentation actuelle :** Scale 1.05 (pas 1.08), pas d'ombre (`elevation` manquant), titre affiché par simple `if (isFocused)` au lieu de `AnimatedVisibility(fadeIn+expandVertically)`.

---

## 2. Analyse Détaillée des Problèmes de Performance

### 2.1 Tableau Synthétique

| # | Fichier | Problème | Type | Impact |
|---|---------|----------|------|--------|
| P1 | `NetflixMediaCard.kt:137` | `coil.size.Size.ORIGINAL` | Memory/I/O | **CRITIQUE** — charge images pleine résolution pour des cartes de 140dp |
| P2 | `NetflixHeroBillboard.kt:100` | `coil.size.Size.ORIGINAL` | Memory/I/O | **CRITIQUE** — images hero non redimensionnées |
| P3 | `HomeUiState.kt:13` | `@Parcelize` + `SavedStateHandle` | Serialization | **CRITIQUE** — sérialise des centaines de MediaItem à chaque state update |
| P4 | `NetflixHeroBillboard.kt:88-109` | `AnimatedContent` avec images full-res | Memory | **ÉLEVÉ** — 2 images ORIGINAL composées simultanément pendant la transition |
| P5 | `DiscoverScreen.kt:112` | Double chargement background | Memory/I/O | **ÉLEVÉ** — `AnimatedBackground` + hero billboard = 2 images full-screen |
| P6 | `NetflixMediaCard.kt:144` | `rememberAsyncImagePainter` dans `error` | Recomposition | **MOYEN** — crée un nouveau painter à chaque recomposition |
| P7 | `HomeViewModel.kt:119-123` | `filter` + `distinctBy` à chaque émission | CPU | **MOYEN** — recalcule sur chaque émission du Flow |
| P8 | `HomeViewModel.kt:146` | `savedStateHandle["home_state"] = newState` | Serialization | **ÉLEVÉ** — sérialise l'état entier à chaque update réussie |
| P9 | `NetflixHeroBillboard.kt:213-225` | `items.forEachIndexed` pour indicateurs | Recomposition | **FAIBLE** — recompose tous les dots à chaque changement d'index |

### 2.2 Détails et Corrections

#### P1 (CRITIQUE) — Images `Size.ORIGINAL` dans les cartes

**Fichier :** `NetflixMediaCard.kt:133-147`

**Problème :** Les cartes POSTER font 140dp × 210dp (~420×630px en xxhdpi). `Size.ORIGINAL` charge l'image Plex à sa résolution native (souvent 1000×1500 voire plus). Sur une Home avec 50+ cartes visibles, cela signifie des centaines de Mo en mémoire et un decode GPU massif.

**Code actuel :**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(false)
        .size(coil.size.Size.ORIGINAL) // ❌ PROBLÈME
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build(),
    ...
)
```

**Code corrigé :**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(false)
        // ✅ Dimensions exactes basées sur le cardType
        .size(
            width = when (cardType) {
                CardType.POSTER, CardType.TOP_TEN -> 420 // 140dp * 3 (xxhdpi)
                CardType.WIDE -> 720 // 240dp * 3
            },
            height = when (cardType) {
                CardType.POSTER, CardType.TOP_TEN -> 630
                CardType.WIDE -> 405
            }
        )
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build(),
    contentDescription = media.title,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize(),
    // ✅ Placeholder au lieu de rememberAsyncImagePainter (fix P6)
    placeholder = coil.compose.rememberAsyncImagePainter(
        model = android.R.drawable.ic_menu_gallery
    ),
    error = coil.compose.rememberAsyncImagePainter(
        model = android.R.drawable.ic_menu_gallery
    )
)
```

#### P2 (CRITIQUE) — Images `Size.ORIGINAL` dans le hero billboard

**Fichier :** `NetflixHeroBillboard.kt:96-108`

**Problème :** L'image hero fait 100% largeur × 550dp de hauteur (~1920×1650px en xxhdpi max). `Size.ORIGINAL` charge des images 4K inutilement.

**Code corrigé :**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(media.artUrl ?: media.thumbUrl)
        .crossfade(true)
        .size(1920, 1080) // ✅ Taille max TV standard
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build(),
    ...
)
```

#### P3 (CRITIQUE) — HomeUiState @Parcelize dans SavedStateHandle

**Fichier :** `HomeUiState.kt:13`, `HomeViewModel.kt:36,146,164`

**Problème :** `HomeUiState` est `@Parcelize` et contient `onDeck: List<MediaItem>`, `hubs: List<Hub>`, `favorites: List<MediaItem>`. Chaque `Hub` contient à son tour une `List<MediaItem>`. Pour une bibliothèque typique, cela peut représenter 200-500 `MediaItem` sérialisés en Parcel à chaque émission de Flow. Cela cause :
- `TransactionTooLargeException` si > 1Mo
- Freeze du main thread pendant la sérialisation
- Overhead CPU massif sur chaque state update

**Code actuel :**
```kotlin
@Parcelize
data class HomeUiState(
    val isLoading: Boolean = false,
    val onDeck: List<MediaItem> = emptyList(),
    val hubs: List<Hub> = emptyList(), // ❌ Potentiellement des centaines d'items
    val favorites: List<MediaItem> = emptyList(),
    ...
) : Parcelable
```

**Code corrigé :**
```kotlin
// ✅ Retirer @Parcelize et Parcelable — pas besoin de sauvegarder dans SavedStateHandle
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialSync: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String = "",
    val onDeck: List<MediaItem> = emptyList(),
    val hubs: List<Hub> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val error: String? = null,
)
```

Et dans `HomeViewModel.kt`, supprimer :
```kotlin
// ❌ SUPPRIMER ces lignes :
private val _uiState = MutableStateFlow(savedStateHandle["home_state"] ?: HomeUiState(isLoading = true))
savedStateHandle["home_state"] = newState // Ligne 146 et 164

// ✅ REMPLACER par :
private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
```

#### P4 (ÉLEVÉ) — AnimatedContent avec images full-res dans le hero

**Fichier :** `NetflixHeroBillboard.kt:88-109`

**Problème :** `AnimatedContent` compose les DEUX états (ancien + nouveau) pendant la transition de 500ms. Avec des images `Size.ORIGINAL`, cela signifie 2 images full-res en mémoire GPU simultanément.

**Code corrigé :** Remplacer `AnimatedContent` par `Crossfade` qui est plus léger, ET limiter la taille d'image :
```kotlin
// ✅ Crossfade au lieu de AnimatedContent
Crossfade(
    targetState = currentItem,
    animationSpec = tween(500),
    label = "HeroImageTransition"
) { media ->
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(media.artUrl ?: media.thumbUrl)
            .crossfade(false) // Le Crossfade compose gère déjà la transition
            .size(1920, 1080)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
```

#### P5 (ÉLEVÉ) — Double background image sur DiscoverScreen

**Fichier :** `DiscoverScreen.kt:112`

**Problème :** `AnimatedBackground(targetUrl = backgroundUrl ?: state.onDeck.firstOrNull()?.artUrl)` charge une image full-screen DERRIÈRE le hero billboard qui charge SA PROPRE image full-screen. Double I/O + double mémoire.

**Code corrigé :** Supprimer `AnimatedBackground` car le hero billboard gère déjà son propre backdrop :
```kotlin
@Composable
fun DiscoverScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ✅ SUPPRIMER AnimatedBackground — le hero billboard a son propre backdrop
            // AnimatedBackground(...) ← SUPPRIMER

            when {
                state.isInitialSync && state.onDeck.isEmpty() && state.hubs.isEmpty() ->
                    InitialSyncState(state.syncProgress, state.syncMessage)
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error) { onAction(HomeAction.Refresh) }
                state.onDeck.isEmpty() && state.hubs.isEmpty() -> EmptyState { onAction(HomeAction.Refresh) }
                else -> NetflixHomeContent(
                    onDeck = state.onDeck,
                    hubs = state.hubs,
                    favorites = state.favorites,
                    onAction = onAction,
                    onScrollStateChanged = { /* TopBar */ },
                )
            }
        }
    }
}
```

#### P7 (MOYEN) — filter+distinctBy recalculé à chaque émission

**Fichier :** `HomeViewModel.kt:119-123`

**Code actuel :**
```kotlin
val filteredHubs = content.hubs
    .filter { it.items.isNotEmpty() }
    .distinctBy { it.title }
```

**Code corrigé :** Pas critique car c'est dans une coroutine et non dans un composable. Mais le résultat devrait être mis en cache :
```kotlin
// ✅ OK tel quel — c'est dans un viewModelScope.launch, pas dans @Composable
// Si performance reste un souci, utiliser un map en dehors du collect
```

---

## 3. Analyse des Problèmes de Navigation D-Pad / Focus

### 3.1 Tableau Synthétique

| # | Fichier | Problème | Impact |
|---|---------|----------|--------|
| F1 | `NetflixHomeScreen.kt` | `LazyColumn` au lieu de `TvLazyColumn` | **BLOQUANT** — pas de restauration focus entre rangées |
| F2 | `NetflixContentRow.kt` | `LazyRow` au lieu de `TvLazyRow` | **BLOQUANT** — pas de scroll-to-focus ni pivot offsets |
| F3 | `NetflixMediaCard.kt` | `clickable()+focusable()` au lieu de TV Card | **ÉLEVÉ** — comportement focus non-standard pour TV |
| F4 | `NetflixTopBar.kt` | Aucune gestion focus Top Bar ↔ Content | **ÉLEVÉ** — impossible de naviguer proprement entre top bar et contenu |
| F5 | `NetflixHeroBillboard.kt` | Boutons Play/Info non focusables par défaut au D-Pad | **ÉLEVÉ** — pas de `FocusRequester` initial |
| F6 | `NetflixOnScreenKeyboard.kt:116` | `KeyButton` sans `.focusable()` | **ÉLEVÉ** — touches du clavier non-navigables au D-Pad |
| F7 | `NetflixDetailScreen.kt:240-298` | `LazyRow` imbriqué dans `LazyColumn` item | **ÉLEVÉ** — conteneurs scrollables imbriqués, focus imprévisible |
| F8 | `NetflixContentRow.kt:38` | `.focusable(false)` sur Column | **MOYEN** — empêche la traversée focus vers les enfants |
| F9 | `NetflixSearchScreen.kt:134-148` | `LazyVerticalGrid` pour les résultats au lieu de rangées horizontales | **MOYEN** — ne suit pas le pattern plan (rangées par type) |
| F10 | `MainScreen.kt` | Pas de gestion `onScrollStateChanged` connectée au TopBar | **FAIBLE** — transparence TopBar ne fonctionne pas |

### 3.2 Détails et Corrections

#### F1 (BLOQUANT) — LazyColumn au lieu de TvLazyColumn

**Fichier :** `NetflixHomeScreen.kt:43-108`

**Pourquoi c'est bloquant :** `TvLazyColumn` de `androidx.tv.foundation` gère automatiquement :
- **Restauration du focus** : quand on navigue entre rangées, le focus revient à l'item précédemment sélectionné
- **Scroll-to-focus** : l'item focusé est automatiquement scrollé dans la vue
- **`pivotOffsets`** : contrôle la position de l'item focusé à l'écran
- **`bringIntoViewOnFocus`** : smooth scroll vers l'item focusé

Sans `TvLazyColumn`, naviguer avec D-Pad UP/DOWN entre les rangées ne fonctionne pas correctement : le focus se perd, ne revient pas au bon item, et le scroll ne suit pas.

**Code actuel :**
```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

// ...
val listState = rememberLazyListState()

LazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 50.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp)
) {
    // ...
}
```

**Code corrigé :**
```kotlin
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.PivotOffsets

// ...
val listState = rememberTvLazyListState()

TvLazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 50.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
    pivotOffsets = PivotOffsets(parentFraction = 0.0f) // ✅ Item focusé reste en haut
) {
    // Les items restent identiques, mais TvLazyColumn gère le focus automatiquement
    item {
        val heroItems = remember(onDeck) { onDeck.take(10) }
        NetflixHeroBillboard(
            items = heroItems,
            onPlay = { onAction(HomeAction.PlayMedia(it)) },
            onInfo = { onAction(HomeAction.OpenMedia(it)) }
        )
    }

    item {
        val continueWatchingItems = remember(onDeck) {
            onDeck.filter { (it.playbackPositionMs ?: 0) > 0 }
        }
        if (continueWatchingItems.isNotEmpty()) {
            NetflixContentRow(
                title = "Continue Watching",
                items = continueWatchingItems,
                cardType = CardType.WIDE,
                onItemClick = { onAction(HomeAction.OpenMedia(it)) },
                onItemPlay = { onAction(HomeAction.PlayMedia(it)) }
            )
        }
    }

    // ... reste identique
}
```

#### F2 (BLOQUANT) — LazyRow au lieu de TvLazyRow

**Fichier :** `NetflixContentRow.kt:50-67`

**Code actuel :**
```kotlin
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

LazyRow(
    state = listState,
    contentPadding = PaddingValues(horizontal = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth()
) {
    items(
        items = items,
        key = { it.ratingKey }
    ) { item ->
        NetflixMediaCard(...)
    }
}
```

**Code corrigé :**
```kotlin
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.PivotOffsets

val listState = rememberTvLazyListState()

TvLazyRow(
    state = listState,
    contentPadding = PaddingValues(horizontal = 48.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), // ✅ 8dp comme le plan, pas 16dp
    pivotOffsets = PivotOffsets(parentFraction = 0.0f),
    modifier = Modifier.fillMaxWidth()
) {
    items(
        items = items,
        key = { "${it.ratingKey}_${it.serverId}" } // ✅ Clé unique composite
    ) { item ->
        NetflixMediaCard(...)
    }
}
```

#### F3 (ÉLEVÉ) — NetflixMediaCard pas adapté TV

**Fichier :** `NetflixMediaCard.kt:69-222`

**Problème :** La carte utilise `clickable(interactionSource, indication = null, onClick)` + `focusable(interactionSource)`. Sur Android TV, il faudrait utiliser les composants de `androidx.tv.material3` ou au minimum s'assurer que le focus fonctionne correctement avec D-Pad.

Le problème principal est que `clickable` attache un handler tactile et clavier, mais sur TV le focus management nécessite `Modifier.handleDPadKeyEvents` ou au minimum `Modifier.focusable()` en premier dans la chaîne.

**Correction recommandée :** Réorganiser les modifiers et ajouter les animations manquantes :
```kotlin
Column(
    modifier = modifier
        .width(cardWidth)
        .zIndex(if (isFocused) 10f else 0f)
        .scale(scale)
        // ✅ focusable AVANT clickable pour que le D-Pad fonctionne
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
) {
    // ... contenu identique

    // ✅ AnimatedVisibility pour le titre au lieu de if (isFocused)
    AnimatedVisibility(
        visible = isFocused,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(text = media.title, ...)
            // ...
        }
    }
}
```

#### F4 (ÉLEVÉ) — Pas de gestion focus Top Bar ↔ Content

**Fichier :** `NetflixTopBar.kt`, `MainScreen.kt`

**Le plan spécifie :**
```
Top Bar FocusRequester ← D-pad UP depuis première rangée
Content FocusRequester ← D-pad DOWN depuis top bar
```

**Code actuel :** Aucun mécanisme de transition focus entre la top bar et le contenu.

**Code corrigé — dans `MainScreen.kt` :**
```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToPlayer: (String, String) -> Unit,
    onNavigateToDetails: (String, String) -> Unit,
    onPlayUrl: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    var isTopBarScrolled by remember { mutableStateOf(false) }
    var isTopBarVisible by remember { mutableStateOf(true) }

    // ✅ FocusRequesters pour la navigation Top Bar ↔ Content
    val topBarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // ... (navigation items, offline redirect, etc.)

    Box(modifier = Modifier.fillMaxSize()) {
        // NavHost content
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ... routes identiques
        }

        if (!uiState.isOffline) {
            NetflixTopBar(
                selectedItem = selectedItem,
                isScrolled = isTopBarScrolled,
                isVisible = isTopBarVisible,
                onItemSelected = { /* ... */ },
                onSearchClick = { /* ... */ },
                onProfileClick = { /* ... */ },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .focusRequester(topBarFocusRequester),
            )
        }
    }
}
```

#### F5 (ÉLEVÉ) — Boutons Hero non focusables correctement

**Fichier :** `NetflixHeroBillboard.kt:196-203`

**Problème :** Les boutons `NetflixPlayButton` et `NetflixInfoButton` utilisent Material3 `Button` standard. Pour TV, il faudrait :
1. Un `FocusRequester` sur le bouton Play pour recevoir le focus initial
2. L'utilisation de `Modifier.focusProperties` pour gérer les directions

**Code corrigé :**
```kotlin
@Composable
fun NetflixHeroBillboard(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateIntervalMs: Long = 8000L,
    playButtonFocusRequester: FocusRequester = remember { FocusRequester() }, // ✅
) {
    // ...

    // ✅ Focus initial sur le bouton Play
    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    // Dans les boutons :
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NetflixPlayButton(
            onClick = { onPlay(currentItem) },
            modifier = Modifier.focusRequester(playButtonFocusRequester) // ✅
        )
        NetflixInfoButton(
            onClick = { onInfo(currentItem) }
        )
    }
}
```

#### F6 (ÉLEVÉ) — KeyButton sans `.focusable()`

**Fichier :** `NetflixOnScreenKeyboard.kt:102-148`

**Code actuel :**
```kotlin
Box(
    modifier = modifier
        .height(56.dp)
        .scale(scale)
        .background(...)
        .border(...)
        .clickable(onClick = onClick)      // ❌ clickable sans focusable
        .onFocusChanged { isFocused = it.isFocused },
    ...
)
```

**Code corrigé :**
```kotlin
Box(
    modifier = modifier
        .height(56.dp)
        .scale(scale)
        .background(...)
        .border(...)
        .focusable()                       // ✅ AJOUTER focusable() AVANT clickable
        .clickable(onClick = onClick)
        .onFocusChanged { isFocused = it.isFocused },
    ...
)
```

#### F7 (ÉLEVÉ) — LazyRow imbriqué dans LazyColumn item

**Fichier :** `NetflixDetailScreen.kt:236-301`

**Problème :** Le code suivant crée un `LazyRow` à l'intérieur d'un `item {}` de `LazyColumn`. Cela crée des conteneurs scrollables imbriqués qui confusent la navigation D-Pad : le focus ne sait pas s'il doit scroller le LazyRow horizontal ou le LazyColumn vertical.

```kotlin
// Dans LazyColumn:
item {
    when (selectedTab) {
        DetailTab.Episodes -> {
            LazyRow(...) { // ❌ LazyRow dans item{} de LazyColumn
                items(seasons) { ... }
            }
        }
        // ...
    }
}
```

**Code corrigé :** Utiliser `TvLazyRow` qui gère mieux la cohabitation avec le parent :
```kotlin
// ✅ Utiliser TvLazyRow et s'assurer que le parent est un TvLazyColumn
item {
    when (selectedTab) {
        DetailTab.Episodes -> {
            if (seasons.isNotEmpty()) {
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(end = 50.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f)
                ) {
                    items(seasons, key = { it.ratingKey }) { season ->
                        NetflixMediaCard(
                            media = season,
                            onClick = { onAction(MediaDetailEvent.OpenSeason(season)) },
                            onPlay = {},
                        )
                    }
                }
            }
        }
        // ...
    }
}
```

#### F8 (MOYEN) — `.focusable(false)` sur Column dans NetflixContentRow

**Fichier :** `NetflixContentRow.kt:38`

**Problème :** `.focusable(false)` empêche le Column d'être dans l'arbre de focus. Si aucun enfant n'est focusable, la rangée entière devient inaccessible.

**Code corrigé :** Retirer le modifier :
```kotlin
Column(
    modifier = modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
        // ✅ SUPPRIMER .focusable(false)
) {
```

---

## 4. Plan de Correction Priorisé

### Niveau 1 — BLOQUANTS (App quasi inutilisable)

| # | Titre | Fichiers | Checklist | Effort | Impact |
|---|-------|----------|-----------|--------|--------|
| **1.1** | Migrer vers `TvLazyColumn`/`TvLazyRow` | `NetflixHomeScreen.kt`, `NetflixContentRow.kt`, `NetflixDetailScreen.kt` | 1. Remplacer imports `LazyColumn`→`TvLazyColumn`, `LazyRow`→`TvLazyRow` 2. Utiliser `rememberTvLazyListState()` 3. Ajouter `pivotOffsets = PivotOffsets(parentFraction = 0.0f)` 4. Vérifier que les keys sont composites `"${ratingKey}_${serverId}"` | **M** | Navigation D-Pad réparée, restauration focus, scroll-to-focus |
| **1.2** | Supprimer `@Parcelize` / `SavedStateHandle` du HomeUiState | `HomeUiState.kt`, `HomeViewModel.kt` | 1. Retirer `@Parcelize` et `: Parcelable` de `HomeUiState` 2. Supprimer `savedStateHandle["home_state"]` (lignes 36, 146, 164) 3. Initialiser `_uiState = MutableStateFlow(HomeUiState(isLoading = true))` | **XS** | Fin des freezes liés à la sérialisation, fin du risque `TransactionTooLargeException` |
| **1.3** | Corriger taille images Coil (`Size.ORIGINAL` → dimensions réelles) | `NetflixMediaCard.kt`, `NetflixHeroBillboard.kt`, `DiscoverScreen.kt` | 1. `NetflixMediaCard`: remplacer `Size.ORIGINAL` par `size(420, 630)` pour POSTER, `size(720, 405)` pour WIDE 2. `NetflixHeroBillboard`: remplacer par `size(1920, 1080)` 3. `DiscoverScreen/AnimatedBackground`: remplacer par `size(1920, 1080)` ou supprimer (cf. 1.4) | **S** | Réduction mémoire images de ~80%, fin des freezes au scroll |
| **1.4** | Supprimer double background (`AnimatedBackground`) | `DiscoverScreen.kt` | 1. Supprimer l'appel `AnimatedBackground(...)` dans `DiscoverScreen` 2. Supprimer la variable `backgroundUrl` inutile 3. Le hero billboard gère déjà son backdrop | **XS** | 1 image full-screen en moins en mémoire |

### Niveau 2 — Performance Critique (Home, scroll, détails)

| # | Titre | Fichiers | Checklist | Effort | Impact |
|---|-------|----------|-----------|--------|--------|
| **2.1** | Remplacer `AnimatedContent` par `Crossfade` dans hero | `NetflixHeroBillboard.kt` | 1. Remplacer `AnimatedContent` par `Crossfade(targetState=currentItem, animationSpec=tween(500))` 2. Garder la même structure de contenu 3. Mettre `crossfade(false)` sur l'ImageRequest interne | **XS** | Transition hero moins gourmande en mémoire |
| **2.2** | Ajouter `FocusRequester` initial sur le hero Play button | `NetflixHeroBillboard.kt` | 1. Ajouter paramètre `playButtonFocusRequester: FocusRequester` 2. Attacher `.focusRequester(...)` au bouton Play 3. `LaunchedEffect(Unit) { playButtonFocusRequester.requestFocus() }` | **S** | Focus initial correct au lancement Home |
| **2.3** | Gestion focus Top Bar ↔ Content | `MainScreen.kt`, `NetflixTopBar.kt` | 1. Créer 2 `FocusRequester` dans `MainScreen` 2. Attacher au TopBar et au NavHost 3. Gérer D-Pad UP depuis content → topBar, D-Pad DOWN depuis topBar → content 4. Utiliser `Modifier.focusProperties { up = topBarFocusRequester }` | **M** | Navigation fluide entre top bar et contenu |
| **2.4** | Ajouter `AnimatedVisibility` pour le titre des cartes | `NetflixMediaCard.kt` | 1. Remplacer `if (isFocused) { Column... }` par `AnimatedVisibility(visible=isFocused, enter=fadeIn+expandVertically, exit=fadeOut+shrinkVertically)` 2. Augmenter scale à 1.08 comme prévu | **XS** | Animation plus fluide, conforme au plan |
| **2.5** | Connecter scroll state du Home au TopBar | `MainScreen.kt`, `NetflixHomeScreen.kt` | 1. Passer un callback `onScrollStateChanged(Boolean)` de `MainScreen` à `HomeRoute` 2. Propager `isTopBarScrolled` au `NetflixTopBar` 3. Le `LaunchedEffect(listState)` dans `NetflixHomeContent` émet déjà l'état | **S** | Transparence TopBar fonctionnelle |

### Niveau 3 — Finitions UX / Stabilité

| # | Titre | Fichiers | Checklist | Effort | Impact |
|---|-------|----------|-----------|--------|--------|
| **3.1** | Corriger `.focusable()` sur `KeyButton` du clavier | `NetflixOnScreenKeyboard.kt` | 1. Ajouter `.focusable()` avant `.clickable()` dans `KeyButton` | **XS** | Clavier TV navigable au D-Pad |
| **3.2** | Retirer `.focusable(false)` sur `NetflixContentRow` Column | `NetflixContentRow.kt` | 1. Supprimer `.focusable(false)` de la ligne 38 | **XS** | Rangées accessibles au focus |
| **3.3** | Résultats recherche en rangées au lieu de grid | `NetflixSearchScreen.kt` | 1. Remplacer `LazyVerticalGrid` par `TvLazyColumn` + `NetflixContentRow` par type de résultat 2. Grouper les résultats par type (Movies, TV Shows, Episodes) | **M** | Conforme au plan, meilleure navigation TV |
| **3.4** | Player : réutiliser `EnhancedSeekBar` et `SkipMarkerButton` | `NetflixPlayerControls.kt` | 1. Remplacer `Slider` standard par `EnhancedSeekBar` existant 2. Intégrer `SkipMarkerButton` conditionnel | **S** | Conformité plan, UX player complète |
| **3.5** | Fix `rememberAsyncImagePainter` dans error handler des cartes | `NetflixMediaCard.kt` | 1. Remplacer `error = rememberAsyncImagePainter(...)` par `error = painterResource(android.R.drawable.ic_menu_gallery)` | **XS** | Évite création de painter à chaque recomposition |
| **3.6** | Ajouter padding top 56dp aux écrans non-Home | `Downloads`, `IPTV`, `Settings`, `Favorites`, `History` | 1. Ajouter `Modifier.padding(top = 56.dp)` au root de chaque écran pour laisser la place au TopBar overlay | **S** | Contenu non masqué par la TopBar |

---

## 5. Annexes : Résumé des Imports à Corriger

### Imports à remplacer dans les fichiers Netflix

**Avant (standard Compose) :**
```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
```

**Après (Compose for TV) :**
```kotlin
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.PivotOffsets
```

### Vérification des dépendances build.gradle

Les dépendances TV sont DÉJÀ présentes dans `app/build.gradle.kts` :
```kotlin
implementation(libs.androidx.tv.foundation) // ✅ Déjà là
implementation(libs.androidx.tv.material)   // ✅ Déjà là
```

Aucune nouvelle dépendance n'est requise.

---

## 6. Ordre d'Exécution Recommandé

1. **1.2** — Supprimer `@Parcelize`/`SavedStateHandle` (XS, gain immédiat)
2. **1.3** — Corriger tailles images Coil (S, gain mémoire massif)
3. **1.4** — Supprimer double background (XS, gain mémoire)
4. **1.1** — Migrer vers `TvLazyColumn`/`TvLazyRow` (M, fix navigation D-Pad)
5. **2.1** — Crossfade hero (XS, gain mémoire transition)
6. **3.1** + **3.2** — Fix focusable clavier et row (XS, quick wins)
7. **2.2** — FocusRequester hero (S, focus initial)
8. **2.3** — Focus Top Bar ↔ Content (M, navigation complète)
9. **2.4** + **2.5** — Animations cartes + scroll TopBar (S, polish)
10. **3.3** → **3.6** — Finitions recherche, player, padding (S-M, conformité plan)

**Effort total estimé : ~10-15 corrections, la majorité étant XS ou S.**
