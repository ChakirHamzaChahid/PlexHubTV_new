# Plan : 4 Features Player Wholphin Parity

## Contexte

PlexHubTV a rattrape Wholphin sur 12/15 features. Il reste 5 features exclusives a Wholphin dont 4 sont demandees ici :
1. **Vue Chapitres overlay** — Navigation visuelle des chapitres dans le player
2. **Vue Queue/Playlist** — File d'attente visible pendant la lecture
3. **Scale player quand NextUp** — Reduire le player quand le popup auto-next apparait
4. **Bouton More/Menu** — Menu regroupant les actions secondaires

## Ordre d'implementation

**D (More Menu) → A (Chapters) → B (Queue) → C (Scale)**

Le More Menu en premier car il cree les hooks pour les overlays chapitres et queue.

---

## Feature D — Bouton More/Menu

### Concept
Remplacer le bouton **Settings** (gear) dans la barre de transport par un bouton **More** (trois points). Le menu More regroupe les actions secondaires : Quality, Speed, Subtitle/Audio Sync, Download Subtitles, Equalizer, Performance Stats + les entrees Chapters et Queue (hooks pour features A et B). Les boutons **Subtitles** et **Audio** restent visibles car tres utilises.

### Fichiers a modifier

| Fichier | Changement |
|---------|------------|
| `feature/player/PlayerUiState.kt` | Ajouter `showMoreMenu: Boolean = false` + actions `ToggleMoreMenu`, `ShowChapterOverlay`, `ShowQueueOverlay`, `SeekToChapter(chapter)`, `PlayQueueItem(index)` |
| `feature/player/PlayerControlViewModel.kt` | Handler `ToggleMoreMenu` (toggle + clear showSettings), handlers pour les nouvelles actions, update `DismissDialog` pour clear `showMoreMenu` |
| `feature/player/components/NetflixPlayerControls.kt` | Remplacer `onShowSettings` par `onShowMore` callback, remplacer `Icons.Default.Settings` par `Icons.Default.MoreVert` (ligne 312-318) |
| `feature/player/VideoPlayerScreen.kt` | Passer `onShowMore` a NetflixPlayerControls, ajouter rendu conditionnel `PlayerMoreMenu` |
| `res/values/strings.xml` | Strings EN : `player_more`, `player_more_quality`, `player_more_speed`, etc. |
| `res/values-fr/strings.xml` | Traductions FR |

### Fichier a creer

**`feature/player/ui/components/PlayerMoreMenu.kt`**

```
PlayerMoreMenu(
    hasChapters: Boolean,
    hasQueue: Boolean,
    showPerformanceOverlay: Boolean,
    onShowSettings: () -> Unit,      // Quality (reuse ToggleSettings)
    onShowSpeed: () -> Unit,
    onShowSubtitleSync: () -> Unit,
    onShowAudioSync: () -> Unit,
    onShowSubtitleDownload: () -> Unit,
    onShowEqualizer: () -> Unit,
    onToggleStats: () -> Unit,
    onShowChapters: () -> Unit,      // Hook Feature A
    onShowQueue: () -> Unit,         // Hook Feature B
    onDismiss: () -> Unit,
)
```

- Style : Panel droit (`Alignment.CenterEnd`), 320dp wide, fond `Color(0xFF1A1A1A)` (coherent avec `PlayerSettingsDialog`)
- `LazyColumn` de rows focusables avec icone + label
- Animation : `slideInHorizontally` from right + `fadeIn`
- Focus : Premier item auto-focus via `FocusRequester`
- Items conditionnels : Chapters seulement si `hasChapters`, Queue seulement si `hasQueue`
- Performance Stats : toggle avec checkmark si actif

---

## Feature A — Vue Chapitres Overlay

### Concept
Overlay plein ecran avec fond semi-transparent, montrant les chapitres dans un `LazyRow` horizontal de cartes avec thumbnail + titre + timestamp. Le chapitre actuel est surligne. Cliquer sur un chapitre y seek directement.

### Fichiers a modifier

| Fichier | Changement |
|---------|------------|
| `feature/player/PlayerUiState.kt` | Ajouter `showChapterOverlay: Boolean = false` (actions deja ajoutees en Feature D) |
| `feature/player/PlayerControlViewModel.kt` | Handlers `ShowChapterOverlay` (set true + clear showMoreMenu), `SeekToChapter` (seekTo + clear overlay) |
| `feature/player/VideoPlayerScreen.kt` | Collecter `currentChapter` depuis `chapterMarkerManager.currentChapter`, ajouter `AnimatedVisibility` pour `ChapterOverlay`, update `DismissDialog` |

### Fichier a creer

**`feature/player/ui/components/ChapterOverlay.kt`**

```
ChapterOverlay(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    currentPosition: Long,
    onSelectChapter: (Chapter) -> Unit,
    onDismiss: () -> Unit,
)
```

Layout :
```
[X] Chapitres (12)
┌────────┐ ┌────────┐ ┌─*──────*┐ ┌────────┐
│ thumb  │ │ thumb  │ │ thumb  │ │ thumb  │  ← LazyRow horizontal
│        │ │        │ │ border │ │        │
└────────┘ └────────┘ └────────┘ └────────┘
 Ch. 1      Ch. 2     ►Ch. 3     Ch. 4
 0:00       3:42       7:15      12:30
```

- Fond : `Color.Black.copy(alpha = 0.85f)` fullscreen
- Header : titre + bouton close (comme dans `PlayerSettingsDialog`)
- Cartes chapitres : `200dp x 112dp` (16:9), `RoundedCornerShape(8.dp)`, `AsyncImage(thumbUrl)`
- Chapitre actuel : bordure `MaterialTheme.colorScheme.primary` 2dp
- Focus : `collectIsFocusedAsState`, scale 1.05f, auto-scroll vers chapitre actuel
- Animation : `slideInVertically(from bottom) + fadeIn`
- D-Pad : Left/Right navigue, Enter selectionne, Back ferme
- Fallback sans thumbnail : fond sombre + titre centre

---

## Feature B — Vue Queue/Playlist dans Player

### Concept
Panel lateral droit montrant la file d'attente. Affiche tous les items de `PlaybackManager.playQueue` avec l'item actuel surligne. Cliquer sur un item saute directement dessus.

### Fichiers a modifier

| Fichier | Changement |
|---------|------------|
| `feature/player/PlayerUiState.kt` | Ajouter `showQueueOverlay: Boolean = false`, `playQueue: List<MediaItem> = emptyList()`, `currentQueueIndex: Int = -1` |
| `feature/player/PlayerControlViewModel.kt` | Collecter `playbackManager.state` et propager vers UiState (`playQueue`, `currentQueueIndex`). Handler `ShowQueueOverlay`, `PlayQueueItem(index)` |
| `feature/player/VideoPlayerScreen.kt` | Ajouter `AnimatedVisibility` pour `QueueOverlay` aligne `CenterEnd` |

### Fichier a creer

**`feature/player/ui/components/QueueOverlay.kt`**

```
QueueOverlay(
    queue: List<MediaItem>,
    currentIndex: Int,
    onSelectItem: (Int) -> Unit,
    onDismiss: () -> Unit,
)
```

Layout :
```
File d'attente (8)              [X]
─────────────────────────────────
 [thumb] S1E1 - Pilot     45min
 [thumb] S1E2 - The Wall  42min
 [thumb] ► S1E3 - Playing  44min  ← highlighted
 [thumb] S1E4 - The Gift  41min
─────────────────────────────────
```

- Panel droit : `380dp` wide, `fillMaxHeight`, fond `Color(0xFF1A1A1A).copy(alpha = 0.95f)`
- Shape : `RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)`
- `LazyColumn` de rows : thumbnail `80x45dp` + titre + duree
- Item actuel : fond `primary.copy(alpha = 0.15f)`, label "En cours" avec dot couleur primary
- Animation : `slideInHorizontally(from right) + fadeIn`
- Focus : auto-scroll + focus sur item actuel, Up/Down navigue, Enter joue, Left/Back ferme
- Condition : seulement visible si `playQueue.size > 1`

### PlayQueueItem action handler
```kotlin
is PlayerAction.PlayQueueItem -> {
    val state = playbackManager.state.value
    if (action.index in state.playQueue.indices && action.index != state.currentIndex) {
        val target = state.playQueue[action.index]
        // Mettre a jour PlaybackManager
        playbackManager.play(target, state.playQueue)
        // Charger le media
        loadOrPlayMedia(target)
        playerController.updateState { it.copy(showQueueOverlay = false) }
    }
}
```

---

## Feature C — Scale Player quand NextUp

### Concept
Quand `showAutoNextPopup` devient true, animer le player surface a 0.75x scale, donnant plus de place au popup next-up. Restaurer a 1.0x quand le popup est ferme.

### Fichier a modifier

**`feature/player/VideoPlayerScreen.kt`** uniquement — changement purement UI.

```kotlin
// Calculer le scale anime
val shouldScale = uiState.showAutoNextPopup
    && uiState.nextItem != null
    && uiState.error == null

val playerScale by animateFloatAsState(
    targetValue = if (shouldScale) 0.75f else 1.0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    ),
    label = "playerScale",
)

// Wrapper autour du player surface
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = playerScale
            scaleY = playerScale
        }
) {
    // ExoPlayer ou MPV surface existante
}
```

- Pas de nouvelle action ni de nouveau state
- Animation spring ~400ms settle
- Ne pas scaler pendant erreur ou dialog ouverte
- Le AutoNextPopup garde sa position actuelle (TopEnd) — le player retrecit naturellement vers le centre

---

## Resume des fichiers

### Fichiers a modifier (6)

| Fichier | Features |
|---------|----------|
| `feature/player/PlayerUiState.kt` | D, A, B |
| `feature/player/PlayerControlViewModel.kt` | D, A, B |
| `feature/player/VideoPlayerScreen.kt` | D, A, B, C |
| `feature/player/components/NetflixPlayerControls.kt` | D |
| `app/src/main/res/values/strings.xml` | D, A, B |
| `app/src/main/res/values-fr/strings.xml` | D, A, B |

### Fichiers a creer (3)

| Fichier | Feature |
|---------|---------|
| `feature/player/ui/components/PlayerMoreMenu.kt` | D |
| `feature/player/ui/components/ChapterOverlay.kt` | A |
| `feature/player/ui/components/QueueOverlay.kt` | B |

### Tests a ajouter

`feature/player/PlayerControlViewModelTest.kt` — Tests pour :
- `ToggleMoreMenu` toggle state
- `ShowChapterOverlay` / `ShowQueueOverlay` set true + clear showMoreMenu
- `SeekToChapter` calls seekTo + clears overlay
- `PlayQueueItem` navigates queue + loads media
- `DismissDialog` clears tous les nouveaux booleans

## Verification

1. `./gradlew compileDebugKotlin` — compilation
2. `./gradlew :app:testDebugUnitTest` — tests unitaires
3. Test manuel :
   - Lancer une video avec chapitres → More → Chapters → naviguer → selectionner → seek OK
   - Lancer une serie (plusieurs episodes) → More → Queue → voir la liste → sauter a un episode
   - Laisser un episode finir → observer le player shrink + popup next-up
   - Verifier que le bouton More regroupe bien Quality/Speed/Sync/Download/Equalizer/Stats
