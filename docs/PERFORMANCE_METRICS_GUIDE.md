# 📊 Guide des Métriques de Performance — PlexHubTV

## Vue d'ensemble

Un système complet de tracking de performance a été ajouté pour mesurer **toutes les latences** du scénario de lecture, de la sélection du contenu jusqu'au démarrage effectif du flux vidéo.

---

## 🎯 Composants Instrumentés

### 1. **PerformanceTracker** (Core)
**Fichier** : `core/common/src/main/java/com/chakir/plexhubtv/core/common/PerformanceTracker.kt`

Tracker centralisé qui mesure les opérations avec :
- ⏱️ Start/End timestamps
- 📍 Checkpoints intermédiaires
- 📋 Métadonnées (durée, cache hit/miss, nombre de sources, etc.)
- 📝 Logs structurés avec tags `[PERF]`

**Catégories** :
- `PLAYBACK` — Flux complet de lecture (clic → player)
- `ENRICHMENT` — Recherche multi-serveur (Room vs Network)
- `IMAGE_LOAD` — Chargement images Coil
- `DB_QUERY` — Requêtes Room
- `NETWORK` — Appels réseau Plex

### 2. **ViewModels Instrumentés**

#### `MediaDetailViewModel` (Films/Shows)
**Fichier** : `app/.../details/MediaDetailViewModel.kt`

**Métriques tracées** :
```
playback_movie_{ratingKey}
├─ Smart Start Resolved (getNextEpisodeUseCase)
├─ Enrichment (Cache Hit) ← Si remoteSources déjà enrichi
│  OU Enrichment (Fresh) ← Si enrichissement synchrone
├─ Source Selection Dialog Shown (si >1 serveur)
│  OU Single Source - Direct Play
├─ Queue Built (getPlayQueueUseCase)
├─ PlaybackManager Initialized
└─ Navigation to Player Triggered
```

**Logs exemples** :
```
⏱️ [PERF][START][PLAYBACK] Movie/Show PlayClicked → Player | ID=playback_movie_12345
🔹 [PERF][CHECKPOINT][PLAYBACK] Smart Start Resolved (+5ms) | resolved=true
🔹 [PERF][CHECKPOINT][PLAYBACK] Enrichment (Cache Hit) (+12ms) | sources=2
🔹 [PERF][CHECKPOINT][PLAYBACK] Queue Built (+8ms) | duration=6ms | items=1
⏱️ [PERF][END][PLAYBACK] Movie/Show PlayClicked → Player | TOTAL=25ms | ✅ SUCCESS
```

#### `SeasonDetailViewModel` (Épisodes)
**Fichier** : `app/.../details/SeasonDetailViewModel.kt`

**Métriques tracées** :
```
playback_episode_{ratingKey}
├─ UI Loading State Shown (isResolvingSources=true)
├─ Enrichment Success/Failed ← Room-first (~5ms) ou network (500-5000ms)
│   └─ cacheHit = (duration < 10ms)
├─ Queue Built (getPlayQueueUseCase)
├─ PlaybackManager Initialized
├─ UI Loading State Hidden
├─ Source Selection Dialog Shown (si >1 serveur)
│  OU Single Source - Direct Navigation
└─ User Selected Source (si dialog)
```

**Logs exemples** :
```
⏱️ [PERF][START][PLAYBACK] Episode PlayClicked → Player | title=S01E05 | viewOffset=45000
🔹 [PERF][CHECKPOINT][PLAYBACK] UI Loading State Shown (+2ms)
🔹 [PERF][CHECKPOINT][PLAYBACK] Enrichment Success (+1850ms) | duration=1850ms | sources=3 | cacheHit=false
🔹 [PERF][CHECKPOINT][PLAYBACK] Queue Built (+1860ms) | items=5
⏱️ [PERF][END][PLAYBACK] Episode PlayClicked → Player | TOTAL=1865ms | ✅ SUCCESS
```

### 3. **PlayerController** (Chargement Média)
**Fichier** : `app/.../player/controller/PlayerController.kt`

**Métriques tracées** :
```
player_load_{ratingKey}
├─ Settings Loaded (Parallel) ← qualityPref + engine + clientId async
├─ Media Detail (Cache Hit) ← PlaybackManager cache
│  OU Media Detail (Network Fetch) ← getMediaDetailUseCase
├─ Media Loaded (title, parts count)
├─ Tracks Populated (audioTracks, subtitles count)
├─ Stream URL Built (directPlay, bitrate)
├─ ExoPlayer Mode
│   ├─ ExoPlayer MediaItem Created
│   ├─ ExoPlayer Prepared (prepare() duration)
│   ├─ ExoPlayer Seek Applied (position si resume)
│   ├─ ExoPlayer PlayWhenReady=true
│   ├─ ExoPlayer STATE_BUFFERING
│   ├─ ExoPlayer STATE_READY (Buffered)
│   └─ 🎬 PLAYBACK STARTED (isPlaying=true) ← FIN DU TRACKING
└─ MPV Player Mode (alternative)
```

**Logs exemples** :
```
⏱️ [PERF][START][PLAYBACK] PlayerController.loadMedia → Stream Ready | ratingKey=67890
🔹 [PERF][CHECKPOINT][PLAYBACK] Settings Loaded (Parallel) (+3ms) | duration=3ms
🔹 [PERF][CHECKPOINT][PLAYBACK] Media Detail (Cache Hit) (+5ms) | duration=2ms
🔹 [PERF][CHECKPOINT][PLAYBACK] Tracks Populated (+12ms) | audioTracks=2 | subtitles=5
🔹 [PERF][CHECKPOINT][PLAYBACK] Stream URL Built (+14ms) | directPlay=true | bitrate=20000
🔹 [PERF][CHECKPOINT][PLAYBACK] ExoPlayer Prepared (+230ms) | duration=216ms
🔹 [PERF][CHECKPOINT][PLAYBACK] ExoPlayer STATE_BUFFERING (+250ms)
🔹 [PERF][CHECKPOINT][PLAYBACK] ExoPlayer STATE_READY (Buffered) (+850ms)
🔹 [PERF][CHECKPOINT][PLAYBACK] 🎬 PLAYBACK STARTED (isPlaying=true) (+920ms)
⏱️ [PERF][END][PLAYBACK] PlayerController.loadMedia → Stream Ready | TOTAL=920ms | ✅ SUCCESS
```

### 4. **EnrichMediaItemUseCase** (Multi-Server)
**Fichier** : `domain/.../usecase/EnrichMediaItemUseCase.kt`

**Métriques tracées** :
```
enrich_{ratingKey}
├─ Server List Loaded (servers count)
├─ Single Server - No Enrichment Needed
│  OU Room Query (Hit/Miss) ← findRemoteSources duration
│  OU Network Fallback Started
│      ├─ Network Search: Server1 (duration, results count)
│      ├─ Network Search: Server2
│      ├─ Network Search FAILED: Server3 (error)
│      └─ Network Fallback Complete (totalDuration, totalMatches)
```

**Logs exemples** :
```
⏱️ [PERF][START][ENRICHMENT] Enrich Media for Multi-Server Sources | title=Inception | type=Movie
🔹 [PERF][CHECKPOINT][ENRICHMENT] Server List Loaded (+2ms) | servers=3
🔹 [PERF][CHECKPOINT][ENRICHMENT] Room Query (Hit) (+7ms) | duration=5ms | matches=2
⏱️ [PERF][END][ENRICHMENT] Enrich Media for Multi-Server Sources | TOTAL=10ms | ✅ SUCCESS | sources=3
```

**Ou en cas de fallback réseau** :
```
🔹 [PERF][CHECKPOINT][ENRICHMENT] Room Query (Miss) (+6ms) | duration=6ms
🔹 [PERF][CHECKPOINT][ENRICHMENT] Network Fallback Started (+7ms)
🔹 [PERF][CHECKPOINT][ENRICHMENT] Network Search: PlexServer1 (+850ms) | duration=843ms | results=5
🔹 [PERF][CHECKPOINT][ENRICHMENT] Network Search: PlexServer2 (+1420ms) | duration=570ms | results=3
🔹 [PERF][CHECKPOINT][ENRICHMENT] Network Search FAILED: PlexServer3 (+2100ms) | error=timeout
🔹 [PERF][CHECKPOINT][ENRICHMENT] Network Fallback Complete (+2120ms) | totalDuration=2113ms | totalMatches=2
⏱️ [PERF][END][ENRICHMENT] Enrich Media for Multi-Server Sources | TOTAL=2125ms | ✅ SUCCESS | sources=3
```

### 5. **PerformanceImageInterceptor** (Coil)
**Fichier** : `app/.../di/image/PerformanceImageInterceptor.kt`

**Métriques tracées** :
- URL chargée
- Cache source (MEMORY / DISK / NETWORK)
- Durée de chargement

**Logs exemples** :
```
⏱️ [PERF][START][IMAGE_LOAD] Coil Image Load | url=http://...poster.jpg
⏱️ [PERF][END][IMAGE_LOAD] Coil Image Load | TOTAL=35ms | ✅ SUCCESS | cacheSource=MEMORY
```

---

## 📈 Exemple de Flux Complet (Épisode)

### Scénario : Utilisateur clique sur un épisode dans SeasonDetailScreen

```
🎬 USER CLICK → SeasonDetailEvent.PlayEpisode

1️⃣ SeasonDetailViewModel (TOTAL: ~1200ms si cache froid)
   ⏱️ [START] playback_episode_12345
   🔹 UI Loading State Shown (+2ms)
   🔹 Enrichment Success (+1150ms) ← EnrichMediaItemUseCase
      ↓
      ⏱️ [START] enrich_12345
      🔹 Room Query (Hit) (+5ms) | matches=2
      ⏱️ [END] enrich_12345 | TOTAL=7ms
   🔹 Queue Built (+1155ms) | items=8
   🔹 PlaybackManager Initialized (+1157ms)
   🔹 UI Loading State Hidden (+1159ms)
   🔹 Single Source - Direct Navigation (+1160ms)
   ⏱️ [END] playback_episode_12345 | TOTAL=1165ms ✅

2️⃣ NAVIGATION → PlayerController.initialize() → loadMedia()

3️⃣ PlayerController (TOTAL: ~950ms)
   ⏱️ [START] player_load_12345
   🔹 Settings Loaded (Parallel) (+3ms) | duration=3ms
   🔹 Media Detail (Cache Hit) (+5ms) | duration=2ms ← PlaybackManager
   🔹 Media Loaded (+7ms) | title=S01E05 | parts=1
   🔹 Tracks Populated (+15ms) | audioTracks=2 | subtitles=5
   🔹 Stream URL Built (+18ms) | directPlay=true | bitrate=20000
   🔹 ExoPlayer MediaItem Created (+22ms) | duration=4ms
   🔹 ExoPlayer Prepared (+240ms) | duration=218ms
   🔹 ExoPlayer Seek Applied (+242ms) | position=45000
   🔹 ExoPlayer PlayWhenReady=true (+244ms)
   🔹 ExoPlayer STATE_BUFFERING (+260ms)
   🔹 ExoPlayer STATE_READY (Buffered) (+850ms)
   🔹 🎬 PLAYBACK STARTED (isPlaying=true) (+950ms)
   ⏱️ [END] player_load_12345 | TOTAL=950ms ✅

🎬 VIDEO PLAYBACK STARTS

📊 LATENCE TOTALE: 1165ms (ViewModel) + 950ms (Player) = ~2115ms
```

---

## 🔍 Comment Analyser les Logs

### 1. **Filtrer par catégorie**
```bash
# Voir UNIQUEMENT les flux de playback
adb logcat | grep "\[PERF\]\[.*\]\[PLAYBACK\]"

# Voir UNIQUEMENT les enrichments
adb logcat | grep "\[PERF\]\[.*\]\[ENRICHMENT\]"

# Voir UNIQUEMENT les images
adb logcat | grep "\[PERF\]\[.*\]\[IMAGE_LOAD\]"
```

### 2. **Identifier les bottlenecks**
Les checkpoints affichent les durées cumulées. Cherchez les **grands deltas** :

```
🔹 [CHECKPOINT] Enrichment Success (+1850ms) ← ⚠️ NETWORK FALLBACK SLOW!
🔹 [CHECKPOINT] Queue Built (+1860ms)        ← Delta = 10ms (OK)
```

Si `Enrichment Success` prend >1s, c'est que :
- Room n'a pas trouvé de match (cache miss)
- Network fallback a interrogé plusieurs serveurs lents

### 3. **Vérifier le cache hit rate**

**Enrichment** :
```
# Cache hit (optimal ~5-10ms)
🔹 Room Query (Hit) (+5ms) | matches=2

# Cache miss → Network fallback (500-5000ms)
🔹 Room Query (Miss) (+6ms)
🔹 Network Fallback Started
```

**Coil Images** :
```
cacheSource=MEMORY  ← Optimal (~10-50ms)
cacheSource=DISK    ← Bon (~50-200ms)
cacheSource=NETWORK ← Lent (500-2000ms)
```

**PlayerController Media** :
```
Media Detail (Cache Hit) ← PlaybackManager (optimal ~2ms)
Media Detail (Network Fetch) ← getMediaDetailUseCase (50-500ms)
```

### 4. **Comparer Film vs Épisode**

**Film** : Devrait être PLUS RAPIDE car :
- Enrichment background déjà fait (cache opportuniste ligne 98)
- Pas de queue épisodes

**Épisode** : Peut être PLUS LENT mais :
- Prefetch des 3 premiers episodes réchauffe le cache
- Si l'utilisateur clique vite, le prefetch n'est pas terminé → cache miss

---

## 🐛 Scénarios de Debug Typiques

### Problème : "Enrichment prend 5 secondes"
**Logs à chercher** :
```
🔹 Network Search: PlexServer1 (+2500ms) | duration=2500ms ← ⚠️ Serveur lent!
🔹 Network Search FAILED: PlexServer2 (+5200ms) | error=timeout ← ⚠️ Serveur offline!
```

**Solutions** :
- Vérifier connexion réseau des serveurs Plex
- Augmenter le timeout si réseau lent
- Améliorer la stratégie Room-first (plus de syncs)

### Problème : "Player buffering infini"
**Logs à chercher** :
```
🔹 ExoPlayer STATE_BUFFERING (+260ms)
... (pas de STATE_READY) ← ⚠️ Problème réseau ou codec
```

**Solutions** :
- Vérifier la connectivité au serveur
- Tester en DirectPlay vs Transcode
- Vérifier les logs ExoPlayer pour erreurs codec

### Problème : "Images ne chargent pas"
**Logs à chercher** :
```
⏱️ [END][IMAGE_LOAD] ... | ❌ FAILED | error=timeout
```

**Solutions** :
- Vérifier `PlexImageKeyer` (strip hostname)
- Augmenter timeout Coil
- Vérifier taille cache (mémoire vs disque)

---

## 📊 Métriques Cibles (Objectifs de Performance)

| Opération | Cible | Acceptable | Lent |
|-----------|-------|------------|------|
| **Enrichment (Room Hit)** | < 10ms | < 50ms | > 100ms |
| **Enrichment (Network)** | < 500ms | < 2s | > 5s |
| **Queue Build** | < 10ms | < 50ms | > 100ms |
| **Player loadMedia** | < 500ms | < 1s | > 2s |
| **ExoPlayer Prepare** | < 200ms | < 500ms | > 1s |
| **Buffering → Ready** | < 500ms | < 2s | > 5s |
| **Image Load (Memory)** | < 50ms | < 100ms | > 200ms |
| **Image Load (Network)** | < 500ms | < 1s | > 2s |
| **TOTAL (Clic → Playback)** | < 1s | < 2.5s | > 5s |

---

## 🎯 Prochaines Étapes

1. **Collecter des métriques réelles** sur différents scénarios :
   - Mono-serveur vs multi-serveur
   - Cache chaud vs cache froid
   - DirectPlay vs Transcode
   - Réseau local vs remote (VPN, WAN)

2. **Identifier les bottlenecks réels** :
   - Quels serveurs sont lents ?
   - Enrichment Room hit rate ?
   - Player buffering patterns ?

3. **Optimisations ciblées** :
   - Améliorer prefetch (plus d'épisodes ? background ?)
   - Timeout adaptatifs par serveur
   - Cache persistent (enrichment sur disque ?)

4. **Monitoring en production** :
   - Agréger les métriques (moyennes, p50, p95, p99)
   - Alertes si latence > seuils
   - Analytics par type de média / serveur / réseau

---

## 📝 Notes Importantes

- **Tous les logs sont en Timber VERBOSE/DEBUG** → Activer les logs si nécessaire
- **IDs opération uniques** (timestamp) → Permet de suivre un flux end-to-end
- **Checkpoints cumulatifs** → Temps depuis START, pas delta entre checkpoints
- **Success/Failure** → Permet de tracker error rate par opération
- **Métadonnées riches** → `sources`, `duration`, `cacheHit`, etc. pour analyse fine

**Bon debugging! 🚀**
