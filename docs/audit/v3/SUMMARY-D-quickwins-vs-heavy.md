# Sortie D — Quick wins vs Heavy

Trois catégories pour faciliter la planification par créneaux de temps disponibles.

- **Quick wins** : < 1 jour, effort `S`. 87 items. Beaucoup de ROI pour peu d'effort.
- **Medium** : 1-3 jours, effort `M`. 46 items. Chantiers focalisés par domaine.
- **Heavy** : > 3 jours, effort `L`/`XL`. 4 items. Refactor + tests de masse.

---

## Quick wins (< 1 jour, effort S)

| ID | Titre | Fichier | Impact |
|---|---|---|---|
| AUDIT-1-001 | Xtream mapper pageOffset fix | `data/mapper/XtreamMediaMapper.kt` | Critique — débloque bibliothèque Xtream |
| AUDIT-1-002 | Fail release if debug keystore | `app/build.gradle.kts` | Critique — empêche OTA break |
| AUDIT-1-003 | versionCode incrementable | `app/build.gradle.kts` | Critique — débloque Play Store |
| AUDIT-1-005 | PlayerControlViewModel validate args | `app/feature/player/PlayerControlViewModel.kt` | Élevé — fixe écran noir deep link |
| AUDIT-1-006 | Remove x86 ABI | `app/build.gradle.kts` | Élevé — APK -60 % |
| AUDIT-1-007 | DataStore corruptionHandler | `core/datastore/DataStoreExtensions.kt` | Moyen — no crash loop |
| AUDIT-1-008 | LibrarySyncWorker POST_NOTIFICATIONS | `work/LibrarySyncWorker.kt` | Moyen |
| AUDIT-1-009 | ConnectionManager unregister callback | `core/network/ConnectionManager.kt` | Moyen — memory |
| AUDIT-1-010 | SecurePreferencesManager threadsafe | `core/datastore/SecurePreferencesManager.kt` | Moyen |
| AUDIT-1-011 | WorkManager enqueue guard | `PlexHubApplication.kt` | Faible |
| AUDIT-1-014 | SettingsViewModel cancel collectors | `feature/settings/SettingsViewModel.kt` | Faible |
| AUDIT-1-015 | ApkInstaller SupervisorJob | `core/update/ApkInstaller.kt` | Moyen |
| AUDIT-1-017 | Main.immediate dispatcher | `core/common/CoroutineModule.kt` | Faible |
| AUDIT-1-018 | Scrub logs LibrarySyncWorker | `work/LibrarySyncWorker.kt` | Élevé — security |
| AUDIT-2-001 / 1-004 | Redact X-Plex-Token logging | `core/network/NetworkModule.kt` | Élevé — security |
| AUDIT-2-002 | Scrub Timber URLs | `data/source/PlexSourceHandler.kt`, `LibrarySyncWorker.kt`, `AuthInterceptor.kt` | Élevé — security |
| AUDIT-2-006 | Tighten cleartext Network Security Config | `app/src/main/res/xml/network_security_config.xml` | Moyen |
| AUDIT-2-007 | ProGuard shrink DTOs | `app/proguard-rules.pro` | Moyen — APK size |
| AUDIT-2-009 | Remove dead `BuildConfig.API_BASE_URL` | `app/build.gradle.kts` | Faible |
| AUDIT-2-010 | Dream Service exported review | `AndroidManifest.xml` | Faible |
| AUDIT-2-011 | UpdateChecker TLS pin | `core/update/UpdateChecker.kt` | Élevé — security |
| AUDIT-2-013 | AuthInterceptor TokenInvalid any 401 | `core/network/AuthInterceptor.kt` | Moyen |
| AUDIT-2-014 | Crashlytics remove PII keys | `app/di/AnalyticsModule.kt` | Moyen — privacy |
| AUDIT-3-001 | LoadControl adaptatif | `feature/player/PlayerFactory.kt` | Critique — fix OOM 4K |
| AUDIT-3-002 | Coil memoryCache tuning | `PlexHubApplication.kt` | Élevé |
| AUDIT-3-004 | Home scroll-on-focus fix | `feature/home/NetflixHomeScreen.kt` | Élevé |
| AUDIT-3-005 | Defer init jobs | `PlexHubApplication.kt` | Moyen |
| AUDIT-3-007 | Coil allowHardware false | `PlexHubApplication.kt` | Moyen |
| AUDIT-3-008 | Immutable Compose params | `feature/home`, `core/ui` | Moyen |
| AUDIT-3-009 | ABI splits enable | `app/build.gradle.kts` | Moyen |
| AUDIT-3-013 | NetflixMediaCard simplify draw | `core/ui/NetflixMediaCard.kt` | Faible |
| AUDIT-3-014 | Reduce periodic workers | `PlexHubApplication.kt`, `work/**` | Faible |
| AUDIT-3-018 | Pause shimmer off-screen | `core/ui/Skeletons.kt` | Faible |
| AUDIT-3-019 | Stable lambdas NetflixContentRow | `core/ui/NetflixContentRow.kt` | Faible |
| AUDIT-3-020 | Disable first-frame animations | `feature/home/DiscoverScreenComponents.kt` | Faible |
| AUDIT-3-021 | Compose compiler metrics | `app/build.gradle.kts` | Faible — tooling |
| AUDIT-3-022 | ExoPlayer tunneling device check | `feature/player/PlayerFactory.kt` | Faible |
| AUDIT-3-023 | FGS notification less noise | `PlexHubApplication.kt`, `work/**` | Faible |
| AUDIT-3-024 | Room queryCallback remove | `core/database/DatabaseModule.kt` | Faible |
| AUDIT-4-001 | Fix nested `android{}` in core:network | `core/network/build.gradle.kts` | Élevé — build |
| AUDIT-4-003 | Move GetSuggestionsUseCase | `app → domain/usecase/` | Moyen — architecture |
| AUDIT-4-004 | Move MediaUrlResolver to data.util | `core.util → data.util` | Moyen |
| AUDIT-4-006 | Remove compose anno from `:core:model` | `core/model/build.gradle.kts` | Faible |
| AUDIT-4-007 | Move ResolveEpisodeSources impl | `data/usecase/ → domain/usecase/` | Faible |
| AUDIT-4-009 | Remove `@Singleton` stateless use cases | `domain/usecase/**`, `data/di/**` | Faible |
| AUDIT-4-011 | Rename one MainViewModel | `app/MainViewModel.kt` | Faible |
| AUDIT-4-014 | Add Turbine to `libs.versions.toml` | `gradle/libs.versions.toml` | Moyen — tests |
| AUDIT-4-016 | Apply ktlint/detekt all modules | `*/build.gradle.kts` | Moyen |
| AUDIT-4-017 | ktlint fail CI | `app/build.gradle.kts` | Moyen |
| AUDIT-4-022 | Fix ABI comment | `app/build.gradle.kts` | Faible |
| AUDIT-4-025 | Enable configuration-cache | `gradle.properties` | Faible — perf build |
| AUDIT-5-001 | Home scroll snap fix | `feature/home/NetflixHomeScreen.kt` | Élevé |
| AUDIT-5-002 / 003 / 006 | i18n Home strings | `feature/home/**`, `strings.xml` | Élevé |
| AUDIT-5-005 | Home initial focus robust | `feature/home/NetflixHomeScreen.kt` | Moyen |
| AUDIT-5-007 / 008 | i18n Detail strings | `feature/details/NetflixDetailScreen.kt`, `strings.xml` | Élevé |
| AUDIT-5-010 | ExpandableSummary focus indicator | `feature/details/NetflixDetailScreen.kt` | Faible |
| AUDIT-5-011 | Detail poster size | `feature/details/NetflixDetailScreen.kt` | Moyen |
| AUDIT-5-012 | CastRow stable key | `feature/details/NetflixDetailScreen.kt` | Moyen |
| AUDIT-5-014 | Library `pendingScrollRestore` race | `feature/library/LibrariesScreen.kt` | Moyen |
| AUDIT-5-015 | Library chips FlowRow | `feature/library/LibraryComponents.kt` | Faible |
| AUDIT-5-016 | Library refresh focus visible | `feature/library/LibrariesScreen.kt` | Faible |
| AUDIT-5-017 | Search BackHandler | `feature/search/NetflixSearchScreen.kt` | Moyen |
| AUDIT-5-018 | Search strings consistency | `feature/search/NetflixSearchScreen.kt` | Faible |
| AUDIT-5-019 | Search D-pad shortcut | `feature/search/NetflixSearchScreen.kt` | Faible |
| AUDIT-5-020 | Player `KEYCODE_MEDIA_STOP` | `feature/player/VideoPlayerScreen.kt` | Moyen |
| AUDIT-5-021 | i18n Player dialogs | `feature/player/ui/components/PlayerSettingsDialog.kt` | Moyen |
| AUDIT-5-022 | Auto-hide respect buffering | `feature/player/components/NetflixPlayerControls.kt` | Moyen |
| AUDIT-5-024 | Auth token field D-pad | `feature/auth/AuthScreen.kt` | Moyen |
| AUDIT-5-025 | LoadingScreen Exit focusRequester | `feature/loading/LoadingScreen.kt` | Faible |
| AUDIT-5-026 | SettingsGrid focus hole | `feature/settings/SettingsGridScreen.kt` | Moyen |
| AUDIT-5-027 | AppProfileSelection Add focus | `feature/appprofile/AppProfileSelectionScreen.kt` | Moyen |
| AUDIT-5-029 | Typography ≥ 14sp | `core/designsystem/Type.kt` | Critique |
| AUDIT-5-030 | NetflixMediaCard theme sizes | `core/ui/NetflixMediaCard.kt` | Moyen |
| AUDIT-5-031 | Skeletons padding configurable | `core/ui/Skeletons.kt` | Faible |
| AUDIT-5-032 | NetflixTopBar RIGHT bound | `core/ui/NetflixTopBar.kt` | Faible |
| AUDIT-7-001 | Mapping file archive CI | `.github/workflows/**` | Moyen |
| AUDIT-7-002 | Privacy policy URL | `docs/` + hosting | Critique — Play Store |
| AUDIT-7-003 | Data Safety form | Play Console | Critique |
| AUDIT-7-004 | Content rating questionnaire | Play Console | Critique |
| AUDIT-7-005 | App category | Play Console | Moyen |
| AUDIT-7-006 | `android:isGame=false` | `AndroidManifest.xml` | Faible |
| AUDIT-7-007 | `uses-feature leanback required=true` | `AndroidManifest.xml` | Moyen |
| AUDIT-7-009 | ANR watchdog API 28 | `PlexHubApplication.kt` | Moyen |
| AUDIT-7-013 | Locale propagation dates | `core/common/ContentUtils.kt` | Faible |
| AUDIT-7-017 | StrictMode debug | `PlexHubApplication.kt` | Moyen |
| FEATURE-6-002 | Android TV Channels câblage | `data/util/TvChannelManagerImpl.kt` | Élevé — marketing |
| FEATURE-6-004 | Avatars enrichis | `feature/appprofile/ProfileFormDialog.kt` | Moyen — marketing |

---

## Medium (1-3 jours, effort M)

| ID | Titre | Fichier | Impact | Dépendances |
|---|---|---|---|---|
| AUDIT-1-013 | PlayerController scope review | `feature/player/controller/PlayerController.kt` | Moyen | — |
| AUDIT-1-016 | `onTrimMemory` implementation | `PlexHubApplication.kt`, ViewModels | Élevé | AUDIT-3-001/002 |
| AUDIT-2-003 | Certificate pinning | `core/network/NetworkModule.kt` | Élevé — security | — |
| AUDIT-2-005 | Encrypt `connectionCacheStore` | `core/datastore/SettingsDataStore.kt` | Élevé — security | — |
| AUDIT-2-008 | APK SHA256 verification | `core/update/ApkInstaller.kt`, `UpdateChecker.kt` | Critique — security | AUDIT-2-011 |
| AUDIT-2-012 | Remove or whitelist REQUEST_INSTALL_PACKAGES | `AndroidManifest.xml`, `ApkInstaller.kt` | Critique | AUDIT-2-008 |
| AUDIT-3-003 | Reduce `media` table index count | `core/database/MediaEntity.kt`, migrations | Élevé | AUDIT-3-012 |
| AUDIT-3-006 | Baseline Profile module | `:baselineprofile` | Élevé | AUDIT-3-005 |
| AUDIT-3-010 | Share OkHttpClient player | `feature/player/PlayerFactory.kt` | Moyen | AUDIT-3-015 |
| AUDIT-3-011 | Index composite query builder | `data/repository/MediaLibraryQueryBuilder.kt` | Moyen | AUDIT-3-003 |
| AUDIT-3-015 | OkHttp DNS cache happy eyeballs | `core/network/NetworkModule.kt` | Moyen | — |
| AUDIT-3-016 | Gson → kotlinx.serialization | `core/network/NetworkModule.kt` | Moyen | — |
| AUDIT-3-017 | FTS5 + rank searchMedia | `core/database/dao/MediaDao.kt`, migrations | Moyen | AUDIT-3-012 |
| AUDIT-4-002 | Migrate `:domain` to JVM-only | `domain/build.gradle.kts` | Élevé — architecture | AUDIT-4-024 |
| AUDIT-4-005 | Wrap PagingData in `:domain` | `domain/repository/**` | Moyen | AUDIT-4-002 |
| AUDIT-4-008 | Remove pass-through use cases | 10+ use cases | Faible | — |
| AUDIT-4-010 | Unify BaseViewModel adoption | `feature/**/*ViewModel.kt` | Moyen | — |
| AUDIT-4-012 | Unify error handling policy | `data/repository/**` | Moyen | AUDIT-4-010 |
| AUDIT-4-013 | Document caching strategy | `data/repository/**`, docs | Faible | — |
| AUDIT-4-018 | Move `core.di` package | `core/common/**` | Moyen | AUDIT-4-004 |
| AUDIT-4-019 | Review repo scopes | `data/di/**` | Faible | — |
| AUDIT-4-021 | Pin stable `libs.versions.toml` | `gradle/libs.versions.toml` | Moyen | — |
| AUDIT-4-024 | Konsist / ArchUnit rules | `:core:testing` | Élevé | AUDIT-4-002, 003, 004, 018 |
| AUDIT-5-004 | HomeHeader clickable actions | `core/ui/HomeHeader.kt` | Moyen | — |
| AUDIT-5-009 | NetflixDetailTabs focus restoration | `feature/details/NetflixDetailTabs.kt` | Moyen | — |
| AUDIT-5-013 | Library focus restoration sync | `feature/library/LibrariesScreen.kt` | Moyen | AUDIT-5-014 |
| AUDIT-5-023 | Auth Jellyfin/Xtream onboarding | `feature/auth/AuthScreen.kt` | Moyen | — |
| AUDIT-5-028 | Apply OverscanSafeArea globally | `core/ui/OverscanSafeArea.kt`, routes | Critique | — |
| AUDIT-5-033 | HandleErrors dialog TV | `core/ui/HandleErrors.kt` | Moyen | — |
| AUDIT-7-008 | TV recommendation rows complete | `data/util/TvChannelManagerImpl.kt` | Moyen | — |
| AUDIT-7-010 | Analytics events coverage | `domain/service/AnalyticsService.kt` | Moyen | — |
| AUDIT-7-011 | contentDescription core/ui | `core/ui/**` | Moyen | — |
| AUDIT-7-012 | TalkBack device test | QA | Moyen | AUDIT-7-011 |
| AUDIT-7-014 | QA device test Mi Box S | QA | Critique | Sprint 1-3 |
| AUDIT-7-015 | QA slow / offline | QA | Moyen | — |
| AUDIT-7-016 | QA multi-serveur | QA | Moyen | — |
| AUDIT-7-018 | Firebase Test Lab CI | `.github/workflows/**` | Moyen | AUDIT-7-014 |
| FEATURE-6-001 | Mode enfant complet | `feature/home/HomeViewModel.kt`, `LibraryViewModel.kt`, `data/repository/MediaLibraryQueryBuilder.kt` | Élevé — sales | AUDIT-3-003 |
| FEATURE-6-003 | Stats visionnage | `feature/stats/**`, `domain/usecase/**` | Moyen — sales | — |
| FEATURE-6-005 | Screensaver dynamique | `feature/screensaver/**` | Moyen — sales | — |

---

## Heavy (> 3 jours, effort L)

| ID | Titre | Fichier | Impact | Dépendances |
|---|---|---|---|---|
| AUDIT-3-012 | Test Room migrations on peuplated DB | `core/database/**`, migrations tests | Élevé | AUDIT-3-003, AUDIT-4-015 |
| AUDIT-4-015 | Add tests `core:database/datastore/ui` | `*/src/test/**` | Élevé | AUDIT-4-014 |
| AUDIT-4-020 | Restore test baseline 60 → 150+ | `*/src/test/**` | Élevé | AUDIT-4-014, AUDIT-4-015 |
| AUDIT-4-023 | Split heavy repositories | `data/repository/LibraryRepositoryImpl.kt`, `HubsRepositoryImpl.kt`, `domain/usecase/EnrichMediaItemUseCase.kt` | Moyen | AUDIT-4-019 |
