# PlexHubTV — Audit Exhaustif Production-Ready

> **Date**: 2026-03-08
> **Méthode**: 5 agents spécialisés en parallèle (Stabilité+Sécurité, Performance, Architecture, UX+Features, Release)
> **Score global**: **B-** (Fondations solides, corrections P0 requises avant vente)

---

## TABLEAU DE BORD P0 (Bloquants Vente)

| #  | Phase | Problème | Fichier | Impact |
|----|-------|----------|---------|--------|
| 1  | Sécurité | SSL TrustManager accepte TOUS les certificats sur IPs privées | `core/network/NetworkModule.kt:203` | MITM sur réseau local |
| 2  | Sécurité | `cleartextTrafficPermitted=true` global | `app/res/xml/network_security_config.xml:16` | Tokens Plex interceptables en HTTP |
| 3  | Release | API keys vides en release (`TMDB=""`, `OMDB=""`) | `app/build.gradle.kts:76-80` | Rating sync, IPTV cassés en prod |
| 4  | Release | Privacy policy non hébergée HTTPS | `docs/privacy-policy-*.md` | Google Play rejette la soumission |
| 5  | Perf | `PagingConfig.maxSize=2000` → OOM Fire TV Stick | `LibraryRepositoryImpl.kt:206` | Crash sur appareils 1GB RAM |
| 6  | Perf | Connection warmup bloque cold start 5s | `PlexHubApplication.kt:134` | UX dégradée au lancement |
| 7  | Perf | GROUP BY sans index matérialisé → full scan | `MediaLibraryQueryBuilder.kt:246` | 500-1000ms sur 10k+ items |
| 8  | Archi | ViewModel injecte directement `MediaDao` | `LibraryViewModel.kt:51` | Violation Clean Arch, intestable |
| 9  | UX | `LoadState.Error` invisible pour l'utilisateur | `LibrariesScreen.kt:424` | Écran blanc sans explication |
| 10 | UX | Pas de countdown auto-next épisode | `VideoPlayerScreen.kt:377` | Feature Netflix attendue manquante |

---

## PHASE 1 — STABILITÉ & CRASH-PROOFING

### P0 Critiques

| ID | Problème | Fichier:Ligne | Fix |
|----|----------|---------------|-----|
| STAB-1 | SSL TrustManager `return` pour toute IP privée | `NetworkModule.kt:203` | Certificate pinning par serveur (TOFU) |
| STAB-2 | HTTP cleartext global autorisé | `network_security_config.xml:16` | Restreindre aux IPs privées uniquement |
| STAB-3 | BuildConfig expose clés API debug | `build.gradle.kts:47-60` | Injection runtime via EncryptedSharedPreferences |

### P1 Importants

| ID | Problème | Fichier:Ligne | Fix |
|----|----------|---------------|-----|
| STAB-4 | PlayerController `@Singleton` retient contexte | `PlayerController.kt:37` | Passer en `@ViewModelScoped` |
| STAB-5 | Race condition ConnectionManager cache vs persist | `ConnectionManager.kt:56-59` | Persister AVANT update StateFlow |
| STAB-6 | `mediaParts.first()` sans vérification liste vide | `MediaDetailViewModel.kt` | Utiliser `firstOrNull()` + error handling |
| STAB-7 | LibrarySyncWorker retourne `Success` sans auth | `LibrarySyncWorker.kt:74-88` | Retourner `Result.retry()` si non authentifié |
| STAB-8 | EnrichMediaItemUseCase cache ConcurrentHashMap non borné | `EnrichMediaItemUseCase.kt:46` | LRU cache 100 entrées max |
| STAB-9 | SavedStateHandle manquant dans 16/24 ViewModels | Multiple | Ajouter SavedStateHandle pour survie process death |
| STAB-10 | AuthInterceptor 401 sans déduplication atomique | `AuthInterceptor.kt:74-76` | `AtomicBoolean.compareAndSet` |

---

## PHASE 2 — SÉCURITÉ

### P0 Critiques

| ID | Risque | Fichier | Fix |
|----|--------|---------|-----|
| SEC-1 | Tokens loggés en debug (partageable support) | Codebase-wide | `token.take(10) + "***"` |
| SEC-2 | EncryptedSharedPreferences fallback non chiffré | `SecurePreferencesManager.kt:52-56` | Throw SecurityException, pas de fallback |
| SEC-3 | Pas de cert pinning pour plex.tv | `NetworkModule.kt` | `CertificatePinner` OkHttp |

### P1 Importants

| ID | Risque | Fix |
|----|--------|-----|
| SEC-4 | ProGuard trop permissif (`-keep class **model.** { *; }`) | Garder seulement `@SerializedName` fields |
| SEC-5 | Room DB non chiffrée (historique, favoris exposés via ADB) | SQLCipher |
| SEC-6 | Pas de rate limiting tentatives login | Backoff exponentiel |
| SEC-7 | Pas de validation input réponses Plex API | Sanitize strings affichées |

---

## PHASE 3 — PERFORMANCE (Top 20 Bottlenecks)

### Startup (Cible: <2s cold start)

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 1 | **Connection warmup 5s bloque init** | +5s cold start | Fire-and-forget hors chemin critique |
| 2 | LibrarySyncWorker démarre immédiatement | +500-2000ms | `setInitialDelay(15, SECONDS)` |
| 3 | Room DB ouvre synchrone au 1er accès | +200-500ms | Pré-ouvrir dans parallel init |

### Mémoire

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 4 | **PagingConfig maxSize=2000 → 400MB** | OOM Fire TV Stick | Réduire à `maxSize=300` |
| 5 | Coil cache non proportionnelle densité écran | Cache thrashing | Scale par `displayMetrics.densityDpi` |
| 6 | Pas de prefetch images Home | 40 requêtes simultanées | Enqueue top 20 URLs après hub load |

### Compose Recomposition

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 7 | NetflixMediaCard `MutableInteractionSource` instable | 200+ recompositions/s en scroll | `remember(media.ratingKey)` |
| 8 | LibrariesScreen 7x `derivedStateOf` sur 22 champs | 7 recompositions par update | Split `LibraryUiState` en sub-states |
| 9 | LazyGrid `key = it.id` instable multi-serveur | Glitch visuel | Key composite `serverId:ratingKey:unificationId` |

### Database

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 10 | **GROUP BY COALESCE() sans index** | 500-1000ms sur 10k items | Colonne matérialisée `groupKey` + index |
| 11 | `searchMedia` LIKE '%query%' au lieu de FTS4 | 200-500ms recherche | Utiliser `searchMediaFts()` par défaut |

### Réseau

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 12 | Pas de cache HTTP OkHttp | Chaque refresh = réseau | Cache 50MB + intercepteur `Cache-Control` |
| 13 | HubsRepository processHubDtos sériel par serveur | 3-5s Home pour 3 serveurs | Paralléliser traitement hubs |
| 14 | LibrarySyncWorker notification chaque 1s → main thread | Jank pendant sync | Debounce à 5s |

### Player

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 15 | loadMedia() séquentiel (settings → fetch → URL → play) | 2-4s avant playback | Overlap settings + fetch en parallèle |
| 16 | Buffer ExoPlayer trop petit pour connexions relay | Rebuffering 30-60s | Buffer adaptatif relay vs local |
| 17 | MPV seek verification bloque main thread 500ms | Freeze UI au seek | `Dispatchers.IO` |

### APK Size

| # | Bottleneck | Impact | Fix |
|---|-----------|--------|-----|
| 18 | `isUniversalApk = true` → APK 200MB+ inutile | +200MB storage | `isUniversalApk = false` |
| 19 | Libs natives non strippées (FFmpeg 8MB, MPV 15MB) | APK 50MB → 30MB possible | `useLegacyPackaging = false` |
| 20 | Pas de prefetch bitmap taille optimisée TV | Images 4K chargées pour écran 1080p | `size(600, 900)` max |

---

## PHASE 4 — ARCHITECTURE

### Score par Module

| Module | Note | Issues P0 | Issues P1 | Commentaire |
|--------|------|-----------|-----------|-------------|
| `:domain` | **A-** | 0 | 1 | Excellent: zéro import Android/Room/Retrofit |
| `:data` | **B+** | 0 | 1 | TvProvider leak, sinon solide |
| `:app` | **C+** | 2 | 3 | Violations Clean Arch dans ViewModels |
| `:core:model` | **A** | 0 | 0 | Parfait |
| `:core:database` | **A** | 0 | 0 | Bien abstrait |
| `:core:network` | **A-** | 0 | 0 | Mineur: build file |
| `:core:ui` | **A** | 0 | 0 | Composants réutilisables |

### Violations Clean Architecture (P0)

1. **`LibraryViewModel.kt:51`** injecte `MediaDao` directement → créer `MediaRepository` interface domain
2. **`LibraryViewModel.kt:54`** injecte `ConnectionManager` → créer `ServerConnectionRepository`
3. **`PlayerController.kt:23`** injecte `ConnectionManager` → abstraire derrière interface

### Points Forts Architecture
- 22 interfaces Repository dans `:domain`, 21 implémentations dans `:data`
- Nommage 100% cohérent (`XxxRepository` → `XxxRepositoryImpl`)
- `AppError` sealed class pour error handling type-safe
- Kotlin 2.2.10, Compose BOM 2026.01, Hilt 2.58, Room 2.8.4 (stack moderne)

### Tests: 33 fichiers existants, 73% supprimés
**Tests prioritaires à restaurer:**
1. `PlaybackRepositoryImplTest.kt` (P1 — streaming logic)
2. `HomeViewModelTest.kt` (P1 — écran principal)
3. `SearchViewModelTest.kt` (P1 — feature critique)

---

## PHASE 5 — UX & DESIGN TV

### P0 Critiques

| ID | Problème | Fix |
|----|----------|-----|
| UX-1 | `LoadState.Error` invisible → écran blanc | Ajouter error state avec Retry button |
| UX-2 | Pas de countdown auto-next (Netflix standard) | Timer 10s avec auto-play |
| UX-3 | Home screen vide si pas de hubs (pas d'empty state) | Message + bouton "Go to Settings" |
| UX-4 | Pas de seek preview (BIF thumbnails) | Fetch BIF depuis Plex API |
| UX-5 | Pas de tutoriel premier lancement | Overlay translucide spotlight features |

### P1 Importants

| ID | Problème | Fix |
|----|----------|-----|
| UX-6 | Focus animation trop rapide (200ms vs Netflix 300ms) | Augmenter à 300ms |
| UX-7 | Back button ne restore pas le focus précédent | `rememberTvLazyListState` + `requestFocus()` |
| UX-8 | Home screen pas de skeleton (Library en a un) | `HomeScreenSkeleton()` |
| UX-9 | Buffering player: spinner sans contexte | Ajouter "Loading video..." / "Buffering..." |
| UX-10 | Search sans résultats: pas de suggestions | Afficher recherches populaires |
| UX-11 | Long press non implémenté sur cartes | Menu contextuel quick actions |

### Points Forts UX (déjà 85% Netflix-level)
- Hero billboard avec crossfade 500ms
- Skeleton screens en Library
- Skip intro/credits via Plex markers
- Navigation D-Pad bien gérée globalement
- Player error overlay avec retry

---

## PHASE 6 — FEATURES VENTE

### Déjà Implémentées

| Feature | Status | Notes |
|---------|--------|-------|
| Profils utilisateur (emoji + kids toggle) | **FAIT** | `feature/appprofile/` complet |
| Plex Home switching | **FAIT** | `feature/plexhome/` |
| Favoris | **FAIT** | Room `FavoriteEntity` |
| Historique | **FAIT** | Room `HistoryEntity` |
| Continue Watching | **FAIT** | `viewOffset` en Room |
| IPTV / Xtream Codes | **FAIT** | `feature/iptv/` |
| Multi-serveur unifié | **FAIT** | `unificationId` — **KILLER FEATURE** |
| Skip intro/credits | **FAIT** | Plex API markers |
| Player hybride ExoPlayer/MPV | **FAIT** | Routing intelligent bitrate/codec |
| Android TV Channels | **PARTIEL** | `TvChannelsWorker` existe |

### À Implémenter (Priorité Vente)

| Feature | Effort | Impact | Priorité |
|---------|--------|--------|----------|
| **Monétisation Free/Premium** | L | CRITIQUE | P0 — Pas de revenu sans ça |
| **Google Play Billing** | M | CRITIQUE | P0 — $4.99/mois ou $39.99/an |
| **Trial 7 jours** | S | HAUT | P0 — Conversion free→paid |
| **Kids Mode filtrage contenu** | S | HAUT | P1 — Flag existe, filtrage manquant |
| **Custom Lists** (au-delà favoris) | M | MOYEN | P1 — Rétention |
| **Push Notifications** (nouveau contenu) | L | HAUT | P1 — Rétention |
| **Statistiques visionnage** | L | MOYEN | P2 — "Year in Review" |
| **Screensaver bibliothèque** | M | MOYEN | P2 — Polish |
| **Recommandations locales** (TF Lite) | XL | MOYEN | P2 — Différenciation |
| **Watch Party** | XL | FAIBLE | P3 — Complexe |

### Avantage Compétitif Unique
**L'agrégation multi-serveur unifiée est un game-changer.** Aucun autre client Plex ne fait ça. Couplé au tier Premium ($4.99/mois), c'est un produit défendable.

---

## PHASE 7 — PRODUCTION READINESS

### Checklist

| Catégorie | Item | Status |
|-----------|------|--------|
| **Build** | Release signing | ✅ FAIT |
| | ProGuard/R8 activé | ✅ FAIT |
| | SemVer (v1.11.0) | ✅ FAIT |
| | ABI splits | ✅ FAIT |
| | Mapping file archivé | ⚠️ PARTIEL (pas d'upload auto) |
| | Timber debug-only | ✅ FAIT |
| | **API keys release fonctionnelles** | ❌ **BLOQUANT** |
| **Play Store** | Privacy policy | ⚠️ Existe mais pas hébergée HTTPS |
| | Target SDK 35 | ✅ FAIT |
| | Permissions justifiées | ✅ FAIT |
| | IARC content rating | ❌ À faire |
| | Data safety form | ❌ À faire |
| **Crash/Analytics** | Firebase Crashlytics | ✅ FAIT |
| | Firebase Analytics | ✅ FAIT (auto events seulement) |
| | Firebase Performance | ✅ FAIT |
| | Custom events/traces | ❌ À ajouter (15+ events) |
| **Accessibilité** | ContentDescription | ✅ 205 instances / 44 fichiers |
| | TalkBack testé | ❌ Non testé |
| | Contraste WCAG AA | ❌ Non audité |
| **Localisation** | FR/EN | ✅ 638/628 strings (98% FR) |
| | Strings externalisées | ✅ FAIT (0 hardcoded) |
| | Plurals.xml | ❌ Manquant |
| | RTL support | ⚠️ Flag activé, non testé |
| **Tests** | Unit tests critiques | ⚠️ 33 fichiers (40% coverage) |
| | Maestro E2E | ✅ 18 flows |
| | CI/CD | ✅ GitHub Actions |
| | Tests device réel | ❌ Pas de rapport |
| | Tests offline/slow | ❌ Non fait |

---

## PHASE 8 — PLAN D'ACTION

### Sprint 1 (1-2 sem) — Stabilité Critique

| Task | Fichier(s) | Effort | Dépendance |
|------|-----------|--------|------------|
| Fix API keys release (env vars) | `app/build.gradle.kts` | 4h | CI secrets |
| Fix SSL trust → cert pinning TOFU | `NetworkModule.kt` | 8h | — |
| Restrict HTTP cleartext IPs privées | `network_security_config.xml` | 1h | — |
| PagingConfig `maxSize=300` | `LibraryRepositoryImpl.kt` | 30min | — |
| Connection warmup non-bloquant | `PlexHubApplication.kt` | 2h | — |
| Restore 3 tests player VM | `app/src/test/` | 18h | — |
| Test release APK device réel | Manuel | 6h | Fix API keys |

### Sprint 2 (1-2 sem) — Sécurité & Performance

| Task | Fichier(s) | Effort | Dépendance |
|------|-----------|--------|------------|
| Héberger privacy policy HTTPS | GitHub Pages | 3h | — |
| Remove google-services.json du repo | `.gitignore`, CI | 2h | — |
| Colonne matérialisée `groupKey` + index | Migration Room + `MediaLibraryQueryBuilder` | 10h | — |
| Cache HTTP 50MB OkHttp | `NetworkModule.kt` | 3h | — |
| 15 analytics events | ViewModels | 8h | — |
| Paralléliser `processHubDtos` | `HubsRepositoryImpl.kt` | 4h | — |

### Sprint 3 (2-3 sem) — UX Polish

| Task | Fichier(s) | Effort | Dépendance |
|------|-----------|--------|------------|
| Error state Library (LoadState.Error) | `LibrariesScreen.kt` | 4h | — |
| Auto-next countdown 10s | `AutoNextPopup` composable | 6h | — |
| Home skeleton screen | `NetflixHomeScreen.kt` | 8h | — |
| Empty state Home (pas de hubs) | `NetflixHomeScreen.kt` | 3h | — |
| Seek preview BIF thumbnails | Player + Plex API | 12h | — |
| Kids mode content filtering | Room queries | 6h | Profile system |
| Accessibility Scanner audit | Manuel | 10h | — |

### Sprint 4 (1-2 sem) — Production Release

| Task | Effort |
|------|--------|
| IARC content rating | 2h |
| Data safety form | 3h |
| Store listing (screenshots, description FR/EN) | 10h |
| Closed beta 100 users | 1 semaine |
| Production release (staged rollout 10→50→100%) | 1h + monitoring 48h |

### Sprint 5+ — Features Premium

| Task | Effort |
|------|--------|
| Google Play Billing ($4.99/mois) | L |
| Trial 7 jours | S |
| Kids mode enforcement | S |
| Custom Lists | M |
| Push Notifications | L |
| Statistiques visionnage | L |

---

## RÉSUMÉ EXÉCUTIF

**PlexHubTV est à 60% production-ready avec des fondations architecturales solides (B-).**

**Forces:**
- Killer feature: agrégation multi-serveur unifiée (unique sur le marché)
- Stack moderne (Kotlin 2.2, Compose BOM 2026, Room 2.8)
- Architecture Clean bien respectée (domain pur, 22 repos)
- UI Netflix-like à 85% du polish
- Player hybride ExoPlayer/MPV intelligent
- Profils, Favoris, Historique, IPTV déjà implémentés

**Faiblesses:**
- 10 P0 bloquants à fixer (sécurité, perf, UX)
- 73% tests supprimés (40% coverage actuel)
- Pas de monétisation
- API keys cassées en release

**Temps estimé vers production: 6-8 semaines** (1 développeur)
- Sprint 1+2: 3-4 semaines (stabilité + sécurité)
- Sprint 3: 2-3 semaines (UX polish)
- Sprint 4: 1-2 semaines (release Play Store)
