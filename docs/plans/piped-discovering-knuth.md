# Plan : Ecran de sélection des bibliothèques

## Contexte

Actuellement, **toutes** les bibliothèques (movies + shows) de **tous** les serveurs sont synchronisées automatiquement. L'utilisateur n'a aucun contrôle sur ce qui est synchronisé.

**Objectif** : Insérer un écran de sélection entre l'authentification et la synchronisation, permettant à l'utilisateur de choisir quelles bibliothèques synchroniser sur chaque serveur.

**Décisions UX** :
- **Tous les utilisateurs** (nouveaux ET existants) verront l'écran de sélection
- Seules les bibliothèques **Movies + Shows** sont affichées (pas musique/photo)

**Flux actuel** : `Login → Loading (auto-sync tout) → Main`
**Flux cible** : `Login → LibrarySelection → Loading (sync sélection uniquement) → Main`
**Flux existants** : `Splash → (token trouvé) → LibrarySelection → Loading → Main`

---

## Etape 1 : Persistance — DataStore + nouveau préférence

**Fichier** : `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`

Ajouter une nouvelle préférence `SELECTED_LIBRARY_IDS` de type `stringSetPreferencesKey`, suivant le pattern existant de `EXCLUDED_SERVER_IDS`.

```
Format: Set<String> avec des IDs composites "serverId:libraryKey"
Exemple: {"abc123:1", "abc123:4", "def456:2"}
```

Nouvelles propriétés :
- `val selectedLibraryIds: Flow<Set<String>>` — retourne l'ensemble des bibliothèques sélectionnées
- `suspend fun saveSelectedLibraryIds(ids: Set<String>)` — sauvegarde la sélection
- `val isLibrarySelectionComplete: Flow<Boolean>` — flag indiquant si l'utilisateur a déjà fait sa sélection (via `stringPreferencesKey`)
- `suspend fun saveLibrarySelectionComplete(complete: Boolean)` — marque la sélection comme faite

---

## Etape 2 : Route de navigation

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/di/navigation/Screen.kt`

Ajouter :
```kotlin
data object LibrarySelection : Screen("library_selection")
```

---

## Etape 3 : ViewModel — LibrarySelectionViewModel

**Nouveau fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/libraryselection/LibrarySelectionViewModel.kt`

Architecture :
- `@HiltViewModel` avec injection de `AuthRepository`, `LibraryRepository`, `SettingsDataStore`
- `UiState` contenant la liste des serveurs avec leurs bibliothèques et l'état de sélection
- Logique d'initialisation :
  1. Appeler `authRepository.getServers()` pour récupérer les serveurs
  2. Pour chaque serveur, appeler `libraryRepository.getLibraries(serverId)` pour récupérer les sections
  3. Filtrer les types supportés (`movie`, `show`) — on ne propose pas les bibliothèques `artist`/`photo`
  4. Pré-sélectionner toutes les bibliothèques par défaut (l'utilisateur décoche ce qu'il ne veut pas)

**UiState** :
```kotlin
data class LibrarySelectionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val servers: List<ServerWithLibraries> = emptyList(),
)

data class ServerWithLibraries(
    val serverId: String,
    val serverName: String,
    val libraries: List<SelectableLibrary>,
)

data class SelectableLibrary(
    val key: String,
    val title: String,
    val type: String,     // "movie" ou "show"
    val isSelected: Boolean = true,  // sélectionné par défaut
)
```

**Actions** :
```kotlin
sealed interface LibrarySelectionAction {
    data class ToggleLibrary(val serverId: String, val libraryKey: String) : LibrarySelectionAction
    data class ToggleServer(val serverId: String) : LibrarySelectionAction  // tout cocher/décocher
    data object Confirm : LibrarySelectionAction
}
```

La méthode `confirm()` :
1. Construit le `Set<String>` des IDs sélectionnés (`"serverId:libraryKey"`)
2. Appelle `settingsDataStore.saveSelectedLibraryIds(ids)`
3. Appelle `settingsDataStore.saveLibrarySelectionComplete(true)`
4. Emet un event de navigation vers `Loading`

---

## Etape 4 : Ecran UI — LibrarySelectionScreen

**Nouveau fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/libraryselection/LibrarySelectionScreen.kt`

Design de l'écran :
- **Titre** : "Choisissez vos bibliothèques"
- **Sous-titre** : "Sélectionnez les bibliothèques que vous souhaitez synchroniser"
- **Corps** : `LazyColumn` avec des sections par serveur
  - **Header serveur** : Nom du serveur + checkbox "tout sélectionner" pour ce serveur
  - **Items bibliothèque** : Chaque bibliothèque avec checkbox + icône type (film/série) + titre
- **Bouton Confirmer** : En bas, fixe, avec le décompte des bibliothèques sélectionnées
- **Focus management** : Focus initial sur la première bibliothèque (TV-friendly)
- **Etat Loading** : Spinner pendant le chargement des serveurs/sections
- **Etat Erreur** : Message + bouton Retry

Pattern : Route → Screen → ViewModel (identique au reste de l'app)

---

## Etape 5 : Navigation — Intégration dans le flux

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`

Modifications dans `PlexHubApp` :

1. Ajouter la route `composable(Screen.LibrarySelection.route)` dans le NavHost
2. Modifier la navigation post-auth : `Login.onAuthSuccess` → `LibrarySelection` (au lieu de `Loading`)
3. `LibrarySelection.onConfirm` → `Loading`
4. Modifier `SplashRoute.onNavigateToLoading` → vérifier `isLibrarySelectionComplete` d'abord

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/splash/SplashViewModel.kt` (ou équivalent)

Modifier la logique de redirection post-splash :
- Si token existe ET `isLibrarySelectionComplete == true` → `Loading` (comportement actuel)
- Si token existe ET `isLibrarySelectionComplete == false` → `LibrarySelection`
- Si pas de token → `Login`

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingViewModel.kt`

Modifier `checkSyncStatus()` :
- Avant de vérifier le sync, vérifier si `isLibrarySelectionComplete == false`
- Si non, émettre un event de navigation vers `LibrarySelection` (sécurité en cas de crash)

Ajouter un `LoadingNavigationEvent.NavigateToLibrarySelection` au sealed class.

---

## Etape 6 : Filtrage sync — SyncRepositoryImpl

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt`

Modifier `syncServer()` :
1. Injecter `SettingsDataStore` dans le constructeur
2. Lire `selectedLibraryIds` depuis DataStore
3. Après le filtre de type (`movie`/`show`), ajouter un filtre supplémentaire :
   ```kotlin
   val selectedIds = settingsDataStore.selectedLibraryIds.first()
   val syncableLibraries = libraries
       .filter { it.type == "movie" || it.type == "show" }
       .filter { selectedIds.isEmpty() || selectedIds.contains("${server.clientIdentifier}:${it.key}") }
   ```
   Note : `selectedIds.isEmpty()` = fallback pour les utilisateurs existants (aucune sélection = tout synchroniser)

**Fichier** : `domain/src/main/java/com/chakir/plexhubtv/domain/repository/SyncRepository.kt`

Pas de modification nécessaire de l'interface — le filtrage est un détail d'implémentation interne.

---

## Etape 7 : Accès depuis Settings — Reconfiguration

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`

Ajouter un `SettingsTile` dans la section sync/serveurs :
```
Titre : "Bibliothèques synchronisées"
Sous-titre : "3 bibliothèques sélectionnées sur 2 serveurs"
Action : Navigue vers LibrarySelection
```

**Fichier** : `feature/libraryselection/LibrarySelectionViewModel.kt`

Quand l'utilisateur confirme sa sélection et que `isFirstSyncComplete == true` (= reconfiguration) :
1. Identifier les bibliothèques retirées (ancienne sélection - nouvelle sélection)
2. Purger les médias des bibliothèques retirées via `MediaDao.deleteMediaByLibrary()`
3. Sauvegarder la nouvelle sélection
4. Remettre `isFirstSyncComplete = false`
5. L'app naviguera vers `Loading` qui déclenchera automatiquement le re-sync via `PlexHubApplication.setupBackgroundSync()`

---

## Etape 8 : Nettoyage des données dé-sélectionnées

Quand l'utilisateur retire une bibliothèque de sa sélection (via Settings), les médias déjà synchronisés pour cette bibliothèque doivent être supprimés de Room.

**Fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt`

Ajouter :
```kotlin
@Query("DELETE FROM media WHERE serverId = :serverId AND librarySectionId = :libraryKey")
suspend fun deleteMediaByLibrary(serverId: String, libraryKey: String)
```

Le ViewModel appellera cette méthode pour chaque bibliothèque dé-sélectionnée avant de lancer le re-sync.

---

## Fichiers impactés (résumé)

| Fichier | Modification |
|---------|-------------|
| `core/datastore/.../SettingsDataStore.kt` | +4 propriétés (selectedLibraryIds, isLibrarySelectionComplete, + save methods) |
| `app/.../di/navigation/Screen.kt` | +1 route LibrarySelection |
| **NEW** `app/.../feature/libraryselection/LibrarySelectionViewModel.kt` | ViewModel complet |
| **NEW** `app/.../feature/libraryselection/LibrarySelectionScreen.kt` | Screen + Route composables |
| `app/.../MainActivity.kt` | Ajout route + modification flux navigation |
| `app/.../feature/splash/SplashViewModel.kt` (ou équivalent) | Redirection vers LibrarySelection si pas complète |
| `app/.../feature/loading/LoadingViewModel.kt` | Vérification sélection + nouveau nav event |
| `data/.../repository/SyncRepositoryImpl.kt` | Injection DataStore + filtrage par bibliothèques sélectionnées |
| `app/.../feature/settings/SettingsScreen.kt` | Tile pour reconfigurer les bibliothèques |
| `core/database/.../MediaDao.kt` | +1 query deleteMediaByLibrary |

---

## Vérification / Tests

1. **Nouveau utilisateur** : Login → LibrarySelection s'affiche avec tous les serveurs/bibliothèques → sélection → Loading → sync uniquement les sélectionnées → Main
2. **Utilisateur existant** : Splash → (token trouvé) → LibrarySelection (car `isLibrarySelectionComplete == false` par défaut) → confirme → re-sync
3. **Sélection partielle** : Décocher une bibliothèque → seules les sélectionnées sont synchronisées
4. **Aucune sélection** : Le bouton Confirmer est désactivé si rien n'est sélectionné
5. **Settings** : Le tile "Bibliothèques synchronisées" affiche le bon décompte et navigue vers LibrarySelection
6. **Re-sync après modif** : Les bibliothèques dé-sélectionnées sont purgées de Room, les nouvelles synchronisées
7. **Fallback backward-compat** : Si `selectedLibraryIds` est vide dans SyncRepository, tout est synchronisé
8. **Serveur offline** : L'écran affiche un message d'erreur + bouton Retry pour les serveurs inaccessibles
9. **TV Focus** : Navigation DPAD fonctionne correctement sur l'écran (checkboxes, bouton confirmer)
10. **Build** : `./gradlew assembleDebug` compile sans erreur
