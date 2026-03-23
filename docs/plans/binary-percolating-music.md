# Plan : Sync Multi-Serveurs - Suivi Granulaire & Recap UI

## Context

L'ecran de sync actuel affiche uniquement le nom de la bibliotheque en cours (`Syncing Films (29/40)`) sans indiquer **quel serveur** est traite. Avec potentiellement 20 serveurs, l'utilisateur n'a aucune visibilite sur l'avancement global. Ce plan ajoute un suivi par serveur/bibliotheque et un recap multi-serveurs en temps reel.

---

## Revue critique du plan initial et decisions

### P1 - CRITIQUE : Over-engineering du systeme d'evenements

Le plan initial cree un systeme d'evenements complet (`ServerSyncEvent` sealed interface avec 6 variants) dans la couche **domaine**, plus un deuxieme callback `onServerSyncEvent` sur le `SyncRepository`.

**Problemes :**
- `ServerSyncEvent` est un concern d'infrastructure (progress tracking d'un worker), pas de logique metier. Ca n'a rien a faire dans `domain/`.
- `SyncRepository` est un **@Singleton** (confirme dans `RepositoryModule.kt:60-63`). Deux callbacks mutables sur un singleton = race condition si sync manuelle + periodique coincident.
- `SyncRepositoryImpl.syncServer()` lance 2 libraries en **async parallele** (chunks of 2, ligne 75). Les callbacks seraient invoques depuis des coroutines concurrentes, modifiant le `mutableListOf<SyncServerState>` du worker sans synchronisation.

**Decision :** Eliminer le systeme d'evenements. Le worker sait deja tout ce qu'il faut : il itere les serveurs, il a le resultat de chaque `syncServer()`, et le callback `onProgressUpdate` existant lui donne deja le nom de la library en cours + progression.

### P2 - CRITIQUE : Serialiseur delimiteur artisanal

Le plan propose un format `serverId|serverName|lib:name:type:status:n:m` avec escaping manuel des `|`, `:`, `\n`. C'est fragile, non teste, et inutile : **`kotlinx.serialization.json` est deja dans tous les modules** (app, core, data).

**Decision :** Utiliser `@Serializable` data classes + `Json.encodeToString()`. Plus sur, plus lisible, deja disponible.

### P3 - Code mort : `SyncLibraryStatus.Skipped` et `SyncServerStatus.Skipped`

Le plan definit un status `Skipped` puis dit lui-meme : "Pas de status Skipped a gerer a ce niveau (le filtrage se fait en amont)".

**Decision :** Supprimer `Skipped`. Les bibliotheques non selectionnees sont filtrees avant la sync ; elles n'apparaissent jamais dans les etats.

### P4 - Double calcul de progression

Le plan calcule `globalProgress` dans **deux endroits** : le worker existant (`libraryFraction * 80f`) et le nouveau `SyncGlobalState.globalProgress`. Lequel fait foi ? Risque de divergence.

**Decision :** Un seul calcul canonique dans le worker. `SyncGlobalState` n'a pas de `globalProgress` calcule - il recoit la valeur du worker.

### P5 - UI : Pas de scroll pour 20 serveurs

La sidebar utilise une `Column` avec `forEach`. Avec 20 serveurs, ca depasse l'ecran, surtout sur des box 720p type Mi Box S.

**Decision :** Utiliser `LazyColumn` avec hauteur max contrainte + scroll automatique vers le serveur en cours.

### P6 - Pas de tests

Le plan initial ne mentionne aucun test unitaire.

**Decision :** Tests obligatoires pour le serialiseur et la derivation de statut serveur.

---

## Architecture simplifiee

**Principe :** Le worker est le seul orchestrateur d'etat. Pas de nouveau callback sur le repository. On enrichit le callback `onProgressUpdate` existant avec le nom du serveur et on track les resultats serveur directement dans le worker.

```
SyncRepositoryImpl                    LibrarySyncWorker                LoadingViewModel
     |                                      |                              |
     |-- onProgressUpdate(cur,tot,lib) ---->|                              |
     |                                      |-- buildServerStates() ------>|
     |                                      |-- setProgressAsync(         |
     |                                      |     serverStates=json,      |
     |                                      |     progress, phase, ...) -->|
     |                                      |                              |-- deserialize
     |                                      |                              |-- SyncGlobalState
     |                                      |                              |-- LoadingScreen
```

---

## Fichiers a creer (1)

| # | Fichier | Description |
|---|---------|-------------|
| 1 | `app/src/main/java/.../feature/loading/SyncStatusModel.kt` | Modeles d'etat + serialisation |

## Fichiers a modifier (5)

| # | Fichier | Changements |
|---|---------|-------------|
| 2 | `app/.../work/LibrarySyncWorker.kt` | Tracker les serveurs, enrichir `setProgressAsync()` |
| 3 | `app/.../feature/loading/LoadingViewModel.kt` | Parser l'etat enrichi, exposer `SyncGlobalState` |
| 4 | `app/.../feature/loading/LoadingScreen.kt` | Nouveau layout avec recap serveurs |
| 5 | `app/src/main/res/values/strings.xml` | Nouvelles chaines sync (EN) |
| 6 | `app/src/main/res/values-fr/strings.xml` | Nouvelles chaines sync (FR) |

**Total : 1 fichier cree, 5 modifies.** (vs plan initial : 3 crees, 6 modifies)
Zero modification dans `domain/` ou `data/`.

---

## Etape 1 : Modeles d'etat + serialisation (`SyncStatusModel.kt`)

Fichier : `app/src/main/java/com/chakir/plexhubtv/feature/loading/SyncStatusModel.kt`

```kotlin
package com.chakir.plexhubtv.feature.loading

import kotlinx.serialization.Serializable

enum class SyncPhase { Discovering, LibrarySync, Extras, Finalizing, Complete }

@Serializable
enum class LibraryStatus { Pending, Running, Success, Error }

@Serializable
data class SyncLibraryState(
    val key: String,
    val name: String,
    val status: LibraryStatus = LibraryStatus.Pending,
    val itemsSynced: Int = 0,
    val itemsTotal: Int = 0,
    val errorMessage: String? = null,
)

@Serializable
enum class ServerStatus { Pending, Running, Success, PartialSuccess, Error }

@Serializable
data class SyncServerState(
    val serverId: String,
    val serverName: String,
    val status: ServerStatus = ServerStatus.Pending,
    val libraries: List<SyncLibraryState> = emptyList(),
    val errorMessage: String? = null,
) {
    val completedLibraryCount: Int
        get() = libraries.count { it.status == LibraryStatus.Success }

    val progress: Float
        get() {
            if (libraries.isEmpty()) return 0f
            val done = libraries.count {
                it.status == LibraryStatus.Success || it.status == LibraryStatus.Error
            }.toFloat()
            val running = libraries.filter { it.status == LibraryStatus.Running }
                .sumOf {
                    if (it.itemsTotal > 0) it.itemsSynced.toDouble() / it.itemsTotal
                    else 0.0
                }.toFloat()
            return (done + running) / libraries.size
        }
}

/** Etat global observe par l'UI. Non serialise - construit dans le ViewModel. */
data class SyncGlobalState(
    val phase: SyncPhase,
    val servers: List<SyncServerState>,
    val currentServerIndex: Int,
    val globalProgress: Float,  // valeur du worker, pas recalculee
) {
    val currentServer: SyncServerState?
        get() = servers.getOrNull(currentServerIndex)

    val currentLibrary: SyncLibraryState?
        get() = currentServer?.libraries?.firstOrNull { it.status == LibraryStatus.Running }
}
```

**Choix cles :**
- `LibraryStatus` et `ServerStatus` sont des **enums** (pas sealed interface). Plus simple, serialisable nativement, suffisant ici.
- `ServerStatus` est **explicite** (mis a jour par le worker), pas derive. Evite des bugs de derivation et permet au worker de distinguer "toutes les libraries en erreur" de "connexion impossible au serveur".
- `SyncGlobalState` n'est PAS `@Serializable` - il est construit dans le ViewModel a partir des donnees WorkManager.
- `globalProgress` vient du worker (source unique de verite).

---

## Etape 2 : LibrarySyncWorker - Suivi d'etat

Fichier : [LibrarySyncWorker.kt](app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt)

### 2a. Ajouter le tracking des serveurs

Apres `authRepository.getServers()` (ligne 159), initialiser un tableau mutable :

```kotlin
val serverStates = Array(servers.size) { i ->
    SyncServerState(
        serverId = servers[i].clientIdentifier,
        serverName = servers[i].name,
        status = ServerStatus.Pending,
    )
}
var currentServerIdx = -1
```

> `Array` (pas `mutableListOf`) - taille fixe, acces par index, pas de risque de ConcurrentModification.

### 2b. Enrichir le callback `onProgressUpdate` avec le contexte serveur

Le probleme actuel : `onProgressUpdate` ne sait pas quel serveur est en cours. Plutot que d'ajouter un 2e callback sur le repo @Singleton, on utilise une **variable locale au worker** qui capture le serveur courant :

```kotlin
var currentServerName = ""

syncRepository.onProgressUpdate = { current, total, libraryName ->
    // ... throttle existant (inchange) ...

    // Mettre a jour la library en cours dans serverStates
    val idx = currentServerIdx
    if (idx >= 0) {
        val server = serverStates[idx]
        val libs = server.libraries.toMutableList()
        val libIdx = libs.indexOfFirst { it.key == libraryName || it.name == libraryName }
        if (libIdx >= 0) {
            libs[libIdx] = libs[libIdx].copy(
                status = LibraryStatus.Running,
                itemsSynced = current,
                itemsTotal = total,
            )
            serverStates[idx] = server.copy(libraries = libs)
        }
    }

    // Serialiser + emettre (avec les cles existantes preservees)
    emitProgress(serverStates, currentServerIdx, globalProgress, "library_sync", libraryName)
}
```

### 2c. Modifier la boucle serveur

```kotlin
servers.forEachIndexed { index, server ->
    currentServerIdx = index
    currentServerName = server.name

    // Marquer ce serveur comme Running
    serverStates[index] = serverStates[index].copy(status = ServerStatus.Running)

    // Decouvrir les libraries avant de sync (info deja disponible via le Result)
    // On ne peut pas pre-remplir les libraries ici car syncServer() le fait en interne.
    // => On les remplira via le callback onProgressUpdate quand la 1ere page arrive.
    // ALTERNATIVE : Appeler libraryRepository.getLibraries() ici pour pre-remplir.

    emitProgress(serverStates, index, /* progress */, "library_sync", "")

    try {
        val syncResult = syncRepository.syncServer(server)
        if (syncResult.isFailure) {
            val errorMsg = syncResult.exceptionOrNull()?.message ?: "Unknown error"
            serverStates[index] = serverStates[index].copy(
                status = ServerStatus.Error,
                errorMessage = errorMsg.take(80),
            )
            failureCount++
        } else {
            // Marquer toutes les libraries restantes en Success
            val libs = serverStates[index].libraries.map { lib ->
                if (lib.status == LibraryStatus.Running || lib.status == LibraryStatus.Pending)
                    lib.copy(status = LibraryStatus.Success)
                else lib
            }
            val hasErrors = libs.any { it.status == LibraryStatus.Error }
            serverStates[index] = serverStates[index].copy(
                libraries = libs,
                status = if (hasErrors) ServerStatus.PartialSuccess else ServerStatus.Success,
            )
        }
    } catch (e: Exception) {
        serverStates[index] = serverStates[index].copy(
            status = ServerStatus.Error,
            errorMessage = e.message?.take(80),
        )
        failureCount++
    }

    emitProgress(serverStates, index, /* progress */, "library_sync", "")
}
```

### 2d. Pre-remplir les libraries

**Probleme :** `syncServer()` decouvre et filtre les libraries en interne. Le worker ne connait pas la liste des libraries avant que le 1er `onProgressUpdate` arrive.

**Solution simple :** Avant la boucle serveur, appeler `libraryRepository.getLibraries()` pour chaque serveur et pre-remplir `serverStates[i].libraries`. On reutilise le meme filtrage que `SyncRepositoryImpl` (type movie/show + selectedIds).

```kotlin
val selectedIds = settingsDataStore.selectedLibraryIds.first()
servers.forEachIndexed { index, server ->
    try {
        val libs = libraryRepository.getLibraries(server.clientIdentifier).getOrNull()
            ?.filter { it.type == "movie" || it.type == "show" }
            ?.filter { lib ->
                selectedIds.contains("${server.clientIdentifier}:${lib.key}")
            }
            ?.map { SyncLibraryState(key = it.key, name = it.title) }
            ?: emptyList()
        serverStates[index] = serverStates[index].copy(libraries = libs)
    } catch (_: Exception) {
        // Pas grave : les libraries seront decouvertes au fil de l'eau
    }
}
totalLibraries = serverStates.sumOf { it.libraries.size }
```

> Cet appel est rapide (~50ms par serveur) car `libraryRepository.getLibraries()` est deja cache localement dans Room via `LibrarySectionDao` (les sections sont persistees lors de la selection). Pas d'appel reseau supplementaire.

### 2e. Methode `emitProgress()`

```kotlin
private fun emitProgress(
    states: Array<SyncServerState>,
    currentIdx: Int,
    progress: Float,
    phase: String,
    libraryName: String,
) {
    try {
        val json = Json.encodeToString(states.toList())
        setProgressAsync(workDataOf(
            "progress" to progress,
            "phase" to phase,
            "message" to "...",  // fallback message
            "libraryName" to libraryName,
            "serverStates" to json,
            "currentServerIdx" to currentIdx,
            // Conserver les anciennes cles pour compatibilite
            "completedLibs" to states.count {
                it.status == ServerStatus.Success || it.status == ServerStatus.PartialSuccess
            },
            "totalLibs" to states.sumOf { it.libraries.size },
        ))
    } catch (e: Exception) {
        Timber.e("Failed to emit sync progress: ${e.message}")
    }
}
```

### 2f. Thread safety

Le callback `onProgressUpdate` est invoque depuis `SyncRepositoryImpl.syncLibrary()` qui tourne dans `withContext(ioDispatcher)`. Avec les chunks de 2, deux libraries peuvent appeler le callback quasi-simultanement.

Cependant le callback dans le worker est deja throttle a 1s (`now - lastNotificationTime >= 1000`). Seul un appel passe a la fois. Et les ecritures sur `serverStates[idx]` sont des copies immutables assignees a un slot d'array - pas de `ConcurrentModificationException` possible sur un `Array`. Le pire cas est un stale read sur un slot adjacent, ce qui n'a aucune consequence visible (la prochaine emission 1s plus tard sera a jour).

### 2g. Nettoyage

Ajouter `// Nettoyage` identique a l'existant ligne 255 : rien de plus a nettoyer (pas de nouveau callback).

---

## Etape 3 : LoadingViewModel - Parser l'etat enrichi

Fichier : [LoadingViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingViewModel.kt)

### 3a. Modifier `LoadingUiState.Loading`

```kotlin
data class Loading(
    val message: String,
    val progress: Float = 0f,
    val syncState: SyncGlobalState? = null,
) : LoadingUiState()
```

### 3b. Modifier le branch `RUNNING` (lignes 87-106)

```kotlin
WorkInfo.State.RUNNING -> {
    val progress = syncWork.progress.getFloat("progress", 0f)
    val phase = syncWork.progress.getString("phase") ?: "discovering"
    val serverStatesJson = syncWork.progress.getString("serverStates")
    val currentServerIdx = syncWork.progress.getInt("currentServerIdx", -1)

    // Deserialiser si disponible
    val syncGlobalState = if (!serverStatesJson.isNullOrBlank()) {
        try {
            val servers = Json.decodeFromString<List<SyncServerState>>(serverStatesJson)
            SyncGlobalState(
                phase = when (phase) {
                    "discovering" -> SyncPhase.Discovering
                    "library_sync" -> SyncPhase.LibrarySync
                    "extras" -> SyncPhase.Extras
                    "finalizing" -> SyncPhase.Finalizing
                    else -> SyncPhase.LibrarySync
                },
                servers = servers,
                currentServerIndex = currentServerIdx,
                globalProgress = progress,
            )
        } catch (e: Exception) {
            Timber.e("Failed to parse serverStates: ${e.message}")
            null
        }
    } else null

    // Message fallback (pour la notification / accessibilite)
    val message = buildSyncMessage(phase, syncGlobalState, syncWork)

    _uiState.value = LoadingUiState.Loading(message, progress, syncGlobalState)
}
```

```kotlin
private fun buildSyncMessage(
    phase: String,
    state: SyncGlobalState?,
    workInfo: WorkInfo,
): String = when (phase) {
    "discovering" -> "Discovering servers..."
    "library_sync" -> {
        val server = state?.currentServer
        val lib = state?.currentLibrary
        when {
            server != null && lib != null ->
                "${server.serverName} - ${lib.name} (${lib.itemsSynced}/${lib.itemsTotal})"
            else -> workInfo.progress.getString("message") ?: "Syncing..."
        }
    }
    "extras" -> "Syncing extras..."
    "finalizing" -> "Finalizing..."
    else -> workInfo.progress.getString("message") ?: "Syncing..."
}
```

---

## Etape 4 : LoadingScreen - UI enrichie

Fichier : [LoadingScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt)

### Layout cible

```
+------------------------------------------------------------------+
|                    Welcome to PlexHub TV                          |
|           Please wait while your media is loading...              |
|                                                                   |
|  +----------------------------+  +---------------------------+    |
|  |       [Spinner]            |  |  Serveurs            2/5  |    |
|  |                            |  |                           |    |
|  |  VM NAS                    |  |  [v] My NAS       3/3     |    |
|  |  Films (234/800)           |  | >[>] VM NAS       1/2     |    |
|  |                            |  |  [ ] Friend       --      |    |
|  |  [=============>    ] 45%  |  |  [!] Remote   Erreur      |    |
|  |                            |  |                           |    |
|  +----------------------------+  +---------------------------+    |
+------------------------------------------------------------------+
```

### Modifications dans le branch `LoadingUiState.Loading`

Remplacer le contenu actuel (lignes 92-118) par :

```kotlin
is LoadingUiState.Loading -> {
    val syncState = state.syncState

    if (syncState != null && syncState.servers.isNotEmpty()) {
        // Layout enrichi
        SyncProgressContent(syncState, state.progress)
    } else {
        // Layout existant (phase discovering / pas de donnees serveur)
        CircularProgressIndicator(...)
        Text(text = state.message, ...)
        LinearProgressIndicator(progress = { state.progress / 100f }, ...)
        Text(text = "${state.progress.toInt()}%", ...)
    }
}
```

### Nouveaux composables

**`SyncProgressContent`** : Row avec zone centrale (60%) + sidebar (35%)

```kotlin
@Composable
private fun SyncProgressContent(
    syncState: SyncGlobalState,
    globalProgress: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        // Zone centrale : serveur courant + progression
        CurrentSyncBlock(
            syncState = syncState,
            globalProgress = globalProgress,
            modifier = Modifier.weight(0.55f),
        )

        // Sidebar : recap serveurs (seulement si 2+ serveurs)
        if (syncState.servers.size >= 2) {
            Spacer(Modifier.width(24.dp))
            ServerRecapSidebar(
                servers = syncState.servers,
                currentIndex = syncState.currentServerIndex,
                modifier = Modifier.weight(0.35f).heightIn(max = 400.dp),
            )
        }
    }
}
```

**`CurrentSyncBlock`** : Spinner + nom serveur + nom library + barre

```kotlin
@Composable
private fun CurrentSyncBlock(syncState: SyncGlobalState, globalProgress: Float, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))

        syncState.currentServer?.let { server ->
            Text(
                text = server.serverName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        syncState.currentLibrary?.let { lib ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${lib.name} (${lib.itemsSynced}/${lib.itemsTotal})",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { globalProgress / 100f },
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text("${globalProgress.toInt()}%", style = MaterialTheme.typography.labelMedium)
    }
}
```

**`ServerRecapSidebar`** : LazyColumn dans une Surface semi-transparente

```kotlin
@Composable
private fun ServerRecapSidebar(
    servers: List<SyncServerState>,
    currentIndex: Int,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Header avec compteur
            val doneCount = servers.count {
                it.status == ServerStatus.Success || it.status == ServerStatus.PartialSuccess
            }
            Text(
                text = stringResource(R.string.sync_server_recap_title_count, doneCount, servers.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // Liste scrollable (gere 20+ serveurs)
            LazyColumn {
                itemsIndexed(servers) { index, server ->
                    ServerRecapItem(server, isCurrent = index == currentIndex)
                }
            }
        }
    }
}
```

**`ServerRecapItem`** : Icone + nom + compteur

```kotlin
@Composable
private fun ServerRecapItem(server: SyncServerState, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (isCurrent) Modifier.background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            ) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icone statut
        when (server.status) {
            ServerStatus.Success -> Icon(Icons.Default.CheckCircle, null,
                Modifier.size(16.dp), tint = Color(0xFF4CAF50))
            ServerStatus.PartialSuccess -> Icon(Icons.Default.Warning, null,
                Modifier.size(16.dp), tint = Color(0xFFFFA726))
            ServerStatus.Error -> Icon(Icons.Default.Error, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            ServerStatus.Running -> CircularProgressIndicator(
                Modifier.size(16.dp), strokeWidth = 2.dp)
            ServerStatus.Pending -> Icon(Icons.Default.Schedule, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }

        Spacer(Modifier.width(8.dp))

        // Nom du serveur
        Text(
            text = server.serverName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Compteur libraries (si applicable)
        if (server.libraries.isNotEmpty() && server.status != ServerStatus.Pending) {
            Text(
                text = "${server.completedLibraryCount}/${server.libraries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

### Focus management (Android TV)

Aucun element de la sidebar n'est focusable - c'est purement informatif. Le seul etat interactif est `LoadingUiState.Error` (boutons Retry/Exit) qui reste inchange.

---

## Etape 5 : Strings

### `values/strings.xml`
```xml
<string name="sync_server_recap_title_count">Servers (%1$d/%2$d)</string>
<string name="sync_server_label">Server: %1$s</string>
```

### `values-fr/strings.xml`
```xml
<string name="sync_server_recap_title_count">Serveurs (%1$d/%2$d)</string>
<string name="sync_server_label">Serveur\u00a0: %1$s</string>
```

> Seulement 2 chaines ajoutees. Les statuts textuels (Termine, Erreur...) ne sont PAS affiches - on utilise des icones uniquement. Plus lisible sur TV et zero probleme i18n.

---

## Etape 6 : Tests

### 6a. Test du modele de statut (`SyncStatusModelTest.kt`)

```
- SyncServerState.progress avec 0 libraries -> 0f
- SyncServerState.progress avec mix Running/Success -> valeur correcte
- SyncServerState.completedLibraryCount -> compte correct
- SyncGlobalState.currentServer avec index valide/invalide
- SyncGlobalState.currentLibrary avec/sans library Running
```

### 6b. Test de serialisation round-trip

```
- Serialiser 1 serveur, 2 libraries -> deserialiser -> assertEqual
- Serialiser 20 serveurs, 5 libraries chacun -> verifier taille < 10KB
- Serveur avec errorMessage contenant des caracteres speciaux (guillemets, accents)
- Liste vide -> deserialiser -> emptyList
```

### 6c. Test LoadingViewModel (modifier existant)

Fichier existant : `app/src/test/java/com/chakir/plexhubtv/feature/loading/` (a creer si absent)

```
- WorkInfo avec serverStates JSON -> SyncGlobalState parse correctement
- WorkInfo sans serverStates (ancien format) -> syncState = null, fallback message OK
- serverStates JSON malformed -> syncState = null, pas de crash
```

---

## Ordre d'implementation

1. **Modeles** : Creer `SyncStatusModel.kt` (data classes + enums serialisables)
2. **Worker** : Modifier `LibrarySyncWorker.kt` (tracker serveurs, pre-remplir libraries, enrichir progress)
3. **ViewModel** : Modifier `LoadingViewModel.kt` (parser JSON, exposer `SyncGlobalState`)
4. **UI** : Modifier `LoadingScreen.kt` (composables SyncProgressContent, ServerRecapSidebar, ServerRecapItem)
5. **Strings** : Ajouter dans les 2 fichiers strings.xml
6. **Tests** : SyncStatusModelTest + serialisation round-trip

---

## Verification end-to-end

| Scenario | Attendu |
|----------|---------|
| 1 serveur, 2 biblio | Bloc central avec nom serveur. Pas de sidebar. |
| 3+ serveurs | Sidebar visible avec `LazyColumn`. Serveurs passent Pending->Running->Success. |
| Serveur inaccessible | Icone erreur rouge dans sidebar. Les autres continuent. |
| Succes partiel | Icone ambre + compteur `1/3`. |
| 20 serveurs | Sidebar scrollable. JSON < 10KB. Pas de lag sur Mi Box S. |
| Ancien format (pas de serverStates) | `syncState = null`, fallback sur l'UI simple existante. |
| Build | `./gradlew assembleDebug` compile. |

---

## Recapitulatif des differences vs plan initial

| Aspect | Plan initial | Plan revise |
|--------|-------------|-------------|
| Fichiers crees | 3 | **1** |
| Fichiers modifies | 6 | **5** |
| Modif couche domain/ | Oui (ServerSyncEvent + SyncRepository) | **Non** |
| Modif couche data/ | Oui (SyncRepositoryImpl) | **Non** |
| Nouveau callback sur repo | `onServerSyncEvent` (2e callback mutable) | **Aucun** |
| Serialisation | Delimiteur artisanal | **kotlinx.serialization (deja dans le projet)** |
| Thread safety | Non adresse | **Analyse : safe via throttle + Array immutable-copy** |
| Scroll 20 serveurs | Column sans scroll | **LazyColumn avec heightIn(max)** |
| Tests | Aucun | **3 suites de tests** |
| Code mort (Skipped) | Present | **Supprime** |
| Double calcul progress | 2 sources (worker + SyncGlobalState) | **1 source (worker)** |
