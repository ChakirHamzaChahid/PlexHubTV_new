# PlexHubTV — Rapport d'Audit Final (Synthese)

**Date** : 22 mars 2026
**Version auditee** : 1.0.15 (versionCode : 1)
**Base de code** : 563 fichiers Kotlin, 10 modules Gradle
**Branche** : `claude/continue-plexhubtv-refactor-YO43N`
**Auditeurs** : 5 agents specialises (Claude Opus 4.6) + chef d'equipe (synthese)

---

## Resume Executif

PlexHubTV est un client Android TV mature et bien architecture, avec une interface de type Netflix construit sur Kotlin, Jetpack Compose, Clean Architecture (MVVM) et Hilt DI. La base de code demontre une ingenierie solide dans tous les domaines : stockage chiffre AES-256-GCM, gestion d'erreurs typee (hierarchie scellee `AppError` avec 22 types d'erreurs), concurrence structuree (zero utilisation de `GlobalScope`), gestion complete du focus D-pad, ecrans de chargement skeleton sur chaque ecran, et un lecteur hybride ExoPlayer/MPV avec 7 sous-controleurs.

L'audit a identifie **84 problemes au total** sur 8 phases. Les points les plus forts de l'application sont la securite (aucun probleme P0), l'architecture de la base de donnees (WAL + 20 index + table unifiee materialisee), et le pattern Strategy pour la gestion multi-source des medias. Les points les plus faibles sont la stabilite de recomposition Compose (20+ classes UiState sans `@Immutable`), la couverture de tests (38 fichiers, en hausse par rapport a 4, mais 5 tests critiques toujours manquants), et la localisation (~40 chaines anglaises codees en dur + 70 traductions francaises manquantes).

### Niveau de preparation global : 7.2/10 — Presque pret (2 a 5 semaines avant la production)

### Score par domaine

| Domaine | Score | Note | Verdict |
|---------|-------|------|---------|
| Stabilite | 8/10 | B+ | 4 vecteurs de crash `!!`, mais patterns coroutines solides, zero GlobalScope |
| Securite | 9/10 | A | Aucun probleme P0, chiffrement AES-256-GCM, gestion 401 correcte |
| Performance | 7.5/10 | B+ | Excellent DB/lecteur, mais lacunes de recomposition Compose sur l'ecran d'accueil |
| Architecture | 8/10 | B+ | Domaine pur Kotlin, pattern Strategy exemplaire, lacunes de coherence mineures |
| UX & Design | 8/10 | A- | Qualite Netflix pour focus/chargement/animations, transitions d'ecran manquantes |
| Preparation Release | 7.2/10 | B- | 3 bloquants : pas de Timber release tree, versionCode=1, chaines codees en dur |

---

## Problemes P0 Unifies (5 elements — A corriger avant la release)

| # | Phase | Probleme | Fichier | Correction | Effort |
|---|-------|----------|---------|------------|--------|
| P0-1 | Stabilite | `bestSeasonRatingKey!!` / `bestSeasonServerId!!` — NPE sur donnees de saison nullable | `MediaDetailViewModel.kt:581-582` | Utiliser `?.let { }` ou `requireNotNull()` avec message | Trivial |
| P0-2 | Stabilite | `it.parentIndex!!` / `it.index!!` dans groupBy apres filter | `MediaDetailRepositoryImpl.kt:144-147` | Utiliser `filterNotNull()` ou `?: 0` | Trivial |
| P0-3 | Stabilite | `entity!!.unificationId` apres verification de null | `MediaDetailRepositoryImpl.kt:357` | Utiliser `entity?.unificationId?.let { }` | Trivial |
| P0-4 | Performance | `NetflixHomeContent` recoit `List<Hub>`, `List<MediaItem>` (instable pour Compose) — tout l'ecran d'accueil se recompose a chaque changement d'etat | `NetflixHomeScreen.kt:33-37`, `DiscoverScreen.kt:148` | Changer les params en `ImmutableList<>`, supprimer `.toList()` | Faible |
| P0-5 | Release | Pas de Timber release tree — 410 appels `Timber.e/w` dans 92 fichiers sont invisibles en production | `PlexHubApplication.kt` | Creer `CrashReportingTree` qui transmet a Crashlytics | Faible |

---

## Problemes P1 Unifies (22 elements — A corriger avant ou peu apres la release)

### Stabilite P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-1 | `SettingsViewModel` ~15 `.collect {}` sans `.catch {}` — le toggle de parametre cesse de se mettre a jour apres une erreur | `SettingsViewModel.kt:769-856` | Ajouter `.catch {}` ou utiliser `safeCollectIn()` |
| P1-2 | `MainViewModel.checkForUpdate()` sans try-catch — crash en debug sur erreur reseau | `MainViewModel.kt:93-101` | Envelopper dans try-catch |
| P1-3 | `_uiState.value =` (21 occurrences) vs `_uiState.update {}` (367) — concurrence incoherente | 5 ViewModels | Standardiser sur `.update {}` |
| P1-4 | `PlayerMediaLoader.loadMedia()` collecte un flow sans `.catch {}` | `PlayerMediaLoader.kt:66` | Ajouter `.catch {}` avant `.collect` |

### Securite P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-5 | Les tokens Plex dans les URLs d'images stockees en Room persistent apres deconnexion | `MediaMapper.kt`, `SyncRepositoryImpl.kt` | Retirer les tokens des URLs stockees, les reinjecter au chargement |
| P1-6 | Token dans l'interpolation d'URL peut apparaitre dans les logs debug | `CollectionSyncWorker.kt:85,110,149` | Utiliser l'injection de parametres de requete Retrofit |
| P1-7 | `cleartextTrafficPermitted="true"` — tokens LAN en HTTP | `network_security_config.xml:16` | Documenter le risque ; preferer HTTPS quand disponible |
| P1-8 | `REQUEST_INSTALL_PACKAGES` — risque de sideload via mecanisme de mise a jour | `AndroidManifest.xml:16` | Verifier mises a jour HTTPS uniquement ; ajouter justification Play Console |

### Performance P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-9 | `PlayerUiState` sans `@Immutable`, mis a jour chaque seconde — les controles du lecteur se recomposent constamment | `PlayerUiState.kt:12-70` | Ajouter `@Immutable`, utiliser `ImmutableList` pour les champs liste |
| P1-10 | `MediaDetailUiState` sans `@Immutable` — l'ecran detail se recompose entierement au toggle d'enrichissement | `MediaDetailUiState.kt:12-32` | Ajouter `@Immutable`, utiliser `ImmutableList` |
| P1-11 | La classe `Hub` a `List<MediaItem>` sans annotation de stabilite | `Hub.kt:16-23` | Ajouter `@Immutable`, changer en `ImmutableList` |
| P1-12 | `NetflixContentRow` recoit `items: List<MediaItem>` (instable) | `NetflixContentRow.kt:36` | Changer en `ImmutableList<MediaItem>` |

### Architecture P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-13 | Seulement 5/34 ViewModels utilisent `BaseViewModel` — gestion d'erreurs incoherente | 29 ViewModels | Standardiser sur `BaseViewModel` |
| P1-14 | Utilisation directe de Firebase dans 5 ViewModels/controleurs — couplage fort | 5 fichiers | Extraire une interface `AnalyticsService` |
| P1-15 | 46+ `catch (_: Exception) {}` silencieux — masquent les erreurs dans les workers de sync | `LibrarySyncWorker.kt`, `RatingSyncWorker.kt` | Ajouter du logging Timber aux catches critiques |

### UX P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-16 | Messages d'erreur en francais uniquement dans `toUserMessage()` | `ErrorExtensions.kt` | Migrer vers les string resources avec EN/FR |
| P1-17 | Pas d'animations de transition d'ecran — NavHost bascule instantanement | NavHost dans `MainScreen.kt` | Ajouter `fadeIn(300)`/`fadeOut(200)` |
| P1-18 | Chaines francaises codees en dur dans HomeViewModel | `HomeViewModel.kt:119,121` | Utiliser les string resources |

### Release P1

| # | Probleme | Fichier | Correction |
|---|----------|---------|------------|
| P1-19 | `versionCode = 1` — doit etre incremente a chaque upload Play Store | `app/build.gradle.kts` | Automatiser depuis le CI ou bump manuel |
| P1-20 | ~40 chaines anglaises codees en dur contournent la localisation | 13+ fichiers composables | Extraire dans `strings.xml` |
| P1-21 | 70 traductions francaises manquantes | `values-fr/strings.xml` | Traduire les entrees manquantes |
| P1-22 | 5 fichiers de tests critiques toujours manquants (MediaDetailRepo, PlaybackRepo, JellyfinSource, etc.) | `data/src/test/` | Ecrire les tests |

---

## Resume Phase par Phase

### Phase 1+2 : Stabilite & Securite

**Rapport complet** : [phase1-2-stability-security.md](phase1-2-stability-security.md)

- **20 problemes** : 4 P0 (tous stabilite), 10 P1 (6 stabilite + 4 securite), 6+ P2
- **Zero probleme P0 de securite** — excellente posture securitaire
- **Zero utilisation de GlobalScope** — tout le travail en arriere-plan utilise `applicationScope`, `viewModelScope`, ou WorkManager
- **367 `_uiState.update {}` atomiques vs 21 `_uiState.value =` non-atomiques** — globalement bon, a standardiser
- **Points forts** : patterns SupervisorJob, GlobalCoroutineExceptionHandler avec Crashlytics, EncryptedSharedPreferences avec degradation gracieuse, AuthInterceptor avec AtomicReference, routage pre-vol des codecs lecteur, validation de schema URI

### Phase 3 : Performance

**Rapport complet** : [phase3-performance.md](phase3-performance.md)

- **20 goulots d'etranglement** classes par impact utilisateur
- **Top 3 des problemes visibles** : saccades de defilement sur l'ecran d'accueil (params Compose instables), taille APK (libs natives 4 ABI), recomposition de PlayerUiState chaque seconde
- **Note par categorie** :
  - Recomposition Compose : C+ (lacunes critiques dans les types de parametres)
  - Temps de demarrage : A- (MainActivity legere, travail differe)
  - Memoire : A- (cache image adaptatif au heap)
  - Reseau : B (multi-serveur parallele, mais cache HTTP sous-utilise)
  - Base de donnees : A (WAL, 20+ index, FTS, table unifiee materialisee)
  - Defilement : B+ (cles correctes, contentType manquant sur l'accueil)
  - Taille APK : B- (splits ABI actifs, mais APK universel gonfle)
  - Lecteur : A (buffers adaptes au reseau, pre-vol codecs, reutilisation de session)

### Phase 4 : Architecture & Qualite du Code

**Rapport complet** : [phase4-architecture.md](phase4-architecture.md)

- **Module domain : A** — zero imports Android, veritablement pur Kotlin
- **Global : B+** — architecture saine, lacunes de coherence
- **Scores par module** : `:domain` A, `:core:model` A-, `:core:database` A, `:data` B+, `:app` B
- **Suite de tests** : Passee de 4 a 38 fichiers (amelioration significative)
- **Points forts cles** : pattern Strategy `MediaSourceHandler` (OCP exemplaire), hierarchie `AppError` complete, DI Hilt propre avec `@IntoSet` multibinding
- **Points faibles cles** : Seulement 5/34 ViewModels utilisent BaseViewModel, 5 dependances codees en dur hors du catalogue de versions, couplage fort Firebase dans les ViewModels

### Phase 5+6 : UX & Fonctionnalites

**Rapport complet** : [phase5-6-ux-features.md](phase5-6-ux-features.md)

- **Score UX : 8/10** — patterns qualite Netflix partout
- **10 ecrans audites** avec analyse par ecran : Accueil, Detail, Saison, Bibliotheque, Recherche, Parametres, Lecteur, Authentification, Favoris, Historique, Telechargements
- **5 parcours de navigation D-pad traces** — tous valides
- **Etats de chargement** : Skeleton/shimmer sur chaque ecran (Accueil, Detail, Saison, Bibliotheque, Recherche, Favoris, Historique, Telechargements)
- **Etats vides** : Presents sur tous les ecrans (bibliotheque vide, aucun resultat, aucun favori, aucun historique, aucun telechargement)
- **Lacune principale** : Pas d'animations de transition d'ecran (bascules instantanees)
- **Etat des fonctionnalites** : 15+ fonctionnalites completes (profils, themes, playlists, watchlist, favoris, historique, economiseur d'ecran, multi-serveur, enrichissement, trickplay, chapitres, auto-next, selection de source, clavier a l'ecran)
- **Manquant pour la monetisation** : Google Play Billing

### Phase 7+8 : Preparation a la Release & Plan d'Action

**Rapport complet** : [phase7-8-release-plan.md](phase7-8-release-plan.md)

- **Score de preparation : 72/100**
- **3 elements bloquants** : Pas de Timber release tree (2h), versionCode=1 (1h), ~40 chaines codees en dur (4h)
- **4 elements importants non-bloquants** : 70 chaines FR manquantes, justification REQUEST_INSTALL_PACKAGES, 5 tests critiques manquants, integration Jellyfin non testee
- **Build/Release : OK** — Minification R8, reduction des ressources, splits ABI, keystore gitignore
- **Firebase : OK** — Crashlytics/Analytics/Performance tous gates sur DEBUG
- **Accessibilite : BON** — 132 contentDescriptions, 103 blocs semantics sur 30+ fichiers
- **Localisation : ATTENTION** — EN 653 chaines, FR 583 chaines (70 manquantes), ~40 chaines codees en dur dans les composables

---

## Plan d'Action Consolide en 5 Sprints

### Sprint 1 : Bloquants Critiques (1 semaine, ~14h)

| # | Tache | Effort | Priorite |
|---|-------|--------|----------|
| 1.1 | Corriger 4 vecteurs de crash `!!` (P0-1, P0-2, P0-3) | 1h | P0 |
| 1.2 | Creer `CrashReportingTree` pour Timber → Crashlytics (P0-5) | 2h | P0 |
| 1.3 | Incrementer `versionCode` et automatiser (P1-19) | 1h | P0 |
| 1.4 | Extraire ~40 chaines codees en dur dans `strings.xml` (P1-20) | 4h | P0 |
| 1.5 | Ajouter les traductions francaises pour 70 chaines manquantes (P1-21) | 3h | P1 |
| 1.6 | Revoir les `contentDescription = null` sur les elements interactifs | 2h | P1 |
| 1.7 | Test de fumee du build release | 2h | P0 |

### Sprint 2 : Stabilite Compose & Tests (1 semaine, ~20h)

| # | Tache | Effort | Priorite |
|---|-------|--------|----------|
| 2.1 | Ajouter `@Immutable` + `ImmutableList` aux 20+ classes UiState (P0-4, P1-9..12) | 4h | P0 |
| 2.2 | Corriger le lambda `onFocus` non-memorise dans `NetflixContentRow` | 30min | P1 |
| 2.3 | Ajouter `contentType` aux LazyRow/LazyColumn de l'ecran d'accueil | 1h | P1 |
| 2.4 | Ecrire `MediaDetailRepositoryImplTest` | 4h | P1 |
| 2.5 | Ecrire `PlaybackRepositoryImplTest` | 4h | P1 |
| 2.6 | Ecrire `JellyfinSourceHandlerTest` + `JellyfinMapperTest` | 5h | P1 |
| 2.7 | Ajouter `.catch {}` aux blocs collect de SettingsViewModel (P1-1) | 1h | P1 |

### Sprint 3 : Polish UX & Architecture (1 semaine, ~18h)

| # | Tache | Effort | Priorite |
|---|-------|--------|----------|
| 3.1 | Ajouter les animations de navigation Compose (P1-17) | 3h | P1 |
| 3.2 | Localiser les messages d'erreur EN/FR (P1-16) | 3h | P1 |
| 3.3 | Standardiser les ViewModels sur `BaseViewModel` (P1-13) | 3h | P1 |
| 3.4 | Extraire Firebase vers une interface `AnalyticsService` (P1-14) | 2h | P1 |
| 3.5 | Deplacer les 5 dependances codees en dur vers le catalogue de versions | 30min | P2 |
| 3.6 | Monter `targetSdk` a 36 | 4h | P1 |
| 3.7 | Ajouter les ressources `<plurals>` pour les chaines de comptage | 2h | P2 |

### Sprint 4 : Stabilisation Jellyfin (1 semaine, ~18h)

| # | Tache | Effort | Priorite |
|---|-------|--------|----------|
| 4.1 | Verifier l'unicite de `pageOffset` Jellyfin | 3h | P0 |
| 4.2 | Test d'integration : setup Jellyfin → connexion → sync → lecture | 4h | P1 |
| 4.3 | Tester `JellyfinPlaybackReporter` avec un vrai serveur | 3h | P1 |
| 4.4 | Verifier `JellyfinUrlBuilder` pour la lecture directe et le transcodage | 3h | P1 |
| 4.5 | Tester le chargement d'images Jellyfin via l'intercepteur | 2h | P1 |
| 4.6 | Tests ProGuard specifiques Jellyfin sur APK release | 3h | P1 |

### Sprint 5 : QA Final & Release (1 semaine, ~17h)

| # | Tache | Effort | Priorite |
|---|-------|--------|----------|
| 5.1 | Regression complete sur 3+ appareils TV (Shield, Chromecast, Fire TV) | 6h | P0 |
| 5.2 | Executer la suite complete Maestro E2E sur le build release | 2h | P0 |
| 5.3 | Verifier que Crashlytics recoit les crashs de test + erreurs Timber | 2h | P1 |
| 5.4 | Tests de performance : demarrage a froid, memoire, defilement | 3h | P1 |
| 5.5 | Bump de version final, changelog, soumission Play Store | 2h | P0 |
| 5.6 | Mettre en place le deploiement progressif (5% → 20% → 100%) | 1h | P1 |
| 5.7 | Configurer les seuils d'alerte Crashlytics | 1h | P2 |

**Effort total estime : 5 sprints / ~87 heures / 5 semaines**
**Release minimum viable** : Apres Sprint 1 + 2 (2 semaines) avec perimetre Plex uniquement

---

## Points Forts Cles (Top 10)

1. **Clean Architecture** — Le module domain n'a zero imports Android ; interfaces pur Kotlin
2. **Pattern Strategy** — `MediaSourceHandler` + Hilt `@IntoSet` multibinding est un OCP exemplaire
3. **Chiffrement AES-256-GCM** — Tous les secrets chiffres avec degradation gracieuse en cas de corruption du Keystore
4. **Zero GlobalScope** — Tout le travail en arriere-plan via `applicationScope` (SupervisorJob), `viewModelScope`, ou WorkManager
5. **Excellence base de donnees** — Mode WAL, 20+ index, FTS4, table unifiee materialisee, colonnes pre-calculees
6. **UI qualite Netflix** — Chargement skeleton sur chaque ecran, gestion du focus sur tous les parcours, 6 themes
7. **Lecteur hybride** — ExoPlayer + MPV avec pre-vol codecs, buffers adaptes au reseau, reutilisation de session, 7 sous-controleurs
8. **Agregation multi-source** — Plex + Jellyfin + Xtream avec enrichissement inter-serveurs et securite MAX correle
9. **Systeme d'erreurs type** — Hierarchie scellee `AppError` a 22 types avec `isCritical()`, `isRetryable()`, `toAppError()`
10. **Cache image adaptatif** — Dimensionnement base sur le heap (20% du max, borne 32-256 Mo) avec chaines d'URL de repli

---

## Evaluation des Risques

| Risque | Probabilite | Impact | Mitigation |
|--------|------------|--------|------------|
| ProGuard supprime une classe critique en release | Moyenne | Eleve | Sprint 2 (Maestro sur build release) |
| L'integration Jellyfin crashe en production | Elevee | Moyen | Sprint 4 (stabilisation dediee) |
| Saccades sur l'ecran d'accueil sur Mi Box S | Moyenne | Eleve | Sprint 2 (corrections stabilite Compose) |
| Les utilisateurs francophones voient des chaines non traduites | Moyenne | Faible | Sprint 1.4-1.5 (extraire + traduire) |
| Le Play Store rejette REQUEST_INSTALL_PACKAGES | Faible | Moyen | Sprint 3 (justification documentee) |
| Erreurs de production invisibles (pas de release tree) | Certaine | Eleve | Sprint 1.2 (CrashReportingTree) |

---

## Conclusion

PlexHubTV est une application Android TV bien concue qui est **a 2-5 semaines d'une release en production**. Le chemin critique est :

1. **Sprint 1 (1 semaine)** : Corriger les vecteurs de crash `!!` + creer CrashReportingTree + extraire les chaines codees en dur + traduire → **levee minimale des bloquants de release**
2. **Sprint 2 (1 semaine)** : Annotations Compose `@Immutable` + tests critiques → **performance + confiance**
3. **Sprints 3-5 (3 semaines)** : Animations de navigation, nettoyage architecture, stabilisation Jellyfin, QA sur appareils reels → **release en toute confiance**

L'ensemble des fonctionnalites est remarquablement complet : profils, themes, playlists, watchlist, favoris, historique, multi-serveur, lecteur hybride, economiseur d'ecran, clavier a l'ecran, trickplay, chapitres, auto-next. **L'infrastructure de monetisation (Google Play Billing) est la principale fonctionnalite manquante pour un lancement commercial.**

---

*Rapports d'audit individuels :*
- Phase 1+2 (Stabilite & Securite) : [phase1-2-stability-security.md](phase1-2-stability-security.md)
- Phase 3 (Performance) : [phase3-performance.md](phase3-performance.md)
- Phase 4 (Architecture) : [phase4-architecture.md](phase4-architecture.md)
- Phase 5+6 (UX & Fonctionnalites) : [phase5-6-ux-features.md](phase5-6-ux-features.md)
- Phase 7+8 (Release & Plan) : [phase7-8-release-plan.md](phase7-8-release-plan.md)
