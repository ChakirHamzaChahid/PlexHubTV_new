# Phase 8 — Action Plan (5 sprints)

> **Agent** : Release Agent (Agent 5)
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Input** : phases 1 à 7, toutes sévérités

Conventions :
- **Effort** : S ≤ 0.5 j · M 1-3 j · L 3-7 j · XL > 7 j
- **Owner** : suggestion de rôle, pas d'attribution nominative
- **Dépendances** : IDs finding à traiter avant

---

## Sprint 1 — Stabilité critique (1-2 semaines)

Objectif : aucun crash / ANR / perte de données observable sur Mi Box S dans les scénarios core (launch, login, lib sync, navigate, play).

| ID finding | Titre | Fichier(s) | Effort | Dépendances | Owner |
|---|---|---|---|---|---|
| AUDIT-1-001 | Xtream mapper pageOffset=0 → UNIQUE INDEX wipe library | `data/mapper/XtreamMediaMapper.kt`, `data/repository/XtreamVodRepositoryImpl.kt`, `XtreamSeriesRepositoryImpl.kt` | S | — | Backend |
| AUDIT-1-002 | Release fallback debug keystore | `app/build.gradle.kts` | S | — | Build |
| AUDIT-1-003 | versionCode figé à 1 | `app/build.gradle.kts` | S | — | Build |
| AUDIT-1-005 | PlayerControlViewModel no-op silencieux sur args null | `feature/player/PlayerControlViewModel.kt`, `PlayerController.kt` | S | — | Player |
| AUDIT-1-016 | Pas de onTrimMemory → OOM Mi Box S | `PlexHubApplication.kt`, `MediaDetailViewModel.kt`, `HomeViewModel.kt` | M | AUDIT-3-001, AUDIT-3-002 | Core |
| AUDIT-3-001 | ExoPlayer buffer 30 s ~300 MB → OOM 4K HEVC | `feature/player/PlayerFactory.kt` | S | — | Player |
| AUDIT-3-003 | `media` table 20+ index → INSERT OR REPLACE coûteux | `core/database/MediaEntity.kt`, migrations | M | AUDIT-3-012 | DB |
| AUDIT-3-004 | Home `scrollToItem` sur chaque D-pad left/right | `feature/home/NetflixHomeScreen.kt`, `DiscoverScreen.kt` | S | — | UI |
| AUDIT-3-005 | 5 jobs parallèles bloquants en onCreate | `PlexHubApplication.kt` | S | AUDIT-3-006 | Core |
| AUDIT-3-006 | Aucun Baseline Profile | nouveau module `:baselineprofile` | M | AUDIT-3-005 | Perf |
| AUDIT-4-001 | `:core:network` bloc android { } imbriqué | `core/network/build.gradle.kts` | S | — | Build |
| AUDIT-4-002 | `:domain` en Android library dépend de `core.ktx` + timber | `domain/build.gradle.kts`, use cases qui importent android.* | M | AUDIT-4-024 | Architecture |
| AUDIT-4-003 | `GetSuggestionsUseCase` dans `:app`, dépendances leak | `app/.../domain/usecase/GetSuggestionsUseCase.kt` | S | AUDIT-4-002, AUDIT-4-004 | Architecture |
| AUDIT-5-028 | OverscanSafeArea défini mais jamais utilisé | `core/ui/OverscanSafeArea.kt`, `feature/home/NetflixHomeScreen.kt`, Library, Settings, Player | M | — | UI |
| AUDIT-5-029 | Typographie trop petite pour 3 m | `core/designsystem/Type.kt`, `core/designsystem/Dimensions.kt` | S | — | Design |
| AUDIT-4-014 | Turbine absent → tests Flow bloqués | `libs.versions.toml`, `:core:testing` ou `:app` test deps | S | — | Test |
| AUDIT-4-015 | 0 tests sur modules core:database/datastore/ui | `*/src/test/**`, `*/src/androidTest/**` | L | AUDIT-4-014 | Test |
| AUDIT-4-020 | Coverage < 10 %, pas de tests de restauration | `*/src/test/**` | L | AUDIT-4-014, AUDIT-4-015 | Test |

**Exit criteria Sprint 1** :
- Build release échoue si keystore absent ; versionCode incrémenté et strategy automatisée.
- Xtream sync ne wipe plus la catégorie ; test unitaire ajouté.
- Deep link malformé affiche un écran d'erreur au lieu d'un écran noir.
- Mi Box S 2 GB peut lire 2 h de 4K HEVC sans OOM (mesure PSS).
- Cold start < 3.5 s sur Mi Box S grâce au Baseline Profile.
- `:domain` compile sans `android.*` import.
- Home bouge correctement au D-pad horizontal sans snap parasite.
- ≥ 60 nouveaux tests unit passent (base pour sprint 2).

---

## Sprint 2 — Sécurité & Performance (1-2 semaines)

Objectif : fermer toutes les fuites de token, durcir TLS, résoudre les 5 bottlenecks perf mesurables.

| ID finding | Titre | Fichier(s) | Effort | Dépendances | Owner |
|---|---|---|---|---|---|
| AUDIT-2-001 / AUDIT-1-004 | HttpLoggingInterceptor HEADERS expose X-Plex-Token | `core/network/NetworkModule.kt` | S | — | Network |
| AUDIT-2-002 | Timber logs URLs serveurs avec tokens | `data/source/PlexSourceHandler.kt`, `LibrarySyncWorker.kt`, `AuthInterceptor.kt` | S | — | Network |
| AUDIT-2-003 | Pas de certificate pinning | `core/network/NetworkModule.kt` | M | — | Security |
| AUDIT-2-004 | Release build peut signer avec debug (dup AUDIT-1-002) | `app/build.gradle.kts` | S | AUDIT-1-002 | Build |
| AUDIT-2-005 | DataStore connectionCacheStore plaintext | `core/datastore/SettingsDataStore.kt`, `core/network/ConnectionManager.kt` | M | — | Security |
| AUDIT-2-006 | Network Security Config permet cleartext non-plex | `app/src/main/res/xml/network_security_config.xml` | S | — | Security |
| AUDIT-2-008 | ApkInstaller installe sans vérification SHA256 | `core/update/ApkInstaller.kt`, `UpdateChecker.kt` | M | AUDIT-2-011 | Security |
| AUDIT-2-011 | UpdateChecker trust GitHub API sans TLS pin | `core/update/UpdateChecker.kt` | S | AUDIT-2-003 | Security |
| AUDIT-2-012 | REQUEST_INSTALL_PACKAGES permission | `AndroidManifest.xml`, `core/update/ApkInstaller.kt` | M | AUDIT-2-008 | Security |
| AUDIT-2-013 | AuthInterceptor n'émet TokenInvalid que sur plex.tv 401 | `core/network/AuthInterceptor.kt`, `core/network/AuthEventBus.kt` | S | — | Network |
| AUDIT-2-014 | Crashlytics setCustomKey PII (ratingKey, serverId) | `app/di/AnalyticsModule.kt`, `handler/CrashReportingTree.kt` | S | — | Analytics |
| AUDIT-1-007 | DataStore no corruptionHandler | `core/datastore/DataStoreExtensions.kt`, `DataStoreModule.kt` | S | — | Core |
| AUDIT-1-008 | LibrarySyncWorker foreground notif sans POST_NOTIFICATIONS check | `work/LibrarySyncWorker.kt` | S | — | Core |
| AUDIT-1-013 | PlayerController singleton tient ExoPlayer+AudioFocus lié à Application | `feature/player/controller/PlayerController.kt` | M | — | Player |
| AUDIT-1-018 | LibrarySyncWorker log URLs tokens | `work/LibrarySyncWorker.kt` | S | AUDIT-2-002 | Core |
| AUDIT-3-002 | Coil memoryCache 20 % heap → flush agressif | `PlexHubApplication.kt` | S | AUDIT-3-007 | Perf |
| AUDIT-3-007 | Coil pas de `allowHardware(false)` pour Mali-450 | `PlexHubApplication.kt` | S | AUDIT-3-002 | Perf |
| AUDIT-3-008 | `List<MediaItem>` non-immutable dans Compose params | `feature/home/NetflixHomeScreen.kt`, `core/ui/NetflixContentRow.kt`, `core/ui/SpotlightGrid.kt`, `feature/details/NetflixDetailScreen.kt` | S | AUDIT-3-021 | Compose |
| AUDIT-3-009 | ABI splits désactivés, 4 ABIs embarquées (dup AUDIT-1-006) | `app/build.gradle.kts` | S | AUDIT-1-006 | Build |
| AUDIT-3-010 | `playerOkHttpClient` duplique ConnectionPool/cache | `feature/player/PlayerFactory.kt`, `core/network/NetworkModule.kt` | M | AUDIT-3-015 | Network |
| AUDIT-3-011 | MediaLibraryQueryBuilder unifié sans index composite | `data/repository/MediaLibraryQueryBuilder.kt`, migrations | M | AUDIT-3-003 | DB |
| AUDIT-3-012 | 36 migrations Room non testées | `core/database/migrations/**` | L | AUDIT-3-003, AUDIT-4-015 | DB |
| AUDIT-3-013 | `NetflixMediaCard` drawWithContent + scrim overdraw | `core/ui/NetflixMediaCard.kt` | S | — | Compose |
| AUDIT-3-014 | 4 workers périodiques + FGS (réveils) | `PlexHubApplication.kt`, `work/**` | S | — | Core |
| AUDIT-3-015 | OkHttp ConnectionPool identique partout, pas DNS cache | `core/network/NetworkModule.kt` | M | — | Network |
| AUDIT-3-016 | Gson utilisé comme converter principal | `core/network/NetworkModule.kt`, tous les DTOs | M | — | Network |
| AUDIT-3-017 | MediaDao.searchMedia LIKE full scan, FTS4 sans rank | `core/database/dao/MediaDao.kt`, migrations | M | AUDIT-3-012 | DB |
| AUDIT-3-021 | Compose compiler metrics non activées | `app/build.gradle.kts`, `core/ui/build.gradle.kts` | S | AUDIT-3-008 | Perf |
| AUDIT-7-001 | Mapping file Crashlytics vérification push | CI scripts, `app/build.gradle.kts` | S | AUDIT-1-002 | Build |
| AUDIT-7-017 | StrictMode activé en debug | `PlexHubApplication.kt` | S | — | Core |

**Exit criteria Sprint 2** :
- `adb logcat` propre : aucun token Plex visible en debug ni en release.
- Certificate pinning actif sur plex.tv, tmdb, omdb, opensubtitles.
- APK release signé release + vérifiable + mapping file uploadé vers Crashlytics à chaque build.
- APK taille ≤ 35 MB (arm64 uniquement) vs > 100 MB actuel universel.
- Compose compiler metrics disponibles ; score de skippability documenté.
- PSS 4K HEVC < 350 MB ; aucun `OutOfMemoryError` dans le test macrobench.

---

## Sprint 3 — UX Polish (2-3 semaines)

Objectif : navigation D-pad fluide, focus management déterministe, skeletons / empty / error states propres, < 100 ms focus latency Mi Box S.

| ID finding | Titre | Fichier(s) | Effort | Dépendances | Owner |
|---|---|---|---|---|---|
| AUDIT-5-001 | Scroll snap agressif supprime autres rails | `feature/home/NetflixHomeScreen.kt`, `DiscoverScreen.kt` | S | AUDIT-3-004 | UI |
| AUDIT-5-002 | Home EmptyState hardcodé anglais | `feature/home/NetflixHomeScreen.kt`, `strings.xml` | S | AUDIT-5-003 | UI/i18n |
| AUDIT-5-003 | Home rails titres hardcodés anglais | `feature/home/DiscoverScreenComponents.kt`, `strings.xml` | S | — | UI/i18n |
| AUDIT-5-004 | HomeHeader décoratif, aucune action | `core/ui/HomeHeader.kt` | M | — | UI |
| AUDIT-5-005 | Premier focus Home fragile | `feature/home/NetflixHomeScreen.kt` | S | — | UI |
| AUDIT-5-006 | InitialSyncState/ErrorState anglais | `feature/home/NetflixHomeScreen.kt`, `strings.xml` | S | AUDIT-5-002 | UI/i18n |
| AUDIT-5-007 | NetflixDetailScreen ≥ 8 strings anglais | `feature/details/NetflixDetailScreen.kt`, `strings.xml` | S | — | UI/i18n |
| AUDIT-5-008 | ActionButtonsRow Play/Loading hardcodé | `feature/details/NetflixDetailScreen.kt` | S | AUDIT-5-007 | UI/i18n |
| AUDIT-5-009 | Tabs detail — pas de focus restoration | `feature/details/NetflixDetailTabs.kt` | M | — | UI |
| AUDIT-5-010 | ExpandableSummary sans indicateur focus | `feature/details/NetflixDetailScreen.kt` | S | — | UI |
| AUDIT-5-011 | DetailHeroSection poster trop petit | `feature/details/NetflixDetailScreen.kt` | S | AUDIT-5-029 | UI |
| AUDIT-5-012 | CastRow sans key → focus saute au recompose | `feature/details/NetflixDetailScreen.kt` | S | — | UI |
| AUDIT-5-013 | Library focus restoration async fragile | `feature/library/LibrariesScreen.kt`, `LibraryViewModel.kt` | M | AUDIT-5-014 | UI |
| AUDIT-5-014 | Library scrollRequest ignore pendingScrollRestore | `feature/library/LibrariesScreen.kt` | S | — | UI |
| AUDIT-5-015 | Library filter chips cachent titre d'écran | `feature/library/LibraryComponents.kt` | S | — | UI |
| AUDIT-5-016 | Library refresh IconButton sans focus visible | `feature/library/LibrariesScreen.kt` | S | — | UI |
| AUDIT-5-017 | Search pas de BackHandler | `feature/search/NetflixSearchScreen.kt` | S | — | UI |
| AUDIT-5-018 | Search idle/error strings mixtes | `feature/search/NetflixSearchScreen.kt`, `strings.xml` | S | — | UI/i18n |
| AUDIT-5-019 | Search focus initial D-pad maladroit | `feature/search/NetflixSearchScreen.kt` | S | — | UI |
| AUDIT-5-020 | Player KEYCODE_MEDIA_STOP non géré | `feature/player/VideoPlayerScreen.kt` | S | — | Player |
| AUDIT-5-021 | Player Audio/Subtitle Sync hardcodés | `feature/player/ui/components/PlayerSettingsDialog.kt`, `strings.xml` | S | — | UI/i18n |
| AUDIT-5-022 | Auto-hide contrôles masque loading | `feature/player/components/NetflixPlayerControls.kt` | S | — | Player |
| AUDIT-5-023 | Auth pas d'onboarding Jellyfin/Xtream | `feature/auth/AuthScreen.kt` | M | — | UI |
| AUDIT-5-024 | Auth token field inaccessible D-pad | `feature/auth/AuthScreen.kt` | S | — | UI |
| AUDIT-5-026 | SettingsGridScreen 2 rows 4+3 Spacer crée trou focus | `feature/settings/SettingsGridScreen.kt` | S | — | UI |
| AUDIT-5-027 | AppProfileSelection Add profile focus pas garanti | `feature/appprofile/AppProfileSelectionScreen.kt` | S | — | UI |
| AUDIT-5-030 | NetflixMediaCard 14sp hardcodé | `core/ui/NetflixMediaCard.kt` | S | AUDIT-5-029 | Design |
| AUDIT-5-031 | Skeletons padding hardcodé 48dp | `core/ui/Skeletons.kt` | S | AUDIT-5-028 | Design |
| AUDIT-5-032 | NetflixTopBar RIGHT bound manquant | `core/ui/NetflixTopBar.kt` | S | — | UI |
| AUDIT-5-033 | HandleErrors Snackbar non-focalisable TV | `core/ui/HandleErrors.kt`, `core/ui/ErrorSnackbarHost.kt` | M | — | UI |
| AUDIT-3-013 | NetflixMediaCard overdraw (dup sprint 2 perf, refactor UX) | `core/ui/NetflixMediaCard.kt` | S | — | UI |
| AUDIT-3-018 | Shimmer skeleton infinite anim off-screen | `core/ui/Skeletons.kt`, `core/ui/CinemaGoldComponents.kt` | S | — | UI |
| AUDIT-3-019 | NetflixContentRow remember lambdas par item | `core/ui/NetflixContentRow.kt` | S | — | Compose |
| AUDIT-3-020 | Staggered row animations au premier frame home | `feature/home/DiscoverScreenComponents.kt` | S | — | UI |
| AUDIT-7-011 | contentDescription partiel sur core/ui | `core/ui/**` | M | — | Accessibility |
| AUDIT-7-012 | TalkBack non testé | device test | M | AUDIT-7-011 | Accessibility |

**Exit criteria Sprint 3** :
- Home + Library + Details + Settings naviguent sans focus perdu, sans snap parasite, < 100 ms.
- Toute chaîne UI visible passe `stringResource` en FR et EN.
- Error state unifié, non-Snackbar, focalisable.
- OverscanSafeArea appliqué partout, typographies respectent le minimum 14 sp.

---

## Sprint 4 — Production Release (1-2 semaines)

Objectif : conforme Play Store, zéro violation StrictMode/LeakCanary, tests sur Mi Box S et Chromecast w/ GTV.

| ID finding | Titre | Fichier(s) | Effort | Dépendances | Owner |
|---|---|---|---|---|---|
| AUDIT-7-002 | Privacy policy URL publiée | Repo public + Play Console | S | — | Product |
| AUDIT-7-003 | Data Safety form rempli | Play Console | S | AUDIT-7-002 | Product |
| AUDIT-7-004 | Content rating questionnaire | Play Console | S | — | Product |
| AUDIT-7-005 | App category | Play Console | S | — | Product |
| AUDIT-7-006 | `android:isGame="false"` | `AndroidManifest.xml` | S | — | Build |
| AUDIT-7-007 | `uses-feature android.software.leanback required="true"` | `AndroidManifest.xml` | S | — | Build |
| AUDIT-7-008 | TV recommendation rows complet (cablage feature 2 phase 6) | `data/util/TvChannelManagerImpl.kt`, `work/ChannelSyncWorker.kt` | M | — | Feature |
| AUDIT-7-009 | ANR watchdog custom | `PlexHubApplication.kt` | S | — | Core |
| AUDIT-7-010 | Couverture analytics events critiques | `domain/service/AnalyticsService.kt`, VMs | M | — | Analytics |
| AUDIT-7-013 | Locale propagation dates/nombres | `core/common/ContentUtils.kt` | S | — | i18n |
| AUDIT-7-014 | Tests sur device réel (Mi Box S + CCwGTV) | smoke checklist | M | Sprint 1-3 | QA |
| AUDIT-7-015 | Tests connexion lente / offline | QA checklist | M | — | QA |
| AUDIT-7-016 | Tests multi-serveur | QA checklist | M | — | QA |
| AUDIT-7-018 | Firebase Test Lab CI | `.github/workflows/**` | M | AUDIT-7-014 | CI |
| AUDIT-2-009 | BuildConfig.API_BASE_URL = plex.tv en release | `app/build.gradle.kts` | S | — | Build |
| AUDIT-2-010 | Dream Service exported=true | `AndroidManifest.xml`, `feature/screensaver/PlexHubDreamService.kt` | S | — | Security |
| AUDIT-4-016 | Ktlint/Detekt non appliqué dans core/ui, core/designsystem, core/navigation | `*/build.gradle.kts` | S | — | Build |
| AUDIT-4-017 | Ktlint ignoreFailures=true | `app/build.gradle.kts` + `config/ktlint/**` | S | AUDIT-4-016 | Build |
| AUDIT-4-018 | Package core.di dans core:common | `core/common/**` refactor | M | — | Architecture |
| AUDIT-4-019 | 25 repos tous @Singleton sans analyse | `data/di/**` | M | — | Architecture |
| AUDIT-4-021 | libs.versions.toml bleeding edge | `gradle/libs.versions.toml` | M | — | Build |
| AUDIT-4-022 | Commentaire ABI contradictoire (dup) | `app/build.gradle.kts` | S | AUDIT-1-006 | Build |
| AUDIT-4-023 | Repositories lourds (12 deps) | `data/repository/LibraryRepositoryImpl.kt`, `HubsRepositoryImpl.kt`, `domain/usecase/EnrichMediaItemUseCase.kt` | L | AUDIT-4-019 | Architecture |
| AUDIT-4-024 | Absence de Konsist / ArchUnit | `:core:testing` ou `:app` test | M | AUDIT-4-002, AUDIT-4-003 | Architecture |
| AUDIT-4-025 | Gradle config-cache non testé | CI, `gradle.properties` | S | AUDIT-4-001 | Build |
| AUDIT-1-009 | ConnectionManager NetworkCallback jamais unregistré | `core/network/ConnectionManager.kt` | S | — | Network |
| AUDIT-1-010 | SecurePreferencesManager init lazy sans lock | `core/datastore/SecurePreferencesManager.kt` | S | — | Security |
| AUDIT-1-011 | WorkManager 4 periodicWork sans check | `PlexHubApplication.kt` | S | — | Core |
| AUDIT-1-012 | MediaDetailViewModel error strings non i18n | `feature/details/MediaDetailViewModel.kt` | S | AUDIT-5-007 | i18n |
| AUDIT-1-014 | SettingsViewModel collect WorkManager sans scope cancellation | `feature/settings/SettingsViewModel.kt` | S | — | Core |
| AUDIT-1-015 | ApkInstaller CoroutineScope sans SupervisorJob | `core/update/ApkInstaller.kt` | S | AUDIT-2-008 | Core |
| AUDIT-1-017 | Main dispatcher non immediate | `core/common/CoroutineModule.kt` | S | — | Core |
| AUDIT-3-022 | ExoPlayer tunneling sur Mi Box S | `feature/player/PlayerFactory.kt` | S | AUDIT-7-014 | Player |
| AUDIT-3-023 | FGS notification bar saturation | `PlexHubApplication.kt`, `work/**` | S | AUDIT-3-014 | Core |
| AUDIT-3-024 | Room queryCallback installé | `core/database/DatabaseModule.kt` | S | — | DB |
| AUDIT-4-005 | PagingData exposé dans :domain | `domain/repository/**`, `domain/usecase/**` | M | AUDIT-4-002 | Architecture |
| AUDIT-4-006 | :core:model dépend de compose runtime-annotation | `core/model/build.gradle.kts` | S | — | Architecture |
| AUDIT-4-007 | Use case impl dans :data | `domain/usecase/ResolveEpisodeSourcesUseCase.kt`, `data/usecase/ResolveEpisodeSourcesUseCaseImpl.kt` | S | — | Architecture |
| AUDIT-4-008 | Use cases pass-through | 10+ use cases | M | — | Architecture |
| AUDIT-4-009 | @Singleton sur use cases stateless | `domain/usecase/**`, `data/di/**` | S | — | Architecture |
| AUDIT-4-010 | BaseViewModel adoption 17/37 | `feature/**/*ViewModel.kt` | M | — | Architecture |
| AUDIT-4-011 | Deux classes MainViewModel | `app/MainViewModel.kt`, `feature/main/MainViewModel.kt` | S | — | Architecture |
| AUDIT-4-012 | Mélange try/catch + Result | `data/repository/**` | M | AUDIT-4-010 | Architecture |
| AUDIT-4-013 | Stratégie de caching non documentée | `data/repository/**`, docs | S | — | Docs |

**Exit criteria Sprint 4** :
- Play Console interne accepte l'upload (versionCode, signing, compliance formulaires remplis).
- Smoke test QA manuel Mi Box S + Chromecast w/ GTV : login, navigation, lecture HEVC 4K, search, multi-source, offline, déconnexion.
- StrictMode active en debug : zéro violation.
- LeakCanary : zéro leak détecté après un scénario typique (30 min d'usage).
- Firebase Test Lab robo test passe 100 %.

---

## Sprint 5+ — Features Premium (ongoing)

Objectif : différenciation vs Plex / Jellyfin officiels, arguments store.

| # | Feature (Phase 6) | Effort | Dépendances | Owner |
|---|---|---|---|---|
| 1 | **Mode enfant complet** (filtrage par rating, PIN, thème kids) | M | Sprint 3, `FilterContentByAgeUseCase`, `ParentalPinDialog` | Feature |
| 2 | **Android TV Channels / Recommendations** (cablage complet On Deck + Resume + Recommended) | S | AUDIT-7-008, `TvChannelManagerImpl`, `ChannelSyncWorker` | Feature |
| 3 | **Statistiques visionnage** (heures, genres, séries, Wrapped) | M | `GetWatchHistoryUseCase`, Room agrégation | Feature |
| 4 | **Profils avatars enrichis** (24 emojis + 8 couleurs fond) | S | `ProfileEntity` migration Room 47→48 | Feature |
| 5 | **Screensaver dynamique** (mosaic / ken-burns / carousel) | M | `PlexHubDreamService`, `ScreensaverViewModel`, Coil | Feature |

Plus tard (non retenus ici) :
- Continue Watching cross-device (bloqué par Jellyfin/Xtream unification).
- Moteur de recommandations ML embarqué.
- Version freemium + Play Billing (après 2-3 features premium livrées).

**Exit criteria Sprint 5+** :
- Au moins 2 features du top 5 livrées et mesurées (store screenshots, retention D7).
- ROI validé sur la prochaine cohorte release.

---

## Dépendances globales (graphe synthétique)

```
Sprint 1 (stabilité)
  ├─ AUDIT-1-002 ─┬─> AUDIT-2-004 ─> AUDIT-7-001
  │               └─> versionCode (AUDIT-1-003)
  ├─ AUDIT-1-001 (Xtream)
  ├─ AUDIT-3-001 (ExoPlayer buffer) ─┐
  ├─ AUDIT-3-005 (init 5 jobs) ─┐     │
  │                             ↓     │
  ├─ AUDIT-3-006 (Baseline Profile)   │
  ├─ AUDIT-3-003 (media 20+ index) ──> AUDIT-3-011, AUDIT-3-012
  ├─ AUDIT-4-001 (nested android{})    │
  ├─ AUDIT-4-002 (:domain) ──> AUDIT-4-005, AUDIT-4-024
  ├─ AUDIT-4-014 (Turbine) ──> AUDIT-4-015 ──> AUDIT-4-020
  └─ AUDIT-1-016 (onTrimMemory) <─ AUDIT-3-001, AUDIT-3-002

Sprint 2 (security + perf)
  ├─ AUDIT-2-001 ≡ AUDIT-1-004 (HEADERS logs)
  ├─ AUDIT-2-002 (Timber URLs) ──> AUDIT-1-018
  ├─ AUDIT-2-003 (cert pinning) ──> AUDIT-2-011 ──> AUDIT-2-008 ──> AUDIT-2-012
  ├─ AUDIT-3-002 (Coil cache) ──> AUDIT-3-007
  ├─ AUDIT-3-010 (player OkHttp) ──> AUDIT-3-015
  └─ AUDIT-3-008 (List<> params) ──> AUDIT-3-021

Sprint 3 (UX)
  ├─ AUDIT-5-002 ──> AUDIT-5-006
  ├─ AUDIT-5-007 ──> AUDIT-5-008, AUDIT-1-012
  ├─ AUDIT-5-013 ──> AUDIT-5-014
  ├─ AUDIT-5-028 ──> AUDIT-5-031
  └─ AUDIT-5-029 ──> AUDIT-5-030, AUDIT-5-011

Sprint 4 (release)
  ├─ AUDIT-7-002 ──> AUDIT-7-003
  ├─ AUDIT-7-014 ──> AUDIT-7-018, AUDIT-3-022
  ├─ AUDIT-4-016 ──> AUDIT-4-017
  ├─ AUDIT-4-019 ──> AUDIT-4-023
  └─ AUDIT-4-024 <── AUDIT-4-002, AUDIT-4-003, AUDIT-4-018

Sprint 5+ (features)
  └─ Mode enfant <── AUDIT-3-003 (requête SQL optimisée), Sprint 3 (UI i18n)
```

---

## Estimation calendrier brute

| Sprint | Durée estimée | Charge équivalente 1 dev |
|---|---|---|
| Sprint 1 | 2 semaines | 12 j (18 findings, 3 L + 4 M + 11 S) |
| Sprint 2 | 2 semaines | 14 j (30 findings dont 1 L + 7 M + 22 S) |
| Sprint 3 | 3 semaines | 18 j (35 findings dont 8 M + 27 S) |
| Sprint 4 | 2 semaines | 13 j (37 findings dont 1 L + 9 M + 27 S) |
| **Sous-total Sprints 1-4** | **9 semaines** | **~57 j dev** |
| Sprint 5+ (features) | ongoing | 5-10 j par feature |

En supposant une équipe de 2 devs + 1 QA en parallèle, Sprint 1-4 tient en **~5-6 semaines calendaires**. Avec 1 seul dev, compter **9-10 semaines**.

**Time-to-release minimale (alpha interne Play Store)** : fin Sprint 4 = ~9 semaines.
**Time-to-release beta publique** : + 2 semaines de stabilisation QA.
**Time-to-release production** : + 2 semaines de bugfix post-beta = **~13 semaines au total**.
