# Plan d'Amelioration des Performances - Enrichissement Episodes

## Contexte

L'analyse des traces montre que l'enrichissement d'un episode prend **6-10 secondes** lors du premier visionnage d'une serie. Trois causes principales identifiees (convergence de mon analyse et celle de Gemini).

**Fichier principal modifie** : `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/EnrichMediaItemUseCase.kt`
**Fichiers secondaires** : `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt` (1 nouvelle query)

---

## Axe 1 : Reduire le scope de recherche (Impact: MAJEUR)

### Probleme
Quand un episode n'est pas dans Room sur les serveurs distants, le Network Fallback (tree traversal) lance `enrichEpisodeViaTreeTraversal()` sur **TOUS** les serveurs Plex actifs (ex: 14 serveurs). Pour chaque serveur, il fait un appel Search API avec timeout 3s. `awaitAll()` attend que TOUS terminent.

Or, la serie parente (ex: "Captain Tsubasa") n'existe typiquement que sur 2-3 serveurs. Les 11 autres serveurs perdent du temps a chercher pour rien.

### Pourquoi Room "miss" pour les episodes distants
Ce n'est PAS un bug de matching. LibrarySyncWorker sync les **shows/movies mais PAS les episodes**. Les episodes distants ne sont dans Room qu'apres avoir ete fetches (browsing saison sur ce serveur, ou premier tree traversal). Le miss est donc **attendu** pour le premier episode.

Mais les **shows** SONT dans Room (synces par LibrarySyncWorker). On peut donc utiliser le show pour pre-filtrer les serveurs.

### Solution
Avant le tree traversal dans `enrichViaNetwork()` (ligne 283), ajouter une **pre-query Room** :

1. Nouvelle query DAO `findServersWithShow(unificationId, excludeServerId)` :
```sql
SELECT DISTINCT serverId, ratingKey FROM media
WHERE type = 'show' AND unificationId = :unificationId
AND unificationId != '' AND serverId != :excludeServerId
```

2. Dans `enrichViaNetwork()`, pour les episodes :
   - Recuperer `parentUnificationId` du show parent (deja fait en ligne 349-351)
   - Appeler `findServersWithShow(parentUnificationId)` → liste des (serverId, showRatingKey)
   - **Filtrer `allServers`** pour ne garder que ceux dans cette liste
   - Passer directement a `traverseShowToEpisode()` avec le `showRatingKey` connu (skip Strategy 2 search API)

3. Si aucun serveur Room-match, fallback au comportement actuel (search API)

### Fichiers modifies
- `MediaDao.kt` : +1 query `findServersWithShow()`
- `MediaDetailRepository.kt` / `MediaDetailRepositoryImpl.kt` : +1 methode
- `EnrichMediaItemUseCase.kt` : modifier `enrichViaNetwork()` pour episodes

### Gain estime
De ~10s (14 serveurs x 3s timeout) a ~2s (2-3 serveurs, acces Room direct)

---

## Axe 2 : Cache show-level pour les episodes suivants (Impact: HAUT)

### Probleme
Quand on enchaine les episodes (binge watching), chaque episode repete la meme recherche "trouver le show sur chaque serveur distant". Episode 2, 3, 4... refont exactement le meme travail que episode 1.

### Solution
Ajouter un `ConcurrentHashMap<String, Map<String, String>>` nomme `showServerCache` dans `EnrichMediaItemUseCase` :

```kotlin
// grandparentTitle → (serverId → showRatingKey)
private val showServerCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
```

- Apres un tree traversal reussi (Strategy 1 ou 2 dans `enrichEpisodeViaTreeTraversal()`), cacher le mapping `serverId → showRatingKey`
- Avant de lancer le tree traversal, verifier ce cache
- Si cache hit : appeler directement `traverseShowToEpisode()` (skip search API)
- Si `traverseShowToEpisode()` echoue malgre le cache : invalider l'entree et fallback

### Fichiers modifies
- `EnrichMediaItemUseCase.kt` uniquement (+1 champ, modification `enrichEpisodeViaTreeTraversal()`)

### Gain estime
Episode 2+ : de ~2s (Axe 1) a ~500ms (skip search, acces direct)

---

## Axe 3 : Deduplication des enrichissements concurrents (Impact: MOYEN)

### Probleme
Le prefetch (background) et le Play (clic utilisateur) lancent l'enrichissement pour le **meme episode** simultanement. Le second appel rate le cache (le premier n'a pas fini d'ecrire) et refait tout le travail reseau en parallele.

### Solution
Ajouter un `ConcurrentHashMap<String, Deferred<MediaItem>>` nomme `inFlight` dans `invoke()` :

```kotlin
private val inFlight = ConcurrentHashMap<String, Deferred<MediaItem>>()
```

Dans `invoke()` (lignes 57-82) :
1. Check `cache` (existant)
2. Check `inFlight` via `putIfAbsent()` :
   - Si un `Deferred` existe deja → `await()` le resultat (zero travail duplique)
   - Sinon → creer un `CompletableDeferred`, l'inserer, executer `enrich()`, `complete()` le deferred, ecrire dans `cache`, retirer de `inFlight`
3. `finally { inFlight.remove(cacheKey) }` pour cleanup

### Fichiers modifies
- `EnrichMediaItemUseCase.kt` uniquement (modification `invoke()`)
- Imports : `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.Deferred`

### Gain estime
Elimination du double enrichissement : -4 a -8s quand prefetch et play se chevauchent

---

## Axe 4 : Timeout global pour le network fallback (Impact: SECURITE)

### Probleme
Si un serveur distant est lent ou mort (ex: "domi's Mac mini" a 5-9s dans les traces), `awaitAll()` bloque tout l'enrichissement meme si les autres serveurs ont deja repondu.

### Solution
Dans `enrichViaNetwork()` (lignes 272-326) :

1. Collecter les resultats dans un `CopyOnWriteArrayList<MediaSource>` au fur et a mesure
2. Wrapper `awaitAll()` dans `withTimeoutOrNull(4000)` :
   - Si tous finissent avant 4s → OK
   - Si timeout → retourner les resultats deja collectes, annuler les jobs restants
3. Appliquer le meme pattern au fallback partiel dans `enrich()` (lignes 158-173)

### Fichiers modifies
- `EnrichMediaItemUseCase.kt` uniquement (modification `enrichViaNetwork()` et bloc `missingPlexServers`)

### Gain estime
Garantie que l'enrichissement ne depasse jamais 4s, meme avec des serveurs morts

---

## Ordre d'implementation

1. **Axe 3** (deduplication) — Plus simple, impact immediat, aucun risque de regression
2. **Axe 1** (pre-filtrage serveurs) — Impact majeur, necessite 1 nouvelle query DAO
3. **Axe 2** (cache show-level) — Complement naturel de l'Axe 1 pour le binge watching
4. **Axe 4** (timeout global) — Filet de securite, a faire en dernier

## Verification

1. **Build** : `./gradlew assembleDebug` doit passer
2. **Test fonctionnel** : Ouvrir une serie Plex → saison → play episode → verifier que l'enrichissement complete avec les sources multi-serveurs → Next → verifier que l'episode 2 est enrichi plus vite
3. **Logs a verifier** dans Logcat :
   - `"deduplicated" to true` → Axe 3 fonctionne
   - `"Pre-filtered servers"` → Axe 1 fonctionne (nombre reduit de serveurs)
   - `"Show Cache Hit"` → Axe 2 fonctionne pour episode 2+
   - Enrichissement total < 4s → Axe 4 fonctionne
4. **Edge cases** : Setup mono-serveur (early exit ligne 99), enrichissement films (pas de tree traversal)
