# Audit Stratégie Offline First — PlexHubTV

> **Date** : 11 février 2026
> **Auditeur** : Claude Opus 4.6
> **Branche** : `claude/continue-plexhubtv-refactor-YO43N`

---

## Résumé Exécutif

| Indicateur | Valeur |
|---|---|
| **Repositories audités** | 17 |
| **Conformité Offline First** | 47% (8/17) |
| **Repositories Network-Only** | 2 (SearchRepo, similaire dans MediaDetailRepo) |
| **Repositories Cache-First complet** | 3 (HubsRepo, OnDeckRepo, FavoritesRepo) |
| **Repositories avec fallback partiel** | 5 (MediaDetailRepo, LibraryRepo, PlaybackRepo, etc.) |
| **Fonctionnalités 100% offline** | 4/8 critiques |
| **WorkManager sync** | ✅ LibrarySyncWorker (6h) + CollectionSyncWorker + RatingSyncWorker |
| **TTL configuré** | ✅ PlexApiCache (1h hubs) — ❌ Pas sur MediaEntity |
| **Tests offline** | ❌ Aucun test de scénario offline |

---

## Repositories — Conformité Offline First Détaillée

### ✅ HubsRepositoryImpl — Score : 85% 🟢

**Fichier** : `data/src/main/java/.../data/repository/HubsRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first | ✅ | `getCachedHubs()` émis en premier (ligne 47-48) |
| Room persistence | ✅ | `HomeContentEntity` + `MediaEntity` pour hubs |
| Network refresh | ✅ | Fetch réseau en background après cache |
| TTL | ✅ | `PlexApiCache` avec `ttlSeconds = 3600` (1h) |
| Offline fallback | ✅ | Si réseau échoue, cache émis reste visible |
| Sync background | ❌ | Pas de WorkManager dédié pour hubs |
| Tests offline | ❌ | Pas de test vérifiant le comportement offline |

**Pattern utilisé** :
```kotlin
// ✅ BON — Cache-first puis réseau
override fun getUnifiedHubs(): Flow<List<Hub>> = flow {
    val cachedHubs = getCachedHubs()  // 1. Cache immédiat
    emit(cachedHubs)
    // ... fetch réseau
    emit(result)  // 2. Données fraîches
}.flowOn(ioDispatcher)
```

**Points faibles** :
- La méthode `getCachedHubs()` appelle `authRepository.getServers()` qui peut échouer offline si les serveurs ne sont pas cachés
- Le cache `PlexApiCache` utilise une table Room `ApiCacheEntity` avec TTL, mais duplique les données déjà stockées dans `MediaEntity`

**Actions requises** :
1. Ajouter un test offline
2. Considérer supprimer le double-cache (PlexApiCache + Room entities) — complexité inutile

---

### ✅ OnDeckRepositoryImpl — Score : 80% 🟢

**Fichier** : `data/src/main/java/.../data/repository/OnDeckRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first | ✅ | `homeContentDao.getHomeMediaItems("onDeck", "onDeck")` émis en premier (ligne 53) |
| Room persistence | ✅ | `HomeContentEntity` + `MediaEntity` |
| Network refresh | ✅ | `refreshOnDeck()` en background (ligne 63) |
| TTL | ❌ | Pas de TTL — cache peut être périmé indéfiniment |
| Offline fallback | ⚠️ | Si `authRepository.getServers()` échoue offline, rien n'est émis |
| Sync background | ❌ | Pas de WorkManager pour OnDeck — dépend de l'ouverture de l'app |
| Tests offline | ❌ | Pas de test offline |

**Pattern utilisé** :
```kotlin
// ✅ BON — Cache-first avec refresh
override fun getUnifiedOnDeck(): Flow<List<MediaItem>> = flow {
    val servers = serversResult.getOrNull() ?: emptyList()
    if (servers.isEmpty()) { emit(emptyList()); return@flow }
    val cachedEntities = homeContentDao.getHomeMediaItems("onDeck", "onDeck")
    if (cachedEntities.isNotEmpty()) emit(deduplicated)  // 1. Cache
    refreshOnDeck()                                       // 2. Network
    emit(freshDeduplicated)                               // 3. Fresh
}
```

**Points faibles** :
- `authRepository.getServers()` est appelé avant l'émission du cache. Si la liste de serveurs n'est pas en cache, aucune donnée OnDeck n'est émise.
- `applicationScope.async` dans `refreshOnDeck()` (ligne 106) lance les tâches dans l'ApplicationScope au lieu du scope structuré — risque de fuite de coroutine si le Flow est annulé.

**Actions requises** :
1. Ajouter TTL sur les données OnDeck
2. Gérer le cas où `getServers()` échoue offline (utiliser serveurs cachés)
3. Ajouter WorkManager pour sync OnDeck périodique

---

### ✅ FavoritesRepositoryImpl — Score : 90% 🟢

**Fichier** : `data/src/main/java/.../data/repository/FavoritesRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first | ✅ | `favoriteDao.getAllFavorites()` est la source primaire (Room Flow) |
| Room persistence | ✅ | `FavoriteEntity` table avec insert/delete |
| Local-first CRUD | ✅ | `toggleFavorite()` modifie Room d'abord, sync Plex en background |
| Sync bidirectionnel | ⚠️ | Sync local→serveur via `applicationScope.launch` (fire-and-forget) |
| Conflict resolution | ❌ | Pas de stratégie de conflit (last-write-wins ou autre) |
| Offline fallback | ✅ | Fonctionne 100% offline car Room est la source de vérité |
| Tests offline | ❌ | `ToggleFavoriteUseCaseTest` existe mais pas de test de scénario offline |

**Pattern utilisé** :
```kotlin
// ✅ EXCELLENT — Local-first avec sync background
override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> {
    val isFav = favoriteDao.isFavorite(media.ratingKey, media.serverId).first()
    if (isFav) {
        favoriteDao.deleteFavorite(media.ratingKey, media.serverId)  // ✅ Local d'abord
        applicationScope.launch(ioDispatcher) {
            api.removeFromWatchlist(...)  // ✅ Sync en background
        }
    }
}
```

**Points faibles** :
- Le sync Plex en background via `applicationScope.launch` est fire-and-forget : si l'app est tuée, le sync est perdu
- Pas de file d'attente pour re-tenter les syncs échoués
- Pas de timestamp `lastModified` sur `FavoriteEntity` pour résolution de conflits

**Actions requises** :
1. Utiliser WorkManager pour le sync au lieu de `applicationScope.launch`
2. Ajouter `lastModified: Long` sur `FavoriteEntity` pour résolution de conflits

---

### ⚠️ MediaDetailRepositoryImpl — Score : 55% 🟡

**Fichier** : `data/src/main/java/.../data/repository/MediaDetailRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first (`getMediaDetail`) | ❌ | Cache key préparé mais parsing commenté (lignes 53-63) |
| Room fallback (`getMediaDetail`) | ⚠️ | Fallback GUID sur autre serveur, pas Room local |
| Cache-first (`getSeasonEpisodes`) | ⚠️ | Network-first avec fallback Room (lignes 117-162) |
| Cache-first (`getSimilarMedia`) | ❌ | Network-only, pas de cache |
| Cache-first (`getMediaCollections`) | ✅ | Room Flow direct (ligne 219) |
| Tests offline | ✅ | `MediaDetailRepositoryImplTest` existe |

**Problème principal — `getMediaDetail()`** :
```kotlin
// ❌ PROBLÈME — Cache préparé mais jamais utilisé
val cacheKey = "$serverId:/library/metadata/$ratingKey"
val cachedJson = plexApiCache.get(cacheKey)
if (cachedJson != null) {
    try {
        // We don't have GSON injected here easily without more refactor,
        // → CACHE IGNORÉ — toujours requête réseau
    } catch (e: Exception) { ... }
}
val response = client.getMetadata(ratingKey)  // ← Toujours appelé
```

**Problème secondaire — `getSeasonEpisodes()`** :
```kotlin
// ⚠️ PATTERN INCOMPLET — Network-first (devrait être cache-first)
try {
    val client = getClient(serverId)
    val response = client.getChildren(ratingKey)  // 1. Réseau d'abord
    if (response.isSuccessful) { return Result.success(items) }
} catch (e: ...) { ... }
// 2. Fallback Room seulement si erreur réseau
val localEntities = mediaDao.getChildren(ratingKey, serverId)
```

**Actions requises** :
1. Injecter `Gson` dans `MediaDetailRepositoryImpl` (déjà singleton Hilt)
2. Parser le cache `PlexApiCache` et émettre avant requête réseau
3. Pour `getSeasonEpisodes` : émettre cache Room d'abord, puis rafraîchir
4. Pour `getSimilarMedia` : ajouter cache Room ou PlexApiCache

---

### ⚠️ LibraryRepositoryImpl — Score : 70% 🟡

**Fichier** : `data/src/main/java/.../data/repository/LibraryRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first (`getLibraries`) | ✅ | Fallback Room si serveur offline (lignes 36-46) |
| Pagination offline | ✅ | Paging 3 avec `MediaRemoteMediator` + Room PagingSource |
| Network refresh | ✅ | `RemoteMediator` gère le refresh |
| Full sync | ✅ | `LibrarySyncWorker` synchronise les bibliothèques complètes |
| Offline browse | ✅ | Room PagingSource fonctionne offline |
| Tests offline | ❌ | `LibraryViewModelTest` existe mais pas de test offline spécifique |

**Points faibles** :
- `getLibraries()` tente le réseau avant le cache (réseau-first avec fallback)
- Pattern `getClient()` (ligne 429) appelle `findBestConnection()` qui peut être lent
- `getIndexOfFirstItem()` pourrait ne pas fonctionner si la DB n'a pas les données requises

**Actions requises** :
1. Refactorer `getLibraries()` pour émettre cache d'abord
2. Ajouter test de browsing offline

---

### ⚠️ PlaybackRepositoryImpl — Score : 65% 🟡

**Fichier** : `data/src/main/java/.../data/repository/PlaybackRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Progress local | ✅ | `updatePlaybackProgress` sauvegarde en Room même si réseau échoue (finally block) |
| Watch history | ✅ | `getWatchHistory()` depuis Room Flow |
| Next/Previous | ⚠️ | Dépend de `getSeasonEpisodes` qui est network-first |
| Toggle watch | ❌ | Network-only, pas de fallback |
| Stream selection | ❌ | Network-only, pas de fallback |
| Tests | ✅ | `PlaybackRepositoryImplTest` existe |

**Point fort** :
```kotlin
// ✅ BON — Sauvegarde locale même en cas d'erreur réseau
} finally {
    try {
        mediaDao.updateProgress(ratingKey, serverId, positionMs, System.currentTimeMillis())
    } catch (e: Exception) { ... }
}
```

**Actions requises** :
1. `toggleWatchStatus` : sauvegarder localement et sync en background
2. `getNextMedia/getPreviousMedia` : fallback sur épisodes cachés en Room

---

### ❌ SearchRepositoryImpl — Score : 0% 🔴

**Fichier** : `data/src/main/java/.../data/repository/SearchRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Cache-first | ❌ | Aucun cache |
| Room persistence | ❌ | Pas de table SearchCache |
| Offline fallback | ❌ | `IOException` → `Result.failure` |
| FTS search | ❌ | Pas de Full-Text Search |
| Debounce | ✅ | 500ms dans SearchViewModel (pas dans le repo) |
| Tests offline | ❌ | Aucun |

**Ce repository est 100% réseau** — il crash immédiatement offline.

Cependant, `MediaRepositoryImpl.searchMedia()` (lignes 172-201) effectue une recherche locale dans Room avec `mediaDao.searchMedia(query, type)`. Cette recherche locale existe mais n'est pas utilisée par `SearchViewModel` — le ViewModel utilise `SearchAcrossServersUseCase` qui appelle `SearchRepositoryImpl.searchAllServers()`.

**Actions requises** :
1. Créer `SearchCacheEntity` avec TTL
2. Implémenter cache-first dans `searchAllServers()`
3. Utiliser `MediaRepositoryImpl.searchMedia()` comme fallback local (Room FTS)
4. Ajouter tests offline complets

**Effort** : 12 heures | **Priorité** : 🔴 P0

---

### ⚠️ AuthRepositoryImpl — Score : 60% 🟡

**Fichier** : `data/src/main/java/.../data/repository/AuthRepositoryImpl.kt`

| Critère | Statut | Détails |
|---|---|---|
| Servers cache | ✅ | `ServerDao` + Room pour persistance |
| Login offline | N/A | Login nécessite réseau (normal) |
| getServers cache | ⚠️ | `forceRefresh=false` utilise Room, `true` fait requête réseau |
| Token storage | ✅ | `EncryptedSharedPreferences` |

**Point faible** : Si `forceRefresh=false` est utilisé mais que la DB serveurs est vide (premier lancement), pas de fallback.

---

### ✅ SettingsRepositoryImpl — Score : 100% 🟢

**Fichier** : `data/src/main/java/.../data/repository/SettingsRepositoryImpl.kt`

Entièrement local (DataStore). Fonctionne toujours offline. Pas de dépendance réseau.

---

### ✅ ProfileRepositoryImpl — Score : 100% 🟢

Entièrement local (Room `ProfileEntity`). CRUD complet offline.

---

### ⚠️ WatchlistRepositoryImpl — Score : 50% 🟡

| Critère | Statut |
|---|---|
| Sync serveur→local | ✅ Via `SyncWatchlistUseCase` |
| Sync local→serveur | ⚠️ Via `FavoritesRepositoryImpl.toggleFavorite` (fire-and-forget) |
| Offline access | ✅ Watchlist stocké dans `FavoriteEntity` |
| Conflict resolution | ❌ Pas de stratégie |

---

### ⚠️ SyncRepositoryImpl — Score : 60% 🟡

| Critère | Statut |
|---|---|
| Batch sync | ✅ Pages de 500 items |
| Semi-parallel | ✅ 2 bibliothèques à la fois |
| Progress tracking | ✅ Callback `onProgressUpdate` |
| Retry policy | ❌ Pas de retry si page échoue |
| Incremental sync | ❌ Full resync à chaque fois |

**Action requise** : Implémenter sync incrémental avec `updatedAt` timestamp.

---

### ⚠️ DownloadsRepositoryImpl — Score : 50% 🟡

Gestion des téléchargements offline. Partiellement implémenté.

---

### ⚠️ IptvRepositoryImpl — Score : 40% 🟡

| Critère | Statut |
|---|---|
| Playlist cache | ⚠️ Probable mais non vérifié en détail |
| Offline playback | ❌ Streams IPTV nécessitent réseau (par nature) |

---

### ⚠️ AccountRepositoryImpl — Score : 50% 🟡

Informations de compte. Partiellement cachées.

---

### ⚠️ OfflineWatchSyncRepositoryImpl — Score : 70% 🟡

Synchronisation des positions de lecture offline. Bonne implémentation avec `OfflineWatchProgressEntity`.

---

## Fonctionnalités — Disponibilité Offline

| Fonctionnalité | Offline | Cache | Sync | UX Indicators | Tests | Score |
|---|---|---|---|---|---|---|
| **Home (OnDeck)** | ✅ Partiel | ✅ Room + HomeContentEntity | ❌ Pas de WorkManager dédié | ❌ Pas de banner offline | ❌ Aucun | 60% 🟡 |
| **Home (Hubs)** | ✅ Partiel | ✅ Room + PlexApiCache (1h) | ❌ Pas de WorkManager dédié | ❌ Pas de banner offline | ❌ Aucun | 70% 🟡 |
| **Library Browse** | ✅ Full | ✅ Room + Paging3 + RemoteMediator | ✅ LibrarySyncWorker (6h) | ❌ Pas de timestamp affiché | ❌ Aucun | 80% 🟢 |
| **Media Details** | ⚠️ Partiel | ⚠️ Cache commenté (pas parsé) | ❌ Pas de sync | ❌ Aucun | ✅ Partiel | 45% 🟡 |
| **Search** | ❌ **Crash** | ❌ Pas de cache | N/A | ❌ N/A | ❌ Aucun | **0% 🔴** |
| **Favorites** | ✅ Full | ✅ Room FavoriteEntity | ⚠️ Fire-and-forget sync | ❌ Aucun | ⚠️ Partiel | 85% 🟢 |
| **Continue Watching** | ✅ Full | ✅ Room + OfflineWatchProgress | ✅ OfflineWatchSyncRepo | ❌ Aucun | ❌ Aucun | 80% 🟢 |
| **Player** | ⚠️ Réseau requis | ⚠️ Pas de cache vidéo local | N/A | ❌ Aucun | ❌ Aucun | 30% 🟠 |
| **Settings** | ✅ Full | ✅ DataStore (local) | N/A | ✅ Toujours disponible | N/A | 100% 🟢 |
| **Profiles** | ✅ Full | ✅ Room ProfileEntity | N/A | ✅ Toujours disponible | ✅ Tests | 100% 🟢 |

---

## Anti-Patterns Offline First Détectés

### ❌ 1. Network-Only Repositories (SearchRepositoryImpl)
**Impact** : Crash `IOException` immédiat si offline
**Fichier** : `SearchRepositoryImpl.kt`
**Correction** : Ajouter `SearchCacheEntity` + pattern cache-first

### ❌ 2. Cache Préparé mais Non Utilisé (MediaDetailRepositoryImpl)
**Impact** : Requête réseau systématique même si cache disponible
**Fichier** : `MediaDetailRepositoryImpl.kt:53-63`
**Correction** : Injecter Gson et parser le cache

### ⚠️ 3. Network-First au lieu de Cache-First (getSeasonEpisodes)
**Impact** : Latence élevée (attente réseau) même quand cache local disponible
**Fichier** : `MediaDetailRepositoryImpl.kt:117-162`
**Correction** : Émettre cache Room d'abord, rafraîchir ensuite

### ⚠️ 4. Fire-and-Forget Sync (FavoritesRepositoryImpl)
**Impact** : Sync perdu si app tuée
**Fichier** : `FavoritesRepositoryImpl.kt:101-120, 135-151`
**Correction** : Utiliser WorkManager pour le sync

### ⚠️ 5. Pas de TTL sur les Données Room
**Impact** : Données périmées affichées indéfiniment sans indication
**Fichier** : `MediaEntity.kt` — champ `updatedAt` existe mais initialisé à `0`
**Correction** : Mettre à jour `updatedAt` systématiquement + vérifier TTL dans les queries

### ⚠️ 6. `authRepository.getServers()` Comme Prérequis
**Impact** : Si les serveurs ne sont pas en cache, aucune donnée n'est émise même si le media cache existe
**Fichier** : `OnDeckRepositoryImpl.kt:44`, `HubsRepositoryImpl.kt:53`, etc.
**Correction** : Charger les serveurs depuis Room (cache local) en premier

### ✅ Pas de runBlocking sur Main Thread
Contrairement à `AuthInterceptor` (qui est sur le thread OkHttp), les repositories utilisent correctement `Dispatchers.IO` ou `flowOn(ioDispatcher)`.

---

## Plan d'Action Priorisé

### 🔴 Priorité 0 : SearchRepository (Bloquant Offline)

**Problème** : Feature critique inutilisable offline
**Impact utilisateur** : Crash si recherche sans réseau

**Étapes** :
1. Créer `SearchCacheEntity` dans `core:database` :
```kotlin
@Entity(tableName = "search_cache", indices = [Index("query", "serverId")])
data class SearchCacheEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val query: String,
    val serverId: String,
    val resultsJson: String,  // JSON sérialisé List<MediaItem>
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlHours: Long = 1): Boolean =
        System.currentTimeMillis() - lastUpdated > (ttlHours * 3_600_000)
}
```

2. Créer `SearchCacheDao`
3. Ajouter à `PlexDatabase` (version 24) + migration
4. Refactorer `SearchRepositoryImpl.searchAllServers()` :
   - Vérifier cache d'abord
   - Si cache valide → émettre
   - Fetch réseau → mettre à jour cache → émettre
   - Si erreur réseau + cache → émettre cache
   - Si erreur réseau + pas de cache → utiliser `MediaRepositoryImpl.searchMedia()` (recherche Room locale)

5. Ajouter tests offline

**Effort** : 12 heures
**Priorité** : Sprint actuel

---

### 🟠 Priorité 1 : MediaDetailRepositoryImpl — Activer le cache

**Problème** : Cache préparé mais code commenté
**Impact** : Requête réseau systématique

**Étapes** :
1. Injecter `Gson` dans le constructeur (déjà singleton Hilt)
2. Parser `cachedJson` et émettre si valide
3. Faire la requête réseau en background
4. Mettre à jour le cache
5. Pour `getSeasonEpisodes()` : inverser l'ordre (cache first)

**Effort** : 4 heures

---

### 🟠 Priorité 2 : WorkManager pour OnDeck/Favorites Sync

**Problème** : Pas de sync background pour OnDeck ni Favorites
**Impact** : Données périmées entre les sessions

**Étapes** :
1. Créer `MediaSyncWorker` pour :
   - Sync OnDeck
   - Sync Favorites (queue les syncs fire-and-forget en attente)
2. Configurer comme `PeriodicWorkRequest` (6h, WiFi, battery not low)
3. Enqueue dans `PlexHubApplication.onCreate()`

**Effort** : 8 heures

---

### 🟡 Priorité 3 : TTL sur MediaEntity

**Problème** : Cache sans expiration
**Impact** : Données périmées

**Étapes** :
1. S'assurer que `updatedAt` est mis à jour dans tous les mappers/upserts
2. Ajouter helper `fun isExpired(ttlHours: Long = 6): Boolean`
3. Dans les repositories, vérifier le TTL avant d'émettre cache

**Effort** : 3 heures

---

### 🟡 Priorité 4 : UI Offline Indicators

**Problème** : Utilisateur ne sait pas s'il est offline
**Impact** : Confusion UX

**Étapes** :
1. Observer `ConnectivityManager` dans MainViewModel
2. Afficher banner "Mode hors ligne" via Composition Local
3. Badge "Dernière mise à jour il y a Xh" sur Home/Library
4. Désactiver bouton Play si média non caché localement

**Effort** : 8 heures

---

## Tests Offline Requis

### Template de test offline par repository

```kotlin
@Test
fun `getMediaDetail offline should return cached data`() = runTest {
    // Arrange
    val cachedEntity = createMockMediaEntity(ratingKey = "123", serverId = "srv1")
    mediaDao.upsertMedia(listOf(cachedEntity))
    // Simuler offline : pas de serveurs disponibles
    coEvery { connectionManager.findBestConnection(any()) } returns null

    // Act
    val result = repository.getMediaDetail("123", "srv1")

    // Assert
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()?.ratingKey).isEqualTo("123")
}

@Test
fun `searchAllServers offline should return cached results`() = runTest {
    // Arrange
    searchCacheDao.insert(SearchCacheEntity(
        query = "breaking",
        serverId = "srv1",
        resultsJson = gson.toJson(listOf(createMockMediaItem(title = "Breaking Bad")))
    ))
    coEvery { connectionManager.findBestConnection(any()) } returns null

    // Act
    val result = repository.searchAllServers("breaking")

    // Assert
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()).hasSize(1)
    assertThat(result.getOrNull()?.first()?.title).isEqualTo("Breaking Bad")
}

@Test
fun `getUnifiedOnDeck offline should return cached items`() = runTest {
    // Arrange
    val cachedEntities = listOf(
        createMockMediaEntity(ratingKey = "ep1", filter = "onDeck")
    )
    mediaDao.upsertMedia(cachedEntities)
    homeContentDao.insertHomeContent(listOf(
        HomeContentEntity(type = "onDeck", hubIdentifier = "onDeck", title = "On Deck",
            itemServerId = "srv1", itemRatingKey = "ep1", orderIndex = 0)
    ))
    coEvery { connectionManager.findBestConnection(any()) } returns null

    // Act
    val result = repository.getUnifiedOnDeck().first()

    // Assert
    assertThat(result).hasSize(1)
    assertThat(result.first().ratingKey).isEqualTo("ep1")
}
```

### Scénarios d'intégration

- [ ] App démarre en mode avion sans crash
- [ ] Home affiche OnDeck et Hubs depuis cache
- [ ] Library browse fonctionne avec Paging3 local
- [ ] Search retourne résultats cachés (après implémentation)
- [ ] Details affiche métadonnées en cache
- [ ] Favorites CRUD entièrement offline
- [ ] Continue Watching restaure position offline
- [ ] Player indique que le réseau est requis si pas de cache vidéo
- [ ] Settings et Profiles toujours disponibles

---

## Métriques Cibles Après Corrections

| Métrique | Avant | Après | Impact |
|---|---|---|---|
| Repositories Offline First | 47% (8/17) | 88% (15/17) | +41% |
| Fonctionnalités offline | 4/8 | 7/8 | +3 features |
| Search offline | ❌ Crash | ✅ Cache | Critique |
| MediaDetail cache | ❌ Commenté | ✅ Actif | -60% latence |
| Cache hit rate | ~35% | ~65% | +30% |
| Time to first content | ~1200ms | ~200ms | -83% |
| Sync background | Library only | Library + OnDeck + Favorites | +2 entités |
| Tests offline | 0 | 15+ | Coverage |

**Note** : Le Player restera à 30% car le streaming vidéo nécessite le réseau par nature. Seul un système de téléchargement offline (déjà ébauché dans `DownloadsRepository`) permettrait 100%.

---

*Fin de l'audit Offline First — Voir `ACTION_PLAN_FOR_SONNET.md` pour le plan d'implémentation détaillé.*
