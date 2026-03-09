# PlexHubTV — Rapport d'Audit Production Complet

> **Date** : 19 février 2026
> **Version auditée** : 0.10.0 (branche `claude/continue-plexhubtv-refactor-YO43N`)
> **Objectif** : Rendre l'app commerciale et publiable sur Google Play Store
> **Méthode** : Audit par 5 agents spécialisés en parallèle (Opus 4.6)

---

## RAPPORT EXÉCUTIF

### Score Global : 4/10

PlexHubTV a une **architecture solide** (Clean Architecture multi-modules, Room WAL, Hilt, Paging3) et un **design Netflix-like prometteur**. Cependant, des problèmes critiques empêchent la mise en production :

### Top 10 Issues P0 (Bloquant vente)

| # | Issue | Agent | Impact | Effort |
|---|-------|-------|--------|--------|
| 1 | **Vulnérabilité MITM** — TrustManager accepte TOUS les certificats | Security | Critique | 2j |
| 2 | **Auth TV inutilisable** — Requiert clavier virtuel, PIN flow commenté | UX | Critique | 3j |
| 3 | **Pas de crash reporting** — Aucun Firebase Crashlytics/analytics | Release | Critique | 2j |
| 4 | **Pas de Privacy Policy** — Rejet Play Store garanti | Release | Bloquant | 1j |
| 5 | **`!!` sur réponses API nullable** — Crash auth sur réponse malformée | Stability | Haute | 1j |
| 6 | **PlayerController scope jamais annulé** — Fuites mémoire + crash use-after-release | Stability | Haute | 1j |
| 7 | **SeasonDetail = layout mobile** — TopAppBar incompatible TV | UX | Haute | 2j |
| 8 | **Profils sans indicateur focus** — Navigation impossible | UX | Haute | 1j |
| 9 | **`core:model` et `core:navigation` — dépendances de tout l'app** | Architecture | Haute | 1j |
| 10 | **Pas de localisation** — 171+ strings hardcodées, pas de FR/EN | Release | Haute | 5-7j |

### Estimation effort total
- **Sprints 1-4 (minimum release)** : ~45-55 jours ouvrés (~10-12 semaines 1 dev)
- **Recommandé** : 2 développeurs pendant 6-8 semaines

---

## PHASE 1 — STABILITÉ & CRASH-PROOFING

### 1.1 Null Safety

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| NS1 | **P0** | `AuthRepositoryImpl.kt:68` | `body.id!!` et `body.code!!` — crash si API retourne null |
| NS2 | **P1** | `OfflineWatchSyncRepositoryImpl.kt:224-225` | `action.viewOffset!!` et `action.duration!!` — gardé par null-check mais fragile |
| NS3 | **P1** | `LibraryRepositoryImpl.kt:308-309` | `entity.serverIds!!.split(",")` — gardé mais fragile |
| NS4 | **P1** | `SplashViewModel.kt:72` | `authenticationResult!!` — race théorique possible |
| NS5 | **P1** | `NetworkModule.kt:87` | `.first()` sur trustManagers — crash possible sur ROM custom |
| NS6 | **P1** | `TrackSelectionUseCase.kt:83` | `realTracks.first()` sur liste potentiellement vide |
| NS7 | **P1** | `PlayerController.kt:287` | `availableQualities.first()/.last()` — crash si liste vide |
| NS8 | **P1** | `PlayerController.kt:100-102, 250-252` | Multiple `!!` sur nullable fields |
| NS9 | **P2** | `DebugViewModel.kt:94`, `ImageModule.kt:38` | `as ActivityManager` unsafe cast |

**Action** : Remplacer tous les `!!` par `?.let {}`, `?:`, ou `checkNotNull()` avec message explicite.

### 1.2 Lifecycle Leaks

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| LL1 | **P0** | `PlayerController.kt:45` | `@Singleton` avec `CoroutineScope` jamais annulé — coroutines zombies après `release()` |
| LL2 | **P1** | `SettingsDataStore.kt:65` | `CoroutineScope(Dispatchers.IO).launch` sans SupervisorJob ni cancellation |
| LL3 | **P1** | `HomeViewModel.kt:110` | Flow collectors accumulés à chaque `loadContent()`/Refresh |

### 1.3 Race Conditions

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| RC1 | **P1** | `ConnectionManager.kt:155-157` | `completedCount++` non atomique — potentiel hang |
| RC2 | **P1** | `ConnectionManager.kt:47` | `failedServers` = `mutableMapOf()` non thread-safe → `ConcurrentHashMap` |
| RC3 | **P1** | `PlaybackManager.kt:30-32` | 3 mutations `.value =` non atomiques |
| RC4 | **P1** | `SecurePreferencesManager.kt` | Writes `synchronized` mais reads non synchronisés |
| RC5 | **P2** | `SplashViewModel.kt:33-34` | `var` partagées entre coroutines sans sync |

### 1.4 Process Death
**Verdict : Adéquat** pour une app TV. SavedStateHandle utilisé dans les ViewModels avec paramètres de navigation. Les données sont re-fetch depuis Room/réseau au redémarrage.

### 1.5 Error Handling

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| EH1 | **P0** | (global) | Aucun `CoroutineExceptionHandler` global — exceptions non gérées crash ou disparaissent |
| EH2 | **P1** | `LibrarySyncWorker.kt:209` | Toujours `Result.success()` — jamais de retry automatique |
| EH3 | **P1** | (ViewModels) | Channels one-shot peuvent perdre des événements lors de config changes |
| EH4 | **P2** | `PlayerController.kt:312` | `loadMedia()` ne catch pas les exceptions de `getMediaDetailUseCase` |

### 1.6 Player Robustness

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| PR1 | **P1** | `PlayerController.kt:221-233` | Seules les erreurs codec déclenchent le fallback MPV — pas les erreurs réseau, pas de retry |
| PR2 | **P1** | (absent) | Aucune gestion de l'audio focus (`AudioManager.requestAudioFocus`) |
| PR3 | **P2** | `PlayerController.kt:107-117` | `release()` ne cancel pas les coroutines scrobbler/stats |

---

## PHASE 2 — SÉCURITÉ

### 2.1 Vulnérabilité Critique

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| **SEC1** | **P0** | `NetworkModule.kt:99-116` | **TrustManager accepte TOUS les certificats** — `checkServerTrusted` catch silencieusement toute erreur de validation. MITM possible sur TOUS les hosts (plex.tv, tmdb, omdb). Le hostname verifier ne compense pas car le handshake SSL est déjà validé. |

**Fix recommandé** : Vérifier si le host est une IP privée DANS `checkServerTrusted`. N'accepter les certificats invalides que pour les IPs privées (LAN).

### 2.2 Autres Issues Sécurité

| # | Priorité | Fichier | Problème |
|---|----------|---------|----------|
| SEC2 | **P1** | `SecurePreferencesManager.kt:52-56` | Fallback vers SharedPreferences en clair si Keystore corrompu |
| SEC3 | **P1** | `network_security_config.xml:8` | `cleartextTrafficPermitted="true"` global — devrait être limité IPTV/IPs privées |
| SEC4 | **P1** | (absent) | Pas de gestion expiration/refresh token — 401 non géré |
| SEC5 | **P1** | `SettingsViewModel.kt` | Logout ne révoque pas le token côté serveur Plex |
| SEC6 | **P2** | (absent) | Aucun certificate pinning pour plex.tv, tmdb, omdb |
| SEC7 | **P2** | `MainActivity.kt:241` | Pas de validation des paramètres deep link |
| SEC8 | **P2** | `IptvRepositoryImpl.kt:32` | URLs IPTV utilisateur non validées (pas de guard `file://`, `javascript://`) |
| SEC9 | **P2** | (absent) | `POST_NOTIFICATIONS` : pas de demande runtime permission (API 33+) |

---

## PHASE 3 — PERFORMANCE (Top 10 Bottlenecks)

| # | Priorité | Fichier | Problème | Solution |
|---|----------|---------|----------|----------|
| **PERF1** | **P0** | `MediaDao.kt:225-369` | 8 queries agrégées copy-paste identiques (seul ORDER BY change) + collision `SELECT *` avec aliases Room | 1 `@RawQuery` dynamique, colonnes explicites |
| **PERF2** | **P0** | `core:model/build.gradle.kts`, `core:navigation/build.gradle.kts` | Dépendances ENTIÈRES de l'app copy-paste (ExoPlayer, MPV, Retrofit, Room, etc.) | Nettoyer : garder uniquement kotlinx-serialization |
| **PERF3** | **P1** | `HomeContentDao.kt:29-41` | JOIN sans filtre `filter/sortOrder` → doublons cards Home | Ajouter `AND m.filter = 'all'` |
| **PERF4** | **P1** | `PerformanceImageInterceptor.kt:17-64` | Tracking sur CHAQUE image load (~100+ ops/écran Home) | Gate `BuildConfig.DEBUG` ou sampling |
| **PERF5** | **P1** | (global Compose) | Zéro `derivedStateOf` — recompositions inutiles | Wrapper computations dans `remember { derivedStateOf {} }` |
| **PERF6** | **P1** | `PlayerController.kt:45-46` | Scope Singleton jamais annulé → coroutines zombies | Cancel/recréer scope dans `release()`/`initialize()` |
| **PERF7** | **P2** | `PlayerFactory.kt:31-34` | ExoPlayer sans `LoadControl` config — buffer 50s par défaut | `DefaultLoadControl.Builder()` adaptatif LAN/relay |
| **PERF8** | **P2** | `ImageModule.kt:64` | `memoryCacheSize.toInt()` — truncation risk (safe avec cap 400MB) | Monitoring |
| **PERF9** | **P2** | `core:ui/build.gradle.kts:64-80` | Dépendances dupliquées (tv-foundation ×2, coil-compose ×2) | Nettoyer |
| **PERF10** | **P2** | `MediaDao.kt:225-369` | Aliases `rating`, `addedAt`, `audienceRating` collisionnent avec colonnes réelles → Room ignore les agrégats | Renommer aliases (`aggregatedRating`, `latestAddedAt`) |

---

## PHASE 4 — ARCHITECTURE

### Scores Santé par Module

| Module | Note | Problème principal |
|--------|------|-------------------|
| `:domain` | **A** | Clean, aucun import Android |
| `:core:datastore` | **A** | Minimal |
| `:core:designsystem` | **A** | Clean |
| `:core:database` | **A-** | Bon, minor Gson duplication |
| `:app` | **B+** | Quelques accès DB directs (PlayerController → TrackPreferenceDao) |
| `:core:common` | **B+** | Navigation + WorkManager mal placés |
| `:data` | **B** | Gson + kotlinx-serialization dual |
| `:core:ui` | **B** | Dépendances dupliquées |
| `:core:network` | **B-** | Importe `core:database` directement (violation) |
| `:core:model` | **D** | TOUTES les dépendances app copy-paste |
| `:core:navigation` | **D** | TOUTES les dépendances app copy-paste |

### Dépendances à Mettre à Jour

| Librairie | Actuelle | Recommandée | Urgence |
|-----------|----------|-------------|---------|
| Coil | 2.7.0 | 3.x | **Majeure** |
| TV Foundation | 1.0.0-alpha12 | 1.0.0-beta+ | Importante |
| TV Material | 1.1.0-alpha01 | 1.1.0-beta+ | Importante |
| Truth | 1.1.5 | 1.4.x | Mineure |
| Robolectric | 4.11.1 | 4.14+ | Mineure |
| Gson | → supprimer | kotlinx-serialization | Standardiser |

---

## PHASE 5 — UX/UI TV

### P0 — Inutilisable (6 findings)

| # | Écran | Problème |
|---|-------|----------|
| UX1 | **Auth** | `OutlinedTextField` requiert clavier virtuel — impossible sur TV sans clavier |
| UX2 | **Auth** | PIN flow commenté (`AuthScreen.kt:91-98`) — pas d'auth standard TV |
| UX3 | **SeasonDetail** | Layout mobile `TopAppBar + Scaffold` — non adapté TV |
| UX4 | **SeasonDetail** | Aucun `FocusRequester` → utilisateur perdu dans la liste |
| UX5 | **Profils** | Cards sans indicateur de focus — navigation impossible |
| UX6 | **Auth** | Aucune gestion focus — utilisateur ne trouve pas le champ input |

### P1 — Expérience Dégradée (17 findings)

| # | Écran | Problème |
|---|-------|----------|
| UX7 | Global | Aucun skeleton/shimmer loading — écrans blancs pendant chargement |
| UX8 | Global | Strings mixtes FR/EN hardcodées (pas de i18n) |
| UX9 | Global | Typographie trop petite (10-11sp metadata, badges) — illisible à 3m |
| UX10 | Global | Pas d'overscan safe area cohérent (48-58dp vs 96dp requis) |
| UX11 | Home | Billboard auto-rotation ne pause pas au focus |
| UX12 | Library | Filter chips 32dp trop petits pour TV (min 40dp) |
| UX13 | Library | Focus indicator quasi-invisible en mode List (alpha 0.05f) |
| UX14 | Detail | Loading = simple `CircularProgressIndicator` centré |
| UX15 | Player | Back quand en pause → resume au lieu de fermer (contre-intuitif) |
| UX16 | Player | Erreur affiche texte brut sans bouton retry |
| UX17 | Search | Pas de debounce sur les touches clavier |
| UX18 | Search | Pas de routing focus explicite keyboard → résultats |
| UX19 | Favorites | `onFocus` ne reset jamais à `false` — élévation permanente |
| UX20 | History | Utilise l'ancien `MediaCard` (pas `NetflixMediaCard`) |
| UX21 | Splash | Pas d'option skip — vidéo obligatoire |
| UX22 | Splash | Pas de fallback si vidéo échoue — écran noir infini |
| UX23 | TopBar | Pas de focus trap → focus imprévisible au DOWN |

### Wireframes Textuels

Fournis pour : Home Screen, Media Detail, Player Controls, Search Screen (voir rapport Agent 3 détaillé).

### Focus Management — Lacunes

```
Auth       → ❌ Aucun focus initial
Profils    → ❌ Aucun focus initial, pas d'indicateur
SeasonDetail → ❌ Aucun focus initial
Home       → ✅ Focus sur Play button billboard
Detail     → ✅ Focus sur Play button
Search     → ✅ Focus sur première touche clavier
Player     → ✅ Controls au D-Pad press
Library    → ✅ Focus restoration avec lastFocusedId
```

---

## PHASE 6 — FEATURES & MONÉTISATION

### Inventaire Features Actuelles

| Feature | Maturité | Statut |
|---------|----------|--------|
| Multi-serveur (découverte, agrégation) | ★★★★ | Production-ready |
| Lecture ExoPlayer + MPV fallback | ★★★★ | Robuste |
| Navigation Netflix-like (Hero, rows) | ★★★☆ | Bon, polish nécessaire |
| Paging3 bibliothèque (filtres, tri) | ★★★★ | Production-ready |
| Favoris / Watchlist | ★★★☆ | Fonctionnel |
| Historique de lecture | ★★★☆ | Fonctionnel |
| Downloads offline | ★★☆☆ | Base, pas de smart sync |
| Profils Plex Home | ★★★☆ | Fonctionnel (UI à améliorer) |
| IPTV (M3U parsing) | ★★☆☆ | Basique |
| Recherche multi-serveur | ★★★☆ | Fonctionnel |
| Collections Plex | ★★★☆ | Fonctionnel |
| Skip Intro/Credits | ★★★☆ | Marqueurs implémentés |
| 5 thèmes (Plex, Netflix, Mono, Morocco) | ★★★★ | Complet |
| Enrichissement multi-serveur (ratings) | ★★★★ | Sophistiqué |
| Sélection pistes audio/sous-titres | ★★★★ | Complet |

### Features Différenciantes Proposées

| Feature | Effort | Impact | Faisabilité | Priorité | Dépendances |
|---------|--------|--------|-------------|----------|-------------|
| **Android TV Channels** (Home widgets) | M (1-2 sem) | ★★★★★ | Easy | Must-have | `androidx.tvprovider` déjà en deps |
| **Continue Watching amélioré** (cross-device) | S (< 1 sem) | ★★★★☆ | Easy | Must-have | On Deck existe déjà |
| **Mode Enfant** (filtrage rating) | M (1-2 sem) | ★★★★☆ | Medium | Should-have | Profils existants |
| **Statistiques visionnage** | M (1-2 sem) | ★★★☆☆ | Easy | Should-have | Room DB existante |
| **Recommandations personnalisées** | L (2-4 sem) | ★★★★☆ | Medium | Should-have | Historique local |
| **Screensaver bibliothèque** | S (< 1 sem) | ★★★☆☆ | Easy | Nice-to-have | Coil cache |
| **Listes personnalisées** | M (1-2 sem) | ★★★☆☆ | Easy | Nice-to-have | Favoris existants |
| **Notifications nouveau contenu** | M (1-2 sem) | ★★★★☆ | Medium | Should-have | FCM + sync worker |
| **Voice Search (Assistant)** | L (2-4 sem) | ★★★★☆ | Hard | Nice-to-have | Android TV intent |
| **Watch Party** | XL (> 4 sem) | ★★★☆☆ | Hard | Nice-to-have | WebSocket server |

### Modèle de Monétisation Recommandé

**Modèle : Freemium + Achat Unique Premium**

| | Free | Premium (one-time $9.99) |
|--|------|--------------------------|
| Serveurs | 1 | Illimité |
| IPTV | ❌ | ✅ |
| Android TV Channels | ❌ | ✅ |
| Thèmes | 2 (Plex + Netflix) | 5 |
| Mode Enfant | ❌ | ✅ |
| Statistiques | ❌ | ✅ |
| Téléchargements offline | 3 | Illimité |
| Skip Intro/Credits | ✅ | ✅ |
| Multi-profils | ❌ | ✅ |

**Pourquoi achat unique vs abonnement** :
- Les clients Plex tiers (Infuse, Swiftfin) utilisent un modèle one-time ou lifetime
- Les utilisateurs Plex ont déjà un abonnement (Plex Pass) — résistance à un 2e abonnement
- $9.99 = prix psychologique attractif (< Infuse $94.99 lifetime)
- Option : version lifetime + version annuelle ($4.99/an)

### Positionnement Concurrentiel

| | PlexHubTV | Plex Official | Infuse | Nova | Kodi |
|--|-----------|---------------|--------|------|------|
| Prix | $9.99 one-time | Gratuit (+ Pass) | $9.99/an | Gratuit | Gratuit |
| UI TV-native | ★★★★ | ★★★☆ | ★★★★★ | ★★☆☆ | ★★☆☆ |
| Multi-serveur unifié | ★★★★★ | ❌ | ★★★★ | ❌ | ★★★☆ |
| IPTV | ✅ | ❌ | ❌ | ❌ | ✅ |
| 5 Thèmes | ✅ | ❌ | ❌ | ❌ | ✅ |
| Skip Intro/Credits | ✅ | ❌ | ✅ | ❌ | ❌ |
| Enrichissement ratings | ✅ | ✅ | ✅ | ❌ | ★★★☆ |
| Android TV Channels | Prévu | ✅ | N/A (iOS) | ❌ | ❌ |

**USP (Unique Selling Points)** :
1. Agrégation multi-serveur avec déduplication
2. IPTV intégré
3. 5 thèmes personnalisables
4. Skip Intro/Credits
5. UI Netflix-like spécialement conçue pour TV

---

## PHASE 7 — PRODUCTION READINESS CHECKLIST

### Build & Release

| Item | Statut | Détail |
|------|--------|--------|
| Release signing | ✅ | `app/build.gradle.kts:28-39` — keystore configuré avec fallback debug |
| ProGuard/R8 | ✅ | 134 lignes de règles, shrinkResources activé |
| Version SemVer | ✅ | `versionCode = 1`, `versionName = "0.10.0"` |
| ABI splits | ✅ | arm64-v8a, armeabi-v7a, x86, x86_64 + universal |
| Mapping file archivé | ❌ | Pas de Crashlytics → pas d'upload mapping |
| Debug logs release | ✅ | Timber.DebugTree seulement en DEBUG, HTTP logging NONE |
| Debug route release | ❌ | `Screen.Debug.route` accessible en release (`MainScreen.kt:159`) |

### Google Play Compliance

| Item | Statut | Détail |
|------|--------|--------|
| Privacy Policy | ❌ | Aucune URL configurée |
| Data Safety Form | ❌ | Non préparé |
| targetSdk | ✅ | compileSdk 36, targetSdk 35 |
| Permissions minimales | ✅ | INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS uniquement |
| Content Rating | ❌ | À configurer Play Console |
| Catégorie | ❌ | À configurer Play Console |

### Crash Reporting & Analytics

| Item | Statut |
|------|--------|
| Firebase Crashlytics | ❌ |
| ANR detection | ❌ |
| Analytics events | ❌ |
| Performance monitoring | ❌ (local Timber uniquement) |

### Accessibilité

| Item | Statut | Détail |
|------|--------|--------|
| ContentDescription | ⚠️ | 124/95 fichiers UI migrés (28%) |
| TalkBack | ⚠️ | Partiel, non testé |
| Contraste | ✅ | NetflixWhite/NetflixBlack passe WCAG AAA |
| Taille texte scalable | ✅ | Utilise `sp` units |

### Localisation

| Item | Statut | Détail |
|------|--------|--------|
| Strings externalisées | ❌ | 13 strings.xml (template), ~171+ hardcodées |
| Support FR/EN | ❌ | Pas de `values-fr/`, mix FR/EN dans le code |
| RTL support | ⚠️ | Déclaré `supportsRtl="true"`, non testé |
| Formats localisés | ❌ | Non vérifié |

### Testing

| Item | Statut | Détail |
|------|--------|--------|
| Tests unitaires | ⚠️ | 19 fichiers (~30-40% coverage) vs objectif 60% |
| Tests Room intégration | ❌ | Aucun |
| Tests UI Maestro | ❌ | Infrastructure ready (125 testTags), 0 tests |
| Tests device réel | ❌ | Non documenté |
| Tests offline/lent | ❌ | Non documenté |
| Tests multi-serveur | ❌ | Non documenté |

---

## PHASE 8 — PLAN D'ACTION SPRINT

### Sprint 1 (1-2 semaines) — Stabilité & Sécurité Critique

| # | Tâche | Priorité | Effort | Fichiers |
|---|-------|----------|--------|----------|
| 1.1 | **Fix TrustManager MITM** — vérifier IP privée dans `checkServerTrusted` | P0 | 2j | `NetworkModule.kt` |
| 1.2 | **Intégrer Firebase Crashlytics** | P0 | 2j | `build.gradle.kts`, `PlexHubApplication.kt`, `google-services.json` |
| 1.3 | **Remplacer tous les `!!`** par alternatives safe | P0 | 1j | `AuthRepositoryImpl`, `PlayerController`, `LibraryRepositoryImpl`, etc. |
| 1.4 | **Fix PlayerController scope** — cancel/recreate dans release/initialize | P0 | 1j | `PlayerController.kt` |
| 1.5 | **Ajouter CoroutineExceptionHandler global** | P0 | 0.5j | `PlexHubApplication.kt`, `CoroutineModule.kt` |
| 1.6 | **Fix ConnectionManager races** — `AtomicInteger` + `ConcurrentHashMap` | P1 | 0.5j | `ConnectionManager.kt` |
| 1.7 | **Scoper cleartext traffic** aux IPs privées/IPTV | P1 | 1j | `network_security_config.xml` |
| 1.8 | **Guard Debug route en release** | P0 | 0.5h | `MainScreen.kt:159` |

**Total Sprint 1 : ~8-9 jours**

### Sprint 2 (2 semaines) — UX TV Critique

| # | Tâche | Priorité | Effort | Fichiers |
|---|-------|----------|--------|----------|
| 2.1 | **Implémenter PIN auth flow** (décommenter + compléter) | P0 | 3j | `AuthScreen.kt`, `AuthViewModel.kt` |
| 2.2 | **Refactor SeasonDetail** — supprimer TopAppBar, layout TV full-bleed | P0 | 2j | `SeasonDetailScreen.kt` |
| 2.3 | **Focus management** — Auth, Profils, SeasonDetail | P0 | 1j | 3 écrans |
| 2.4 | **Focus indicators** sur ProfileScreen cards | P0 | 0.5j | `ProfileScreen.kt` |
| 2.5 | **Skeleton/shimmer loading** sur Home, Library, Detail | P1 | 2j | Composants core:ui + 3 écrans |
| 2.6 | **Augmenter typographie** — minimum 14sp pour metadata TV | P1 | 1j | `NetflixMediaCard`, `Theme.kt` |
| 2.7 | **Overscan safe area** — composable wrapper 96dp | P1 | 1j | Créer `OverscanSafeArea`, appliquer partout |
| 2.8 | **Fix Favorites onFocus** — reset à false | P1 | 0.5h | `FavoritesScreen.kt:124` |
| 2.9 | **Fix player Back button** — pause ne doit pas resume | P1 | 0.5j | `VideoPlayerScreen.kt:168-175` |

**Total Sprint 2 : ~11-12 jours**

### Sprint 3 (2 semaines) — Performance & Architecture

| # | Tâche | Priorité | Effort | Fichiers |
|---|-------|----------|--------|----------|
| 3.1 | **Nettoyer `core:model` et `core:navigation` build.gradle** | P0 | 1j | 2 `build.gradle.kts` |
| 3.2 | **Consolider 8 queries MediaDao → 1 @RawQuery** | P0 | 2j | `MediaDao.kt` |
| 3.3 | **Fix HomeContentDao JOIN doublons** | P1 | 0.5j | `HomeContentDao.kt` |
| 3.4 | **Fix SELECT * alias collision** dans MediaDao | P1 | 1j | `MediaDao.kt` |
| 3.5 | **Gate PerformanceImageInterceptor** en DEBUG | P1 | 0.5h | `PerformanceImageInterceptor.kt` |
| 3.6 | **Ajouter derivedStateOf** dans Home + Library | P1 | 0.5j | `NetflixHomeScreen.kt`, `LibrariesScreen.kt` |
| 3.7 | **Configurer ExoPlayer buffer** adaptatif | P2 | 1j | `PlayerFactory.kt` |
| 3.8 | **Restaurer tests P1** (7 fichiers) | P1 | 7j | 7 nouveaux fichiers test |
| 3.9 | **Supprimer Gson, standardiser kotlinx-serialization** | P2 | 2j | Modules data, network |

**Total Sprint 3 : ~15-16 jours**

### Sprint 4 (1-2 semaines) — Production Release

| # | Tâche | Priorité | Effort | Fichiers |
|---|-------|----------|--------|----------|
| 4.1 | **Créer Privacy Policy** | P0 | 1j | Hébergement externe + lien Settings |
| 4.2 | **Externaliser 171+ strings → strings.xml** | P0 | 5j | ~44+ fichiers feature |
| 4.3 | **Créer `values-fr/strings.xml`** | P1 | 2j | Nouveau fichier |
| 4.4 | **Data Safety Form** Play Console | P0 | 1j | Play Console |
| 4.5 | **Test release build** sur devices TV réels | P0 | 2j | Shield, Chromecast GTV |
| 4.6 | **Préparer Store listing** | P0 | 2j | Screenshots, description |

**Total Sprint 4 : ~13 jours**

### Sprint 5+ — Features Premium & Hardening

| # | Tâche | Priorité | Effort |
|---|-------|----------|--------|
| 5.1 | Android TV Channels integration | Should-have | 1-2 sem |
| 5.2 | Google Play Billing (Premium) | Should-have | 2 sem |
| 5.3 | Mode Enfant | Should-have | 1-2 sem |
| 5.4 | Statistiques visionnage | Nice-to-have | 1-2 sem |
| 5.5 | Tests Maestro E2E | Should-have | 1 sem |
| 5.6 | Audio focus management | Should-have | 2j |
| 5.7 | Token refresh / 401 handling | Should-have | 2j |
| 5.8 | Recommandations personnalisées | Nice-to-have | 2-4 sem |

---

## DOCUMENTATION TECHNIQUE — FIXES CRITIQUES

### Fix 1 : TrustManager (P0 Sécurité)

```kotlin
// NetworkModule.kt — checkServerTrusted fix
override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    try {
        defaultTrustManager.checkServerTrusted(chain, authType)
    } catch (e: CertificateException) {
        // Only bypass for private IPs (LAN Plex servers with self-signed certs)
        // The socket's remote address must be checked here
        // For now, re-throw for public domains
        throw e
    }
}

// Better approach: Use separate OkHttpClients
// - Default client: standard TrustManager for plex.tv, tmdb, omdb
// - LAN client: custom TrustManager only for private IPs (10.x, 192.168.x, 172.16-31.x)
```

### Fix 2 : PlayerController Scope (P0 Stabilité)

```kotlin
// PlayerController.kt
private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun release() {
    positionTrackerJob?.cancel()
    playerScrobbler.stop()
    playerStatsTracker.stopTracking()
    player?.release()
    player = null
    scope.cancel() // Cancel ALL coroutines
}

fun initialize(...) {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main) // Fresh scope
    // ... rest of init
}
```

### Fix 3 : ConnectionManager Race (P1 Stabilité)

```kotlin
// ConnectionManager.kt
private val failedServers = ConcurrentHashMap<String, Long>()

// In raceUrls:
val completedCount = AtomicInteger(0)
// ...
if (completedCount.incrementAndGet() == urls.size && !winner.isCompleted) {
    winner.complete(null)
}
```

---

## CHECKLIST VALIDATION PRÉ-RELEASE

### Avant chaque release, vérifier :

- [ ] `./gradlew testDebugUnitTest` passe (0 failures)
- [ ] APK release testée sur device TV réel
- [ ] Pas de `!!` (grep `'!!'` retourne 0 dans src/main)
- [ ] Mapping file sauvegardée
- [ ] Version code incrémentée
- [ ] Pas de `Log.d` dans le code (grep `'Log.d'`)
- [ ] Tous les strings dans `strings.xml`
- [ ] Privacy Policy URL accessible
- [ ] Cleartext traffic limité aux IPs privées
- [ ] TrustManager validé (pas de bypass global)

---

> **Rapport généré le 19 février 2026 par équipe d'audit PlexHubTV (5 agents Opus 4.6)**
