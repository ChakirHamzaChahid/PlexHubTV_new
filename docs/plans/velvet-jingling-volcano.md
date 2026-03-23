# Plan : Amélioration sélection serveurs (v2 — révisé)

## Contexte

Trois améliorations autour de la gestion des serveurs :

1. **Navigation lente** sur `LibrarySelectionScreen` (choix bibliothèques au démarrage). Avec 5-10 serveurs × 3-5 bibliothèques = 15-50 items, la navigation D-pad est fastidieuse sans moyen de sauter entre serveurs.
2. **Bug "All servers"** : le dialog de serveur par défaut ne propose que les noms de serveurs — pas d'option "Tous les serveurs". Impossible de revenir à `"all"` après avoir choisi un serveur spécifique.
3. **Audit du filtre** : vérifier que `defaultServer` est reflété dans home, search, continue watching.

---

## Tâche 1 : Sidebar jump-to-server sur LibrarySelectionScreen

**Fichier** : [LibrarySelectionScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/libraryselection/LibrarySelectionScreen.kt)

**Pattern réutilisé** : [AlphabetSidebar.kt](app/src/main/java/com/chakir/plexhubtv/feature/library/AlphabetSidebar.kt) — même pattern de sidebar focusable avec `onFocusChanged` + `clickable`.

**Objectif** : une colonne à droite avec les noms de serveurs. Cliquer sur un nom scrolle la `LazyColumn` jusqu'au header de ce serveur. Permet de "descendre plus vite" comme demandé.

### Changements :

**1a. Ajouter `rememberLazyListState()`** (avant la `LazyColumn`, ligne ~155) :
```kotlin
val lazyListState = rememberLazyListState()
```
Passer `state = lazyListState` au `LazyColumn`.

**1b. Calculer l'index des headers serveur** — chaque serveur a un header item suivi de N items bibliothèque. L'index du header de chaque serveur = cumul des items précédents :
```kotlin
val serverHeaderIndices = remember(state.servers) {
    val indices = mutableMapOf<String, Int>()
    var idx = 0
    state.servers.forEach { server ->
        indices[server.serverId] = idx
        idx += 1 + server.libraries.size // 1 header + N libraries
    }
    indices
}
```

**1c. Wrapper `Row` autour du contenu** — `LazyColumn` à gauche (weight 1f), sidebar à droite :
```kotlin
Row(modifier = Modifier.weight(1f)) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.weight(1f),
        // ... existing params
    ) { /* ... existing items */ }

    // Sidebar — only show when 2+ servers
    if (state.servers.size >= 2) {
        ServerSidebar(
            servers = state.servers,
            onServerSelected = { serverId ->
                val idx = serverHeaderIndices[serverId] ?: 0
                coroutineScope.launch { lazyListState.animateScrollToItem(idx) }
            },
        )
    }
}
```
Note : ajouter `val coroutineScope = rememberCoroutineScope()` dans le composable.

**1d. Composable `ServerSidebar`** — directement dans `LibrarySelectionScreen.kt` (privé, pas de nouveau fichier) :
```kotlin
@Composable
private fun ServerSidebar(
    servers: List<ServerWithLibraries>,
    onServerSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        servers.forEach { server ->
            ServerSidebarItem(
                label = server.serverName.take(3).uppercase(), // "MYS", "NAS", etc.
                onClick = { onServerSelected(server.serverId) },
            )
        }
    }
}

@Composable
private fun ServerSidebarItem(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
```

**Imports additionnels** : `rememberLazyListState`, `rememberCoroutineScope`, `onFocusChanged`, `RoundedCornerShape`, `FontWeight`, `Row`

---

## Tâche 2 : Option "Tous les serveurs" dans le dialog serveur par défaut

### 2a. Strings (2 fichiers)

**[strings.xml](app/src/main/res/values/strings.xml)** (après ligne 518 `settings_default_server`) :
```xml
<string name="settings_all_servers">All servers</string>
```

**[strings.xml (FR)](app/src/main/res/values-fr/strings.xml)** (après ligne 478 `settings_default_server`) :
```xml
<string name="settings_all_servers">Tous les serveurs</string>
```

### 2b. SettingsViewModel — préparer la liste

**Fichier** : [SettingsViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt)

1. Ajouter constante sentinel :
```kotlin
companion object {
    const val ALL_SERVERS_SENTINEL = "all"
}
```

2. Dans `loadOneTimeData()` (ligne 635) — prépendre le sentinel :
```kotlin
val serverNames = listOf(ALL_SERVERS_SENTINEL) + servers.map { it.name }
```

L'action `SelectDefaultServer` ne change pas — elle stocke déjà la valeur telle quelle.

### 2c. ServerSettingsScreen — afficher le label localisé

**Fichier** : [ServerSettingsScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/categories/ServerSettingsScreen.kt)

1. **Subtitle du tile** (ligne 66) : mapper `"all"` → `stringResource(R.string.settings_all_servers)`
2. **Dialog** (lignes 130-141) : mapper sentinel ↔ label localisé dans les deux sens

```kotlin
val allServersLabel = stringResource(R.string.settings_all_servers)
val displayOptions = state.availableServers.map {
    if (it == SettingsViewModel.ALL_SERVERS_SENTINEL) allServersLabel else it
}
val displayValue = if (state.defaultServer == SettingsViewModel.ALL_SERVERS_SENTINEL)
    allServersLabel else state.defaultServer

SettingsDialog(
    options = displayOptions,
    currentValue = displayValue,
    onOptionSelected = { displayName ->
        val value = if (displayName == allServersLabel)
            SettingsViewModel.ALL_SERVERS_SENTINEL else displayName
        onAction(SettingsAction.SelectDefaultServer(value))
        showServerDialog = false
    },
)
```

**Séparation des responsabilités** : le mapping localisation reste dans la couche UI (composable), le ViewModel ne touche pas aux string resources. `LibraryViewModel` l.240 gère déjà `"all"` via `takeIf { it != "all" }` — pas de changement.

---

## Tâche 3 : Audit — `defaultServer` reflété partout ?

| Écran | Utilise `defaultServer` ? | Mécanisme réel | Correct ? |
|-------|--------------------------|----------------|-----------|
| **Library** | OUI | Fallback `defaultServer` si pas de filtre library-specific (l.240) | Correct |
| **Home** | NON | `excludedServerIds` = visibilité globale des serveurs | Correct par design |
| **Search** | NON | Recherche sur tous les serveurs non-exclus | Correct — recherche exhaustive |
| **OnDeck** | NON | `selectedLibraryIds` = sélection per-bibliothèque | Correct |

**Architecture intentionnelle** : deux mécanismes distincts :
- `defaultServer` = préférence de navigation Library (quel serveur afficher par défaut dans la grille)
- `excludedServerIds` = visibilité globale (quels serveurs sont actifs dans l'app)

Pas de changement nécessaire. Pas de bug, c'est une séparation de concerns correcte.

---

## Fichiers impactés

| Fichier | Changement |
|---------|-----------|
| `app/.../libraryselection/LibrarySelectionScreen.kt` | `ServerSidebar` + `LazyListState` + `animateScrollToItem` |
| `app/.../settings/SettingsViewModel.kt` | Constante `ALL_SERVERS_SENTINEL`, prépendre dans `loadOneTimeData()` |
| `app/.../settings/categories/ServerSettingsScreen.kt` | Mapper sentinel ↔ label localisé |
| `app/src/main/res/values/strings.xml` | +1 string `settings_all_servers` |
| `app/src/main/res/values-fr/strings.xml` | +1 string `settings_all_servers` |

Pas de nouveau fichier — `ServerSidebar` est privé dans `LibrarySelectionScreen.kt`.

---

## Vérification

1. **Sidebar serveurs** : Settings → Manage Libraries → vérifier sidebar à droite avec initiales des serveurs. Focus un serveur avec D-pad → la liste scrolle jusqu'à ce serveur. Sidebar masquée si un seul serveur.
2. **All servers** : Settings → Server → Default Server → "All servers" en tête de liste. Le sélectionner, quitter, revérifier subtitle = "All servers" / "Tous les serveurs"
3. **Persistence** : Fermer/rouvrir l'app → choix "All servers" persiste
4. **Library** : Après "All servers" comme défaut → Library en mode unifié (tous serveurs)
5. **Build** : `./gradlew assembleDebug` sans erreur
