# Refresh Metadata from TMDB/IMDb — Detail Screen

## Context

L'utilisateur veut pouvoir appuyer sur un bouton dans l'ecran de detail d'un film/serie pour rafraichir le **rating**, le **synopsis** et le **poster** depuis l'API TMDB (ou OMDB en fallback). Les donnees mises a jour doivent survivre au prochain sync automatique (`LibrarySyncWorker`).

**Constat**: Le pattern de preservation existe deja pour `scrapedRating` et `isHidden` — on l'etend a deux nouvelles colonnes.

---

## Revue critique du plan initial (Lead Dev)

### Defaut 1 — COALESCE dans les requetes SQL : complexite inutile et risque eleve

Le plan initial ajoutait des COALESCE dans `UNIFIED_SELECT`, `NON_UNIFIED_SELECT` et `AggregationService.buildRebuildSql()`. MEMORY.md documente **6 bugs critiques** lies a ces requetes. Chaque modification est un risque de regression silent (`LoadState.Error`, items disparus, crash runtime). **On ne touche pas a ces requetes.**

**Alternative retenue** : ecrire les valeurs TMDB directement dans les colonnes d'affichage (`summary`, `resolvedThumbUrl`) en plus des colonnes de persistence (`overriddenSummary`, `overriddenThumbUrl`). Le sync restaure les deux. Zero modification SQL.

### Defaut 2 — Override non applique quand le detail charge via API Plex

`PlexSourceHandler.getDetail()` a **deux chemins** :
- **Room hit** (entity + mediaParts avec streams) → `mapEntityToDomain()` → override visible
- **API fetch** (pas de streams, ou episode) → `mapDtoToDomain()` → **override invisible**

Pour un film synce sans mediaParts caches, chaque visite passe par l'API. Le plan initial ne gerait que le premier chemin.

**Fix retenu** : apres l'API fetch dans `PlexSourceHandler`, merge one-shot des colonnes override depuis Room (~1ms par primary key).

### Defaut 3 — `RefreshResult` sealed class dans le domain : fuite de details TMDB

Le plan initial retournait `RefreshResult.Success(rating, summary, posterUrl)` depuis le domain. Ca fait fuiter des details d'implementation (URLs TMDB, format de rating) dans la couche domain.

**Fix retenu** : retourner `Result<Unit>`. Apres succes, le ViewModel recharge le detail via `loadDetail()`, qui passe desormais par le chemin Room-merge (defaut 2 corrige).

### Defaut 4 — Mise a jour directe du UI state : double source de verite

Le plan initial faisait `_uiState.update { media.copy(summary = ...) }` apres le call TMDB. Ca cree une divergence entre Room et l'UI state. Si l'utilisateur navigue et revient, l'UI state est perdu.

**Fix retenu** : apres l'ecriture DB, appeler `loadDetail()` qui recharge depuis Room/API (avec merge). Source de verite unique = Room.

### Defaut 5 — `media_unified` pas mis a jour apres refresh manuel

Le plan initial modifiait le COALESCE dans `buildRebuildSql()` mais ne declenchait pas de rebuild apres le refresh. `media_unified` restait stale jusqu'au prochain sync.

**Fix retenu** : declencher `AggregationService.rebuildGroup(groupKey)` apres l'ecriture DAO. Et comme on ecrit directement dans `summary` + `resolvedThumbUrl`, le rebuild SQL n'a pas besoin de COALESCE.

### Defaut 6 — Pas de gestion de `success=false` dans les reponses TMDB

`TmdbTvResponse.success` peut etre `false` (ID invalide, cle API fausse). Le plan initial ne le verifiait pas.

**Fix retenu** : checker `response.success != false` avant d'utiliser les valeurs.

---

## Plan revise

### 1. Etendre les DTOs TMDB

**Fichier**: [TmdbApiService.kt](core/network/src/main/java/com/chakir/plexhubtv/core/network/TmdbApiService.kt)

Ajouter a `TmdbTvResponse` et `TmdbMovieResponse` :
```kotlin
@SerializedName("overview") val overview: String? = null,
@SerializedName("poster_path") val posterPath: String? = null,
```

---

### 2. Schema DB — colonnes de persistence

#### 2.1 MediaEntity
**Fichier**: [MediaEntity.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt)

Ajouter apres `scrapedRating` (ligne ~117) :
```kotlin
// PERSISTENCE: TMDB overrides — survived sync via SyncRepositoryImpl restoration
val overriddenSummary: String? = null,
val overriddenThumbUrl: String? = null,
```

#### 2.2 Migration v43 → v44
**Fichier**: [PlexDatabase.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt)

```kotlin
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media ADD COLUMN overriddenSummary TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE media ADD COLUMN overriddenThumbUrl TEXT DEFAULT NULL")
    }
}
```
Incrementer `version = 44`, enregistrer la migration.

---

### 3. DAO — Mise a jour ciblee

**Fichier**: [MediaDao.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt)

#### 3.1 Ecriture TMDB (cross-serveur) — ecrit AUSSI dans les colonnes d'affichage
```kotlin
@Query("""
    UPDATE media SET
        scrapedRating = :rating,
        displayRating = :rating,
        overriddenSummary = :summary,
        overriddenThumbUrl = :thumbUrl,
        summary = COALESCE(:summary, summary),
        resolvedThumbUrl = COALESCE(:thumbUrl, resolvedThumbUrl)
    WHERE tmdbId = :tmdbId
""")
suspend fun updateMetadataByTmdbId(
    tmdbId: String, rating: Double, summary: String?, thumbUrl: String?
): Int
```

> **Point cle** : `COALESCE(:summary, summary)` preserve la valeur existante si TMDB renvoie `null`.

#### 3.2 Query groupKeys pour rebuild media_unified
```kotlin
@Query("SELECT DISTINCT groupKey FROM media WHERE tmdbId = :tmdbId AND groupKey != ''")
suspend fun getGroupKeysByTmdbId(tmdbId: String): List<String>
```

#### 3.3 Persistence helper pour le sync
```kotlin
data class OverriddenMetadata(
    val ratingKey: String,
    val overriddenSummary: String?,
    val overriddenThumbUrl: String?,
)

@Query("""
    SELECT ratingKey, overriddenSummary, overriddenThumbUrl FROM media
    WHERE ratingKey IN (:ratingKeys) AND serverId = :serverId
    AND (overriddenSummary IS NOT NULL OR overriddenThumbUrl IS NOT NULL)
""")
suspend fun getOverriddenMetadata(
    ratingKeys: List<String>, serverId: String
): List<OverriddenMetadata>
```

---

### 4. Preservation lors du sync

**Fichier**: [SyncRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt)

A cote de `getScrapedRatings()` + `getHiddenRatingKeys()` (~ligne 150) :
```kotlin
val overriddenMap = mediaDao.getOverriddenMetadata(ratingKeys, server.clientIdentifier)
    .associateBy { it.ratingKey }
```

Dans `entity.copy()` (~ligne 165), ajouter :
```kotlin
val overrides = overriddenMap[dto.ratingKey]
// ...existing scrapedRating, isHidden...
overriddenSummary = overrides?.overriddenSummary,
overriddenThumbUrl = overrides?.overriddenThumbUrl,
// Restaurer les colonnes d'affichage depuis les overrides
summary = overrides?.overriddenSummary ?: entity.summary,
resolvedThumbUrl = overrides?.overriddenThumbUrl ?: /* existing resolvedThumbUrl computation */,
```

> Pas de COALESCE SQL, pas de modification de `UNIFIED_SELECT` / `NON_UNIFIED_SELECT` / `AggregationService`.

---

### 5. Merge overrides dans PlexSourceHandler (chemin API)

**Fichier**: [PlexSourceHandler.kt](data/src/main/java/com/chakir/plexhubtv/data/source/PlexSourceHandler.kt)

Apres l'API fetch (`mapDtoToDomain()`), ajouter un merge conditionnel :
```kotlin
// After mapping DTO to domain (around line 75-80)
val domainItem = mapper.mapDtoToDomain(metadata, ...)

// Merge Room overrides if any exist (1ms Room lookup by PK)
val entity = mediaDao.getMedia(ratingKey, serverId)
val finalItem = if (entity?.overriddenSummary != null || entity?.overriddenThumbUrl != null) {
    domainItem.copy(
        summary = entity.overriddenSummary ?: domainItem.summary,
        thumbUrl = entity.overriddenThumbUrl ?: domainItem.thumbUrl,
    )
} else domainItem
```

> `PlexSourceHandler` a deja `mediaDao` dans son constructeur. Cout : ~1ms (lookup par PK indexe).

---

### 6. Repository — Interface + Implementation

#### 6.1 Interface
**Fichier**: [MediaDetailRepository.kt](domain/src/main/java/com/chakir/plexhubtv/domain/repository/MediaDetailRepository.kt)

```kotlin
suspend fun refreshMetadataFromTmdb(media: MediaItem): Result<Unit>
```

> Retourne `Result<Unit>` — le ViewModel recharge via `loadDetail()` apres succes.

#### 6.2 Implementation
**Fichier**: [MediaDetailRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt)

Ajouter au constructeur : `TmdbApiService`, `OmdbApiService`, `ApiKeyManager`, `AggregationService`

```kotlin
override suspend fun refreshMetadataFromTmdb(media: MediaItem): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val tmdbKey = apiKeyManager.getTmdbApiKey()
                ?: throw IllegalStateException("TMDB API key not configured")
            val tmdbId = media.tmdbId
            val imdbId = media.imdbId

            if (tmdbId != null) {
                // TMDB path: rating + summary + poster
                val response = when (media.type) {
                    MediaType.Movie -> tmdbApiService.getMovieDetails(tmdbId, tmdbKey)
                    MediaType.Show -> tmdbApiService.getTvDetails(tmdbId, tmdbKey)
                    else -> throw IllegalArgumentException("Unsupported type: ${media.type}")
                }
                if (response.success == false) {
                    throw RuntimeException("TMDB error: ${response.statusMessage}")
                }
                val rating = response.voteAverage ?: throw RuntimeException("No rating")
                val posterUrl = response.posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                mediaDao.updateMetadataByTmdbId(tmdbId, rating, response.overview, posterUrl)
                // Rebuild affected media_unified groups
                val groupKeys = mediaDao.getGroupKeysByTmdbId(tmdbId)
                groupKeys.forEach { aggregationService.rebuildGroup(it) }

            } else if (imdbId != null) {
                // OMDB fallback: rating only
                val omdbKey = apiKeyManager.getOmdbApiKey()
                    ?: throw IllegalStateException("OMDB API key not configured")
                val omdbResponse = omdbApiService.getRating(imdbId, omdbKey)
                val rating = omdbResponse.imdbRating?.toDoubleOrNull()
                    ?: throw RuntimeException("Invalid OMDB rating")
                mediaDao.updateRatingByImdbId(imdbId, rating)
            } else {
                throw IllegalStateException("No external ID (tmdbId or imdbId)")
            }
        }
    }
```

> **Note sur `getTvDetails` / `getMovieDetails`** : les deux retournent des types differents (`TmdbTvResponse` / `TmdbMovieResponse`). Comme ils partagent `voteAverage`, `overview`, `posterPath`, on pourrait extraire une interface commune. Mais pour 2 appels c'est de l'over-engineering — on duplique le when branch.

---

### 7. UI State + Event

**Fichier**: [MediaDetailUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailUiState.kt)

```kotlin
// Dans MediaDetailUiState :
val isRefreshingMetadata: Boolean = false,

// Dans MediaDetailEvent :
data object RefreshMetadata : MediaDetailEvent
```

---

### 8. ViewModel — Handler

**Fichier**: [MediaDetailViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt)

```kotlin
is MediaDetailEvent.RefreshMetadata -> refreshMetadata()
```

```kotlin
private fun refreshMetadata() {
    val media = _uiState.value.media ?: return
    viewModelScope.launch {
        _uiState.update { it.copy(isRefreshingMetadata = true) }
        mediaDetailRepository.refreshMetadataFromTmdb(media)
            .onSuccess {
                loadDetail() // Recharge depuis Room/API avec merge des overrides
            }
            .onFailure { e ->
                emitError(e.message ?: "TMDB refresh failed")
            }
        _uiState.update { it.copy(isRefreshingMetadata = false) }
    }
}
```

> Source de verite unique = Room. Pas de `.copy()` direct sur l'UI state.

---

### 9. UI — Bouton Refresh

**Fichier**: [MediaDetailScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailScreen.kt)

Ajouter un `IconButton` dans la zone d'actions :
```kotlin
IconButton(
    onClick = { onEvent(MediaDetailEvent.RefreshMetadata) },
    enabled = !uiState.isRefreshingMetadata
        && uiState.media?.type in listOf("movie", "show")
        && (uiState.media?.tmdbId != null || uiState.media?.imdbId != null),
) {
    if (uiState.isRefreshingMetadata) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
    } else {
        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_metadata))
    }
}
```

> Desactive si : episode, pas de tmdbId/imdbId, ou refresh en cours.

---

### 10. Strings

**Fichier**: [strings.xml](app/src/main/res/values/strings.xml) + [strings.xml (fr)](app/src/main/res/values-fr/strings.xml)

```xml
<!-- EN -->
<string name="refresh_metadata">Refresh from TMDB</string>
<!-- FR -->
<string name="refresh_metadata">Actualiser depuis TMDB</string>
```

> Les messages d'erreur techniques passent via `emitError()` (deja en anglais dans le codebase).

---

## Fichiers modifies (resume)

| # | Fichier | Action |
|---|---------|--------|
| 1 | `core/network/.../TmdbApiService.kt` | +2 champs par DTO (`overview`, `posterPath`) |
| 2 | `core/database/.../MediaEntity.kt` | +2 colonnes (`overriddenSummary`, `overriddenThumbUrl`) |
| 3 | `core/database/.../PlexDatabase.kt` | Migration 43→44 |
| 4 | `core/database/.../MediaDao.kt` | +3 methodes DAO |
| 5 | `data/.../SyncRepositoryImpl.kt` | Preservation (pattern identique a `scrapedRating`) |
| 6 | `data/.../PlexSourceHandler.kt` | Merge overrides apres API fetch (~5 lignes) |
| 7 | `domain/.../MediaDetailRepository.kt` | +1 methode interface |
| 8 | `data/.../MediaDetailRepositoryImpl.kt` | Implementation + injection |
| 9 | `feature/details/MediaDetailUiState.kt` | +1 event, +1 state |
| 10 | `feature/details/MediaDetailViewModel.kt` | Handler `refreshMetadata()` |
| 11 | `feature/details/MediaDetailScreen.kt` | Bouton Refresh |
| 12 | `res/values/strings.xml` + `values-fr/` | Labels |

### Fichiers NON modifies (vs plan initial)
- ~~`MediaLibraryQueryBuilder.kt`~~ — zero changement SQL
- ~~`AggregationService.kt`~~ — zero changement SQL (rebuild chirurgical via `rebuildGroup()`)
- ~~`MediaMapper.kt`~~ — pas de COALESCE dans le mapper (les colonnes d'affichage contiennent deja les bonnes valeurs)

---

## Risques techniques

| Risque | Severite | Mitigation |
|--------|----------|------------|
| Sync restaure `resolvedThumbUrl` = URL TMDB (pas une URL Plex) | LOW | Aucun code ne manipule `resolvedThumbUrl` comme une URL Plex — c'est juste charge par Coil |
| `success=false` dans reponse TMDB | MEDIUM | Check explicite + throw dans le repository |
| Media sans `tmdbId` ni `imdbId` | LOW | Bouton desactive cote UI (enable condition) |
| TMDB `posterPath` null | LOW | `?.let{}` → reste null → poster Plex inchange |
| `loadDetail()` recharge tout l'ecran | LOW | ~500ms max, acceptable pour une action manuelle |
| `rebuildGroup()` apres refresh | LOW | ~5ms pour un seul groupe, negligeable |

---

## Verification

1. **Test unitaire** : `refreshMetadataFromTmdb()` avec mock TMDB API → verifie ecriture dans `media.summary`, `media.resolvedThumbUrl`, `media.overriddenSummary`, `media.overriddenThumbUrl`, `media.scrapedRating`, `media.displayRating`
2. **Test manuel** :
   - Film avec tmdbId → Refresh → rating/summary/poster changent instantanement
   - Sync auto → les overrides survivent (verifier en DB)
   - Naviguer vers la librairie → le poster/summary TMDB s'affiche
   - Revenir au detail → les overrides sont toujours la (chemin Room)
   - Film sans tmdbId ni imdbId → bouton desactive
3. **Test de non-regression** :
   - Tri par rating fonctionne toujours (`displayRating`)
   - Aucun crash au demarrage (migration 43→44)
   - La vue unifiee affiche toujours les bons posters (pas de melange cross-serveur)
