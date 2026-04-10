# Sortie C — Backlog ticket-ready (137 items)

Backlog complet, un ticket par ligne. Colonnes : `ID source` · `Titre ticket` · `Fichier(s) à modifier` · `Effort estimé` · `Dépendances (IDs prérequis)` · `Critères d'acceptation`.

Effort légende : **S** < 1 j · **M** 1-3 j · **L** 3-7 j · **XL** > 7 j.

---

## Phase 1 — Stability (18)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-1-001 | Fix Xtream mapper pageOffset UNIQUE INDEX wipe | `data/mapper/XtreamMediaMapper.kt`, `data/repository/XtreamVodRepositoryImpl.kt`, `XtreamSeriesRepositoryImpl.kt` | S | — | Test unit : 3 DTOs null-num insérés, 3 entités avec pageOffset distincts |
| AUDIT-1-002 | Fail release build if keystore missing | `app/build.gradle.kts` | S | — | `./gradlew assembleRelease` sans keystore → GradleException |
| AUDIT-1-003 | Incrementable versionCode strategy | `app/build.gradle.kts` | S | — | versionCode = 1016 puis auto-increment CI |
| AUDIT-1-004 | Redact X-Plex-Token in HttpLoggingInterceptor | `core/network/NetworkModule.kt` | S | — | `adb logcat` aucun token visible en debug |
| AUDIT-1-005 | Validate PlayerControlViewModel args in init | `app/feature/player/PlayerControlViewModel.kt`, `PlayerController.kt` | S | — | Deep link null args → UI erreur, pas d'écran noir |
| AUDIT-1-006 | Remove x86 ABI filters + AAB splits | `app/build.gradle.kts` | S | — | APK release ne contient que `lib/armeabi-v7a` + `arm64-v8a` |
| AUDIT-1-007 | DataStore corruptionHandler | `core/datastore/DataStoreExtensions.kt`, `DataStoreModule.kt` | S | — | Simuler `prefs_pb` corrompu → no crash, empty prefs |
| AUDIT-1-008 | LibrarySyncWorker POST_NOTIFICATIONS runtime check | `work/LibrarySyncWorker.kt` | S | — | Si permission denied → FGS sans notification |
| AUDIT-1-009 | Unregister ConnectionManager NetworkCallback | `core/network/ConnectionManager.kt` | S | — | Lifecycle-aware, unregister dans cleanup |
| AUDIT-1-010 | SecurePreferencesManager init lock | `core/datastore/SecurePreferencesManager.kt` | S | — | Init threadsafe via `lazy(SYNCHRONIZED)` |
| AUDIT-1-011 | Guard WorkManager periodicWork enqueue | `PlexHubApplication.kt` | S | — | Enqueue seulement si `librarySelectionComplete` |
| AUDIT-1-012 | i18n MediaDetailViewModel error strings | `feature/details/MediaDetailViewModel.kt` | S | AUDIT-5-007 | Toutes chaînes via `stringResource` |
| AUDIT-1-013 | PlayerController scope review (singleton vs activity) | `feature/player/controller/PlayerController.kt` | M | — | ExoPlayer release sur Player exit, pas juste pause |
| AUDIT-1-014 | SettingsViewModel cancel WorkManager collectors | `feature/settings/SettingsViewModel.kt` | S | — | Aucun orphan collector dans viewModelScope |
| AUDIT-1-015 | ApkInstaller SupervisorJob + error handler | `core/update/ApkInstaller.kt` | S | AUDIT-2-008 | Une erreur ne cancel pas le scope |
| AUDIT-1-016 | Implement onTrimMemory / TRIM_MEMORY_RUNNING_LOW | `PlexHubApplication.kt`, `HomeViewModel`, `MediaDetailViewModel` | M | AUDIT-3-001/002 | Flush Coil memoryCache, trim Room cache |
| AUDIT-1-017 | Use Dispatchers.Main.immediate | `core/common/CoroutineModule.kt` | S | — | `provideMainDispatcher` retourne Main.immediate |
| AUDIT-1-018 | Scrub URLs from LibrarySyncWorker logs | `work/LibrarySyncWorker.kt` | S | AUDIT-2-002 | Log sans token ni URL complète |

---

## Phase 2 — Security (14)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-2-001 | Redact tokens in HttpLoggingInterceptor (dup AUDIT-1-004) | `core/network/NetworkModule.kt` | S | — | logcat propre en debug |
| AUDIT-2-002 | Scrub URLs from Timber calls (data/, core/) | `data/source/PlexSourceHandler.kt`, `LibrarySyncWorker.kt`, `AuthInterceptor.kt` | S | — | `grep` debug logs : 0 token |
| AUDIT-2-003 | Certificate pinning (plex.tv, tmdb, omdb, opensubtitles) | `core/network/NetworkModule.kt` | M | — | CertificatePinner configuré et fonctionnel |
| AUDIT-2-004 | Fail release build if debug signing (dup AUDIT-1-002) | `app/build.gradle.kts` | S | AUDIT-1-002 | Échec explicite |
| AUDIT-2-005 | Encrypt connectionCacheStore | `core/datastore/SettingsDataStore.kt`, `ConnectionManager.kt` | M | — | Utiliser SecurePreferencesManager ou DataStore chiffré |
| AUDIT-2-006 | Tighten Network Security Config cleartext | `app/src/main/res/xml/network_security_config.xml` | S | — | cleartext autorisé uniquement LAN explicitement |
| AUDIT-2-007 | ProGuard shrink DTOs `core.network.model.**` | `app/proguard-rules.pro` | S | — | `-keep` remplacé par `@SerializedName`-based keep |
| AUDIT-2-008 | Verify APK SHA256 before install | `core/update/ApkInstaller.kt`, `UpdateChecker.kt` | M | AUDIT-2-011 | Hash GitHub release vs téléchargé |
| AUDIT-2-009 | Remove BuildConfig.API_BASE_URL dead code | `app/build.gradle.kts` | S | — | Champ supprimé |
| AUDIT-2-010 | Dream Service exported review | `AndroidManifest.xml` | S | — | `exported=false` ou permission custom |
| AUDIT-2-011 | UpdateChecker TLS pin + response integrity | `core/update/UpdateChecker.kt` | S | AUDIT-2-003 | `api.github.com` pinné |
| AUDIT-2-012 | Remove REQUEST_INSTALL_PACKAGES or Play whitelist | `AndroidManifest.xml`, `core/update/ApkInstaller.kt` | M | AUDIT-2-008 | Permission retirée OU in-app updater via Play |
| AUDIT-2-013 | AuthInterceptor emit TokenInvalid on any 401 | `core/network/AuthInterceptor.kt`, `AuthEventBus.kt` | S | — | 401 local server déclenche aussi SessionExpired |
| AUDIT-2-014 | Crashlytics remove PII customKeys (ratingKey, serverId) | `app/di/AnalyticsModule.kt`, `handler/CrashReportingTree.kt` | S | — | `setCustomKey` sans PII |

---

## Phase 3 — Performance (24)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-3-001 | LoadControl adaptatif `isLowRamDevice` | `feature/player/PlayerFactory.kt` | S | — | PSS < 350 MB en 4K HEVC Mi Box S |
| AUDIT-3-002 | Coil memoryCache tuning Mi Box S | `PlexHubApplication.kt` | S | AUDIT-3-007 | Flush rate < 10 %/min scroll Home |
| AUDIT-3-003 | Reduce `media` table index count | `core/database/MediaEntity.kt`, migrations | M | AUDIT-3-012 | ≤ 12 index utiles, INSERT plus rapide |
| AUDIT-3-004 | Fix NetflixHomeContent scroll-on-focus | `feature/home/NetflixHomeScreen.kt` | S | — | D-pad horizontal ne déclenche pas `scrollToItem` |
| AUDIT-3-005 | Defer non-critical init jobs | `PlexHubApplication.kt` | S | AUDIT-3-006 | `appReady` signal dans < 800 ms |
| AUDIT-3-006 | Add :baselineprofile module | `:baselineprofile`, `app/build.gradle.kts` | M | AUDIT-3-005 | Cold start Mi Box S < 2.5 s |
| AUDIT-3-007 | Coil `allowHardware(false)` API < 26 | `PlexHubApplication.kt` | S | AUDIT-3-002 | Pas de crash Mali-450 bitmap |
| AUDIT-3-008 | Immutable Compose params (ImmutableList) | `feature/home/NetflixHomeScreen.kt`, `core/ui/NetflixContentRow.kt`, `SpotlightGrid.kt`, `feature/details/NetflixDetailScreen.kt` | S | AUDIT-3-021 | 100 % skippability sur rows |
| AUDIT-3-009 | Enable ABI splits (dup AUDIT-1-006) | `app/build.gradle.kts` | S | — | APK arm64 ≤ 35 MB |
| AUDIT-3-010 | Share OkHttpClient with player | `feature/player/PlayerFactory.kt`, `core/network/NetworkModule.kt` | M | AUDIT-3-015 | ConnectionPool partagé |
| AUDIT-3-011 | Index composite MediaLibraryQueryBuilder | `data/repository/MediaLibraryQueryBuilder.kt`, migrations | M | AUDIT-3-003 | Paging query < 80 ms p95 |
| AUDIT-3-012 | Test Room migrations on peuplated DB | `core/database/**`, migrations tests | L | AUDIT-3-003, AUDIT-4-015 | `MigrationTestHelper` sur 10k rows |
| AUDIT-3-013 | Simplify `NetflixMediaCard.drawWithContent` | `core/ui/NetflixMediaCard.kt` | S | — | Overdraw < 3x |
| AUDIT-3-014 | Consolidate WorkManager periodic workers | `PlexHubApplication.kt`, `work/**` | S | — | Moins de 2 réveils/h TV veille |
| AUDIT-3-015 | OkHttp DNS cache + happy eyeballs | `core/network/NetworkModule.kt` | M | — | Latence LAN cold < 100 ms |
| AUDIT-3-016 | Migrate converter Gson → kotlinx.serialization | `core/network/NetworkModule.kt`, DTOs | M | — | Suppression dep Gson, APK -2 MB |
| AUDIT-3-017 | FTS5 + rank for `MediaDao.searchMedia` | `core/database/dao/MediaDao.kt`, migrations | M | AUDIT-3-012 | Search < 100 ms sur 10k |
| AUDIT-3-018 | Pause infinite shimmer animations off-screen | `core/ui/Skeletons.kt`, `CinemaGoldComponents.kt` | S | — | Animations stoppées hors écran |
| AUDIT-3-019 | Stable lambdas in NetflixContentRow | `core/ui/NetflixContentRow.kt` | S | — | Skippability 100 % |
| AUDIT-3-020 | Disable first-frame row animations | `feature/home/DiscoverScreenComponents.kt` | S | — | Aucun jank initial > 16 ms |
| AUDIT-3-021 | Enable Compose compiler metrics | `app/build.gradle.kts`, `core/ui/build.gradle.kts` | S | AUDIT-3-008 | Report généré en CI |
| AUDIT-3-022 | Validate ExoPlayer tunneling on Mi Box S | `feature/player/PlayerFactory.kt` | S | AUDIT-7-014 | Tunneling off si bugs firmware |
| AUDIT-3-023 | Reduce foreground service notif noise | `PlexHubApplication.kt`, `work/**` | S | AUDIT-3-014 | 1 notif max pendant sync |
| AUDIT-3-024 | Remove Room queryCallback overhead | `core/database/DatabaseModule.kt` | S | — | `queryCallback = null` en release |

---

## Phase 4 — Architecture (25)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-4-001 | Fix nested `android{}` in core:network | `core/network/build.gradle.kts` | S | — | Bloc aplati, BuildConfigField visible |
| AUDIT-4-002 | Migrate `:domain` to JVM-only library | `domain/build.gradle.kts`, use cases | M | AUDIT-4-024 | `:domain` compile sans `android.*` |
| AUDIT-4-003 | Move GetSuggestionsUseCase to `:domain` | `app → domain/usecase/GetSuggestionsUseCase.kt` | S | AUDIT-4-002, AUDIT-4-004 | Aucun use case dans `:app` |
| AUDIT-4-004 | Move MediaUrlResolver to `:data/util` | `core.util → data.util.MediaUrlResolver` | S | — | Package = module |
| AUDIT-4-005 | Wrap PagingData in `:domain`-neutral type | `domain/repository/**`, `usecase/**` | M | AUDIT-4-002 | `:domain` sans `androidx.paging` |
| AUDIT-4-006 | Remove compose runtime-annotation from `:core:model` | `core/model/build.gradle.kts` | S | — | `:core:model` JVM pur |
| AUDIT-4-007 | Move ResolveEpisodeSourcesUseCaseImpl to `:domain` | `data/usecase/ → domain/usecase/` | S | — | Impl dans son propre module |
| AUDIT-4-008 | Remove pass-through use cases | 10+ use cases | M | — | Use cases avec logique réelle |
| AUDIT-4-009 | Remove `@Singleton` from stateless use cases | `domain/usecase/**`, `data/di/**` | S | — | Use cases non annotés |
| AUDIT-4-010 | Unify BaseViewModel adoption | `feature/**/*ViewModel.kt` | M | — | 37/37 héritent BaseViewModel OU zéro |
| AUDIT-4-011 | Rename one MainViewModel | `app/MainViewModel.kt`, `feature/main/MainViewModel.kt` | S | — | Un seul MainViewModel |
| AUDIT-4-012 | Unify error handling policy (`:data`) | `data/repository/**` | M | AUDIT-4-010 | Politique `Result<T>` documentée |
| AUDIT-4-013 | Document caching strategy | `data/repository/**`, docs | S | — | ADR cache Room + ApiCache |
| AUDIT-4-014 | Add Turbine to `libs.versions.toml` | `libs.versions.toml` | S | — | Flow tests via Turbine |
| AUDIT-4-015 | Add tests to core:database/datastore/ui | `*/src/test/**` | L | AUDIT-4-014 | Coverage > 40 % par module core |
| AUDIT-4-016 | Apply ktlint/detekt to all core modules | `*/build.gradle.kts` | S | — | Tous les modules lintés |
| AUDIT-4-017 | ktlint fail CI (`ignoreFailures = false`) | `app/build.gradle.kts`, `config/ktlint/**` | S | AUDIT-4-016 | CI rouge sur violation |
| AUDIT-4-018 | Move `core.di` package | `core/common/** → app/di` or split | M | AUDIT-4-004 | Package reflète module |
| AUDIT-4-019 | Review repo scopes (`@Singleton` audit) | `data/di/**` | M | — | Scopes justifiés par ADR |
| AUDIT-4-020 | Restore test baseline 60 tests → 150+ | `*/src/test/**` | L | AUDIT-4-014, AUDIT-4-015 | Coverage domain/data > 60 % |
| AUDIT-4-021 | Pin stable libs.versions.toml | `gradle/libs.versions.toml` | M | — | AGP 8.7, Kotlin 2.0, Compose BOM stable |
| AUDIT-4-022 | Fix contradictory ABI comment (dup AUDIT-1-006) | `app/build.gradle.kts` | S | AUDIT-1-006 | Commentaire exact |
| AUDIT-4-023 | Split heavy repositories (12 deps) | `data/repository/LibraryRepositoryImpl.kt`, `HubsRepositoryImpl.kt`, `domain/usecase/EnrichMediaItemUseCase.kt` | L | AUDIT-4-019 | Repos ≤ 6 deps |
| AUDIT-4-024 | Add Konsist / ArchUnit rules | `:core:testing` ou `:app` test | M | AUDIT-4-002, 003, 004, 018 | CI rouge sur violation archi |
| AUDIT-4-025 | Enable Gradle configuration cache | `gradle.properties` | S | AUDIT-4-001, AUDIT-4-021 | `--configuration-cache` passe |

---

## Phase 5 — UX (33)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-5-001 | Home remove scroll snap parasite | `feature/home/NetflixHomeScreen.kt`, `DiscoverScreen.kt` | S | AUDIT-3-004 | Autres rails visibles pendant navigation |
| AUDIT-5-002 | i18n Home EmptyState | `feature/home/NetflixHomeScreen.kt`, `strings.xml` | S | AUDIT-5-003 | Aucun texte anglais dur |
| AUDIT-5-003 | i18n Home rails titles | `feature/home/DiscoverScreenComponents.kt`, `strings.xml` | S | — | `stringResource` partout |
| AUDIT-5-004 | HomeHeader clickable actions | `core/ui/HomeHeader.kt` | M | — | Hero → Play / Info |
| AUDIT-5-005 | Home initial focus robust | `feature/home/NetflixHomeScreen.kt` | S | — | Focus sur premier item après compose |
| AUDIT-5-006 | i18n InitialSync/Error states | `feature/home/NetflixHomeScreen.kt`, `strings.xml` | S | AUDIT-5-002 | `stringResource` |
| AUDIT-5-007 | i18n NetflixDetailScreen strings | `feature/details/NetflixDetailScreen.kt`, `strings.xml` | S | — | Zéro texte dur |
| AUDIT-5-008 | i18n ActionButtonsRow Play/Loading | `feature/details/NetflixDetailScreen.kt` | S | AUDIT-5-007 | `stringResource` |
| AUDIT-5-009 | NetflixDetailTabs focus restoration | `feature/details/NetflixDetailTabs.kt` | M | — | Focus mémorisé par tab |
| AUDIT-5-010 | ExpandableSummary focus indicator | `feature/details/NetflixDetailScreen.kt` | S | — | Scale + border sur focus |
| AUDIT-5-011 | DetailHeroSection poster size | `feature/details/NetflixDetailScreen.kt` | S | AUDIT-5-029 | ≥ 140 dp large |
| AUDIT-5-012 | CastRow items key stable | `feature/details/NetflixDetailScreen.kt` | S | — | `key = actor.id` |
| AUDIT-5-013 | Library focus restoration sync | `feature/library/LibrariesScreen.kt`, `LibraryViewModel.kt` | M | AUDIT-5-014 | Focus restauré sans `delay` |
| AUDIT-5-014 | Library `pendingScrollRestore` race fix | `feature/library/LibrariesScreen.kt` | S | — | Race résolue |
| AUDIT-5-015 | Library filter chips wrap | `feature/library/LibraryComponents.kt` | S | — | `FlowRow` sur chips |
| AUDIT-5-016 | Library refresh IconButton focus | `feature/library/LibrariesScreen.kt` | S | — | Scale + border visible |
| AUDIT-5-017 | Search BackHandler clears query | `feature/search/NetflixSearchScreen.kt` | S | — | Back 1x → clear query |
| AUDIT-5-018 | Search state strings consistency | `feature/search/NetflixSearchScreen.kt`, `strings.xml` | S | — | Tous messages via `stringResource` |
| AUDIT-5-019 | Search D-pad shortcut to results | `feature/search/NetflixSearchScreen.kt` | S | — | RIGHT depuis keyboard focus résultats |
| AUDIT-5-020 | Player `KEYCODE_MEDIA_STOP` | `feature/player/VideoPlayerScreen.kt` | S | — | Stop géré, retour Home |
| AUDIT-5-021 | i18n Audio/Subtitle Sync dialogs | `feature/player/ui/components/PlayerSettingsDialog.kt`, `strings.xml` | S | — | `stringResource` |
| AUDIT-5-022 | Auto-hide delay respect buffering | `feature/player/components/NetflixPlayerControls.kt` | S | — | Pas de hide pendant buffering |
| AUDIT-5-023 | Auth Jellyfin/Xtream onboarding | `feature/auth/AuthScreen.kt` | M | — | Wizard dédié |
| AUDIT-5-024 | Auth token field D-pad accessible | `feature/auth/AuthScreen.kt` | S | — | OnScreen keyboard fallback |
| AUDIT-5-025 | LoadingScreen Exit focusRequester | `feature/loading/LoadingScreen.kt` | S | — | Exit focusable au retry |
| AUDIT-5-026 | SettingsGrid supprimer trou focus | `feature/settings/SettingsGridScreen.kt` | S | — | Pas de Spacer weight focalisable |
| AUDIT-5-027 | AppProfileSelection Add profile focus | `feature/appprofile/AppProfileSelectionScreen.kt` | S | — | Add button toujours focusable |
| AUDIT-5-028 | Apply OverscanSafeArea globally | `core/ui/OverscanSafeArea.kt`, all routes | M | — | Écrans wrappés, contenu non coupé |
| AUDIT-5-029 | Typography ≥ 14 sp | `core/designsystem/Type.kt`, `Dimensions.kt` | S | — | CardTitle 16sp min, body 14sp min |
| AUDIT-5-030 | NetflixMediaCard badge sizes via Type | `core/ui/NetflixMediaCard.kt` | S | AUDIT-5-029 | Lecture depuis theme |
| AUDIT-5-031 | Skeletons padding configurable | `core/ui/Skeletons.kt` | S | AUDIT-5-028 | Padding via theme |
| AUDIT-5-032 | NetflixTopBar RIGHT bound | `core/ui/NetflixTopBar.kt` | S | — | Focus trapped à droite |
| AUDIT-5-033 | HandleErrors dialog TV instead of Snackbar | `core/ui/HandleErrors.kt`, `ErrorSnackbarHost.kt` | M | — | Dialog focalisable |

---

## Phase 6 — Features (5)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| FEATURE-6-001 | Mode enfant complet (filtrage + PIN + thème) | `feature/home/HomeViewModel.kt`, `LibraryViewModel.kt`, `SearchViewModel.kt`, `data/repository/MediaLibraryQueryBuilder.kt`, `feature/appprofile/**` | M | AUDIT-3-003 | Kids profile ne voit jamais contenu > PG |
| FEATURE-6-002 | Android TV Channels complet | `data/util/TvChannelManagerImpl.kt`, `work/ChannelSyncWorker.kt` | S | AUDIT-7-008 | 3 channels visibles Home GTV |
| FEATURE-6-003 | Stats visionnage | `domain/usecase/GetWatchStatsUseCase.kt`, `feature/stats/**` | M | — | 4 métriques visibles, < 500 ms |
| FEATURE-6-004 | Profils avatars enrichis | `feature/appprofile/ProfileFormDialog.kt`, `core/database/ProfileEntity.kt`, migration 47→48 | S | — | 24 emojis + 8 couleurs |
| FEATURE-6-005 | Screensaver dynamique | `feature/screensaver/**` | M | — | 3 modes + settings |

---

## Phase 7 — Production gaps (18)

| ID | Titre ticket | Fichier(s) | Effort | Dépendances | Critères d'acceptation |
|---|---|---|---|---|---|
| AUDIT-7-001 | CI task archive mapping file | `.github/workflows/**`, `app/build.gradle.kts` | S | AUDIT-1-002 | `mapping.txt` archivé par build release |
| AUDIT-7-002 | Publish privacy policy URL | `docs/privacy-policy.md` + hosting | S | — | URL publique + lien Play Console |
| AUDIT-7-003 | Complete Data Safety form | Play Console | S | AUDIT-7-002 | Formulaire validé |
| AUDIT-7-004 | Content rating questionnaire | Play Console | S | — | IARC rating obtenu |
| AUDIT-7-005 | App category Play Console | Play Console | S | — | « Video Players » + « Entertainment » |
| AUDIT-7-006 | `android:isGame="false"` | `AndroidManifest.xml` | S | — | Attribut présent |
| AUDIT-7-007 | `uses-feature leanback required=true` | `AndroidManifest.xml` | S | — | Required true (si TV-only) |
| AUDIT-7-008 | Complete TV recommendation rows | `data/util/TvChannelManagerImpl.kt`, `work/ChannelSyncWorker.kt` | M | — | 3 channels populated |
| AUDIT-7-009 | ANR watchdog custom for API 28 | `PlexHubApplication.kt`, deps | S | — | ANRWatchDog ou équivalent |
| AUDIT-7-010 | Analytics events coverage critical actions | `domain/service/AnalyticsService.kt`, VMs | M | — | `play_start`, `search`, `source_switch` tracés |
| AUDIT-7-011 | contentDescription on core/ui components | `core/ui/**` | M | — | TalkBack-navigable |
| AUDIT-7-012 | TalkBack test on device | QA checklist | M | AUDIT-7-011 | Parcours principal fonctionne |
| AUDIT-7-013 | Locale propagation dates/numbers | `core/common/ContentUtils.kt` | S | — | Format FR/EN correct |
| AUDIT-7-014 | QA device test Mi Box S + CCwGTV | QA checklist | M | Sprint 1-3 | Smoke test passé |
| AUDIT-7-015 | QA connexion lente / offline | QA checklist | M | — | App utilisable offline |
| AUDIT-7-016 | QA multi-serveur | QA checklist | M | — | Plex+Jellyfin+Xtream simultanés OK |
| AUDIT-7-017 | StrictMode in debug | `PlexHubApplication.kt` | S | — | Zéro violation |
| AUDIT-7-018 | Firebase Test Lab CI | `.github/workflows/**` | M | AUDIT-7-014 | Robo test vert |

---

**Total backlog : 18 + 14 + 24 + 25 + 33 + 5 + 18 = 137 items**
