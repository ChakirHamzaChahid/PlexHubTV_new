# Performance Mi Box S - Library Screen Optimization

## Context

La Library screen prend 30s+ a s'afficher (scenario 1) et 20s+ au retour du player (scenario 2) sur Mi Box S (CPU Amlogic S905X-H, 2GB RAM).

**Causes racines identifiees:**
1. `selectedServerId` est TOUJOURS `null` → `"all"` → requete unifiee complexe meme avec 1 seul serveur (LEFT JOIN id_bridge, 5x correlated MAX, GROUP BY COALESCE)
2. `PlayerScrobbler` ecrit dans la table `media` toutes les 30s → Room invalide le PagingSource → re-execute la requete (20s) en continu pendant la lecture
3. 7 lectures DataStore sequentielles dans init + requete count prematuree

---

## Bloc 1: Single-server fast path (P0 - scenarios 1 & 2)

**Impact attendu: query 20-30s → 1-3s**

La requete non-unifiee utilise l'index `(serverId, librarySectionId, filter, sortOrder, pageOffset)` directement — pas de JOIN, pas de correlated MAX.

### Fichiers modifies

**1. `domain/src/main/java/.../domain/repository/LibraryRepository.kt`**
- Ajouter: `suspend fun getLibrarySectionKey(serverId: String, type: String): String?`

**2. `data/src/main/java/.../data/repository/LibraryRepositoryImpl.kt`**
- Implementer `getLibrarySectionKey()` via `database.librarySectionDao().getLibrarySectionByType()`

**3. `app/src/main/java/.../feature/library/LibraryViewModel.kt` — `loadMetadata()`**
- Refactorer le chargement xtream/backend pour extraire les listes dans des variables reutilisables
- Apres le chargement des 3 sources: si `servers.size == 1 && xtreamAccounts.isEmpty() && backendServers.isEmpty()`:
  - Resoudre le `libraryKey` via `libraryRepository.getLibrarySectionKey()`
  - Mettre a jour `_uiState` avec `selectedServerId` = clientIdentifier du serveur et `selectedLibraryId` = libraryKey resolu
  - Cela fait que `serverId != "all"` → `isUnified = false` → requete non-unifiee

---

## Bloc 2: Reduction des ecritures scrobble (P0 - scenario 2)

**Impact attendu: 0 invalidation pendant la lecture au lieu de N/30**

### Fichiers modifies

**1. `domain/src/main/java/.../domain/repository/PlaybackRepository.kt`**
- Ajouter: `suspend fun flushLocalProgress()`

**2. `data/src/main/java/.../data/repository/PlaybackRepositoryImpl.kt`**
- Ajouter un cache en memoire: `private val progressCache = mutableMapOf<String, ProgressEntry>()`
- `updatePlaybackProgress()`: remplacer `mediaDao.updateProgress()` par un stockage dans `progressCache`
  - L'appel API Plex continue toutes les 30s (serveur toujours a jour)
- `flushLocalProgress()`: ecrire tous les entries du cache dans Room en 1 batch, puis vider le cache
  - 1 seule ecriture DB par session de lecture au lieu de N

**3. `app/src/main/java/.../feature/player/controller/PlayerScrobbler.kt` — `stop()`**
- Appeler `playbackRepository.flushLocalProgress()` dans le bloc `applicationScope.launch(ioDispatcher)` existant (ligne 106)
- Le flush utilise `applicationScope` car le scope du scrobbler est deja annule

### Compromis acceptables
- Si l'app est tuee pendant la lecture, le cache memoire est perdu. Mais le serveur Plex a deja la progression (API calls toutes les 30s). Le prochain sync Library la recuperera.
- L'ecran "Continue Watching" ne se met pas a jour en temps reel pendant la lecture — acceptable car l'utilisateur regarde.

---

## Bloc 3: Lectures DataStore paralleles + count differe (P1 - scenario 1)

**Impact attendu: ~500ms-5s d'economie sur l'init**

### Fichiers modifies

**`app/src/main/java/.../feature/library/LibraryViewModel.kt` — `init` block**

- Remplacer les 7 `firstOrNull()` sequentiels (lignes 231-240) par `coroutineScope { async { } }` paralleles
- Creer une data class privee `DataStorePrefs` pour le destructuring
- Extraire l'observateur de count filtre dans une methode `launchFilteredCountObserver()`
- Lancer cet observateur APRES `loadMetadata()` (pas concurrent) pour eviter une requete count unifiee prematuree avec les params par defaut

---

## Ordre d'implementation

1. **Bloc 2** (plus simple, risque bas, elimine scenario 2)
2. **Bloc 1** (elimine scenario 1 pour single-server)
3. **Bloc 3** (polish init time)

## Verification

- Bloc 1: Verifier via Timber logs que `isUnified = false` quand 1 seul serveur
- Bloc 2: Verifier que `mediaDao.updateProgress()` n'est JAMAIS appele pendant la lecture, et 1 seule fois au stop
- Bloc 3: Mesurer le temps d'init avant/apres avec `System.currentTimeMillis()`
- Test global: Sur Mi Box S, mesurer le temps d'affichage Library (objectif < 3s) et le temps de retour du player (objectif < 2s)
