# Sortie A — Executive Summary

> PlexHubTV — Audit Production-Ready v3.0 · Release Agent final report
> Consommé à partir des phases 0 → 6 produites par les agents spécialisés.

## 1. État global

### Forces
PlexHubTV est une application Android TV remarquablement ambitieuse pour un projet solo : Clean Architecture multi-modules (`:app`, `:domain`, `:data`, `:core:*`) avec 496 fichiers Kotlin, Jetpack Compose TV + D-pad first, Room v47 offline-first SSOT avec 24 entités et 36 migrations, Hilt, WorkManager (6 workers), Media3/ExoPlayer primaire + libmpv fallback, Firebase Crashlytics/Analytics/Performance, 5 thèmes Material3, support multi-source (Plex + Jellyfin + Xtream + Backend + IPTV M3U), deep links, Android TV Channels (en partie), profils locaux complets, Plex Home switcher, update OTA, ParentalPinDialog, screensaver DreamService. L'intention fonctionnelle est au niveau d'une app commerciale. Crashlytics/Analytics/Perf intégrés, LeakCanary en debug, Conscrypt activé, EncryptedSharedPreferences pour tokens, Network Security Config présent.

### Faiblesses
La qualité d'exécution est en-dessous du standard release. **137 findings** numérotés dont **19 P0, 52 P1, 61 P2**, plus 5 features phase 6 + 18 nouveaux gaps AUDIT-7. Les risques bloquants :
- [AUDIT-1-001](phase1-2-stability-security.md) Xtream mapper wipe silencieux de la bibliothèque
- [AUDIT-1-002](phase1-2-stability-security.md) release fallback debug keystore
- [AUDIT-1-003](phase1-2-stability-security.md) versionCode figé
- [AUDIT-1-005](phase1-2-stability-security.md) deep link player écran noir
- [AUDIT-3-006](phase3-performance.md) pas de Baseline Profile sur cible Android 9
- [AUDIT-3-001](phase3-performance.md) ExoPlayer buffer 300 MB documenté sur 4K HEVC
- [AUDIT-4-002](phase4-architecture.md) `:domain` en Android library
- [AUDIT-5-028](phase5-6-ux-features.md) OverscanSafeArea non utilisé
- [AUDIT-5-029](phase5-6-ux-features.md) typographie sous 14 sp
- [AUDIT-4-015](phase4-architecture.md) 38 tests unit / 0 dans core:database/datastore
- checklist release à **~31%**.
- [AUDIT-2-001](phase1-2-stability-security.md) HttpLoggingInterceptor expose X-Plex-Token en debug
- [AUDIT-2-008](phase1-2-stability-security.md) ApkInstaller sans SHA256

**Release Play Store immédiate impossible.**

---

## 2. Note production readiness : **4 / 10**

- **Build & signing cassés** : AUDIT-1-002 (fallback debug keystore), AUDIT-1-003 (versionCode=1 figé), AUDIT-1-006 (4 ABIs dans l'APK, commentaire faux) → upload Play Store impossible.
- **Tests quasi absents** : 38 unit + 4 instrumented sur ~496 fichiers Kotlin (~8 %), zéro test sur `core:database` + `core:datastore` (AUDIT-4-015, AUDIT-4-020) → aucun filet.
- **Perf non mesurée sur device cible** : aucun Baseline Profile (AUDIT-3-006), buffer 300 MB reconnu dans le code (AUDIT-3-001), aucun `onTrimMemory` (AUDIT-1-016) → OOM probable Mi Box S.
- **Checklist Play Console vide** : privacy policy, Data Safety, content rating, app category (AUDIT-7-002/003/004/005), `REQUEST_INSTALL_PACKAGES` problématique (AUDIT-2-012).

---

## 3. Note vendabilité / UX : **5.5 / 10**

- **UX TV partiellement aboutie** : 33 findings phase 5 dont 2 P0 critiques (OverscanSafeArea non utilisé, typo sous 14 sp) qui rendent l'app difficile à lire à 3 m — mais composants NetflixMediaCard / ContentRow / HomeHeader visuellement forts.
- **Localisation incomplète** : strings FR/EN présents mais 15+ UI hardcodées anglais dans Home, Detail, Player, error states (AUDIT-5-002/003/006/007/008/018/021).
- **Focus management fragile** : scroll snap parasite Home (AUDIT-5-001), focus restoration Library async (AUDIT-5-013/014), tabs Detail sans focus restore (AUDIT-5-009), CastRow sans key (AUDIT-5-012) — régressions visibles en 5 min de test.
- **Features différenciantes présentes mais non livrées** : Mode enfant 80 % codé mais pas appliqué, Android TV Channels infra présente mais non câblée bout-à-bout, Stats + avatars enrichis atteignables en 1-3 j. Potentiel de vendabilité réel une fois le top 5 livré.

---

## 4. Top 5 risques critiques

| # | ID | Titre | Pourquoi c'est critique | Impact business |
|---|---|---|---|---|
| 1 | **AUDIT-1-001** | Xtream mapper pageOffset=0 → UNIQUE INDEX collision wipe library | Le `MEMORY.md` documente le piège. INSERT OR REPLACE sur index unique écrase les items précédents de la catégorie. Utilisateur voit 1 film/catégorie au lieu de 2000, sans crash. | Blocker release. Reviews 1★ « ne marche pas avec mon Xtream ». Perte du segment IPTV (probablement la base la plus large). |
| 2 | **AUDIT-1-002 + AUDIT-2-004** | Release fallback debug keystore silencieux | Build CI sans keystore signe debug sans erreur. Si publié, OTA chain permanently cassée (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) → désinstall/réinstall obligatoire → perte profils/prefs/Room. | Blocker release. Reset utilisateur forcé. Réputation compromise. |
| 3 | **AUDIT-3-001 + AUDIT-1-016** | ExoPlayer buffer 30 s ~300 MB + pas de `onTrimMemory` sur Mi Box S | Commentaire du code reconnaît le pic 300 MB. Android TV 9 + largeHeap 192 MB + Coil + Room + Compose → lowmemorykiller pendant 4K HEVC. | Blocker device cible. Le use case central ne marche pas sur le device principal déclaré. |
| 4 | **AUDIT-1-003** | versionCode=1 figé depuis 16 versions | Play Store refuse upload si versionCode non strictement > précédent. Stratégie OTA GitHub marche par hasard sur versionName. | Blocker Play Store. Distribution pro impossible. |
| 5 | **AUDIT-2-008 + AUDIT-2-012 + AUDIT-2-011** | ApkInstaller sans SHA256 + `REQUEST_INSTALL_PACKAGES` + UpdateChecker sans TLS pin | MITM GitHub API installe APK arbitraire sous le nom PlexHubTV. Vecteur RCE. Play Policy refuse souvent `REQUEST_INSTALL_PACKAGES`. | Blocker sécurité + release. Risque juridique, refus Play Store probable. |

---

## 5. Nombre de findings (severity × phase)

| Phase | P0 | P1 | P2 | Total |
|---|---|---|---|---|
| 1 — Stability | 3 | 7 | 8 | 18 |
| 2 — Security | 2 | 6 | 6 | 14 |
| 3 — Performance | 6 | 11 | 7 | 24 |
| 4 — Architecture | 3 | 12 | 10 | 25 |
| 5 — UX | 2 | 10 | 21 | 33 |
| 7 — Production gaps (nouveaux) | 3 implicites | 6 | 9 | 18 |
| **Total numérotés** | **19** | **52** | **61** | **132** |
| Features Phase 6 | — | — | — | +5 |
| **Grand total** | | | | **137** |

> Note : le décompte Phase 4 utilisé ici est celui du fichier réel (`phase4-architecture.md` : 3 P0 / 12 P1 / 10 P2), pas le résumé de pré-lancement.

---

## 6. Graphe de dépendances

```
[Release signing cluster]
  AUDIT-1-002 ≡ AUDIT-2-004 → AUDIT-7-001 (mapping)
  AUDIT-1-003 → AUDIT-7-002 (privacy) → AUDIT-7-003 (data safety)

[Token leak cluster]
  AUDIT-1-004 ≡ AUDIT-2-001 (HEADERS logs)
  AUDIT-1-018 → AUDIT-2-002 (Timber URLs)

[TLS & OTA cluster]
  AUDIT-2-003 → AUDIT-2-011 → AUDIT-2-008 → AUDIT-2-012 + AUDIT-1-015

[Memory pressure cluster]
  AUDIT-3-001 (300MB buffer) ─┐
  AUDIT-3-002 (Coil 20%) ─┬───┴→ AUDIT-1-016 (onTrimMemory)
                          └→ AUDIT-3-007 (allowHardware false)

[Startup cluster]
  AUDIT-3-005 (5 init jobs) → AUDIT-3-006 (Baseline Profile)

[Database cluster]
  AUDIT-3-003 (media 20+ index) ─┬→ AUDIT-3-011 (query unifié)
                                 └→ AUDIT-3-012 (36 migrations) → AUDIT-3-017 (LIKE + FTS4)

[Network client cluster]
  AUDIT-3-010 (playerOkHttpClient) → AUDIT-3-015 (ConnectionPool)

[Compose perf cluster]
  AUDIT-3-008 (List<> non-immutable) → AUDIT-3-021 (compiler metrics)

[Architecture purity cluster]
  AUDIT-4-002 (:domain Android) ─┬→ AUDIT-4-005 (PagingData in domain)
                                 └→ AUDIT-4-024 (Konsist)
  AUDIT-4-003 (UseCase in :app) → AUDIT-4-002 + AUDIT-4-004 (MediaUrlResolver)
  AUDIT-4-004 → AUDIT-4-018 (core.di drift)
  AUDIT-4-010 (BaseViewModel 17/37) → AUDIT-4-012 (try/catch mix)
  AUDIT-4-024 ← AUDIT-4-003 / 004 / 018 / 010 / 012

[Test cluster]
  AUDIT-4-014 (Turbine) → AUDIT-4-015 (0 core tests) → AUDIT-4-020 (coverage)
  AUDIT-4-015 → AUDIT-3-012 (test migrations)

[UX Home cluster]
  AUDIT-3-004 (scrollToItem) → AUDIT-5-001 (scroll snap)
  AUDIT-5-003 (rails i18n) → AUDIT-5-002 (EmptyState i18n) → AUDIT-5-006 (init/error)

[UX Detail cluster]
  AUDIT-5-007 → AUDIT-5-008 (ActionButtons) + AUDIT-1-012 (VM error strings)

[UX Library cluster]
  AUDIT-5-013 → AUDIT-5-014

[Design tokens cluster]
  AUDIT-5-028 (Overscan) → AUDIT-5-031 (Skeletons padding)
  AUDIT-5-029 (typo) → AUDIT-5-030 (card 14sp) + AUDIT-5-011 (poster)

[Device validation cluster]
  AUDIT-7-014 (test Mi Box S) → AUDIT-3-022 (tunneling) + AUDIT-7-018 (Firebase Test Lab)
  AUDIT-7-011 → AUDIT-7-012 (TalkBack)

[Standalone haute gravité — aucune dépendance]
  AUDIT-1-001 (Xtream wipe) — priorité absolue
  AUDIT-1-005 (player deep link)
```

---

## 7. Time-to-release estimate

| Milestone | Durée | Cumul |
|---|---|---|
| Sprint 1 Stabilité critique | 2 sem | S2 |
| Sprint 2 Sécurité + Perf | 2 sem | S4 |
| Sprint 3 UX Polish | 3 sem | S7 |
| Sprint 4 Production Release | 2 sem | S9 |
| Stabilisation QA + beta interne | 2 sem | S11 |
| Bugfix post-beta | 2 sem | S13 |
| **Production release** | | **~13 semaines calendaires** |
| Sprint 5+ Features | ongoing | S13+ |

Avec 1 dev full-time : **~13 semaines**. Avec 2 devs + 1 QA : **~8 semaines**.

---

## 8. Rejected low-confidence findings (cross-validation)

| ID | Confiance initiale | Action | Justification |
|---|---|---|---|
| AUDIT-1-011 | Faible | **Kept P2, confiance upgraded Moyenne** | Vérifié `PlexHubApplication.kt:227-278` : `ExistingPeriodicWorkPolicy.KEEP` évite re-scheduling mais coûte un IPC par boot. Impact mineur mais réel. |
| AUDIT-3-022 | Faible | **Kept P2, confiance Faible maintenue — device validation requise** | Hypothèse firmware-spécifique. À valider via AUDIT-7-014. |
| AUDIT-3-024 | Faible | **Kept P2, confiance upgraded Moyenne** | `DatabaseModule` contient bien le callback, overhead mesurable. |
| AUDIT-4-025 | Faible | **Kept P2** | Gap tooling, pas un bug. Effort S. |
| AUDIT-5-025 | Faible | **Kept P2** | Observation D-pad mineure. |

**Aucun finding rejeté.** Tous conservés, deux confidence upgrades (AUDIT-1-011, AUDIT-3-024), un flag device-validation (AUDIT-3-022).

---

## 9. Verdict release

**Statut actuel : Not releasable.**

**Conditions minimales alpha Play Console** :
1. Tous les P0 phases 1-4 fixés (Sprint 1 + moitié Sprint 2)
2. Phase 7 items 1-3 remplis
3. versionCode strategy implémentée
4. Release signing sécurisé fail-fast
5. ≥ 30 nouveaux tests unit sur les chemins P0 fixés
6. Smoke test manuel Mi Box S passé

**Délai minimal alpha** : 4-5 semaines avec 1 dev.
**Production** : ~13 semaines.
