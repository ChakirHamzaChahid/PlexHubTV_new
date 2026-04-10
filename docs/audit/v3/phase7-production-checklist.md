# Phase 7 — Production Readiness Checklist (PlexHubTV)

> **Agent** : Release Agent (Agent 5)
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Méthode** : vérification statique directe (lecture `app/build.gradle.kts`, `AndroidManifest.xml`, `proguard-rules.pro`, `res/values*/`, `PlexHubApplication.kt`, dossiers `src/test/`, `src/androidTest/`).

Légende :
- ✅ Fait — vérifié dans le code
- ⚠️ Partiel — partiellement présent
- ❌ Non fait — absent
- 🔍 Non vérifiable statiquement — requiert un device ou la console Play

---

## 1. Build & Release

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Release signing configuré et fonctionnel | ⚠️ Partiel | **AUDIT-1-002** / **AUDIT-2-004** | `app/build.gradle.kts:35-49` crée un `signingConfigs.release` qui charge `keystore/keystore.properties` si présent ; mais `app/build.gradle.kts:75-81` retombe silencieusement sur `signingConfigs.debug` si le fichier est absent → un build CI sans keystore signe en debug sans erreur. |
| ProGuard/R8 minification + shrink sans crash | ⚠️ Partiel | **AUDIT-2-007** | `app/build.gradle.kts:68-74` active `isMinifyEnabled = true` + `isShrinkResources = true` + `proguard-android-optimize.txt` + `proguard-rules.pro`. Règles très permissives : `-keep class com.chakir.plexhubtv.core.network.model.**`, `-keep class com.chakir.plexhubtv.core.database.** { *; }`, `-keep class androidx.media3.** { *; }` → R8 efficace mais surface DTO exposée. Aucun build release n'a été produit dans le repo, donc 🔍 absence de crash en runtime non vérifiable. |
| Version code/name strategy (SemVer) | ❌ Non fait | **AUDIT-1-003** | `app/build.gradle.kts:27-28` : `versionCode = 1`, `versionName = "1.0.16"`. Aucune incrementation depuis 16 versions. Bloque tout upload Play Store. |
| ABI splits pour réduire la taille | ❌ Non fait | **AUDIT-1-006** / **AUDIT-3-009** / **AUDIT-4-022** | `app/build.gradle.kts:30-32` embarque les 4 ABI `armeabi-v7a, arm64-v8a, x86, x86_64` dans un APK universel. Commentaire `app/build.gradle.kts:131` ment ("ARM only, x86 excluded"). Pas d'`splits.abi`, pas d'AAB. APK ~3-4× plus gros (libmpv + ffmpeg natifs en double). |
| Mapping file archivé pour crash reports | 🔍 Non vérifiable statiquement | **AUDIT-7-001** *(nouveau)* | Crashlytics est intégré (`PlexHubApplication.kt:358`) et la dépendance `firebase-crashlytics-gradle` est appliquée (`app/build.gradle.kts:15`) ce qui devrait uploader les mappings auto. Absence de CI script visible pour archiver `mapping.txt` localement. |
| Pas de debug code/logs en release (Timber release tree) | ⚠️ Partiel | **AUDIT-1-004** / **AUDIT-2-002** / **AUDIT-1-018** | `PlexHubApplication.kt:88-94` plante un `CrashReportingTree` en release et `DebugTree` en debug, OK. Mais : (a) `HttpLoggingInterceptor.Level.HEADERS` en debug expose `X-Plex-Token` (AUDIT-1-004 / 2-001), (b) `Timber.w/e` côté workers et clients loggent des URLs serveurs avec tokens potentiels (AUDIT-2-002), (c) `LibrarySyncWorker` log les `connectionCandidates` URLs (AUDIT-1-018). |

---

## 2. Google Play Compliance

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Privacy policy URL | 🔍 Non vérifiable statiquement | **AUDIT-7-002** *(nouveau)* | Aucun lien dans le repo. À déclarer dans Play Console (lié au formulaire Data Safety). |
| Data safety form rempli | 🔍 Non vérifiable statiquement | **AUDIT-7-003** *(nouveau)* | Action Play Console. Doit déclarer : auth tokens locaux (Plex), historique de lecture, logs Crashlytics avec PII (cf AUDIT-2-014). |
| targetSdk 35 à jour | ✅ Fait | — | `app/build.gradle.kts:26` : `targetSdk = 35`, `compileSdk = 36`. Conforme à la deadline Play Store août 2025. |
| Permissions justifiées | ⚠️ Partiel | **AUDIT-2-012** / **AUDIT-1-008** | `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`. La dernière (`REQUEST_INSTALL_PACKAGES`) est unique pour `ApkInstaller` (sideload OTA via GitHub) — Play Console refuse souvent cette permission sans whitelisting. À documenter ou retirer la voie sideload. |
| Content rating questionnaire | 🔍 Non vérifiable statiquement | **AUDIT-7-004** *(nouveau)* | Action Play Console. App = lecteur média multi-source, contenu hébergé chez l'utilisateur → IARC "Tout public" probable mais à confirmer. |
| App category : "Entertainment" / "Video Players" | 🔍 Non vérifiable statiquement | **AUDIT-7-005** *(nouveau)* | Action Play Console. Recommandé : "Video Players & Editors" + "Entertainment". |

---

## 3. Android TV Specific

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Banner icon 320×180 (`android:banner`) | ✅ Fait | — | `AndroidManifest.xml:35` `android:banner="@mipmap/ic_banner"`, et `app/src/main/res/mipmap-xhdpi/ic_banner.png` mesure exactement **320×180** (vérifié `file` shell). Activity hérite aussi : `MainActivity` ligne 40 redéclare `android:banner`. |
| Leanback launcher intent filter | ✅ Fait | — | `AndroidManifest.xml:50` : `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />` présent dans `MainActivity`. |
| `android:isGame="false"` | ❌ Non fait | **AUDIT-7-006** *(nouveau)* | Attribut absent du `<application>` dans `AndroidManifest.xml`. Recommandé pour Android TV : indique aux launchers que ce n'est pas un jeu (placement dans la row "Apps" et non "Games"). |
| `uses-feature android.software.leanback required="true"` | ❌ Non fait | **AUDIT-7-007** *(nouveau)* | `AndroidManifest.xml:21-23` déclare `android:required="false"` → l'app peut être installée sur smartphone, ce qui est inattendu pour un client TV. Si l'intention est TV-only, doit passer à `true`. |
| `uses-feature android.hardware.touchscreen required="false"` | ✅ Fait | — | `AndroidManifest.xml:18-20` : `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`. |
| Catégorie TV correcte dans le manifest | ⚠️ Partiel | **AUDIT-7-008** *(nouveau)* | Aucun `<category android:name="android.intent.category.LEANBACK_SETTINGS" />` ou autre. La category leanback est suffisante pour l'app launcher mais pas pour les Android TV recommendations channels (cf. AUDIT-3-014, feature 2 phase 6 — câblage incomplet). |

---

## 4. Crash Reporting & Analytics

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Firebase Crashlytics | ✅ Fait | (mais voir AUDIT-2-014) | `app/build.gradle.kts:15` plugin appliqué, `app/build.gradle.kts:240` dep `libs.firebase.crashlytics`. `PlexHubApplication.kt:358` `setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)`. **AUDIT-2-014** : `setCustomKey("media_rating_key", ...)` peut leak PII. |
| ANR detection | ⚠️ Partiel | **AUDIT-7-009** *(nouveau)* | Crashlytics collecte les ANR par défaut depuis Android 11 (API 30). Mi Box S = API 28 (Android 9) → ANR detection plus limitée. Aucun watchdog custom (`com.github.anrwatchdog`) trouvé. |
| Analytics events sur les actions clés | ⚠️ Partiel | **AUDIT-7-010** *(nouveau)* | `domain/service/AnalyticsService.kt` interface présente, impl Firebase dans `app/di/AnalyticsModule.kt` (cf phase 0 §17). Couverture des events critiques (play start, search, source switch) **non vérifiée** dans le détail. |
| Performance monitoring | ✅ Fait | — | `app/build.gradle.kts:16` plugin `firebase-perf`, dep `libs.firebase.perf`. Aucun trace custom trouvé via grep — utilisation des seuls traces auto. |

---

## 5. Accessibilité

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| ContentDescription sur images/boutons | ⚠️ Partiel | **AUDIT-7-011** *(nouveau)* | `feature/**` contient au moins 74 occurrences de `contentDescription` dans 10+ écrans (sample). Couverture partielle mais loin d'être systématique sur les composants `core/ui` (NetflixMediaCard, FallbackAsyncImage, badges). |
| Navigation TalkBack fonctionnelle | 🔍 Non vérifiable statiquement | **AUDIT-7-012** *(nouveau)* | Nécessite test device avec TalkBack activé. Compose pour TV gère l'order semantics par défaut mais focus order custom (`focusVersion++`, `requestFocus()`) non testé sous lecteur d'écran. |
| Contraste suffisant (WCAG AA) | ⚠️ Partiel | **AUDIT-5-029** | `core/designsystem/Type.kt` : CardTitle 13sp, BadgeSmall 9sp → en-dessous des minima TV (≥ 14sp). Cinema Gold theme refonte en cours sur cette branche. Contraste couleur non audité formellement. |
| Taille de texte scalable | ❌ Non fait | **AUDIT-5-029** / **AUDIT-5-030** | `Type.kt` : tailles en `sp` mais figées (13, 9, 11). Aucun support d'échelle utilisateur (pas de `LocalDensity` override, pas de setting "text size"). |

---

## 6. Localisation

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Strings externalisées | ⚠️ Partiel | **AUDIT-5-002** / **AUDIT-5-003** / **AUDIT-5-006** / **AUDIT-5-007** / **AUDIT-5-008** / **AUDIT-5-021** | `app/src/main/res/values/strings.xml` existe et est largement peuplé. Au moins 8 chaînes UI hardcodées en anglais dans `NetflixDetailScreen` (AUDIT-5-007), Home rails titres en anglais (AUDIT-5-003), EmptyState anglais (AUDIT-5-002), error/init states anglais (AUDIT-5-006), Player Audio/Subtitle Sync dialogs (AUDIT-5-021). |
| Support FR/EN minimum | ✅ Fait | — | `app/src/main/res/values/strings.xml` (en) + `app/src/main/res/values-fr/strings.xml` (fr) tous deux présents. Les deux fichiers sont modifiés sur la branche actuelle. |
| Formats date/nombre localisés | 🔍 Non vérifiable statiquement | **AUDIT-7-013** *(nouveau)* | `core/common/ContentUtils.kt` formate des durées et timestamps mais non audité ici pour `Locale.getDefault()` propagation. |

---

## 7. Testing

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Tests unitaires logique critique (> 60 % coverage domain/data) | ❌ Non fait | **AUDIT-4-015** / **AUDIT-4-020** | 38 tests unit + 4 instrumented (`find -path "*/src/test/*" -name "*.kt"`). 0 tests dans `:core:database`, `:core:datastore`, `:core:designsystem`, `:core:ui`, `:core:navigation`. Coverage estimée < 10 % par fichier. |
| Tests d'intégration Room | ❌ Non fait | **AUDIT-4-015** / **AUDIT-4-020** | Aucun test sous `core/database/src/androidTest/`. Migrations 36 chaînées non testées. |
| Tests UI de base (Maestro ou Compose UI tests) | ❌ Non fait | **AUDIT-4-015** | 4 instrumented tests seulement, pas de Maestro flow trouvé. |
| Tests sur device réel Android TV | 🔍 Non vérifiable statiquement | **AUDIT-7-014** *(nouveau)* | Aucun rapport ou trace dans le repo. À effectuer sur Mi Box S et Chromecast w/ Google TV. |
| Tests avec connexion lente / offline | 🔍 Non vérifiable statiquement | **AUDIT-7-015** *(nouveau)* | À effectuer manuellement. Le code a `OkHttp` cache + Room offline-first, mais comportement utilisateur non validé. |
| Tests multi-serveur | 🔍 Non vérifiable statiquement | **AUDIT-7-016** *(nouveau)* | Architecture multi-source (Plex / Jellyfin / Xtream / Backend / IPTV) testée par lecture de code mais pas par test d'intégration. |

---

## 8. Tooling Debug

| Item | Statut | Finding | Preuve / Note |
|---|---|---|---|
| Baseline Profiles générés | ❌ Non fait | **AUDIT-3-006** | Aucun `baseline-prof.txt` trouvé dans `app/src/main/`. Aucun module `:baselineprofile`. Cible Android 9 (Mi Box S) → impact cold start +20-30 % garanti. |
| StrictMode activé en debug | ❌ Non fait | **AUDIT-7-017** *(nouveau)* | Aucune occurrence de `StrictMode.setThreadPolicy` ou `setVmPolicy` dans le code (`grep` confirmé sur tout le repo). |
| LeakCanary intégré en debug | ✅ Fait | — | `app/build.gradle.kts:161` : `debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`. Aucun rapport fourni. |
| Firebase Test Lab | 🔍 Non vérifiable statiquement | **AUDIT-7-018** *(nouveau)* | Aucun script CI (pas de `.github/workflows/firebase-test-lab.yml` trouvé) → non utilisé. |

---

## 9. Findings Phase 7 ouverts (gaps non couverts par phases 1-6)

| ID | Titre | Sévérité suggérée | Effort |
|---|---|---|---|
| AUDIT-7-001 | Mapping file Crashlytics : pas de tâche CI explicite d'archivage / vérification du push automatique | P2 | S |
| AUDIT-7-002 | Privacy policy URL non publiée — bloque submission Play | P0 release blocker | S |
| AUDIT-7-003 | Data Safety form Play Console non rempli | P0 release blocker | S |
| AUDIT-7-004 | Content rating questionnaire non rempli | P0 release blocker | S |
| AUDIT-7-005 | App category Play Store non choisie | P1 | S |
| AUDIT-7-006 | `android:isGame="false"` absent du manifest | P2 | S |
| AUDIT-7-007 | `uses-feature android.software.leanback required="false"` — devrait être `true` pour TV-only | P1 | S |
| AUDIT-7-008 | Aucun cabling Android TV recommendation rows complet (lien feature 2 phase 6) | P1 | M |
| AUDIT-7-009 | ANR watchdog custom absent — Mi Box S API 28 ne fournit pas l'ANR Crashlytics auto | P2 | S |
| AUDIT-7-010 | Couverture analytics events critiques non documentée | P2 | M |
| AUDIT-7-011 | `contentDescription` partiel sur composants `core/ui` partagés (NetflixMediaCard, FallbackAsyncImage, badges) | P1 | M |
| AUDIT-7-012 | TalkBack non testé — focus order custom non vérifié | P1 | M |
| AUDIT-7-013 | `Locale.getDefault()` propagation FR/EN pour dates/nombres non vérifiée | P2 | S |
| AUDIT-7-014 | Aucun test sur device Mi Box S / Chromecast w/ GTV | P0 release blocker | M |
| AUDIT-7-015 | Aucun test connexion lente / offline | P1 | M |
| AUDIT-7-016 | Aucun test multi-serveur (Plex+Jellyfin+Xtream simultanés) | P1 | M |
| AUDIT-7-017 | StrictMode jamais activé en debug | P2 | S |
| AUDIT-7-018 | Firebase Test Lab non intégré CI | P2 | M |

---

## 10. Récap par catégorie

| Section | ✅ | ⚠️ | ❌ | 🔍 |
|---|---|---|---|---|
| Build & Release | 0 | 3 | 2 | 1 |
| Google Play Compliance | 1 | 1 | 0 | 4 |
| Android TV Specific | 2 | 1 | 2 | 0 |
| Crash Reporting & Analytics | 2 | 2 | 0 | 0 |
| Accessibilité | 0 | 2 | 1 | 1 |
| Localisation | 1 | 1 | 0 | 1 |
| Testing | 0 | 0 | 3 | 3 |
| Tooling Debug | 1 | 0 | 2 | 1 |
| **Total (38 items)** | **7** | **10** | **10** | **11** |

**Score brut production-readiness checklist** : 7 ✅ + 10 ⚠️/2 = **12 / 38 ≈ 31 %** (on traite ⚠️ comme demi-points et 🔍 comme 0).

---

## 11. Phase 7 — items qui bloquent un release Play Store immédiat

1. **AUDIT-1-003** — `versionCode = 1` figé → upload Play Store rejeté.
2. **AUDIT-1-002 / AUDIT-2-004** — Release fallback debug keystore → APK potentiellement debug-signé.
3. **AUDIT-7-002** — Privacy policy URL absente.
4. **AUDIT-7-003** — Data Safety form non rempli.
5. **AUDIT-7-004** — Content rating questionnaire non rempli.
6. **AUDIT-2-012 + AUDIT-2-008** — `REQUEST_INSTALL_PACKAGES` + side-load APK sans vérification SHA256 → Play Policy violation possible (à supprimer ou whitelister).
7. **AUDIT-7-014** — Aucun test sur device TV réel → release prématurée.

Ces 7 items doivent être tous résolus avant d'envisager une release alpha/beta sur Play Console interne.
