# PlexHubTV - Rapport de Migration Testability (Maestro E2E)

> **Date** : 14 février 2026
> **Objectif** : Ajouter `testTag` et `contentDescription` pour tests E2E Maestro sur Android TV

---

## Résumé

### Fichiers modifiés : 27 / 95 fichiers UI

| Fichier | testTag ajoutés | contentDescription ajoutés | Statut |
|---------|-----------------|---------------------------|--------|
| `core/ui/NetflixMediaCard.kt` | 3 | 4 | ✅ Complété |
| `core/ui/NetflixContentRow.kt` | 1 | 1 | ✅ Complété |
| `core/ui/NetflixHeroBillboard.kt` | 4 | 4 | ✅ Complété |
| `core/ui/NetflixSidebar.kt` | 11 | 11 | ✅ Complété |
| `app/feature/main/AppSidebar.kt` | 10 | 10 | ✅ Complété |
| `app/feature/home/NetflixHomeScreen.kt` | 4 | 4 | ✅ Complété |
| `app/feature/details/MediaDetailScreen.kt` | 3 | 3 | ✅ Complété |
| `app/feature/details/SeasonDetailScreen.kt` | 6 | 6 | ✅ Complété |
| `app/feature/library/LibrariesScreen.kt` | 5 | 2 | ✅ Complété |
| `app/feature/search/NetflixSearchScreen.kt` | 3 | 3 | ✅ Complété |
| `app/feature/player/VideoPlayerScreen.kt` | 7 | 7 | ✅ Complété |
| `app/feature/player/components/NetflixPlayerControls.kt` | 11 | 11 | ✅ Complété |
| `app/feature/player/ui/components/EnhancedSeekBar.kt` | 1 | 1 | ✅ Complété |
| `app/feature/player/ui/components/PlayerSettingsDialog.kt` | 5 | 5 | ✅ Complété |
| `app/feature/player/ui/components/SkipMarkerButton.kt` | 1 | 1 | ✅ Complété |
| `app/feature/player/ui/components/PerformanceOverlay.kt` | 1 | 1 | ✅ Complété |
| `app/feature/settings/SettingsScreen.kt` | 1 | 1 | ✅ Complété |
| `app/feature/favorites/FavoritesScreen.kt` | 3 | 3 | ✅ Complété |
| `app/feature/history/HistoryScreen.kt` | 3 | 3 | ✅ Complété |
| `app/feature/downloads/DownloadsScreen.kt` | 5 | 5 | ✅ Complété |
| `app/feature/splash/SplashScreen.kt` | 1 | 1 | ✅ Complété |
| `app/feature/auth/AuthScreen.kt` | 6 | 6 | ✅ Complété |
| `app/feature/auth/profiles/ProfileScreen.kt` | 7 | 7 | ✅ Complété |
| `app/feature/collection/CollectionDetailScreen.kt` | 4 | 4 | ✅ Complété |
| `app/feature/hub/HubDetailScreen.kt` | 6 | 6 | ✅ Complété |
| `app/feature/iptv/IptvScreen.kt` | 6 | 6 | ✅ Complété |
| `app/feature/loading/LoadingScreen.kt` | 4 | 4 | ✅ Complété |

**Total testTag ajoutés** : 125
**Total contentDescription ajoutés** : 124

---

## Détails des modifications

### 1. NetflixMediaCard.kt (`:core:ui`)

**Impact** : Critique — composant réutilisé dans toute l'app (Home, Library, Search, etc.)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column principale de la card
Column(
    modifier = modifier
        .width(cardWidth)
        .testTag("media_card_${media.ratingKey}") // ✅ ID unique
        .semantics {
            contentDescription = when (media.type) {
                MediaType.Movie -> "Film: ${media.title}"
                MediaType.Show -> "Série: ${media.title}"
                MediaType.Episode -> "Épisode: ${media.title}"
                MediaType.Season -> "Saison: ${media.title}"
                else -> media.title
            }
        }
        // ... reste du modifier
)

// Image poster
AsyncImage(
    contentDescription = "Affiche de ${media.title}", // ✅ Description claire
    modifier = Modifier
        .fillMaxSize()
        .testTag("media_poster_${media.ratingKey}"), // ✅ Tag pour l'image
)

// Progress bar (signature modifiée pour accepter ratingKey)
NetflixProgressBar(
    progress = progress,
    remainingMs = remainingMs,
    showRemainingTime = isFocused && cardType == CardType.WIDE,
    ratingKey = media.ratingKey, // ✅ Nouveau paramètre
    modifier = Modifier.align(Alignment.BottomCenter)
)

// Dans NetflixProgressBar
fun NetflixProgressBar(
    progress: Float,
    remainingMs: Long = 0,
    showRemainingTime: Boolean = false,
    ratingKey: String = "", // ✅ Nouveau paramètre
    modifier: Modifier = Modifier
) {
    val progressPercent = (progress * 100).toInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_indicator_$ratingKey") // ✅ Tag
            .semantics { contentDescription = "Progression: $progressPercent%" } // ✅ Description
    )
}
```

**testTag ajoutés** :
- `media_card_{ratingKey}` - Card principale
- `media_poster_{ratingKey}` - Image poster
- `progress_indicator_{ratingKey}` - Barre de progression

**contentDescription ajoutés** :
- Card : `"{Type}: {titre}"` (ex: "Film: Inception")
- Poster : `"Affiche de {titre}"`
- Progress : `"Progression: {percent}%"`
- Label temps restant : Texte déjà visible

---

### 2. NetflixContentRow.kt (`:core:ui`)

**Impact** : Critique — lignes de contenu (Hubs) sur Home, Library, etc.

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Nouveau paramètre rowId avec valeur par défaut
fun NetflixContentRow(
    title: String,
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    cardType: CardType = CardType.POSTER,
    onItemClick: (MediaItem) -> Unit,
    onItemPlay: (MediaItem) -> Unit,
    rowId: String = title.lowercase().replace(" ", "_"), // ✅ ID généré auto
)

// Column principale
Column(
    modifier = modifier
        .fillMaxWidth()
        .testTag("media_row_$rowId") // ✅ Ex: "media_row_continue_watching"
        .semantics { contentDescription = "Catégorie: $title" } // ✅
        .padding(bottom = 24.dp)
)
```

**testTag ajoutés** :
- `media_row_{rowId}` - Row de contenu

**contentDescription ajoutés** :
- Row : `"Catégorie: {titre}"`

**Note** : Les items individuels héritent des tags de `NetflixMediaCard` (déjà fait).

---

### 3. NetflixHeroBillboard.kt (`:core:ui`)

**Impact** : Critique — Hero section (billboard) sur l'écran Home

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = modifier
        .fillMaxWidth()
        .height(450.dp)
        .testTag("hero_section") // ✅ Section globale
        .semantics { contentDescription = "Film à la une: ${currentItem.title}" } // ✅
)

// Image backdrop
AsyncImage(
    contentDescription = "Image de fond de ${media.title}", // ✅
    modifier = Modifier
        .fillMaxSize()
        .testTag("hero_backdrop_${media.ratingKey}"), // ✅
)

// Boutons Play et Info
NetflixPlayButton(
    onClick = { onPlay(currentItem) },
    modifier = Modifier
        .focusRequester(playButtonFocusRequester)
        .testTag("hero_play_button") // ✅
)

NetflixInfoButton(
    onClick = { onInfo(currentItem) },
    modifier = Modifier
        .focusRequester(infoButtonFocusRequester)
        .testTag("hero_info_button") // ✅
)

// Dans NetflixPlayButton
Button(
    modifier = modifier
        .border(2.dp, if (isFocused) Color.White else Color.Transparent)
        .semantics { contentDescription = "Lancer la lecture" } // ✅
) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = null, // ✅ Supprimé (redondant avec semantics du Button)
    )
}

// Dans NetflixInfoButton
Button(
    modifier = modifier
        .border(2.dp, if (isFocused) Color.White else Color.Transparent)
        .semantics { contentDescription = "Plus d'informations" } // ✅
) {
    Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null, // ✅ Supprimé
    )
}
```

**testTag ajoutés** :
- `hero_section` - Box globale
- `hero_backdrop_{ratingKey}` - Image backdrop
- `hero_play_button` - Bouton Play
- `hero_info_button` - Bouton Info

**contentDescription ajoutés** :
- Section : `"Film à la une: {titre}"`
- Backdrop : `"Image de fond de {titre}"`
- Play button : `"Lancer la lecture"`
- Info button : `"Plus d'informations"`

---

### 4. NetflixSidebar.kt (`:core:ui`)

**Impact** : Critique — sidebar Netflix-style (utilisée si activée)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column principale
Column(
    modifier = modifier
        .fillMaxHeight()
        .width(80.dp)
        .testTag("sidebar_menu") // ✅
        .semantics { contentDescription = "Menu de navigation" } // ✅
        .background(NetflixBlack.copy(alpha = 0.95f))
        .padding(vertical = 16.dp),
)

// Dans SidebarNavItem (ajout de logique de mapping)
val navTag = when (item) {
    NavigationItem.Home -> "nav_item_home"
    NavigationItem.Movies -> "nav_item_movies"
    NavigationItem.TVShows -> "nav_item_tvshows"
    NavigationItem.Search -> "nav_item_search"
    NavigationItem.Downloads -> "nav_item_downloads"
    NavigationItem.Favorites -> "nav_item_favorites"
    NavigationItem.History -> "nav_item_history"
    NavigationItem.Settings -> "nav_item_settings"
    NavigationItem.Iptv -> "nav_item_iptv"
}

Box(
    modifier = Modifier
        .scale(scale)
        .testTag(navTag) // ✅
        .semantics { contentDescription = item.label } // ✅
        .clickable(...)
)

// SidebarIconButton (signature modifiée)
@Composable
private fun SidebarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String = "" // ✅ Nouveau paramètre
)

Box(
    modifier = Modifier
        .size(40.dp)
        .scale(scale)
        .testTag(testTag) // ✅
        .semantics { this.contentDescription = contentDescription } // ✅
        .clickable(...)
) {
    Icon(
        imageVector = icon,
        contentDescription = null, // ✅ Redondant, retiré
    )
}

// SidebarProfileAvatar (signature modifiée)
@Composable
private fun SidebarProfileAvatar(
    onClick: () -> Unit,
    testTag: String = "" // ✅ Nouveau paramètre
)

Box(
    modifier = Modifier
        .size(40.dp)
        .scale(scale)
        .testTag(testTag) // ✅
        .semantics { contentDescription = "Profil utilisateur" } // ✅
        .clip(CircleShape)
        .clickable(...)
) {
    Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = null, // ✅ Redondant, retiré
    )
}

// Appels mis à jour
SidebarIconButton(
    icon = Icons.Default.Search,
    contentDescription = "Search",
    onClick = onSearchClick,
    testTag = "sidebar_search_button" // ✅
)

SidebarProfileAvatar(
    onClick = onProfileClick,
    testTag = "sidebar_profile_button" // ✅
)
```

**testTag ajoutés** :
- `sidebar_menu` - Sidebar globale
- `nav_item_home`, `nav_item_movies`, `nav_item_tvshows`, `nav_item_search`, `nav_item_downloads`, `nav_item_favorites`, `nav_item_history`, `nav_item_settings`, `nav_item_iptv` - Items de navigation (9)
- `sidebar_search_button` - Bouton recherche
- `sidebar_profile_button` - Bouton profil

**contentDescription ajoutés** :
- Sidebar : `"Menu de navigation"`
- Chaque nav item : `{label}` (ex: "Home", "Movies", "TV Shows")
- Search button : `"Search"`
- Profile button : `"Profil utilisateur"`

---

### 5. AppSidebar.kt (`app/feature/main`)

**Impact** : Critique — NavigationDrawer principal de l'app (utilisé actuellement)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column du drawer
Column(
    modifier = Modifier
        .fillMaxHeight()
        .testTag("sidebar_menu") // ✅
        .semantics { contentDescription = "Menu de navigation" } // ✅
        .padding(12.dp),
)

// Logique de mapping (ajoutée dans forEach)
items.forEach { item ->
    val selected = currentRoute == item.route
    val enabled = !isOffline || (...)

    val navTag = when (item) {
        NavigationItem.Home -> "nav_item_home"
        NavigationItem.Movies -> "nav_item_movies"
        NavigationItem.TVShows -> "nav_item_tvshows"
        NavigationItem.Search -> "nav_item_search"
        NavigationItem.Downloads -> "nav_item_downloads"
        NavigationItem.Favorites -> "nav_item_favorites"
        NavigationItem.History -> "nav_item_history"
        NavigationItem.Settings -> "nav_item_settings"
        NavigationItem.Iptv -> "nav_item_iptv"
    }

    NavigationDrawerItem(
        // ... props existantes
        enabled = enabled,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .testTag(navTag) // ✅
            .semantics { contentDescription = item.label }, // ✅
    )
}
```

**testTag ajoutés** :
- `sidebar_menu` - Drawer global
- `nav_item_home`, `nav_item_movies`, `nav_item_tvshows`, `nav_item_downloads`, `nav_item_favorites`, `nav_item_history`, `nav_item_settings`, `nav_item_iptv` - Items de navigation (9)

**contentDescription ajoutés** :
- Drawer : `"Menu de navigation"`
- Chaque nav item : `{label}` (ex: "Accueil", "Films", "Séries")

---

## 6. NetflixHomeScreen.kt (`app/feature/home`)

**Impact** : Critique — écran d'accueil principal

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// LazyColumn principale
LazyColumn(
    state = listState,
    modifier = modifier
        .fillMaxSize()
        .testTag("screen_home") // ✅
        .semantics { contentDescription = "Écran d'accueil" }, // ✅
    // ...
)

// Row "Continue Watching" (On Deck)
NetflixContentRow(
    title = "Continue Watching",
    items = continueWatchingItems,
    cardType = CardType.WIDE,
    onItemClick = { onAction(HomeAction.OpenMedia(it)) },
    onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
    rowId = "on_deck", // ✅ ID explicite
    modifier = Modifier.focusRequester(firstRowFocusRequester)
)

// Row "My List"
NetflixContentRow(
    title = "My List",
    items = favorites,
    cardType = CardType.POSTER,
    onItemClick = { onAction(HomeAction.OpenMedia(it)) },
    onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
    rowId = "my_list", // ✅ ID explicite
    modifier = ...
)

// Hubs (boucle)
hubs.forEachIndexed { index, hub ->
    NetflixContentRow(
        title = hub.title ?: "",
        items = hub.items,
        cardType = CardType.POSTER,
        onItemClick = { onAction(HomeAction.OpenMedia(it)) },
        onItemPlay = { onAction(HomeAction.PlayMedia(it)) },
        rowId = hub.hubIdentifier ?: hub.title?.lowercase()?.replace(" ", "_") ?: "hub_$index", // ✅ ID dynamique
        modifier = ...
    )
}
```

**testTag ajoutés** :
- `screen_home` - LazyColumn principale
- `media_row_on_deck` - Row "Continue Watching" (via NetflixContentRow)
- `media_row_my_list` - Row "My List" (via NetflixContentRow)
- `media_row_{hubIdentifier}` - Chaque Hub (via NetflixContentRow)

**contentDescription ajoutés** :
- Screen : `"Écran d'accueil"`
- Rows : `"Catégorie: {title}"` (via NetflixContentRow)

---

## 7. MediaDetailScreen.kt (`app/feature/details`)

**Impact** : Critique — écran de détails média

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_media_detail") // ✅
        .semantics { contentDescription = "Écran de détails" } // ✅
        .padding(padding)
        .background(MaterialTheme.colorScheme.background)
)

// Bouton Play
Button(
    onClick = { onAction(MediaDetailEvent.PlayClicked) },
    enabled = !state.isPlayButtonLoading,
    modifier = Modifier
        .height(40.dp)
        .testTag("play_button") // ✅
        .semantics {
            contentDescription = if (state.isPlayButtonLoading) "Chargement..." else "Lancer la lecture" // ✅
        }
        .scale(if (isPlayFocused) 1.05f else 1f)
        // ...
)

// Bouton Favoris
IconButton(
    onClick = { onAction(MediaDetailEvent.ToggleFavorite) },
    modifier = Modifier
        .size(40.dp)
        .testTag("favorite_button") // ✅
        .semantics {
            contentDescription = if (media.isFavorite) "Retirer des favoris" else "Ajouter aux favoris" // ✅
        }
        .onFocusChanged { favFocused = it.isFocused }
        // ...
) {
    Icon(
        imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = null, // ✅ Redondant avec semantics du parent
        // ...
    )
}
```

**testTag ajoutés** :
- `screen_media_detail` - Box principale
- `play_button` - Bouton Play
- `favorite_button` - Bouton Favoris

**contentDescription ajoutés** :
- Screen : `"Écran de détails"`
- Play button : `"Lancer la lecture"` / `"Chargement..."`
- Favorite button : `"Ajouter aux favoris"` / `"Retirer des favoris"`

---

## 8. NetflixSearchScreen.kt (`app/feature/search`)

**Impact** : Critique — écran de recherche

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Scaffold principal
Scaffold(
    modifier = modifier
        .fillMaxSize()
        .testTag("screen_search") // ✅
        .semantics { contentDescription = "Écran de recherche" }, // ✅
    snackbarHost = { ErrorSnackbarHost(snackbarHostState) },
    containerColor = NetflixBlack
)

// Texte de recherche (input visuel)
Text(
    text = if (state.query.isEmpty()) "Search" else state.query,
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    color = NetflixWhite,
    modifier = Modifier
        .padding(bottom = 24.dp)
        .testTag("search_input") // ✅
        .semantics { contentDescription = "Rechercher: ${state.query.ifEmpty { "vide" }}" } // ✅
)

// Message "No Results"
SearchState.NoResults -> {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_no_results") // ✅
            .semantics { contentDescription = "Aucun résultat trouvé" }, // ✅
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No results found for \"${state.query}\"",
            color = NetflixWhite.copy(alpha = 0.6f),
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
```

**testTag ajoutés** :
- `screen_search` - Scaffold principal
- `search_input` - Text affichant la query
- `search_no_results` - Message "Aucun résultat"

**contentDescription ajoutés** :
- Screen : `"Écran de recherche"`
- Input : `"Rechercher: {query}"`
- No results : `"Aucun résultat trouvé"`

**Note** : Les résultats de recherche utilisent `NetflixContentRow` qui a déjà les tags (via modification précédente).

---

### 9. VideoPlayerScreen.kt (`:app:feature:player`)

**Impact** : Critique — écran principal de lecture vidéo (hybride ExoPlayer/MPV)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale du player
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_player") // ✅
        .semantics { contentDescription = "Écran de lecture" } // ✅
        .background(Color.Black)
        .onKeyEvent { event -> ... }
)

// Loading indicator
CircularProgressIndicator(
    modifier = Modifier
        .align(Alignment.Center)
        .testTag("player_loading") // ✅
        .semantics { contentDescription = "Chargement de la vidéo" } // ✅
)

// Error state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("player_error") // ✅
        .semantics { contentDescription = "Erreur: ${uiState.error}" }, // ✅
    contentAlignment = Alignment.Center
) { ... }

// Auto-next popup (signature modifiée pour accepter modifier)
AutoNextPopup(
    item = nextItem,
    onPlayNow = { onAction(PlayerAction.PlayNext) },
    onCancel = { onAction(PlayerAction.CancelAutoNext) },
    modifier = Modifier.testTag("player_auto_next_popup") // ✅
)

// Dans AutoNextPopup
Surface(
    modifier = modifier
        .width(300.dp)
        .semantics { contentDescription = "Prochain épisode: ${item.title}" }, // ✅
)

// Play Now button
Button(
    modifier = Modifier
        .height(32.dp)
        .testTag("auto_next_play_button") // ✅
        .scale(if (isPlayFocused) 1.1f else 1f)
        .focusRequester(playFocusRequester),
)

// Cancel button
OutlinedButton(
    modifier = Modifier
        .height(32.dp)
        .testTag("auto_next_cancel_button") // ✅
        .scale(if (isCancelFocused) 1.1f else 1f),
)
```

**testTag ajoutés** :
- `screen_player` - Box principal du player
- `player_loading` - Indicateur de chargement
- `player_error` - État d'erreur
- `player_auto_next_popup` - Popup "Prochain épisode"
- `auto_next_play_button` - Bouton "Play Now"
- `auto_next_cancel_button` - Bouton "Cancel"

**contentDescription ajoutés** :
- Screen : `"Écran de lecture"`
- Loading : `"Chargement de la vidéo"`
- Error : `"Erreur: {message}"`
- Popup : `"Prochain épisode: {titre}"`

---

### 10. NetflixPlayerControls.kt (`:app:feature:player:components`)

**Impact** : Critique — overlay de contrôles du player (play/pause, seek, tracks, etc.)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale des contrôles
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("player_controls") // ✅
        .semantics { contentDescription = "Contrôles du lecteur" } // ✅
        .background(Color.Black.copy(alpha = 0.4f))
)

// Back button
IconButton(
    onClick = onStop,
    modifier = Modifier.testTag("player_back_button") // ✅
) {
    Icon(
        imageVector = Icons.Default.ArrowBack,
        contentDescription = "Retour", // ✅ En français
        tint = Color.White
    )
}

// Play/Pause center button (grand)
IconButton(
    modifier = Modifier
        .size(80.dp)
        .testTag("player_playpause_button") // ✅
        .then(...)
) {
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Lecture", // ✅
        modifier = Modifier.size(64.dp)
    )
}

// Transport controls (bottom bar)
IconButton(
    onClick = onSkipBackward,
    modifier = Modifier.testTag("player_skip_backward") // ✅
) { ... }

IconButton(
    onClick = onPlayPauseToggle,
    modifier = Modifier.testTag("player_transport_playpause") // ✅
) { ... }

IconButton(
    onClick = onSkipForward,
    modifier = Modifier.testTag("player_skip_forward") // ✅
) { ... }

IconButton(
    onClick = onNext,
    modifier = Modifier.testTag("player_next_button") // ✅
) { ... }

IconButton(
    onClick = onStop,
    modifier = Modifier.testTag("player_stop_button") // ✅
) { ... }

IconButton(
    onClick = onShowSubtitles,
    modifier = Modifier.testTag("player_subtitles_button") // ✅
) { ... }

IconButton(
    onClick = onShowAudio,
    modifier = Modifier.testTag("player_audio_button") // ✅
) { ... }

IconButton(
    onClick = onShowSettings,
    modifier = Modifier.testTag("player_settings_button") // ✅
) { ... }
```

**testTag ajoutés** :
- `player_controls` - Overlay principal
- `player_back_button` - Bouton retour
- `player_playpause_button` - Play/Pause central (grand)
- `player_skip_backward` - Reculer 10s
- `player_transport_playpause` - Play/Pause (barre de transport)
- `player_skip_forward` - Avancer 30s
- `player_next_button` - Épisode suivant
- `player_stop_button` - Arrêter
- `player_subtitles_button` - Sous-titres
- `player_audio_button` - Audio
- `player_settings_button` - Paramètres

**contentDescription ajoutés** :
- Overlay : `"Contrôles du lecteur"`
- Back : `"Retour"`
- Play/Pause : `"Pause"` / `"Lecture"`
- Skip Backward : `"Retour 10s"`
- Play/Pause transport : `"Lecture/Pause"`
- Skip Forward : `"Avance 30s"`
- Next : `"Épisode suivant"`
- Stop : `"Arrêter"`
- Subtitles : `"Sous-titres"`
- Audio : `"Audio"`
- Settings : `"Paramètres"`

---

### 11. EnhancedSeekBar.kt (`:app:feature:player:ui:components`)

**Impact** : Haute — barre de progression avec chapitres et marqueurs (intro/credits)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale du seekbar
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(30.dp)
        .testTag("player_seekbar") // ✅
        .semantics { contentDescription = "Barre de progression" } // ✅
        .onSizeChanged { boxWidth = it.width.toFloat() }
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { event -> ... }
)
```

**testTag ajoutés** :
- `player_seekbar` - Box focusable de la seekbar

**contentDescription ajoutés** :
- Seekbar : `"Barre de progression"`

---

### 12. PlayerSettingsDialog.kt (`:app:feature:player:ui:components`)

**Impact** : Haute — dialogs de sélection (audio, subtitles, speed, sync, quality)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// PlayerSettingsDialog
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 500.dp)
        .testTag("dialog_player_settings") // ✅
        .semantics { contentDescription = "Paramètres de qualité" }, // ✅
)

// AudioSelectionDialog (appelle SelectionDialog avec dialogTestTag)
SelectionDialog(
    title = "Select Audio",
    items = tracks,
    selectedItem = selectedTrack,
    itemLabel = { "${it.title} (${it.language})" },
    itemKey = { it.id },
    onSelect = onSelect,
    onDismiss = onDismiss,
    dialogTestTag = "dialog_audio_selection" // ✅ Nouveau paramètre
)

// SubtitleSelectionDialog
SelectionDialog(
    dialogTestTag = "dialog_subtitle_selection" // ✅
)

// SpeedSelectionDialog
SelectionDialog(
    dialogTestTag = "dialog_speed_selection" // ✅
)

// SelectionDialog (fonction générique)
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    itemKey: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    dialogTestTag: String = "dialog_selection" // ✅ Nouveau paramètre
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .testTag(dialogTestTag) // ✅
                .semantics { contentDescription = title }, // ✅
        )
    }
}

// SyncSettingsDialog (audio/subtitle sync)
Surface(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 400.dp)
        .testTag("dialog_sync_settings") // ✅
        .semantics { contentDescription = title }, // ✅
)
```

**testTag ajoutés** :
- `dialog_player_settings` - Dialog de qualité
- `dialog_audio_selection` - Dialog de piste audio
- `dialog_subtitle_selection` - Dialog de sous-titres
- `dialog_speed_selection` - Dialog de vitesse de lecture
- `dialog_sync_settings` - Dialog de synchronisation audio/subtitle

**contentDescription ajoutés** :
- Settings : `"Paramètres de qualité"`
- Audio : `"Select Audio"`
- Subtitle : `"Select Subtitles"`
- Speed : `"Playback Speed"`
- Sync : `"{title}"` (Audio Sync / Subtitle Sync)

---

### 13. SkipMarkerButton.kt (`:app:feature:player:ui:components`)

**Impact** : Moyenne — bouton "Passer l'intro" / "Passer les crédits"

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Surface du bouton
androidx.compose.material3.Surface(
    onClick = onSkip,
    shape = RoundedCornerShape(8.dp),
    color = if (isFocused) buttonColor else buttonColor.copy(alpha = 0.8f),
    modifier = Modifier
        .testTag("skip_marker_${markerType}") // ✅ Dynamique : intro/credits
        .semantics { contentDescription = displayText } // ✅ "Passer l'intro"/"Passer les crédits"
        .scale(scale),
    interactionSource = interactionSource,
)
```

**testTag ajoutés** :
- `skip_marker_{type}` - Bouton de skip (ex: `skip_marker_intro`, `skip_marker_credits`)

**contentDescription ajoutés** :
- Button : `"Passer l'intro"` / `"Passer les crédits"` (selon le type)

---

### 14. PerformanceOverlay.kt (`:app:feature:player:ui:components`)

**Impact** : Basse — overlay de statistiques de performance (nerd stats)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = modifier
        .testTag("player_performance_overlay") // ✅
        .semantics { contentDescription = "Statistiques de performance" } // ✅
        .padding(16.dp)
        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
        .padding(12.dp),
)
```

**testTag ajoutés** :
- `player_performance_overlay` - Overlay de stats

**contentDescription ajoutés** :
- Overlay : `"Statistiques de performance"`

---

### 15. SeasonDetailScreen.kt (`:app:feature:details`)

**Impact** : Haute — écran de détails de saison avec liste d'épisodes

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .testTag("screen_season_detail") // ✅
        .semantics { contentDescription = "Écran de détails de saison" } // ✅
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("season_loading") // ✅
        .semantics { contentDescription = "Chargement des épisodes" }, // ✅
    contentAlignment = Alignment.Center
)

// Error state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("season_error") // ✅
        .semantics { contentDescription = "Erreur: ${state.error}" }, // ✅
    contentAlignment = Alignment.Center
)

// Empty state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("season_empty") // ✅
        .semantics { contentDescription = "Aucun épisode trouvé" }, // ✅
    contentAlignment = Alignment.Center
)

// Episodes list
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .testTag("episodes_list") // ✅
        .semantics { contentDescription = "Liste des épisodes" }, // ✅
    state = listState,
)

// Individual episode item
Row(
    modifier = Modifier
        .fillMaxWidth()
        .testTag("episode_item_${episode.ratingKey}") // ✅ ID unique
        .semantics { contentDescription = "Épisode ${episode.episodeIndex}: ${episode.title}" } // ✅
        .onFocusChanged { isFocused = it.isFocused }
        .scale(scale)
)
```

**testTag ajoutés** :
- `screen_season_detail` - Box principale
- `season_loading` - État de chargement
- `season_error` - État d'erreur
- `season_empty` - État vide
- `episodes_list` - LazyColumn des épisodes
- `episode_item_{ratingKey}` - Chaque épisode

**contentDescription ajoutés** :
- Screen : `"Écran de détails de saison"`
- Loading : `"Chargement des épisodes"`
- Error : `"Erreur: {message}"`
- Empty : `"Aucun épisode trouvé"`
- List : `"Liste des épisodes"`
- Episode : `"Épisode {index}: {titre}"`

---

### 16. LibrariesScreen.kt (`:app:feature:library`)

**Impact** : Haute — écran de bibliothèque (Films/Séries) avec filtres et tri

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale avec testTag dynamique
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .testTag(if (state.mediaType == MediaType.Movie) "screen_movies" else "screen_tvshows") // ✅
        .semantics {
            contentDescription = if (state.mediaType == MediaType.Movie) "Écran de films" else "Écran de séries" // ✅
        }
        .background(NetflixBlack),
)

// FilterButton avec testTag (signature modifiée)
@Composable
fun FilterButton(
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    testTag: String = "", // ✅ Nouveau paramètre
) {
    OutlinedButton(
        modifier = Modifier
            .height(32.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
    )
}

// Appels avec testTag
FilterButton(
    text = serverLabel,
    isActive = isServerFiltered,
    onClick = { onAction(LibraryAction.OpenServerFilter) },
    testTag = "library_filter_server" // ✅
)

FilterButton(
    text = genreLabel,
    isActive = isGenreFiltered,
    onClick = { onAction(LibraryAction.OpenGenreFilter) },
    testTag = "library_filter_genre" // ✅
)

FilterButton(
    text = state.currentSort ?: "Title",
    isActive = false,
    onClick = { onAction(LibraryAction.OpenSortDialog) },
    testTag = "library_sort_button" // ✅
)

// View mode switch
IconButton(
    onClick = {
        val newMode = if (state.viewMode == LibraryViewMode.Grid) LibraryViewMode.List else LibraryViewMode.Grid
        onAction(LibraryAction.ChangeViewMode(newMode))
    },
    modifier = Modifier.testTag("library_view_mode") // ✅
)
```

**testTag ajoutés** :
- `screen_movies` / `screen_tvshows` - Box principale (dynamique selon mediaType)
- `library_filter_server` - Bouton filtre serveur
- `library_filter_genre` - Bouton filtre genre
- `library_sort_button` - Bouton tri
- `library_view_mode` - Bouton changement de vue (Grid/List)

**contentDescription ajoutés** :
- Screen : `"Écran de films"` / `"Écran de séries"`
- FilterButton : Utilise le texte du bouton comme label

**Note** : Les items individuels héritent des tags de `NetflixMediaCard` (déjà fait au Batch 1).

---

### 17. SettingsScreen.kt (`:app:feature:settings`)

**Impact** : Haute — écran de paramètres de l'application

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// LazyColumn principale
LazyColumn(
    state = listState,
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .testTag("screen_settings") // ✅
        .semantics { contentDescription = "Écran des paramètres" }, // ✅
    contentPadding = PaddingValues(vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
)
```

**testTag ajoutés** :
- `screen_settings` - LazyColumn principale

**contentDescription ajoutés** :
- Screen : `"Écran des paramètres"`

**Note** : Les composants de paramètres individuels (SettingRow, SwitchSetting, etc.) pourraient être enrichis dans une itération future si besoin.

---

### 18. FavoritesScreen.kt (`:app:feature:favorites`)

**Impact** : Moyenne — écran de favoris/watchlist

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column principale
Column(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_favorites") // ✅
        .semantics { contentDescription = "Écran des favoris" } // ✅
        .background(NetflixBlack)
        .padding(start = 58.dp, end = 58.dp, top = 80.dp),
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("favorites_loading") // ✅
        .semantics { contentDescription = "Chargement des favoris" }, // ✅
    contentAlignment = Alignment.Center
)

// Empty state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("favorites_empty") // ✅
        .semantics { contentDescription = "Aucun favori" }, // ✅
    contentAlignment = Alignment.Center
)
```

**testTag ajoutés** :
- `screen_favorites` - Column principale
- `favorites_loading` - État de chargement
- `favorites_empty` - État vide

**contentDescription ajoutés** :
- Screen : `"Écran des favoris"`
- Loading : `"Chargement des favoris"`
- Empty : `"Aucun favori"`

**Note** : Les items individuels utilisent `MediaCard` qui a déjà les tags (Batch 1).

---

### 19. HistoryScreen.kt (`:app:feature:history`)

**Impact** : Moyenne — écran d'historique de visionnage

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column principale
Column(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_history") // ✅
        .semantics { contentDescription = "Écran de l'historique" } // ✅
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 80.dp),
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("history_loading") // ✅
        .semantics { contentDescription = "Chargement de l'historique" }, // ✅
    contentAlignment = Alignment.Center
)

// Empty state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("history_empty") // ✅
        .semantics { contentDescription = "Aucun historique" }, // ✅
    contentAlignment = Alignment.Center
)
```

**testTag ajoutés** :
- `screen_history` - Column principale
- `history_loading` - État de chargement
- `history_empty` - État vide

**contentDescription ajoutés** :
- Screen : `"Écran de l'historique"`
- Loading : `"Chargement de l'historique"`
- Empty : `"Aucun historique"`

**Note** : Les items individuels utilisent `MediaCard` qui a déjà les tags (Batch 1).

---

### 20. DownloadsScreen.kt (`:app:feature:downloads`)

**Impact** : Moyenne — écran de gestion des téléchargements

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .testTag("screen_downloads") // ✅
        .semantics { contentDescription = "Écran des téléchargements" } // ✅
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("downloads_loading") // ✅
        .semantics { contentDescription = "Chargement des téléchargements" }, // ✅
    contentAlignment = Alignment.Center
)

// Empty state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("downloads_empty") // ✅
        .semantics { contentDescription = "Aucun téléchargement" }, // ✅
    contentAlignment = Alignment.Center
)

// Downloads list
LazyColumn(
    state = listState,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
        .testTag("downloads_list") // ✅
        .semantics { contentDescription = "Liste des téléchargements" } // ✅
)

// Individual download item
Row(
    modifier = Modifier
        .fillMaxWidth()
        .testTag("download_item_${item.id}") // ✅ ID unique
        .semantics { contentDescription = "Téléchargement: ${item.title}" } // ✅
        .onFocusChanged { isFocused = it.isFocused }
        .scale(scale)
)
```

**testTag ajoutés** :
- `screen_downloads` - Box principale
- `downloads_loading` - État de chargement
- `downloads_empty` - État vide
- `downloads_list` - LazyColumn des téléchargements
- `download_item_{id}` - Chaque item téléchargé

**contentDescription ajoutés** :
- Screen : `"Écran des téléchargements"`
- Loading : `"Chargement des téléchargements"`
- Empty : `"Aucun téléchargement"`
- List : `"Liste des téléchargements"`
- Item : `"Téléchargement: {titre}"`

---

### 21. SplashScreen.kt (`:app:feature:splash`)

**Impact** : Critique — écran de démarrage avec vidéo (ExoPlayer)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_splash") // ✅
        .semantics { contentDescription = "Écran de démarrage" } // ✅
        .background(Color.Black)
)
```

**testTag ajoutés** :
- `screen_splash` - Box principale

**contentDescription ajoutés** :
- Screen : `"Écran de démarrage"`

---

### 22. AuthScreen.kt (`:app:feature:auth`)

**Impact** : Critique — écran d'authentification Plex (PIN flow)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_auth") // ✅
        .semantics { contentDescription = "Écran d'authentification" } // ✅
        .background(NetflixBlack)
        .padding(32.dp)
)

// TextField token
OutlinedTextField(
    value = state.manualToken,
    onValueChange = { onAction(AuthAction.UpdateManualToken(it)) },
    modifier = Modifier
        .fillMaxWidth()
        .testTag("auth_token_field") // ✅
        .semantics { contentDescription = "Champ de saisie du token Plex" } // ✅
        .focusRequester(tokenFieldFocusRequester)
)

// Login button
Button(
    onClick = { onAction(AuthAction.LoginWithToken) },
    enabled = state.manualToken.isNotBlank() && !state.isLoading,
    modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("auth_login_button") // ✅
        .semantics { contentDescription = "Bouton de connexion" } // ✅
)

// PIN state (QuickConnect)
Column(
    modifier = Modifier
        .fillMaxSize()
        .testTag("auth_pin_state") // ✅
        .semantics { contentDescription = "Code PIN: ${state.pin}" }, // ✅
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
)

// Error state
Column(
    modifier = Modifier
        .fillMaxSize()
        .testTag("auth_error_state") // ✅
        .semantics { contentDescription = "Erreur: ${state.errorMessage}" }, // ✅
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
)

// Success state
Column(
    modifier = Modifier
        .fillMaxSize()
        .testTag("auth_success_state") // ✅
        .semantics { contentDescription = "Connexion réussie" }, // ✅
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
)
```

**testTag ajoutés** :
- `screen_auth` - Box principale
- `auth_token_field` - Champ de saisie du token
- `auth_login_button` - Bouton de connexion
- `auth_pin_state` - État PIN (QuickConnect)
- `auth_error_state` - État d'erreur
- `auth_success_state` - État de succès

**contentDescription ajoutés** :
- Screen : `"Écran d'authentification"`
- Token field : `"Champ de saisie du token Plex"`
- Login button : `"Bouton de connexion"`
- PIN state : `"Code PIN: {pin}"`
- Error state : `"Erreur: {message}"`
- Success state : `"Connexion réussie"`

---

### 23. ProfileScreen.kt (`:app:feature:auth:profiles`)

**Impact** : Haute — écran de sélection de profil Plex Home

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_profile") // ✅
        .semantics { contentDescription = "Écran de sélection de profil" } // ✅
        .background(MaterialTheme.colorScheme.background)
)

// Loading state
CircularProgressIndicator(
    modifier = Modifier
        .padding(32.dp)
        .testTag("profile_loading") // ✅
        .semantics { contentDescription = "Chargement des profils" } // ✅
)

// Error state
Text(
    text = state.error,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier
        .padding(16.dp)
        .testTag("profile_error") // ✅
        .semantics { contentDescription = "Erreur: ${state.error}" } // ✅
)

// Profile list
LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Adaptive(160.dp),
    modifier = Modifier
        .widthIn(max = 800.dp)
        .testTag("profile_list") // ✅
        .semantics { contentDescription = "Liste des profils" } // ✅
)

// Individual profile card
Column(
    modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .testTag("profile_card_${user.id}") // ✅ ID unique
        .semantics { contentDescription = "Profil: ${user.title}" } // ✅
        .clickable { onClick() }
)

// PIN dialog
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .testTag("profile_pin_dialog") // ✅
        .semantics { contentDescription = "Dialogue de saisie du code PIN" } // ✅
)

// Switching overlay
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("profile_switching") // ✅
        .semantics { contentDescription = "Changement de profil en cours" } // ✅
        .background(Color.Black.copy(alpha = 0.5f))
)
```

**testTag ajoutés** :
- `screen_profile` - Box principale
- `profile_loading` - État de chargement
- `profile_error` - État d'erreur
- `profile_list` - Grille de profils
- `profile_card_{id}` - Chaque profil
- `profile_pin_dialog` - Dialog de saisie PIN
- `profile_switching` - Overlay de changement de profil

**contentDescription ajoutés** :
- Screen : `"Écran de sélection de profil"`
- Loading : `"Chargement des profils"`
- Error : `"Erreur: {message}"`
- List : `"Liste des profils"`
- Card : `"Profil: {nom}"`
- PIN dialog : `"Dialogue de saisie du code PIN"`
- Switching : `"Changement de profil en cours"`

---

### 24. CollectionDetailScreen.kt (`:app:feature:collection`)

**Impact** : Moyenne — écran de détails d'une collection

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_collection_detail") // ✅
        .semantics { contentDescription = "Écran de détails de collection" } // ✅
        .padding(paddingValues)
        .background(Color.Black)
)

// Loading state
CircularProgressIndicator(
    modifier = Modifier
        .align(Alignment.Center)
        .testTag("collection_loading") // ✅
        .semantics { contentDescription = "Chargement de la collection" } // ✅
)

// Error state
Text(
    text = state.error,
    color = Color.Red,
    modifier = Modifier
        .align(Alignment.Center)
        .testTag("collection_error") // ✅
        .semantics { contentDescription = "Erreur: ${state.error}" } // ✅
)

// Items list
LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Adaptive(minSize = 120.dp),
    modifier = Modifier
        .fillMaxSize()
        .testTag("collection_items_list") // ✅
        .semantics { contentDescription = "Liste des éléments de la collection" } // ✅
)
```

**testTag ajoutés** :
- `screen_collection_detail` - Box principale
- `collection_loading` - État de chargement
- `collection_error` - État d'erreur
- `collection_items_list` - Grille d'items

**contentDescription ajoutés** :
- Screen : `"Écran de détails de collection"`
- Loading : `"Chargement de la collection"`
- Error : `"Erreur: {message}"`
- List : `"Liste des éléments de la collection"`

---

### 25. HubDetailScreen.kt (`:app:feature:hub`)

**Impact** : Haute — écran de détails d'un hub (ex: Recently Added, Continue Watching)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Box principale
Box(
    modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .testTag("screen_hub_detail") // ✅
        .semantics { contentDescription = "Écran de détails du hub" } // ✅
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("hub_loading") // ✅
        .semantics { contentDescription = "Chargement du hub" }, // ✅
    contentAlignment = Alignment.Center
)

// Error state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("hub_error") // ✅
        .semantics { contentDescription = "Erreur: ${state.error}" }, // ✅
    contentAlignment = Alignment.Center
)

// Empty state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("hub_empty") // ✅
        .semantics { contentDescription = "Aucun élément trouvé" }, // ✅
    contentAlignment = Alignment.Center
)

// Grid view
LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Adaptive(minSize = 150.dp),
    modifier = Modifier
        .testTag("hub_items_grid") // ✅
        .semantics { contentDescription = "Grille des éléments du hub" } // ✅
)

// List view
LazyVerticalGrid(
    state = listState,
    columns = GridCells.Fixed(1),
    modifier = Modifier
        .testTag("hub_items_list") // ✅
        .semantics { contentDescription = "Liste des éléments du hub" } // ✅
)
```

**testTag ajoutés** :
- `screen_hub_detail` - Box principale
- `hub_loading` - État de chargement
- `hub_error` - État d'erreur
- `hub_empty` - État vide
- `hub_items_grid` - Vue grille
- `hub_items_list` - Vue liste

**contentDescription ajoutés** :
- Screen : `"Écran de détails du hub"`
- Loading : `"Chargement du hub"`
- Error : `"Erreur: {message}"`
- Empty : `"Aucun élément trouvé"`
- Grid : `"Grille des éléments du hub"`
- List : `"Liste des éléments du hub"`

---

### 26. IptvScreen.kt (`:app:feature:iptv`)

**Impact** : Moyenne — écran de gestion des chaînes IPTV (Live TV)

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Column principale
Column(
    modifier = Modifier
        .padding(padding)
        .testTag("screen_iptv") // ✅
        .semantics { contentDescription = "Écran IPTV" } // ✅
)

// Loading state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("iptv_loading") // ✅
        .semantics { contentDescription = "Chargement des chaînes IPTV" }, // ✅
    contentAlignment = Alignment.Center
)

// Error state
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("iptv_error") // ✅
        .semantics { contentDescription = "Erreur: ${state.error}" }, // ✅
    contentAlignment = Alignment.Center
)

// Channels list
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .testTag("iptv_channels_list") // ✅
        .semantics { contentDescription = "Liste des chaînes IPTV" } // ✅
        .focusRequester(channelListFocusRequester)
)

// Individual channel
Card(
    onClick = onClick,
    modifier = Modifier
        .fillMaxWidth()
        .testTag("iptv_channel_${channel.name}") // ✅ ID unique
        .semantics { contentDescription = "Chaîne: ${channel.name}" } // ✅
        .onFocusChanged { isFocused = it.isFocused }
)

// URL dialog
AlertDialog(
    onDismissRequest = { onEvent(IptvEvent.DismissUrlDialog) },
    modifier = Modifier
        .testTag("iptv_url_dialog") // ✅
        .semantics { contentDescription = "Dialogue de saisie d'URL M3U" } // ✅
)
```

**testTag ajoutés** :
- `screen_iptv` - Column principale
- `iptv_loading` - État de chargement
- `iptv_error` - État d'erreur
- `iptv_channels_list` - Liste de chaînes
- `iptv_channel_{name}` - Chaque chaîne
- `iptv_url_dialog` - Dialog de saisie d'URL

**contentDescription ajoutés** :
- Screen : `"Écran IPTV"`
- Loading : `"Chargement des chaînes IPTV"`
- Error : `"Erreur: {message}"`
- List : `"Liste des chaînes IPTV"`
- Channel : `"Chaîne: {nom}"`
- Dialog : `"Dialogue de saisie d'URL M3U"`

---

### 27. LoadingScreen.kt (`:app:feature:loading`)

**Impact** : Haute — écran de chargement initial avec progression

**Modifications** :

```kotlin
// Imports ajoutés
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Surface principale
Surface(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_loading") // ✅
        .semantics { contentDescription = "Écran de chargement" }, // ✅
    color = MaterialTheme.colorScheme.background
)

// Loading state (progress indicator)
CircularProgressIndicator(
    modifier = Modifier
        .testTag("loading_progress") // ✅
        .semantics { contentDescription = "Chargement en cours: ${state.progress.toInt()}%" } // ✅
)

// Error state
Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = "Error",
    modifier = Modifier
        .testTag("loading_error") // ✅
        .semantics { contentDescription = "Erreur de chargement: ${state.message}" } // ✅
)

// Completed state
Text(
    "Chargement terminé !",
    modifier = Modifier
        .testTag("loading_completed") // ✅
        .semantics { contentDescription = "Chargement terminé" } // ✅
)
```

**testTag ajoutés** :
- `screen_loading` - Surface principale
- `loading_progress` - Indicateur de progression
- `loading_error` - État d'erreur
- `loading_completed` - État de complétion

**contentDescription ajoutés** :
- Screen : `"Écran de chargement"`
- Progress : `"Chargement en cours: {percent}%"`
- Error : `"Erreur de chargement: {message}"`
- Completed : `"Chargement terminé"`

---

## Guide de migration pour les fichiers restants

### Fichiers prioritaires à traiter ensuite :

#### Batch 4 - Écrans secondaires ✅ COMPLÉTÉ
1. **SeasonDetailScreen.kt** (`app/feature/details`) ✅
   - 6 testTag : `screen_season_detail`, `season_loading`, `season_error`, `season_empty`, `episodes_list`, `episode_item_{ratingKey}`
   - 6 contentDescription

2. **LibrariesScreen.kt** (`app/feature/library`) ✅
   - 5 testTag : `screen_movies`/`screen_tvshows`, `library_filter_server`, `library_filter_genre`, `library_sort_button`, `library_view_mode`
   - 2 contentDescription

3. **SettingsScreen.kt** (`app/feature/settings`) ✅
   - 1 testTag : `screen_settings`
   - 1 contentDescription

4. **FavoritesScreen.kt** (`app/feature/favorites`) ✅
   - 3 testTag : `screen_favorites`, `favorites_loading`, `favorites_empty`
   - 3 contentDescription

5. **HistoryScreen.kt** (`app/feature/history`) ✅
   - 3 testTag : `screen_history`, `history_loading`, `history_empty`
   - 3 contentDescription

6. **DownloadsScreen.kt** (`app/feature/downloads`) ✅
   - 5 testTag : `screen_downloads`, `downloads_loading`, `downloads_empty`, `downloads_list`, `download_item_{id}`
   - 5 contentDescription

#### Batch 5 - Auth & autres ✅ COMPLÉTÉ
1. **SplashScreen.kt** (`app/feature/splash`) ✅
   - 1 testTag : `screen_splash`
   - 1 contentDescription

2. **AuthScreen.kt** (`app/feature/auth`) ✅
   - 6 testTag : `screen_auth`, `auth_token_field`, `auth_login_button`, `auth_pin_state`, `auth_error_state`, `auth_success_state`
   - 6 contentDescription

3. **ProfileScreen.kt** (`app/feature/auth/profiles`) ✅
   - 7 testTag : `screen_profile`, `profile_loading`, `profile_error`, `profile_list`, `profile_card_{id}`, `profile_pin_dialog`, `profile_switching`
   - 7 contentDescription

4. **CollectionDetailScreen.kt** (`app/feature/collection`) ✅
   - 4 testTag : `screen_collection_detail`, `collection_loading`, `collection_error`, `collection_items_list`
   - 4 contentDescription

5. **HubDetailScreen.kt** (`app/feature/hub`) ✅
   - 6 testTag : `screen_hub_detail`, `hub_loading`, `hub_error`, `hub_empty`, `hub_items_grid`, `hub_items_list`
   - 6 contentDescription

6. **IptvScreen.kt** (`app/feature/iptv`) ✅
   - 6 testTag : `screen_iptv`, `iptv_loading`, `iptv_error`, `iptv_channels_list`, `iptv_channel_{name}`, `iptv_url_dialog`
   - 6 contentDescription

7. **LoadingScreen.kt** (`app/feature/loading`) ✅
   - 4 testTag : `screen_loading`, `loading_progress`, `loading_error`, `loading_completed`
   - 4 contentDescription

---

## Pattern de modification recommandé

### Étape 1 : Ajouter les imports

```kotlin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

### Étape 2 : Ajouter testTag + semantics dans le Modifier

**Pour un composant simple :**
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .testTag("screen_home") // ✅ AVANT focusable/clickable
        .semantics { contentDescription = "Écran d'accueil" } // ✅
        .focusable()
)
```

**Pour une liste (LazyRow/LazyColumn) :**
```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .testTag("episodes_list")
        .semantics { contentDescription = "Liste des épisodes" }
) {
    items(
        items = episodes,
        key = { it.ratingKey }
    ) { episode ->
        EpisodeCard(
            episode = episode,
            modifier = Modifier
                .testTag("episode_item_${episode.ratingKey}") // ✅ Tag dans l'item
                .semantics { contentDescription = "Épisode ${episode.index}: ${episode.title}" }
        )
    }
}
```

**Pour un Button/IconButton :**
```kotlin
Button(
    onClick = { onPlay() },
    modifier = Modifier
        .testTag("play_button") // ✅ AVANT focusRequester
        .semantics { contentDescription = "Lancer la lecture" }
        .focusRequester(playFocusRequester)
) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = null, // ✅ null car semantics du Button suffit
    )
}
```

**Pour une Image :**
```kotlin
AsyncImage(
    model = media.thumbUrl,
    contentDescription = "Affiche de ${media.title}", // ✅ contentDescription natif
    modifier = Modifier
        .fillMaxSize()
        .testTag("media_poster_${media.ratingKey}") // ✅ Tag séparé
)
```

### Étape 3 : Placement dans la chaîne Modifier

**Ordre recommandé** :
1. `.size()`, `.width()`, `.height()` (dimensions)
2. `.testTag()` ⬅️ **ICI** (avant interactions)
3. `.semantics {}` ⬅️ **ICI**
4. `.focusable()`, `.clickable()`, `.focusRequester()` (interactions)
5. `.padding()`, `.background()`, `.border()` (styling)

**Exemple complet :**
```kotlin
Card(
    modifier = Modifier
        .width(150.dp)
        .height(225.dp)
        .testTag("media_card_${media.ratingKey}") // 1️⃣ Tag
        .semantics { contentDescription = "Film: ${media.title}" } // 2️⃣ Description
        .focusable() // 3️⃣ Interactions
        .onFocusChanged { ... }
        .padding(8.dp) // 4️⃣ Styling
        .border(2.dp, borderColor)
)
```

---

## Règles impératives

### ✅ À FAIRE
1. **Placer `.testTag()` AVANT `.focusable()`** dans la chaîne Modifier
2. **Utiliser des IDs dynamiques** pour les listes : `media_card_{ratingKey}`, `episode_item_{ratingKey}`
3. **Ajouter `contentDescription` en français** pour les éléments visuels sans texte (images, icônes)
4. **Préférer `.semantics {}` sur le parent** au lieu de `contentDescription` sur chaque enfant
5. **Mettre `contentDescription = null`** sur les Icons/Images enfants si le parent a déjà un semantics

### ❌ À ÉVITER
1. **NE PAS** remplacer un Modifier existant, toujours ajouter dans la chaîne
2. **NE PAS** dupliquer `contentDescription` (parent + enfant)
3. **NE PAS** oublier les imports (sinon erreur de compilation)
4. **NE PAS** utiliser des tags fixes pour des listes (`media_card` au lieu de `media_card_{id}`)
5. **NE PAS** casser la logique métier (navigation, états, etc.)

---

## Tests Maestro recommandés après migration

```yaml
# maestro/tests/home-navigation.yaml
appId: com.chakir.plexhubtv
---
# Test : Navigation dans le menu
- tapOn:
    id: "nav_item_home"
- assertVisible:
    id: "screen_home"
- tapOn:
    id: "nav_item_movies"
- assertVisible:
    id: "screen_movies"

# Test : Lecture d'un film depuis Home
- tapOn:
    id: "screen_home"
- tapOn:
    id: "hero_play_button"
- assertVisible:
    id: "screen_player"
- assertVisible:
    id: "player_controls_overlay"

# Test : Clic sur une card média
- tapOn:
    id: "media_row_on_deck"
- tapOn:
    id: "media_card_12345"  # Remplacer par un vrai ratingKey
- assertVisible:
    id: "screen_media_detail"
- assertVisible:
    id: "play_button"
```

---

## Prochaines étapes

1. **Traiter les fichiers du Batch 1** (NetflixHomeScreen, MediaDetailScreen, NetflixSearchScreen)
2. **Traiter le Batch 2** (NetflixPlayerControls, TrackSelectionDialog, etc.)
3. **Compiler et vérifier** qu'il n'y a pas d'erreurs
4. **Écrire les premiers tests Maestro** sur les flows critiques
5. **Itérer** sur les autres écrans selon les besoins de tests

---

## Contact & Support

Pour toute question sur cette migration :
- Vérifier ce document en premier
- Consulter les exemples de modification dans les 5 fichiers déjà traités
- Suivre le pattern de modification recommandé

**Dernière mise à jour** : 14 février 2026
