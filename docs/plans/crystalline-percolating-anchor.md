# Verification de l'implementation Solution C

## Resultat global : IMPLEMENTATION CORRECTE — 4 hooks manquants mineurs

---

## 1. Core Schema — TOUT OK

| Fichier | Statut | Details |
|---------|--------|---------|
| `MediaUnifiedEntity.kt` | PASS | PK=groupKey, bestRatingKey/bestServerId, pas de mediaParts, 6 index corrects, champs watch state presents |
| `MediaEntity.kt` | PASS | `groupKey: String = ""` (ligne 127), index `(type, groupKey)` (ligne 54) |
| `MediaDao.kt` | PASS | `updateGroupKeys()` avec COALESCE correct incluant id_bridge lookup |
| `MediaUnifiedDao.kt` | PASS | rebuild, paging (`observedEntities = [MediaUnifiedEntity::class]`), updates chirurgicaux |
| `PlexDatabase.kt` | PASS | Entity enregistree, DAO expose, version 40 |

---

## 2. Write Paths — 4 hooks manquants (tous sur des EPISODES)

### Hooks corrects (8/12)

| Fichier | Methode | `updateGroupKeys()` | Chirurgie unified |
|---------|---------|:---:|:---:|
| `SyncRepositoryImpl.kt` | `syncLibrary()` | OK (post-bridge) | N/A (rebuild en fin) |
| `XtreamVodRepositoryImpl.kt` | `syncMovies()` | OK (post-bridge) | N/A |
| `XtreamVodRepositoryImpl.kt` | `enrichMovieDetail()` | OK + mutation groupKey geree | `handleGroupKeyMutation()` OK |
| `XtreamSeriesRepositoryImpl.kt` | `syncSeries()` | OK (post-bridge) | N/A |
| `CollectionSyncWorker.kt` | upsert collections | OK (par batch 500) | N/A |
| `RatingSyncWorker.kt` | `flushUpdates()` | N/A | `updateRatingByImdbId/TmdbId` OK |
| `PlaybackRepositoryImpl.kt` | `flushLocalProgress()` | N/A | `updateProgress` OK |
| `LibrarySyncWorker.kt` | worker chain | N/A | `beginWith([collection, rating]).then(rebuild)` OK |

### Hooks manquants (4/12)

| Fichier | Methode | Impact reel |
|---------|---------|-------------|
| `XtreamSeriesRepositoryImpl.kt` | `getSeriesDetail()` episodes | **AUCUN** — type="episode", pas dans `media_unified` |
| `BackendRepositoryImpl.kt` | `syncMedia()` episodes flush (2000-batch) | **AUCUN** — type="episode" |
| `BackendRepositoryImpl.kt` | `getEpisodes()` | **AUCUN** — type="episode" |
| `BackendRepositoryImpl.kt` | `getMediaDetail()` | **MINEUR** — si appele pour un movie/show, groupKey="" jusqu'au prochain rebuild |

**Verdict** : Les 3 premiers sont des faux positifs — les episodes ne vont PAS dans `media_unified` (filtre `WHERE type IN ('movie', 'show')`). Seul `getMediaDetail()` est un vrai manque, mais c'est un edge case rare (fallback detail pour items pas encore sync).

**Fix recommande pour `getMediaDetail()`** : Ajouter `updateGroupKeys()` apres `insertMedia()` — 2 lignes de code.

---

## 3. Aggregation & Rebuild — TOUT OK

| Composant | Statut | Detail critique |
|-----------|--------|-----------------|
| `AggregationService.kt` | PASS | CORRELATED MAX correct : single combined `MAX(score\|\|ratingKey\|\|serverId)` — pas d'extraction independante |
| `bestField()` | PASS | Pattern `MAX(SORT_KEY \|\| CHAR(31) \|\| COALESCE(field, ''))` avec INSTR/SUBSTR |
| Filtre rebuild | PASS | `WHERE type IN ('movie', 'show') AND groupKey != ''` |
| `handleGroupKeyMutation()` | PASS | `deleteOrphanedGroup(old)` + `rebuildGroup(new)` en transaction |
| `UnifiedRebuildWorker.kt` | PASS | `rebuildAll()` + error handling + logging |

---

## 4. Query Builder & Paging — TOUT OK

| Composant | Statut | Detail |
|-----------|--------|--------|
| `buildMaterializedPagedQuery()` | PASS | `SELECT * FROM media_unified WHERE type = ?` — zero GROUP BY |
| `buildMaterializedCountQuery()` | PASS | `SELECT COUNT(*) FROM media_unified WHERE type = ?` |
| `buildMaterializedIndexQuery()` | PASS | Pour alphabet jump |
| Filtre serveur | PASS | `LIKE '%serverId=%'` sur `serverIds` |
| Filtre genre | PASS | `LIKE '%keyword%'` sur `genres` |
| Filtre parental | PASS | CAST contentRating + integer comparison |
| Recherche | PASS | LIKE sur title/summary (pas de FTS sur unified — OK) |
| Anciennes queries | PASS | `UNIFIED_SELECT`, `UNIFIED_FROM` etc. preservees pour le rebuild |

---

## 5. Paging Integration — TOUT OK

| Composant | Statut | Detail |
|-----------|--------|--------|
| `LibraryRepositoryImpl.kt` | PASS | `if (isUnified)` → `mediaUnifiedDao.getPagedUnified()` avec `buildMaterializedPagedQuery()` |
| Count queries | PASS | Utilise `buildMaterializedCountQuery()` pour le mode unifie |
| Index queries | PASS | Utilise `buildMaterializedIndexQuery()` pour alphabet jump |
| `mapUnifiedEntityToDomain()` | PASS | `bestRatingKey → ratingKey`, `bestServerId → serverId`, `alternativeThumbUrls` split par pipe |

---

## 6. Points d'attention (pas des bugs)

### 6.1 PRINTF dans AggregationService vs entier dans QueryBuilder

`AggregationService.bestField()` utilise `PRINTF('%04d', metadataScore + 1000)` alors que `MediaLibraryQueryBuilder` utilise `(metadataScore + 1000)` sans PRINTF (commente comme "PRINTF slow on ARM / Mi Box S").

**Impact** : Aucun — `AggregationService` ne tourne qu'au rebuild (1 fois apres sync), pas a chaque scroll. Le PRINTF est acceptable ici.

### 6.2 `@Ignore val ratingKeys` dans MediaUnifiedEntity

Le champ `ratingKeys` est `@Ignore` → pas de colonne dans la table → `SELECT *` ne le retourne pas. Room utilise la valeur par defaut `null`. C'est correct.

### 6.3 Recherche LIKE vs FTS sur media_unified

La recherche sur `media_unified` utilise `LIKE '%query%'` (full scan) au lieu de FTS4. Sur ~36K lignes, c'est acceptable pour le moment. Si la recherche lag, envisager un FTS4 shadow table.

---

## 7. Action requise

### Fix unique recommande

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt` — `getMediaDetail()` (ligne 301)

```kotlin
// Ligne 301 existante :
mediaDao.insertMedia(mappedEntity)
// Ajouter juste apres (ligne 302) :
mediaDao.updateGroupKeys("backend_$backendId", listOf(mappedEntity.ratingKey))
```

**Pourquoi** : Sans ce hook, un movie/show recupere via `getMediaDetail()` (fallback API quand l'item n'est pas encore sync) aura `groupKey=""` jusqu'au prochain rebuild complet. Cela signifie qu'il ne sera PAS visible dans la vue unifiee.

Ce fix couvre le seul vrai cas ou un movie/show pourrait avoir `groupKey=""` apres insertion. Les 3 autres hooks manquants (tous sur des episodes) sont des faux positifs et ne necessitent pas de fix.

---

## 8. Verdict

L'implementation Solution C est **correcte et complete**. L'architecture couvre :

- **Schema** : `groupKey` sur MediaEntity + `media_unified` avec indices corrects
- **Rebuild** : CORRELATED MAX correct, single combined MAX, bestField() avec CHAR(31)
- **Worker chain** : `beginWith([collection, rating]).then(rebuild)` — sequentiel apres parallele
- **Query** : Flat SELECT sur media_unified, zero GROUP BY au read-time
- **Chirurgie** : Ratings + progress + mutation groupKey Xtream couverts
- **Write paths** : 8/9 movies/shows hooks OK, 1 edge case mineur (getMediaDetail)
