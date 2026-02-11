# PlexHubTV — Plan d'Exécution 6 Mois (Solo Dev)
> **Date**: 11 février 2026
> **Profil**: Développeur solo, timeline aggressive
> **Focus**: Architecture technique → Premium features
> **Objectif**: App production-ready en 6 mois

---

## 🎯 Vision Stratégique

### Contexte

**Décisions Stratégiques**:
1. ✅ **Priorité**: Technique (architecture solide d'abord)
2. ✅ **Timeline**: Aggressive (6 mois vs 12 mois standard)
3. ✅ **Ressources**: Solo dev (pas de parallélisation)
4. ✅ **Marché**: Android TV exclusif (pas de mobile/tablet)
5. ✅ **Monétisation**: Premium features (architecture robuste requise)

### Implications

**Conséquences pour le plan**:
- 🔴 **Focus P2 uniquement** (P3 reporté après 6 mois ou incrémental)
- 🔴 **Actions séquentielles** (pas de travail parallèle)
- 🔴 **Priorisation stricte** (ROI maximum seulement)
- 🔴 **Pas de polish superflu** (UX fonctionnel > animations)
- ✅ **Architecture first** (fondations pour premium features)

### Objectifs 6 Mois

| Mois | Objectif | Livrables |
|------|----------|-----------|
| **1-2** | Architecture critique | Module `:data`, CI/CD, sécurité, PlayerVM split |
| **3-4** | Qualité & stabilité | Tests, cache, optimisations build |
| **5-6** | Features premium-ready | UX cohérente, erreurs gérées, profils base |

---

## 📅 Roadmap Détaillée 6 Mois

### 🗓️ Mois 1 — Semaines 1-4 (Architecture Critique)

**Objectif**: Poser les fondations techniques solides

#### Semaine 1 (40h)

**Lundi-Mardi** (16h): **P2.3 — Extraire Module `:data`** 🔴
```
Tâches:
✓ Créer module :data avec build.gradle.kts (2h)
✓ Déplacer app/data/ → :data/src/main/java/ (4h)
✓ Configurer dépendances (:domain, :core:*) (2h)
✓ Mettre à jour imports dans :app (4h)
✓ Tester compilation incrémentale (2h)
✓ Documenter dans ARCHITECTURE.md (2h)

Livrable: Module :data indépendant, build incrémental fonctionnel
```

**Mercredi-Jeudi** (16h): **P2.5 — Chiffrer Tokens (Sécurité)** 🔴
```
Tâches:
✓ Ajouter dépendance androidx.security:security-crypto (0.5h)
✓ Créer SecureSettingsDataStore avec EncryptedSharedPreferences (4h)
✓ Implémenter migration depuis DataStore plaintext (3h)
✓ Migrer AuthRepository vers SecureSettingsDataStore (3h)
✓ Ajouter tests de chiffrement/déchiffrement (3h)
✓ Tester sur émulateur rooté (1h)
✓ Documenter dans SECURITY.md (1.5h)

Livrable: Tokens chiffrés AES-256-GCM, migration automatique
```

**Vendredi** (8h): **P2.10 — GitHub Actions CI/CD** 🔴
```
Tâches:
✓ Créer .github/workflows/ci.yml (2h)
✓ Configurer Gradle cache (1h)
✓ Ajouter lint + tests + build (2h)
✓ Tester sur branche test (2h)
✓ Activer branch protection main (0.5h)
✓ Ajouter badge README.md (0.5h)

Livrable: CI/CD automatique sur chaque PR
```

#### Semaine 2 (40h)

**Lundi-Vendredi** (40h): **P2.1 — Splitter PlayerViewModel en 3 VMs** 🔴
```
Jour 1-2 (16h): Créer PlayerControlViewModel
  ✓ Extraire play/pause/seek/quality (6h)
  ✓ Gérer ExoPlayer/MPV switch (4h)
  ✓ Migrer UI state minimal (4h)
  ✓ Tests unitaires (4 tests) (2h)

Jour 3 (8h): Créer PlayerTrackViewModel
  ✓ Extraire audio/subtitle tracks (3h)
  ✓ Gérer delay sync (2h)
  ✓ Tests unitaires (3 tests) (2h)
  ✓ Documentation (1h)

Jour 4 (8h): Créer PlayerStatsViewModel
  ✓ Extraire performance overlay (3h)
  ✓ Bitrate monitoring (2h)
  ✓ Tests unitaires (2 tests) (2h)
  ✓ Documentation (1h)

Jour 5 (8h): Migration finale
  ✓ Refactoriser PlayerViewModel en coordinateur (4h)
  ✓ Migrer VideoPlayerScreen vers 3 VMs (3h)
  ✓ Tests d'intégration (1h)

Livrable: PlayerViewModel 150 lignes (vs 696), 3 VMs testés
```

#### Semaine 3 (40h)

**Lundi-Mardi** (16h): **P2.8 — Cache Mémoire Adaptatif** 🔴
```
Tâches:
✓ Implémenter calcul RAM disponible (2h)
✓ Adapter cache selon RAM (10-15%) (2h)
✓ Configurer disk cache 512 MB (1h)
✓ Tester sur émulateur 1GB/2GB/4GB (4h)
✓ Ajouter logs debug cache (1h)
✓ Tests unitaires calcul (2h)
✓ Documentation (2h)
✓ Benchmarks mémoire (2h)

Livrable: Cache adaptatif, stable sur Mi Box 1GB
```

**Mercredi-Jeudi** (16h): **P2.9 — Clés Composites Items** 🔴
```
Tâches:
✓ Audit tous les items {} sans key (2h)
✓ Corriger FavoritesScreen (1h)
✓ Corriger HistoryScreen (1h)
✓ Corriger DownloadsScreen (1h)
✓ Corriger IptvScreen (1h)
✓ Corriger SearchScreen (1h)
✓ Corriger LibrariesScreen (1h)
✓ Corriger CollectionDetailScreen (1h)
✓ Corriger HubDetailScreen (1h)
✓ Corriger SeasonDetailScreen (1h)
✓ Vérifier écrans Netflix (2h)
✓ Tests focus restoration (3h)

Livrable: Focus stable sur tous les écrans, clés "${ratingKey}_${serverId}"
```

**Vendredi** (8h): **P2.7 + P2.12 — Cleanup Code** 🟢
```
Tâches:
✓ Consolider PlexImageHelper (supprimer ImageUtil) (2h)
✓ Identifier use cases pass-through (1h)
✓ Supprimer ToggleFavoriteUseCase (0.5h)
✓ Supprimer ToggleWatchStatusUseCase (0.5h)
✓ Supprimer SyncWatchlistUseCase (0.5h)
✓ Supprimer GetWatchHistoryUseCase (0.5h)
✓ Mettre à jour ViewModels (2h)
✓ Tests de régression (1h)

Livrable: -60 lignes duplication, -4 use cases inutiles
```

#### Semaine 4 (40h) — Tests & Review

**Lundi-Mercredi** (24h): **Buffer & Tests Mois 1** ⚠️
```
Tâches:
✓ Tests end-to-end compilation incrémentale (4h)
✓ Tests sécurité tokens chiffrés (2h)
✓ Tests CI/CD sur plusieurs PRs (2h)
✓ Tests PlayerVM split (tous les use cases) (4h)
✓ Tests cache adaptatif sur 3 appareils (4h)
✓ Tests focus restoration (4h)
✓ Fixes bugs découverts (4h)

Livrable: Mois 1 validé, aucune régression
```

**Jeudi-Vendredi** (16h): **Documentation & Planification**
```
Tâches:
✓ Mettre à jour ARCHITECTURE.md (4h)
✓ Créer MIGRATION_GUIDE.md (module :data) (3h)
✓ Documenter PlayerVM split (2h)
✓ Créer SECURITY.md (chiffrement) (2h)
✓ Review code complet mois 1 (3h)
✓ Planification détaillée mois 2 (2h)

Livrable: Documentation complète, plan mois 2
```

**📊 Checkpoint Mois 1**:
- ✅ Module `:data` séparé → Build incrémental OK
- ✅ Tokens chiffrés AES-256 → Sécurité OK
- ✅ CI/CD GitHub Actions → Tests automatiques OK
- ✅ PlayerViewModel 150 lignes → Maintenabilité OK
- ✅ Cache adaptatif → Stabilité bas de gamme OK
- ✅ Focus stable → UX TV OK

---

### 🗓️ Mois 2 — Semaines 5-8 (Qualité & Tests)

**Objectif**: Augmenter confiance code, optimiser build

#### Semaine 5 (40h)

**Lundi-Vendredi** (40h): **P2.11 — Tests ViewModel (+29 tests)** 🔴
```
Jour 1 (8h): Tests PlayerControlViewModel
  ✓ Happy path play/pause/seek (2h)
  ✓ Quality selection (1h)
  ✓ ExoPlayer/MPV switch (2h)
  ✓ Error handling (2h)
  ✓ Edge cases (1h)

Jour 2 (8h): Tests PlayerTrackViewModel + PlayerStatsViewModel
  ✓ Track selection (audio/subtitle) (3h)
  ✓ Delay sync (2h)
  ✓ Stats monitoring (2h)
  ✓ Edge cases (1h)

Jour 3 (8h): Tests MediaDetailViewModel
  ✓ Load detail success/error (2h)
  ✓ Similar media loading (2h)
  ✓ Collections loading (2h)
  ✓ Source selection (2h)

Jour 4 (8h): Tests HomeViewModel
  ✓ Prefetch logic (2h)
  ✓ Error handling (2h)
  ✓ Sync WorkManager (2h)
  ✓ Pagination (2h)

Jour 5 (8h): Tests LibraryViewModel + MediaDetailRepositoryImpl
  ✓ Filter combinations (2h)
  ✓ Letter jump (2h)
  ✓ Repository cache (2h)
  ✓ Concurrent calls (2h)

Livrable: +29 tests unitaires, couverture VM > 70%
```

#### Semaine 6 (40h)

**Lundi-Mardi** (16h): **P2.2 — Splitter MediaDetailViewModel** 🟡
```
Jour 1 (8h): Créer MediaEnrichmentViewModel
  ✓ Extraire enrichment multi-serveur (3h)
  ✓ Extraire similar media (2h)
  ✓ Extraire collections (2h)
  ✓ Tests unitaires (1h)

Jour 2 (8h): Refactoring MediaDetailViewModel
  ✓ Simplifier vers loading de base (3h)
  ✓ Favoris/watch status seulement (2h)
  ✓ Migrer écran Detail (2h)
  ✓ Tests d'intégration (1h)

Livrable: MediaDetailViewModel 180 lignes (vs 357)
```

**Mercredi-Jeudi** (16h): **P2.6 — Éliminer Duplication HubsRepository** 🟡
```
Tâches:
✓ Analyser duplication cache/réseau (2h)
✓ Créer stratégie unifiée (4h)
✓ Refactoriser getHubs() (4h)
✓ Tests unitaires stratégie (3h)
✓ Tests de régression (2h)
✓ Documentation (1h)

Livrable: -160 lignes code, HubsRepositoryImpl 120 lignes (vs 228)
```

**Vendredi** (8h): **P3.12 + P3.13 — Build Optimizations** 🟢
```
Tâches:
✓ Activer ksp.incremental=true (0.5h)
✓ Tester build incrémental (1h)
✓ Retirer Detekt ignoreFailures (0.5h)
✓ Fixer violations Detekt (4h)
✓ Benchmarks build (cold/incremental) (1h)
✓ Documentation build.md (1h)

Livrable: Build 30% plus rapide, Detekt strict mode
```

#### Semaine 7 (40h)

**Lundi-Mardi** (16h): **P2.4 — Module `:core:ui`** 🟢
```
Jour 1 (8h): Créer module et extraire composants
  ✓ Créer core/ui/build.gradle.kts (1h)
  ✓ Extraire NetflixMediaCard (2h)
  ✓ Extraire NetflixContentRow (2h)
  ✓ Extraire NetflixTopBar (2h)
  ✓ Configurer dépendances (1h)

Jour 2 (8h): Finalisation
  ✓ Extraire NetflixOnScreenKeyboard (2h)
  ✓ Extraire SourceSelectionDialog (2h)
  ✓ Mettre à jour imports :app (2h)
  ✓ Tests screenshot (Robolectric) (2h)

Livrable: Module :core:ui avec 5 composants réutilisables
```

**Mercredi** (8h): **P3.20 — Fix Thread-Safety AuthInterceptor** 🟡
```
Tâches:
✓ Analyser race condition actuelle (1h)
✓ Implémenter AtomicReference<AuthData> (2h)
✓ Tests concurrence (MockWebServer) (3h)
✓ Tests de régression (1h)
✓ Documentation (1h)

Livrable: AuthInterceptor thread-safe
```

**Jeudi-Vendredi** (16h): **Buffer & Tests Mois 2**
```
Tâches:
✓ Tests end-to-end tous les VMs (4h)
✓ Tests build incrémental (2h)
✓ Tests module :core:ui (2h)
✓ Benchmarks performance globaux (3h)
✓ Fixes bugs découverts (3h)
✓ Review code complet (2h)

Livrable: Mois 2 validé, aucune régression
```

#### Semaine 8 (40h) — Documentation & Review

**Lundi-Mercredi** (24h): **Documentation Complète**
```
Tâches:
✓ Mettre à jour ARCHITECTURE.md (modules) (4h)
✓ Créer TESTING.md (stratégie tests) (3h)
✓ Créer PERFORMANCE.md (benchmarks) (3h)
✓ Documenter tous les VMs (6h)
✓ Créer diagrammes architecture (Mermaid) (4h)
✓ Review documentation complète (2h)
✓ Vidéo demo changements (2h)

Livrable: Documentation exhaustive mois 1-2
```

**Jeudi-Vendredi** (16h): **Planification Mois 3-4**
```
Tâches:
✓ Analyser vélocité mois 1-2 (2h)
✓ Ajuster plan mois 3-4 si besoin (2h)
✓ Prioriser actions restantes P2 (2h)
✓ Définir features premium MVP (4h)
✓ Créer wireframes features premium (3h)
✓ Planification détaillée mois 3 (3h)

Livrable: Plan ajusté mois 3-4, specs premium features
```

**📊 Checkpoint Mois 2**:
- ✅ +29 tests VM → Couverture > 70%
- ✅ ViewModels < 200 lignes → Maintenabilité OK
- ✅ Aucune duplication > 20 lignes → Code clean OK
- ✅ Build < 1 min cold → Productivité OK
- ✅ Module :core:ui → Réutilisabilité OK
- ✅ Detekt strict → Qualité forcée OK

---

### 🗓️ Mois 3 — Semaines 9-12 (UX Pro)

**Objectif**: Polish UX, gestion erreurs, features premium base

#### Semaine 9 (40h)

**Lundi-Mardi** (16h): **P2.13 — Gestion Erreur Centralisée** 🔴
```
Jour 1 (8h): Créer ErrorHandler
  ✓ Sealed class AppError (1h)
  ✓ ErrorSnackbarHost composable (3h)
  ✓ Extension toUserMessage() (2h)
  ✓ Tests unitaires (2h)

Jour 2 (8h): Migration écrans
  ✓ Migrer HomeScreen (1h)
  ✓ Migrer SearchScreen (1h)
  ✓ Migrer LibraryScreen (1h)
  ✓ Migrer DetailScreen (1h)
  ✓ Migrer PlayerScreen (1h)
  ✓ Tests UX (2h)
  ✓ Documentation (1h)

Livrable: Snackbar global erreurs, UX cohérente
```

**Mercredi-Jeudi** (16h): **P2.14 — Continue Watching Amélioré** 🟡
```
Jour 1 (8h): Barre de progression
  ✓ Créer EnhancedMediaCard (4h)
  ✓ Intégrer barre progression (2h)
  ✓ Afficher temps restant (1h)
  ✓ Tests screenshot (1h)

Jour 2 (8h): Tri et optimisations
  ✓ Tri par viewedAt desc (2h)
  ✓ Migrer NetflixHomeScreen (2h)
  ✓ Prefetch images Continue Watching (2h)
  ✓ Tests d'intégration (2h)

Livrable: Continue Watching avec barre progression, tri intelligent
```

**Vendredi** (8h): **P2.15 — Prefetch Prochain Épisode** 🟡
```
Tâches:
✓ Implémenter findNextEpisode() (2h)
✓ Détection 80% lecture (1h)
✓ Préchargement dans cache ExoPlayer (3h)
✓ Tests sur série multi-épisodes (2h)

Livrable: Transition fluide épisodes < 1s
```

#### Semaine 10 (40h)

**Lundi-Mercredi** (24h): **P3.3 — Écran de Debug** 🟡
```
Jour 1 (8h): Interface Debug
  ✓ Créer DebugScreen.kt (3h)
  ✓ Afficher version/build info (1h)
  ✓ Afficher server status (2h)
  ✓ Afficher cache stats (2h)

Jour 2 (8h): Features avancées
  ✓ Export logs (Timber → fichier) (3h)
  ✓ Force sync button (1h)
  ✓ Clear cache button (1h)
  ✓ DB stats (1h)
  ✓ Network diagnostics (2h)

Jour 3 (8h): Finalisation
  ✓ Settings → Debug hidden menu (2h)
  ✓ Secure access (dev mode only) (2h)
  ✓ Tests (2h)
  ✓ Documentation (2h)

Livrable: Écran debug complet pour support utilisateur
```

**Jeudi-Vendredi** (16h): **Premium Features — Specs Détaillées** 📋
```
Tâches:
✓ Définir feature gate system (4h)
✓ Créer PremiumFeature enum (1h)
✓ Implémenter PremiumManager (3h)
✓ Mock payment flow (Google Play Billing) (4h)
✓ UI "Upgrade to Premium" (3h)
✓ Documentation PREMIUM.md (1h)

Livrable: Système premium features prêt, mock payment
```

#### Semaine 11 (40h)

**Lundi-Vendredi** (40h): **P3.7 — Profils Avancés (Base)** 🔴
```
Jour 1 (8h): Base de données
  ✓ ProfileEntity (avatar, restrictions) (2h)
  ✓ ProfileDao CRUD (2h)
  ✓ Migration DB (2h)
  ✓ Tests DAO (2h)

Jour 2 (8h): Repository & ViewModel
  ✓ ProfileRepository interface (1h)
  ✓ ProfileRepositoryImpl (3h)
  ✓ ProfileManagementViewModel (3h)
  ✓ Tests unitaires (1h)

Jour 3 (8h): UI Profils
  ✓ ProfileSelectionScreen (3h)
  ✓ ProfileEditScreen (3h)
  ✓ Avatar picker (2h)

Jour 4 (8h): Restrictions
  ✓ Content rating filtering (3h)
  ✓ Age restriction enforcement (2h)
  ✓ Intégrer dans LibraryRepository (2h)
  ✓ Tests (1h)

Jour 5 (8h): Historique/Favoris séparés
  ✓ Ajouter profileId à HistoryEntity (2h)
  ✓ Ajouter profileId à FavoriteEntity (2h)
  ✓ Migrer queries (2h)
  ✓ Tests end-to-end (2h)

Livrable: Profils multi-user avec avatar et restrictions (feature premium)
```

#### Semaine 12 (40h) — Buffer & Tests Mois 3

**Lundi-Mercredi** (24h): **Tests & Fixes**
```
Tâches:
✓ Tests gestion erreurs sur tous écrans (4h)
✓ Tests Continue Watching (barre, tri) (2h)
✓ Tests prefetch épisodes (3h)
✓ Tests écran debug (2h)
✓ Tests profils (CRUD, restrictions) (4h)
✓ Tests premium feature gates (3h)
✓ Fixes bugs découverts (4h)
✓ Review code complet (2h)

Livrable: Mois 3 validé
```

**Jeudi-Vendredi** (16h): **Documentation & Demo**
```
Tâches:
✓ Mettre à jour ARCHITECTURE.md (profils) (2h)
✓ Créer PREMIUM_FEATURES.md (specs) (3h)
✓ Documenter error handling (2h)
✓ Créer USER_GUIDE.md (profils) (2h)
✓ Vidéo demo profils (2h)
✓ Planification mois 4 (3h)
✓ Review mi-parcours (2h)

Livrable: Documentation complète, demo profils
```

**📊 Checkpoint Mois 3** (Mi-Parcours):
- ✅ Erreurs gérées uniformément → UX robuste OK
- ✅ Continue Watching pro → Barre + tri OK
- ✅ Prefetch épisodes → Transition < 1s OK
- ✅ Écran debug → Support technique OK
- ✅ Profils multi-user → Feature premium #1 OK
- ✅ Feature gates → Monétisation ready OK

**🎯 État Global (50% timeline)**:
- ✅ P2 Critique: 10/15 complétées (67%)
- ✅ P3 Stratégique: 2/20 complétées (10%)
- ✅ Architecture: SOLIDE
- ✅ Qualité: HAUTE (tests > 70%)
- ✅ Features Premium: BASE READY

---

### 🗓️ Mois 4 — Semaines 13-16 (Features Premium)

**Objectif**: Finaliser features premium, polish UX

#### Semaine 13 (40h)

**Lundi-Mardi** (16h): **P3.4 — Recherche Vocale** 🟢
```
Jour 1 (8h): Implémentation base
  ✓ VoiceSearchHelper (SpeechRecognizer) (3h)
  ✓ Intégration NetflixSearchScreen (2h)
  ✓ Gestion permissions (1h)
  ✓ Error handling (2h)

Jour 2 (8h): UI et tests
  ✓ Bouton micro avec animation (2h)
  ✓ Feedback visuel (listening...) (2h)
  ✓ Tests sur émulateur (2h)
  ✓ Tests voix synthétique (1h)
  ✓ Documentation (1h)

Livrable: Recherche vocale fonctionnelle (feature premium)
```

**Mercredi-Jeudi** (16h): **P3.8 — Sections Home Configurables (Base)** 🟡
```
Jour 1 (8h): Base de données
  ✓ HomeSectionConfig entity (2h)
  ✓ CRUD operations (2h)
  ✓ Default config (2h)
  ✓ Tests DAO (2h)

Jour 2 (8h): UI Configuration
  ✓ HomeSectionSettingsScreen (4h)
  ✓ Toggle show/hide sections (2h)
  ✓ Reorder sections (simple list) (2h)

Livrable: Configuration sections home (show/hide, reorder)
```

**Vendredi** (8h): **P3.9 — Crashlytics/Analytics** 🟡
```
Tâches:
✓ Ajouter Firebase SDK (1h)
✓ Configurer Crashlytics (2h)
✓ Configurer Analytics (1h)
✓ Log events clés (play, search, etc.) (2h)
✓ Tester crash reporting (1h)
✓ Documentation ANALYTICS.md (1h)

Livrable: Télémétrie active, crash reporting
```

#### Semaine 14 (40h)

**Lundi-Mardi** (16h): **P3.19 — Bandes-Annonces (YouTube)** 🟡
```
Jour 1 (8h): Intégration API
  ✓ YouTube Data API v3 (2h)
  ✓ Fetch trailers par TMDB ID (3h)
  ✓ Cache trailers (1h)
  ✓ Error handling (2h)

Jour 2 (8h): UI Player
  ✓ Bouton "Watch Trailer" dans Detail (2h)
  ✓ YouTube player (AndroidView) (3h)
  ✓ UI controls (2h)
  ✓ Tests (1h)

Livrable: Trailers YouTube dans détail média (feature premium)
```

**Mercredi-Vendredi** (24h): **Premium Features — Finalisation** 🔴
```
Jour 1 (8h): Payment Flow
  ✓ Google Play Billing Library v5 (3h)
  ✓ Purchase flow (2h)
  ✓ Verify purchase (2h)
  ✓ Tests sandbox (1h)

Jour 2 (8h): Feature Unlock Logic
  ✓ PremiumManager.isPremium() (2h)
  ✓ Feature gate enforcement (3h)
  ✓ Restore purchases (2h)
  ✓ Tests (1h)

Jour 3 (8h): UI Premium
  ✓ Premium badge UI (2h)
  ✓ Paywall screens (3h)
  ✓ Success/error dialogs (2h)
  ✓ Tests end-to-end (1h)

Livrable: Payment flow complet, features premium lockées
```

#### Semaine 15 (40h)

**Lundi-Mardi** (16h): **P3.5 — Mode Picture-in-Picture** 🟡
```
Jour 1 (8h): Implémentation PiP
  ✓ PictureInPictureParams (2h)
  ✓ Gérer lifecycle (onPause → PiP) (3h)
  ✓ Custom controls PiP (2h)
  ✓ Tests (1h)

Jour 2 (8h): Polish
  ✓ Aspect ratio dynamique (2h)
  ✓ Actions PiP (play/pause/next) (3h)
  ✓ Tests sur Android TV (2h)
  ✓ Documentation (1h)

Livrable: Mode PiP fonctionnel
```

**Mercredi-Vendredi** (24h): **Tests & Polish Global**
```
Jour 1 (8h): Tests Premium Features
  ✓ Tests purchase flow (3h)
  ✓ Tests feature gates (2h)
  ✓ Tests restore purchases (2h)
  ✓ Tests edge cases (1h)

Jour 2 (8h): Tests UX
  ✓ Tests recherche vocale (2h)
  ✓ Tests trailers YouTube (2h)
  ✓ Tests PiP (2h)
  ✓ Tests sections config (2h)

Jour 3 (8h): Fixes & Polish
  ✓ Fixes bugs critiques (4h)
  ✓ Polish UI (animations) (2h)
  ✓ Performance profiling (2h)

Livrable: Features premium testées et polished
```

#### Semaine 16 (40h) — Review & Documentation

**Lundi-Mercredi** (24h): **Documentation Complète**
```
Tâches:
✓ PREMIUM_FEATURES.md final (4h)
✓ USER_GUIDE.md complet (4h)
✓ DEVELOPER_GUIDE.md (4h)
✓ API_INTEGRATION.md (YouTube, Billing) (3h)
✓ DEPLOYMENT.md (Play Store) (3h)
✓ VIDEO tutorials (3h)
✓ Review documentation (3h)

Livrable: Documentation exhaustive
```

**Jeudi-Vendredi** (16h): **Planification Mois 5-6**
```
Tâches:
✓ Review vélocité mois 1-4 (2h)
✓ Ajuster plan mois 5-6 (2h)
✓ Définir critères "production-ready" (3h)
✓ Planifier tests beta (2h)
✓ Préparer Play Store assets (3h)
✓ Planification détaillée mois 5 (4h)

Livrable: Plan finalisé mois 5-6, beta ready
```

**📊 Checkpoint Mois 4**:
- ✅ Recherche vocale → Standard TV OK
- ✅ Sections configurables → Personnalisation OK
- ✅ Crashlytics → Monitoring OK
- ✅ Trailers YouTube → Engagement OK
- ✅ Payment flow → Monétisation OK
- ✅ Mode PiP → Standard Android TV OK

**🎯 État Global (67% timeline)**:
- ✅ P2 Critique: 13/15 complétées (87%)
- ✅ P3 Stratégique: 6/20 complétées (30%)
- ✅ Features Premium: 4/4 MVP ready
- ✅ Monétisation: ACTIVE

---

### 🗓️ Mois 5 — Semaines 17-20 (Polish & Accessibilité)

**Objectif**: App production-ready, tests beta

#### Semaine 17 (40h)

**Lundi-Mardi** (16h): **P3.10 — Accessibilité TV** 🟡
```
Jour 1 (8h): Audit accessibilité
  ✓ Audit tous les composants (3h)
  ✓ Identifier manques ContentDescription (2h)
  ✓ Planifier corrections (2h)
  ✓ Créer checklist (1h)

Jour 2 (8h): Corrections
  ✓ Ajouter ContentDescription (4h)
  ✓ Tester avec TalkBack (2h)
  ✓ Fixes feedback TalkBack (2h)

Livrable: App accessible, TalkBack compatible
```

**Mercredi** (8h): **P3.6 — Animations de Transition** 🟡
```
Tâches:
✓ SharedTransitionLayout setup (2h)
✓ Shared element Home → Detail (3h)
✓ Fade transitions écrans (2h)
✓ Tests performance (1h)

Livrable: Transitions polies entre écrans
```

**Jeudi-Vendredi** (16h): **P3.17 — Onboarding Guidé** 🟢
```
Jour 1 (8h): Design & Implémentation
  ✓ OnboardingScreen steps (4h)
  ✓ Persistence (skip onboarding) (1h)
  ✓ Navigation (2h)
  ✓ Tests (1h)

Jour 2 (8h): Polish
  ✓ Illustrations/assets (2h)
  ✓ Animations (2h)
  ✓ i18n strings (2h)
  ✓ Tests UX (2h)

Livrable: Onboarding première utilisation
```

#### Semaine 18 (40h)

**Lundi-Vendredi** (40h): **Tests Beta — Preparation** 🔴
```
Jour 1 (8h): Beta Infrastructure
  ✓ Configurer beta track Play Store (2h)
  ✓ ProGuard/R8 configuration (3h)
  ✓ Signing release (2h)
  ✓ Tests release build (1h)

Jour 2 (8h): Tests End-to-End
  ✓ Tests tous les flows (4h)
  ✓ Tests premium features (2h)
  ✓ Tests crash reporting (1h)
  ✓ Tests analytics (1h)

Jour 3 (8h): Performance
  ✓ Profiling mémoire (3h)
  ✓ Profiling CPU (2h)
  ✓ Benchmarks startup (2h)
  ✓ Fixes performance (1h)

Jour 4 (8h): Stabilité
  ✓ Stress tests (3h)
  ✓ Tests long-running (2h)
  ✓ Tests rotation/lifecycle (2h)
  ✓ Fixes stabilité (1h)

Jour 5 (8h): Documentation Beta
  ✓ BETA_TESTING.md (2h)
  ✓ Known issues list (2h)
  ✓ Feedback form (1h)
  ✓ Privacy policy (2h)
  ✓ Terms of service (1h)

Livrable: App beta-ready, docs complètes
```

#### Semaine 19 (40h)

**Lundi** (8h): **Release Beta v1** 🚀
```
Tâches:
✓ Upload APK Play Console (1h)
✓ Configurer beta testers (1h)
✓ Publish beta release (1h)
✓ Monitor crashes first 24h (3h)
✓ Hotfixes urgents (2h)

Livrable: Beta v1 live
```

**Mardi-Vendredi** (32h): **Feedback Beta & Fixes** ⚠️
```
Tâches:
✓ Collecter feedback beta testers (4h)
✓ Trier bugs par priorité (2h)
✓ Fixes bugs critiques (12h)
✓ Fixes bugs moyens (8h)
✓ Améliorer UX selon feedback (4h)
✓ Release beta v1.1 (2h)

Livrable: Beta v1.1 avec fixes
```

#### Semaine 20 (40h) — Review Mois 5

**Lundi-Mercredi** (24h): **Tests & Polish Final**
```
Tâches:
✓ Tests régression complets (6h)
✓ Tests sur 3 appareils TV (6h)
✓ Polish UI global (4h)
✓ Fixes bugs restants (4h)
✓ Performance profiling final (2h)
✓ Documentation updates (2h)

Livrable: App stable, testée
```

**Jeudi-Vendredi** (16h): **Planification Mois 6 (Final)**
```
Tâches:
✓ Review vélocité complète (3h)
✓ Définir checklist production (3h)
✓ Préparer Play Store listing (4h)
✓ Planifier marketing launch (2h)
✓ Planification détaillée mois 6 (4h)

Livrable: Plan mois 6, production-ready checklist
```

**📊 Checkpoint Mois 5**:
- ✅ Accessibilité → Conformité OK
- ✅ Animations → Polish OK
- ✅ Onboarding → First-time UX OK
- ✅ Beta tests → Feedback collecté OK
- ✅ Stabilité → Production-ready OK

**🎯 État Global (83% timeline)**:
- ✅ P2 Critique: 15/15 complétées (100%) 🎉
- ✅ P3 Stratégique: 9/20 complétées (45%)
- ✅ Beta: LIVE
- ✅ Production: PROCHE

---

### 🗓️ Mois 6 — Semaines 21-24 (Production Launch)

**Objectif**: Launch production, monitoring, premières itérations

#### Semaine 21 (40h)

**Lundi-Mardi** (16h): **Play Store Preparation** 🚀
```
Jour 1 (8h): Assets
  ✓ Screenshots (phone, tablet, TV) (3h)
  ✓ Feature graphic (2h)
  ✓ Promo video (2h)
  ✓ App icon final (1h)

Jour 2 (8h): Listing
  ✓ Title & short description (2h)
  ✓ Full description (2h)
  ✓ Keywords SEO (1h)
  ✓ Translations (2h)
  ✓ Content rating (1h)

Livrable: Play Store listing complet
```

**Mercredi** (8h): **P3.11 — i18n Base** 🟢
```
Tâches:
✓ Extraire hardcoded strings (3h)
✓ Traduire FR/ES/DE (3h)
✓ Tests langues (1h)
✓ Documentation i18n (1h)

Livrable: App multi-langue (EN/FR/ES/DE)
```

**Jeudi-Vendredi** (16h): **Final Testing**
```
Tâches:
✓ Tests end-to-end complets (4h)
✓ Tests sur 5 appareils différents (4h)
✓ Tests premium flow (2h)
✓ Tests payment sandbox (2h)
✓ Performance profiling (2h)
✓ Security audit (2h)

Livrable: App 100% testée
```

#### Semaine 22 (40h)

**Lundi** (8h): **Production Release v1.0** 🎉
```
Tâches:
✓ Build release final (1h)
✓ Upload production track (1h)
✓ Publish production (1h)
✓ Monitor crashes 24h (3h)
✓ Hotfixes critiques si besoin (2h)

Livrable: PlexHubTV v1.0 LIVE! 🚀
```

**Mardi-Vendredi** (32h): **Monitoring & Support**
```
Tâches:
✓ Monitor Crashlytics daily (4h/jour × 4 = 16h)
✓ Répondre reviews Play Store (2h/jour × 4 = 8h)
✓ Fixes bugs urgents (4h)
✓ Optimisations basées analytics (4h)

Livrable: App stable en production
```

#### Semaine 23 (40h)

**Lundi-Vendredi** (40h): **Iteration v1.1** 🔄
```
Tâches:
✓ Analyser analytics (top features, churn) (4h)
✓ Analyser feedback utilisateurs (4h)
✓ Prioriser features v1.1 (2h)
✓ Implémenter top 3 requests (20h)
✓ Tests (6h)
✓ Release v1.1 (2h)
✓ Documentation release notes (2h)

Livrable: PlexHubTV v1.1 avec améliorations
```

#### Semaine 24 (40h) — Retrospective & Roadmap

**Lundi-Mercredi** (24h): **Documentation Finale**
```
Tâches:
✓ Mettre à jour tous les docs (6h)
✓ Créer CHANGELOG.md complet (3h)
✓ Documenter architecture finale (4h)
✓ Créer guide contribution (3h)
✓ Video tutorials utilisateur (4h)
✓ Blog post launch (2h)
✓ Social media posts (2h)

Livrable: Documentation exhaustive
```

**Jeudi-Vendredi** (16h): **Retrospective & Roadmap v2**
```
Tâches:
✓ Retrospective 6 mois (4h)
✓ Métriques success (users, revenue) (2h)
✓ Lessons learned (2h)
✓ Roadmap v2.0 (features futures) (4h)
✓ Planification Q3/Q4 (4h)

Livrable: Retrospective complète, roadmap v2
```

**📊 Checkpoint Final (Mois 6)**:
- ✅ Play Store → LIVE
- ✅ i18n → 4 langues
- ✅ Monitoring → Crashlytics active
- ✅ v1.0 → Production
- ✅ v1.1 → Première itération

**🎯 État Final (100% timeline)**:
- ✅ P2: 15/15 (100%) ✅
- ✅ P3: 11/20 (55%)
- ✅ Premium: MONETIZED
- ✅ Production: LIVE
- ✅ Users: GROWING

---

## 📊 Métriques Success

### KPIs Techniques

| Métrique | Baseline (Départ) | Objectif 6 Mois | Actuel (Prévision) |
|----------|-------------------|-----------------|-------------------|
| **Build Cold** | 3 min | < 1 min | ✅ 50s |
| **Build Incrémental** | 45s | < 15s | ✅ 12s |
| **Tests VM Couverture** | ~30% | > 70% | ✅ 75% |
| **ViewModels > 200 lignes** | 3 | 0 | ✅ 0 |
| **Duplication Code** | 160+ lignes | 0 | ✅ 0 |
| **Modules Gradle** | 7 | 9+ | ✅ 10 |
| **CI/CD** | ❌ Aucun | ✅ Automatique | ✅ GitHub Actions |
| **Detekt Strict** | ❌ ignoreFailures | ✅ Bloquant | ✅ Bloquant |

### KPIs Produit

| Métrique | Objectif 6 Mois | Status |
|----------|-----------------|--------|
| **Play Store Release** | ✅ Production | 🎯 Mois 6 |
| **Features Premium** | 4+ ready | ✅ 6 ready |
| **Langues Supportées** | 3+ | ✅ 4 (EN/FR/ES/DE) |
| **Beta Testers** | 50+ | 🎯 Semaine 19 |
| **Crashlytics** | < 1% crash rate | 🎯 Monitor |
| **Payment Flow** | ✅ Fonctionnel | ✅ Google Play Billing |

---

## 🎯 Checklist Production-Ready

### Architecture ✅

- [x] Module `:data` séparé
- [x] ViewModels < 200 lignes
- [x] Aucune duplication > 20 lignes
- [x] Clean Architecture respectée
- [x] Dependency Inversion OK
- [x] Module `:core:ui` partagé

### Qualité ✅

- [x] Tests VM couverture > 70%
- [x] CI/CD GitHub Actions
- [x] Detekt strict mode
- [x] Pas de warnings compilation
- [x] ProGuard/R8 configuré
- [x] Signing release configuré

### Sécurité ✅

- [x] Tokens chiffrés AES-256
- [x] Thread-safety vérifié
- [x] Permissions minimales
- [x] Privacy policy
- [x] Terms of service

### UX ✅

- [x] Gestion erreur centralisée
- [x] Continue Watching avec barre
- [x] Prefetch épisodes
- [x] Accessibilité TalkBack
- [x] Animations transitions
- [x] Onboarding guidé

### Features Premium ✅

- [x] Profils multi-user
- [x] Recherche vocale
- [x] Trailers YouTube
- [x] Mode PiP
- [x] Sections configurables
- [x] Payment flow Google Play

### Monitoring ✅

- [x] Crashlytics actif
- [x] Analytics events
- [x] Performance tracking
- [x] Écran debug

### Play Store ✅

- [x] Listing complet
- [x] Screenshots
- [x] Feature graphic
- [x] Promo video
- [x] Content rating
- [x] i18n 4 langues

---

## 💰 Budget Temps (Solo Dev)

### Répartition Globale

| Catégorie | Heures | % Total | Semaines |
|-----------|--------|---------|----------|
| **Architecture** | 160h | 33% | 4 sem |
| **Features** | 144h | 30% | 3.6 sem |
| **Tests/Qualité** | 96h | 20% | 2.4 sem |
| **Documentation** | 48h | 10% | 1.2 sem |
| **Buffer/Fixes** | 32h | 7% | 0.8 sem |
| **TOTAL** | **480h** | **100%** | **12 sem** |

**Timeline**: 480h ÷ 40h/semaine = **12 semaines** (3 mois) tempo soutenu

**Réalité avec buffer**: 3 mois × 2 = **6 mois** (timing confortable)

### Répartition par Priorité

| Priorité | Actions | Heures | % |
|----------|---------|--------|---|
| **P2 (15)** | Architecture/Qualité | 288h | 60% |
| **P3 (11)** | Features/Polish | 144h | 30% |
| **Buffer** | Tests/Fixes | 48h | 10% |
| **TOTAL** | **26 actions** | **480h** | **100%** |

---

## ⚠️ Risques & Mitigation

### Risques Majeurs

| Risque | Probabilité | Impact | Mitigation |
|--------|-------------|--------|------------|
| **Burnout solo dev** | 🔴 Haute | Critique | Buffer 1 semaine/mois, vacances planifiées |
| **Scope creep features** | 🟡 Moyenne | Haute | Strict prioritization, "No" aux features non-plan |
| **Bugs bloquants production** | 🟡 Moyenne | Critique | Beta testing 2 semaines, hotfix process |
| **Payment flow rejeté Play Store** | 🟢 Faible | Haute | Tests sandbox exhaustifs, review guidelines |
| **Performance bas de gamme** | 🟡 Moyenne | Moyenne | Tests Mi Box 1GB dès mois 1 |

### Plan B (Si retard)

**Si retard 2 semaines à mi-parcours**:
1. ✅ Garder P2 (architecture critique)
2. ❌ Couper P3 non-essential (animations, i18n extended)
3. ✅ Garder features premium core (profils, vocale)
4. 📅 Reporter production launch +1 mois

**Si retard 1 mois à mi-parcours**:
1. ✅ Garder P2 complet
2. ❌ Couper 50% P3 (polish only)
3. ✅ MVP premium features (profils seulement)
4. 📅 Reporter production launch +2 mois

---

## 🎯 Success Criteria

### Technique (Mois 1-2)

✅ Module `:data` compile indépendamment
✅ Build < 1 min (cold)
✅ Tests couverture > 70%
✅ Tokens chiffrés validé audit sécurité
✅ CI/CD green sur toutes les PRs

### Produit (Mois 3-4)

✅ Profils multi-user fonctionnel
✅ 4+ features premium ready
✅ Payment flow testé sandbox
✅ UX cohérente (erreurs, loading, etc.)
✅ Crashlytics intégré

### Launch (Mois 5-6)

✅ Beta tests 50+ utilisateurs
✅ Crash rate < 1%
✅ Production release Play Store
✅ 4 langues supportées
✅ Documentation complète

---

## 📚 Ressources Nécessaires

### Développement

- ✅ Android Studio Hedgehog+ (latest)
- ✅ Émulateur Android TV (API 27+)
- ✅ Device Android TV physique (Mi Box ou Shield)
- ✅ MacBook/PC 16GB+ RAM (build performance)

### Services

- ✅ GitHub Pro (CI/CD minutes)
- ✅ Firebase (Crashlytics, Analytics) — FREE tier OK
- ✅ Google Play Console ($25 one-time)
- ✅ Plex Pass (testing) — $5/mois

### Design

- ✅ Figma (wireframes premium features) — FREE tier OK
- ✅ Asset generation (icons, screenshots)

**Budget Total**: ~$100 (one-time) + $5/mois (Plex)

---

## 🎉 Conclusion

### Vision 6 Mois

**De**: App fonctionnelle mais architecture chaotique, pas de tests, pas de premium
**À**: App production-ready, architecture solide, monétisée, 1000+ users

### Timeline Réaliste (Solo Dev Aggressive)

```
Mois 1-2: Architecture & Sécurité  ████████░░░░░░░░░░░░ 20%
Mois 3-4: Features Premium         ████████████░░░░░░░░ 50%
Mois 5-6: Beta → Production        ████████████████████ 100%
```

### Post-6 Mois

**V2.0 Roadmap** (Mois 7-12):
- P3 features restantes (screenshot tests, intégration complète)
- Marketing & growth (1000 → 10000 users)
- Premium conversion optimization
- Android mobile/tablet support?
- Feature requests top utilisateurs

**Statut Prévisionnel (Fin Mois 6)**:
- ✅ **Architecture**: SOLIDE (modules, tests, CI/CD)
- ✅ **Produit**: COMPLET (premium features, monétisé)
- ✅ **Production**: LIVE (Play Store, monitoring)
- ✅ **Utilisateurs**: CROISSANCE (beta → production)
- ✅ **Revenus**: ACTIFS (premium subscriptions)

---

**Document créé le**: 11 février 2026
**Timeline**: 6 mois aggressive (solo dev)
**Focus**: Technique → Premium → Production
**Status**: 🟢 **READY TO START**

**Next Steps**:
1. ✅ Review plan complet
2. ✅ Setup environment (Android Studio, GitHub, Firebase)
3. ✅ Créer branche `develop`
4. 🚀 **START Semaine 1 Lundi**: Module `:data`
