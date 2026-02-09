# PlexHubTV - Plan d'Optimisation Offline-First & Performances

## Vue d'ensemble - Diagnostic et Strategie

### Diagnostic du probleme

Apres audit complet du code, voici la **cause racine** du delai d'affichage des images au demarrage :

1. **Les URLs d'images sont reconstruites a chaque lecture depuis la DB.** Les `MediaEntity` stockent des **chemins relatifs** (ex: `/library/metadata/1/thumb`) dans `thumbUrl`/`artUrl`. A chaque affichage, le code doit :
   - Recuperer les serveurs (`authRepository.getServers()`)
   - Trouver la meilleure connexion (`connectionManager.findBestConnection()` ou `getCachedUrl()`)
   - Reconstruire l'URL complete avec baseUrl + token + transcodage

2. **Le cache de ConnectionManager est en memoire uniquement.** `_activeConnections: MutableStateFlow<Map<String, String>>` est perdu a chaque redemarrage. Donc a chaque cold start, `getCachedUrl()` retourne `null` et on tombe sur `server.address` (qui peut etre une adresse obsolete).

3. **La cle de cache Coil depend du baseUrl.** Si le baseUrl change entre deux sessions (ex: LAN `192.168.1.100:32400` vs relay `xxx.plex.direct:32400`), le `PlexImageKeyer` genere une cle differente et le disk cache de 1Go est ignore pour ces images.

4. **`getActiveClients()` force un refresh reseau des serveurs.** Les deux repos (OnDeck + Hubs) appellent `authRepository.getServers(forceRefresh=true)` dans `getActiveClients()`, ce qui force un appel API a plex.tv AVANT de pouvoir rafraichir les donnees.

5. **Le prefetch ImagePrefetchManager et l'affichage utilisent des fonctions de construction d'URL differentes.** `ImageUtil.getOptimizedImageUrl()` (affichage) ne met PAS le token dans le parametre `url` encode. `PlexImageHelper.getOptimizedImageUrl()` (prefetch) MET le token dans le parametre `url`. Resultat : deux cles de cache differentes pour la meme image.

### Strategie de correction

| # | Correction | Impact | Effort |
|---|-----------|--------|--------|
| 1 | Persister le cache ConnectionManager (DataStore) | Critique - URLs correctes des le cold start | Moyen |
| 2 | Rendre PlexImageKeyer independant du baseUrl | Critique - Disk cache stable entre sessions | Faible |
| 3 | Unifier les fonctions de construction d'URL | Important - Cohérence prefetch/display | Faible |
| 4 | Stocker des URLs resolues dans la DB | Important - Zero resolution au read time | Moyen |
| 5 | Supprimer `forceRefresh=true` dans le chemin cache | Important - Pas de blocage reseau | Faible |
| 6 | Pre-warm ConnectionManager au startup | Moyen - Connexions pretes plus tot | Faible |

---

## Phase 1 - Audit des flux Offline First

### Grille d'audit par ecran

#### Home (DiscoverScreen)
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme Offline-First ? |
|---------|-----------|----------------|---------|-------------|-------------------------|
| `getUnifiedOnDeck()` | `OnDeckRepositoryImpl` | DB (HomeContentDao) puis reseau | Cache-first + Network | `Flow<List<MediaItem>>` | **PARTIELLEMENT** - emit cache OK, mais resolution URL depend du ConnectionManager (vide au cold start) |
| `getUnifiedHubs()` | `HubsRepositoryImpl` | DB (HomeContentDao) puis PlexApiCache puis reseau | Cache-first + Network | `Flow<List<Hub>>` | **PARTIELLEMENT** - meme probleme de resolution URL |

#### Library
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme ? |
|---------|-----------|----------------|---------|-------------|-----------|
| `getLibraryContent()` | `LibraryRepositoryImpl` | DB via PagingSource + RemoteMediator | Paging 3 DB-first | `Flow<PagingData<MediaItem>>` | **OUI** - Paging 3 lit d'abord la DB, fetch reseau quand necessaire |
| `getLibraries()` | `LibraryRepositoryImpl` | DB (LibrarySectionDao) | DB-first | `Flow<List<LibrarySection>>` | **OUI** |

#### Detail (MediaDetail)
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme ? |
|---------|-----------|----------------|---------|-------------|-----------|
| `getMediaDetail()` | `MediaDetailRepositoryImpl` | PlexApiCache puis reseau | Cache-first (API cache) | `Result<MediaItem>` | **NON** - Depend du reseau ou du cache API (JSON), pas de la DB directement |
| `getSeasonEpisodes()` | `MediaDetailRepositoryImpl` | Reseau direct | Network-first | `Result<List<MediaItem>>` | **NON** |
| `getSimilarMedia()` | `MediaDetailRepositoryImpl` | Reseau direct | Network-first | `Result<List<MediaItem>>` | **NON** |

#### Favorites
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme ? |
|---------|-----------|----------------|---------|-------------|-----------|
| `getFavorites()` | `FavoritesRepositoryImpl` | DB (FavoriteDao + MediaDao) | DB-first | `Flow<List<MediaItem>>` | **OUI** |
| `toggleFavorite()` | `FavoritesRepositoryImpl` | DB puis sync async | DB-first | `Result<Boolean>` | **OUI** |

#### Search
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme ? |
|---------|-----------|----------------|---------|-------------|-----------|
| `searchAllServers()` | `SearchRepositoryImpl` | Reseau (federe) | Network-only | `Result<List<MediaItem>>` | **N/A** - recherche = besoin reseau |

#### IPTV
| Methode | Repository | Source initiale | Pattern | Type retour | Conforme ? |
|---------|-----------|----------------|---------|-------------|-----------|
| `getChannels()` | `IptvRepositoryImpl` | Memoire puis reseau | Memory-first | `Flow<List<IptvChannel>>` | **NON** - pas de persistance DB |

### Fichiers a verifier/modifier (pour Sonnet)

```
# Repositories critiques pour Offline-First
app/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt
app/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt
app/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt

# Pipeline images
app/src/main/java/com/chakir/plexhubtv/core/image/PlexImageKeyer.kt
app/src/main/java/com/chakir/plexhubtv/core/image/ImageModule.kt
app/src/main/java/com/chakir/plexhubtv/core/image/ImagePrefetchManager.kt
app/src/main/java/com/chakir/plexhubtv/core/util/ImageUtil.kt
app/src/main/java/com/chakir/plexhubtv/core/network/PlexImageHelper.kt
app/src/main/java/com/chakir/plexhubtv/core/util/MediaUrlResolver.kt

# Connexion & Startup
app/src/main/java/com/chakir/plexhubtv/core/network/ConnectionManager.kt
app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt

# Mapper
app/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt

# DB
core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt
core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt
core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt
core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt
```

---

## Phase 2 - Correction critique du PlexImageKeyer

### Probleme
Le cache key Coil inclut le hostname/baseUrl du serveur. Si le serveur est accessible via `192.168.1.100:32400` un jour et `xxx.plex.direct:32400` le lendemain, les images en disk cache ne sont PAS reutilisees.

### Solution
Modifier `PlexImageKeyer` pour produire des cles basees uniquement sur :
- Le chemin relatif de l'image (extrait du parametre `url` pour les URLs de transcodage)
- Les dimensions (`width`, `height`)
- **PAS** le hostname, **PAS** le token

### Instructions pour Sonnet

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/core/image/PlexImageKeyer.kt`

**Logique attendue :**
```
1. Detecter si l'URL est une URL de transcodage Plex (contient "/photo/:/transcode")
2. Si oui:
   a. Extraire le parametre "url" (le chemin relatif encode)
   b. URL-decoder ce chemin
   c. Retirer tout "X-Plex-Token=xxx" du chemin decode
   d. Construire la cle: "plex-img://{chemin_relatif_sans_token}?w={width}&h={height}"
3. Si non (URL directe sans transcodage):
   a. Extraire seulement le path de l'URL
   b. Retirer les parametres volatiles (X-Plex-Token, etc.)
   c. Construire la cle: "plex-img://{path}"
```

### Egalement corriger l'incoherence ImageUtil vs PlexImageHelper

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/core/network/PlexImageHelper.kt`

**Probleme:** La ligne `val encodedPath = Uri.encode("$path${if (path.contains("?")) "&" else "?"}X-Plex-Token=$token")` inclut le token dans le parametre `url` encode.

**Correction:** Ne PAS inclure le token dans le parametre `url`. Le token doit etre un parametre de query separe (comme le fait `ImageUtil.getOptimizedImageUrl()`).

Changer:
```kotlin
val encodedPath = Uri.encode("$path${if (path.contains("?")) "&" else "?"}X-Plex-Token=$token")
```
En:
```kotlin
val encodedPath = Uri.encode(path)
```

---

## Phase 3 - Persistance du cache ConnectionManager

### Probleme
`ConnectionManager._activeConnections` est un `MutableStateFlow<Map<String, String>>` en memoire. Perdu a chaque redemarrage.

### Solution
Persister les connexions validees dans `SettingsDataStore` et les restaurer au demarrage.

### Instructions pour Sonnet

**Fichier:** `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`

Ajouter:
```kotlin
private val CACHED_CONNECTIONS = stringPreferencesKey("cached_connections")

val cachedConnections: Flow<Map<String, String>>
    get() = dataStore.data.map { prefs ->
        val json = prefs[CACHED_CONNECTIONS] ?: "{}"
        // Deserialiser le JSON en Map<String, String>
    }

suspend fun saveCachedConnections(connections: Map<String, String>) {
    dataStore.edit { prefs ->
        prefs[CACHED_CONNECTIONS] = // Serialiser en JSON
    }
}
```

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/core/network/ConnectionManager.kt`

Modifier le constructeur pour injecter `SettingsDataStore` :
```kotlin
@Singleton
class ConnectionManager @Inject constructor(
    private val connectionTester: ServerConnectionTester,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    init {
        // Restaurer les connexions depuis DataStore au demarrage
        scope.launch {
            settingsDataStore.cachedConnections.first().forEach { (serverId, url) ->
                _activeConnections.update { it + (serverId to url) }
            }
        }
    }

    fun cacheConnection(serverId: String, url: String) {
        _activeConnections.update { it + (serverId to url) }
        // Persister en arriere-plan
        scope.launch {
            settingsDataStore.saveCachedConnections(_activeConnections.value)
        }
    }
}
```

---

## Phase 4 - Supprimer forceRefresh dans le chemin cache

### Probleme
`OnDeckRepositoryImpl.getActiveClients()` et `HubsRepositoryImpl.getActiveClients()` appellent :
```kotlin
authRepository.getServers(forceRefresh = true)
```
Cela force un appel reseau a plex.tv AVANT de pouvoir rafraichir OnDeck/Hubs. Inutile et bloquant.

### Instructions pour Sonnet

**Fichiers:**
- `app/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt`
- `app/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt`

Dans la methode `getActiveClients()` des DEUX fichiers, changer :
```kotlin
val servers = authRepository.getServers(forceRefresh = true).getOrNull()
```
En :
```kotlin
val servers = authRepository.getServers(forceRefresh = false).getOrNull()
```

Raison : les serveurs sont deja en cache DB depuis le dernier sync. Un `forceRefresh=false` retourne le cache memoire ou DB en millisecondes. Le refresh des serveurs est gere periodiquement par `LibrarySyncWorker` (toutes les 6h).

---

## Phase 5 - Pre-warm des connexions au startup

### Probleme
Au cold start, `ConnectionManager` ne connait aucune URL. Les images du cache sont emises avec `server.address` (qui peut etre incorrecte).

### Solution
Ajouter un pre-warm dans `PlexHubApplication.initializeAppInParallel()`.

### Instructions pour Sonnet

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`

Dans `initializeAppInParallel()`, ajouter un 5eme job parallele:
```kotlin
// Job 5: Pre-warm ConnectionManager avec les serveurs connus
val connectionWarmup = async(ioDispatcher) {
    try {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
        servers.forEach { server ->
            launch {
                connectionManager.findBestConnection(server)
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Connection warmup failed")
    }
}
```

**Note:** Ce job utilise les connexions restaurees depuis DataStore (Phase 3) comme premiere tentative, puis teste les autres candidats en parallele.

---

## Phase 6 - Stocker des URLs resolues dans MediaEntity (optionnel mais recommande)

### Probleme
`MediaEntity.thumbUrl` stocke `/library/metadata/1/thumb` (relatif). A chaque lecture, on doit reconstruire l'URL complete. Si on n'a pas le bon baseUrl, les images ne chargent pas.

### Solution
Ajouter des champs `resolvedThumbUrl` et `resolvedArtUrl` a `MediaEntity` pour stocker les URLs completes de transcodage. Ces URLs sont calculees au moment de l'ecriture (sync, refresh) quand on connait le baseUrl correct.

### Instructions pour Sonnet

**Cette phase est OPTIONNELLE.** Les phases 2-5 resolvent le probleme principal. Cette phase est un renforcement supplementaire.

**Fichier:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt`

Ajouter les champs:
```kotlin
val resolvedThumbUrl: String? = null,
val resolvedArtUrl: String? = null,
val resolvedBaseUrl: String? = null, // Pour detecter si l'URL est encore valide
```

**Fichier:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt`

Ajouter une migration 19->20 :
```kotlin
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media ADD COLUMN resolvedThumbUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media ADD COLUMN resolvedArtUrl TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media ADD COLUMN resolvedBaseUrl TEXT DEFAULT NULL")
    }
}
```

Mettre a jour la version DB a 20 et ajouter la migration.

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt`

Dans `mapDtoToEntity()`, NE PAS remplir les champs resolved (ce n'est pas le mapper qui a le baseUrl correct). Les remplir dans les repositories au moment du upsert.

**Fichiers:** `OnDeckRepositoryImpl.kt`, `HubsRepositoryImpl.kt`, `SyncRepositoryImpl.kt`

Apres avoir cree les entities, avant `mediaDao.upsertMedia()`, enrichir chaque entity:
```kotlin
val resolvedEntity = entity.copy(
    resolvedThumbUrl = getOptimizedImageUrl(
        "${baseUrl}${entity.thumbUrl}?X-Plex-Token=$token", 300, 450
    ),
    resolvedArtUrl = getOptimizedImageUrl(
        "${baseUrl}${entity.artUrl}?X-Plex-Token=$token", 1280, 720
    ),
    resolvedBaseUrl = baseUrl,
)
```

**Fichier:** `app/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt`

Dans `mapEntityToDomain()`, utiliser les URLs resolues si disponibles:
```kotlin
thumbUrl = entity.resolvedThumbUrl ?: entity.thumbUrl,
artUrl = entity.resolvedArtUrl ?: entity.artUrl,
```

---

## Phase 7 - FTS & Indexes Room

### Ou FTS serait utile
- **Search global** (`SearchRepositoryImpl`) : actuellement network-only, mais pour un mode offline, FTS sur `title`, `summary`, `genres` serait pertinent.
- **Suggestions offline** : recherche locale instantanee.

### Ou FTS n'est PAS necessaire
- Home/OnDeck/Hubs : queries simples avec JOIN sur `home_content`, pas de recherche textuelle.
- Libraries : pagination par `pageOffset`, tri par colonnes indexees.

### Index existants (deja bons)
```
media(serverId, librarySectionId, filter, sortOrder, pageOffset) - UNIQUE
media(guid)
media(type, addedAt)
media(imdbId)
media(tmdbId)
media(serverId, librarySectionId)
media(unificationId)
media(updatedAt)
media(parentRatingKey)
```

### Index a ajouter
```sql
-- Pour les requetes HomeContentDao qui font JOIN media ON ratingKey + serverId
CREATE INDEX IF NOT EXISTS idx_media_ratingkey_serverid ON media(ratingKey, serverId);

-- Pour les requetes de recherche locale (si FTS pas implemente)
CREATE INDEX IF NOT EXISTS idx_media_title ON media(title COLLATE NOCASE);
```

### Instructions pour Sonnet

**Fichier:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt`

Verifier que les `@Index` incluent deja `(ratingKey, serverId)`. Si non, ajouter dans l'annotation `@Entity`:
```kotlin
Index(value = ["ratingKey", "serverId"]),
```

**Fichier:** `core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt`

Ajouter dans la migration (ou dans le callback `onOpen`) :
```kotlin
db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_title ON media(title COLLATE NOCASE)")
```

---

## Phase 8 - Autres optimisations data

### DTO "slim" pour les listes
Actuellement `MediaEntity` porte 30+ champs dont `mediaParts` (JSON serialise). Pour les listes Home/Hubs, on n'a besoin que de :
- `ratingKey`, `serverId`, `title`, `type`, `year`
- `thumbUrl`, `artUrl`, `parentThumb`, `grandparentThumb`
- `viewOffset`, `duration`, `contentRating`, `rating`, `audienceRating`

**Instructions pour Sonnet:**

Creer une projection dans `HomeContentDao` :
```kotlin
@Query("""
    SELECT m.ratingKey, m.serverId, m.title, m.type, m.year,
           m.thumbUrl, m.artUrl, m.parentThumb, m.grandparentThumb,
           m.resolvedThumbUrl, m.resolvedArtUrl,
           m.viewOffset, m.duration, m.contentRating, m.rating, m.audienceRating,
           m.parentTitle, m.grandparentTitle
    FROM home_content hc
    INNER JOIN media m ON m.ratingKey = hc.itemRatingKey AND m.serverId = hc.itemServerId
    WHERE hc.type = :type AND hc.hubIdentifier = :hubIdentifier
    ORDER BY hc.orderIndex ASC
""")
suspend fun getHomeMediaItemsSlim(type: String, hubIdentifier: String): List<SlimMediaProjection>
```

Creer la data class `SlimMediaProjection` avec seulement les champs necessaires.

### Strategie de rafraichissement
- Ne PAS invalider les hubs plus souvent que toutes les 30 minutes (actuellement PlexApiCache TTL = 1h, c'est bien).
- Batcher les upserts : actuellement `mediaDao.upsertMedia(entities)` est deja batche. OK.
- Ne PAS faire `homeContentDao.clearHomeContent()` puis `insertHomeContent()` de maniere non-transactionnelle. Wrapper dans une `@Transaction`.

---

## Checklist pour Sonnet - Taches concretes

### Priorite 1 - Impact immediat (resout le probleme principal)

- [ ] **[PlexImageKeyer]** `app/src/main/java/com/chakir/plexhubtv/core/image/PlexImageKeyer.kt`
  - Modifier `key()` pour extraire le chemin relatif des URLs de transcodage et ignorer le hostname
  - Tester avec des URLs de type `http://192.168.1.100:32400/photo/:/transcode?width=300&height=450&url=%2Flibrary%2Fmetadata%2F1%2Fthumb&X-Plex-Token=abc`
  - La cle doit etre identique quelle que soit l'adresse du serveur

- [ ] **[PlexImageHelper]** `app/src/main/java/com/chakir/plexhubtv/core/network/PlexImageHelper.kt`
  - Retirer le token du parametre `url` encode (ligne `val encodedPath = ...`)
  - Aligner avec le comportement de `ImageUtil.getOptimizedImageUrl()`

- [ ] **[ConnectionManager - Persistance]** `app/src/main/java/com/chakir/plexhubtv/core/network/ConnectionManager.kt`
  - Injecter `SettingsDataStore`
  - Restaurer les connexions depuis DataStore dans `init {}`
  - Persister dans `cacheConnection()`

- [ ] **[SettingsDataStore]** `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`
  - Ajouter `cachedConnections` (get/save) avec serialisation JSON Map<String,String>

- [ ] **[OnDeckRepositoryImpl]** `app/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt`
  - Changer `forceRefresh = true` en `forceRefresh = false` dans `getActiveClients()`

- [ ] **[HubsRepositoryImpl]** `app/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt`
  - Changer `forceRefresh = true` en `forceRefresh = false` dans `getActiveClients()`

### Priorite 2 - Renforcement

- [ ] **[PlexHubApplication]** `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`
  - Ajouter le pre-warm ConnectionManager dans `initializeAppInParallel()`

- [ ] **[MediaEntity + Migration]** Ajouter `resolvedThumbUrl`, `resolvedArtUrl`, `resolvedBaseUrl`
  - Migration 19->20
  - Mettre a jour `PlexDatabase` version et entities

- [ ] **[MediaMapper]** Utiliser `resolvedThumbUrl` dans `mapEntityToDomain()`

- [ ] **[Repositories]** Remplir les champs resolved lors des upserts dans OnDeck, Hubs, Sync repos

### Priorite 3 - Optimisation fine

- [ ] **[Index Room]** Ajouter `INDEX(ratingKey, serverId)` sur `media` si absent
- [ ] **[Index Room]** Ajouter `INDEX(title COLLATE NOCASE)` pour recherche locale
- [ ] **[HomeContentDao]** Creer une projection slim pour les requetes home
- [ ] **[HomeContentDao]** Wrapper clear+insert dans @Transaction
