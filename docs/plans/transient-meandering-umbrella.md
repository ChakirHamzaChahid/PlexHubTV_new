# Plan : Fix Continue Watching + Watchlist

## Context

Deux plaintes utilisateur (Klicer) :
1. **Continue Watching incomplet** : Les series en cours de lecture sur le serveur n'apparaissent pas sur l'accueil
2. **Watchlist partielle** : L'utilisateur a 131 elements mais n'en voit qu'une fraction dans "My List"

### Causes racines identifiees

**Continue Watching** :
- `PlexClient.getOnDeck()` limite a `X-Plex-Container-Size=50` sans pagination ([PlexClient.kt:99-101](core/network/src/main/java/com/chakir/plexhubtv/core/network/PlexClient.kt#L99-L101))
- `OnDeckRepositoryImpl.refreshOnDeck()` fait un seul appel par serveur, pas de boucle ([OnDeckRepositoryImpl.kt:112](data/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt#L112))
- Items au-dela de 50 silencieusement tronques

**Watchlist** :
- `syncWatchlist()` recupere TOUS les items cloud (pagination OK, 100/page) ([WatchlistRepositoryImpl.kt:131-144](data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt#L131-L144))
- MAIS ne garde que ceux matchant un GUID local via `mediaDao.getAllMediaByGuids()` ([WatchlistRepositoryImpl.kt:147-152](data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt#L147-L152))
- Items cloud sans correspondance locale (contenu Plex Discover, librairies non-syncees) = **silencieusement ignores**
- "My List" affiche seulement les `FavoriteEntity` matches => sous-ensemble de la watchlist reelle

---

## PARTIE 1 : Continue Watching — Pagination OnDeck

### Tache 1.1 : Ajouter parametres pagination a `PlexClient.getOnDeck()`

**Fichier** : [PlexClient.kt](core/network/src/main/java/com/chakir/plexhubtv/core/network/PlexClient.kt)

Modifier la signature pour accepter `start` et `size` :
```kotlin
suspend fun getOnDeck(start: Int = 0, size: Int = 100): Response<PlexResponse> {
    return api.getMetadata(
        buildUrl("/library/onDeck?includeGuids=1&includeMeta=1" +
            "&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size")
    )
}
```
- Taille par defaut augmentee de 50 a 100 (reduit les round-trips)
- Parametres `start`/`size` pour pagination

### Tache 1.2 : Ajouter boucle de pagination dans `OnDeckRepositoryImpl.refreshOnDeck()`

**Fichier** : [OnDeckRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt)

Dans le bloc `applicationScope.async` (lignes 108-131), remplacer l'appel unique par une boucle :
```kotlin
val allDtos = mutableListOf<MetadataDTO>()
var offset = 0
val pageSize = 100
do {
    val response = client.getOnDeck(start = offset, size = pageSize)
    val container = response.body()?.mediaContainer
    val metadata = container?.metadata ?: emptyList()
    allDtos.addAll(metadata)
    val totalSize = container?.totalSize ?: 0
    offset += pageSize
    Timber.d("OnDeck page: offset=$offset, fetched=${metadata.size}, total=$totalSize, server=${client.server.name}")
} while (offset < totalSize)
// Puis mapper allDtos au lieu de response.body()?.mediaContainer?.metadata
```

Securite :
- `offset` augmente strictement => pas de boucle infinie
- Le `try/catch` existant capture les erreurs mid-pagination
- Plex PMS 1.25+ supporte `X-Plex-Container-Start` sur `/library/onDeck`

---

## PARTIE 2 : Watchlist — Stocker TOUS les items cloud

### Tache 2.1 : Creer `WatchlistEntity`

**Fichier (nouveau)** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/WatchlistEntity.kt`

```kotlin
@Entity(tableName = "watchlist", primaryKeys = ["cloudRatingKey"])
data class WatchlistEntity(
    val cloudRatingKey: String,        // ID metadata Plex Discover
    val guid: String?,                 // plex://movie/... ou plex://show/...
    val title: String,
    val type: String,                  // movie, show
    val year: Int? = null,
    val thumbUrl: String? = null,      // URL absolue cloud (CDN Plex)
    val artUrl: String? = null,
    val summary: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val localRatingKey: String? = null, // Lien vers MediaEntity locale (nullable)
    val localServerId: String? = null,
    val orderIndex: Int = 0,
)
```

Justification d'une table separee plutot qu'etendre `FavoriteEntity` :
- `FavoriteEntity` a PK `(ratingKey, serverId)` — items cloud n'ont ni l'un ni l'autre
- Separation semantique claire : watchlist cloud vs favoris locaux
- Pas de migration destructive sur `favorites`

### Tache 2.2 : Creer `WatchlistDao`

**Fichier (nouveau)** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/WatchlistDao.kt`

Methodes :
- `getAllWatchlistItems(): Flow<List<WatchlistEntity>>` (ORDER BY orderIndex)
- `replaceAll(items)` (transaction: clearAll + insertAll)
- `getCount(): Int`
- `isInWatchlistByGuid(guid): Flow<Boolean>`

### Tache 2.3 : Migration DB v44 -> v45

**Fichier** : [DatabaseModule.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt)

```sql
CREATE TABLE IF NOT EXISTS `watchlist` (
    `cloudRatingKey` TEXT NOT NULL,
    `guid` TEXT,
    `title` TEXT NOT NULL,
    `type` TEXT NOT NULL,
    `year` INTEGER,
    `thumbUrl` TEXT,
    `artUrl` TEXT,
    `summary` TEXT,
    `addedAt` INTEGER NOT NULL,
    `localRatingKey` TEXT,
    `localServerId` TEXT,
    `orderIndex` INTEGER NOT NULL,
    PRIMARY KEY(`cloudRatingKey`)
);
CREATE INDEX IF NOT EXISTS `index_watchlist_guid` ON `watchlist` (`guid`);
```

**Fichier** : [PlexDatabase.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt)
- Ajouter `WatchlistEntity::class` aux entities
- Bumper version 44 -> 45
- Ajouter `abstract fun watchlistDao(): WatchlistDao`
- Ajouter `@Provides` dans DatabaseModule

### Tache 2.4 : Reecrire `syncWatchlist()` pour stocker TOUS les items

**Fichier** : [WatchlistRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt)

Logique modifiee :
1. Pagination cloud (inchange, deja correcte)
2. Batch GUID match avec `mediaDao.getAllMediaByGuids()` (inchange)
3. **NOUVEAU** : Construire `WatchlistEntity` pour CHAQUE item (matche ou non)
   - `localRatingKey`/`localServerId` = non-null si match GUID, null sinon
   - `thumbUrl` = URL absolue du cloud (CDN Plex)
4. `watchlistDao.replaceAll(entities)` — remplacement atomique
5. **Compatibilite** : continuer a ecrire les items matches dans `favorites` (backward compat)
6. Logging : `Timber.i("Watchlist sync: ${total} total, ${matched} matched locally")`

### Tache 2.5 : Ajouter `getWatchlistItems()` au repository

**Fichier** : [WatchlistRepository.kt](domain/src/main/java/com/chakir/plexhubtv/domain/repository/WatchlistRepository.kt)
```kotlin
fun getWatchlistItems(): Flow<List<MediaItem>>
```

**Fichier** : [WatchlistRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/WatchlistRepositoryImpl.kt)

Implementation : `watchlistDao.getAllWatchlistItems().map { entities -> ... }`
- Items avec `localRatingKey != null` : `id = "${localServerId}_${localRatingKey}"`, navigation normale
- Items cloud-only : `serverId = "watchlist"`, `thumbUrl` = URL cloud absolue (pas de resolution necessaire)

### Tache 2.6 : Fusionner watchlist + favoris locaux dans HomeViewModel

**Fichier** : [HomeViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt)

Modifier `collectFavorites()` pour combiner les deux sources :
```kotlin
private fun collectMyList() {
    combine(
        watchlistRepository.getWatchlistItems(),
        favoritesRepository.getFavorites()
    ) { watchlistItems, localFavorites ->
        // watchlistItems inclut items matches + cloud-only
        // localFavorites inclut items ajoutes localement
        // Deduplication : watchlist prioritaire, puis favoris locaux sans GUID match
        val watchlistGuids = watchlistItems.mapNotNull { it.guid }.toSet()
        val localOnly = localFavorites.filter { it.guid == null || it.guid !in watchlistGuids }
        watchlistItems + localOnly
    }
    .catch { e -> Timber.e(e, "HomeViewModel: my list collection failed") }
    .onEach { items -> _uiState.update { it.copy(favorites = items.toImmutableList()) } }
    .launchIn(viewModelScope)
}
```

Ajouter `WatchlistRepository` au constructeur du ViewModel.

### Tache 2.7 : Gerer le clic sur items cloud-only

**Fichier** : [HomeViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt)

Dans `HomeAction.OpenMedia` :
```kotlin
is HomeAction.OpenMedia -> {
    if (action.media.serverId == "watchlist") {
        // Item cloud-only : toast informatif
        _errorEvents.trySend(AppError.UI.NotInLibrary("Ce titre n'est pas dans votre librairie"))
    } else {
        viewModelScope.launch {
            _navigationEvents.send(HomeNavigationEvent.NavigateToDetails(...))
        }
    }
}
```

Ajouter `AppError.UI.NotInLibrary` si necessaire, ou utiliser un event toast dedie.

---

## Fichiers modifies (resume)

| Fichier | Action | Partie |
|---------|--------|--------|
| `core/network/.../PlexClient.kt` | Modifier `getOnDeck()` | CW |
| `data/.../OnDeckRepositoryImpl.kt` | Ajouter boucle pagination | CW |
| `core/database/.../WatchlistEntity.kt` | **Creer** | WL |
| `core/database/.../WatchlistDao.kt` | **Creer** | WL |
| `core/database/.../PlexDatabase.kt` | Ajouter entity + DAO + version | WL |
| `core/database/.../DatabaseModule.kt` | Migration 44->45 + provider | WL |
| `domain/.../WatchlistRepository.kt` | Ajouter `getWatchlistItems()` | WL |
| `data/.../WatchlistRepositoryImpl.kt` | Reecrire sync + impl getItems | WL |
| `app/.../HomeViewModel.kt` | Fusion my list + toast cloud-only | WL |

---

## Risques techniques

| Risque | Probabilite | Mitigation |
|--------|------------|------------|
| Plex ancien ne supporte pas `X-Plex-Container-Start` sur onDeck | Faible | Degradation gracieuse : retourne tous les items jusqu'a Container-Size |
| URLs cloud expirees pour les posters | Faible | CDN Plex a TTL long ; resync via LibrarySyncWorker rafraichit |
| Items cloud-only non-cliquables | Attendu | Toast MVP, ecran detail Discover en follow-up |
| Memory spike si >500 items onDeck | Tres faible | ~1KB/DTO, 500 items = ~500KB, acceptable |
| Migration DB echoue | Tres faible | CREATE TABLE simple, pattern utilise 44 fois deja |

---

## Verification et tests

### Tests manuels
1. **CW pagination** : Utilisateur avec >50 items onDeck sur un serveur => tous visibles
2. **CW multi-serveur** : 2 serveurs avec 60 items chacun => deduplication + tous affiches
3. **WL complete** : 131 items watchlist cloud => "My List" montre 131 items
4. **WL posters cloud** : Items non-locaux affichent bien leur poster depuis le CDN Plex
5. **WL clic cloud-only** : Clic sur item non-local => toast "Ce titre n'est pas dans votre librairie"
6. **WL clic local** : Clic sur item avec match local => navigation detail normale
7. **Migration DB** : Upgrade v44->v45 sans crash, donnees preservees
8. **Toggle favori** : Ajouter/retirer un favori local fonctionne toujours + sync cloud
9. **Offline** : Items caches affiches meme sans reseau

### Tests unitaires
- `WatchlistRepositoryImpl.syncWatchlist()` : mock 131 DTOs dont 80 matches => 131 WatchlistEntity, 80 FavoriteEntity
- `OnDeckRepositoryImpl.refreshOnDeck()` : mock API retournant totalSize=75 avec 2 pages => 75 entities
- `HomeViewModel` combine : 100 watchlist + 5 local-only favorites => 105 items dans uiState.favorites
