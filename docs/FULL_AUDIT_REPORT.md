# PLEXHUBTV — DEEP AUDIT v5 — RAPPORT FINAL

**Date**: 2026-03-09
**Auditeur**: Claude Opus 4.6 (7 agents specialises + 1 agent de synthese)
**Branche**: `claude/continue-plexhubtv-refactor-YO43N`
**Scope**: Codebase complet (~35K LOC Kotlin, 120+ fichiers)

---

## RESUME EXECUTIF

PlexHubTV est un media player Android TV fonctionnel pour Plex, IPTV et backends custom. Le coeur de l'app (lecture video, navigation bibliotheque, multi-serveur) fonctionne correctement. Cependant, l'audit revele **102 findings** dont **14 P0 critiques** qui compromettent la securite, la stabilite, et la fiabilite de l'application.

Les 5 problemes les plus graves:
1. **Securite enfant inexistante** — Le toggle "Kids Mode" est cosmetique: aucun filtrage de contenu n'est implemente (AGENT-6-001)
2. **Perte de donnees silencieuse** — `fallbackToDestructiveMigration()` efface la DB Room entiere lors d'un upgrade rate, avec des gaps de migration manquantes (AGENT-4-001, AGENT-4-002)
3. **Tokens en clair** — Les tokens Plex et credentials IPTV sont stockes sans encryption dans Room et loggues via HTTP interceptor (AGENT-3-003, AGENT-7-014)
4. **Crash player** — `PlayerController.scope` cree un nouveau CoroutineScope a chaque acces, causant des coroutines orphelines et des race conditions (AGENT-1-001)
5. **Feature factice** — Le systeme de telechargement retourne "succes" sans rien telecharger (AGENT-6-002)

**Verdict**: L'app necessite des corrections P0 avant toute mise en production. Score: **28/100**.

---

## SCORE DE PRODUCTION

```
Score de depart:                          100
14 P0 critiques (x-15, securite x-20):   -225
  dont 3 securite P0 (x-20):              -60
  dont 11 autres P0 (x-15):              -165
35 P1 importants (x-8):                  -280
53 P2 mineurs (x-3):                     -159
Patterns pervasifs (x-5 chacun):
  - Strings hardcodees (FR+EN, 6 agents)   -5
  - Caches unbounded (3 instances)         -5
  - Missing @Transaction (3 instances)     -5
  - URL resolution dupliquee (12+ lieux)   -5
                                         ------
Score brut:                              -584 -> plancher a 0
Score ajuste (app fonctionnelle,
  cas d'usage principal OK):               28
```

| Plage   | Signification                                                          |
|---------|------------------------------------------------------------------------|
| 0-30    | Non deployable. Defaillances critiques probables en usage normal.      |
| 31-50   | Risque eleve. Rework significatif requis avant mise en production.     |
| 51-70   | Deployable uniquement pour usage interne avec monitoring rapproche.    |
| 71-85   | Viable en production avec corrections ciblees. Risques connus bornes. |
| 86-100  | Pret pour la production. Ameliorations mineures uniquement.            |

**Score: 28/100 — Non deployable en l'etat.**

---

## VERDICT PLAY STORE

**NON RECOMMANDE** pour publication en l'etat. Blockers:
1. **Kids Mode factice** — Risque legal (COPPA/RGPD enfants) si presente comme fonctionnel
2. **Downloads factice** — Feature trompeuse (succes silencieux sans telechargement)
3. **allowBackup="true"** — Toute donnee utilisateur extractible via ADB
4. **Perte de donnees** — `fallbackToDestructiveMigration()` peut effacer l'historique/favoris
5. **Zero tests instrumentes** — Aucune validation des requetes SQL complexes

**Prerequis minimaux pour le Play Store**:
- [ ] Corriger ou retirer Kids Mode UI
- [ ] Corriger ou retirer Downloads UI
- [ ] `allowBackup="false"` ou `android:dataExtractionRules`
- [ ] Remplacer `fallbackToDestructiveMigration()` par migrations explicites
- [ ] Ajouter tests Room DAO pour les requetes critiques

---

## TOP 10 FINDINGS

| # | ID | Severite | Titre | Agent | Impact |
|---|-----|---------|-------|-------|--------|
| 1 | AGENT-6-001 | P0 | Kids Mode: zero filtrage contenu | Regressions | Enfants voient tout le catalogue adulte |
| 2 | AGENT-4-001 | P0 | fallbackToDestructiveMigration() en prod | Database | DB effacee silencieusement a l'upgrade |
| 3 | AGENT-1-001 | P0 | scope getter cree un CoroutineScope par acces | Coroutines | Coroutines orphelines, fuite memoire |
| 4 | AGENT-3-002 | P0 | allowBackup="true" | Security | Extraction donnees utilisateur via ADB |
| 5 | AGENT-3-003 | P0 | Tokens Plex stockes en clair dans Room | Security | Vol de tokens par extraction DB |
| 6 | AGENT-6-002 | P0 | Downloads entierement stub | Regressions | Feature affichee mais non fonctionnelle |
| 7 | AGENT-6-003 | P0 | Auto-next sans countdown | Regressions | Pas de binge-watching automatique |
| 8 | AGENT-4-002 | P0 | Migrations DB manquantes (v12-15, v16-18) | Database | Destruction silencieuse via fallback |
| 9 | AGENT-2-001 | P0 | MPVLib observers jamais retires | Memory | Fuite memoire + callbacks sur objet detruit |
| 10 | AGENT-1-002 | P0 | PlaybackManager mutations non-atomiques | Coroutines | Etat incoherent entre media/queue/index |

---

## TOUS LES FINDINGS PAR AGENT

---

### AGENT 1 — COROUTINES & THREADING (13 findings)

#### P0 — CRITIQUES

```
[AGENT-1-001] PlayerController.scope getter cree un nouveau CoroutineScope a chaque acces
Severite: P0
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:59-60
Code:
  private val scope: CoroutineScope
      get() = CoroutineScope(sessionJob + mainDispatcher + globalHandler)
Probleme: Chaque acces a `scope` instancie un NOUVEAU CoroutineScope. Les coroutines lancees
  via `scope.launch { }` ne sont pas liees au meme scope — elles deviennent orphelines.
  `sessionJob.cancel()` n'annule que les coroutines lancees via le dernier scope cree.
Impact utilisateur: Fuites memoire progressives. Coroutines de position tracking et scrobbling
  survivent apres la fermeture du player.
Fix: Remplacer `get() =` par `=` (initialisation unique):
  private val scope = CoroutineScope(sessionJob + mainDispatcher + globalHandler)
  Reinitialiser dans initSession() quand sessionJob est recree.
Effort: 1h
Sprint: 1
```

```
[AGENT-1-002] PlaybackManager — mutations multi-StateFlow non atomiques
Severite: P0
Fichier: domain/src/main/java/com/chakir/plexhubtv/domain/service/PlaybackManager.kt:26-40
Code:
  fun play(media: MediaItem, queue: List<MediaItem> = emptyList()) {
      _currentMedia.value = media        // mutation 1
      _playQueue.value = ...             // mutation 2
      _currentIndex.value = ...          // mutation 3
  }
Probleme: 3 StateFlows mutes sequentiellement. Un collecteur qui combine ces 3 flows
  (via combine()) verra des etats intermediaires incoherents (nouveau media + ancienne queue).
Impact utilisateur: Glitch visuel possible — affichage du mauvais titre/thumbnail pendant
  une fraction de seconde lors du changement de media.
Fix: Unifier en un seul StateFlow<PlaybackState> avec data class:
  data class PlaybackState(val media: MediaItem?, val queue: List<MediaItem>, val index: Int)
Effort: 4h
Sprint: 1
```

```
[AGENT-1-003] PlayerController — 9+ vars mutables sans @Volatile dans @Singleton
Severite: P0
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:65-79
Code:
  var player: ExoPlayer? = null
  var mpvPlayer: MpvPlayer? = null
  private var isMpvMode = false
  private var isDirectPlay = false
  private var sessionJob = SupervisorJob()
  private var positionTrackerJob: Job? = null
  private var hasShownResumeToast = false
  private var pendingSeekTarget: Long? = null
  private var isDirectUrlPlayback = false
Probleme: Ces variables sont lues/ecrites depuis Dispatchers.Main et Dispatchers.IO
  sans @Volatile ni synchronisation. Sur ARM multi-coeur (tous les Android TV),
  un thread peut lire une valeur stale depuis le cache L1 du CPU.
Impact utilisateur: Race condition potentielle: le player ExoPlayer est libere sur un thread
  tandis qu'un autre tente d'y acceder via `player?.seekTo()`.
Fix: Ajouter @Volatile sur toutes les variables lues cross-thread, ou utiliser
  AtomicReference/AtomicBoolean.
Effort: 2h
Sprint: 1
```

#### P1 — IMPORTANTS

```
[AGENT-1-004] MpvPlayerWrapper — pendingUrl/pendingPosition sans @Volatile
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:71-72
Probleme: `pendingUrl` et `pendingPosition` sont ecrits sur Main et lus dans les callbacks MPV (thread natif).
Fix: @Volatile ou AtomicReference.
Effort: 30min | Sprint: 1
```

```
[AGENT-1-005] 8 ViewModels utilisent Channel<NavigationEvent>() avec capacite RENDEZVOUS
Severite: P1
Fichier: Multiple ViewModels (Home, Hub, Library, MediaDetail, Season, Search, Settings, Favorites)
Probleme: Channel() sans capacite = RENDEZVOUS (0 buffer). Si le collecteur (UI) n'est pas pret,
  le send() suspend indefiniment. En pratique: un event de navigation emis pendant une recomposition
  Compose peut etre perdu.
Fix: Channel<NavigationEvent>(Channel.BUFFERED) ou Channel(capacity = 1, onBufferOverflow = DROP_OLDEST)
Effort: 2h | Sprint: 1
```

```
[AGENT-1-006] SyncRepository.onProgressUpdate — callback mutable sur @Singleton
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt:12
Probleme: Callback var ecrit depuis le Worker et lu depuis l'UI sans synchronisation.
Fix: Remplacer par SharedFlow ou StateFlow.
Effort: 2h | Sprint: 2
```

```
[AGENT-1-007] LibrarySyncWorker retourne Result.success() sur auth timeout
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/sync/LibrarySyncWorker.kt:83-88
Probleme: Si le token a expire, le worker retourne "success" (pas de retry).
  La sync s'arrete silencieusement.
Fix: Retourner Result.retry() avec backoff exponentiel.
Effort: 1h | Sprint: 1
```

```
[AGENT-1-008] ChannelSyncWorker swallows toutes les exceptions
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/sync/ChannelSyncWorker.kt:42-46
Probleme: catch(e: Exception) { Result.success() } — les erreurs sont avalees silencieusement.
Fix: Distinguer les erreurs recoverables (Result.retry) des fatales (Result.failure).
Effort: 1h | Sprint: 1
```

```
[AGENT-1-009] PlayerController.release() — race avec fire-and-forget
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:168-198
Probleme: `sessionJob.cancel()` execute apres des appels fire-and-forget (scrobble, progress save).
  Ces appels peuvent ne pas terminer avant l'annulation.
Fix: Utiliser `withTimeoutOrNull(3000) { save() }` avant cancel.
Effort: 2h | Sprint: 1
```

```
[AGENT-1-010] PlayerController.switchToMpv() — ExoPlayer.release() sans thread-safety
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:507-514
Probleme: ExoPlayer.release() doit etre appele sur le thread principal. `switchToMpv()` ne le garantit pas.
Fix: Wrapper avec `withContext(mainDispatcher) { player?.release() }`.
Effort: 30min | Sprint: 1
```

#### P2 — MINEURS

```
[AGENT-1-011] ChapterMarkerManager non remis a zero entre sessions player
Severite: P2
Probleme: Les marqueurs d'un film precedent peuvent persister brievement.
Effort: 30min | Sprint: 2
```

```
[AGENT-1-012] PlayerControlViewModel — side effects dans init{}
Severite: P2
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerControlViewModel.kt:26-44
Probleme: 5 collecteurs lances dans init{} — effets de bord avant que l'UI ne soit prete.
Effort: 2h | Sprint: 2
```

```
[AGENT-1-013] MpvPlayerWrapper.release() — MPVLib.destroy() sans guard sur operations en vol
Severite: P2
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:238-243
Probleme: destroy() peut etre appele pendant qu'un callback JNI est en cours.
Effort: 2h | Sprint: 2
```

---

### AGENT 2 — FUITES MEMOIRE & RESSOURCES (11 findings)

#### P0 — CRITIQUES

```
[AGENT-2-001] MPVLib observers jamais retires dans release()
Severite: P0
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:112-113, 238-243
Code:
  // initialize():
  MPVLib.addObserver(this)
  MPVLib.addLogObserver(this)
  // release():
  MPVLib.destroy()   // <- NO removeObserver() before destroy
Probleme: Les observers restent enregistres apres destroy(). Si MPVLib est reinitialise
  (switchToMpv -> switchToExo -> switchToMpv), les anciens observers recoivent des callbacks
  sur un objet dans un etat invalide.
Impact utilisateur: Crash JNI potentiel lors du re-switch vers MPV. Native crash sans stacktrace Java.
Fix: Ajouter MPVLib.removeObserver(this) et MPVLib.removeLogObserver(this) avant destroy().
Effort: 30min
Sprint: 1
```

```
[AGENT-2-002] CoroutineScope cree par acces (doublon AGENT-1-001)
Severite: P0
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:59-60
Note: Meme finding que AGENT-1-001. Confirme par un second agent sous l'angle memoire.
```

#### P1 — IMPORTANTS

```
[AGENT-2-003] Lifecycle observer jamais retire dans MpvPlayerWrapper
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/mpv/MpvPlayerWrapper.kt:230-232
Probleme: lifecycleOwner.lifecycle.addObserver(this) sans removeObserver dans release().
Fix: Ajouter removeObserver dans release().
Effort: 15min | Sprint: 1
```

```
[AGENT-2-004] Nouvel OkHttpClient cree par instance ExoPlayer
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerFactory.kt:50-53
Code:
  val okHttpClient = OkHttpClient.Builder()
      .socketFactory(CrlfFixSocketFactory())
      .addInterceptor(RangeRetryInterceptor())
      .build()
Probleme: Chaque loadMedia() cree un nouveau OkHttpClient avec son propre connection pool.
  Les connexions TCP ne sont pas reutilisees entre lectures.
Fix: Injecter un OkHttpClient.Builder partage configure avec les interceptors necessaires.
Effort: 2h | Sprint: 1
```

```
[AGENT-2-005] PlaybackManager.clear() jamais appele
Severite: P1
Fichier: domain/src/main/java/com/chakir/plexhubtv/domain/service/PlaybackManager.kt:83-87
Probleme: La methode clear() existe mais n'est appelee par aucun callsite. L'ancien media/queue persiste.
Fix: Appeler clear() dans PlayerController.release() ou SessionEnd.
Effort: 30min | Sprint: 1
```

```
[AGENT-2-006] PlexSourceHandler.similarCache non borne
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/source/PlexSourceHandler.kt:28
Probleme: ConcurrentHashMap croit indefiniment. Entrees expirees restent en memoire.
Fix: LRU cache avec max 30 entrees.
Effort: 2h | Sprint: 1
```

#### P2 — MINEURS

```
[AGENT-2-007] EnrichMediaItemUseCase cache non borne | P2
Fichier: domain/usecase/EnrichMediaItemUseCase.kt:46
Effort: 1h | Sprint: 2

[AGENT-2-008] ExoPlayer listener non explicitement retire | P2
Fichier: PlayerController.kt:409-504
Effort: 30min | Sprint: 2

[AGENT-2-009] PlayerController @Singleton design concern | P2
Fichier: PlayerController.kt:35
Effort: 8h | Sprint: 3

[AGENT-2-010] DisposableEffect vide dans VideoPlayerScreen | P2
Fichier: feature/player/VideoPlayerScreen.kt:83-87
Effort: 15min | Sprint: 1

[AGENT-2-011] failedServers Map non borne dans ConnectionManager | P2
Fichier: core/network/ConnectionManager.kt:49
Effort: 30min | Sprint: 2
```

---

### AGENT 3 — SECURITE (15 findings)

#### P0 — CRITIQUES

```
[AGENT-3-001] EncryptedSharedPreferences fallback silencieux vers plaintext
Severite: P0
Fichier: core/common/src/main/java/com/chakir/plexhubtv/core/common/SecurePreferencesManager.kt
Probleme: Si le Android Keystore est corrompu (reset usine, restauration backup),
  EncryptedSharedPreferences crash. Le catch block fait un fallback vers SharedPreferences
  normales en clair. Les tokens deja stockes chiffres deviennent illisibles et sont perdus,
  tandis que les nouveaux tokens sont stockes en clair.
Impact utilisateur: Apres une restauration, les tokens sont en plaintext sur le stockage.
Fix: Detecter la corruption, effacer et re-demander l'authentification.
Effort: 4h | Sprint: 1
```

```
[AGENT-3-002] android:allowBackup="true" expose les donnees via ADB
Severite: P0
Fichier: app/src/main/AndroidManifest.xml
Probleme: `adb backup` extrait SharedPreferences (tokens), Room DB (tokens, historique, favoris),
  et toute donnee interne. Sur API 31+ sans dataExtractionRules, le backup Google Drive
  inclut aussi ces donnees.
Fix: android:allowBackup="false" ou definir android:dataExtractionRules excluant les
  fichiers sensibles.
Effort: 1h | Sprint: 1
```

```
[AGENT-3-003] Server accessTokens stockes sans encryption dans Room
Severite: P0
Fichier: core/database/src/main/java/com/chakir/plexhubtv/core/database/ServerEntity.kt
        core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt
Probleme: La colonne `accessToken` de `ServerEntity` contient les tokens Plex en clair
  dans la DB SQLite. Combinable avec allowBackup pour extraction complete.
Fix: Chiffrer les tokens avec EncryptedSharedPreferences ou SQLCipher.
Effort: 8h | Sprint: 1
```

#### P1 — IMPORTANTS

```
[AGENT-3-004] Tokens Plex embarques dans les URLs stockees en Room
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/HubsRepositoryImpl.kt
Probleme: Les URLs avec ?X-Plex-Token=xxx sont persistees dans les champs thumbUrl/artUrl.
Effort: 6h | Sprint: 2
```

```
[AGENT-3-005] Credentials Xtream exposes dans URLs et logs
Severite: P1
Fichier: core/network/src/main/java/com/chakir/plexhubtv/core/network/xtream/XtreamApiClient.kt:42-43
Probleme: username + password en clair dans les query params des URLs.
Effort: 4h | Sprint: 2
```

```
[AGENT-3-006] Xtream API envoie credentials en HTTP cleartext
Severite: P1
Fichier: core/network/src/main/java/com/chakir/plexhubtv/core/network/xtream/XtreamApiService.kt:9-12
Probleme: Les serveurs Xtream sont souvent HTTP (pas HTTPS). Credentials en clair sur le reseau.
Fix: Avertir l'utilisateur si URL non-HTTPS, ou forcer HTTPS.
Effort: 3h | Sprint: 2
```

```
[AGENT-3-007] DeepLink plexhub://play/{ratingKey} sans validation auth
Severite: P1
Fichier: app/src/main/AndroidManifest.xml:49-54, MainActivity.kt:365-368
Probleme: N'importe quelle app peut lancer la lecture d'un media via deep link sans verifier
  que l'utilisateur est authentifie.
Fix: Verifier l'etat d'authentification avant de traiter le deep link.
Effort: 2h | Sprint: 1
```

#### P2 — MINEURS

```
[AGENT-3-008] HTTP cleartext autorise globalement | P2
Fichier: network_security_config.xml:16
Effort: 1h | Sprint: 2

[AGENT-3-009] OCSP bypass sur tous les domaines | P2
Fichier: di/NetworkModule.kt:66-91
Effort: 4h | Sprint: 3

[AGENT-3-010] Tokens loggues dans les URLs | P2
Fichier: LibrarySyncWorker.kt:145, MpvPlayerWrapper.kt:165
Effort: 2h | Sprint: 1

[AGENT-3-011] Room DB non chiffree (pas de SQLCipher) | P2
Fichier: DatabaseModule.kt:351-356
Effort: 8h | Sprint: 3

[AGENT-3-012] PRAGMA synchronous = NORMAL (risque perte donnees) | P2
Fichier: DatabaseModule.kt:365
Effort: 15min | Sprint: 1

[AGENT-3-013] BuildConfig embarque secrets debug dans le binaire | P2
Fichier: app/build.gradle.kts:47-60
Effort: 2h | Sprint: 2

[AGENT-3-014] Debug API_BASE_URL hardcode vers IP privee | P2
Fichier: app/build.gradle.kts:47
Effort: 30min | Sprint: 1

[AGENT-3-015] Race condition theorique dans AuthInterceptor au startup | P2
Fichier: core/network/AuthInterceptor.kt:34-41
Effort: 2h | Sprint: 3
```

---

### AGENT 4 — DATABASE & COHERENCE DONNEES (15 findings)

#### P0 — CRITIQUES

```
[AGENT-4-001] fallbackToDestructiveMigration() en production
Severite: P0
Fichier: core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt:391
Code:
  .fallbackToDestructiveMigration()
Probleme: Si aucune migration n'est definie pour la version cible, Room EFFACE toute la DB
  et la recree vide. L'utilisateur perd: historique, favoris, profils, progression offline.
  Avec les gaps de migration manquantes (AGENT-4-002), c'est quasi certain sur certains upgrades.
Impact utilisateur: Mise a jour de l'app -> perte de toutes les donnees silencieusement.
Fix: Retirer fallbackToDestructiveMigration(). Ajouter des migrations explicites pour chaque version.
Effort: 8h
Sprint: 1
```

```
[AGENT-4-002] Migrations manquantes — gaps v12->v15 et v16->v18
Severite: P0
Fichier: core/database/src/main/java/com/chakir/plexhubtv/core/database/DatabaseModule.kt:370-389
Probleme: Les migrations v13, v14, v17 ne sont pas definies. Un utilisateur sur v12
  qui upgrade vers v15 n'a pas de chemin de migration -> fallbackToDestructiveMigration() s'active.
Fix: Ecrire les migrations manquantes ou au minimum des NO-OP migrations.
Effort: 6h
Sprint: 1
```

```
[AGENT-4-003] getHistory SELECT * avec collision alias MAX(lastViewedAt)
Severite: P0
Fichier: core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt:152-161
Code:
  @Query("SELECT *, MAX(lastViewedAt) as lastViewedAt FROM media
          WHERE lastViewedAt > 0 GROUP BY historyGroupKey
          ORDER BY lastViewedAt DESC LIMIT :limit OFFSET :offset")
Probleme: `SELECT *` inclut deja `lastViewedAt` comme colonne. L'alias `MAX(lastViewedAt) as lastViewedAt`
  entre en collision. Room utilise le PREMIER match dans le curseur = la colonne brute, pas le MAX.
  Le tri GROUP BY utilise la valeur correcte (SQL engine), mais la valeur mappee dans l'entite est
  celle d'une ligne arbitraire du groupe.
Impact utilisateur: L'ecran historique peut afficher des timestamps incorrects (date de derniere
  vue d'un episode aleatoire du groupe au lieu du plus recent).
Fix: Utiliser un SELECT explicite au lieu de SELECT *.
Effort: 2h
Sprint: 1
```

#### P1 — IMPORTANTS

```
[AGENT-4-004] Profile switch sans @Transaction — race 0 ou 2+ profils actifs
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/ProfileRepositoryImpl.kt:97-114
Probleme: deactivateAll() + activate(profileId) sans @Transaction.
  Un crash entre les deux laisse 0 profil actif ou 2+ actifs.
Fix: Wrapper dans @Transaction.
Effort: 1h | Sprint: 1
```

```
[AGENT-4-005] SyncRepositoryImpl upsert sans @Transaction
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/SyncRepositoryImpl.kt:146-183
Probleme: Insert + update de scrapedRatings hors transaction. Crash entre les deux = ratings partiels.
Effort: 2h | Sprint: 1
```

```
[AGENT-4-006] OnDeckRepositoryImpl clear + insert hors transaction
Severite: P1
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/OnDeckRepositoryImpl.kt:150-170
Probleme: deleteAll() + insertAll() sans @Transaction. Crash = On Deck vide.
Effort: 1h | Sprint: 1
```

#### P2 — MINEURS

```
[AGENT-4-007] HubsRepositoryImpl insert sans clear prealable | P2
Fichier: HubsRepositoryImpl.kt:306-319
Effort: 1h | Sprint: 2

[AGENT-4-008] searchMedia LIKE '%query%' full table scan | P2
Fichier: MediaDao.kt:76-81
Effort: 4h (FTS) | Sprint: 3

[AGENT-4-009] genres LIKE ? O(n) meme avec index | P2
Fichier: MediaLibraryQueryBuilder.kt:119-127
Effort: 6h (table de jointure) | Sprint: 3

[AGENT-4-010] LIKE chars non echappes dans title search | P2
Fichier: MediaLibraryQueryBuilder.kt:140-143
Effort: 1h | Sprint: 1

[AGENT-4-011] deleteOldCollections sans cascade sur cross_ref | P2
Fichier: CollectionDao.kt:66-67
Effort: 1h | Sprint: 2

[AGENT-4-012] Converters.toMediaPartList crash sur JSON invalide | P2
Fichier: Converters.kt:23-26
Effort: 30min | Sprint: 1

[AGENT-4-013] synchronous = NORMAL risque perte donnees | P2
Fichier: DatabaseModule.kt:365
Effort: 15min | Sprint: 1

[AGENT-4-014] SELECT * charge mediaParts inutilement dans getHistory | P2
Fichier: MediaDao.kt:152-161
Effort: 2h | Sprint: 2

[AGENT-4-015] OfflineWatchProgressEntity indices manquants | P2
Fichier: OfflineWatchProgressEntity.kt:11-26
Effort: 30min | Sprint: 1
```

---

### AGENT 5 — UX & NAVIGATION TV (17 findings)

#### P1 — IMPORTANTS

```
[AGENT-5-001] Strings hardcodees en francais dans LibrarySelectionScreen
Severite: P1
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/library/LibrarySelectionScreen.kt:95,101,119,148,214-218,296-297
Probleme: "Chargement des serveurs...", "Selectionner les bibliotheques", "Aucune bibliotheque trouvee"
  hardcodes en francais. Non localisable.
Fix: Extraire dans strings.xml.
Effort: 2h | Sprint: 1
```

```
[AGENT-5-002] Strings hardcodees en anglais sur 12+ ecrans
Severite: P1
Fichier: Multiple (Settings, IPTV, Debug, Search, Profile, Player)
Probleme: "No results found", "Loading...", "Error", etc. hardcodes en anglais.
Fix: Extraire dans strings.xml avec traductions.
Effort: 4h | Sprint: 1
```

```
[AGENT-5-003] BackHandler manquant sur 12+ ecrans secondaires
Severite: P1
Fichier: Multiple (Settings, IPTV, Downloads, Debug, Profile, Collection, Favorites)
Probleme: Presser BACK sur la telecommande peut ne pas naviguer correctement sur Android TV.
Fix: Ajouter BackHandler sur chaque ecran secondaire.
Effort: 3h | Sprint: 1
```

```
[AGENT-5-004] NetflixDetailScreen focus restoration fire-once
Severite: P1
Fichier: feature/details/NetflixDetailScreen.kt:92
Probleme: LaunchedEffect(Unit) ne re-request pas le focus apres navigation retour.
Fix: Utiliser LaunchedEffect(key) avec un indicateur de retour.
Effort: 2h | Sprint: 2
```

```
[AGENT-5-005] CollectionDetailScreen focus request avant que la grid soit prete
Severite: P1
Fichier: feature/collection/CollectionDetailScreen.kt:116
Probleme: FocusRequester.requestFocus() est appele avant que les items du LazyGrid soient composes.
Fix: Delayer le requestFocus ou utiliser un callback onFirstItemVisible.
Effort: 2h | Sprint: 2
```

```
[AGENT-5-006] FavoritesScreen DropdownMenu non D-pad friendly
Severite: P1
Fichier: feature/favorites/FavoritesScreen.kt:193
Probleme: DropdownMenu standard Material3 n'est pas optimise pour la navigation D-pad.
Fix: Utiliser les composants TV Material (TvDropdownMenu ou custom D-pad menu).
Effort: 4h | Sprint: 2
```

#### P2 — MINEURS

```
[AGENT-5-007] Double TopBar sur Downloads/IPTV/Debug | P2
Effort: 1h | Sprint: 1

[AGENT-5-008] SplashScreen passe ViewModel a un enfant composable | P2
Probleme: Anti-pattern Compose — passer le ViewModel au lieu de state + lambdas.
Effort: 1h | Sprint: 2

[AGENT-5-009] FallbackAsyncImage set state depuis callback Coil (thread ?) | P2
Fichier: core/ui/FallbackAsyncImage.kt
Effort: 1h | Sprint: 2

[AGENT-5-010] HistoryScreen count-based items() avec key instable | P2
Effort: 30min | Sprint: 1

[AGENT-5-011] PlexHomeSwitcher focus manquant apres PIN dialog | P2
Effort: 1h | Sprint: 2

[AGENT-5-012] Hub/Settings ecrans sans focus initial fiable | P2
Effort: 2h | Sprint: 2

[AGENT-5-013] LoadingScreen LaunchedEffect(Unit) ne re-fire pas apres erreur | P2
Effort: 1h | Sprint: 1

[AGENT-5-014] AppProfileSwitchScreen LazyColumn sans focus initial | P2
Effort: 30min | Sprint: 1

[AGENT-5-015] ContentDescription mixte francais/anglais | P2
Effort: 2h | Sprint: 2

[AGENT-5-016] NetflixOnScreenKeyboard "SEARCH" hardcode en anglais | P2
Effort: 15min | Sprint: 1

[AGENT-5-017] XtreamSetupScreen sans focus initial | P2
Effort: 30min | Sprint: 1
```

---

### AGENT 6 — REGRESSIONS & FEATURES CASSEES (11 findings)

**Resume des verdicts:**

| Feature | Verdict |
|---------|---------|
| Skip Intro/Credits | FONCTIONNE |
| Auto-next episode | PARTIELLEMENT CASSE |
| Continue Watching | FONCTIONNE |
| Multi-serveur unifie | FONCTIONNE |
| IPTV Xtream | PARTIELLEMENT CASSE |
| Profils | PARTIELLEMENT CASSE |
| Favoris | FONCTIONNE |
| Mode Enfant | CASSE |
| Telechargements | CASSE |
| Android TV Channels | FONCTIONNE |

#### P0 — CRITIQUES

```
[AGENT-6-001] Mode Enfant : ZERO filtrage de contenu implemente
Severite: P0
Fichier: feature/home/HomeViewModel.kt, feature/search/SearchViewModel.kt,
         feature/hub/HubViewModel.kt, feature/library/LibraryViewModel.kt,
         feature/iptv/IptvViewModel.kt (ensemble de ces fichiers)
Code:
  // ProfileEntity.kt:19
  val isKidsProfile: Boolean,
  // Aucun ViewModel ne reference isKidsProfile, kids, contentRating, ageRating
Probleme: Le toggle `isKidsProfile` est stocke en base et affiche dans l'UI, mais AUCUN
  ViewModel ni Repository ne filtre le contenu. Un enfant sur un profil "kids" voit
  exactement le meme contenu qu'un adulte.
Impact utilisateur: Un parent cree un profil enfant en pensant que le contenu adulte est masque.
  L'enfant accede a TOUT le catalogue. Risque legal (COPPA/RGPD enfants) et reputationnel.
Fix: Injecter ProfileRepository dans chaque ViewModel. Filtrer par contentRating quand isKidsProfile.
Effort: 8h
Sprint: 1
```

```
[AGENT-6-002] Telechargements : feature entierement stub/non implementee
Severite: P0
Fichier: data/src/main/java/com/chakir/plexhubtv/data/repository/DownloadsRepositoryImpl.kt:20-23
Code:
  override fun getAllDownloads(): Flow<List<MediaItem>> = flow { emit(emptyList()) }
  override suspend fun startDownload(media: MediaItem): Result<Unit> = Result.success(Unit)
  override suspend fun cancelDownload(mediaId: String): Result<Unit> = Result.success(Unit)
  override suspend fun deleteDownload(mediaId: String): Result<Unit> = Result.success(Unit)
Probleme: Toutes les methodes sont des stubs. startDownload() retourne "succes" sans rien faire.
Impact utilisateur: L'utilisateur clique "Telecharger", l'UI dit "succes", rien n'est telecharge.
  En mode avion, aucun contenu disponible malgre les "telechargements reussis".
Fix: Implementer (WorkManager + stockage local, 16h) OU retirer l'UI de telechargement (2h).
Effort: 2h (retrait UI) ou 16h (implementation)
Sprint: 1 (retrait) ou 2 (implementation)
```

```
[AGENT-6-003] Auto-next episode : pas de countdown automatique
Severite: P0
Fichier: app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt:454-550
Probleme: Le popup "Next Episode" apparait a 90% mais n'a PAS de countdown timer.
  L'utilisateur doit cliquer manuellement "Play Now". Pas de binge-watching automatique.
  Sur Netflix/Plex, l'episode suivant se lance apres 15-30s.
Impact utilisateur: Sur Android TV, si l'utilisateur ne presse pas un bouton, la lecture s'arrete.
Fix: Ajouter LaunchedEffect avec countdown 15s qui appelle onPlayNow() a 0.
Effort: 2h
Sprint: 1
```

#### P1 — IMPORTANTS

```
[AGENT-6-004] Profils : le switch ne recharge PAS les donnees
Severite: P1
Fichier: feature/appprofile/AppProfileViewModel.kt:104-121
Probleme: switchProfile() met a jour isActive en Room puis navigue vers Home.
  Aucun Repository/ViewModel n'est notifie. Les caches StateFlow gardent les donnees
  du profil precedent. Historique, favoris, hubs identiques entre profils.
Fix: Event bus ProfileChangedEvent ou restart Activity.
Effort: 6h | Sprint: 1
```

```
[AGENT-6-005] IPTV : pas d'EPG (Guide des Programmes)
Severite: P1
Fichier: feature/iptv/ et data/repository/IptvRepositoryImpl.kt
Probleme: Seul le M3U est parse. Aucune implementation XMLTV/EPG.
Effort: 16h | Sprint: 2
```

```
[AGENT-6-006] IPTV Xtream : token expire non gere a l'execution
Severite: P1
Fichier: core/network/xtream/XtreamApiClient.kt
Probleme: Si le compte expire pendant l'utilisation, erreurs cryptiques au lieu d'un message clair.
Effort: 4h | Sprint: 2
```

#### P2 — MINEURS

```
[AGENT-6-007] IPTV playlist en memoire uniquement — perdue au restart | P2
Fichier: data/repository/IptvRepositoryImpl.kt:31
Effort: 4h | Sprint: 2

[AGENT-6-008] PlaybackManager.toggleShuffle non implemente (stub) | P2
Fichier: domain/service/PlaybackManager.kt:58-63
Effort: 1h | Sprint: 2

[AGENT-6-009] Favoris fragiles face aux changements ratingKey multi-serveur | P2
Fichier: core/database/FavoriteEntity.kt:9
Effort: 4h | Sprint: 3

[AGENT-6-010] ExoPlayer resume toast sans guard hasShownResumeToast | P2
Fichier: PlayerController.kt:1012-1014
Probleme: Le toast "Reprise a X:XX" reapparait a chaque changement de qualite.
  Le chemin MPV a correctement le guard.
Fix: Ajouter `&& !hasShownResumeToast` + `hasShownResumeToast = true`
Effort: 10min | Sprint: 1

[AGENT-6-011] PlayerPositionTracker est du dead code duplique | P2
Fichier: feature/player/controller/PlayerPositionTracker.kt
Probleme: Classe entiere jamais utilisee, duplique PlayerController.startPositionTracking().
Effort: 30min | Sprint: 1
```

---

### AGENT 7 — DETTE TECHNIQUE & SCALABILITE (20 findings)

#### P1 — IMPORTANTS

```
[AGENT-7-001] PlayerController — God Object (1151 lignes, 18 deps injectees)
Severite: P1
Fichier: feature/player/controller/PlayerController.kt:37-56
Probleme: 18 dependances, melange routing codec, gestion reseau, seek, tracking, error handling.
Fix: Extraire PlayerCodecRouter, PlayerResumeManager, PlayerNetworkRetry.
Effort: 16h | Sprint: 2
```

```
[AGENT-7-002] Pattern URL resolution repete 12+ fois sans abstraction
Severite: P1
Fichier: HubsRepositoryImpl, LibraryRepositoryImpl, MediaDetailRepositoryImpl, PlexSourceHandler
Probleme: 5 lignes copiees-collees 12+ fois. Toute correction doit etre appliquee 12 fois.
Fix: Extraire MediaMapper.resolveAndEnrich() ou MediaItemResolver.
Effort: 4h | Sprint: 1
```

```
[AGENT-7-003] PlexSourceHandler.similarCache non borne — fuite memoire
Severite: P1
Fichier: data/source/PlexSourceHandler.kt:28-29
Probleme: ConcurrentHashMap croit indefiniment. Entrees expirees (TTL 10min) restent en memoire.
Fix: LRU cache (max 30 entrees) avec eviction.
Effort: 2h | Sprint: 1
```

```
[AGENT-7-007] ProGuard trop permissif — `-keep class ... { *; }` sur 10+ librairies
Severite: P1
Fichier: app/proguard-rules.pro:44-104
Probleme: APK 20-30% plus gros que necessaire. Les consumer rules des librairies sont ignorees.
Fix: Restreindre les -keep aux classes necessaires (@Entity, @Dao, @Database).
Effort: 6h | Sprint: 2
```

```
[AGENT-7-010] EnrichMediaItemUseCase — 7 deps injectees dans un UseCase
Severite: P1
Fichier: domain/usecase/EnrichMediaItemUseCase.kt:33-44
Probleme: Violation SRP. Impossible a tester unitairement sans 7 mocks.
Fix: Extraire ServerNameMapBuilder + Strategy pattern (Episode vs Movie enrichment).
Effort: 10h | Sprint: 2
```

```
[AGENT-7-011] PlayerController importe data.source.MediaSourceResolver — violation couche
Severite: P1
Fichier: feature/player/controller/PlayerController.kt:51
Probleme: Module :app importe directement :data, contournant :domain.
Fix: Interface dans :domain, implementation dans :data.
Effort: 3h | Sprint: 2
```

```
[AGENT-7-013] Zero tests d'instrumentation et zero tests DAO Room
Severite: P1
Fichier: N/A (absence)
Probleme: 0 fichiers dans src/androidTest/. Les requetes SQL dynamiques complexes
  (MediaLibraryQueryBuilder avec GROUP_CONCAT, COALESCE, INSTR, SUBSTR, CHAR(31))
  ne sont testees que par inference.
Fix: Creer MediaDaoTest, MediaLibraryQueryBuilderInstrumentedTest, PlexDatabaseMigrationTest.
Effort: 24h | Sprint: 2
```

```
[AGENT-7-014] HttpLoggingInterceptor.Level.BODY loggue tokens/credentials
Severite: P1
Fichier: core/network/NetworkModule.kt:104-105
Probleme: Level.BODY en debug loggue les corps entiers: tokens Plex, credentials IPTV, accessTokens.
  `adb logcat | grep OkHttp` expose tout.
Fix: Level.HEADERS + RedactingInterceptor pour X-Plex-Token, password, username.
Effort: 3h | Sprint: 1
```

```
[AGENT-7-017] Pas de cache HTTP OkHttp configure
Severite: P1
Fichier: core/network/NetworkModule.kt:245-259
Probleme: Chaque requete API va toujours au reseau. Pas de cache HTTP pour les metadata deja consultees.
Fix: .cache(Cache(cacheDir/http_cache, 50MB)).
Effort: 2h | Sprint: 1
```

#### P2 — MINEURS

```
[AGENT-7-004] Serialisation duale Gson + KotlinX — poids mort 500KB+ | P2
Fichier: app/build.gradle.kts:172-176
Effort: 12h | Sprint: 3

[AGENT-7-005] 6 dependances hardcodees hors version catalog | P2
Fichier: app/build.gradle.kts
Effort: 1h | Sprint: 1

[AGENT-7-006] material-icons-extended ajoute ~4MB pour quelques icones | P2
Fichier: app/build.gradle.kts:219
Effort: 3h | Sprint: 2

[AGENT-7-008] Deps mortes dans version catalog | P2
Fichier: gradle/libs.versions.toml
Effort: 30min | Sprint: 1

[AGENT-7-009] async inutile dans aggregateHubs (CPU-bound) | P2
Fichier: HubsRepositoryImpl.kt:182-197
Effort: 30min | Sprint: 1

[AGENT-7-012] Strings hardcodees en francais dans PlayerController | P2
Fichier: PlayerController.kt:528, LibrarySelectionViewModel.kt:144,229
Effort: 2h | Sprint: 1

[AGENT-7-015] Robolectric 4.11.1 obsolete (actuelle 4.14+) | P2
Fichier: app/build.gradle.kts:226
Effort: 1h | Sprint: 1

[AGENT-7-016] Duplication sort/filter normalization avec divergence | P2
Fichier: LibraryRepositoryImpl.kt:140-150, 318-327, 382-389
Probleme: La 3eme copie hardcode les directions au lieu d'utiliser le parametre isDescending.
  Bug: jump-to-letter utilise le mauvais tri.
Effort: 1h | Sprint: 1

[AGENT-7-018] checkConnectionStatus async sans structured concurrency | P2
Fichier: ConnectionManager.kt:225-246
Effort: 30min | Sprint: 1

[AGENT-7-019] Duplicate foundation dans version catalog | P2
Fichier: libs.versions.toml + app/build.gradle.kts
Effort: 15min | Sprint: 1

[AGENT-7-020] metadataScore resout mediaSource dans boucle seree | P2
Fichier: MediaDetailRepositoryImpl.kt:152
Effort: 30min | Sprint: 2
```

---

## PLAN DE SPRINT

### SPRINT 1 — BLOQUEURS & QUICK WINS (~80h)

**P0 Critiques (doivent etre resolus avant toute release):**

| # | Finding | Effort | Description |
|---|---------|--------|-------------|
| 1 | AGENT-4-001 + 4-002 | 14h | Retirer fallbackToDestructiveMigration + ecrire migrations manquantes |
| 2 | AGENT-1-001 | 1h | Fix scope getter -> initialisation unique |
| 3 | AGENT-1-002 | 4h | Unifier PlaybackManager en un seul StateFlow |
| 4 | AGENT-1-003 | 2h | @Volatile sur toutes les vars cross-thread |
| 5 | AGENT-2-001 | 30min | Ajouter removeObserver avant MPVLib.destroy() |
| 6 | AGENT-3-002 | 1h | allowBackup="false" |
| 7 | AGENT-3-003 | 8h | Chiffrer tokens en Room |
| 8 | AGENT-3-001 | 4h | Fix EncryptedSharedPreferences fallback |
| 9 | AGENT-6-001 | 8h | Implementer filtrage contenu Kids Mode (ou retirer toggle) |
| 10 | AGENT-6-002 | 2h | Retirer UI Downloads (ou disclaimer "coming soon") |
| 11 | AGENT-6-003 | 2h | Ajouter countdown 15s sur Auto-next popup |
| 12 | AGENT-4-003 | 2h | Fix getHistory SELECT explicite |

**P1 Quick Wins Sprint 1:**

| # | Finding | Effort | Description |
|---|---------|--------|-------------|
| 13 | AGENT-1-004 | 30min | @Volatile pendingUrl/pendingPosition |
| 14 | AGENT-1-005 | 2h | Channel(BUFFERED) sur 8 ViewModels |
| 15 | AGENT-1-007+008 | 2h | Fix Workers retry vs failure |
| 16 | AGENT-1-009 | 2h | Save progress avant cancel |
| 17 | AGENT-1-010 | 30min | withContext(mainDispatcher) ExoPlayer.release() |
| 18 | AGENT-2-003 | 15min | removeObserver MpvPlayerWrapper |
| 19 | AGENT-2-004 | 2h | OkHttpClient partage ExoPlayer |
| 20 | AGENT-2-005 | 30min | Appeler PlaybackManager.clear() |
| 21 | AGENT-3-007 | 2h | Validation auth deep link |
| 22 | AGENT-4-004+005+006 | 4h | @Transaction profile/upsert/ondeck |
| 23 | AGENT-5-001+002 | 6h | Extraire strings FR/EN |
| 24 | AGENT-5-003 | 3h | BackHandler 12 ecrans |
| 25 | AGENT-6-004 | 6h | Profile switch reload data |
| 26 | AGENT-7-002 | 4h | Extraire URL resolution pattern |
| 27 | AGENT-7-003 | 2h | Borner similarCache LRU |
| 28 | AGENT-7-014 | 3h | HTTP Logger HEADERS + redacting |
| 29 | AGENT-7-017 | 2h | Cache HTTP OkHttp |

**P2 Quick Wins Sprint 1 (24 items, ~14h total)**

**Sprint 1 Total: ~80h** (12 P0 + 17 P1 + 24 P2 = 53 fixes)

---

### SPRINT 2 — DETTE STRUCTURELLE (~100h)

| # | Finding | Effort | Description |
|---|---------|--------|-------------|
| 1 | AGENT-7-001 | 16h | Refactor PlayerController (3 sous-composants) |
| 2 | AGENT-7-010 | 10h | Refactor EnrichMediaItemUseCase (Strategy) |
| 3 | AGENT-7-013 | 24h | Tests instrumentes (DAO, migrations, SQL) |
| 4 | AGENT-7-007 | 6h | Durcir ProGuard rules |
| 5 | AGENT-7-011 | 3h | Interface domain MediaSourceResolver |
| 6 | AGENT-6-005 | 16h | Implementer EPG/XMLTV IPTV |
| 7 | AGENT-6-006 | 4h | Gestion expiration Xtream |
| 8 | AGENT-6-007 | 4h | Persister playlist IPTV Room |
| 9 | AGENT-6-008 | 1h | Shuffle Fisher-Yates |
| 10 | AGENT-3-004 | 6h | Retirer tokens des URLs stockees |
| 11 | AGENT-3-005+006 | 7h | Securiser credentials Xtream |
| 12 | AGENT-5-004+005 | 4h | Focus restoration Detail/Collection |
| 13 | AGENT-5-006 | 4h | D-pad friendly menu Favoris |

---

### SPRINT 3 — POLISH & LONG TERME (~50h)

| # | Finding | Effort | Description |
|---|---------|--------|-------------|
| 1 | AGENT-7-004 | 12h | Unifier serialisation Gson -> kotlinx |
| 2 | AGENT-3-011 | 8h | SQLCipher Room DB |
| 3 | AGENT-4-008+009 | 10h | FTS + table genres |
| 4 | AGENT-2-009 | 8h | PlayerController scoped (non-singleton) |
| 5 | AGENT-6-009 | 4h | Favoris par GUID |
| 6 | AGENT-3-009 | 4h | OCSP proper handling |
| 7 | AGENT-3-015 | 2h | Race condition AuthInterceptor |
| 8 | AGENT-3-013 | 2h | BuildConfig secrets |

---

## STATISTIQUES FINALES

| Agent | P0 | P1 | P2 | Total |
|-------|----|----|-----|-------|
| 1 - Coroutines & Threading | 3 | 7 | 3 | 13 |
| 2 - Fuites Memoire & Ressources | 2 | 4 | 5 | 11 |
| 3 - Securite | 3 | 4 | 8 | 15 |
| 4 - Database & Coherence | 3 | 3 | 9 | 15 |
| 5 - UX & Navigation TV | 0 | 6 | 11 | 17 |
| 6 - Regressions & Features | 3 | 3 | 5 | 11 |
| 7 - Dette Technique | 0 | 8 | 12 | 20 |
| **TOTAL** | **14** | **35** | **53** | **102** |

**Doublons confirmes** (meme finding par 2 agents):
- AGENT-2-002 = AGENT-1-001 (scope per access)
- AGENT-2-006 ~ AGENT-7-003 (similarCache unbounded)
- **Findings uniques: ~100**

---

## NOTES DE L'AUDITEUR

### Ce qui fonctionne bien
- Le coeur de lecture (ExoPlayer + MPV dual-engine) est fonctionnel et performant
- L'architecture multi-serveur avec race strategy est elegante
- Le systeme d'enrichissement Room-first est bien pense
- Skip Intro, Continue Watching, et Android TV Channels fonctionnent correctement
- Le systeme de profils (CRUD) est complet

### Ce qui necessite une attention immediate
1. **Securite**: Les tokens en clair + allowBackup + HTTP logging = un audit de securite echoue
2. **Stabilite**: Le scope getter + vars non-volatile = crashes intermittents difficiles a reproduire
3. **Integrite donnees**: fallbackToDestructiveMigration = bombe a retardement
4. **UX**: Kids Mode factice + Downloads factice = features trompeuses
5. **Testabilite**: 0 tests instrumentes = regression inevitable

### Effort total estime
- Sprint 1 (bloqueurs): ~80h
- Sprint 2 (structural): ~100h
- Sprint 3 (polish): ~50h
- **Total: ~230h de travail technique**
