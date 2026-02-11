# Plan d'Action Correctif PlexHubTV
## Instructions pour Claude Sonnet

> **Généré par** : Claude Opus 4.6 — Audit du 11 février 2026
> **Branche source** : `claude/continue-plexhubtv-refactor-YO43N`
> **Documents de référence** : `AUDIT_REPORT_OPUS.md`, `OFFLINE_FIRST_AUDIT.md`, `performance_benchmarks.csv`

**⚠️ IMPORTANT** : Suivre l'ordre STRICT des phases. Ne JAMAIS committer du code non compilable.

---

## Phase 0 : Préparation (30 minutes)

### Action 0.1 : Vérifier la branche et l'état actuel

```bash
git checkout claude/continue-plexhubtv-refactor-YO43N
git pull origin claude/continue-plexhubtv-refactor-YO43N

# Vérifier l'état de compilation actuel
./gradlew clean build 2>&1 | tee build_errors_before.log
echo "Erreurs avant corrections:" && grep -c "error:" build_errors_before.log
```

**Critère de succès** : État initial documenté.

---

## Phase 1 : Corrections Bloquantes (Priorité Absolue)
**Durée estimée** : 1 jour
**Objectif** : Corriger les 3 problèmes bloquants identifiés par l'audit Opus

---

### Action 1.1 : Déplacer `ResolveEpisodeSourcesUseCase` de `domain` vers `data`

**Criticité** : 🔴 Bloquant — Violation Clean Architecture (domain → data + app)

**Fichiers impactés** :
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/ResolveEpisodeSourcesUseCase.kt` → DÉPLACER vers `data/`
- Tous fichiers qui importent cette classe (chercher avec grep)

**Problème** :
```kotlin
// ❌ domain/usecase/ResolveEpisodeSourcesUseCase.kt — Lignes 7-11
package com.chakir.plexhubtv.domain.usecase

import com.chakir.plexhubtv.di.network.ConnectionManager    // ❌ Package app (DI)
import com.chakir.plexhubtv.di.network.PlexApiService        // ❌ Package app (DI)
import com.chakir.plexhubtv.di.network.PlexClient            // ❌ Package app (DI)
import com.chakir.plexhubtv.di.network.model.MetadataDTO     // ❌ Package app (DTO)
import com.chakir.plexhubtv.data.mapper.MediaMapper           // ❌ Package data
```

**Étapes détaillées** :

**1. Créer le dossier cible** :
```bash
mkdir -p data/src/main/java/com/chakir/plexhubtv/data/usecase
```

**2. Déplacer le fichier** :
```bash
git mv domain/src/main/java/com/chakir/plexhubtv/domain/usecase/ResolveEpisodeSourcesUseCase.kt \
       data/src/main/java/com/chakir/plexhubtv/data/usecase/ResolveEpisodeSourcesUseCase.kt
```

**3. Modifier le fichier déplacé** — Changer le package et corriger les imports :
```kotlin
// ✅ APRÈS — data/src/.../data/usecase/ResolveEpisodeSourcesUseCase.kt
package com.chakir.plexhubtv.data.usecase  // ✅ Changé de domain.usecase

import com.chakir.plexhubtv.core.network.ConnectionManager   // ✅ core.network (pas di.network)
import com.chakir.plexhubtv.core.network.PlexApiService       // ✅ core.network
import com.chakir.plexhubtv.core.network.PlexClient           // ✅ core.network
import com.chakir.plexhubtv.core.network.model.MetadataDTO    // ✅ core.network.model
import com.chakir.plexhubtv.data.mapper.MediaMapper            // ✅ data.mapper (même module)
// ... reste des imports inchangé
```

**4. Mettre à jour les imports dans les fichiers qui l'utilisent** :
```bash
# Trouver tous les fichiers qui importent l'ancien package
grep -r "import com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase" --include="*.kt"

# Dans chaque fichier trouvé, remplacer :
# AVANT: import com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase
# APRÈS: import com.chakir.plexhubtv.data.usecase.ResolveEpisodeSourcesUseCase
```

**5. Vérifier que le module Hilt fournit bien cette classe** :
Si `ResolveEpisodeSourcesUseCase` est injecté via Hilt, s'assurer qu'un `@Provides` ou `@Inject constructor` existe dans le module `data` ou `app`.

**Tests de validation** :
```bash
./gradlew :domain:build    # ✅ domain ne doit plus dépendre de data/app
./gradlew :data:build      # ✅ data compile avec le nouveau use case
./gradlew :app:build       # ✅ app compile avec imports mis à jour
```

**Commit** :
```bash
git add -A && git commit -m "fix(architecture): move ResolveEpisodeSourcesUseCase from domain to data

- domain module was importing data.mapper.MediaMapper and app DI classes
- Use case belongs in data layer since it depends on network/mapper implementations
- Fixed package imports from di.network.* to core.network.*
- Updated imports in consuming ViewModels"
```

---

### Action 1.2 : Corriger `AuthInterceptor` — Remplacer `runBlocking` par cache non-bloquant

**Criticité** : 🔴 Bloquant Performance — `runBlocking` sur thread OkHttp

**Fichier** : `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt`

**Problème actuel (lignes 29-39)** :
```kotlin
// ❌ PROBLÈME — runBlocking bloque le thread OkHttp 2× par requête
override fun intercept(chain: Interceptor.Chain): Response {
    val token = runBlocking {
        settingsDataStore.plexToken.first()  // ❌ Blocking I/O
    }
    val clientId = runBlocking {
        settingsDataStore.clientId.first()   // ❌ Blocking I/O
    }
    // ...
}
```

**Code corrigé complet** :
```kotlin
package com.chakir.plexhubtv.core.network

import android.os.Build
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepteur OkHttp pour injecter automatiquement les tokens d'authentification Plex.
 *
 * Thread-safe: Uses AtomicReference for non-blocking reads.
 * Values are updated asynchronously from DataStore via CoroutineScope.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        @ApplicationScope private val scope: CoroutineScope,
    ) : Interceptor {

    private val cachedToken = AtomicReference<String?>(null)
    private val cachedClientId = AtomicReference<String?>(null)

    init {
        // Non-blocking: collect values in background
        settingsDataStore.plexToken
            .onEach { cachedToken.set(it) }
            .launchIn(scope)
        settingsDataStore.clientId
            .onEach { cachedClientId.set(it) }
            .launchIn(scope)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val token = cachedToken.get()     // ✅ Non-blocking O(1) read
        val clientId = cachedClientId.get() // ✅ Non-blocking O(1) read

        if (clientId != null && originalRequest.header("X-Plex-Client-Identifier") == null) {
            requestBuilder.addHeader("X-Plex-Client-Identifier", clientId)
        }

        if (token != null && originalRequest.header("X-Plex-Token") == null && !originalRequest.url.toString().contains("X-Plex-Token")) {
            requestBuilder.addHeader("X-Plex-Token", token)
        }

        if (originalRequest.header("Accept") == null) {
            requestBuilder.addHeader("Accept", "application/json")
        }

        requestBuilder.header("X-Plex-Platform", "Android")
        requestBuilder.header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        requestBuilder.header("X-Plex-Provides", "player")
        requestBuilder.header("X-Plex-Product", "Plex for Android (TV)")
        requestBuilder.header("X-Plex-Version", "1.0.0")
        requestBuilder.header("X-Plex-Device", Build.MODEL)
        requestBuilder.header("X-Plex-Model", Build.MODEL)

        return chain.proceed(requestBuilder.build())
    }
}
```

**Mise à jour de `NetworkModule.kt`** :
Le `provideAuthInterceptor` dans `NetworkModule.kt` (lignes 62-68) doit passer le scope au constructeur. Actuellement :

```kotlin
// Vérifier que le @Provides passe bien le scope
@Provides
@Singleton
fun provideAuthInterceptor(
    settingsDataStore: SettingsDataStore,
    @ApplicationScope scope: CoroutineScope
): AuthInterceptor {
    return AuthInterceptor(settingsDataStore, scope)
}
```

**Note** : Le `AuthInterceptor` actuel a un constructeur `@Inject constructor(settingsDataStore)` sans scope. La version corrigée nécessite `@ApplicationScope scope: CoroutineScope`. Vérifier que `@ApplicationScope` est bien défini dans `core/di/` ou `core/common/`.

**Tests de validation** :
```bash
./gradlew :core:network:build  # ✅ Module compile
./gradlew :app:build           # ✅ App compile
```

**Commit** :
```bash
git add -A && git commit -m "perf(network): replace runBlocking with AtomicReference in AuthInterceptor

- AuthInterceptor was blocking OkHttp threads with runBlocking for every request
- Now uses AtomicReference for non-blocking reads
- Token/clientId are updated asynchronously from DataStore via CoroutineScope
- Eliminates potential ANR on slow devices (Mi Box)"
```

---

### Action 1.3 : Désactiver le logging HTTP en production

**Criticité** : 🟠 Haute — Fuite de données sensibles + impact performance

**Fichier** : `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt` (lignes 55-59)

**Problème** :
```kotlin
// ❌ PROBLÈME — Log BODY en production (tokens, données utilisateur dans les logs)
fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY  // ❌ Même en release!
    }
}
```

**Solution** :
```kotlin
// ✅ SOLUTION — Conditionnel selon le build
fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
        level = if (com.chakir.plexhubtv.core.network.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
}
```

**Note** : Le module `core:network` n'a peut-être pas `BuildConfig.DEBUG`. Alternatives :
- Passer un `Boolean` via le constructeur du module
- Utiliser `@Named("isDebug") isDebug: Boolean` fourni par un module `app`
- Vérifier si `BuildConfig` est accessible dans ce module

Si `BuildConfig.DEBUG` n'est pas disponible dans `core:network`, utiliser :
```kotlin
import android.util.Log
// ...
level = if (Log.isLoggable("PlexHubTV", Log.DEBUG)) {
    HttpLoggingInterceptor.Level.BODY
} else {
    HttpLoggingInterceptor.Level.NONE
}
```

Ou ajouter `buildFeatures { buildConfig = true }` dans `core/network/build.gradle.kts`.

**Tests de validation** :
```bash
./gradlew :core:network:build  # ✅ Compile
./gradlew assembleRelease       # ✅ Release build avec NONE logging
```

**Commit** :
```bash
git add -A && git commit -m "fix(security): disable HTTP body logging in release builds

- HttpLoggingInterceptor.Level.BODY was logging all request/response bodies
- Tokens and user data were visible in production logs
- Now uses NONE level in release, BODY only in debug"
```

---

### Validation Phase 1

```bash
# Compilation complète
./gradlew clean build

# Domain ne dépend plus de data
./gradlew :domain:dependencies --configuration implementation | grep -i "data\|retrofit\|okhttp"
# ✅ Doit retourner VIDE

# Tests existants passent toujours
./gradlew testDebugUnitTest
```

---

## Phase 2 : Migration Offline First (Haute Priorité)
**Durée estimée** : 4 jours
**Objectif** : Toutes les fonctionnalités critiques disponibles offline

---

### Action 2.1 : Implémenter cache offline pour Search

**Criticité** : 🔴 Bloquant Offline — Search crash sans réseau

**Fichiers à créer/modifier** :
1. **Créer** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/SearchCacheEntity.kt`
2. **Créer** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/SearchCacheDao.kt`
3. **Modifier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt`
4. **Modifier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt`
5. **Modifier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/SearchRepositoryImpl.kt`

**Étape 1 — Créer `SearchCacheEntity`** :
```kotlin
// core/database/src/main/java/com/chakir/plexhubtv/core/database/SearchCacheEntity.kt
package com.chakir.plexhubtv.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_cache",
    indices = [Index(value = ["query", "serverId"], unique = true)]
)
data class SearchCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val serverId: String,
    val resultsJson: String,  // JSON sérialisé
    val resultCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMillis: Long = 3_600_000): Boolean =  // 1 heure par défaut
        System.currentTimeMillis() - lastUpdated > ttlMillis
}
```

**Étape 2 — Créer `SearchCacheDao`** :
```kotlin
// core/database/src/main/java/com/chakir/plexhubtv/core/database/SearchCacheDao.kt
package com.chakir.plexhubtv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE query = :query AND serverId = :serverId")
    suspend fun get(query: String, serverId: String): SearchCacheEntity?

    @Query("SELECT * FROM search_cache WHERE query = :query")
    suspend fun getAll(query: String): List<SearchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: SearchCacheEntity)

    @Query("DELETE FROM search_cache WHERE lastUpdated < :minTimestamp")
    suspend fun deleteExpired(minTimestamp: Long = System.currentTimeMillis() - 86_400_000) // 24h

    @Query("DELETE FROM search_cache")
    suspend fun deleteAll()
}
```

**Étape 3 — Mettre à jour `PlexDatabase`** :
```kotlin
// Ajouter SearchCacheEntity à la liste des entities
@Database(
    entities = [
        MediaEntity::class,
        ServerEntity::class,
        DownloadEntity::class,
        ApiCacheEntity::class,
        OfflineWatchProgressEntity::class,
        HomeContentEntity::class,
        FavoriteEntity::class,
        RemoteKey::class,
        LibrarySectionEntity::class,
        TrackPreferenceEntity::class,
        CollectionEntity::class,
        MediaCollectionCrossRef::class,
        ProfileEntity::class,
        SearchCacheEntity::class,  // ✅ AJOUTER
    ],
    version = 24,  // ✅ INCRÉMENTER de 23 à 24
    exportSchema = true,
)
abstract class PlexDatabase : RoomDatabase() {
    // ... DAOs existants ...
    abstract fun searchCacheDao(): SearchCacheDao  // ✅ AJOUTER
}
```

**Étape 4 — Ajouter migration et DAO provider dans `DatabaseModule`** :
```kotlin
// Dans DatabaseModule.kt, ajouter la migration :
private val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `search_cache` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `query` TEXT NOT NULL,
                `serverId` TEXT NOT NULL,
                `resultsJson` TEXT NOT NULL,
                `resultCount` INTEGER NOT NULL DEFAULT 0,
                `lastUpdated` INTEGER NOT NULL
            )
        """)
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_search_cache_query_serverId`
            ON `search_cache` (`query`, `serverId`)
        """)
    }
}

// Ajouter à la liste des migrations :
.addMigrations(
    MIGRATION_11_12,
    MIGRATION_15_16,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24  // ✅ AJOUTER
)

// Ajouter le provider DAO :
@Provides
fun provideSearchCacheDao(database: PlexDatabase): SearchCacheDao {
    return database.searchCacheDao()
}
```

**Étape 5 — Refactorer `SearchRepositoryImpl`** :

Le `SearchRepositoryImpl` actuel a une méthode `searchAllServers` qui recherche sur tous les serveurs en parallèle. La modification doit :
1. Vérifier le cache d'abord
2. Émettre les résultats cachés si valides
3. Lancer la recherche réseau
4. Mettre à jour le cache
5. Si erreur réseau, servir le cache (même expiré)

Modifier `searchAllServers` ou la méthode principale de recherche pour ajouter la logique cache :

```kotlin
// Dans SearchRepositoryImpl, ajouter le searchCacheDao et gson au constructeur
class SearchRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val mapper: MediaMapper,
    private val mediaUrlResolver: MediaUrlResolver,
    private val searchCacheDao: SearchCacheDao,  // ✅ AJOUTER
    private val gson: Gson,                       // ✅ AJOUTER
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SearchRepository {

    // Modifier la méthode searchOnServer pour ajouter le cache
    override suspend fun searchOnServer(
        server: Server,
        query: String,
        // ... autres params
    ): Result<List<MediaItem>> {
        // 1. Vérifier cache
        val cached = searchCacheDao.get(query.lowercase().trim(), server.clientIdentifier)
        if (cached != null && !cached.isExpired()) {
            val cachedItems = try {
                gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
            } catch (e: Exception) {
                null
            }
            if (cachedItems != null) {
                return Result.success(cachedItems)
            }
        }

        // 2. Tentative réseau
        return try {
            val baseUrl = connectionManager.findBestConnection(server)
                ?: return if (cached != null) {
                    // Offline mais cache disponible (même expiré) → servir cache
                    val fallback = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
                    Result.success(fallback)
                } else {
                    Result.failure(Exception("No connection and no cache"))
                }

            val client = PlexClient(server, api, baseUrl)
            val response = client.search(query, /* ... params ... */)
            if (response.isSuccessful) {
                val items = response.body()?.mediaContainer?.metadata?.map {
                    mapper.mapDtoToDomain(it, server.clientIdentifier, baseUrl, server.accessToken)
                } ?: emptyList()

                // 3. Mettre à jour cache
                searchCacheDao.upsert(
                    SearchCacheEntity(
                        query = query.lowercase().trim(),
                        serverId = server.clientIdentifier,
                        resultsJson = gson.toJson(items),
                        resultCount = items.size
                    )
                )

                Result.success(items)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            // 4. Erreur réseau → fallback cache
            if (cached != null) {
                val fallback = gson.fromJson(cached.resultsJson, Array<MediaItem>::class.java).toList()
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }
}
```

**Tests de validation** :
```bash
./gradlew :core:database:build  # ✅ Entity + DAO + Migration compile
./gradlew :data:build            # ✅ SearchRepositoryImpl compile
./gradlew :app:build             # ✅ App compile
./gradlew testDebugUnitTest      # ✅ Tests existants passent
```

**Commit** :
```bash
git add -A && git commit -m "feat(offline): implement search cache for offline-first search

- Adds SearchCacheEntity with TTL (1h) in Room
- Adds SearchCacheDao with upsert and cleanup methods
- Adds DB migration 23→24 for search_cache table
- Refactors SearchRepositoryImpl with cache-first pattern
- Search now returns cached results when offline
- Cache expires after 1 hour, stale cache served as fallback"
```

---

### Action 2.2 : Activer le cache dans `MediaDetailRepositoryImpl.getMediaDetail()`

**Criticité** : 🟠 Haute — Cache existant mais commenté

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt`

**Problème (lignes 53-63)** :
Le code prépare une clé cache et vérifie `plexApiCache.get(cacheKey)` mais ne parse jamais le résultat — le bloc try est vide avec un commentaire expliquant "We don't have GSON injected".

**Solution** : Injecter `Gson` (déjà singleton dans Hilt) et parser le cache.

**Modifications** :

1. Ajouter `Gson` au constructeur de `MediaDetailRepositoryImpl` :
```kotlin
class MediaDetailRepositoryImpl @Inject constructor(
    private val api: PlexApiService,
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val mediaDao: MediaDao,
    private val collectionDao: CollectionDao,
    private val plexApiCache: PlexApiCache,
    private val mapper: MediaMapper,
    private val mediaUrlResolver: MediaUrlResolver,
    private val gson: Gson,  // ✅ AJOUTER
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaDetailRepository {
```

2. Remplacer le bloc cache vide (lignes 53-63) :
```kotlin
// ✅ REMPLACER le bloc commenté par :
val cacheKey = "$serverId:/library/metadata/$ratingKey"
val cachedJson = plexApiCache.get(cacheKey)
if (cachedJson != null) {
    try {
        val cachedResponse = gson.fromJson(cachedJson, com.chakir.plexhubtv.core.network.model.PlexResponse::class.java)
        val cachedMetadata = cachedResponse?.mediaContainer?.metadata?.firstOrNull()
        if (cachedMetadata != null) {
            val cachedItem = mapper.mapDtoToDomain(cachedMetadata, serverId, client.baseUrl, client.server.accessToken)
            return Result.success(cachedItem)
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to parse cached metadata for $ratingKey")
    }
}
```

3. Également ajouter un fallback Room dans le bloc `catch (e: IOException)` (lignes 97-99) :
```kotlin
catch (e: IOException) {
    Timber.e(e, "Network error fetching media detail $ratingKey")
    // ✅ AJOUTER : Fallback Room si réseau échoue
    val localEntity = mediaDao.getMedia(ratingKey, serverId)
    if (localEntity != null) {
        return Result.success(mapper.mapEntityToDomain(localEntity))
    }
    Result.failure(NetworkException("Network error", e))
}
```

**Tests de validation** :
```bash
./gradlew :data:build
./gradlew :data:testDebugUnitTest --tests "*MediaDetailRepositoryImplTest*"
```

**Commit** :
```bash
git add -A && git commit -m "feat(offline): activate cache parsing in MediaDetailRepository

- Injects Gson to parse PlexApiCache JSON responses
- getMediaDetail now returns cached data immediately if available
- Adds Room fallback on network IOException
- Reduces network calls when cache is fresh"
```

---

### Action 2.3 : Refactorer `getSeasonEpisodes` pour Cache-First

**Criticité** : 🟡 — Network-first devrait être Cache-first

**Fichier** : `data/src/main/java/.../data/repository/MediaDetailRepositoryImpl.kt` (lignes 113-162)

**Modification** : Inverser l'ordre — émettre les épisodes depuis Room d'abord, puis rafraîchir depuis le réseau.

```kotlin
override suspend fun getSeasonEpisodes(
    ratingKey: String,
    serverId: String,
): Result<List<MediaItem>> {
    // 1. Cache-first: Émettre depuis Room immédiatement
    val localEntities = mediaDao.getChildren(ratingKey, serverId)
    if (localEntities.isNotEmpty()) {
        val client = getClient(serverId)
        val baseUrl = client?.baseUrl
        val token = client?.server?.accessToken
        val cachedItems = localEntities.map {
            mapper.mapEntityToDomain(it).let { domain ->
                if (baseUrl != null && token != null) {
                    mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                        baseUrl = baseUrl, accessToken = token
                    )
                } else domain
            }
        }
        // Si on a des données locales et pas de réseau, les retourner directement
        val networkClient = getClient(serverId)
        if (networkClient == null) return Result.success(cachedItems)
    }

    // 2. Tentative réseau pour rafraîchir
    try {
        val client = getClient(serverId)
        if (client != null) {
            val response = client.getChildren(ratingKey)
            if (response.isSuccessful) {
                val metadata = response.body()?.mediaContainer?.metadata
                if (metadata != null) {
                    val items = metadata.map {
                        mapper.mapDtoToDomain(it, serverId, client.baseUrl, client.server.accessToken)
                    }
                    return Result.success(items)
                }
            }
        }
    } catch (e: IOException) {
        Timber.w(e, "Network error fetching episodes for $ratingKey")
    } catch (e: HttpException) {
        Timber.w(e, "HTTP error ${e.code()} fetching episodes for $ratingKey")
    } catch (e: Exception) {
        Timber.e(e, "Error fetching episodes for $ratingKey")
    }

    // 3. Fallback: Retourner cache (déjà chargé plus haut)
    if (localEntities.isNotEmpty()) {
        val client = getClient(serverId)
        val items = localEntities.map {
            val domain = mapper.mapEntityToDomain(it)
            val baseUrl = client?.baseUrl
            val token = client?.server?.accessToken
            if (baseUrl != null && token != null) {
                mediaUrlResolver.resolveUrls(domain, baseUrl, token).copy(
                    baseUrl = baseUrl, accessToken = token
                )
            } else domain
        }
        return Result.success(items)
    }

    return Result.failure(MediaNotFoundException("Episodes for $ratingKey not found"))
}
```

**Commit** :
```bash
git add -A && git commit -m "feat(offline): refactor getSeasonEpisodes to cache-first

- Episodes are now loaded from Room first for instant display
- Network fetch happens in background for refresh
- Falls back to cached episodes if network unavailable"
```

---

### Action 2.4 : Ajouter TTL sur les données `updatedAt`

**Criticité** : 🟡 — Cache sans expiration

**Fichier** : `data/src/main/java/.../data/mapper/MediaMapper.kt`

**Problème** : Le champ `updatedAt` de `MediaEntity` est initialisé à `0` et n'est pas systématiquement mis à jour lors des upserts.

**Solution** : S'assurer que `updatedAt = System.currentTimeMillis()` est défini dans la méthode `mapDtoToEntity()` du mapper :

```kotlin
// Dans MediaMapper.mapDtoToEntity(), s'assurer que :
fun mapDtoToEntity(dto: MetadataDTO, serverId: String, libraryKey: String): MediaEntity {
    return MediaEntity(
        // ... autres champs ...
        updatedAt = System.currentTimeMillis()  // ✅ Toujours mettre à jour
    )
}
```

Vérifier aussi que `SyncRepositoryImpl.syncLibrary()` passe bien le `updatedAt` lors du `copy()` (ligne 135-141).

**Commit** :
```bash
git add -A && git commit -m "fix(cache): ensure updatedAt is set on all media entity upserts

- MediaMapper.mapDtoToEntity now always sets updatedAt to current time
- Enables TTL-based cache invalidation in repositories"
```

---

### Validation Phase 2

```bash
./gradlew clean build
./gradlew testDebugUnitTest

# Test manuel offline (si émulateur disponible)
# adb shell svc wifi disable && adb shell svc data disable
# Lancer l'app → Home → Library → Search → Details
# Tout doit fonctionner avec données cachées
# adb shell svc wifi enable
```

---

## Phase 3 : Optimisations Performance
**Durée estimée** : 2 jours
**Objectif** : Améliorer temps de chargement et consommation ressources

---

### Action 3.1 : Réutiliser OkHttpClient pour TMDB/OMDB

**Criticité** : 🟡

**Fichier** : `core/network/src/main/java/.../core/network/NetworkModule.kt` (lignes 168-215)

**Problème** : Les clients TMDB et OMDB créent de nouveaux `OkHttpClient` sans réutiliser le pool de connexions.

**Solution** : Utiliser `baseClient.newBuilder()` pour partager le pool :
```kotlin
@Provides
@Singleton
@Named("tmdb")
fun provideTmdbRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
    val tmdbClient = okHttpClient.newBuilder()  // ✅ Partage le pool
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/")
        .client(tmdbClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}

@Provides
@Singleton
@Named("omdb")
fun provideOmdbRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
    val omdbClient = okHttpClient.newBuilder()  // ✅ Partage le pool
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl("https://www.omdbapi.com/")
        .client(omdbClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
```

**Commit** :
```bash
git add -A && git commit -m "perf(network): share OkHttp connection pool across TMDB/OMDB clients

- TMDB and OMDB Retrofit instances now use okHttpClient.newBuilder()
- Shares connection pool and thread pool with main Plex client
- Reduces resource usage for external API calls"
```

---

### Action 3.2 : Ajouter `@Immutable` sur les data classes UI clés

**Criticité** : 🟡

**Fichier** : `core/model/src/main/java/com/chakir/plexhubtv/core/model/MediaItem.kt` (et autres)

**Vérifier d'abord** si `MediaItem` est une `data class` avec uniquement des `val` (pas de `var`). Si oui, ajouter `@Immutable` :

```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: String = "",
    val ratingKey: String = "",
    // ... tous val, pas de var
)
```

**Note** : `@Immutable` ne doit être ajouté QUE si TOUS les champs sont `val` et les types sont immuables (String, Int, List — pas MutableList). Vérifier avant d'ajouter.

**Aussi ajouter sur** :
- `Hub` data class
- `Server` data class
- Les `sealed class` UI State des ViewModels

**Commit** :
```bash
git add -A && git commit -m "perf(compose): add @Immutable annotations to key data classes

- Enables Compose compiler to skip unnecessary recompositions
- Applied to MediaItem, Hub, Server, and UI state classes
- Improves scroll performance on low-end Android TV devices"
```

---

### Action 3.3 : Fix migration 22→23 UUID statique

**Criticité** : 🟢

**Fichier** : `core/database/src/main/java/.../core/database/DatabaseModule.kt` (ligne 131)

**Problème** : `java.util.UUID.randomUUID()` est évalué dans le template string lors de la compilation, pas à l'exécution. Mais en fait en Kotlin, les valeurs dans les `object` sont évaluées à l'exécution lors du chargement de la classe, donc ce n'est techniquement pas un bug — l'UUID sera différent à chaque installation. Cependant, le pattern est confus et fragile.

**Solution plus propre** :
```kotlin
private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ... table creation ...
        val uuid = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.execSQL("""
            INSERT INTO `profiles` (...) VALUES (
                '$uuid', 'Default', NULL, NULL, 0,
                'GENERAL', 1, NULL, NULL, 'AUTO',
                $now, $now, 1
            )
        """)
    }
}
```

**Note** : Si la migration 22→23 a déjà été exécutée sur des appareils existants, cette modification n'a pas d'effet rétroactif. C'est un fix de code quality uniquement pour les nouvelles installations.

**Commit** :
```bash
git add -A && git commit -m "fix(database): use explicit UUID variable in migration 22→23

- UUID was previously embedded in string template
- Now uses local variable for clarity and correctness"
```

---

## Phase 4 : Refactorings Architecture
**Durée estimée** : 3 jours
**Objectif** : ViewModels < 300 lignes, SRP respecté, duplication éliminée

---

### Action 4.1 : Extraire `ServerClientResolver` partagé

**Criticité** : 🟡

**Créer** : `data/src/main/java/com/chakir/plexhubtv/data/repository/ServerClientResolver.kt`

```kotlin
package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.network.PlexApiService
import com.chakir.plexhubtv.core.network.PlexClient
import com.chakir.plexhubtv.domain.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerClientResolver @Inject constructor(
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val api: PlexApiService,
) {
    suspend fun getActiveClients(): List<PlexClient> = coroutineScope {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull()
            ?: return@coroutineScope emptyList()
        servers.map { server ->
            async {
                val baseUrl = connectionManager.findBestConnection(server)
                if (baseUrl != null) PlexClient(server, api, baseUrl) else null
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun getClient(serverId: String): PlexClient? {
        val servers = authRepository.getServers(forceRefresh = false).getOrNull() ?: return null
        val server = servers.find { it.clientIdentifier == serverId } ?: return null
        val baseUrl = connectionManager.findBestConnection(server) ?: return null
        return PlexClient(server, api, baseUrl)
    }

    suspend fun getServers(): List<Server> {
        return authRepository.getServers(forceRefresh = false).getOrNull() ?: emptyList()
    }
}
```

Puis remplacer les `getActiveClients()` et `getClient()` dupliqués dans `HubsRepositoryImpl`, `OnDeckRepositoryImpl`, `MediaDetailRepositoryImpl`, `PlaybackRepositoryImpl`, `LibraryRepositoryImpl` par l'injection de `ServerClientResolver`.

**Commit** :
```bash
git add -A && git commit -m "refactor(data): extract ServerClientResolver to eliminate duplication

- Centralizes getActiveClients() and getClient() in one injectable class
- Removes duplicate implementations from 5 repository classes
- Reduces ~100 lines of duplicated code"
```

---

### Action 4.2 : Extraire `MediaEntityResolver` pour résolution d'URLs

**Criticité** : 🟡

**Créer** : `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaEntityResolver.kt`

```kotlin
package com.chakir.plexhubtv.data.repository

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.Server
import com.chakir.plexhubtv.core.network.ConnectionManager
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaEntityResolver @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val mapper: MediaMapper,
    private val mediaUrlResolver: MediaUrlResolver,
) {
    fun resolve(entity: MediaEntity, servers: List<Server>): MediaItem {
        val server = servers.find { it.clientIdentifier == entity.serverId }
        val baseUrl = server?.let {
            connectionManager.getCachedUrl(it.clientIdentifier) ?: it.address
        }
        val token = server?.accessToken

        val domain = mapper.mapEntityToDomain(entity)
        return if (server != null && baseUrl != null) {
            mediaUrlResolver.resolveUrls(domain, baseUrl, token ?: "").copy(
                baseUrl = baseUrl,
                accessToken = token,
            )
        } else {
            domain
        }
    }

    fun resolveAll(entities: List<MediaEntity>, servers: List<Server>): List<MediaItem> {
        return entities.map { resolve(it, servers) }
    }
}
```

Puis remplacer les ~15 occurrences du pattern dans les repositories.

**Commit** :
```bash
git add -A && git commit -m "refactor(data): extract MediaEntityResolver to deduplicate URL resolution

- Centralizes entity→domain+URL resolution in one injectable class
- Eliminates ~15 copies of the same resolution pattern across repositories
- Reduces ~150 lines of duplicated code"
```

---

### Action 4.3 : Fix `LibrarySyncWorker` — Injecter interface au lieu de concret

**Criticité** : 🟡

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt` (ligne 39)

**Problème** : `private val syncRepository: SyncRepositoryImpl` — injecte l'implémentation concrète.

**Solution** :
1. Changer en `private val syncRepository: SyncRepository` (interface)
2. Si `onProgressUpdate` est nécessaire, ajouter la propriété à l'interface `SyncRepository` :

```kotlin
// domain/repository/SyncRepository.kt — ajouter :
interface SyncRepository {
    suspend fun syncServer(server: Server): Result<Unit>
    suspend fun syncLibrary(server: Server, libraryKey: String): Result<Unit>
    var onProgressUpdate: ((current: Int, total: Int, libraryName: String) -> Unit)?  // ✅ AJOUTER
}
```

Ou utiliser un mécanisme de Flow pour la progression au lieu d'un callback mutable.

**Commit** :
```bash
git add -A && git commit -m "fix(architecture): inject SyncRepository interface in LibrarySyncWorker

- LibrarySyncWorker now depends on SyncRepository interface, not SyncRepositoryImpl
- Adds onProgressUpdate property to SyncRepository interface
- Respects Dependency Inversion principle"
```

---

### Action 4.4 : Splitter `LibraryViewModel` (396 lignes)

**Criticité** : 🟡

Cette action est plus complexe et nécessite une analyse détaillée du ViewModel pour identifier les responsabilités à séparer. En général :

1. **`LibraryPagingViewModel`** : Gestion de Paging3 + sort/filter state
2. **`LibraryFilterViewModel`** : Gestion des filtres (genre, sort, direction)

Ou un seul ViewModel plus ciblé avec des helper classes privées.

**Effort** : 6 heures — nécessite de lire le ViewModel complet et planifier le split.

---

### Action 4.5 : Splitter `MediaDetailViewModel` (365 lignes)

**Criticité** : 🟡

Similaire à 4.4. Séparer en :
1. **`MediaLoadViewModel`** : Chargement des détails, saisons, épisodes
2. **`MediaActionsViewModel`** : Actions (favori, watch status, source selection)

**Effort** : 6 heures

---

## Phase 5 : Tests
**Durée estimée** : 2 jours
**Objectif** : Couverture tests 70% sur ViewModels

---

### Action 5.1 : Ajouter tests offline pour les repositories

**Fichiers à créer** :
- `data/src/test/.../data/repository/SearchRepositoryImplTest.kt` (tests offline)
- `data/src/test/.../data/repository/OnDeckRepositoryImplTest.kt` (tests offline)
- `data/src/test/.../data/repository/HubsRepositoryImplTest.kt` (tests offline)

Utiliser le template de test suivant :
```kotlin
@Test
fun `method offline should return cached data`() = runTest {
    // Arrange: Insert cache, simulate offline
    coEvery { connectionManager.findBestConnection(any()) } returns null

    // Act
    val result = repository.method(...)

    // Assert
    assertThat(result.isSuccess).isTrue()
}
```

---

### Action 5.2 : Ajouter tests pour les ViewModels non couverts

**ViewModels sans tests** (13 ViewModels) :
- `FavoritesViewModel` (57 lignes — petit, test rapide)
- `HistoryViewModel` (57 lignes — petit)
- `DownloadsViewModel` (86 lignes — petit)
- `IptvViewModel` (130 lignes — moyen)
- `SettingsViewModel` (302 lignes — complexe)
- Les 8 autres (LoadingVM, MainVM, ProfileVM, etc.)

**Priorité** : Tester en premier les VMs les plus complexes (Settings, Iptv) puis les petits.

---

## Phase 6 : Polish & Code Quality
**Durée estimée** : 1 jour
**Objectif** : Cleanup final

---

### Action 6.1 : Nettoyer les commentaires TODO restants

Vérifier avec :
```bash
grep -rn "TODO\|FIXME\|HACK\|XXX" --include="*.kt" app/ data/ domain/ core/
```

### Action 6.2 : Standardiser les commentaires (français → anglais)

### Action 6.3 : Supprimer le code mort / imports inutilisés

```bash
./gradlew lintDebug  # Détecte les imports inutilisés
```

---

## Checklist de Validation Globale

Après **CHAQUE** phase, vérifier :

### Build & Compilation
- [ ] `./gradlew clean build` passe sans erreur
- [ ] `./gradlew :domain:dependencies --configuration implementation | grep -i "data\|retrofit"` retourne vide

### Tests
- [ ] `./gradlew testDebugUnitTest` passe à 100%

### Architecture
- [ ] domain ne dépend ni de data ni de app
- [ ] Tous les repositories injectent des interfaces (pas d'implémentations concrètes dans les constructeurs de Workers/ViewModels)

### Offline First
- [ ] Search fonctionne offline avec cache
- [ ] MediaDetail retourne cache avant réseau
- [ ] Home (OnDeck + Hubs) affiche cache immédiatement

### Commits Git
Format : `<type>(<scope>): <description>`
- `fix(architecture): ...`
- `feat(offline): ...`
- `perf(network): ...`
- `refactor(data): ...`
- `test(repository): ...`

---

## Ordre d'Exécution STRICT

| # | Phase | Actions | Validation |
|---|---|---|---|
| 1 | Phase 0 | Préparation | Branche prête |
| 2 | Phase 1 | 1.1 + 1.2 + 1.3 | `./gradlew clean build` ✅ |
| 3 | Phase 2 | 2.1 + 2.2 + 2.3 + 2.4 | Test offline ✅ |
| 4 | Phase 3 | 3.1 + 3.2 + 3.3 | Build + tests ✅ |
| 5 | Phase 4 | 4.1 + 4.2 + 4.3 + (4.4 + 4.5 optionnel) | Architecture clean ✅ |
| 6 | Phase 5 | 5.1 + 5.2 | Couverture 70% ✅ |
| 7 | Phase 6 | 6.1 + 6.2 + 6.3 | Lint clean ✅ |

**❌ INTERDICTIONS** :
- Ne JAMAIS mélanger les phases dans un même commit
- Ne JAMAIS committer du code qui ne compile pas
- Ne JAMAIS refactorer sans tests existants

**✅ WORKFLOW** :
1. Lire l'action complète
2. Implémenter la correction exacte
3. Exécuter les tests de validation
4. Committer avec message conventionnel
5. Passer à l'action suivante

---

## Métriques de Succès Finales

| Objectif | Avant | Après |
|---|---|---|
| Build réussit | ❌ (domain violations) | ✅ |
| AuthInterceptor | runBlocking | AtomicReference |
| Logging production | BODY | NONE |
| Search offline | ❌ Crash | ✅ Cache |
| MediaDetail cache | ❌ Commenté | ✅ Actif |
| Repositories Offline First | 47% | 88% |
| Code dupliqué getActiveClients | 3 copies | 1 |
| Code dupliqué URL resolution | ~15 copies | 1 |
| LibrarySyncWorker DI | Concret | Interface |
| OkHttp pool TMDB/OMDB | Non partagé | Partagé |

---

*Fin du plan d'action — Exécuter phase par phase en suivant l'ordre strict.*
