# Plan: Solution C — Table `media_unified` matérialisée + updates chirurgicaux

## Context

**Environnement : PREPROD — installation vierge.**

**Problème** : La library screen exécute un GROUP BY sur ~69K lignes à chaque page scroll avec correlated MAX, bestRowField(), GROUP_CONCAT, LEFT JOIN id_bridge. Lag visible sur Android TV.

**Solution retenue** : Table matérialisée `media_unified` (~36K lignes plates) + full rebuild en fin de sync + updates chirurgicaux pour les changements incrémentaux. Pas de dirty tracking (dégénère en full rebuild pendant le sync).

**Agrégation multi-source confirmée** : Plex, Xtream (`xtream_$id`) et Backend (`backend_$id`) sont TOUS inclus dans la requête unifiée. Backend s'agrège via imdbId/tmdbId pré-enrichis. Xtream s'agrège après `enrichMovieDetail()` qui ajoute tmdbId.

---

## Prérequis : colonne `groupKey` sur `MediaEntity` (ex-Solution A)

La table `media_unified` utilise `groupKey` comme clé primaire. Ce champ doit d'abord exister sur `MediaEntity`.

### 1. Ajouter `groupKey` à MediaEntity

**Fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt`

```kotlin
// Nouvelle colonne
val groupKey: String = ""  // Calculé par SQL post-bridge, PAS dans le mapper

// Nouvel index dans @Entity(indices = [...])
Index(value = ["type", "groupKey"])
```

### 2. Helper DAO centralisé

**Fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt`

```kotlin
@Query("""
    UPDATE media SET groupKey = COALESCE(
        imdbId,
        (SELECT imdbId FROM id_bridge WHERE id_bridge.tmdbId = media.tmdbId),
        CASE WHEN tmdbId IS NOT NULL AND tmdbId != '' THEN 'tmdb_' || tmdbId ELSE NULL END,
        ratingKey || serverId
    ) WHERE serverId = :serverId AND ratingKey IN (:ratingKeys)
""")
suspend fun updateGroupKeys(serverId: String, ratingKeys: List<String>)
```

### 3. Appeler `updateGroupKeys()` dans les 6 write paths

Chaque write path appelle `updateGroupKeys()` **dans la même transaction**, **après** l'upsert media + bridge :

| # | Fichier | Méthode | Où appeler |
|---|---------|---------|------------|
| 1 | `SyncRepositoryImpl.kt` | `syncLibrary()` | Après `mediaDao.upsertMedia(entities)` + `idBridgeDao.upsertAll()` |
| 2 | `XtreamVodRepositoryImpl.kt` | `syncMovies()` | Après `mediaDao.upsertMedia(entities)` + bridge |
| 3 | `XtreamSeriesRepositoryImpl.kt` | `syncSeries()` | Après `mediaDao.upsertMedia(entities)` + bridge |
| 4 | `BackendRepositoryImpl.kt` | `syncMedia()` | Après chaque `mediaDao.upsertMedia(entities)` + bridge (movies, shows, episodes) |
| 5 | `CollectionSyncWorker.kt` | sync collections | Après upsert des items collection |
| 6 | `XtreamVodRepositoryImpl.kt` | `enrichMovieDetail()` | Après `mediaDao.insertMedia(updated)` — **critique car mute tmdbId** |

**Pattern d'appel** (exemple SyncRepositoryImpl) :
```kotlin
database.withTransaction {
    mediaDao.upsertMedia(entities)
    idBridgeDao.upsertAll(bridgeEntries)
    // ↓ NOUVEAU : calculer groupKey post-bridge
    mediaDao.updateGroupKeys(serverId, entities.map { it.ratingKey })
}
```

### 4. Simplifier `MediaLibraryQueryBuilder`

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilder.kt`

```sql
-- AVANT (unified) :
FROM media LEFT JOIN id_bridge ON media.tmdbId = id_bridge.tmdbId AND media.imdbId IS NULL
GROUP BY media.type, COALESCE(media.imdbId, id_bridge.imdbId, ...)

-- APRÈS (unified) :
FROM media
GROUP BY media.type, media.groupKey
```

Supprimer `UNIFIED_FROM` avec LEFT JOIN. Remplacer `COALESCE(...)` par `media.groupKey` dans GROUP BY et COUNT.

> Note : Ce changement dans le QueryBuilder reste utile même avec media_unified, car le QueryBuilder est utilisé pour le full rebuild et pour le mode non-unifié.

---

## Table `media_unified`

### 5. Nouvelle entité `MediaUnifiedEntity`

**Nouveau fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaUnifiedEntity.kt`

```kotlin
@Entity(
    tableName = "media_unified",
    indices = [
        Index(value = ["type", "titleSortable"]),
        Index(value = ["type", "displayRating"]),
        Index(value = ["type", "addedAt"]),
        Index(value = ["type", "year"]),
        Index(value = ["type", "genres"]),           // filtre par genre
        Index(value = ["type", "contentRating"]),     // filtre parental
    ]
)
data class MediaUnifiedEntity(
    @PrimaryKey val groupKey: String,

    // "Gagnant" du groupe (meilleur metadataScore)
    val bestRatingKey: String,
    val bestServerId: String,

    // Affichage
    val type: String,
    val title: String,
    val titleSortable: String = "",
    val year: Int? = null,
    val summary: String? = null,
    val duration: Long? = null,
    val resolvedThumbUrl: String? = null,
    val resolvedArtUrl: String? = null,
    val resolvedBaseUrl: String? = null,

    // IDs externes
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val guid: String? = null,

    // Pré-agrégés (remplacent GROUP_CONCAT au read-time)
    val serverIds: String? = null,              // "server1=rk1,server2=rk2"
    val alternativeThumbUrls: String? = null,   // "url1|url2|url3"
    val serverCount: Int = 1,

    // Tri / filtrage
    val genres: String? = null,
    val contentRating: String? = null,
    val displayRating: Double = 0.0,
    val avgDisplayRating: Double = 0.0,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,

    // Watch state (mis à jour par chirurgie)
    val viewOffset: Long = 0,
    val viewCount: Long = 0,
    val lastViewedAt: Long = 0,
    val historyGroupKey: String = "",

    // Metadata
    val metadataScore: Int = 0,
    val isOwned: Boolean = false,
    val unificationId: String = "",
    val scrapedRating: Double? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,

    // Navigation vers détail (pas de mediaParts — récupéré depuis media au clic)
    val librarySectionId: String? = null,
    val parentTitle: String? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val grandparentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val index: Int? = null,

    // Virtuel (rempli par GROUP_CONCAT lors du rebuild, ignoré par Room dans SELECT *)
    @Ignore val ratingKeys: String? = null,
)
```

**PAS de `mediaParts`** — blob JSON lourd (~1-5KB/item). Récupéré depuis `media` via `mediaDao.getMedia(bestRatingKey, bestServerId)` au clic sur le détail.

### 6. Enregistrer dans PlexDatabase

**Fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/PlexDatabase.kt`

```kotlin
@Database(
    entities = [
        MediaEntity::class,
        MediaUnifiedEntity::class,  // ← NOUVEAU
        // ... autres entities existantes
    ],
    version = 40,  // ← incrémenter (ou reset en preprod)
)
```

### 7. Nouveau DAO `MediaUnifiedDao`

**Nouveau fichier** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaUnifiedDao.kt`

```kotlin
@Dao
interface MediaUnifiedDao {

    // ═══════════════════════════════════════════
    // FULL REBUILD (appelé par UnifiedRebuildWorker)
    // ═══════════════════════════════════════════

    @Query("DELETE FROM media_unified")
    suspend fun deleteAll()

    @RawQuery
    suspend fun rebuildFromRawQuery(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // PAGING (utilisé par la library screen)
    // ═══════════════════════════════════════════

    @RawQuery(observedEntities = [MediaUnifiedEntity::class])
    fun getPagedUnified(query: SupportSQLiteQuery): PagingSource<Int, MediaUnifiedEntity>

    @RawQuery
    suspend fun getCountUnified(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // UPDATES CHIRURGICAUX
    // ═══════════════════════════════════════════

    // Après RatingSyncWorker
    @Query("""
        UPDATE media_unified
        SET displayRating = :rating, scrapedRating = :rating
        WHERE groupKey IN (
            SELECT DISTINCT groupKey FROM media WHERE imdbId = :imdbId
        )
    """)
    suspend fun updateRatingByImdbId(imdbId: String, rating: Double)

    @Query("""
        UPDATE media_unified
        SET displayRating = :rating, scrapedRating = :rating
        WHERE groupKey IN (
            SELECT DISTINCT groupKey FROM media WHERE tmdbId = :tmdbId
        )
    """)
    suspend fun updateRatingByTmdbId(tmdbId: String, rating: Double)

    // Après PlaybackRepo.flushLocalProgress()
    @Query("""
        UPDATE media_unified
        SET viewOffset = :viewOffset, lastViewedAt = :lastViewedAt
        WHERE bestRatingKey = :ratingKey AND bestServerId = :serverId
    """)
    suspend fun updateProgress(ratingKey: String, serverId: String, viewOffset: Long, lastViewedAt: Long)

    // Après XtreamVodRepo.enrichMovieDetail() — supprime ancien groupe orphelin
    @Query("""
        DELETE FROM media_unified
        WHERE groupKey = :oldGroupKey
        AND NOT EXISTS (SELECT 1 FROM media WHERE groupKey = :oldGroupKey AND type IN ('movie', 'show'))
    """)
    suspend fun deleteOrphanedGroup(oldGroupKey: String)

    // Rebuild un seul groupe (après enrichment ou merge)
    @RawQuery
    suspend fun rebuildSingleGroup(query: SupportSQLiteQuery): Int

    // ═══════════════════════════════════════════
    // QUERIES UTILITAIRES
    // ═══════════════════════════════════════════

    @Query("SELECT COUNT(*) FROM media_unified WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT * FROM media_unified WHERE groupKey = :groupKey")
    suspend fun getByGroupKey(groupKey: String): MediaUnifiedEntity?
}
```

---

## Rebuild Logic

### 8. `AggregationService` — logique de reconstruction

**Nouveau fichier** : `data/src/main/java/com/chakir/plexhubtv/data/service/AggregationService.kt`

```kotlin
@Singleton
class AggregationService @Inject constructor(
    private val database: PlexDatabase,
    private val mediaUnifiedDao: MediaUnifiedDao,
) {
    /**
     * Full rebuild de media_unified depuis la table media.
     * Exécuté UNE FOIS après tous les sync workers.
     * Temps estimé : 2-5 sec sur 69K lignes.
     */
    suspend fun rebuildAll() {
        database.withTransaction {
            mediaUnifiedDao.deleteAll()
            mediaUnifiedDao.rebuildFromRawQuery(buildFullRebuildQuery())
        }
    }

    /**
     * Rebuild un seul groupe (après enrichment Xtream).
     */
    suspend fun rebuildGroup(groupKey: String) {
        mediaUnifiedDao.rebuildSingleGroup(buildSingleGroupRebuildQuery(groupKey))
    }

    /**
     * Gère la mutation de groupKey (enrichMovieDetail ajoute tmdbId).
     * 1. Supprime l'ancien groupe s'il est devenu orphelin
     * 2. Recalcule le nouveau groupe (fusion potentielle avec Plex)
     */
    suspend fun handleGroupKeyMutation(oldGroupKey: String, newGroupKey: String) {
        database.withTransaction {
            mediaUnifiedDao.deleteOrphanedGroup(oldGroupKey)
            rebuildGroup(newGroupKey)
        }
    }

    private fun buildFullRebuildQuery(): SupportSQLiteQuery {
        val sql = """
            INSERT OR REPLACE INTO media_unified (
                groupKey, bestRatingKey, bestServerId,
                type, title, titleSortable, year, summary, duration,
                resolvedThumbUrl, resolvedArtUrl, resolvedBaseUrl,
                imdbId, tmdbId, guid,
                serverIds, alternativeThumbUrls, serverCount,
                genres, contentRating, displayRating, avgDisplayRating,
                addedAt, updatedAt,
                viewOffset, viewCount, lastViewedAt, historyGroupKey,
                metadataScore, isOwned, unificationId, scrapedRating,
                rating, audienceRating,
                librarySectionId, parentTitle, parentRatingKey, parentIndex,
                grandparentTitle, grandparentRatingKey, `index`
            )
            SELECT
                media.groupKey,

                -- Gagnant : ratingKey du meilleur metadataScore
                SUBSTR(
                    MAX(PRINTF('%04d', media.metadataScore + 1000) || '|' || media.ratingKey),
                    6
                ) as bestRatingKey,

                -- Gagnant : serverId du meilleur metadataScore (même ligne)
                SUBSTR(
                    MAX(PRINTF('%04d', media.metadataScore + 1000) || '|' || media.serverId),
                    6
                ) as bestServerId,

                -- Champs du "meilleur" (via bestRowField pattern)
                media.type,
                ${bestField("media.title")},
                ${bestField("media.titleSortable")},
                ${bestField("media.year")},
                ${bestField("media.summary")},
                ${bestField("media.duration")},
                ${bestField("media.resolvedThumbUrl")},
                ${bestField("media.resolvedArtUrl")},
                ${bestField("media.resolvedBaseUrl")},
                ${bestField("media.imdbId")},
                ${bestField("media.tmdbId")},
                ${bestField("media.guid")},

                -- Agrégés
                GROUP_CONCAT(DISTINCT media.serverId || '=' || media.ratingKey) as serverIds,
                GROUP_CONCAT(DISTINCT CASE
                    WHEN media.resolvedThumbUrl IS NOT NULL AND media.resolvedThumbUrl != ''
                    THEN media.resolvedThumbUrl ELSE NULL END
                ) as alternativeThumbUrls,
                COUNT(DISTINCT media.serverId) as serverCount,

                -- Sort/filter
                ${bestField("media.genres")},
                ${bestField("media.contentRating")},
                ${bestField("media.displayRating")},
                AVG(media.displayRating) as avgDisplayRating,
                MAX(media.addedAt) as addedAt,
                MAX(media.updatedAt) as updatedAt,

                -- Watch state (du meilleur row)
                ${bestField("media.viewOffset")},
                ${bestField("media.viewCount")},
                MAX(media.lastViewedAt) as lastViewedAt,
                ${bestField("media.historyGroupKey")},

                -- Metadata
                MAX(media.metadataScore) as metadataScore,
                MAX(media.isOwned) as isOwned,
                ${bestField("media.unificationId")},
                ${bestField("media.scrapedRating")},
                ${bestField("media.rating")},
                ${bestField("media.audienceRating")},

                -- Navigation
                ${bestField("media.librarySectionId")},
                ${bestField("media.parentTitle")},
                ${bestField("media.parentRatingKey")},
                ${bestField("media.parentIndex")},
                ${bestField("media.grandparentTitle")},
                ${bestField("media.grandparentRatingKey")},
                ${bestField("media.`index`")}

            FROM media
            WHERE media.type IN ('movie', 'show')
            AND media.groupKey != ''
            GROUP BY media.type, media.groupKey
        """.trimIndent()

        return SimpleSQLiteQuery(sql)
    }

    /**
     * bestField : extrait le champ depuis la ligne avec le meilleur metadataScore.
     * Même principe que bestRowField() actuel mais simplifié pour le INSERT.
     */
    private fun bestField(field: String): String {
        val sortKey = "PRINTF('%04d', media.metadataScore + 1000)"
        return """NULLIF(SUBSTR(
            MAX($sortKey || CHAR(31) || COALESCE(CAST($field AS TEXT), '')),
            INSTR(MAX($sortKey || CHAR(31) || COALESCE(CAST($field AS TEXT), '')), CHAR(31)) + 1
        ), '')"""
    }

    private fun buildSingleGroupRebuildQuery(groupKey: String): SupportSQLiteQuery {
        // Même SQL que fullRebuild mais avec WHERE media.groupKey = ?
        val sql = """
            INSERT OR REPLACE INTO media_unified (...)
            SELECT ... FROM media
            WHERE media.type IN ('movie', 'show')
            AND media.groupKey = ?
            GROUP BY media.type, media.groupKey
        """.trimIndent()
        return SimpleSQLiteQuery(sql, arrayOf(groupKey))
    }
}
```

> **Note sur bestRatingKey/bestServerId** : Les deux utilisent des `MAX()` indépendants, ce qui risque de sélectionner des lignes différentes en cas d'égalité de score. **Correction** : utiliser le même pattern que le code actuel (single combined `MAX(score||ratingKey||serverId)` avec INSTR-based extraction) tel que documenté dans MEMORY.md sous "CORRELATED MAX (CRITICAL)".

### 9. `UnifiedRebuildWorker`

**Nouveau fichier** : `app/src/main/java/com/chakir/plexhubtv/work/UnifiedRebuildWorker.kt`

```kotlin
@HiltWorker
class UnifiedRebuildWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val aggregationService: AggregationService,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.i("UNIFIED_REBUILD: Starting full rebuild...")
            val startTime = System.currentTimeMillis()

            aggregationService.rebuildAll()

            val duration = System.currentTimeMillis() - startTime
            Timber.i("UNIFIED_REBUILD: Complete in ${duration}ms")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "UNIFIED_REBUILD: Failed")
            Result.retry()
        }
    }
}
```

### 10. Orchestration du worker chain

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt`

```
LibrarySyncWorker termine
  ↓ marque firstSyncComplete
  ↓ chaîne en parallèle :
  ├── CollectionSyncWorker     (existant)
  ├── EpisodeSyncWorker        (SUJET 1, si implémenté)
  └── RatingSyncWorker         (existant)
      ↓ quand TOUS terminés :
      └── UnifiedRebuildWorker  (NOUVEAU — full rebuild UNE FOIS)
```

```kotlin
// Dans LibrarySyncWorker.doWork(), après le sync :
val collectionWork = OneTimeWorkRequestBuilder<CollectionSyncWorker>().build()
val ratingWork = OneTimeWorkRequestBuilder<RatingSyncWorker>().build()
val rebuildWork = OneTimeWorkRequestBuilder<UnifiedRebuildWorker>().build()

workManager
    .beginWith(listOf(collectionWork, ratingWork))  // parallèle
    .then(rebuildWork)                               // séquentiel après tous
    .enqueue()
```

Le `UnifiedRebuildWorker` se lance **après** que CollectionSync et RatingSync ont fini → la table `media` est dans son état final → le rebuild est complet et cohérent.

---

## Intégration Library Screen

### 11. Modifier `MediaLibraryQueryBuilder` — chemin unifié

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/MediaLibraryQueryBuilder.kt`

**Changement principal** : quand `isUnified = true`, requêter `media_unified` au lieu de `media`.

```kotlin
fun buildPagedQuery(config: QueryConfig): SupportSQLiteQuery {
    val sql = StringBuilder()
    val args = mutableListOf<Any>()

    if (config.isUnified) {
        // ═══ NOUVEAU : requête plate sur media_unified ═══
        sql.append("SELECT * FROM media_unified ")
        sql.append("WHERE type = ? ")
        args.add(config.mediaTypeStr)

        // Filtre par serveur spécifique (serverIds LIKE '%serverId=%')
        if (config.selectedServerId != null) {
            sql.append("AND serverIds LIKE ? ")
            args.add("%${config.selectedServerId}=%")
        }

        // Exclure des serveurs
        if (config.excludedServerIds.isNotEmpty()) {
            for (excludedId in config.excludedServerIds) {
                sql.append("AND serverIds NOT LIKE ? ")
                args.add("%${excludedId}=%")
            }
        }

        // Filtre genre
        if (config.genre != null) {
            sql.append("AND genres LIKE ? ")
            args.add("%${config.genre}%")
        }

        // Filtre parental (contentRating)
        if (config.maxAgeRating != null) {
            // ... même logique que l'existant
        }

        // Recherche FTS — Note : pas de FTS sur media_unified pour l'instant,
        // fallback sur LIKE pour la recherche
        if (config.query != null) {
            sql.append("AND (title LIKE ? OR summary LIKE ?) ")
            args.add("%${config.query}%")
            args.add("%${config.query}%")
        }

        // ORDER BY
        when (config.baseSort) {
            "titleSort" -> sql.append("ORDER BY titleSortable ${if (config.isDescending) "DESC" else "ASC"} ")
            "addedAt" -> sql.append("ORDER BY addedAt ${if (config.isDescending) "DESC" else "ASC"} ")
            "rating" -> sql.append("ORDER BY displayRating ${if (config.isDescending) "DESC" else "ASC"} ")
            "year" -> sql.append("ORDER BY year ${if (config.isDescending) "DESC" else "ASC"} ")
            else -> sql.append("ORDER BY titleSortable ASC ")
        }

    } else {
        // ═══ EXISTANT : requête non-unifiée sur media (inchangé) ═══
        // ... garder le code actuel tel quel
        // La non-unified query filtre par serverId+libraryKey, pas d'agrégation multi-serveur
    }

    return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
}
```

**Pour le COUNT** :
```kotlin
fun buildCountQuery(config: QueryConfig): SupportSQLiteQuery {
    if (config.isUnified) {
        // Simple COUNT sur media_unified (pas de GROUP BY)
        val sql = "SELECT COUNT(*) FROM media_unified WHERE type = ? ..."
        // ... mêmes filtres que buildPagedQuery
    } else {
        // ... existant
    }
}
```

### 12. Modifier `LibraryRepositoryImpl`

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/LibraryRepositoryImpl.kt`

```kotlin
// Paging source selon le mode
val pagingSourceFactory = if (isUnified) {
    { mediaUnifiedDao.getPagedUnified(builtQuery) }
} else {
    { mediaDao.getMediaPagedRaw(builtQuery) }
}

val pager = Pager(
    config = PagingConfig(pageSize = 50, prefetchDistance = 15, maxSize = 800),
    pagingSourceFactory = pagingSourceFactory,
)
```

### 13. Mapper `MediaUnifiedEntity → MediaItem`

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/mapper/MediaMapper.kt`

Ajouter une méthode pour convertir `MediaUnifiedEntity` en `MediaItem` (le domain model) :

```kotlin
fun mapUnifiedEntityToDomain(entity: MediaUnifiedEntity): MediaItem {
    return MediaItem(
        ratingKey = entity.bestRatingKey,
        serverId = entity.bestServerId,
        title = entity.title,
        type = entity.type,
        thumbUrl = entity.resolvedThumbUrl,
        artUrl = entity.resolvedArtUrl,
        // ... tous les champs d'affichage
        remoteSources = parseServerIds(entity.serverIds),  // "s1=rk1,s2=rk2" → List<MediaSource>
        alternativeThumbUrls = entity.alternativeThumbUrls?.split("|"),
        // mediaParts = null  → récupéré au clic détail depuis media table
    )
}

private fun parseServerIds(serverIds: String?): List<MediaSource> {
    if (serverIds.isNullOrBlank()) return emptyList()
    return serverIds.split(",").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) MediaSource(serverId = parts[0], ratingKey = parts[1])
        else null
    }
}
```

---

## Updates chirurgicaux (entre les syncs)

### 14. Hook RatingSyncWorker

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/work/RatingSyncWorker.kt`

Dans `flushUpdates()`, après chaque batch d'UPDATEs sur `media` :

```kotlin
private suspend fun flushUpdates(): Int {
    database.withTransaction {
        batch.forEach { u ->
            when (u.type) {
                "tmdb" -> {
                    mediaDao.updateRatingByTmdbId(u.id, u.rating)
                    mediaUnifiedDao.updateRatingByTmdbId(u.id, u.rating)  // ← NOUVEAU
                }
                else -> {
                    mediaDao.updateRatingByImdbId(u.id, u.rating)
                    mediaUnifiedDao.updateRatingByImdbId(u.id, u.rating)  // ← NOUVEAU
                }
            }
        }
    }
}
```

### 15. Hook PlaybackRepository

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/PlaybackRepositoryImpl.kt`

Dans `flushLocalProgress()` :

```kotlin
for (entry in entries) {
    mediaDao.updateProgress(entry.ratingKey, entry.serverId, entry.viewOffset, entry.lastViewedAt)
    // ← NOUVEAU : mettre à jour media_unified si c'est le bestRatingKey
    mediaUnifiedDao.updateProgress(entry.ratingKey, entry.serverId, entry.viewOffset, entry.lastViewedAt)
}
```

> Note : `updateProgress` sur media_unified utilise `WHERE bestRatingKey = ? AND bestServerId = ?`. Si l'utilisateur regarde un film depuis un serveur qui n'est PAS le "best", le viewOffset ne sera pas reflété dans la library. C'est acceptable — au prochain full rebuild (6h max), ça se corrige.

### 16. Hook enrichMovieDetail (Xtream — mutation groupKey)

**Fichier** : `data/src/main/java/com/chakir/plexhubtv/data/repository/XtreamVodRepositoryImpl.kt`

Dans `enrichMovieDetail()` :

```kotlin
val existing = mediaDao.getMedia(ratingKey, serverId) ?: return@withContext Unit
val oldGroupKey = existing.groupKey  // ← sauvegarder AVANT mutation

val updated = existing.copy(
    tmdbId = info.tmdbId?.toString() ?: existing.tmdbId,
    // ... autres champs
)
mediaDao.insertMedia(updated)

// Recalculer le groupKey (tmdbId a pu changer)
mediaDao.updateGroupKeys(serverId, listOf(ratingKey))

// Récupérer le nouveau groupKey
val newEntity = mediaDao.getMedia(ratingKey, serverId)
val newGroupKey = newEntity?.groupKey ?: oldGroupKey

if (oldGroupKey != newGroupKey) {
    // Le groupKey a changé → gérer la fusion/orphelin
    aggregationService.handleGroupKeyMutation(oldGroupKey, newGroupKey)
}
```

---

## Gestion du filtre serveur dans `media_unified`

### 17. Filtre serveur sur `serverIds` (champ texte)

Quand l'utilisateur sélectionne un serveur spécifique dans la vue unifiée, on filtre via LIKE sur `serverIds` :

```sql
-- Filtre "ne montrer que le contenu disponible sur server X"
WHERE serverIds LIKE '%xtream_1=%'

-- Exclure un serveur
WHERE serverIds NOT LIKE '%xtream_1=%'
```

C'est moins performant qu'un index direct, mais suffisant car :
- `media_unified` a ~36K lignes (pas 69K)
- Le LIKE est appliqué APRÈS le filtre `WHERE type = ?` qui est indexé
- Pas de GROUP BY = la requête reste rapide

---

## Résumé des fichiers à modifier/créer

### Nouveaux fichiers (4)
| Fichier | Contenu |
|---------|---------|
| `core/database/.../MediaUnifiedEntity.kt` | Entité Room |
| `core/database/.../MediaUnifiedDao.kt` | DAO avec rebuild + chirurgie + paging |
| `data/.../service/AggregationService.kt` | Logique de rebuild (full + single group) |
| `app/.../work/UnifiedRebuildWorker.kt` | Worker chaîné après sync |

### Fichiers modifiés (10)
| Fichier | Changement |
|---------|------------|
| `core/database/.../MediaEntity.kt` | + colonne `groupKey` + index |
| `core/database/.../MediaDao.kt` | + `updateGroupKeys()` helper |
| `core/database/.../PlexDatabase.kt` | + `MediaUnifiedEntity` + `MediaUnifiedDao` + version++ |
| `data/.../MediaLibraryQueryBuilder.kt` | Chemin unifié → query `media_unified` |
| `data/.../repository/LibraryRepositoryImpl.kt` | PagingSource unifié → `mediaUnifiedDao` |
| `data/.../repository/SyncRepositoryImpl.kt` | + appel `updateGroupKeys()` post-upsert |
| `data/.../mapper/MediaMapper.kt` | + `mapUnifiedEntityToDomain()` |
| `app/.../work/LibrarySyncWorker.kt` | Chaîner `UnifiedRebuildWorker` après les autres |
| `app/.../work/RatingSyncWorker.kt` | + chirurgie `mediaUnifiedDao.updateRating*()` |
| `data/.../repository/PlaybackRepositoryImpl.kt` | + chirurgie `mediaUnifiedDao.updateProgress()` |

### Fichiers modifiés pour groupKey (6 write paths)
| Fichier | Méthode |
|---------|---------|
| `data/.../repository/SyncRepositoryImpl.kt` | `syncLibrary()` |
| `data/.../repository/XtreamVodRepositoryImpl.kt` | `syncMovies()` + `enrichMovieDetail()` |
| `data/.../repository/XtreamSeriesRepositoryImpl.kt` | `syncSeries()` |
| `data/.../repository/BackendRepositoryImpl.kt` | `syncMedia()` |
| `app/.../work/CollectionSyncWorker.kt` | post-upsert collections |

---

## Vérification

### Après implémentation groupKey
1. Vérifier que `groupKey` est non-vide pour tous les items après sync (Plex, Xtream, Backend)
2. Vérifier que les items Plex et Backend avec même imdbId ont le même groupKey
3. Vérifier que les items Xtream sans IDs ont un groupKey unique (ratingKey||serverId)

### Après implémentation media_unified
1. `SELECT COUNT(*) FROM media_unified` ≈ 36K (vs 69K dans media)
2. Scroll rapide dans 17K films → pas de lag (mesurer temps page vs avant)
3. Changer le tri → résultat immédiat
4. "Disponible sur X serveurs" affiché correctement
5. Cliquer sur un film → le détail charge (mediaParts récupéré depuis media)

### Updates chirurgicaux
6. Regarder un film → `media_unified.viewOffset` mis à jour → indicateur "en cours" visible sur la library
7. RatingSyncWorker tourne → ratings mis à jour dans media_unified
8. Enrichir un film Xtream (ouvrir le détail) → si tmdbId ajouté, vérifier que le film fusionne avec le groupe Plex dans la library
