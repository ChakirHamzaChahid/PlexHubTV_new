# Plan: Integrate PlexHub Backend as Third Server Type

## Context

The PlexHub Python backend (FastAPI) is operational. It syncs Xtream catalogs, enriches with TMDB IDs, and checks stream health. Now we need the Android app to consume it via REST API — treating it as a third server type alongside Plex and direct Xtream.

**Key integration doc:** `PLEXHUB_BACKEND_INTEGRATION_GUIDE.md` — defines all API endpoints, response formats (camelCase JSON matching MediaEntity), and data flows.

**Design decisions (from prior conversation):**
- Backend is **optional** — existing direct Xtream code stays as fallback
- Backend provides **stream URLs** (`GET /api/stream/{ratingKey}`)
- **No auth** (LAN-only)
- Media synced from backend uses `serverId = "backend_<backendId>"` (app remaps from backend's `xtream_*` to avoid collision with local Xtream sync)
- Backend manages its own Xtream accounts — the app just syncs media from it

---

## Conformité Audit V4

### A1 — Dispatch factorisation (MediaDetailRepositoryImpl / PlayerControlViewModel)

Le pattern existant pour `xtream_` utilise déjà des **helpers privés** (`getXtreamMediaDetail()`, `getCachedXtreamSeriesDetail()`) appelés depuis des dispatches plats `if/else if` en entrée de chaque méthode. Le code backend_ suivra **exactement le même pattern** :

- **MediaDetailRepositoryImpl** : ajout d'un helper privé `getBackendMediaDetail()` (même structure que `getXtreamMediaDetail()` aux lignes 360-401) + un helper `getBackendEpisodes()`. Les 5 dispatch points restent des `if/else if` plats en entrée — pas d'imbrication supplémentaire. Complexité cyclomatique de chaque méthode publique : +1 branche (identique à l'ajout xtream_ original).
- **PlayerControlViewModel** : factorisation du code dupliqué init/loadOrPlayMedia en un helper privé `resolveAndPlayDirectStream(ratingKey, serverId, urlBuilder)` pour éviter de dupliquer le launch + error handling. Les 3 dispatches deviennent une seule ligne chacun.

**Résultat** : les classes ne deviennent pas des "hubs" — chaque nouveau chemin est encapsulé dans un helper privé, le dispatch reste plat et lisible.

### A2 — Dispatchers injectés (F-05)

`BackendRepositoryImpl` utilisera **exclusivement** le dispatcher injecté `@IoDispatcher` — aucun `Dispatchers.IO` / `Dispatchers.Main` en dur. Signature du constructeur :

```kotlin
@Singleton
class BackendRepositoryImpl @Inject constructor(
    private val backendApiClient: BackendApiClient,
    private val backendServerDao: BackendServerDao,
    private val mediaDao: MediaDao,
    private val mapper: BackendMediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackendRepository
```

Tous les appels réseau et Room dans cette classe seront wrappés en `withContext(ioDispatcher)` ou `.flowOn(ioDispatcher)` — pattern identique à `XtreamVodRepositoryImpl` (ligne 28, 32, 41, 71) et `MediaDetailRepositoryImpl` (ligne 35).

### A3 — LibrarySyncWorker et S-10

Le worker actuel (lignes 194-220 + 240-242) implémente déjà partiellement S-10 :
- Si **tous** les serveurs Plex échouent → `Result.retry()` (ligne 197)
- Exception fatale → `appError.isRetryable()` détermine retry vs failure (ligne 242)
- Sinon → `Result.success()` (ligne 220)

Le bloc backend adopte le **pattern "best effort"** existant (identique à Xtream, lignes 159-181) :
- Un backend en échec est loggé mais **n'impacte pas** le Result final du worker
- Les autres sources (Plex, Xtream, Watchlist) continuent normalement
- Justification : un backend down ne doit pas bloquer la sync Plex qui est la source principale

**Porte ouverte pour S-10 complet** : plus tard, on pourrait ajouter un compteur `backendFailureCount` et l'intégrer au calcul final du Result (si tous Plex + tous Xtream + tous Backend échouent → `Result.retry()`). Le code actuel le permet sans refactor car les try/catch par source sont déjà isolés.

### A4 — Module boundaries (F-06 / F-07)

| Module | Autorisé pour Backend | Interdit |
|--------|----------------------|----------|
| `core:model` | `BackendServer` (pure Kotlin data class, zéro dépendance Android/Retrofit/Compose), `BackendConnectionInfo` (idem) | Annotations Retrofit, Compose, Room |
| `core:network` | `BackendApiService`, `BackendDto`, `BackendApiClient` (Retrofit, OkHttp, Gson) | Rien n'en sort vers core:model |
| `core:database` | `BackendServerEntity`, `BackendServerDao` (Room) | — |
| `domain` | `BackendRepository` interface (retourne `BackendServer`, `BackendConnectionInfo`, `MediaItem`, `String`, `Int`) | Pas de BackendDto, pas de MediaEntity, pas de BackendHealthResponse |
| `data` | `BackendRepositoryImpl`, `BackendMediaMapper` (conversion DTO→Entity→Domain) | — |
| `core:common` | **Aucun ajout** — pas de nouvelle classe backend dans ce module | Éviter d'en faire un "kitchen sink" (audit F-07) |

`BackendRepository` (domain) ne retourne **aucun** DTO réseau ni Entity Room — uniquement des types de `core:model` (`BackendServer`, `BackendConnectionInfo`, `MediaItem`) ou des primitives (`String`, `Int`).

### A5 — Mapping serverId : solution propre (pas de hack resolvedBaseUrl)

**Problème** : Le backend retourne `serverId: "xtream_05fd75e9"` (l'ID Xtream account côté backend). On stocke `"backend_<backendId>"` dans Room pour éviter collision avec le Xtream direct. Mais pour les appels API backend (`/api/stream/{rk}?server_id=...`), il faut l'original.

**Solution retenue** : Ajouter un champ dédié `sourceServerId: String? = null` à `MediaEntity` (dans la même migration 33→34).

- **Clair et explicite** : le nom documente l'intention
- **Nullable** : `null` pour Plex et Xtream direct (pas besoin), rempli uniquement pour `backend_*`
- **Pas de hack** : on n'abuse pas de `resolvedBaseUrl` (qui est un URL, pas un ID)
- **Compatible audit** : pas de "surprise field" — le champ a une sémantique claire

**SYNC TRAP** (rappel MEMORY.md) : tout nouveau champ dans `MediaEntity` doit aussi être ajouté à la liste de colonnes explicites dans `LibraryRepositoryImpl.getLibraryContent()` unified SQL. À faire dans Step 4.

**Alternative rejetée** : Stocker dans `resolvedBaseUrl` — sémantiquement incorrect, le champ est un URL pas un ID serveur, source de confusion pour les futurs développeurs.

---

## Step 1: Database — BackendServerEntity + DAO + Migration 33→34

**New:** `core/database/.../BackendServerEntity.kt`
```kotlin
@Entity(tableName = "backend_servers")
data class BackendServerEntity(
    @PrimaryKey val id: String,       // Auto-generated UUID or user label hash
    val label: String,                // "My PlexHub Backend"
    val baseUrl: String,              // "http://192.168.1.50:8000"
    val isActive: Boolean = true,
    val lastSyncedAt: Long = 0,
)
```

**New:** `core/database/.../BackendServerDao.kt`
Pattern: mirror `XtreamAccountDao.kt` (`core/database/.../XtreamAccountDao.kt:8-22`)
```kotlin
@Dao
interface BackendServerDao {
    @Query("SELECT * FROM backend_servers")
    fun observeAll(): Flow<List<BackendServerEntity>>
    @Query("SELECT * FROM backend_servers WHERE id = :id")
    suspend fun getById(id: String): BackendServerEntity?
    @Upsert
    suspend fun upsert(entity: BackendServerEntity)
    @Query("DELETE FROM backend_servers WHERE id = :id")
    suspend fun delete(id: String)
}
```

**Modify:** `core/database/.../PlexDatabase.kt` — add `BackendServerEntity::class` to entities array, add `abstract fun backendServerDao(): BackendServerDao`, bump version to 34.

**Modify:** `core/database/.../MediaEntity.kt` — add field:
```kotlin
// Original serverId from backend (e.g. "xtream_05fd75e9"), used for backend API calls
// Only populated for media synced from a PlexHub Backend server
val sourceServerId: String? = null,
```

**Modify:** `core/database/.../DatabaseModule.kt`:
- Add `MIGRATION_33_34` with **two operations** (backend_servers table + sourceServerId column):
```kotlin
private val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS backend_servers (
                id TEXT NOT NULL PRIMARY KEY,
                label TEXT NOT NULL,
                baseUrl TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                lastSyncedAt INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("ALTER TABLE media ADD COLUMN sourceServerId TEXT DEFAULT NULL")
    }
}
```
- Add to `.addMigrations(...)` chain (line ~355)
- Add `@Provides fun provideBackendServerDao(db: PlexDatabase) = db.backendServerDao()`

---

## Step 2: Network — BackendApiService + DTOs + Client

**New:** `core/network/.../backend/BackendApiService.kt`
Retrofit interface matching the API doc section 7.7:
```kotlin
interface BackendApiService {
    @GET("api/health")
    suspend fun getHealth(): BackendHealthResponse
    @GET("api/accounts")
    suspend fun getAccounts(): List<BackendAccountResponse>
    @GET("api/media/movies")
    suspend fun getMovies(@Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0, @Query("sort") sort: String = "added_desc"): BackendMediaListResponse
    @GET("api/media/shows")
    suspend fun getShows(@Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0): BackendMediaListResponse
    @GET("api/media/episodes")
    suspend fun getEpisodes(@Query("parent_rating_key") parentRatingKey: String, @Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0): BackendMediaListResponse
    @GET("api/media/{ratingKey}")
    suspend fun getMediaDetail(@Path("ratingKey") ratingKey: String, @Query("server_id") serverId: String): BackendMediaItemDto
    @GET("api/stream/{ratingKey}")
    suspend fun getStreamUrl(@Path("ratingKey") ratingKey: String, @Query("server_id") serverId: String): BackendStreamResponse
    @POST("api/sync/xtream")
    suspend fun triggerSync(@Body request: BackendSyncRequest): BackendSyncJobResponse
    @GET("api/sync/status/{jobId}")
    suspend fun getSyncStatus(@Path("jobId") jobId: String): BackendSyncStatusResponse
}
```

**New:** `core/network/.../backend/BackendDto.kt`
Pydantic-like DTOs matching API doc section 7.6:
- `BackendMediaListResponse(items, total, hasMore)`
- `BackendMediaItemDto` — all fields from the API doc movie response (§6.3)
- `BackendStreamResponse(url, expiresAt)`
- `BackendHealthResponse(status, version, accounts, totalMedia, enrichedMedia, brokenStreams, lastSyncAt)`
- `BackendAccountResponse(id, label, baseUrl, port, username, status, ...)`
- `BackendSyncRequest(accountId, force)`, `BackendSyncJobResponse(jobId)`, `BackendSyncStatusResponse(status, progress)`

**New:** `core/network/.../backend/BackendApiClient.kt`
Pattern: mirror `XtreamApiClient.kt` (`core/network/.../xtream/XtreamApiClient.kt:12-29`):
```kotlin
@Singleton
class BackendApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val cache = ConcurrentHashMap<String, BackendApiService>()
    fun getService(baseUrl: String): BackendApiService {
        val key = baseUrl.trimEnd('/') + "/"
        return cache.getOrPut(key) {
            Retrofit.Builder().baseUrl(key).client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(BackendApiService::class.java)
        }
    }
}
```

---

## Step 3: Domain — BackendRepository interface + Models

**New:** `core/model/.../BackendServer.kt`
```kotlin
data class BackendServer(val id: String, val label: String, val baseUrl: String, val isActive: Boolean, val lastSyncedAt: Long)
```

**New:** `core/model/.../BackendConnectionInfo.kt`
```kotlin
/** Domain model for backend health check result — no Retrofit/DTO dependency (F-06) */
data class BackendConnectionInfo(val totalMedia: Int, val enrichedMedia: Int, val version: String)
```

**New:** `domain/.../repository/BackendRepository.kt`
```kotlin
interface BackendRepository {
    fun observeServers(): Flow<List<BackendServer>>
    suspend fun addServer(label: String, baseUrl: String): Result<BackendServer>
    suspend fun removeServer(id: String)
    suspend fun testConnection(baseUrl: String): Result<BackendConnectionInfo>  // Domain model, NOT DTO (F-06)
    suspend fun syncMedia(backendId: String): Result<Int>
    suspend fun getStreamUrl(ratingKey: String, backendServerId: String): Result<String>
    suspend fun getEpisodes(parentRatingKey: String, backendServerId: String): Result<List<MediaItem>>  // Domain model, NOT Entity (F-06)
    suspend fun getMediaDetail(ratingKey: String, backendServerId: String): Result<MediaItem>  // Domain model, NOT Entity (F-06)
}
```

**Note F-06** : L'interface ne référence que des types de `core:model` (`BackendServer`, `BackendConnectionInfo`, `MediaItem`) et des primitives. `BackendRepositoryImpl` (data layer) gère la conversion DTO→Entity→Domain en interne.

---

## Step 4: Data — BackendRepositoryImpl + Mapper

**New:** `data/.../mapper/BackendMediaMapper.kt`
Maps `BackendMediaItemDto` → `MediaEntity` (pour Room) et `BackendMediaItemDto` → `MediaItem` (pour domain). Key logic:
- Remap `serverId`: `dto.serverId` ("xtream_05fd75e9") → `entity.serverId = "backend_<backendId>"`
- Store original: `entity.sourceServerId = dto.serverId` (le serverId original pour les appels API backend)
- Copy all other fields 1:1 (backend response matches MediaEntity schema)
- `resolvedThumbUrl = dto.thumbUrl` (backend provides full URLs)
- `resolvedArtUrl = dto.artUrl`
- `mediaParts = dto.mediaParts` (always `"[]"` for backend Xtream content)
- `unificationId` : copier tel quel depuis le DTO (le backend le pré-calcule, format `imdb://ttXXX` ou `tmdb://XXX`)
- Calculer `historyGroupKey` et `displayRating` comme dans `XtreamMediaMapper`

**New:** `data/.../repository/BackendRepositoryImpl.kt`

Constructor avec dispatcher injecté (conformité F-05) :
```kotlin
@Singleton
class BackendRepositoryImpl @Inject constructor(
    private val backendApiClient: BackendApiClient,
    private val backendServerDao: BackendServerDao,
    private val mediaDao: MediaDao,
    private val mapper: BackendMediaMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackendRepository
```

Core logic (tout dans `withContext(ioDispatcher)`) :
- **`addServer()`**: calls `GET /api/health` to validate, maps response → `BackendConnectionInfo`, generates UUID, saves `BackendServerEntity`
- **`syncMedia()`**: paginated fetch of movies + shows from backend, maps DTOs → `MediaEntity` (with remapped serverId + sourceServerId), upserts to Room. Pattern: `XtreamVodRepositoryImpl.syncMovies()` (lignes 40-58)
- **`getStreamUrl()`**: read `sourceServerId` from MediaEntity via Room, call `GET /api/stream/{ratingKey}?server_id=<sourceServerId>`, return URL string
- **`getEpisodes()`**: call `GET /api/media/episodes?parent_rating_key=...`, map DTO→Entity→Domain (return `List<MediaItem>`)
- **`getMediaDetail()`**: call `GET /api/media/{ratingKey}?server_id=...`, map DTO→Entity→Domain (return `MediaItem`)
- **`testConnection()`**: call `GET /api/health`, map `BackendHealthResponse` DTO → `BackendConnectionInfo` domain model

**Key sync flow:**
```
1. GET /api/media/movies?limit=500&offset=0 → upsert batch → offset += 500 → repeat until !hasMore
2. GET /api/media/shows?limit=500&offset=0 → same pagination loop
3. Differential cleanup: compare ratingKeys in Room vs API, delete stale
4. Update backend.lastSyncedAt
```

**Mapping serverId (solution propre — cf. section A5)** :
- DTO `serverId: "xtream_05fd75e9"` → Entity `serverId = "backend_<backendId>"` + `sourceServerId = "xtream_05fd75e9"`
- Pour les appels API backend : lire `sourceServerId` depuis Room (ex: `mediaDao.getMedia(ratingKey, serverId)?.sourceServerId`)
- Le champ `sourceServerId` est nullable — `null` pour Plex/Xtream, rempli uniquement pour backend

**SYNC TRAP** : Ajouter `sourceServerId` à la liste de colonnes explicites dans `LibraryRepositoryImpl.getLibraryContent()` (unified SQL).

---

## Step 5: Backend URL Builder + Player Dispatch

**New:** `app/.../feature/player/url/BackendUrlBuilder.kt`
```kotlin
@Singleton
class BackendUrlBuilder @Inject constructor(
    private val backendRepository: BackendRepository,
) {
    suspend fun buildUrl(ratingKey: String, serverId: String): String? {
        return backendRepository.getStreamUrl(ratingKey, serverId).getOrNull()
    }
}
```

**Modify:** `app/.../feature/player/PlayerControlViewModel.kt`
Add `BackendUrlBuilder` to constructor.

**Factorisation (conformité A1)** : extraire un helper privé partagé pour éviter la duplication init/loadOrPlayMedia :
```kotlin
/** Shared helper — resolves stream URL via a suspend builder and plays it (used by xtream_ and backend_) */
private fun resolveAndPlayDirectStream(
    ratingKey: String, serverId: String,
    urlBuilder: suspend () -> String?,
    cachedMedia: MediaItem? = null,
) {
    playerController.initialize(ratingKey, serverId, null, startOffset)
    viewModelScope.launch {
        val url = urlBuilder()
        if (url != null) {
            playerController.playDirectStream(url, cachedMedia)
        } else {
            playerController.updateState { it.copy(error = "Failed to get stream URL", isBuffering = false) }
        }
    }
}
```

Les 3 dispatch points deviennent :
1. **init block** : `resolveAndPlayDirectStream(ratingKey, serverId, { backendUrlBuilder.buildUrl(ratingKey, serverId) }, cachedItem)`
2. **SelectQuality** : skip quality for `backend_` (même no-op que `xtream_`, ligne 92)
3. **loadOrPlayMedia** : `resolveAndPlayDirectStream(media.ratingKey, media.serverId, { backendUrlBuilder.buildUrl(...) }, media)`

**Résultat** : le code xtream_ existant peut aussi utiliser ce helper (refactor optionnel), pas de duplication de launch+error handling.

---

## Step 6: MediaDetailRepository — Backend Dispatch

**Modify:** `data/.../repository/MediaDetailRepositoryImpl.kt`
Add `BackendRepository` to constructor (à côté de `xtreamSeriesRepository` et `xtreamVodRepository`, ligne 33-34).

**Factorisation (conformité A1)** : chaque dispatch `backend_` est isolé dans un **helper privé dédié** — même pattern que les helpers xtream_ existants (`getXtreamMediaDetail()` lignes 360-401, `getCachedXtreamSeriesDetail()` lignes 407-422). Les méthodes publiques restent des dispatches plats `if/else if` sans imbrication :

```kotlin
// Pattern de dispatch plat — chaque méthode publique
override suspend fun getMediaDetail(ratingKey: String, serverId: String): Result<MediaItem> {
    if (serverId.startsWith("backend_")) return getBackendMediaDetail(ratingKey, serverId)
    if (serverId.startsWith("xtream_")) return getXtreamMediaDetail(ratingKey, serverId)
    return getPlexMediaDetail(ratingKey, serverId)  // default Plex path
}
```

**5 dispatch points** (identique à la structure xtream_) :

1. **`getMediaDetail()`** → `getBackendMediaDetail()` (helper privé)
2. **`getSeasonEpisodes()`** → `getBackendEpisodes()` (helper privé) : Room first, fallback `backendRepository.getEpisodes()`
3. **`getShowSeasons()`** → parse `series_X` prefix, fetch episodes from backend, group by `parentIndex` to build virtual season items
4. **`getSimilarMedia()`** → return `emptyList()` (no similar API on backend)
5. **`getBackendMediaDetail()`** (nouveau helper privé) :
   - Room first (comme `getXtreamMediaDetail()`)
   - Pas d'enrichissement on-demand (backend déjà enrichi)
   - Season handling : parse `season_X_Y` → build virtual season item
   - Series handling : return from Room or fetch from backend API

**Complexité ajoutée** : +1 branche par méthode publique, +2 helpers privés. La classe passe de ~420 lignes à ~480 lignes — reste raisonnable et aligné avec le pattern xtream_ existant.

---

## Step 7: LibrarySyncWorker — Backend Sync Step

**Modify:** `app/.../work/LibrarySyncWorker.kt`
Add `BackendRepository` to constructor (line 48, via `@Assisted` + DI). After Xtream sync block (line 181), before Watchlist sync (line 183) :

```kotlin
// SYNC FROM BACKEND SERVERS
try {
    val backends = backendRepository.observeServers().first()
    backends.filter { it.isActive }.forEach { backend ->
        try {
            updateNotification("Syncing Backend: ${backend.label}...")
            val result = backendRepository.syncMedia(backend.id)
            if (result.isFailure) {
                Timber.w("✗ [Backend:${backend.label}] Sync failed: ${result.exceptionOrNull()?.message}")
            } else {
                Timber.d("✓ [Backend:${backend.label}] Synced ${result.getOrDefault(0)} items")
            }
        } catch (e: Exception) {
            Timber.e("✗ [Backend:${backend.label}] Exception: ${e.message}")
        }
    }
} catch (e: Exception) {
    Timber.e("✗ Backend sync failed: ${e.message}")
}
```

**Intégration S-10 (conformité A3)** :
- Le bloc backend suit le **pattern "best effort"** identique au bloc Xtream (lignes 159-181) : erreurs loggées, pas d'impact sur le `Result` final.
- Le `Result` du worker est déterminé uniquement par les serveurs Plex (lignes 194-220) + le catch global (lignes 240-242).
- **Rationale** : le backend est un service optionnel — son indisponibilité ne doit pas déclencher un `Result.retry()` qui replanifierait toute la sync (Plex + Xtream + Backend).
- **Future improvement** : si on veut intégrer les échecs backend dans le calcul du Result (S-10 complet), ajouter un `var backendFailureCount = 0` incrémenté dans le catch, et l'inclure dans la condition ligne 194 (`if (failureCount == servers.size && backendFailureCount == backends.size ...)`). Le code est structuré pour le permettre sans refactor.

---

## Step 8: Settings UI — Backend Server Configuration

**Modify:** `app/.../feature/settings/SettingsUiState.kt`
Add state fields:
```kotlin
val backendServers: List<BackendServer> = emptyList(),
val isTestingBackend: Boolean = false,
val backendConfigMessage: String? = null,
val isSyncingBackend: Boolean = false,
val backendSyncMessage: String? = null,
```
Add actions:
```kotlin
data class AddBackendServer(val label: String, val url: String) : SettingsAction
data class RemoveBackendServer(val id: String) : SettingsAction
data class TestBackendConnection(val url: String) : SettingsAction
data object SyncBackend : SettingsAction
```

**Modify:** `app/.../feature/settings/SettingsViewModel.kt`
- Add `BackendRepository` to constructor (line 51)
- Observe `backendRepository.observeServers()` in `observeSettings()` (add to flow group)
- Handle new actions:
  - `AddBackendServer`: validate URL with health check, save via repo
  - `RemoveBackendServer`: delete via repo + delete media from Room
  - `TestBackendConnection`: call health endpoint, show result
  - `SyncBackend`: call `backendRepository.syncMedia()` for each server (pattern: `SyncXtream` handler at lines 302-338)

**Modify:** `app/.../feature/settings/SettingsScreen.kt`
Add new section BEFORE the IPTV section (lines 419-480). Pattern: mirror IPTV section structure.
```
[SettingsSection: "PlexHub Backend"]
  [Per configured backend:]
    [SettingsTile: backend.label / subtitle=backend.baseUrl / icon=Cloud]
      → Long press to remove (with confirmation dialog)
  [SettingsTile: "Add Backend" / icon=AddCircle]
    → Shows AlertDialog with label + URL OutlinedTextField fields (pattern: API Keys dialog at lines 621-701)
  [SettingsTile: "Sync from Backend" / icon=Sync]
    → Only visible if backends configured
    → Shows spinner when syncing (pattern: Xtream sync tile at line 438)
```

---

## Step 9: DI Wiring

**Modify:** `data/.../di/RepositoryModule.kt`
Add binding (pattern: lines 149-165):
```kotlin
@Binds @Singleton
abstract fun bindBackendRepository(impl: BackendRepositoryImpl): BackendRepository
```

**Modify:** `core/database/.../DatabaseModule.kt`
Add provider (pattern: lines 416-419):
```kotlin
@Provides
fun provideBackendServerDao(database: PlexDatabase): BackendServerDao = database.backendServerDao()
```

---

## Step 10: String Resources

**Modify:** `app/src/main/res/values/strings.xml` + `values-fr/strings.xml`

EN:
```xml
<string name="settings_backend_section">PlexHub Backend</string>
<string name="settings_add_backend">Add Backend Server</string>
<string name="settings_backend_label">Server Name</string>
<string name="settings_backend_url">Server URL</string>
<string name="settings_backend_url_hint">http://192.168.1.50:8000</string>
<string name="settings_backend_sync">Sync from Backend</string>
<string name="settings_backend_sync_subtitle">Fetch enriched media from backend</string>
<string name="settings_backend_testing">Testing connection…</string>
<string name="settings_backend_connected">Connected (%1$d media, %2$d enriched)</string>
<string name="settings_backend_unreachable">Backend unreachable</string>
<string name="settings_backend_remove_confirm">Remove this backend server?</string>
<string name="settings_backend_sync_done">Synced %1$d items from backend</string>
```

FR:
```xml
<string name="settings_backend_section">Backend PlexHub</string>
<string name="settings_add_backend">Ajouter un serveur backend</string>
<string name="settings_backend_label">Nom du serveur</string>
<string name="settings_backend_url">URL du serveur</string>
<string name="settings_backend_url_hint">http://192.168.1.50:8000</string>
<string name="settings_backend_sync">Synchroniser depuis le backend</string>
<string name="settings_backend_sync_subtitle">Récupérer les médias enrichis depuis le backend</string>
<string name="settings_backend_testing">Test de connexion…</string>
<string name="settings_backend_connected">Connecté (%1$d médias, %2$d enrichis)</string>
<string name="settings_backend_unreachable">Backend inaccessible</string>
<string name="settings_backend_remove_confirm">Supprimer ce serveur backend ?</string>
<string name="settings_backend_sync_done">%1$d éléments synchronisés depuis le backend</string>
```

---

## Files Summary

**New files (11):**
| File | Location | Module |
|------|----------|--------|
| `BackendServerEntity.kt` | `core/database/` | core:database |
| `BackendServerDao.kt` | `core/database/` | core:database |
| `BackendApiService.kt` | `core/network/.../backend/` | core:network |
| `BackendDto.kt` | `core/network/.../backend/` | core:network |
| `BackendApiClient.kt` | `core/network/.../backend/` | core:network |
| `BackendServer.kt` | `core/model/` | core:model (pure Kotlin) |
| `BackendConnectionInfo.kt` | `core/model/` | core:model (pure Kotlin) |
| `BackendRepository.kt` | `domain/.../repository/` | domain |
| `BackendRepositoryImpl.kt` | `data/.../repository/` | data |
| `BackendMediaMapper.kt` | `data/.../mapper/` | data |
| `BackendUrlBuilder.kt` | `app/.../feature/player/url/` | app |

**Modified files (11):**
| File | Change | Module |
|------|--------|--------|
| `MediaEntity.kt` | Add `sourceServerId` field | core:database |
| `PlexDatabase.kt` | Add entity + DAO + version 34 | core:database |
| `DatabaseModule.kt` | Migration 33→34 (table + ALTER) + DAO provider | core:database |
| `RepositoryModule.kt` | Bind BackendRepository | data |
| `MediaDetailRepositoryImpl.kt` | `backend_` dispatch (5 points) + 2 helpers privés | data |
| `PlayerControlViewModel.kt` | `backend_` dispatch (3 points) + helper factorisé | app |
| `LibrarySyncWorker.kt` | Backend sync step (best effort, S-10 compatible) | app |
| `SettingsUiState.kt` | Backend state + actions | app |
| `SettingsViewModel.kt` | Backend action handlers | app |
| `SettingsScreen.kt` | Backend UI section | app |
| `strings.xml` (EN + FR) | String resources | app |

**Note** : `LibraryRepositoryImpl.kt` devra aussi être modifié pour ajouter `sourceServerId` à la liste de colonnes explicites du SQL unifié (SYNC TRAP).

---

## Implementation Order

1. Steps 1-2: DB + Network layer (no UI impact, compilable independently)
2. Step 3: Domain interface
3. Step 4: Data implementation (mapper + repository)
4. Step 9: DI wiring
5. **Compile check** (`./gradlew :app:compileDebugKotlin`)
6. Steps 5-6: Player + MediaDetail dispatch
7. Step 7: LibrarySyncWorker
8. **Compile check**
9. Steps 8+10: Settings UI + strings
10. **Final compile check**

## Verification

1. `./gradlew :app:compileDebugKotlin` — compiles
2. `./gradlew :app:testDebugUnitTest` — existing tests pass (non-régression)
3. Settings → "PlexHub Backend" → "Add Backend" → enter URL → test connection → verify health response displayed
4. Trigger sync → verify movies/shows appear in library with `backend_` serverId
5. Open a backend movie detail → verify tmdbId, summary, genres populated from backend
6. Play a backend movie → verify stream URL obtained from backend API → ExoPlayer plays
7. Navigate to series → episodes load from backend → play episode
8. Verify Plex+Backend aggregation: same movie on both shows as single entry (unified by `unificationId`)
9. Periodic sync (6h) → verify backend sync runs alongside Plex and Xtream
10. Remove backend → verify existing direct Xtream still works

---

## Checklist de Validation Non-Régression

### Scénarios par source isolée

| Scénario | Plex seul | Xtream direct seul | Backend seul |
|----------|-----------|---------------------|--------------|
| **Sync** — Déclenchement initial | Serveurs Plex synchent normalement | Comptes Xtream synchent (VOD + séries) | Backend synchent movies + shows |
| **Sync** — Périodique (6h) | Fonctionne sans régression | Fonctionne sans régression | Backend sync s'exécute dans le worker |
| **Library** — Affichage liste | Films/séries Plex affichés | Films/séries Xtream affichés | Films/séries backend affichés |
| **Detail** — Film | Metadata complètes (Plex API) | Metadata (enrichi via get_vod_info) | Metadata complètes (pré-enrichi TMDB) |
| **Detail** — Série | Saisons + épisodes | Saisons virtuelles + épisodes | Saisons virtuelles + épisodes |
| **Lecture** — Film | Via ServerClientResolver | Via XtreamUrlBuilder | Via BackendUrlBuilder → /api/stream/ |
| **Lecture** — Episode | Via ServerClientResolver | Via XtreamUrlBuilder | Via BackendUrlBuilder → /api/stream/ |
| **Recherche** | FTS sur titre | FTS sur titre | FTS sur titre (même table media) |
| **History** | viewOffset/lastViewedAt OK | viewOffset/lastViewedAt OK | viewOffset/lastViewedAt OK |
| **Favoris** | Toggle + persistence | Toggle + persistence | Toggle + persistence |

### Scénarios multi-sources (agrégation)

| Combinaison | Ce qu'on vérifie |
|-------------|------------------|
| **Plex + Xtream** | Chaque source dans sa library. Pas d'interférence. Existing behavior intact. |
| **Plex + Backend** | Même film sur Plex et Backend → unifié via `unificationId` (agrégation). Lecture depuis l'une ou l'autre source. |
| **Xtream + Backend** | Les deux coexistent. `xtream_` et `backend_` serverId distincts. Pas de collision. |
| **Plex + Xtream + Backend** | Les 3 synchent dans le worker. Library affiche tout. Recherche cross-source. History cross-source. |

### Scénarios de résilience

| Scénario | Comportement attendu |
|----------|---------------------|
| Backend down pendant sync | Log warning, Plex + Xtream continuent, Result.success() |
| Backend ajouté puis supprimé | Media `backend_*` supprimés de Room, Plex + Xtream intacts |
| Aucun backend configuré | Bloc backend dans LibrarySyncWorker est no-op (liste vide) |
| Backend down pendant lecture | Erreur affichée dans player, pas de crash |
| Migration 33→34 | Table `backend_servers` créée, colonne `sourceServerId` ajoutée, données existantes préservées |

---

## Module Boundaries Verification (F-06 / F-07)

| Check | Comment vérifier |
|-------|-----------------|
| `core:model` n'importe pas Retrofit | Grep `import retrofit` dans `core/model/` → 0 résultats |
| `core:model` n'importe pas Compose | Grep `import androidx.compose` dans `core/model/` → 0 résultats |
| `core:model` n'importe pas Room | Grep `import androidx.room` dans `core/model/` → 0 résultats |
| `BackendDto.kt` est dans `core:network` | Vérifier chemin : `core/network/.../backend/BackendDto.kt` |
| `BackendApiService.kt` est dans `core:network` | Vérifier chemin : `core/network/.../backend/BackendApiService.kt` |
| `core:common` inchangé | Aucun fichier backend dans `core/common/` |
| `domain/BackendRepository.kt` types de retour | Seulement `BackendServer`, `BackendConnectionInfo`, `MediaItem`, `String`, `Int` — pas de DTO ni Entity |
| `./gradlew :core:model:dependencies` | Pas de dépendance vers Retrofit, Room, ou Compose Runtime |
